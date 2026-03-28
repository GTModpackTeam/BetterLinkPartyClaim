package com.github.gtexpert.blpc.client.gui.party;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

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
    private static final int W = 220;
    private static final int H = 180;

    /** Builds the transfer ownership panel with a member selection list. */
    public static ModularPanel build(ModularPanel parentPanel) {
        UUID myId = Minecraft.getMinecraft().player.getUniqueID();
        Party party = ClientPartyCache.getPartyByPlayer(myId);
        if (party == null) return new ModularPanel(PANEL_ID);

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(W, H);

        // Centered title
        panel.child(IKey.lang("blpc.party.transfer_title").color(GuiColors.WHITE).shadow(true)
                .asWidget().alignment(Alignment.Center).left(0).right(0).top(6).height(10));
        // Centered subtitle
        panel.child(IKey.lang("blpc.party.transfer_msg").color(GuiColors.GRAY).shadow(true)
                .asWidget().alignment(Alignment.Center).left(0).right(0).top(18).height(10));
        panel.child(ButtonWidget.panelCloseButton());

        // Search field (visual placeholder)
        panel.child(new TextFieldWidget()
                .hintText(IKey.lang("blpc.party.search").get())
                .left(8).right(8).top(32).height(14));

        panel.child(new ListWidget<>()
                .left(8).right(8).top(50).bottom(8)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .children(party.getMembers().entrySet(),
                        entry -> createTransferRow(entry, myId, panel, parentPanel)));

        return panel;
    }

    private static Flow createTransferRow(Map.Entry<UUID, PartyRole> entry, UUID myId,
                                          ModularPanel panel, ModularPanel parentPanel) {
        UUID memberId = entry.getKey();
        // Don't show self
        if (memberId.equals(myId)) {
            return Flow.row().height(0);
        }

        String memberName = PartyWidgets.getDisplayName(memberId);
        PartyRole role = entry.getValue();
        String roleStr = IKey.lang("blpc.party.role." + role.name().toLowerCase()).get();

        return Flow.row()
                .widthRel(1f).height(18)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new ButtonWidget<>().widthRel(1f).height(18).padding(4, 0, 0, 0)
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
