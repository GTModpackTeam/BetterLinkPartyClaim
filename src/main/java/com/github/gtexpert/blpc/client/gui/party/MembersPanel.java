package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ButtonWidget;
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

    public static ModularPanel build(Party party) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        PartyRole myRole = party.getRole(playerId);
        boolean canManage = myRole != null && myRole.canInvite();

        ModularPanel panel = new ModularPanel(PANEL_ID);

        if (canManage) {
            panel.size(PartyWidgets.LARGE_W, PartyWidgets.LARGE_H);
            PartyWidgets.addHeader(panel, "blpc.party.members_title");
            PartyWidgets.addTabs(panel, new PagedWidget.Controller(),
                    new String[] { "blpc.party.tab.members", "blpc.party.tab.invite" },
                    new IWidget[] { buildMembersPage(party, playerId, myRole, true), buildInvitePage(party) });
        } else {
            panel.size(PartyWidgets.STANDARD_W, PartyWidgets.STANDARD_H);
            PartyWidgets.addHeader(panel, "blpc.party.members_title");
            // margin instead of left/right/top/bottom: Flow.column() pre-fills sizeRel(1f, 1f),
            // adding START+END units would conflict with SIZE.
            Flow content = buildMembersPage(party, playerId, myRole, false);
            content.margin(8, 8, 22, 8);
            panel.child(content);
        }

        PartyWidgets.addSyncCloseListener(panel);

        return panel;
    }

    private static Flow buildMembersPage(Party party, UUID playerId, PartyRole myRole, boolean canManage) {
        return PartyWidgets.buildSearchableList(
                collectMembers(party),
                entry -> createMemberRow(entry, party, playerId, myRole, canManage),
                entry -> entry.name,
                "blpc.party.no_players_online");
    }

    private static Flow buildInvitePage(Party party) {
        return PartyWidgets.buildSearchableList(
                collectInvitableOnlinePlayers(party),
                entry -> createInviteRow(entry, party),
                entry -> entry.name,
                "blpc.party.no_players_online");
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
        // Self can leave (non-owner only); admins+ can kick lower-ranked members.
        boolean canSelfLeave = isSelf && entry.role != PartyRole.OWNER;
        boolean canKickOther = !isSelf && canManage && entry.role != null && myRole != null &&
                myRole.canKick(entry.role);
        if (canSelfLeave || canKickOther) {
            String playerName = entry.name;
            btn.onMousePressed(b -> {
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.kickOrLeave(playerName));
                party.removeMember(entry.uuid);
                ClientPartyCache.fireSyncListeners();
                return true;
            });
            btn.addTooltipLine(IKey.lang(canSelfLeave ? "blpc.party.tooltip.member_self" : "blpc.party.tooltip.kick"));
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
