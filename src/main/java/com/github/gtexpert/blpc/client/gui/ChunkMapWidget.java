package com.github.gtexpert.blpc.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import com.github.gtexpert.blpc.client.map.AsyncMapRenderer;
import com.github.gtexpert.blpc.client.map.ChunkMapRenderer;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.ClientCache;
import com.github.gtexpert.blpc.common.network.MessageClaimChunk;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;

public class ChunkMapWidget extends Widget<ChunkMapWidget> implements Interactable {

    public static final int GRID = 15;
    private static final int RADIUS = GRID / 2;
    private static final int GRID_LINE_COLOR = 0x30FFFFFF;
    private static final int BORDER_COLOR = GuiColors.WHITE;

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

        ChunkMapRenderer.drawChunkGrid(ox, oy, cs, RADIUS, pX, pZ,
                GRID_LINE_COLOR, mc.world, mc.player.getUniqueID(), true, true);

        GuiDraw.drawBorderOutsideXYWH(ox, oy, mapPx, mapPx, 1, BORDER_COLOR);

        drawPlayerIcon(mc, ox, oy, cs);

        Stencil.remove();

        drawHoverTooltip(mc, context.getMouseX(), context.getMouseY());
    }

    private void drawHoverTooltip(Minecraft mc, int mouseX, int mouseY) {
        ClaimedChunkData d = ClientCache.get(selectedRX, selectedRZ);
        if (d == null) return;

        Party ownerParty = ClientPartyCache.getPartyByPlayer(d.ownerUUID);
        String partyLabel;
        int textColor;
        if (ownerParty != null) {
            partyLabel = ownerParty.getName();
            textColor = 0xFF000000 | (ownerParty.getColor() & 0xFFFFFF);
        } else {
            partyLabel = d.ownerName;
            textColor = GuiColors.WHITE;
        }

        FontRenderer fr = mc.fontRenderer;
        int tw = fr.getStringWidth(partyLabel);
        int tx = mouseX + 8;
        int ty = mouseY - fr.FONT_HEIGHT - 2;

        GuiDraw.drawRect(tx - 2, ty - 1, tw + 4, fr.FONT_HEIGHT + 2, 0xCC000000);
        GuiDraw.drawText(partyLabel, tx, ty, 1f, textColor, true);
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
}
