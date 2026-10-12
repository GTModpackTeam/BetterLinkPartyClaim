package com.github.gtexpert.blpc.api.party;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a party with members, roles, trust settings, and invitations.
 * <p>
 * Persisted to {@code world/betterlink/pc/parties/<id>.dat} by the save handler.
 * Invitations are persisted to NBT but reconstructed with no expiry on load.
 */
public class Party {

    private static final Logger LOG = LogManager.getLogger("blpc/Party");

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
    // EnumDyeColor.BLUE.colorValue — inlined because EnumDyeColor.getColorValue() is @SideOnly(CLIENT).
    private int color = 3949738;
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
     *
     * @param playerUUID    the player to check
     * @param playerPartyId the player's own party UUID, or {@code null} if unpartnered
     * @return the trust level, or {@code null} if the player's party is an enemy (absolute deny)
     */
    @Nullable
    public TrustLevel getEffectiveTrustLevel(UUID playerUUID, @Nullable UUID playerPartyId) {
        PartyRole role = members.get(playerUUID);
        if (role != null) return role.toTrustLevel();

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

    public boolean canAddMember() {
        return maxMembers == 0 || members.size() < maxMembers;
    }

    /** Returns {@code memberCount * perPlayerLimit}. Pass {@code ModConfig.claims.maxClaimsPerPlayer}. */
    public int sumClaimLimit(int perPlayerLimit) {
        return members.size() * perPlayerLimit;
    }

    /** Returns {@code memberCount * perPlayerLimit}. Pass {@code ModConfig.claims.maxForceLoadsPerPlayer}. */
    public int sumForceLoadLimit(int perPlayerLimit) {
        return members.size() * perPlayerLimit;
    }

    public int countOnlineMembers(MinecraftServer server) {
        int count = 0;
        for (UUID uuid : members.keySet()) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(uuid);
            if (p != null) count++;
        }
        return count;
    }

    /**
     * Copies all protection and relation settings from {@code source} into this party.
     * Used by {@code BQuPartyProvider} when building the client-sync view from the
     * owner's self-managed party.
     */
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
     * Populated by {@link #resolvePlayerNames(Function)} on the server before sync.
     */
    @Nullable
    public String getPlayerName(UUID uuid) {
        return playerNames.get(uuid);
    }

    /**
     * Resolves a member's UUID from their cached display name — case-insensitive, matched
     * against {@link #getMembers()} only. Used to target offline members by name (e.g. kick)
     * since {@code UsernameCache}/{@code playerNames} persist across logout, unlike the live
     * player list.
     */
    @Nullable
    public UUID findMemberByName(String name) {
        for (UUID uuid : members.keySet()) {
            String cached = playerNames.get(uuid);
            if (cached != null && cached.equalsIgnoreCase(name)) {
                return uuid;
            }
        }
        return null;
    }

    /**
     * Like {@link #findMemberByName(String)}, but falls back to the live player list when the
     * name isn't in the cache yet (e.g. a member who joined this tick, before the next
     * {@link #resolvePlayerNames(Function)} sync pass). Preferred over calling
     * {@code server.getPlayerList().getPlayerByUsername()} directly, since that alone can't
     * target offline members at all.
     */
    @Nullable
    public UUID findMemberByUsername(MinecraftServer server, String name) {
        UUID cached = findMemberByName(name);
        if (cached != null) return cached;
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        return online != null && members.containsKey(online.getUniqueID()) ? online.getUniqueID() : null;
    }

    /**
     * Resolves display names for all known UUIDs (members) and party names for
     * allies/enemies. Call this server-side before serializing for client sync.
     *
     * @param partyLookup function to resolve a party UUID to a {@link Party}, or {@code null}
     */
    public void resolvePlayerNames(Function<UUID, Party> partyLookup) {
        playerNames.clear();
        for (UUID uuid : members.keySet()) {
            resolveOne(uuid);
        }
        resolvePartyNames(allies, allyPartyNames, partyLookup);
        resolvePartyNames(enemies, enemyPartyNames, partyLookup);
    }

    private void resolvePartyNames(Set<UUID> ids, Map<UUID, String> target,
                                   Function<UUID, Party> partyLookup) {
        target.clear();
        for (UUID pId : ids) {
            Party p = partyLookup.apply(pId);
            if (p != null) target.put(pId, p.getName());
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

    private static NBTTagList uuidNameMapToNBT(Map<UUID, String> map) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, String> entry : map.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
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
        for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setUniqueId("uuid", entry.getKey());
            memberTag.setString("role", entry.getValue().name());
            memberList.appendTag(memberTag);
        }
        tag.setTag("members", memberList);

        NBTTagCompound trustTag = new NBTTagCompound();
        for (Map.Entry<TrustAction, TrustLevel> entry : requiredTrustLevels.entrySet()) {
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

    @Nullable
    public static Party fromNBT(NBTTagCompound tag) {
        if (tag == null) {
            LOG.error("Party NBT tag is null, skipping corrupt entry");
            return null;
        }
        UUID id = tag.getUniqueId("partyId");
        if (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0) {
            LOG.error("Party NBT missing 'partyId' key, skipping corrupt entry");
            return null;
        }
        String name = tag.getString("name");
        long created = tag.getLong("created");

        if (name.isEmpty()) {
            name = "Party " + id.toString().substring(0, 8);
            LOG.warn("Party {} has empty name, using default", id);
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
            for (UUID uuid : uuidSetFromNBT(
                    tag.getTagList("pendingInvites", Constants.NBT.TAG_COMPOUND))) {
                party.addInvite(uuid, Long.MAX_VALUE);
            }
        }

        return party;
    }
}
