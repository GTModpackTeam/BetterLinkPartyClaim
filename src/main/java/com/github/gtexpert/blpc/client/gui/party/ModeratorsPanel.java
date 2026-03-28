package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

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
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Moderator management panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * FTB Utilities style: shows ALL party members in a single list.
 * ADMINs shown in green, MEMBERs in gray, OWNER in gold.
 * OWNER can click to promote MEMBER -> ADMIN or demote ADMIN -> MEMBER.
 * Cannot change OWNER or self.
 */
public class ModeratorsPanel {

    public static final String PANEL_ID = "blpc.party.moderators";
    private static final int W = 220;
    private static final int H = 180;

    public static ModularPanel build(Party party) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        PartyRole myRole = party.getRole(playerId);
        boolean isOwner = myRole == PartyRole.OWNER;

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(W, H);

        panel.child(IKey.lang("blpc.party.moderators_title").color(GuiColors.WHITE).shadow(true)
                .asWidget().alignment(Alignment.Center).left(0).right(0).top(8).height(10));
        panel.child(ButtonWidget.panelCloseButton());

        panel.child(new TextFieldWidget()
                .hintText(IKey.lang("blpc.party.search").get())
                .left(8).right(8).top(22).height(14));

        @SuppressWarnings("rawtypes")
        ListWidget list = new ListWidget();
        list.left(8).right(8).top(40).bottom(8);
        list.crossAxisAlignment(Alignment.CrossAxis.START);

        List<Map.Entry<UUID, PartyRole>> sorted = new ArrayList<>(party.getMembers().entrySet());
        sorted.sort((a, b) -> {
            // OWNER first, then ADMIN, then MEMBER
            int cmp = b.getValue().ordinal() - a.getValue().ordinal();
            if (cmp != 0) return cmp;
            return PartyWidgets.getDisplayName(a.getKey())
                    .compareToIgnoreCase(PartyWidgets.getDisplayName(b.getKey()));
        });

        for (Map.Entry<UUID, PartyRole> entry : sorted) {
            list.child(createRow(entry, isOwner, playerId));
        }

        panel.child(list);
        return panel;
    }

    private static Flow createRow(Map.Entry<UUID, PartyRole> entry, boolean isOwner, UUID myId) {
        UUID memberId = entry.getKey();
        PartyRole role = entry.getValue();
        String memberName = PartyWidgets.getDisplayName(memberId);
        String roleStr = IKey.lang("blpc.party.role." + role.name().toLowerCase()).get();

        int color = role == PartyRole.MEMBER ? GuiColors.GRAY_LIGHT : PartyWidgets.getRoleColor(role);

        String label = memberName + " [" + roleStr + "]";

        ButtonWidget<?> btn = new ButtonWidget<>();
        btn.widthRel(1f).height(18).padding(4, 0, 0, 0);
        btn.overlay(IKey.str(label).color(color).shadow(true).alignment(Alignment.CenterLeft));

        // Owner can promote/demote other non-owner members (not self)
        if (isOwner && !memberId.equals(myId) && role != PartyRole.OWNER) {
            if (role == PartyRole.MEMBER) {
                btn.onMousePressed(b -> {
                    ModNetwork.INSTANCE.sendToServer(
                            MessagePartyAction.changeRole(memberName + ":ADMIN"));
                    return true;
                });
            } else if (role == PartyRole.ADMIN) {
                btn.onMousePressed(b -> {
                    ModNetwork.INSTANCE.sendToServer(
                            MessagePartyAction.changeRole(memberName + ":MEMBER"));
                    return true;
                });
            }
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.moderator"));
        }

        return Flow.row()
                .widthRel(1f).height(18)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(btn);
    }
}
