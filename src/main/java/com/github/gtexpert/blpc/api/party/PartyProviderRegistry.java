package com.github.gtexpert.blpc.api.party;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registry for the active {@link IPartyProvider}.
 * <p>
 * {@code CoreModule} registers {@code DefaultPartyProvider} at {@link #PRIORITY_DEFAULT}.
 * {@code BQuModule} replaces it with {@code BQuPartyProvider} at {@link #PRIORITY_HIGH} when
 * BetterQuesting is present. Addons that need to override the provider should use
 * {@link #PRIORITY_HIGH}; addons that want a fallback-only provider should use
 * {@link #PRIORITY_LOW}.
 * <p>
 * A lower-priority registration is silently ignored after a higher-priority one is set.
 * Registrations at equal priority log a warning and win (last-write-wins at tie).
 * <p>
 * Also provides an optional native screen opener for BQu's party management UI.
 */
public class PartyProviderRegistry {

    /** Standard priorities for {@link #register(IPartyProvider, int)}. */
    public static final int PRIORITY_LOW = -100;
    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_HIGH = 100;

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
        public boolean acceptInvite(EntityPlayerMP player, UUID partyId) {
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

    private static final Logger LOG = LogManager.getLogger("blpc/PartyProviderRegistry");

    private static volatile IPartyProvider provider = NO_OP;
    private static volatile int registeredPriority = Integer.MIN_VALUE;
    private static volatile Runnable nativePartyScreenOpener;

    /**
     * Registers the active party provider at {@link #PRIORITY_DEFAULT}.
     * Calls {@link #register(IPartyProvider, int)} — see that method for priority semantics.
     */
    public static void register(IPartyProvider newProvider) {
        register(newProvider, PRIORITY_DEFAULT);
    }

    /**
     * Registers a party provider at the given priority.
     * <ul>
     * <li>Higher priority wins over a registered lower-priority provider.</li>
     * <li>Equal priority logs a warning and accepts the new provider (last-write-wins).</li>
     * <li>Lower priority than the currently registered one is silently ignored.</li>
     * </ul>
     */
    public static synchronized void register(IPartyProvider newProvider, int priority) {
        if (newProvider == null) {
            LOG.warn("Ignoring null party provider registration (priority {})", priority);
            return;
        }
        if (provider != NO_OP && priority < registeredPriority) {
            LOG.warn(
                    "Ignoring {} (priority {}) — {} is already registered at higher priority {}",
                    newProvider.getClass().getSimpleName(), priority,
                    provider.getClass().getSimpleName(), registeredPriority);
            return;
        }
        if (provider != NO_OP && priority == registeredPriority) {
            LOG.warn(
                    "{} (priority {}) is replacing {} at the same priority — check registration order",
                    newProvider.getClass().getSimpleName(), priority,
                    provider.getClass().getSimpleName());
        }
        provider = newProvider;
        registeredPriority = priority;
    }

    /**
     * Clears the registered provider, reverting {@link #get()} to the internal no-op
     * fallback and {@link #getRegisteredPriority()} to {@code Integer.MIN_VALUE}. Mainly
     * useful for tests and hot-reload scenarios.
     */
    public static synchronized void unregister() {
        provider = NO_OP;
        registeredPriority = Integer.MIN_VALUE;
    }

    /** Registers a runnable that opens the native party management screen (e.g. BQu's party UI). */
    public static void registerNativeScreenOpener(Runnable opener) {
        nativePartyScreenOpener = opener;
    }

    /** Clears the registered native party screen opener, if any. */
    public static void unregisterNativeScreenOpener() {
        nativePartyScreenOpener = null;
    }

    /** Returns the currently registered party provider. */
    public static IPartyProvider get() {
        return provider;
    }

    /**
     * Returns the currently registered party provider wrapped in an {@link Optional}.
     * <p>
     * Convenience for addon authors — eliminates the need to null-check against
     * the registry's no-op fallback. Returns an empty optional when no provider
     * has been registered yet.
     */
    public static Optional<IPartyProvider> getSafe() {
        return Optional.ofNullable(provider);
    }

    /**
     * The priority the current provider was registered at, or {@code Integer.MIN_VALUE}
     * if none has been registered (still on the no-op fallback).
     */
    public static int getRegisteredPriority() {
        return registeredPriority;
    }

    /** Returns true if a native party screen opener has been registered. */
    public static boolean hasNativeScreen() {
        return nativePartyScreenOpener != null;
    }

    /** Opens the native party screen if one has been registered; otherwise a no-op. */
    public static void openNativeScreen() {
        if (nativePartyScreenOpener != null) {
            nativePartyScreenOpener.run();
        }
    }
}
