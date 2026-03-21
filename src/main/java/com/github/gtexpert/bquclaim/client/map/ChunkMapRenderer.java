package com.github.gtexpert.bquclaim.client.map;

import java.util.UUID;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;

import com.github.gtexpert.bquclaim.api.party.PartyProviderRegistry;
import com.github.gtexpert.bquclaim.common.chunk.ClaimedChunkData;
import com.github.gtexpert.bquclaim.common.chunk.ClientCache;

public class ChunkMapRenderer {

    private static final ResourceLocation MAP_ICONS = new ResourceLocation("textures/map/map_icons.png");

    private static final int COLOR_OWN = 0x5500FF00;
    private static final int COLOR_PARTY = 0x5500FFFF;
    private static final int COLOR_OTHER = 0x55FF0000;
    private static final int COLOR_BORDER = 0xFFFFFFFF;
    private static final int COLOR_HATCHING = 0xAAFF0000;
    private static final int HATCHING_SPACING = 4;

    private static final int ICON_TEX_SIZE = 8;
    private static final int ICON_SHEET_SIZE = 32;

    /**
     * チャンクグリッド全体を描画する共通メソッド。
     * フルマップとミニマップの両方から使用される。
     *
     * @param ox            描画原点X
     * @param oy            描画原点Y
     * @param chunkSize     1チャンクあたりの描画ピクセル数
     * @param radius        中心からの表示チャンク数
     * @param centerCX      中心チャンクX
     * @param centerCZ      中心チャンクZ
     * @param gridColor     グリッド線の色（0で非表示）
     * @param world         ワールド
     * @param playerUUID    プレイヤーUUID
     * @param showForceLoad フォースロードのハッチング表示
     */
    public static void drawChunkGrid(int ox, int oy, int chunkSize, int radius,
                                     int centerCX, int centerCZ, int gridColor,
                                     World world, UUID playerUUID, boolean showForceLoad) {
        int gridLen = radius * 2 + 1;
        int mapPx = gridLen * chunkSize;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int rx = centerCX + x;
                int rz = centerCZ + z;
                int dx = ox + (x + radius) * chunkSize;
                int dy = oy + (z + radius) * chunkSize;

                drawChunkTerrain(rx, rz, dx, dy, chunkSize, world);
                drawClaimOverlay(rx, rz, dx, dy, chunkSize, playerUUID);

                if (showForceLoad) {
                    ClaimedChunkData d = ClientCache.get(rx, rz);
                    if (d != null && d.isForceLoaded) {
                        drawHatching(dx, dy, chunkSize, chunkSize, COLOR_HATCHING);
                    }
                }
            }
        }

        // グリッド線
        if (gridColor != 0) {
            for (int i = 1; i < gridLen; i++) {
                GuiDraw.drawRect(ox + i * chunkSize, oy, 1, mapPx, gridColor);
                GuiDraw.drawRect(ox, oy + i * chunkSize, mapPx, 1, gridColor);
            }
        }
    }

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

    public static void drawClaimOverlay(int chunkX, int chunkZ, float dx, float dy, int size, UUID playerUUID) {
        ClaimedChunkData d = ClientCache.get(chunkX, chunkZ);
        if (d == null) return;

        int color;
        if (d.ownerUUID.equals(playerUUID)) {
            color = COLOR_OWN;
        } else if (PartyProviderRegistry.get().areInSameParty(playerUUID, d.ownerUUID)) {
            color = COLOR_PARTY;
        } else {
            color = COLOR_OTHER;
        }

        GuiDraw.drawRect(dx, dy, size, size, color);
        drawClaimBorder(chunkX, chunkZ, dx, dy, size, d.ownerUUID);
    }

    public static void drawPlayerIcon(float cx, float cy, float yaw, int iconSize) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.rotate(yaw + 180.0f, 0, 0, 1);
        float scale = iconSize / (float) ICON_TEX_SIZE;
        GlStateManager.scale(scale, scale, 1.0f);

        Platform.setupDrawTex(MAP_ICONS);
        float half = ICON_TEX_SIZE / 2.0f;
        GuiDraw.drawTexture(-half, -half, 0, 0, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_SHEET_SIZE, ICON_SHEET_SIZE);

        GlStateManager.popMatrix();
    }

    private static void drawClaimBorder(int chunkX, int chunkZ, float dx, float dy, int size, UUID owner) {
        if (!isSameOwner(chunkX, chunkZ - 1, owner))
            GuiDraw.drawRect(dx, dy, size, 1, COLOR_BORDER);
        if (!isSameOwner(chunkX, chunkZ + 1, owner))
            GuiDraw.drawRect(dx, dy + size - 1, size, 1, COLOR_BORDER);
        if (!isSameOwner(chunkX - 1, chunkZ, owner))
            GuiDraw.drawRect(dx, dy, 1, size, COLOR_BORDER);
        if (!isSameOwner(chunkX + 1, chunkZ, owner))
            GuiDraw.drawRect(dx + size - 1, dy, 1, size, COLOR_BORDER);
    }

    private static boolean isSameOwner(int chunkX, int chunkZ, UUID owner) {
        ClaimedChunkData neighbor = ClientCache.get(chunkX, chunkZ);
        return neighbor != null && neighbor.ownerUUID.equals(owner);
    }

    private static void drawHatching(int x, int y, int w, int h, int color) {
        GlStateManager.glLineWidth(1.0F);
        Platform.setupDrawColor();
        Platform.startDrawing(Platform.DrawMode.LINES, Platform.VertexFormat.POS_COLOR, buffer -> {
            int r = Color.getRed(color);
            int g = Color.getGreen(color);
            int b = Color.getBlue(color);
            int a = Color.getAlpha(color);
            for (int i = 0; i <= w + h; i += HATCHING_SPACING) {
                buffer.pos(x + Math.max(0, i - h), y + Math.min(i, h), 0).color(r, g, b, a).endVertex();
                buffer.pos(x + Math.min(i, w), y + Math.max(0, i - w), 0).color(r, g, b, a).endVertex();
            }
        });
    }
}
