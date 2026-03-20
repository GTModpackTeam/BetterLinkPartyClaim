package com.github.gtexpert.bquclaim.map;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class AsyncMapRenderer {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Map<Long, int[]> COLOR_CACHE = new ConcurrentHashMap<>();
    private static final Set<Long> PROCESSING = ConcurrentHashMap.newKeySet();

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static void requestChunk(World world, int cx, int cz) {
        MapColorHelper.init();

        long key = chunkKey(cx, cz);
        if (COLOR_CACHE.containsKey(key) || PROCESSING.contains(key)) return;

        Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
        if (chunk == null) return;

        // シェーディング用に北隣チャンクも事前取得（未ロードならnull）
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

    public static int[] getColors(int cx, int cz) {
        return COLOR_CACHE.get(chunkKey(cx, cz));
    }

    public static void evict(int centerCX, int centerCZ, int radius) {
        Iterator<Long> it = COLOR_CACHE.keySet().iterator();
        while (it.hasNext()) {
            long key = it.next();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            if (Math.abs(cx - centerCX) > radius || Math.abs(cz - centerCZ) > radius) {
                it.remove();
            }
        }
    }

    public static void clearCache() {
        COLOR_CACHE.clear();
    }
}
