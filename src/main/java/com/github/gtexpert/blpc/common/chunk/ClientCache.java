package com.github.gtexpert.blpc.common.chunk;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side in-memory cache of chunk claim data.
 * Populated via {@code MessageSyncClaims} / {@code MessageSyncAllClaims} from the server.
 */
public class ClientCache {

    private static final Map<String, ClaimedChunkData> cache = new HashMap<>();

    public static void update(int x, int z, UUID owner, String name, String partyName, boolean isForceLoaded) {
        String key = ChunkManagerData.chunkKey(x, z);
        if (owner == null) {
            cache.remove(key);
        } else {
            cache.put(key, new ClaimedChunkData(x, z, owner, name, partyName, isForceLoaded));
        }
    }

    public static ClaimedChunkData get(int x, int z) {
        return cache.get(ChunkManagerData.chunkKey(x, z));
    }

    public static void clear() {
        cache.clear();
    }

    public static Collection<ClaimedChunkData> getAll() {
        return cache.values();
    }
}
