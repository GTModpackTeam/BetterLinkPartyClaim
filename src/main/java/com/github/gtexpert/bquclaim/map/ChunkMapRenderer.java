package com.github.gtexpert.bquclaim.map;

import java.util.UUID;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.utils.Platform;
import com.github.gtexpert.bquclaim.BQPartyHelper;
import com.github.gtexpert.bquclaim.chunk.ClaimedChunkData;
import com.github.gtexpert.bquclaim.chunk.ClientCache;

/**
 * フルマップとミニマップで共通のチャンク描画ロジック。
 * すべての描画に MUI の GuiDraw / Platform API を使用する。
 */
public class ChunkMapRenderer {

    private static final ResourceLocation MAP_ICONS = new ResourceLocation("textures/map/map_icons.png");

    /** 地形テクスチャを描画する。未ロードなら非同期リクエストしてプレースホルダーを描画。 */
    public static void drawChunkTerrain(int chunkX, int chunkZ, float dx, float dy, int size, World world) {
        int[] colors = AsyncMapRenderer.getColors(chunkX, chunkZ);
        if (colors != null) {
            TextureCache.ChunkTexture tex = TextureCache.getOrCreate(chunkX, chunkZ, colors);
            Platform.setupDrawTex(tex.resourceLocation);
            GuiDraw.drawTexture(dx, dy, 0, 0, size, size, 16, 16);
        } else {
            AsyncMapRenderer.requestChunk(world, chunkX, chunkZ);
            GuiDraw.drawRect(dx, dy, size, size, 0xFF222222);
        }
    }

    /** クレームオーバーレイ（色付き半透明矩形 + 境界線）を描画する。 */
    public static void drawClaimOverlay(int chunkX, int chunkZ, float dx, float dy, int size, UUID playerUUID) {
        ClaimedChunkData d = ClientCache.get(chunkX, chunkZ);
        if (d == null) return;

        int color;
        if (d.ownerUUID.equals(playerUUID)) {
            color = 0x5500FF00; // 自分: 半透明の緑
        } else if (BQPartyHelper.areInSameParty(playerUUID, d.ownerUUID)) {
            color = 0x5500FFFF; // パーティ: 半透明の水色
        } else {
            color = 0x55FF0000; // 他人: 半透明の赤
        }

        GuiDraw.drawRect(dx, dy, size, size, color);
        drawChunkBorder(chunkX, chunkZ, dx, dy, size, d.ownerUUID);
    }

    /** 隣接チャンクと所有者が異なる辺に白い境界線を描画する。 */
    public static void drawChunkBorder(int chunkX, int chunkZ, float dx, float dy, int size, UUID owner) {
        int c = 0xFFFFFFFF;
        if (!isSameOwner(chunkX, chunkZ - 1, owner))
            GuiDraw.drawRect(dx, dy, size, 1, c);
        if (!isSameOwner(chunkX, chunkZ + 1, owner))
            GuiDraw.drawRect(dx, dy + size - 1, size, 1, c);
        if (!isSameOwner(chunkX - 1, chunkZ, owner))
            GuiDraw.drawRect(dx, dy, 1, size, c);
        if (!isSameOwner(chunkX + 1, chunkZ, owner))
            GuiDraw.drawRect(dx + size - 1, dy, 1, size, c);
    }

    /**
     * プレイヤーアイコンを描画する。
     *
     * @param cx       描画中心X
     * @param cy       描画中心Y
     * @param yaw      プレイヤーの向き
     * @param iconSize アイコンサイズ (フルマップ: 8, ミニマップ: 6)
     * @param offsetX  チャンク内サブオフセットX (ミニマップでは 0)
     * @param offsetY  チャンク内サブオフセットY (ミニマップでは 0)
     */
    public static void drawPlayerIcon(float cx, float cy, float yaw, int iconSize, float offsetX, float offsetY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.rotate(yaw, 0, 0, 1);
        GlStateManager.translate(-iconSize / 2.0f, -iconSize / 2.0f, 0);

        Platform.setupDrawTex(MAP_ICONS);
        GuiDraw.drawTexture(offsetX, offsetY, 0, 0, iconSize, iconSize, 32, 32);

        GlStateManager.popMatrix();
    }

    /** 隣接チャンクが同じ所有者かどうかを判定する。 */
    public static boolean isSameOwner(int chunkX, int chunkZ, UUID owner) {
        ClaimedChunkData neighbor = ClientCache.get(chunkX, chunkZ);
        return neighbor != null && neighbor.ownerUUID.equals(owner);
    }
}
