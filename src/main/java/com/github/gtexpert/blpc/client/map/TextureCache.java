package com.github.gtexpert.blpc.client.map;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public class TextureCache {

    private static final Map<Long, ChunkTexture> CACHE = new HashMap<>();
    private static final Map<Long, Integer> HASH_CACHE = new HashMap<>();

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static ChunkTexture getOrCreate(int cx, int cz, int[] colors) {
        long key = chunkKey(cx, cz);
        int newHash = Arrays.hashCode(colors);

        if (HASH_CACHE.getOrDefault(key, -1) == newHash && CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        // 古いテクスチャを解放してから置換
        ChunkTexture old = CACHE.get(key);
        if (old != null) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(old.resourceLocation);
        }

        HASH_CACHE.put(key, newHash);
        ChunkTexture tex = new ChunkTexture(colors);
        CACHE.put(key, tex);
        return tex;
    }

    public static void clear() {
        for (ChunkTexture tex : CACHE.values()) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(tex.resourceLocation);
        }
        CACHE.clear();
        HASH_CACHE.clear();
    }

    public static class ChunkTexture {

        public final DynamicTexture texture;
        public final ResourceLocation resourceLocation;

        public ChunkTexture(int[] colors) {
            this.texture = new DynamicTexture(16, 16);
            System.arraycopy(colors, 0, texture.getTextureData(), 0, 256);
            this.texture.updateDynamicTexture();
            this.resourceLocation = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("chunk_map_" + System.nanoTime(), texture);
        }
    }
}
