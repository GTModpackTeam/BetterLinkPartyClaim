package com.github.gtexpert.bquclaim.chunk;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientCache {

    private static final Map<String, ClaimedChunkData> cache = new HashMap<>();

    public static void update(int x, int z, UUID owner, String name, boolean isForceLoaded) {
        String key = ChunkManagerData.chunkKey(x, z);
        if (owner == null) {
            cache.remove(key);
        } else {
            cache.put(key, new ClaimedChunkData(x, z, owner, name, isForceLoaded));
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
