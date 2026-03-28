package com.github.gtexpert.blpc.client.gui;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.client.gui.party.MainPanel;
import com.github.gtexpert.blpc.client.gui.party.PartyWidgets;
import com.github.gtexpert.blpc.client.gui.party.widget.ConfirmDialog;
import com.github.gtexpert.blpc.client.map.AsyncMapRenderer;
import com.github.gtexpert.blpc.client.map.TextureCache;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.ClientCache;
import com.github.gtexpert.blpc.common.network.MessageClaimChunk;
import com.github.gtexpert.blpc.common.network.ModNetwork;

public class ChunkMapScreen extends CustomModularScreen {

    private static final int BTN_SIZE = 16;
    private static final int BTN_GAP = 2;
    private static final int MAP_PX = 195;
    private static final int DIALOG_BORDER = GuiColors.WHITE;

    private ChunkMapWidget mapWidget;
    private IPanelHandler confirmHandler;
    private IPanelHandler partyHandler;
    private int pendingConfirmAction;

    public ChunkMapScreen() {
        super(Tags.MODID);
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        mapWidget = new ChunkMapWidget();

        int blockW = MAP_PX + BTN_SIZE;
        int leftOff = -blockW / 2;

        return new ModularPanel("blpc.map")
                .fullScreenInvisible()
                .child(mapWidget.size(MAP_PX, MAP_PX)
                        .leftRel(0.5f, leftOff, 0f)
                        .verticalCenter()
                        .background(new Rectangle().color(0xE0101010))
                        .overlay(new Rectangle().color(DIALOG_BORDER).hollow(1)))
                .child(createToolButtons()
                        .leftRel(0.5f, leftOff + MAP_PX, 0f)
                        .verticalCenter())
                .child(createCounterText("blpc.map.claimed_chunks",
                        this::countMyClaims, ModConfig.maxClaimsPerPlayer, 16))
                .child(createCounterText("blpc.map.loaded_chunks",
                        this::countMyForceLoads, ModConfig.maxForceLoadsPerPlayer, 4));
    }

    private TextWidget<?> createCounterText(String langKey,
                                            IntSupplier counter, int max, int bottom) {
        return new TextWidget<>(IKey.lang(langKey, () -> new Object[] { counter.getAsInt(), max }))
                .color(() -> counter.getAsInt() >= max ? GuiColors.RED : GuiColors.WHITE)
                .shadow(true).right(4).bottom(bottom);
    }

    private ParentWidget<?> createToolButtons() {
        int y = 0;
        ButtonWidget<?> btnClose = createToolButton("X", "blpc.map.close", y, mb -> close());
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnParty = createToolButton("P", "blpc.map.party", y, mb -> openPartyScreen());
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnRedraw = createToolButton("R", "blpc.map.redraw", y, mb -> {
            AsyncMapRenderer.clearCache();
            TextureCache.clear();
        });
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnUnclaimAll = createToolButton("C", "blpc.map.unclaim_all", y,
                mb -> openConfirmDialog(1));
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnUnloadAll = createToolButton("L", "blpc.map.unload_all", y,
                mb -> openConfirmDialog(2));
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnHelp = createToolButton("?", "blpc.map.help", y, mb -> openHelpDialog());

        int totalH = BTN_SIZE * 6 + BTN_GAP * 5;
        return new ParentWidget<>()
                .size(BTN_SIZE, totalH)
                .child(btnClose).child(btnRedraw)
                .child(btnUnclaimAll).child(btnUnloadAll)
                .child(btnHelp).child(btnParty);
    }

    private ButtonWidget<?> createToolButton(String label, String tooltipKey, int y, Consumer<Integer> action) {
        ButtonWidget<?> btn = new ButtonWidget<>();
        btn.size(BTN_SIZE, BTN_SIZE).pos(0, y)
                .overlay(IKey.str(label))
                .onMousePressed(mb -> {
                    if (mb == 0) {
                        action.accept(mb);
                        return true;
                    }
                    return false;
                })
                .addTooltipLine(IKey.lang(tooltipKey));
        return btn;
    }

    private void openConfirmDialog(int action) {
        pendingConfirmAction = action;
        if (confirmHandler != null) {
            confirmHandler.deleteCachedPanel();
        } else {
            confirmHandler = IPanelHandler.simple(
                    getMainPanel(), (parentPanel, player) -> buildConfirmDialog(), true);
        }
        confirmHandler.openPanel();
    }

    private Dialog<Boolean> buildConfirmDialog() {
        boolean isUnclaim = pendingConfirmAction == 1;
        int action = pendingConfirmAction;
        return ConfirmDialog.builder("blpc.map.dialog.confirm")
                .title(isUnclaim ? "blpc.map.confirm_unclaim_title" : "blpc.map.confirm_unload_title")
                .message(isUnclaim ? "blpc.map.confirm_unclaim_msg" : "blpc.map.confirm_unload_msg")
                .onConfirm(() -> executeBulkAction(action))
                .closeParent(false)
                .build(getMainPanel());
    }

    private void executeBulkAction(int action) {
        UUID myId = Minecraft.getMinecraft().player.getUniqueID();
        int mode = (action == 1) ? MessageClaimChunk.MODE_UNCLAIM : MessageClaimChunk.MODE_TOGGLE_FORCE;
        for (ClaimedChunkData d : ClientCache.getAll()) {
            if (d.ownerUUID.equals(myId)) {
                if (action == 1 || d.isForceLoaded) {
                    ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(d.x, d.z, mode));
                }
            }
        }
    }

    private void openHelpDialog() {
        IPanelHandler.simple(getMainPanel(), (parentPanel, player) -> {
            Dialog<Void> dialog = new Dialog<>("blpc.map.dialog.help");
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(200, 100)
                    .child(IKey.lang("blpc.map.controls").color(GuiColors.WHITE).shadow(true).asWidget()
                            .top(6).left(8))
                    .child(IKey.lang("blpc.map.help_claim").color(GuiColors.GRAY).shadow(true).asWidget()
                            .top(20).left(8))
                    .child(IKey.lang("blpc.map.help_unclaim").color(GuiColors.GRAY).shadow(true).asWidget()
                            .top(32).left(8))
                    .child(IKey.lang("blpc.map.help_force").color(GuiColors.GRAY).shadow(true).asWidget()
                            .top(44).left(8))
                    .child(IKey.lang("blpc.map.help_drag").color(GuiColors.GRAY).shadow(true).asWidget()
                            .top(56).left(8))
                    .child(ButtonWidget.panelCloseButton());
            return dialog;
        }, true).openPanel();
    }

    private void openPartyScreen() {
        PartyWidgets.resetSubPanelHandler();
        if (partyHandler != null) {
            partyHandler.deleteCachedPanel();
        } else {
            partyHandler = IPanelHandler.simple(getMainPanel(), (parentPanel, player) -> {
                return MainPanel.build(
                        Minecraft.getMinecraft().player.getUniqueID());
            }, true);
        }
        partyHandler.openPanel();
    }

    private int countMyClaims() {
        return countMyChunks(false);
    }

    private int countMyForceLoads() {
        return countMyChunks(true);
    }

    private int countMyChunks(boolean forceLoadedOnly) {
        UUID myId = Minecraft.getMinecraft().player.getUniqueID();
        int count = 0;
        for (ClaimedChunkData d : ClientCache.getAll()) {
            if (d.ownerUUID.equals(myId) && (!forceLoadedOnly || d.isForceLoaded)) count++;
        }
        return count;
    }
}
