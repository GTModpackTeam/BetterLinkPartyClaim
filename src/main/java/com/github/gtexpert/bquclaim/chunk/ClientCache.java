package com.github.gtexpert.bquclaim.chunk;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientCache {

    // 座標 "x,z" をキーにして領地データを保持
    private static final Map<String, ClaimedChunkData> cache = new HashMap<>();

    // パケット受信時に呼ばれる更新メソッド
    public static void update(int x, int z, UUID owner, String name, boolean isForceLoaded) {
        String key = x + "," + z;
        if (owner == null) {
            cache.remove(key);
        } else {
            cache.put(key, new ClaimedChunkData(x, z, owner, name, isForceLoaded));
        }
    }

    // GUIが描画時に呼び出すメソッド
    public static ClaimedChunkData get(int x, int z) {
        return cache.get(x + "," + z);
    }

    // 全消去（ワールド退出時などに使用）
    public static void clear() {
        cache.clear();
    }

    public static Collection<ClaimedChunkData> getAll() {
        return cache.values();
    }
}
