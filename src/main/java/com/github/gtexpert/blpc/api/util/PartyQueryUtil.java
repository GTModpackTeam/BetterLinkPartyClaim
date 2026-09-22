package com.github.gtexpert.blpc.api.util;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.UsernameCache;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.party.PartyRole;

/**
 * Public query utilities for addon authors — thin delegation layer over the active
 * {@link IPartyProvider} so callers never reference internal {@code PartyManagerData}
 * directly.
 * <p>
 * All methods are safe to call from the server thread after world load. Calling from the
 * client thread or before {@code FMLServerStartedEvent} will return empty/null results.
 *
 * <h3>Typical addon usage</h3>
 * 
 * <pre>
 * 
 * {
 *     &#64;code
 *     // Find a party by display name
 *     Party party = PartyQueryUtil.findByName("MyCrew");
 *
 *     // Resolve a UUID to a display name for chat output
 *     String name = PartyQueryUtil.resolveName(server, party, playerUUID);
 * }
 * </pre>
 */
public final class PartyQueryUtil {

    private PartyQueryUtil() {}

    /** Returns the party with the given display name, or {@code null} if none exists. */
    @Nullable
    public static Party findByName(String name) {
        return PartyProviderRegistry.get().findByName(name);
    }

    /** Returns the display names of all known parties. */
    public static List<String> allPartyNames() {
        return PartyProviderRegistry.get().allPartyNames();
    }

    /** Returns all parties that have a pending invite for the given player UUID. */
    public static List<Party> pendingInvitesFor(UUID playerUUID) {
        return PartyProviderRegistry.get().pendingInvitesFor(playerUUID);
    }

    /**
     * Resolves a display name for a UUID using multiple fallback sources:
     * online player → party name cache → global UsernameCache → UUID prefix.
     *
     * @param server the running server instance
     * @param party  the party the UUID belongs to (used for the cached name fallback); may be {@code null}
     * @param uuid   the player UUID to resolve
     * @return a human-readable name, never {@code null}
     */
    public static String resolveName(MinecraftServer server, @Nullable Party party, UUID uuid) {
        var online = server.getPlayerList().getPlayerByUUID(uuid);
        if (online != null) return online.getName();
        String cached = party != null ? party.getPlayerName(uuid) : null;
        if (cached != null) return cached;
        String global = UsernameCache.getLastKnownUsername(uuid);
        return global != null ? global : uuid.toString().substring(0, 8);
    }

    /** Returns the active {@link IPartyProvider} — shorthand for {@code PartyProviderRegistry.get()}. */
    public static IPartyProvider provider() {
        return PartyProviderRegistry.get();
    }

    /** Returns the owner UUID of the given player's party, or {@code null} if no party. */
    @Nullable
    public static UUID getOwner(UUID playerUUID) {
        return PartyProviderRegistry.get().getOwner(playerUUID);
    }

    /**
     * Returns whether the given player has OWNER or ADMIN (moderator-level) role
     * in their party. Shorthand for the repeated pattern
     * {@code role == PartyRole.OWNER || role == PartyRole.ADMIN}.
     *
     * @param playerUUID the player to check
     * @return {@code true} if the player is OWNER or ADMIN in their party
     */
    public static boolean isOwnerOrMod(UUID playerUUID) {
        var provider = PartyProviderRegistry.get();
        var role = PartyRole.fromName(provider.getRole(playerUUID));
        return role == PartyRole.OWNER || role == PartyRole.ADMIN;
    }
}
