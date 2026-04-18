package com.github.gtexpert.blpc.client.gui.party;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Transfer ownership panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * Displays a scrollable list of party members (excluding the current owner).
 * Clicking a member's name transfers ownership to them.
 */
public class TransferOwnerDialog {

    public static final String PANEL_ID = "blpc.party.dialog.transfer";

    /** Builds the transfer ownership panel with a member selection list. */
    public static ModularPanel build(ModularPanel parentPanel) {
        UUID myId = Minecraft.getMinecraft().player.getUniqueID();
        Party party = ClientPartyCache.getPartyByPlayer(myId);
        if (party == null) return new ModularPanel(PANEL_ID);

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(PanelSizes.STANDARD_W, PanelSizes.STANDARD_H);

        PanelBuilder.addHeader(panel, "blpc.party.transfer_title");

        ListWidget<?, ?> list = new ListWidget<>()
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .children(party.getMembers().entrySet().stream()
                        .filter(e -> !e.getKey().equals(myId))
                        .collect(Collectors.toList()),
                        entry -> createTransferRow(entry, panel, parentPanel));

        PanelBuilder.addList(panel, list);

        Runnable syncListener = () -> {
            if (!panel.isOpen()) return;
            panel.closeIfOpen();
        };
        ClientPartyCache.addSyncListener(syncListener);
        panel.onCloseAction(() -> ClientPartyCache.removeSyncListener(syncListener));

        return panel;
    }

    private static Flow createTransferRow(Map.Entry<UUID, PartyRole> entry,
                                          ModularPanel panel, ModularPanel parentPanel) {
        UUID memberId = entry.getKey();
        String memberName = PartyWidgets.getDisplayName(memberId);
        PartyRole role = entry.getValue();
        String roleStr = IKey.lang("blpc.party.role." + role.name().toLowerCase()).get();

        return Flow.row()
                .height(PanelSizes.BTN_H)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new ButtonWidget<>().widthRel(1f).height(PanelSizes.BTN_H).padding(4, 0, 0, 0)
                        .hoverBackground(new Rectangle().color(GuiColors.HOVER))
                        .overlay(IKey.str(memberName + " [" + roleStr + "]").alignment(Alignment.CenterLeft))
                        .onMousePressed(btn -> {
                            ModNetwork.INSTANCE.sendToServer(
                                    MessagePartyAction.transferOwnership(memberName));
                            panel.closeIfOpen();
                            parentPanel.closeIfOpen();
                            return true;
                        }));
    }
}
