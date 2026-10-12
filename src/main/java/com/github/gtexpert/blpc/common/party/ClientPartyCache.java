package com.github.gtexpert.blpc.common.party;

import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.common.ModLog;

/**
 * Client-side party data cache. Populated via {@code PartySync}.
 * Also tracks BQu linking flags per player for UI decisions.
 */
public class ClientPartyCache {

    private static final Map<UUID, Party> parties = new LinkedHashMap<>();
    private static final Set<UUID> bquLinkedPlayers = new HashSet<>();
    private static final List<Runnable> syncListeners = new ArrayList<>();
    private static final Map<String, Map<String, Object>> addonData = new HashMap<>();

    public static void addSyncListener(Runnable listener) {
        syncListeners.add(listener);
    }

    public static void removeSyncListener(Runnable listener) {
        syncListeners.remove(listener);
    }

    public static void loadFromNBT(NBTTagCompound data) {
        parties.clear();
        NBTTagList list = data.getTagList("parties", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            try {
                Party party = Party.fromNBT(list.getCompoundTagAt(i));
                if (party == null) continue;
                parties.put(party.getPartyId(), party);
            } catch (Exception e) {
                ModLog.SYNC.error("Failed to parse party entry at index {}, skipping", i, e);
            }
        }
        bquLinkedPlayers.clear();
        NBTTagList linkedList = data.getTagList("bquLinked", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < linkedList.tagCount(); i++) {
            bquLinkedPlayers.add(linkedList.getCompoundTagAt(i).getUniqueId("uuid"));
        }
        // Fire listeners immediately — no tick-based coalescing needed
        for (Runnable listener : new ArrayList<>(syncListeners)) {
            listener.run();
        }
    }

    public static boolean isBQuLinked(UUID playerUUID) {
        return bquLinkedPlayers.contains(playerUUID);
    }

    public static Set<UUID> getBQuLinkedPlayers() {
        return Collections.unmodifiableSet(bquLinkedPlayers);
    }

    /** Fires all registered sync listeners. Used for optimistic updates that need immediate UI refresh. */
    public static void fireSyncListeners() {
        for (Runnable listener : new ArrayList<>(syncListeners)) {
            listener.run();
        }
    }

    /** Locally update BQu link state without server sync. Used by BQuPartyEventHandler. */
    public static void setLocalBQuLinked(UUID playerUUID, boolean linked) {
        if (linked) {
            bquLinkedPlayers.add(playerUUID);
        } else {
            bquLinkedPlayers.remove(playerUUID);
        }
    }

    public static void clear() {
        parties.clear();
        bquLinkedPlayers.clear();
        addonData.clear();
        // Don't clear syncListeners — they survive reconnects
    }

    public static void clearAll() {
        parties.clear();
        bquLinkedPlayers.clear();
        syncListeners.clear();
        addonData.clear();
    }

    /**
     * Stores an addon-namespaced client-side cache value alongside core party data. Data
     * shares BLPC's cache lifecycle — cleared on {@link #clear()}/{@link #clearAll()}
     * (disconnect) — so addons don't need to wire up their own disconnect handling.
     *
     * @param modId your addon's mod ID (used as the namespace; avoids key collisions)
     * @param key   cache key, scoped to {@code modId}
     * @param value value to store
     */
    public static void putAddonData(String modId, String key, Object value) {
        addonData.computeIfAbsent(modId, id -> new HashMap<>()).put(key, value);
    }

    /** Reads a value stored via {@link #putAddonData}, or {@code null} if absent. */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T getAddonData(String modId, String key) {
        var namespace = addonData.get(modId);
        return namespace == null ? null : (T) namespace.get(key);
    }

    /** Removes a single value stored via {@link #putAddonData}. */
    public static void removeAddonData(String modId, String key) {
        var namespace = addonData.get(modId);
        if (namespace != null) namespace.remove(key);
    }

    /** Clears every value stored under {@code modId} via {@link #putAddonData}. */
    public static void clearAddonData(String modId) {
        addonData.remove(modId);
    }

    @Nullable
    public static Party getParty(UUID partyId) {
        return parties.get(partyId);
    }

    @Nullable
    public static Party getPartyByPlayer(UUID playerUUID) {
        for (Party party : parties.values()) {
            if (party.isMember(playerUUID)) {
                return party;
            }
        }
        return null;
    }

    public static Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }
}
