package com.github.gtexpert.bquclaim.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.widget.Widget;

import com.github.gtexpert.bquclaim.chunk.ClaimedChunkData;
import com.github.gtexpert.bquclaim.chunk.ClientCache;
import com.github.gtexpert.bquclaim.map.AsyncMapRenderer;
import com.github.gtexpert.bquclaim.map.ChunkMapRenderer;
import com.github.gtexpert.bquclaim.network.MessageClaimChunk;
import com.github.gtexpert.bquclaim.network.ModNetwork;

public class ChunkMapWidget extends Widget<ChunkMapWidget> implements Interactable {

    public static final int GRID = 15; // 15x15チャンク
    private static final int RADIUS = GRID / 2; // ±7

    private int selectedRX = Integer.MIN_VALUE;
    private int selectedRZ = Integer.MIN_VALUE;
    private int lastDragX = Integer.MIN_VALUE;
    private int lastDragZ = Integer.MIN_VALUE;

    public int getSelectedRX() {
        return selectedRX;
    }

    public int getSelectedRZ() {
        return selectedRZ;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = getArea().width;
        int h = getArea().height;
        int side = Math.min(w, h); // 正方形に使うサイズ
        int cs = side / GRID; // 1チャンクあたりのピクセル数（動的）
        int mapPx = cs * GRID; // 実際に描画する正方形サイズ
        int ox = (w - mapPx) / 2; // 余りをセンタリング
        int oy = (h - mapPx) / 2;
        int pX = mc.player.chunkCoordX;
        int pZ = mc.player.chunkCoordZ;

        AsyncMapRenderer.evict(pX, pZ, RADIUS + 2);

        updateSelectedChunk(context.getMouseX(), context.getMouseY(), ox, oy, cs, pX, pZ);

        Stencil.apply(ox, oy, mapPx, mapPx, context);

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int rx = pX + x;
                int rz = pZ + z;
                int dx = ox + (x + RADIUS) * cs;
                int dy = oy + (z + RADIUS) * cs;

                ChunkMapRenderer.drawChunkTerrain(rx, rz, dx, dy, cs, mc.world);
                ChunkMapRenderer.drawClaimOverlay(rx, rz, dx, dy, cs, mc.player.getUniqueID());

                ClaimedChunkData d = ClientCache.get(rx, rz);
                if (d != null && d.isForceLoaded) {
                    drawHatching(dx, dy, cs, cs, 0xAAFF0000);
                }
            }
        }

        // マップ枠線（正方形部分のみ）
        com.cleanroommc.modularui.drawable.GuiDraw.drawRect(ox - 1, oy - 1, mapPx + 2, 1, 0xFFFFFFFF);
        com.cleanroommc.modularui.drawable.GuiDraw.drawRect(ox - 1, oy + mapPx, mapPx + 2, 1, 0xFFFFFFFF);
        com.cleanroommc.modularui.drawable.GuiDraw.drawRect(ox - 1, oy, 1, mapPx, 0xFFFFFFFF);
        com.cleanroommc.modularui.drawable.GuiDraw.drawRect(ox + mapPx, oy, 1, mapPx, 0xFFFFFFFF);

        // プレイヤーアイコン
        float relX = (float) (mc.player.posX % 16) / 16.0f * cs;
        float relZ = (float) (mc.player.posZ % 16) / 16.0f * cs;
        if (relX < 0) relX += cs;
        if (relZ < 0) relZ += cs;
        float iconCX = ox + RADIUS * cs + relX;
        float iconCY = oy + RADIUS * cs + relZ;
        int iconSize = Math.max(4, cs / 2);
        ChunkMapRenderer.drawPlayerIcon(iconCX, iconCY, mc.player.rotationYaw, iconSize, 0, 0);

        Stencil.remove();
    }

    private void updateSelectedChunk(int mouseX, int mouseY, int ox, int oy, int cs, int pX, int pZ) {
        if (cs <= 0) return;
        int rx = (mouseX - ox) / cs - RADIUS + pX;
        int rz = (mouseY - oy) / cs - RADIUS + pZ;
        selectedRX = rx;
        selectedRZ = rz;
    }

    // --- Mouse interaction ---

    @Override
    public Result onMousePressed(int mouseButton) {
        handleAction(mouseButton);
        return Result.SUCCESS;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        handleAction(mouseButton);
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        lastDragX = Integer.MIN_VALUE;
        lastDragZ = Integer.MIN_VALUE;
        return true;
    }

    private void handleAction(int mouseButton) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = getArea().width;
        int h = getArea().height;
        int side = Math.min(w, h);
        int cs = side / GRID;
        if (cs <= 0) return;
        int ox = (w - cs * GRID) / 2;
        int oy = (h - cs * GRID) / 2;

        int mouseX = getContext().getMouseX();
        int mouseY = getContext().getMouseY();
        int rx = (mouseX - ox) / cs - RADIUS + mc.player.chunkCoordX;
        int rz = (mouseY - oy) / cs - RADIUS + mc.player.chunkCoordZ;

        if (rx == lastDragX && rz == lastDragZ) return;

        if (mouseButton == 0) {
            int mode = Interactable.hasShiftDown() ? 2 : 0;
            ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(rx, rz, mode));
        } else if (mouseButton == 1) {
            ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(rx, rz, 1));
        }

        if (mouseButton == 0 || mouseButton == 1) {
            lastDragX = rx;
            lastDragZ = rz;
            Interactable.playButtonClickSound();
        }
    }

    // --- フルマップ専用描画 ---

    private void drawHatching(int x, int y, int w, int h, int color) {
        int spacing = 4;
        GlStateManager.glLineWidth(1.0F);
        Platform.setupDrawColor();
        Platform.startDrawing(Platform.DrawMode.LINES, Platform.VertexFormat.POS_COLOR, buffer -> {
            int r = Color.getRed(color);
            int g = Color.getGreen(color);
            int b = Color.getBlue(color);
            int a = Color.getAlpha(color);
            for (int i = 0; i <= w + h; i += spacing) {
                int xStart = Math.max(0, i - h);
                int yStart = Math.min(i, h);
                int xEnd = Math.min(i, w);
                int yEnd = Math.max(0, i - w);
                buffer.pos(x + xStart, y + yStart, 0).color(r, g, b, a).endVertex();
                buffer.pos(x + xEnd, y + yEnd, 0).color(r, g, b, a).endVertex();
            }
        });
    }
}
