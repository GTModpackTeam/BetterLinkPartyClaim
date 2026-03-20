package com.github.gtexpert.bquclaim.gui;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
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
    private static final int MAP_PX = 195; // 13px * 15chunks
    private static final int INFO_H = 36;

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
                // マップ (画面中央、独自背景+ボーダー)
                .child(mapWidget.size(MAP_PX, MAP_PX)
                        .leftRel(0.5f, leftOff, 0f)
                        .verticalCenter()
                        .background(new Rectangle().color(0xE0101010))
                        .overlay(new Rectangle().color(0xFFFFFFFF).hollow(1)))
                // ボタン列 (マップ右端にくっつく、マップ外)
                .child(createToolButtons()
                        .leftRel(0.5f, leftOff + MAP_PX, 0f)
                        .verticalCenter())
                // 情報 (画面右下、独立)
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    int rx = mapWidget.getSelectedRX();
                    int rz = mapWidget.getSelectedRZ();
                    ClaimedChunkData d = ClientCache.get(rx, rz);
                    String owner = (d == null) ? "Unclaimed" : d.ownerName;
                    String force = (d != null && d.isForceLoaded) ? " [F]" : "";
                    return String.format("Chunk %d, %d - %s%s", rx, rz, owner, force);
                })).color(0xFFFFFFFF).shadow(true).right(4).bottom(28))
                .child(new TextWidget<>(IKey.dynamic(() -> String.format(
                        "Claimed Chunks: %d / %d", countMyClaims(), ModConfig.maxClaimsPerPlayer)))
                                .color(() -> countMyClaims() >= ModConfig.maxClaimsPerPlayer ? 0xFFFF5555 : 0xFFFFFFFF)
                                .shadow(true).right(4).bottom(16))
                .child(new TextWidget<>(IKey.dynamic(() -> String.format(
                        "Loaded Chunks: %d / %d", countMyForceLoads(), ModConfig.maxForceLoadsPerPlayer)))
                                .color(() -> countMyForceLoads() >= ModConfig.maxForceLoadsPerPlayer ? 0xFFFF5555 :
                                        0xFFFFFFFF)
                                .shadow(true).right(4).bottom(4));
    }

    private ParentWidget<?> createToolButtons() {
        int y = 0;

        ButtonWidget<?> btnClose = new ButtonWidget<>();
        btnClose.size(BTN_SIZE, BTN_SIZE).pos(0, y)
                .overlay(IKey.str("X"))
                .onMousePressed(mb -> {
                    if (mb == 0) {
                        close();
                        return true;
                    }
                    return false;
                })
                .tooltip((Consumer<RichTooltip>) t -> t.addLine(IKey.str("Close")));
        y += BTN_SIZE + BTN_GAP;

        ButtonWidget<?> btnRedraw = new ButtonWidget<>();
        btnRedraw.size(BTN_SIZE, BTN_SIZE).pos(0, y)
                .overlay(IKey.str("R"))
                .onMousePressed(mb -> {
                    if (mb == 0) {
                        AsyncMapRenderer.clearCache();
                        TextureCache.clear();
                        return true;
                    }
                    return false;
                })
                .tooltip((Consumer<RichTooltip>) t -> t.addLine(IKey.str("Redraw map")));
        y += BTN_SIZE + BTN_GAP;

        ButtonWidget<?> btnUnclaimAll = new ButtonWidget<>();
        btnUnclaimAll.size(BTN_SIZE, BTN_SIZE).pos(0, y)
                .overlay(IKey.str("C"))
                .onMousePressed(mb -> {
                    if (mb == 0) {
                        openConfirmDialog(1);
                        return true;
                    }
                    return false;
                })
                .tooltip((Consumer<RichTooltip>) t -> t.addLine(IKey.str("Unclaim all chunks")));
        y += BTN_SIZE + BTN_GAP;

        ButtonWidget<?> btnUnloadAll = new ButtonWidget<>();
        btnUnloadAll.size(BTN_SIZE, BTN_SIZE).pos(0, y)
                .overlay(IKey.str("L"))
                .onMousePressed(mb -> {
                    if (mb == 0) {
                        openConfirmDialog(2);
                        return true;
                    }
                    return false;
                })
                .tooltip((Consumer<RichTooltip>) t -> t.addLine(IKey.str("Unload all chunks")));
        y += BTN_SIZE + BTN_GAP;

        ButtonWidget<?> btnHelp = new ButtonWidget<>();
        btnHelp.size(BTN_SIZE, BTN_SIZE).pos(0, y)
                .overlay(IKey.str("?"))
                .onMousePressed(mb -> {
                    if (mb == 0) {
                        openHelpDialog();
                        return true;
                    }
                    return false;
                })
                .tooltip((Consumer<RichTooltip>) t -> t.addLine(IKey.str("Help")));

        int totalH = BTN_SIZE * 5 + BTN_GAP * 4;
        return new ParentWidget<>()
                .size(BTN_SIZE, totalH)
                .child(btnClose)
                .child(btnRedraw)
                .child(btnUnclaimAll)
                .child(btnUnloadAll)
                .child(btnHelp);
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
        String title = (pendingConfirmAction == 1) ? "Unclaim all chunks?" : "Unload all chunks?";
        String message = (pendingConfirmAction == 1) ? "All your claimed chunks will be released." :
                "All your force-loaded chunks will be unloaded.";

        Dialog<Boolean> dialog = new Dialog<>("confirm_dialog", result -> {
            if (Boolean.TRUE.equals(result)) {
                executeBulkAction(pendingConfirmAction);
            }
        });
        dialog.setDisablePanelsBelow(true);
        dialog.setCloseOnOutOfBoundsClick(true);
        dialog.size(220, 70)
                .background(new Rectangle().color(0xDD000000))
                .overlay(new Rectangle().color(0xFFFFFFFF).hollow(1))
                .child(new TextWidget<>(title).color(0xFFFFFFFF).shadow(true).top(6).left(8))
                .child(new TextWidget<>(message).color(0xFFAAAAAA).shadow(true).top(18).left(8))
                .child(new ParentWidget<>()
                        .bottom(6).horizontalCenter().size(170, 20)
                        .child(new ButtonWidget<>().size(80, 20).pos(0, 0)
                                .overlay(IKey.str("Yes"))
                                .onMousePressed(btn -> {
                                    dialog.closeWith(true);
                                    return true;
                                }))
                        .child(new ButtonWidget<>().size(80, 20).pos(90, 0)
                                .overlay(IKey.str("No"))
                                .onMousePressed(btn -> {
                                    dialog.closeWith(false);
                                    return true;
                                })));
        return dialog;
    }

    private void executeBulkAction(int action) {
        Minecraft mc = Minecraft.getMinecraft();
        int mode = (action == 1) ? 1 : 2;
        for (ClaimedChunkData d : ClientCache.getAll()) {
            if (d.ownerUUID.equals(mc.player.getUniqueID())) {
                if (action == 1 || (action == 2 && d.isForceLoaded)) {
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
                    .background(new Rectangle().color(0xDD000000))
                    .overlay(new Rectangle().color(0xFFFFFFFF).hollow(1))
                    .child(new TextWidget<>("Controls").color(0xFFFFFFFF).shadow(true).top(6).left(8))
                    .child(new TextWidget<>("Left click: Claim").color(0xFFAAAAAA).shadow(true).top(20).left(8))
                    .child(new TextWidget<>("Right click: Unclaim").color(0xFFAAAAAA).shadow(true).top(32).left(8))
                    .child(new TextWidget<>("Shift+Left: Claim+Force").color(0xFFAAAAAA).shadow(true).top(44).left(8))
                    .child(new TextWidget<>("Drag: Bulk operation").color(0xFFAAAAAA).shadow(true).top(56).left(8))
                    .child(ButtonWidget.panelCloseButton());
            return dialog;
        }, true).openPanel();
    }

    private int countMyClaims() {
        Minecraft mc = Minecraft.getMinecraft();
        int count = 0;
        for (ClaimedChunkData d : ClientCache.getAll()) {
            if (d.ownerUUID.equals(mc.player.getUniqueID())) count++;
        }
        return count;
    }

    private int countMyForceLoads() {
        Minecraft mc = Minecraft.getMinecraft();
        int count = 0;
        for (ClaimedChunkData d : ClientCache.getAll()) {
            if (d.ownerUUID.equals(mc.player.getUniqueID()) && d.isForceLoaded) count++;
        }
        return count;
    }
}
