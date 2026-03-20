package com.github.gtexpert.bquclaim.map;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.world.World;

public class AsyncMapRenderer {

    // スレッドプール（CPUのコア数に合わせて並列処理）
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    // すでに計算済みのデータキャッシュ
    private static final Map<String, int[]> colorCache = new ConcurrentHashMap<>();
    // 現在計算中のチャンクを記録（重複防止）
    private static final Set<String> processing = ConcurrentHashMap.newKeySet();

    public static void requestChunk(World world, int cx, int cz) {
        String key = cx + "," + cz;
        if (colorCache.containsKey(key) || processing.contains(key)) return;

        // 未読込チャンクはスキップ（次フレームで再試行される）
        if (world.getChunkProvider().getLoadedChunk(cx, cz) == null) return;

        processing.add(key);

        // 非同期タスクの実行
        executor.submit(() -> {
            try {
                int[] colors = new int[256];
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        colors[x + z * 16] = MapColorHelper.getBlockColor(world, (cx << 4) + x, (cz << 4) + z);
                    }
                }
                colorCache.put(key, colors);
            } finally {
                processing.remove(key);
            }
        });
    }

    public static int[] getColors(int cx, int cz) {
        return colorCache.get(cx + "," + cz);
    }

    /** 表示範囲外のキャッシュを除去する。描画ループの先頭で呼び出す。 */
    public static void evict(int centerCX, int centerCZ, int radius) {
        Iterator<String> it = colorCache.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            int sep = key.indexOf(',');
            int cx = Integer.parseInt(key.substring(0, sep));
            int cz = Integer.parseInt(key.substring(sep + 1));
            if (Math.abs(cx - centerCX) > radius || Math.abs(cz - centerCZ) > radius) {
                it.remove();
            }
        }
    }

    public static void clearCache() {
        colorCache.clear();
    }
}
