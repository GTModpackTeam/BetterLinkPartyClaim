package com.github.gtexpert.blpc.api.party;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Registry for the active {@link IPartyProvider}.
 * <p>
 * {@code CoreModule} registers {@code DefaultPartyProvider} by default.
 * {@code BQuModule} replaces it with {@code BQPartyProvider} when BetterQuesting is present.
 * <p>
 * Also provides an optional native screen opener for BQu's party management UI.
 */
public class PartyProviderRegistry {

    private static final IPartyProvider NO_OP = new IPartyProvider() {

        @Override
        public boolean areInSameParty(UUID playerA, UUID playerB) {
            return false;
        }

        @Override
        @Nullable
        public String getPartyName(UUID playerUUID) {
            return null;
        }

        @Override
        public List<UUID> getPartyMembers(UUID playerUUID) {
            return Collections.emptyList();
        }

        @Override
        @Nullable
        public String getRole(UUID playerUUID) {
            return null;
        }

        @Override
        public boolean createParty(EntityPlayerMP player, String name) {
            return false;
        }

        @Override
        public boolean disbandParty(EntityPlayerMP player) {
            return false;
        }

        @Override
        public boolean renameParty(EntityPlayerMP player, String newName) {
            return false;
        }

        @Override
        public boolean invitePlayer(EntityPlayerMP inviter, String targetUsername) {
            return false;
        }

        @Override
        public boolean acceptInvite(EntityPlayerMP player, int partyId) {
            return false;
        }

        @Override
        public boolean kickOrLeave(EntityPlayerMP actor, String targetUsername) {
            return false;
        }

        @Override
        public boolean changeRole(EntityPlayerMP actor, String targetUsername, String newRole) {
            return false;
        }

        @Override
        public void syncToAll() {}

        @Override
        public NBTTagCompound serializeForClient() {
            return new NBTTagCompound();
        }
    };

    private static volatile IPartyProvider provider = NO_OP;
    private static volatile Runnable nativePartyScreenOpener;

    public static void register(IPartyProvider teamProvider) {
        provider = teamProvider;
    }

    public static void registerNativeScreenOpener(Runnable opener) {
        nativePartyScreenOpener = opener;
    }

    public static IPartyProvider get() {
        return provider;
    }

    public static boolean hasNativeScreen() {
        return nativePartyScreenOpener != null;
    }

    public static void openNativeScreen() {
        if (nativePartyScreenOpener != null) {
            nativePartyScreenOpener.run();
        }
    }
}
