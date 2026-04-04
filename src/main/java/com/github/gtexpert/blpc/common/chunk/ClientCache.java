package com.github.gtexpert.blpc.common.chunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side in-memory cache of chunk claim data.
 * Populated via {@code MessageSyncClaims} / {@code MessageSyncAllClaims} from the server.
 */
public class ClientCache {

    private static final Map<Long, ClaimedChunkData> cache = new HashMap<>();
    private static final List<Runnable> changeListeners = new ArrayList<>();

    public static void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public static void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private static void fireChangeListeners() {
        for (Runnable listener : new ArrayList<>(changeListeners)) {
            listener.run();
        }
    }

    public static void update(int x, int z, UUID owner, String name, String partyName, boolean isForceLoaded) {
        long key = ChunkManagerData.chunkKey(x, z);
        if (owner == null) {
            cache.remove(key);
        } else {
            cache.put(key, new ClaimedChunkData(x, z, owner, name, partyName, isForceLoaded));
        }
        fireChangeListeners();
    }

    public static ClaimedChunkData get(int x, int z) {
        return cache.get(ChunkManagerData.chunkKey(x, z));
    }

    public static void clear() {
        cache.clear();
        fireChangeListeners();
    }

    public static void clearAll() {
        cache.clear();
        changeListeners.clear();
    }

    public static Collection<ClaimedChunkData> getAll() {
        return cache.values();
    }
}
