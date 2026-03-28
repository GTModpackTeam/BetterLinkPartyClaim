package com.github.gtexpert.blpc.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cleanroommc.modularui.drawable.GuiDraw;

import com.github.gtexpert.blpc.client.map.AsyncMapRenderer;
import com.github.gtexpert.blpc.client.map.ChunkMapRenderer;
import com.github.gtexpert.blpc.common.ModConfig;

/**
 * In-game minimap HUD overlay.
 * <p>
 * Renders the chunk map at full resolution (same as the full map screen)
 * then scales it down for the HUD display. This ensures the minimap
 * looks identical to the full map, just smaller.
 */
public class MinimapHUD {

    /** Number of chunks visible on each side of the player. */
    private static final int RADIUS = 8;
    /** Grid size in chunks (diameter). */
    private static final int GRID = RADIUS * 2 + 1;
    /** Internal chunk render size in pixels (same scale as full map). */
    private static final int RENDER_CHUNK_SIZE = 16;
    /** Internal render size in pixels. */
    private static final int RENDER_SIZE = GRID * RENDER_CHUNK_SIZE;
    /** Display size on screen in pixels. */
    private static final int DISPLAY_SIZE = 68;
    /** Scale factor: display / render. */
    private static final float SCALE = (float) DISPLAY_SIZE / RENDER_SIZE;

    private static final int MARGIN = 5;
    private static final int PLAYER_ICON_SIZE = (int) (6 / SCALE);
    private static final float COORD_SCALE = 0.5f;

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ModConfig.showMinimap) return;
        if (mc.currentScreen != null) return;

        int pX = mc.player.chunkCoordX;
        int pZ = mc.player.chunkCoordZ;
        int startX = event.getResolution().getScaledWidth() - DISPLAY_SIZE - MARGIN;

        AsyncMapRenderer.evict(pX, pZ, RADIUS + 2);

        GlStateManager.pushMatrix();
        GlStateManager.translate(startX, MARGIN, 0);

        // Black background at display size
        GuiDraw.drawRect(0, 0, DISPLAY_SIZE, DISPLAY_SIZE, 0xFF000000);

        // Scale down and draw at full resolution
        GlStateManager.scale(SCALE, SCALE, 1.0f);

        ChunkMapRenderer.drawChunkGrid(0, 0, RENDER_CHUNK_SIZE, RADIUS, pX, pZ,
                0, mc.player.world, mc.player.getUniqueID(), true, false);

        // Player icon
        float relX = (float) (mc.player.posX % 16) / 16.0f * RENDER_CHUNK_SIZE;
        float relZ = (float) (mc.player.posZ % 16) / 16.0f * RENDER_CHUNK_SIZE;
        if (relX < 0) relX += RENDER_CHUNK_SIZE;
        if (relZ < 0) relZ += RENDER_CHUNK_SIZE;
        float iconCX = RADIUS * RENDER_CHUNK_SIZE + relX;
        float iconCY = RADIUS * RENDER_CHUNK_SIZE + relZ;
        ChunkMapRenderer.drawPlayerIcon(iconCX, iconCY, mc.player.rotationYaw, PLAYER_ICON_SIZE);

        GlStateManager.popMatrix();

        // Coordinates (drawn at display scale, outside the scaled matrix)
        GlStateManager.pushMatrix();
        GlStateManager.translate(startX, MARGIN, 0);
        String coords = String.format("%d, %d", (int) mc.player.posX, (int) mc.player.posZ);
        float textWidth = mc.fontRenderer.getStringWidth(coords) * COORD_SCALE;
        float tx = (DISPLAY_SIZE - textWidth) / 2f;
        float ty = DISPLAY_SIZE + 2;
        GuiDraw.drawText(coords, tx, ty, COORD_SCALE, GuiColors.WHITE, true);
        GlStateManager.popMatrix();
    }
}
