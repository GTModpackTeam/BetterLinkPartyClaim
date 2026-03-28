package com.github.gtexpert.blpc.api.party;

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
 * <li>{@code BQPartyProvider} — delegates to BetterQuesting's party system with self-managed fallback</li>
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

    /** Syncs party data to all connected clients after mutations. */
    void syncToAll();

    /** Returns NBT data representing all parties for client-side cache. */
    NBTTagCompound serializeForClient();
}
