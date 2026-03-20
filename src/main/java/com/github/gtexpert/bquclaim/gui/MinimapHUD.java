package com.github.gtexpert.bquclaim.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.github.gtexpert.bquclaim.ModConfig;
import com.github.gtexpert.bquclaim.map.ChunkMapRenderer;

public class MinimapHUD {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final int mapSize = 64;
    private final int zoomSize = 4;
    private final int margin = 5;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ModConfig.showMinimap) return;
        if (mc.currentScreen != null) return;

        int startX = event.getResolution().getScaledWidth() - mapSize - margin;
        int startY = margin;
        int pX = mc.player.chunkCoordX;
        int pZ = mc.player.chunkCoordZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(startX, startY, 0);

        // 背景
        GuiDraw.drawRect(0, 0, mapSize, mapSize, 0xFF000000);

        // チャンク描画
        int range = (mapSize / zoomSize) / 2;
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                int dx = (mapSize / 2) + (x * zoomSize);
                int dy = (mapSize / 2) + (z * zoomSize);
                ChunkMapRenderer.drawChunkTerrain(pX + x, pZ + z, dx, dy, zoomSize, mc.player.world);
                ChunkMapRenderer.drawClaimOverlay(pX + x, pZ + z, dx, dy, zoomSize, mc.player.getUniqueID());
            }
        }

        // プレイヤーアイコン（中心固定、サブオフセットなし）
        ChunkMapRenderer.drawPlayerIcon(mapSize / 2f, mapSize / 2f, mc.player.rotationYaw + 180.0f, 6, 0, 0);

        // 座標表示（50%スケール）
        String coords = String.format("%d, %d", (int) mc.player.posX, (int) mc.player.posZ);
        GlStateManager.scale(0.5, 0.5, 0.5);
        mc.fontRenderer.drawStringWithShadow(coords,
                mapSize - (float) mc.fontRenderer.getStringWidth(coords) / 2,
                (mapSize * 2) + 4, 0xFFFFFFFF);

        GlStateManager.popMatrix();
    }
}
