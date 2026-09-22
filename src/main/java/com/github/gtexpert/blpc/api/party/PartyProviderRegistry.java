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
 * {@code BQuModule} replaces it with {@code BQuPartyProvider} at {@link #PRIORITY_HIGH}
 * when BetterQuesting is present. Use {@link #PRIORITY_HIGH} to take over, or
 * {@link #PRIORITY_LOW} for a fallback-only provider.
 */
public class PartyProviderRegistry {

    /** Standard priorities for {@link #register(IPartyProvider, int)}. */
    public static final int PRIORITY_LOW = -100;
    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_HIGH = 100;

    private static final IPartyProvider NO_OP = new IPartyProvider() {

        @Override
        public boolean areInSameParty(UUID pA, UUID pB) {
            return false;
        }

        @Override
        @Nullable
        public String getPartyName(UUID u) {
            return null;
        }

        @Override
        public List<UUID> getPartyMembers(UUID u) {
            return Collections.emptyList();
        }

        @Override
        @Nullable
        public String getRole(UUID u) {
            return null;
        }

        @Override
        public boolean createParty(EntityPlayerMP p, String n) {
            return false;
        }

        @Override
        public boolean disbandParty(EntityPlayerMP p) {
            return false;
        }

        @Override
        public boolean renameParty(EntityPlayerMP p, String n) {
            return false;
        }

        @Override
        public boolean invitePlayer(EntityPlayerMP i, String t) {
            return false;
        }

        @Override
        public boolean acceptInvite(EntityPlayerMP p, UUID id) {
            return false;
        }

        @Override
        public boolean kickOrLeave(EntityPlayerMP a, String t) {
            return false;
        }

        @Override
        public boolean changeRole(EntityPlayerMP a, String t, String r) {
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

    /** Registers the provider at {@link #PRIORITY_DEFAULT}. */
    public static void register(IPartyProvider newProvider) {
        register(newProvider, PRIORITY_DEFAULT);
    }

    /**
     * Registers at the given priority. Higher wins; equal priority logs a warning (last-write-wins);
     * lower is silently ignored.
     */
    public static synchronized void register(IPartyProvider newProvider, int priority) {
        if (newProvider == null) {
            LOG.warn("Ignoring null party provider registration (priority {})", priority);
            return;
        }
        if (provider != NO_OP && priority < registeredPriority) {
            LOG.warn("Ignoring {} (priority {}) — {} is already registered at higher priority {}",
                    newProvider.getClass().getSimpleName(), priority,
                    provider.getClass().getSimpleName(), registeredPriority);
            return;
        }
        if (provider != NO_OP && priority == registeredPriority) {
            LOG.warn("{} (priority {}) is replacing {} at the same priority",
                    newProvider.getClass().getSimpleName(), priority,
                    provider.getClass().getSimpleName());
        }
        provider = newProvider;
        registeredPriority = priority;
    }

    /** Clears the registered provider, reverting to the no-op fallback. */
    public static synchronized void unregister() {
        provider = NO_OP;
        registeredPriority = Integer.MIN_VALUE;
    }

    public static void registerNativeScreenOpener(Runnable opener) {
        nativePartyScreenOpener = opener;
    }

    public static void unregisterNativeScreenOpener() {
        nativePartyScreenOpener = null;
    }

    /** Returns the currently registered party provider. */
    public static IPartyProvider get() {
        return provider;
    }

    /**
     * Returns the provider wrapped in an {@link Optional}.
     * Empty when no provider has been registered (still on the no-op fallback).
     */
    public static Optional<IPartyProvider> getSafe() {
        return Optional.ofNullable(provider);
    }

    /** Returns the priority the current provider was registered at. {@code Integer.MIN_VALUE} if none. */
    public static int getRegisteredPriority() {
        return registeredPriority;
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
