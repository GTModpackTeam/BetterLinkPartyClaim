package com.github.gtexpert.blpc.client.gui.party;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.client.gui.party.PartyWidgets.MemberEntry;
import com.github.gtexpert.blpc.common.network.message.PartyAction;

/** Transfer-ownership panel (panel ID: {@value #PANEL_ID}). OWNER-only member picker. */
public class TransferOwnerPanel {

    public static final String PANEL_ID = "blpc.party.dialog.transfer";

    public static ModularPanel build(Party party) {
        UUID myId = Minecraft.getMinecraft().player.getUniqueID();
        if (party == null) return new ModularPanel(PartyWidgets.uniquePanelId(PANEL_ID));

        UUID partyId = party.getPartyId();

        ModularPanel panel = PartyWidgets.buildMemberPanel(
                PANEL_ID,
                "blpc.party.transfer_title",
                entry -> createTransferRow(entry, partyId),
                p -> PartyWidgets.collectSortedMembers(p, myId),
                partyId,
                f -> f.getRole(myId) == PartyRole.OWNER);

        return panel;
    }

    private static IWidget createTransferRow(MemberEntry entry, UUID partyId) {
        ButtonWidget<?> btn = PartyWidgets.createPlayerRow(
                entry.uuid(),
                PartyWidgets.formatMemberLabel(entry.name(), entry.role()),
                PartyWidgets.getRoleColor(entry.role()));
        btn.onMousePressed(b -> PartyWidgets.sendAndApply(
                PartyAction.transferOwnership(entry.name()),
                partyId,
                p -> p.setRole(entry.uuid(), PartyRole.OWNER)));
        return btn;
    }
}
