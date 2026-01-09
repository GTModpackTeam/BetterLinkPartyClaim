package com.sysnote8.bquclaim.hud;

import com.sysnote8.bquclaim.ModConfig;
import com.sysnote8.bquclaim.Tags;
import com.sysnote8.bquclaim.chunk.ClaimedChunkData;
import com.sysnote8.bquclaim.chunk.ClientCache;
import com.sysnote8.bquclaim.gui.AsyncMapRenderer;
import com.sysnote8.bquclaim.gui.TextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class MinimapHUD {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final int mapSize = 128; // ミニマップのサイズ(px)
    private final int zoomSize = 8;  // 1チャンクを何ピクセルで描くか

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ModConfig.showMinimap) return;
        if (mc.currentScreen != null) return;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();

        int startX = event.getResolution().getScaledWidth() - mapSize - 10;
        int startY = 10;
        GlStateManager.translate(startX, startY, 0);

        // --- 1. まず「四角いまま」地図を描画 ---
        // (背後の黒背景)
        Gui.drawRect(0, 0, mapSize, mapSize, 0xFF000000);

        int pX = mc.player.chunkCoordX;
        int pZ = mc.player.chunkCoordZ;
        int range = mapSize / zoomSize / 2;
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                renderMinimapChunk(pX + x, pZ + z, (mapSize / 2) + (x * zoomSize), (mapSize / 2) + (z * zoomSize));
            }
        }

        GlStateManager.enableBlend();
// 「上から重ねる画像（マスク）のアルファ値を優先する」設定
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

// マスク画像をバインド（外側が黒・内側が透明な画像）
        mc.getTextureManager().bindTexture(new ResourceLocation(Tags.MODID, "textures/gui/minimap_mask.png"));
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

// マスクを描画。これで「角」だけが上書きされる
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, mapSize, mapSize, (float)mapSize, (float)mapSize);

        GlStateManager.disableBlend();

// 最後にアイコン
        drawSmallPlayerIcon(mapSize / 2, mapSize / 2);
    }

    private void drawCircle(int x, int y, int radius, int color) {
        float a = (float)(color >> 24 & 255) / 255.0F;
        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(r, g, b, a);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION);

        // 360度を細かく分割して多角形で円を表現
        for (int i = 0; i < 360; i++) {
            double angle = Math.toRadians(i);
            bufferbuilder.pos(x + Math.sin(angle) * radius, y + Math.cos(angle) * radius, 0).endVertex();
        }

        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    /** * 円の縁を描画するヘルパーメソッド
     */
    private void drawCircleOutline(int x, int y, int radius, float thickness, int color) {
        float a = (float)(color >> 24 & 255) / 255.0F;
        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(r, g, b, a);
        GL11.glLineWidth(thickness);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);
        for (int i = 0; i < 360; i++) {
            double angle = Math.toRadians(i);
            bufferbuilder.pos(x + Math.sin(angle) * radius, y + Math.cos(angle) * radius, 0).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }
    private void renderMinimapChunk(int rx, int rz, int dx, int dy) {
        // 1. 地形テクスチャの取得
        int[] colors = AsyncMapRenderer.getColors(rx, rz);
        if (colors != null) {
            TextureCache.ChunkTexture tex = TextureCache.getOrCreate(rx, rz, colors);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            tex.bind();
            // zoomSizeに合わせて描画 (例: 8x8ピクセル)
            Gui.drawModalRectWithCustomSizedTexture(dx, dy, 0, 0, zoomSize, zoomSize, 16, 16);
        }

        // 2. 領地オーバーレイ (ClientCacheから取得)
        ClaimedChunkData d = ClientCache.get(rx, rz);
        if (d != null) {
            int color = d.ownerUUID.equals(mc.player.getUniqueID()) ? 0x4400FF00 : 0x44FF0000;
            Gui.drawRect(dx, dy, dx + zoomSize, dy + zoomSize, color);

            // 境界線（ミニマップが小さい場合は1pxの線で十分）
            renderSmallBorder(rx, rz, dx, dy, d.ownerUUID);
        }
    }

    private void drawSmallPlayerIcon(int centerX, int centerY) {
        float yaw = mc.player.rotationYawHead;

        mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation("textures/map/map_icons.png"));

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.rotate(yaw, 0, 0, 1);
        // アイコンサイズを少し小さめ(6x6)にする
        GlStateManager.translate(-3, -3, 0);

        // プレイヤーの矢印テクスチャ (UV: 0, 0, Size: 8x8 on a 32x32 sheet)
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 6, 6, 32, 32);
        GlStateManager.popMatrix();
    }

    private void renderSmallBorder(int rx, int rz, int dx, int dy, java.util.UUID owner) {
        int borderColor = 0xFFFFFFFF;
        // 上下左右を確認して1pxの線を引く
        if (!isSameOwner(rx, rz - 1, owner)) Gui.drawRect(dx, dy, dx + zoomSize, dy + 1, borderColor);
        if (!isSameOwner(rx, rz + 1, owner))
            Gui.drawRect(dx, dy + zoomSize - 1, dx + zoomSize, dy + zoomSize, borderColor);
        if (!isSameOwner(rx - 1, rz, owner)) Gui.drawRect(dx, dy, dx + 1, dy + zoomSize, borderColor);
        if (!isSameOwner(rx + 1, rz, owner))
            Gui.drawRect(dx + zoomSize - 1, dy, dx + zoomSize, dy + zoomSize, borderColor);
    }

    private boolean isSameOwner(int rx, int rz, java.util.UUID currentOwner) {
        // クライアント側のキャッシュから隣接チャンクのデータを取得
        ClaimedChunkData neighbor = ClientCache.get(rx, rz);

        // 隣が誰のものでもない場合は false
        if (neighbor == null) return false;

        // 隣の所有者 UUID が、今描画しているチャンクの所有者と一致するかチェック
        return neighbor.ownerUUID.equals(currentOwner);
    }
}
