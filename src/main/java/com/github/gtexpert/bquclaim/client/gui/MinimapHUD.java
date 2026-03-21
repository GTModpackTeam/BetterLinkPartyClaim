package com.github.gtexpert.bquclaim.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cleanroommc.modularui.drawable.GuiDraw;

import com.github.gtexpert.bquclaim.ModConfig;
import com.github.gtexpert.bquclaim.client.map.AsyncMapRenderer;
import com.github.gtexpert.bquclaim.client.map.ChunkMapRenderer;

public class MinimapHUD {

    private static final int CHUNK_SIZE = 4;
    private static final int RADIUS = 8;
    private static final int MAP_SIZE = (RADIUS * 2 + 1) * CHUNK_SIZE; // 17 * 4 = 68
    private static final int MARGIN = 5;
    private static final int GRID_LINE_COLOR = 0x20FFFFFF;
    private static final int PLAYER_ICON_SIZE = 6;
    private static final float COORD_SCALE = 0.5f;

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ModConfig.showMinimap) return;
        if (mc.currentScreen != null) return;

        int pX = mc.player.chunkCoordX;
        int pZ = mc.player.chunkCoordZ;
        int startX = event.getResolution().getScaledWidth() - MAP_SIZE - MARGIN;

        GlStateManager.pushMatrix();
        GlStateManager.translate(startX, MARGIN, 0);

        GuiDraw.drawRect(0, 0, MAP_SIZE, MAP_SIZE, 0xFF000000);

        AsyncMapRenderer.evict(pX, pZ, RADIUS + 2);

        ChunkMapRenderer.drawChunkGrid(0, 0, CHUNK_SIZE, RADIUS, pX, pZ,
                GRID_LINE_COLOR, mc.player.world, mc.player.getUniqueID(), true);

        // プレイヤーアイコン（中心チャンク内のサブ位置を反映）
        float relX = (float) (mc.player.posX % 16) / 16.0f * CHUNK_SIZE;
        float relZ = (float) (mc.player.posZ % 16) / 16.0f * CHUNK_SIZE;
        if (relX < 0) relX += CHUNK_SIZE;
        if (relZ < 0) relZ += CHUNK_SIZE;
        float iconCX = RADIUS * CHUNK_SIZE + relX;
        float iconCY = RADIUS * CHUNK_SIZE + relZ;
        ChunkMapRenderer.drawPlayerIcon(iconCX, iconCY, mc.player.rotationYaw, PLAYER_ICON_SIZE);

        // 座標表示
        String coords = String.format("%d, %d", (int) mc.player.posX, (int) mc.player.posZ);
        GlStateManager.scale(COORD_SCALE, COORD_SCALE, COORD_SCALE);
        mc.fontRenderer.drawStringWithShadow(coords,
                MAP_SIZE - (float) mc.fontRenderer.getStringWidth(coords) / 2,
                (MAP_SIZE * 2) + 4, 0xFFFFFFFF);

        GlStateManager.popMatrix();
    }
}
