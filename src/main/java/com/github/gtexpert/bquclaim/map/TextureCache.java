package com.github.gtexpert.bquclaim.map;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public class TextureCache {

    // チャンク座標文字列 "x,z" とテクスチャのペアを保持
    private static final Map<String, ChunkTexture> cache = new HashMap<>();
    private static final Map<String, Integer> colorCache = new HashMap<>();

    public static ChunkTexture getOrCreate(int cx, int cz, int[] colors) {
        String key = cx + "," + cz;
        int oldHash = colorCache.getOrDefault(key, -1);
        int newHash = Arrays.hashCode(colors);
        if (oldHash == newHash && cache.containsKey(key)) {
            return cache.get(key);
        }
        colorCache.put(key, newHash);

        // 新しいテクスチャを作成
        ChunkTexture newTex = new ChunkTexture(colors);
        cache.put(key, newTex);
        return newTex;
    }

    // メモリ節約のため、ワールド移動時などに呼び出す
    public static void clear() {
        for (ChunkTexture tex : cache.values()) {
            // テクスチャをビデオメモリから解放（重要！）
            Minecraft.getMinecraft().getTextureManager().deleteTexture(tex.resourceLocation);
        }
        cache.clear();
        colorCache.clear();
    }

    // 1つのチャンクのテクスチャ情報をまとめるインナークラス
    public static class ChunkTexture {

        public final DynamicTexture texture;
        public final ResourceLocation resourceLocation;

        public ChunkTexture(int[] colors) {
            this.texture = new DynamicTexture(16, 16);
            // データを流し込む
            System.arraycopy(colors, 0, texture.getTextureData(), 0, 256);
            // GPUへアップロード
            this.texture.updateDynamicTexture();
            // 名前（ResourceLocation）を付けて管理
            this.resourceLocation = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("chunk_map_" + System.nanoTime(), texture);
        }
    }
}
