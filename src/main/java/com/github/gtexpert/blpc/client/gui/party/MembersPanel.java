package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Member management panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * MEMBER role: single members-only list with search (self-leave only, STANDARD size).
 * ADMIN+ role: two tabs (Members / Invite) with search per tab (LARGE size, mirrors SettingsPanel).
 */
public class MembersPanel {

    public static final String PANEL_ID = "blpc.party.members";

    private static final int TAB_H = 16;

    public static ModularPanel build(Party party) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        PartyRole myRole = party.getRole(playerId);
        boolean canManage = myRole != null && myRole.canInvite();

        ModularPanel panel = new ModularPanel(PANEL_ID);

        if (canManage) {
            panel.size(PartyWidgets.LARGE_W, PartyWidgets.LARGE_H);
            PartyWidgets.addHeader(panel, "blpc.party.members_title");
            buildTabs(panel, party, playerId, myRole);
        } else {
            panel.size(PartyWidgets.STANDARD_W, PartyWidgets.STANDARD_H);
            PartyWidgets.addHeader(panel, "blpc.party.members_title");
            buildSingleMembersList(panel, party, playerId, myRole);
        }

        PartyWidgets.addSyncCloseListener(panel);

        return panel;
    }

    private static void buildTabs(ModularPanel panel, Party party, UUID playerId, PartyRole myRole) {
        var controller = new PagedWidget.Controller();

        var tabRow = Flow.row()
                .childPadding(2)
                .left(4).right(4).top(22).height(TAB_H)
                .child(new PageButton(0, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.tab.members")))
                .child(new PageButton(1, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.tab.invite")));

        var pagedWidget = new PagedWidget<>()
                .controller(controller)
                .left(4).right(4).top(40).bottom(4)
                .addPage(buildMembersPage(party, playerId, myRole))
                .addPage(buildInvitePage(party));

        panel.child(tabRow);
        panel.child(pagedWidget);
    }

    private static void buildSingleMembersList(ModularPanel panel, Party party, UUID playerId,
                                               PartyRole myRole) {
        var entries = collectMembers(party);

        @SuppressWarnings("unchecked")
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.crossAxisAlignment(Alignment.CrossAxis.START);

        var widgets = new ArrayList<IWidget>();
        var searchNames = new ArrayList<String>();
        for (PlayerEntry entry : entries) {
            ButtonWidget<?> row = createMemberRow(entry, party, playerId, myRole, false);
            widgets.add(row);
            searchNames.add(entry.name.toLowerCase(Locale.ROOT));
            list.child(row);
        }

        if (widgets.isEmpty()) {
            PartyWidgets.addList(panel, list);
            return;
        }

        var content = PartyWidgets.wrapWithSearchBox(list, widgets, searchNames);
        // margin instead of left/right/top/bottom: Flow.column() pre-fills sizeRel(1f, 1f),
        // adding START+END units would conflict with SIZE.
        content.margin(8, 8, 22, 8);
        panel.child(content);
    }

    private static IWidget buildMembersPage(Party party, UUID playerId, PartyRole myRole) {
        var entries = collectMembers(party);

        @SuppressWarnings("unchecked")
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.widthRel(1f).heightRel(1f);
        list.crossAxisAlignment(Alignment.CrossAxis.START);

        var widgets = new ArrayList<IWidget>();
        var searchNames = new ArrayList<String>();
        for (PlayerEntry entry : entries) {
            ButtonWidget<?> row = createMemberRow(entry, party, playerId, myRole, true);
            widgets.add(row);
            searchNames.add(entry.name.toLowerCase(Locale.ROOT));
            list.child(row);
        }

        if (widgets.isEmpty()) {
            list.child(IKey.lang("blpc.party.no_players_online").color(GuiColors.GRAY)
                    .asWidget().widthRel(1f).height(PartyWidgets.BTN_H).marginLeft(4));
            return list;
        }

        return PartyWidgets.wrapWithSearchBox(list, widgets, searchNames);
    }

    private static IWidget buildInvitePage(Party party) {
        var entries = collectInvitableOnlinePlayers(party);

        @SuppressWarnings("unchecked")
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.widthRel(1f).heightRel(1f);
        list.crossAxisAlignment(Alignment.CrossAxis.START);

        var widgets = new ArrayList<IWidget>();
        var searchNames = new ArrayList<String>();
        for (PlayerEntry entry : entries) {
            ButtonWidget<?> row = createInviteRow(entry, party);
            widgets.add(row);
            searchNames.add(entry.name.toLowerCase(Locale.ROOT));
            list.child(row);
        }

        if (widgets.isEmpty()) {
            list.child(IKey.lang("blpc.party.no_players_online").color(GuiColors.GRAY)
                    .asWidget().widthRel(1f).height(PartyWidgets.BTN_H).marginLeft(4));
            return list;
        }

        return PartyWidgets.wrapWithSearchBox(list, widgets, searchNames);
    }

    private static List<PlayerEntry> collectMembers(Party party) {
        List<PlayerEntry> result = new ArrayList<>();
        for (Map.Entry<UUID, PartyRole> entry : party.getMembers().entrySet()) {
            UUID uuid = entry.getKey();
            String name = PartyWidgets.getDisplayName(uuid);
            result.add(new PlayerEntry(uuid, name, entry.getValue()));
        }
        // OWNER first, then ADMIN, then MEMBER; alphabetical within role.
        result.sort((a, b) -> {
            int cmp = b.role.ordinal() - a.role.ordinal();
            if (cmp != 0) return cmp;
            return a.name.compareToIgnoreCase(b.name);
        });
        return result;
    }

    private static List<PlayerEntry> collectInvitableOnlinePlayers(Party party) {
        List<PlayerEntry> result = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null) return result;
        for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
            UUID uuid = info.getGameProfile().getId();
            if (party.isMember(uuid) || party.hasInvite(uuid)) continue;
            result.add(new PlayerEntry(uuid, info.getGameProfile().getName(), null));
        }
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    private static ButtonWidget<?> createMemberRow(PlayerEntry entry, Party party, UUID playerId,
                                                   PartyRole myRole, boolean canManage) {
        int color = PartyWidgets.getRoleColor(entry.role);
        String label = PartyWidgets.formatMemberLabel(entry.name, entry.role);
        ButtonWidget<?> btn = PartyWidgets.createPlayerRow(entry.uuid, label, color);

        boolean isSelf = entry.uuid.equals(playerId);
        if (isSelf && entry.role != PartyRole.OWNER) {
            // Self can leave (non-owner only). Server rejects owner self-leave anyway.
            String playerName = entry.name;
            btn.onMousePressed(b -> {
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.kickOrLeave(playerName));
                party.removeMember(entry.uuid);
                ClientPartyCache.fireSyncListeners();
                return true;
            });
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.member_self"));
        } else if (!isSelf && canManage && entry.role != null && myRole != null &&
                myRole.canKick(entry.role)) {
                    String playerName = entry.name;
                    btn.onMousePressed(b -> {
                        ModNetwork.INSTANCE.sendToServer(MessagePartyAction.kickOrLeave(playerName));
                        party.removeMember(entry.uuid);
                        ClientPartyCache.fireSyncListeners();
                        return true;
                    });
                    btn.addTooltipLine(IKey.lang("blpc.party.tooltip.kick"));
                }

        return btn;
    }

    private static ButtonWidget<?> createInviteRow(PlayerEntry entry, Party party) {
        ButtonWidget<?> btn = PartyWidgets.createPlayerRow(entry.uuid, entry.name, GuiColors.GRAY_LIGHT);
        String playerName = entry.name;
        btn.onMousePressed(b -> {
            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.invite(playerName));
            party.addInvite(entry.uuid, Long.MAX_VALUE);
            ClientPartyCache.fireSyncListeners();
            return true;
        });
        btn.addTooltipLine(IKey.lang("blpc.party.tooltip.invite"));
        return btn;
    }

    private static class PlayerEntry {

        final UUID uuid;
        final String name;
        final PartyRole role;

        PlayerEntry(UUID uuid, String name, PartyRole role) {
            this.uuid = uuid;
            this.name = name;
            this.role = role;
        }
    }
}
