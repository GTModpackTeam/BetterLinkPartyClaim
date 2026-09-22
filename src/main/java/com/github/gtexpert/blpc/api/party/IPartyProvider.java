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
 * All mutation methods identify the player's party by their UUID.
 * Two implementations exist: {@code DefaultPartyProvider} (self-managed)
 * and {@code BQuPartyProvider} (delegates to BetterQuesting).
 */
public interface IPartyProvider {

    // --- Query ---

    boolean areInSameParty(UUID playerA, UUID playerB);

    /** Returns the party name, or {@code null} if the player has no party. */
    @Nullable
    String getPartyName(UUID playerUUID);

    List<UUID> getPartyMembers(UUID playerUUID);

    /** Returns the role name, or {@code null} if not in a party. */
    @Nullable
    String getRole(UUID playerUUID);

    /**
     * Returns a stable storage-key identifier for the player's party, or {@code null}.
     * Unlike the display-facing {@link Party} objects synced to clients, this id is
     * guaranteed identical for every member of the same real party.
     */
    @Nullable
    default UUID getPartyId(UUID playerUUID) {
        return null;
    }

    /**
     * Returns the fully-populated {@link Party} for authoritative server-side checks.
     * Unlike reading a local {@code PartyManagerData} record directly, this reflects
     * live membership even for members who joined through a delegate's own UI (e.g.
     * BQu's native party screen). Returns {@code null} if the player has no party.
     */
    @Nullable
    default Party getEffectiveParty(UUID playerUUID) {
        return null;
    }

    /** Convenience shortcut for {@code getEffectiveParty(uuid).getOwner()}. */
    @Nullable
    default UUID getOwner(UUID playerUUID) {
        var party = getEffectiveParty(playerUUID);
        return party != null ? party.getOwner() : null;
    }

    /** Returns the party with the given name, or {@code null}. */
    @Nullable
    default Party findByName(String name) {
        return null;
    }

    default List<String> allPartyNames() {
        return Collections.emptyList();
    }

    /** Returns all known {@link Party} objects. Default: empty. */
    default List<Party> getAllParties() {
        return Collections.emptyList();
    }

    default List<Party> pendingInvitesFor(UUID playerUUID) {
        return Collections.emptyList();
    }

    // --- Mutation ---

    /** Returns false if the player already has a party. */
    boolean createParty(EntityPlayerMP player, String name);

    boolean disbandParty(EntityPlayerMP player);

    boolean renameParty(EntityPlayerMP player, String newName);

    boolean invitePlayer(EntityPlayerMP inviter, String targetUsername);

    boolean acceptInvite(EntityPlayerMP player, UUID partyId);

    /** Returns false if the actor is the owner trying to leave. */
    boolean kickOrLeave(EntityPlayerMP actor, String targetUsername);

    boolean changeRole(EntityPlayerMP actor, String targetUsername, String newRole);

    default boolean hasNativeParty(UUID playerUUID) {
        return getPartyName(playerUUID) != null;
    }

    /**
     * Returns true if the player is in a BQu-linked party, so mutations should
     * route through this provider instead of the self-managed fallback.
     */
    default boolean isLinkedParty(UUID playerUUID) {
        return false;
    }

    /**
     * Ensures a native party exists for the owner with the same members as the
     * given BLPC party. Returns {@code true} if the native party is ready for linking.
     */
    default boolean ensureNativePartyWithMembers(EntityPlayerMP owner,
                                                 Party blpcParty) {
        return hasNativeParty(owner.getUniqueID());
    }

    void syncToAll();

    default void syncToPlayer(EntityPlayerMP player) {}

    default long countClaims(UUID partyId) {
        return 0;
    }

    NBTTagCompound serializeForClient();
}
