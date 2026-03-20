package com.github.gtexpert.bquclaim.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
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

    public static final int GRID = 15;
    private static final int RADIUS = GRID / 2;
    private static final int GRID_LINE_COLOR = 0x30FFFFFF;
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int HATCHING_COLOR = 0xAAFF0000;

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

    private int getChunkSize() {
        return Math.min(getArea().width, getArea().height) / GRID;
    }

    private int getMapPixels() {
        return getChunkSize() * GRID;
    }

    private int getOriginX() {
        return (getArea().width - getMapPixels()) / 2;
    }

    private int getOriginY() {
        return (getArea().height - getMapPixels()) / 2;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        Minecraft mc = Minecraft.getMinecraft();
        int cs = getChunkSize();
        int mapPx = getMapPixels();
        int ox = getOriginX();
        int oy = getOriginY();
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
                    drawHatching(dx, dy, cs, cs, HATCHING_COLOR);
                }
            }
        }

        drawGridLines(ox, oy, mapPx, cs);
        drawBorder(ox, oy, mapPx);
        drawPlayerIcon(mc, ox, oy, cs);

        Stencil.remove();
    }

    private void drawGridLines(int ox, int oy, int mapPx, int cs) {
        for (int i = 1; i < GRID; i++) {
            GuiDraw.drawRect(ox + i * cs, oy, 1, mapPx, GRID_LINE_COLOR);
            GuiDraw.drawRect(ox, oy + i * cs, mapPx, 1, GRID_LINE_COLOR);
        }
    }

    private void drawBorder(int ox, int oy, int mapPx) {
        GuiDraw.drawBorderOutsideXYWH(ox, oy, mapPx, mapPx, 1, BORDER_COLOR);
    }

    private void drawPlayerIcon(Minecraft mc, int ox, int oy, int cs) {
        float relX = (float) (mc.player.posX % 16) / 16.0f * cs;
        float relZ = (float) (mc.player.posZ % 16) / 16.0f * cs;
        if (relX < 0) relX += cs;
        if (relZ < 0) relZ += cs;
        float iconCX = ox + RADIUS * cs + relX;
        float iconCY = oy + RADIUS * cs + relZ;
        int iconSize = Math.max(4, cs / 2);
        ChunkMapRenderer.drawPlayerIcon(iconCX, iconCY, mc.player.rotationYaw, iconSize);
    }

    private void updateSelectedChunk(int mouseX, int mouseY, int ox, int oy, int cs, int pX, int pZ) {
        if (cs <= 0) return;
        selectedRX = (mouseX - ox) / cs - RADIUS + pX;
        selectedRZ = (mouseY - oy) / cs - RADIUS + pZ;
    }

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
        int cs = getChunkSize();
        if (cs <= 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        int ox = getOriginX();
        int oy = getOriginY();
        int mouseX = getContext().getMouseX();
        int mouseY = getContext().getMouseY();
        int rx = (mouseX - ox) / cs - RADIUS + mc.player.chunkCoordX;
        int rz = (mouseY - oy) / cs - RADIUS + mc.player.chunkCoordZ;

        if (rx == lastDragX && rz == lastDragZ) return;

        if (mouseButton == 0) {
            int mode = Interactable.hasShiftDown() ? MessageClaimChunk.MODE_TOGGLE_FORCE : MessageClaimChunk.MODE_CLAIM;
            ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(rx, rz, mode));
        } else if (mouseButton == 1) {
            ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(rx, rz, MessageClaimChunk.MODE_UNCLAIM));
        }

        if (mouseButton == 0 || mouseButton == 1) {
            lastDragX = rx;
            lastDragZ = rz;
            Interactable.playButtonClickSound();
        }
    }

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
                buffer.pos(x + Math.max(0, i - h), y + Math.min(i, h), 0).color(r, g, b, a).endVertex();
                buffer.pos(x + Math.min(i, w), y + Math.max(0, i - w), 0).color(r, g, b, a).endVertex();
            }
        });
    }
}
