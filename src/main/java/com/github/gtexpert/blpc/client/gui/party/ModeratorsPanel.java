package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.client.gui.PlayerFaceDrawable;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Moderator management panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * Shows ALL party members in a single list, sorted by role (OWNER → ADMIN → MEMBER).
 * OWNER can change non-self/non-owner members' role via a {@link CycleButtonWidget}
 * (MEMBER ↔ ADMIN). OWNER role is reserved for the {@link TransferOwnerDialog}.
 * Includes a search box for filtering by member name.
 */
public class ModeratorsPanel {

    public static final String PANEL_ID = "blpc.party.moderators";

    public static ModularPanel build(Party party) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        PartyRole myRole = party.getRole(playerId);
        boolean isOwner = myRole == PartyRole.OWNER;

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(PartyWidgets.STANDARD_W, PartyWidgets.STANDARD_H);

        PartyWidgets.addHeader(panel, "blpc.party.moderators_title");

        var sorted = new ArrayList<>(party.getMembers().entrySet());
        sorted.sort((a, b) -> {
            // OWNER first, then ADMIN, then MEMBER
            int cmp = b.getValue().ordinal() - a.getValue().ordinal();
            if (cmp != 0) return cmp;
            return PartyWidgets.getDisplayName(a.getKey())
                    .compareToIgnoreCase(PartyWidgets.getDisplayName(b.getKey()));
        });

        @SuppressWarnings("unchecked")
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.crossAxisAlignment(Alignment.CrossAxis.START);

        var widgets = new ArrayList<IWidget>();
        var searchNames = new ArrayList<String>();
        for (Map.Entry<UUID, PartyRole> entry : sorted) {
            String name = PartyWidgets.getDisplayName(entry.getKey());
            IWidget row = createRow(entry, name, party, isOwner, playerId);
            widgets.add(row);
            searchNames.add(name.toLowerCase(Locale.ROOT));
            list.child(row);
        }

        if (widgets.isEmpty()) {
            PartyWidgets.addList(panel, list);
        } else {
            var content = PartyWidgets.wrapWithSearchBox(list, widgets, searchNames);
            // margin instead of left/right/top/bottom: Flow.column() pre-fills sizeRel(1f, 1f),
            // adding START+END units would conflict with SIZE.
            content.margin(8, 8, 22, 8);
            panel.child(content);
        }

        // No sync-close listener: the cycle button's IntValue.Dynamic and the role tooltip's
        // IKey.dynamic re-read party state on each frame, so role changes refresh in place
        // without rebuilding the panel. Closing on every cycle would interrupt bulk edits.

        return panel;
    }

    private static IWidget createRow(Map.Entry<UUID, PartyRole> entry, String memberName,
                                     Party party, boolean isOwner, UUID myId) {
        UUID memberId = entry.getKey();
        PartyRole role = entry.getValue();
        boolean canEdit = isOwner && !memberId.equals(myId) && role != PartyRole.OWNER;

        if (!canEdit) {
            int color = role == PartyRole.MEMBER ? GuiColors.GRAY_LIGHT : PartyWidgets.getRoleColor(role);
            String label = PartyWidgets.formatMemberLabel(memberName, role);
            ButtonWidget<?> btn = PartyWidgets.createPlayerRow(memberId, label, color);
            return btn;
        }

        // 2-state cycle: MEMBER (0) <-> ADMIN (1). OWNER is reserved for TransferOwnerDialog.
        PartyRole[] cycleRoles = { PartyRole.MEMBER, PartyRole.ADMIN };
        CycleButtonWidget cycle = new CycleButtonWidget()
                .length(2)
                .value(new IntValue.Dynamic(
                        () -> party.getRole(memberId) == PartyRole.ADMIN ? 1 : 0,
                        idx -> {
                            PartyRole r = idx == 1 ? PartyRole.ADMIN : PartyRole.MEMBER;
                            if (r == party.getRole(memberId)) return;
                            ModNetwork.INSTANCE.sendToServer(
                                    MessagePartyAction.changeRole(memberName + ":" + r.name()));
                            party.setRole(memberId, r);
                            ClientPartyCache.fireSyncListeners();
                        }))
                .stateChild(0, memberRowDisplay(memberId, memberName, PartyRole.MEMBER))
                .stateChild(1, memberRowDisplay(memberId, memberName, PartyRole.ADMIN))
                .widthRel(1f).height(PartyWidgets.BTN_H)
                .addTooltipLine(IKey.lang("blpc.party.tooltip.moderator"))
                .addTooltipLine(IKey.lang("blpc.party.tooltip.options"));
        for (PartyRole r : cycleRoles) {
            cycle.addTooltipLine(IKey.dynamic(() -> formatRoleOptionLine(r, party.getRole(memberId))));
        }
        return cycle;
    }

    private static String formatRoleOptionLine(PartyRole option, PartyRole current) {
        String name = IKey.lang("blpc.party.role." + option.name().toLowerCase(Locale.ROOT)).get();
        // OWNER is not a cycle option; treat as MEMBER for highlight purposes.
        PartyRole shown = current == PartyRole.OWNER ? PartyRole.MEMBER : current;
        if (option == shown) {
            return TextFormatting.YELLOW + "→ " + TextFormatting.WHITE + name;
        }
        return TextFormatting.GRAY + "  " + name;
    }

    private static IWidget memberRowDisplay(UUID memberId, String memberName, PartyRole role) {
        int color = PartyWidgets.getRoleColor(role);
        String label = PartyWidgets.formatMemberLabel(memberName, role);
        return Flow.row()
                .widthRel(1f).heightRel(1f)
                .padding(4, 0, 0, 0)
                .childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new PlayerFaceDrawable(memberId).asWidget()
                        .size(PartyWidgets.FACE_SIZE, PartyWidgets.FACE_SIZE))
                .child(IKey.str(label).color(color).shadow(true)
                        .alignment(Alignment.CenterLeft)
                        .asWidget().expanded());
    }
}
