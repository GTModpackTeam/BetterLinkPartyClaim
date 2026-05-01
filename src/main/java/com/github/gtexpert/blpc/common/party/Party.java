package com.github.gtexpert.blpc.common.party;

import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;

import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.ModLog;

/**
 * Represents a party with members, roles, trust settings, and invitations.
 * <p>
 * Persisted to {@code world/betterlink/pc/parties/<id>.dat} via
 * {@link com.github.gtexpert.blpc.common.BLPCSaveHandler}.
 * Invitations are persisted to NBT but reconstructed with no expiry on load.
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

    private final Map<TrustAction, TrustLevel> requiredTrustLevels = new EnumMap<>(TrustAction.class);
    private TrustLevel fakePlayerTrustLevel = TrustLevel.ALLY;
    private boolean protectExplosions = true;

    private final Set<UUID> allies = new LinkedHashSet<>();
    private final Set<UUID> enemies = new LinkedHashSet<>();

    private boolean freeToJoin = false;
    private int color = EnumDyeColor.BLUE.getColorValue();
    private String description = "";
    /** Maximum members allowed. 0 = unlimited. */
    private int maxMembers = 0;

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

    /**
     * Sets a member's role. When assigning OWNER, the previous owner is
     * automatically demoted to ADMIN (not MEMBER) to retain moderation rights.
     */
    public void setRole(UUID uuid, PartyRole role) {
        if (!members.containsKey(uuid)) return;
        if (role == PartyRole.OWNER) {
            for (var entry : members.entrySet()) {
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
        for (var entry : members.entrySet()) {
            if (entry.getValue() == PartyRole.OWNER) {
                return entry.getKey();
            }
        }
        return null;
    }

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

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(0, maxMembers);
    }

    /** Returns {@code true} if a new member can join (unlimited or below cap). */
    public boolean canAddMember() {
        return maxMembers == 0 || members.size() < maxMembers;
    }

    /**
     * Copies all protection and relation settings from {@code source} into this party.
     * Used by {@link com.github.gtexpert.blpc.integration.bqu.BQPartyProvider} when
     * building the client-sync view from the owner's self-managed party.
     */
    public int sumClaimLimit() {
        return members.size() * ModConfig.claims.maxClaimsPerPlayer;
    }

    public int sumForceLoadLimit() {
        return members.size() * ModConfig.claims.maxForceLoadsPerPlayer;
    }

    public int countOnlineMembers(MinecraftServer server) {
        int count = 0;
        for (UUID uuid : members.keySet()) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(uuid);
            if (p != null) count++;
        }
        return count;
    }

    public void copySettingsFrom(Party source) {
        this.description = source.description;
        this.color = source.color;
        this.freeToJoin = source.freeToJoin;
        this.maxMembers = source.maxMembers;
        this.fakePlayerTrustLevel = source.fakePlayerTrustLevel;
        this.protectExplosions = source.protectExplosions;
        for (TrustAction ta : TrustAction.values()) {
            setTrustLevel(ta, source.getTrustLevel(ta));
        }
        allies.clear();
        allies.addAll(source.getAllies());
        enemies.clear();
        enemies.addAll(source.getEnemies());
    }

    /**
     * Returns the cached display name for a player, or {@code null} if unknown.
     * Populated by {@link #resolvePlayerNames()} on the server before sync.
     */
    @Nullable
    public String getPlayerName(UUID uuid) {
        return playerNames.get(uuid);
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
        resolvePartyNames(allies, allyPartyNames);
        resolvePartyNames(enemies, enemyPartyNames);
    }

    private void resolvePartyNames(Set<UUID> ids, Map<UUID, String> target) {
        target.clear();
        for (UUID partyId : ids) {
            Party p = PartyManagerData.getInstance().getParty(partyId);
            if (p != null) target.put(partyId, p.getName());
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

    private static NBTTagList uuidSetToNBT(Collection<UUID> uuids) {
        var list = new NBTTagList();
        for (UUID uuid : uuids) {
            var tag = new NBTTagCompound();
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

    private static NBTTagList uuidNameMapToNBT(Map<UUID, String> map) {
        var list = new NBTTagList();
        for (var entry : map.entrySet()) {
            var tag = new NBTTagCompound();
            tag.setUniqueId("uuid", entry.getKey());
            tag.setString("name", entry.getValue());
            list.appendTag(tag);
        }
        return list;
    }

    private static void uuidNameMapFromNBT(NBTTagList list, Map<UUID, String> target) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound nameTag = list.getCompoundTagAt(i);
            target.put(nameTag.getUniqueId("uuid"), nameTag.getString("name"));
        }
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("partyId", partyId);
        tag.setString("name", name);
        tag.setLong("created", createdAt);

        NBTTagList memberList = new NBTTagList();
        for (var entry : members.entrySet()) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setUniqueId("uuid", entry.getKey());
            memberTag.setString("role", entry.getValue().name());
            memberList.appendTag(memberTag);
        }
        tag.setTag("members", memberList);

        NBTTagCompound trustTag = new NBTTagCompound();
        for (var entry : requiredTrustLevels.entrySet()) {
            trustTag.setString(entry.getKey().getNbtKey(), entry.getValue().name());
        }
        tag.setTag("trustLevels", trustTag);
        tag.setString("fakePlayerTrust", fakePlayerTrustLevel.name());
        tag.setBoolean("protectExplosions", protectExplosions);

        tag.setTag("allies", uuidSetToNBT(allies));
        tag.setTag("enemies", uuidSetToNBT(enemies));

        tag.setBoolean("freeToJoin", freeToJoin);
        tag.setInteger("color", color);
        tag.setString("description", description);
        tag.setInteger("maxMembers", maxMembers);

        return tag;
    }

    /**
     * Returns NBT for client sync (includes player name cache).
     * Use this instead of {@link #toNBT()} when sending to clients.
     */
    public NBTTagCompound toSyncNBT() {
        NBTTagCompound tag = toNBT();
        if (!playerNames.isEmpty()) {
            tag.setTag("playerNames", uuidNameMapToNBT(playerNames));
        }
        if (!allyPartyNames.isEmpty()) {
            tag.setTag("allyPartyNames", uuidNameMapToNBT(allyPartyNames));
        }
        if (!enemyPartyNames.isEmpty()) {
            tag.setTag("enemyPartyNames", uuidNameMapToNBT(enemyPartyNames));
        }
        if (!invites.isEmpty()) {
            cleanExpiredInvites();
            tag.setTag("pendingInvites", uuidSetToNBT(invites.keySet()));
        }
        return tag;
    }

    public static Party fromNBT(NBTTagCompound tag) {
        UUID id = tag.getUniqueId("partyId");
        String name = tag.getString("name");
        long created = tag.getLong("created");

        if (name.isEmpty()) {
            name = "Party " + id.toString().substring(0, 8);
            ModLog.IO.warn("Party {} has empty name, using default", id);
        }

        Party party = new Party(id, name, created);

        NBTTagList memberList = tag.getTagList("members", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound memberTag = memberList.getCompoundTagAt(i);
            UUID uuid = memberTag.getUniqueId("uuid");
            PartyRole role = PartyRole.fromName(memberTag.getString("role"));
            party.addMember(uuid, role);
        }

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
        }
        if (tag.hasKey("protectExplosions")) {
            party.setProtectExplosions(tag.getBoolean("protectExplosions"));
        }

        for (UUID uuid : uuidSetFromNBT(tag.getTagList("allies", Constants.NBT.TAG_COMPOUND))) {
            party.addAlly(uuid);
        }
        for (UUID uuid : uuidSetFromNBT(tag.getTagList("enemies", Constants.NBT.TAG_COMPOUND))) {
            party.addEnemy(uuid);
        }

        if (tag.hasKey("allyPartyNames")) {
            uuidNameMapFromNBT(tag.getTagList("allyPartyNames", Constants.NBT.TAG_COMPOUND),
                    party.allyPartyNames);
        }
        if (tag.hasKey("enemyPartyNames")) {
            uuidNameMapFromNBT(tag.getTagList("enemyPartyNames", Constants.NBT.TAG_COMPOUND),
                    party.enemyPartyNames);
        }

        if (tag.hasKey("freeToJoin")) party.setFreeToJoin(tag.getBoolean("freeToJoin"));
        if (tag.hasKey("color")) party.setColor(tag.getInteger("color"));
        if (tag.hasKey("description")) party.setDescription(tag.getString("description"));
        if (tag.hasKey("maxMembers")) party.setMaxMembers(tag.getInteger("maxMembers"));

        if (tag.hasKey("playerNames")) {
            uuidNameMapFromNBT(tag.getTagList("playerNames", Constants.NBT.TAG_COMPOUND),
                    party.playerNames);
        }

        // Sync-only: client-side invites have no expiry timestamp.
        if (tag.hasKey("pendingInvites")) {
            for (UUID uuid : uuidSetFromNBT(tag.getTagList("pendingInvites", Constants.NBT.TAG_COMPOUND))) {
                party.addInvite(uuid, Long.MAX_VALUE);
            }
        }

        return party;
    }
}
