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
import com.cleanroommc.modularui.widgets.layout.Flow;

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
                        this::countMyClaims, () -> ModConfig.claims.maxClaimsPerPlayer, 16))
                .child(createCounterText("blpc.map.loaded_chunks",
                        this::countMyForceLoads, () -> ModConfig.claims.maxForceLoadsPerPlayer, 4));
    }

    private TextWidget<?> createCounterText(String langKey,
                                            IntSupplier counter, IntSupplier max, int bottom) {
        return new TextWidget<>(IKey.lang(langKey, () -> new Object[] { counter.getAsInt(), max.getAsInt() }))
                .color(() -> counter.getAsInt() >= max.getAsInt() ? GuiColors.RED : GuiColors.WHITE)
                .shadow(true).right(4).bottom(bottom);
    }

    private ParentWidget<?> createToolButtons() {
        int totalH = BTN_SIZE * 5 + BTN_GAP * 4;
        return Flow.col()
                .size(BTN_SIZE, totalH)
                .childPadding(BTN_GAP)
                .child(createToolButton("X", mb -> close(), "blpc.map.close"))
                .child(createToolButton("P", mb -> openPartyScreen(), "blpc.map.party"))
                .child(createToolButton("R", mb -> {
                    AsyncMapRenderer.clearCache();
                    TextureCache.clear();
                }, "blpc.map.redraw"))
                .child(createToolButton("C", mb -> openConfirmDialog(1),
                        "blpc.map.unclaim_all", "blpc.map.help_claim", "blpc.map.help_unclaim"))
                .child(createToolButton("L", mb -> openConfirmDialog(2),
                        "blpc.map.unload_all", "blpc.map.help_force", "blpc.map.help_drag"));
    }

    private ButtonWidget<?> createToolButton(String label, Consumer<Integer> action,
                                             String... tooltipKeys) {
        ButtonWidget<?> btn = new ButtonWidget<>();
        btn.size(BTN_SIZE, BTN_SIZE)
                .overlay(IKey.str(label))
                .onMousePressed(mb -> {
                    if (mb == 0) {
                        action.accept(mb);
                        return true;
                    }
                    return false;
                });
        if (tooltipKeys.length > 0) {
            btn.addTooltipLine(IKey.lang(tooltipKeys[0]));
        }
        if (tooltipKeys.length > 1) {
            btn.addTooltipLine(IKey.str("")); // 区切り
        }
        for (int i = 1; i < tooltipKeys.length; i++) {
            btn.addTooltipLine(IKey.lang(tooltipKeys[i]).color(GuiColors.GRAY));
        }
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
        return (int) ClientCache.getAll().stream()
                .filter(d -> d.ownerUUID.equals(myId) && (!forceLoadedOnly || d.isForceLoaded))
                .count();
    }
}
