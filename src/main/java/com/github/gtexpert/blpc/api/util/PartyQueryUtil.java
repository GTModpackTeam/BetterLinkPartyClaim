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
 * {@link IPartyProvider} so callers never reference internal packages directly.
 */
public final class PartyQueryUtil {

    private PartyQueryUtil() {}

    @Nullable
    public static Party findByName(String name) {
        return PartyProviderRegistry.get().findByName(name);
    }

    public static List<String> allPartyNames() {
        return PartyProviderRegistry.get().allPartyNames();
    }

    public static List<Party> pendingInvitesFor(UUID playerUUID) {
        return PartyProviderRegistry.get().pendingInvitesFor(playerUUID);
    }

    /**
     * Resolves a display name for a UUID: online player → party cache → global username cache → UUID prefix.
     */
    public static String resolveName(MinecraftServer server, @Nullable Party party, UUID uuid) {
        var online = server.getPlayerList().getPlayerByUUID(uuid);
        if (online != null) return online.getName();
        String cached = party != null ? party.getPlayerName(uuid) : null;
        if (cached != null) return cached;
        String global = UsernameCache.getLastKnownUsername(uuid);
        return global != null ? global : uuid.toString().substring(0, 8);
    }

    /** Shorthand for {@code PartyProviderRegistry.get()}. */
    public static IPartyProvider provider() {
        return PartyProviderRegistry.get();
    }

    @Nullable
    public static UUID getOwner(UUID playerUUID) {
        return PartyProviderRegistry.get().getOwner(playerUUID);
    }

    /** Returns true if the player has OWNER or ADMIN role in their party. */
    public static boolean isOwnerOrMod(UUID playerUUID) {
        var provider = PartyProviderRegistry.get();
        var role = PartyRole.fromName(provider.getRole(playerUUID));
        return role == PartyRole.OWNER || role == PartyRole.ADMIN;
    }
}
