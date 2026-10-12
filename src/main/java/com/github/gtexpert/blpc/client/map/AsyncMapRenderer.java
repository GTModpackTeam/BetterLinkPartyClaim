package com.github.gtexpert.blpc.client.map;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class AsyncMapRenderer {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "BLPC-MapRenderer");
        t.setDaemon(true);
        return t;
    });
    private static final Map<ChunkKey, int[]> COLOR_CACHE = new ConcurrentHashMap<>();
    private static final Set<ChunkKey> PROCESSING = ConcurrentHashMap.newKeySet();

    /**
     * Composite cache key: chunk x/z plus dimension id, so traveling to another dimension at
     * overlapping chunk coordinates doesn't render stale terrain carried over from the previous
     * dimension.
     */
    private static final class ChunkKey {

        final int cx, cz, dim;

        ChunkKey(int cx, int cz, int dim) {
            this.cx = cx;
            this.cz = cz;
            this.dim = dim;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ChunkKey other && other.cx == cx && other.cz == cz && other.dim == dim;
        }

        @Override
        public int hashCode() {
            return Objects.hash(cx, cz, dim);
        }
    }

    public static void requestChunk(World world, int cx, int cz) {
        if (!MapColorHelper.isInitialized()) {
            MapColorHelper.requestInitAsync();
            return;
        }

        int dim = world.provider.getDimension();
        ChunkKey key = new ChunkKey(cx, cz, dim);
        if (COLOR_CACHE.containsKey(key) || PROCESSING.contains(key)) return;

        Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
        if (chunk == null) return;

        // Pre-fetch north neighbor chunk for height shading (null if unloaded)
        Chunk northChunk = world.getChunkProvider().getLoadedChunk(cx, cz - 1);

        PROCESSING.add(key);

        EXECUTOR.submit(() -> {
            try {
                int[] colors = MapColorHelper.computeChunkColors(world, chunk, northChunk, cx, cz);
                COLOR_CACHE.put(key, colors);
            } finally {
                PROCESSING.remove(key);
            }
        });
    }

    public static void submitInit(Runnable initTask) {
        EXECUTOR.submit(initTask);
    }

    public static int[] getColors(int cx, int cz, int dim) {
        return COLOR_CACHE.get(new ChunkKey(cx, cz, dim));
    }

    public static void evict(int centerCX, int centerCZ, int dim, int radius) {
        Iterator<ChunkKey> it = COLOR_CACHE.keySet().iterator();
        while (it.hasNext()) {
            ChunkKey key = it.next();
            if (key.dim != dim || Math.abs(key.cx - centerCX) > radius || Math.abs(key.cz - centerCZ) > radius) {
                it.remove();
            }
        }
    }

    public static void clearCache() {
        COLOR_CACHE.clear();
    }
}
