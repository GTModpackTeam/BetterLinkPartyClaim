package com.github.gtexpert.bquclaim.gui;

import java.util.UUID;
import java.util.function.Consumer;

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

import com.github.gtexpert.bquclaim.ModConfig;
import com.github.gtexpert.bquclaim.Tags;
import com.github.gtexpert.bquclaim.chunk.ClaimedChunkData;
import com.github.gtexpert.bquclaim.chunk.ClientCache;
import com.github.gtexpert.bquclaim.map.AsyncMapRenderer;
import com.github.gtexpert.bquclaim.map.TextureCache;
import com.github.gtexpert.bquclaim.network.MessageClaimChunk;
import com.github.gtexpert.bquclaim.network.ModNetwork;

public class ChunkMapScreen extends CustomModularScreen {

    private static final int BTN_SIZE = 16;
    private static final int BTN_GAP = 2;
    private static final int MAP_PX = 195;
    private static final int DIALOG_BG = 0xDD000000;
    private static final int DIALOG_BORDER = 0xFFFFFFFF;

    private ChunkMapWidget mapWidget;
    private IPanelHandler confirmHandler;
    private int pendingConfirmAction;

    public ChunkMapScreen() {
        super(Tags.MODID);
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        mapWidget = new ChunkMapWidget();

        int blockW = MAP_PX + BTN_SIZE;
        int leftOff = -blockW / 2;

        return new ModularPanel("chunk_map")
                .fullScreenInvisible()
                .child(mapWidget.size(MAP_PX, MAP_PX)
                        .leftRel(0.5f, leftOff, 0f)
                        .verticalCenter()
                        .background(new Rectangle().color(0xE0101010))
                        .overlay(new Rectangle().color(DIALOG_BORDER).hollow(1)))
                .child(createToolButtons()
                        .leftRel(0.5f, leftOff + MAP_PX, 0f)
                        .verticalCenter())
                .child(createChunkInfoText())
                .child(createCounterText("bquclaim.gui.claimed_chunks",
                        this::countMyClaims, ModConfig.maxClaimsPerPlayer, 16))
                .child(createCounterText("bquclaim.gui.loaded_chunks",
                        this::countMyForceLoads, ModConfig.maxForceLoadsPerPlayer, 4));
    }

    private TextWidget<?> createChunkInfoText() {
        return new TextWidget<>(IKey.dynamic(() -> {
            int rx = mapWidget.getSelectedRX();
            int rz = mapWidget.getSelectedRZ();
            ClaimedChunkData d = ClientCache.get(rx, rz);
            String owner = (d == null) ? IKey.lang("bquclaim.gui.unclaimed").get() : d.ownerName;
            String force = (d != null && d.isForceLoaded) ? IKey.lang("bquclaim.gui.force_loaded_suffix").get() : "";
            return IKey.lang("bquclaim.gui.chunk_info", rx, rz, owner, force).get();
        })).color(0xFFFFFFFF).shadow(true).right(4).bottom(28);
    }

    private TextWidget<?> createCounterText(String langKey,
                                            java.util.function.IntSupplier counter, int max, int bottom) {
        return new TextWidget<>(IKey.lang(langKey, () -> new Object[] { counter.getAsInt(), max }))
                .color(() -> counter.getAsInt() >= max ? 0xFFFF5555 : 0xFFFFFFFF)
                .shadow(true).right(4).bottom(bottom);
    }

    private ParentWidget<?> createToolButtons() {
        int y = 0;
        ButtonWidget<?> btnClose = createToolButton("X", "bquclaim.gui.close", y, mb -> close());
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnRedraw = createToolButton("R", "bquclaim.gui.redraw", y, mb -> {
            AsyncMapRenderer.clearCache();
            TextureCache.clear();
        });
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnUnclaimAll = createToolButton("C", "bquclaim.gui.unclaim_all", y,
                mb -> openConfirmDialog(1));
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnUnloadAll = createToolButton("L", "bquclaim.gui.unload_all", y,
                mb -> openConfirmDialog(2));
        y += BTN_SIZE + BTN_GAP;
        ButtonWidget<?> btnHelp = createToolButton("?", "bquclaim.gui.help", y, mb -> openHelpDialog());

        int totalH = BTN_SIZE * 5 + BTN_GAP * 4;
        return new ParentWidget<>()
                .size(BTN_SIZE, totalH)
                .child(btnClose).child(btnRedraw)
                .child(btnUnclaimAll).child(btnUnloadAll)
                .child(btnHelp);
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
        IKey title = IKey.lang(isUnclaim ? "bquclaim.gui.confirm_unclaim_title" :
                "bquclaim.gui.confirm_unload_title");
        IKey message = IKey.lang(isUnclaim ? "bquclaim.gui.confirm_unclaim_msg" :
                "bquclaim.gui.confirm_unload_msg");

        Dialog<Boolean> dialog = new Dialog<>("confirm_dialog", result -> {
            if (Boolean.TRUE.equals(result)) {
                executeBulkAction(pendingConfirmAction);
            }
        });
        dialog.setDisablePanelsBelow(true);
        dialog.setCloseOnOutOfBoundsClick(true);
        dialog.size(220, 70)
                .background(new Rectangle().color(DIALOG_BG))
                .overlay(new Rectangle().color(DIALOG_BORDER).hollow(1))
                .child(title.color(0xFFFFFFFF).shadow(true).asWidget().top(6).left(8))
                .child(message.color(0xFFAAAAAA).shadow(true).asWidget().top(18).left(8))
                .child(new ParentWidget<>()
                        .bottom(6).horizontalCenter().size(170, 20)
                        .child(new ButtonWidget<>().size(80, 20).pos(0, 0)
                                .overlay(IKey.lang("bquclaim.gui.yes"))
                                .onMousePressed(btn -> {
                                    dialog.closeWith(true);
                                    return true;
                                }))
                        .child(new ButtonWidget<>().size(80, 20).pos(90, 0)
                                .overlay(IKey.lang("bquclaim.gui.no"))
                                .onMousePressed(btn -> {
                                    dialog.closeWith(false);
                                    return true;
                                })));
        return dialog;
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
            Dialog<Void> dialog = new Dialog<>("help_dialog");
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(200, 100)
                    .background(new Rectangle().color(DIALOG_BG))
                    .overlay(new Rectangle().color(DIALOG_BORDER).hollow(1))
                    .child(IKey.lang("bquclaim.gui.controls").color(0xFFFFFFFF).shadow(true).asWidget()
                            .top(6).left(8))
                    .child(IKey.lang("bquclaim.gui.help_claim").color(0xFFAAAAAA).shadow(true).asWidget()
                            .top(20).left(8))
                    .child(IKey.lang("bquclaim.gui.help_unclaim").color(0xFFAAAAAA).shadow(true).asWidget()
                            .top(32).left(8))
                    .child(IKey.lang("bquclaim.gui.help_force").color(0xFFAAAAAA).shadow(true).asWidget()
                            .top(44).left(8))
                    .child(IKey.lang("bquclaim.gui.help_drag").color(0xFFAAAAAA).shadow(true).asWidget()
                            .top(56).left(8))
                    .child(ButtonWidget.panelCloseButton());
            return dialog;
        }, true).openPanel();
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
