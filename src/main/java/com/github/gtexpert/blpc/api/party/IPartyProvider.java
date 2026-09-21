package com.github.gtexpert.blpc.api.party;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Service Provider Interface for party management.
 * <p>
 * Implementations handle both query and mutation operations.
 * All mutation methods identify the player's party by their UUID,
 * eliminating the need for explicit party ID parameters.
 * <p>
 * Two implementations exist:
 * <ul>
 * <li>{@code DefaultPartyProvider} — self-managed via {@code PartyManagerData}</li>
 * <li>{@code BQuPartyProvider} — delegates to BetterQuesting's party system with self-managed fallback</li>
 * </ul>
 */
public interface IPartyProvider {

    // --- Query ---

    /** Checks if two players belong to the same party. */
    boolean areInSameParty(UUID playerA, UUID playerB);

    /** Returns the party name for the player, or null if not in a party. */
    @Nullable
    String getPartyName(UUID playerUUID);

    /** Returns member UUIDs of the player's party, or empty if no party. */
    List<UUID> getPartyMembers(UUID playerUUID);

    /** Returns the player's role name (e.g. "OWNER","ADMIN","MEMBER"), or null. */
    @Nullable
    String getRole(UUID playerUUID);

    /**
     * Returns a stable identifier for the player's party, or {@code null} if they have no party.
     * Unlike the display-facing {@link Party} objects synced to clients (whose id can vary by
     * which member's data happened to seed it — see {@code BQuPartyProvider#serializeForClient}),
     * this id is guaranteed identical for every member of the same real party, making it safe as
     * a server-side storage key (e.g. {@code WaypointManagerData}) even for members who have
     * never had their own BLPC-side party record created.
     */
    @Nullable
    default UUID getPartyId(UUID playerUUID) {
        return null;
    }

    /**
     * Returns the player's fully-populated {@link Party} (members, trust levels, allies/enemies,
     * claim-limit settings) for authoritative server-side checks — chunk protection trust
     * resolution, additive claim/force-load limits, and chunk-transit relation notifications.
     * <p>
     * Unlike reading a self-managed {@code PartyManagerData} record directly, this is guaranteed
     * to reflect live membership even for a member who joined entirely through a delegate's own
     * UI (e.g. BQu's native party screen) and so has no BLPC-side {@link Party} record of their
     * own — see {@code BQuPartyProvider#getEffectiveParty}. Returns {@code null} if the player has
     * no party.
     */
    @Nullable
    default Party getEffectiveParty(UUID playerUUID) {
        return null;
    }

    /**
     * Returns the owner UUID of the player's party, or {@code null} if they have no party.
     * This is a convenience shortcut for {@code getEffectiveParty(uuid).getOwner()}.
     */
    @Nullable
    default UUID getOwner(UUID playerUUID) {
        var party = getEffectiveParty(playerUUID);
        return party != null ? party.getOwner() : null;
    }

    /** Returns the party with the given name, or null if none exists. */
    @Nullable
    default Party findByName(String name) {
        return null;
    }

    /** Returns the names of all known parties. */
    default List<String> allPartyNames() {
        return Collections.emptyList();
    }

    /**
     * Returns all known {@link Party} objects, or an empty list.
     * <p>
     * Default returns empty — self-managed provider overrides this to return
     * {@code PartyManagerData.getInstance().getAllParties()}.
     */
    default List<Party> getAllParties() {
        return Collections.emptyList();
    }

    /** Returns all parties that have a pending invite for the given player. */
    default List<Party> pendingInvitesFor(UUID playerUUID) {
        return Collections.emptyList();
    }

    // --- Mutation (player UUID identifies the party) ---

    /** Creates a new party with the player as OWNER. Returns false if player already has a party. */
    boolean createParty(EntityPlayerMP player, String name);

    /** Disbands the player's party. Releases all members' chunk claims. */
    boolean disbandParty(EntityPlayerMP player);

    /** Renames the player's party. Requires OWNER role. */
    boolean renameParty(EntityPlayerMP player, String newName);

    /** Invites a player by username. Requires ADMIN+ role. */
    boolean invitePlayer(EntityPlayerMP inviter, String targetUsername);

    /** Accepts a pending invite to the specified party. */
    boolean acceptInvite(EntityPlayerMP player, UUID partyId);

    /** Kicks a player or leaves the party. Owner cannot leave without transferring ownership. */
    boolean kickOrLeave(EntityPlayerMP actor, String targetUsername);

    /** Changes a member's role. Requires OWNER role. */
    boolean changeRole(EntityPlayerMP actor, String targetUsername, String newRole);

    /** Returns true if the player is in a native (non-fallback) party managed by this provider. */
    default boolean hasNativeParty(UUID playerUUID) {
        return getPartyName(playerUUID) != null;
    }

    /**
     * Returns true if this player's current native party has been linked to BLPC (i.e. any of
     * its members opted in via {@code ACTION_TOGGLE_BQU_LINK}), so mutations for this player
     * should route through this provider instead of the self-managed fallback.
     * <p>
     * Unlike a per-player flag snapshotted at link time, this reflects the party's <em>current</em>
     * membership: a player who joins an already-linked native party afterward (e.g. through BQu's
     * own party screen, entirely outside BLPC) is recognized immediately, without requiring a
     * separate propagation step every time membership changes elsewhere.
     */
    default boolean isLinkedParty(UUID playerUUID) {
        return false;
    }

    /**
     * Ensures a native party exists for the owner with the same members as the
     * given BLPC party. Creates the native party if absent, adds missing
     * members, and maps roles. Returns {@code true} if the native party is
     * ready for linking after this call.
     */
    default boolean ensureNativePartyWithMembers(EntityPlayerMP owner,
                                                 Party blpcParty) {
        return hasNativeParty(owner.getUniqueID());
    }

    /** Syncs party data to all connected clients after mutations. */
    void syncToAll();

    /**
     * Sends the current authoritative party snapshot to a single player.
     * Used to roll back optimistic client mutations when an action is rejected
     * server-side — broadcasting via {@link #syncToAll} would be wasteful when
     * only the actor's local cache is divergent.
     * <p>
     * Implementations that support per-player sync should override this.
     */
    default void syncToPlayer(EntityPlayerMP player) {}

    /**
     * Returns the number of chunk claims owned by members of the given party.
     * <p>
     * Default implementation returns 0 (self-managed parties rely on
     * {@link com.github.gtexpert.blpc.common.chunk.ChunkManagerData} directly).
     * Override in providers that need cross-party claim counting.
     */
    default long countClaims(UUID partyId) {
        return 0;
    }

    /** Returns NBT data representing all parties for client-side cache. */
    NBTTagCompound serializeForClient();
}
