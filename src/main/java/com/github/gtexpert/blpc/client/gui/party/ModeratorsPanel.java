package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.client.gui.BLPCColors;
import com.github.gtexpert.blpc.client.gui.party.PartyWidgets.MemberEntry;
import com.github.gtexpert.blpc.common.network.message.PartyAction;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;

/**
 * Member roles list (panel ID: {@value #PANEL_ID}). OWNER cycles others between
 * MEMBER ↔ ADMIN; OWNER transfer lives in {@link TransferOwnerPanel}.
 */
public class ModeratorsPanel {

    public static final String PANEL_ID = "blpc.party.moderators";

    public static ModularPanel build(Party party) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        UUID partyId = party.getPartyId();

        ModularPanel panel = PartyWidgets.buildMemberPanel(
                PANEL_ID,
                "blpc.party.moderators_title",
                entry -> createRow(entry, partyId, party),
                p -> PartyWidgets.collectSortedMembers(p, null, r -> r != PartyRole.OWNER),
                partyId,
                f -> f.isMember(playerId) && f.getRole(playerId) == PartyRole.OWNER);

        return panel;
    }

    private static IWidget createRow(MemberEntry entry, UUID partyId, Party party) {
        UUID myId = Minecraft.getMinecraft().player.getUniqueID();
        boolean isOwner = party.getRole(myId) == PartyRole.OWNER;
        boolean canEdit = isOwner && !entry.uuid.equals(myId) && entry.role != PartyRole.OWNER;

        if (!canEdit) {
            int color = entry.role == PartyRole.MEMBER ? BLPCColors.inactive() : PartyWidgets.getRoleColor(entry.role);
            String label = PartyWidgets.formatMemberLabel(entry.name, entry.role);
            return PartyWidgets.createPlayerRow(entry.uuid, label, color);
        }

        PartyRole[] cycleRoles = { PartyRole.MEMBER, PartyRole.ADMIN };
        CycleButtonWidget cycle = new CycleButtonWidget()
                .length(2)
                .value(new IntValue.Dynamic(
                        () -> currentRole(partyId, entry.uuid) == PartyRole.ADMIN ? 1 : 0,
                        idx -> {
                            PartyRole r = idx == 1 ? PartyRole.ADMIN : PartyRole.MEMBER;
                            if (r == currentRole(partyId, entry.uuid)) return;
                            PartyWidgets.sendAndApply(PartyAction.changeRole(entry.name + ":" + r.name()),
                                    partyId, p -> p.setRole(entry.uuid, r));
                        }))
                .stateChild(0, memberRowDisplay(entry, PartyRole.MEMBER))
                .stateChild(1, memberRowDisplay(entry, PartyRole.ADMIN))
                .widthRel(1f).height(PartyWidgets.BTN_H)
                .addTooltipLine(IKey.lang("blpc.party.tooltip.moderator"))
                .addTooltipLine(IKey.lang("blpc.party.tooltip.options"));
        for (PartyRole r : cycleRoles) {
            cycle.addTooltipLine(IKey.dynamic(() -> formatRoleOptionLine(r, currentRole(partyId, entry.uuid))));
        }
        return cycle;
    }

    private static PartyRole currentRole(UUID partyId, UUID memberId) {
        Party current = ClientPartyCache.getParty(partyId);
        return current != null ? current.getRole(memberId) : null;
    }

    private static String formatRoleOptionLine(PartyRole option, PartyRole current) {
        PartyRole shown = current == PartyRole.OWNER ? PartyRole.MEMBER : current;
        return PartyWidgets.formatCycleOptionLine("blpc.party.role.", option.name(), option == shown);
    }

    private static IWidget memberRowDisplay(MemberEntry entry, PartyRole role) {
        return PartyWidgets.memberRow(entry, PartyWidgets.getRoleColor(role));
    }
}
