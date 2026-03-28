package com.github.gtexpert.blpc.common.party;

import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.github.gtexpert.blpc.common.ModLog;

/**
 * Represents a party with members, roles, trust settings, and invitations.
 * <p>
 * Persisted to {@code world/betterlink/pc/parties/<id>.dat} via
 * {@link com.github.gtexpert.blpc.common.BLPCSaveHandler}.
 * Invitations are memory-only and do not survive server restarts.
 */
public class Party {

    public static final String DEFAULT_NAME_KEY = "blpc.party.default_name";

    public static UUID uuidFromIntId(int id) {
        return UUID.nameUUIDFromBytes(("blpc:party:" + id).getBytes(StandardCharsets.UTF_8));
    }

    private final UUID partyId;
    private String name;
    private final Map<UUID, PartyRole> members = new LinkedHashMap<>();
    private final long createdAt;

    // Trust level settings per action
    private final Map<TrustAction, TrustLevel> requiredTrustLevels = new EnumMap<>(TrustAction.class);
    private TrustLevel fakePlayerTrustLevel = TrustLevel.ALLY;
    private boolean protectExplosions = true;

    // Ally / Enemy sets
    private final Set<UUID> allies = new LinkedHashSet<>();
    private final Set<UUID> enemies = new LinkedHashSet<>();

    // Party metadata
    private boolean freeToJoin = false;
    private int color = EnumDyeColor.BLUE.getColorValue();
    private String description = "";

    /** UUID → display name cache. Populated server-side before sync, read client-side. */
    private final Map<UUID, String> playerNames = new HashMap<>();

    /** Party UUID → party name cache for ally/enemy display. Populated server-side before sync. */
    private final Map<UUID, String> allyPartyNames = new HashMap<>();

    private final Map<UUID, String> enemyPartyNames = new HashMap<>();

    private final Map<UUID, Long> invites = new HashMap<>();

    public Party(UUID partyId, String name, long createdAt) {
        this.partyId = partyId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // --- Members ---

    public Map<UUID, PartyRole> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public List<UUID> getMemberUUIDs() {
        return Collections.unmodifiableList(new ArrayList<>(members.keySet()));
    }

    @Nullable
    public PartyRole getRole(UUID uuid) {
        return members.get(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public void addMember(UUID uuid, PartyRole role) {
        members.put(uuid, role);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public void setRole(UUID uuid, PartyRole role) {
        if (!members.containsKey(uuid)) return;
        if (role == PartyRole.OWNER) {
            for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
                if (entry.getValue() == PartyRole.OWNER) {
                    entry.setValue(PartyRole.ADMIN);
                    break;
                }
            }
        }
        members.put(uuid, role);
    }

    @Nullable
    public UUID getOwner() {
        for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
            if (entry.getValue() == PartyRole.OWNER) {
                return entry.getKey();
            }
        }
        return null;
    }

    // --- Trust level settings ---

    public TrustLevel getTrustLevel(TrustAction action) {
        return requiredTrustLevels.getOrDefault(action, action.getDefaultLevel());
    }

    public void setTrustLevel(TrustAction action, TrustLevel level) {
        requiredTrustLevels.put(action, level);
    }

    public TrustLevel getFakePlayerTrustLevel() {
        return fakePlayerTrustLevel;
    }

    public void setFakePlayerTrustLevel(TrustLevel level) {
        this.fakePlayerTrustLevel = level;
    }

    public boolean protectsExplosions() {
        return protectExplosions;
    }

    public void setProtectExplosions(boolean protect) {
        this.protectExplosions = protect;
    }

    /**
     * Resolves a player's effective trust level in this party.
     * Allies and enemies are now keyed by party UUID, not player UUID.
     *
     * @return the trust level, or {@code null} if the player's party is an enemy (absolute deny).
     */
    @Nullable
    public TrustLevel getEffectiveTrustLevel(UUID playerUUID) {
        PartyRole role = members.get(playerUUID);
        if (role != null) return role.toTrustLevel();

        // For non-members, resolve their party UUID and check allies/enemies
        Party playerParty = PartyManagerData.getInstance().getPartyByPlayer(playerUUID);
        UUID playerPartyId = playerParty != null ? playerParty.getPartyId() : null;

        if (playerPartyId != null && enemies.contains(playerPartyId)) return null;
        if (playerPartyId != null && allies.contains(playerPartyId)) return TrustLevel.ALLY;
        return TrustLevel.NONE;
    }

    // --- Allies ---

    public Set<UUID> getAllies() {
        return Collections.unmodifiableSet(allies);
    }

    public boolean isAlly(UUID uuid) {
        return allies.contains(uuid);
    }

    public void addAlly(UUID uuid) {
        allies.add(uuid);
        enemies.remove(uuid);
    }

    public void removeAlly(UUID uuid) {
        allies.remove(uuid);
    }

    // --- Enemies ---

    public Set<UUID> getEnemies() {
        return Collections.unmodifiableSet(enemies);
    }

    public boolean isEnemy(UUID uuid) {
        return enemies.contains(uuid);
    }

    public void addEnemy(UUID uuid) {
        enemies.add(uuid);
        allies.remove(uuid);
    }

    public void removeEnemy(UUID uuid) {
        enemies.remove(uuid);
    }

    // --- Party metadata ---

    public boolean isFreeToJoin() {
        return freeToJoin;
    }

    public void setFreeToJoin(boolean freeToJoin) {
        this.freeToJoin = freeToJoin;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // --- Player name cache ---

    /**
     * Returns the cached display name for a player, or {@code null} if unknown.
     * Populated by {@link #resolvePlayerNames()} on the server before sync.
     */
    @Nullable
    public String getPlayerName(UUID uuid) {
        return playerNames.get(uuid);
    }

    /** Sets a cached display name for a player UUID. */
    public void setPlayerName(UUID uuid, String name) {
        playerNames.put(uuid, name);
    }

    /**
     * Resolves display names for all known UUIDs (members) and party names for
     * allies/enemies. Call this server-side before serializing for client sync.
     */
    public void resolvePlayerNames() {
        playerNames.clear();
        for (UUID uuid : members.keySet()) {
            resolveOne(uuid);
        }

        // Resolve ally party names
        allyPartyNames.clear();
        for (UUID partyId : allies) {
            Party allyParty = PartyManagerData.getInstance().getParty(partyId);
            if (allyParty != null) {
                allyPartyNames.put(partyId, allyParty.getName());
            }
        }
        // Resolve enemy party names
        enemyPartyNames.clear();
        for (UUID partyId : enemies) {
            Party enemyParty = PartyManagerData.getInstance().getParty(partyId);
            if (enemyParty != null) {
                enemyPartyNames.put(partyId, enemyParty.getName());
            }
        }
    }

    private void resolveOne(UUID uuid) {
        String cached = net.minecraftforge.common.UsernameCache.getLastKnownUsername(uuid);
        if (cached != null) {
            playerNames.put(uuid, cached);
        }
    }

    public String getAllyPartyName(UUID partyId) {
        return allyPartyNames.getOrDefault(partyId, partyId.toString().substring(0, 8));
    }

    public String getEnemyPartyName(UUID partyId) {
        return enemyPartyNames.getOrDefault(partyId, partyId.toString().substring(0, 8));
    }

    // --- Invites (memory only, not persisted) ---

    public void addInvite(UUID target, long expiryTimestamp) {
        invites.put(target, expiryTimestamp);
    }

    public boolean hasInvite(UUID target) {
        Long expiry = invites.get(target);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            invites.remove(target);
            return false;
        }
        return true;
    }

    public void removeInvite(UUID target) {
        invites.remove(target);
    }

    public void cleanExpiredInvites() {
        long now = System.currentTimeMillis();
        invites.entrySet().removeIf(e -> now > e.getValue());
    }

    // --- NBT helpers ---

    private static NBTTagList uuidSetToNBT(Collection<UUID> uuids) {
        NBTTagList list = new NBTTagList();
        for (UUID uuid : uuids) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("uuid", uuid);
            list.appendTag(tag);
        }
        return list;
    }

    private static List<UUID> uuidSetFromNBT(NBTTagList list) {
        List<UUID> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(list.getCompoundTagAt(i).getUniqueId("uuid"));
        }
        return result;
    }

    // --- NBT ---

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("partyId", partyId);
        tag.setString("name", name);
        tag.setLong("created", createdAt);

        // Members
        NBTTagList memberList = new NBTTagList();
        for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setUniqueId("uuid", entry.getKey());
            memberTag.setString("role", entry.getValue().name());
            memberList.appendTag(memberTag);
        }
        tag.setTag("members", memberList);

        // Trust levels
        NBTTagCompound trustTag = new NBTTagCompound();
        for (Map.Entry<TrustAction, TrustLevel> entry : requiredTrustLevels.entrySet()) {
            trustTag.setString(entry.getKey().getNbtKey(), entry.getValue().name());
        }
        tag.setTag("trustLevels", trustTag);
        tag.setString("fakePlayerTrust", fakePlayerTrustLevel.name());
        tag.setBoolean("protectExplosions", protectExplosions);

        // Allies / Enemies
        tag.setTag("allies", uuidSetToNBT(allies));
        tag.setTag("enemies", uuidSetToNBT(enemies));

        // Metadata
        tag.setBoolean("freeToJoin", freeToJoin);
        tag.setInteger("color", color);
        tag.setString("description", description);

        return tag;
    }

    /**
     * Returns NBT for client sync (includes player name cache).
     * Use this instead of {@link #toNBT()} when sending to clients.
     */
    public NBTTagCompound toSyncNBT() {
        NBTTagCompound tag = toNBT();
        // Player name cache
        if (!playerNames.isEmpty()) {
            NBTTagList nameList = new NBTTagList();
            for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
                NBTTagCompound nameTag = new NBTTagCompound();
                nameTag.setUniqueId("uuid", entry.getKey());
                nameTag.setString("name", entry.getValue());
                nameList.appendTag(nameTag);
            }
            tag.setTag("playerNames", nameList);
        }
        // Ally party names
        if (!allyPartyNames.isEmpty()) {
            NBTTagList allyNameList = new NBTTagList();
            for (Map.Entry<UUID, String> entry : allyPartyNames.entrySet()) {
                NBTTagCompound nameTag = new NBTTagCompound();
                nameTag.setUniqueId("uuid", entry.getKey());
                nameTag.setString("name", entry.getValue());
                allyNameList.appendTag(nameTag);
            }
            tag.setTag("allyPartyNames", allyNameList);
        }
        // Enemy party names
        if (!enemyPartyNames.isEmpty()) {
            NBTTagList enemyNameList = new NBTTagList();
            for (Map.Entry<UUID, String> entry : enemyPartyNames.entrySet()) {
                NBTTagCompound nameTag = new NBTTagCompound();
                nameTag.setUniqueId("uuid", entry.getKey());
                nameTag.setString("name", entry.getValue());
                enemyNameList.appendTag(nameTag);
            }
            tag.setTag("enemyPartyNames", enemyNameList);
        }
        // Pending invites (UUID only, no expiry)
        if (!invites.isEmpty()) {
            cleanExpiredInvites();
            tag.setTag("pendingInvites", uuidSetToNBT(invites.keySet()));
        }
        return tag;
    }

    public static Party fromNBT(NBTTagCompound tag) {
        UUID id;
        if (tag.hasKey("partyIdMost")) {
            // New UUID format
            id = tag.getUniqueId("partyId");
        } else {
            // Old int format - migrate
            int oldId = tag.getInteger("id");
            id = uuidFromIntId(oldId);
        }
        String name = tag.getString("name");
        long created = tag.getLong("created");

        // Validate
        if (name.isEmpty()) {
            // Check if old title exists and use it
            if (tag.hasKey("title") && !tag.getString("title").isEmpty()) {
                name = tag.getString("title");
            } else {
                name = "Party " + id.toString().substring(0, 8);
            }
            ModLog.IO.warn("Party {} has empty name, using default", id);
        }

        Party party = new Party(id, name, created);

        // Members
        NBTTagList memberList = tag.getTagList("members", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound memberTag = memberList.getCompoundTagAt(i);
            UUID uuid = memberTag.getUniqueId("uuid");
            PartyRole role = PartyRole.fromName(memberTag.getString("role"));
            party.addMember(uuid, role);
        }

        // Trust levels
        if (tag.hasKey("trustLevels")) {
            NBTTagCompound trustTag = tag.getCompoundTag("trustLevels");
            for (TrustAction action : TrustAction.values()) {
                if (trustTag.hasKey(action.getNbtKey())) {
                    party.setTrustLevel(action, TrustLevel.fromName(trustTag.getString(action.getNbtKey())));
                }
            }
        }
        if (tag.hasKey("fakePlayerTrust")) {
            party.setFakePlayerTrustLevel(TrustLevel.fromName(tag.getString("fakePlayerTrust")));
        } else if (tag.hasKey("allowFakePlayers")) {
            // Migration from old boolean format
            party.setFakePlayerTrustLevel(tag.getBoolean("allowFakePlayers") ? TrustLevel.ALLY : TrustLevel.NONE);
        }
        if (tag.hasKey("protectExplosions")) {
            party.setProtectExplosions(tag.getBoolean("protectExplosions"));
        }

        // Allies / Enemies
        for (UUID uuid : uuidSetFromNBT(tag.getTagList("allies", Constants.NBT.TAG_COMPOUND))) {
            party.addAlly(uuid);
        }
        for (UUID uuid : uuidSetFromNBT(tag.getTagList("enemies", Constants.NBT.TAG_COMPOUND))) {
            party.addEnemy(uuid);
        }

        // Ally party names (sync only)
        if (tag.hasKey("allyPartyNames")) {
            NBTTagList allyNameList = tag.getTagList("allyPartyNames", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < allyNameList.tagCount(); i++) {
                NBTTagCompound nameTag = allyNameList.getCompoundTagAt(i);
                party.allyPartyNames.put(nameTag.getUniqueId("uuid"), nameTag.getString("name"));
            }
        }
        if (tag.hasKey("enemyPartyNames")) {
            NBTTagList enemyNameList = tag.getTagList("enemyPartyNames", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < enemyNameList.tagCount(); i++) {
                NBTTagCompound nameTag = enemyNameList.getCompoundTagAt(i);
                party.enemyPartyNames.put(nameTag.getUniqueId("uuid"), nameTag.getString("name"));
            }
        }

        // Metadata
        if (tag.hasKey("freeToJoin")) party.setFreeToJoin(tag.getBoolean("freeToJoin"));
        if (tag.hasKey("color")) party.setColor(tag.getInteger("color"));
        if (tag.hasKey("description")) party.setDescription(tag.getString("description"));

        // Player name cache
        if (tag.hasKey("playerNames")) {
            NBTTagList nameList = tag.getTagList("playerNames", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < nameList.tagCount(); i++) {
                NBTTagCompound nameTag = nameList.getCompoundTagAt(i);
                party.setPlayerName(nameTag.getUniqueId("uuid"), nameTag.getString("name"));
            }
        }

        // Pending invites (sync only, no expiry on client)
        if (tag.hasKey("pendingInvites")) {
            for (UUID uuid : uuidSetFromNBT(tag.getTagList("pendingInvites", Constants.NBT.TAG_COMPOUND))) {
                party.addInvite(uuid, Long.MAX_VALUE);
            }
        }

        return party;
    }
}
