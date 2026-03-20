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

public class ChunkMapRenderer {

    private static final ResourceLocation MAP_ICONS = new ResourceLocation("textures/map/map_icons.png");

    private static final int COLOR_OWN = 0x5500FF00;
    private static final int COLOR_PARTY = 0x5500FFFF;
    private static final int COLOR_OTHER = 0x55FF0000;
    private static final int COLOR_BORDER = 0xFFFFFFFF;

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
        } else if (BQPartyHelper.areInSameParty(playerUUID, d.ownerUUID)) {
            color = COLOR_PARTY;
        } else {
            color = COLOR_OTHER;
        }

        GuiDraw.drawRect(dx, dy, size, size, color);
        drawClaimBorder(chunkX, chunkZ, dx, dy, size, d.ownerUUID);
    }

    private static final int ICON_TEX_SIZE = 8;
    private static final int ICON_SHEET_SIZE = 32;

    public static void drawPlayerIcon(float cx, float cy, float yaw, int iconSize) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        // yaw: 0=南, 180=北。アイコンは上(北)向きなので+180で補正
        GlStateManager.rotate(yaw + 180.0f, 0, 0, 1);
        // 常に8x8テクセルをサンプリングし、iconSizeにスケール
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
}
