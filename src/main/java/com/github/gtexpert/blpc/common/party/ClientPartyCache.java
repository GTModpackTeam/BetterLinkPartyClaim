package com.github.gtexpert.blpc.common.party;

import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * Client-side party data cache. Populated via {@code MessagePartySync}.
 * Also tracks BQu linking flags per player for UI decisions.
 */
public class ClientPartyCache {

    private static final Map<UUID, Party> parties = new LinkedHashMap<>();
    private static final Set<UUID> bquLinkedPlayers = new HashSet<>();
    private static final List<Runnable> syncListeners = new ArrayList<>();

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
            Party party = Party.fromNBT(list.getCompoundTagAt(i));
            parties.put(party.getPartyId(), party);
        }
        // Only update bquLinked if the tag is present (avoid clearing on partial updates)
        if (data.hasKey("bquLinked")) {
            bquLinkedPlayers.clear();
            NBTTagList linkedList = data.getTagList("bquLinked", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < linkedList.tagCount(); i++) {
                bquLinkedPlayers.add(linkedList.getCompoundTagAt(i).getUniqueId("uuid"));
            }
        }
        for (Runnable listener : new ArrayList<>(syncListeners)) {
            listener.run();
        }
    }

    public static boolean isBQuLinked(UUID playerUUID) {
        return bquLinkedPlayers.contains(playerUUID);
    }

    /** Locally update BQu link state without server sync. Used by BQPartyEventHandler. */
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
        // Don't clear syncListeners — they survive reconnects
    }

    public static void clearAll() {
        parties.clear();
        bquLinkedPlayers.clear();
        syncListeners.clear();
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
