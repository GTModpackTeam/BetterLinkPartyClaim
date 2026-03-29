package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Member list panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * FTB Utilities style: shows ALL online players in a single list.
 * Party members are shown in green, non-members in gray.
 * Clicking a non-member invites them (ADMIN+ only).
 * Clicking a member kicks them (ADMIN+ only, cannot kick higher role or self).
 */
public class MembersPanel {

    public static final String PANEL_ID = "blpc.party.members";

    public static ModularPanel build(Party party) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        PartyRole myRole = party.getRole(playerId);
        boolean canManage = myRole != null && myRole.canInvite();

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(PanelSizes.STANDARD_W, PanelSizes.STANDARD_H);

        PanelBuilder.addHeader(panel, "blpc.party.members_title");

        @SuppressWarnings("rawtypes")
        ListWidget list = new ListWidget();

        List<PlayerEntry> entries = collectAllPlayers(party);
        for (PlayerEntry entry : entries) {
            list.child(createRow(entry, party, playerId, myRole, canManage));
        }

        PanelBuilder.addList(panel, list);
        return panel;
    }

    private static List<PlayerEntry> collectAllPlayers(Party party) {
        List<PlayerEntry> result = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null) return result;

        Set<UUID> seen = new HashSet<>();
        for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
            UUID uuid = info.getGameProfile().getId();
            String name = info.getGameProfile().getName();
            boolean isMember = party.isMember(uuid);
            PartyRole role = party.getRole(uuid);
            result.add(new PlayerEntry(uuid, name, isMember, role));
            seen.add(uuid);
        }

        result.sort((a, b) -> {
            // Members first, then non-members; within each group sort by name
            if (a.isMember != b.isMember) return a.isMember ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        return result;
    }

    private static Flow createRow(PlayerEntry entry, Party party, UUID playerId,
                                  PartyRole myRole, boolean canManage) {
        int color;
        if (entry.isMember) {
            color = PartyWidgets.getRoleColor(entry.role);
        } else {
            color = GuiColors.GRAY_LIGHT;
        }

        String label = entry.name;
        if (entry.isMember && entry.role != null) {
            String roleStr = IKey.lang("blpc.party.role." + entry.role.name().toLowerCase()).get();
            label = entry.name + " [" + roleStr + "]";
        }

        ButtonWidget<?> btn = new ButtonWidget<>();
        btn.widthRel(1f).height(PanelSizes.BTN_H).padding(4, 0, 0, 0);
        btn.overlay(IKey.str(label).color(color).shadow(true).alignment(Alignment.CenterLeft));

        boolean isSelf = entry.uuid.equals(playerId);
        if (entry.isMember) {
            if (isSelf && entry.role != PartyRole.OWNER) {
                // Click self to leave (non-owner only)
                String playerName = entry.name;
                btn.onMousePressed(b -> {
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.kickOrLeave(playerName));
                    return true;
                });
                btn.addTooltipLine(IKey.lang("blpc.party.tooltip.member_self"));
            } else if (!isSelf && canManage && entry.role != null && myRole != null && myRole.canKick(entry.role)) {
                // Click to kick (if permission allows)
                String playerName = entry.name;
                btn.onMousePressed(b -> {
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.kickOrLeave(playerName));
                    return true;
                });
                btn.addTooltipLine(IKey.lang("blpc.party.tooltip.kick"));
            }
        } else if (canManage) {
            // Click to invite
            String playerName = entry.name;
            btn.onMousePressed(b -> {
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.invite(playerName));
                return true;
            });
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.invite"));
        }

        return Flow.row()
                .widthRel(1f).height(PanelSizes.BTN_H)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(btn);
    }

    private static class PlayerEntry {

        final UUID uuid;
        final String name;
        final boolean isMember;
        final PartyRole role;

        PlayerEntry(UUID uuid, String name, boolean isMember, PartyRole role) {
            this.uuid = uuid;
            this.name = name;
            this.isMember = isMember;
            this.role = role;
        }
    }
}
