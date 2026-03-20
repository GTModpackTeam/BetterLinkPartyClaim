package com.github.gtexpert.bquclaim.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cleanroommc.modularui.drawable.GuiDraw;

import com.github.gtexpert.bquclaim.ModConfig;
import com.github.gtexpert.bquclaim.map.AsyncMapRenderer;
import com.github.gtexpert.bquclaim.map.ChunkMapRenderer;

public class MinimapHUD {

    private static final int MAP_SIZE = 64;
    private static final int CHUNK_SIZE = 4;
    private static final int MARGIN = 5;
    private static final int RANGE = (MAP_SIZE / CHUNK_SIZE) / 2;
    private static final int GRID_LINE_COLOR = 0x20FFFFFF;
    private static final int PLAYER_ICON_SIZE = 6;
    private static final float COORD_SCALE = 0.5f;

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ModConfig.showMinimap) return;
        if (mc.currentScreen != null) return;

        int startX = event.getResolution().getScaledWidth() - MAP_SIZE - MARGIN;
        int pX = mc.player.chunkCoordX;
        int pZ = mc.player.chunkCoordZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(startX, MARGIN, 0);

        GuiDraw.drawRect(0, 0, MAP_SIZE, MAP_SIZE, 0xFF000000);

        AsyncMapRenderer.evict(pX, pZ, RANGE + 2);

        int half = MAP_SIZE / 2;
        for (int x = -RANGE; x <= RANGE; x++) {
            for (int z = -RANGE; z <= RANGE; z++) {
                int dx = half + (x * CHUNK_SIZE);
                int dy = half + (z * CHUNK_SIZE);
                ChunkMapRenderer.drawChunkTerrain(pX + x, pZ + z, dx, dy, CHUNK_SIZE, mc.player.world);
                ChunkMapRenderer.drawClaimOverlay(pX + x, pZ + z, dx, dy, CHUNK_SIZE, mc.player.getUniqueID());
            }
        }

        for (int x = -RANGE; x <= RANGE + 1; x++) {
            GuiDraw.drawRect(half + (x * CHUNK_SIZE), 0, 1, MAP_SIZE, GRID_LINE_COLOR);
        }
        for (int z = -RANGE; z <= RANGE + 1; z++) {
            GuiDraw.drawRect(0, half + (z * CHUNK_SIZE), MAP_SIZE, 1, GRID_LINE_COLOR);
        }

        ChunkMapRenderer.drawPlayerIcon(half, half, mc.player.rotationYaw, PLAYER_ICON_SIZE);

        String coords = String.format("%d, %d", (int) mc.player.posX, (int) mc.player.posZ);
        GlStateManager.scale(COORD_SCALE, COORD_SCALE, COORD_SCALE);
        mc.fontRenderer.drawStringWithShadow(coords,
                MAP_SIZE - (float) mc.fontRenderer.getStringWidth(coords) / 2,
                (MAP_SIZE * 2) + 4, 0xFFFFFFFF);

        GlStateManager.popMatrix();
    }
}
