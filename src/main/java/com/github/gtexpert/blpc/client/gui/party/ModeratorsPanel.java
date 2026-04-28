package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;

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
 * OWNER can change non-self/non-owner members' role via a {@link DropdownWidget}
 * (MEMBER ↔ ADMIN). OWNER role is reserved for the {@link TransferOwnerDialog}.
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

        ListWidget<?, ?> list = new ListWidget<>()
                .children(sorted, entry -> createRow(entry, party, isOwner, playerId));

        PartyWidgets.addList(panel, list);

        PartyWidgets.addSyncCloseListener(panel);

        return panel;
    }

    private static IWidget createRow(Map.Entry<UUID, PartyRole> entry, Party party,
                                     boolean isOwner, UUID myId) {
        UUID memberId = entry.getKey();
        PartyRole role = entry.getValue();
        String memberName = PartyWidgets.getDisplayName(memberId);
        boolean canEdit = isOwner && !memberId.equals(myId) && role != PartyRole.OWNER;

        if (!canEdit) {
            int color = role == PartyRole.MEMBER ? GuiColors.GRAY_LIGHT : PartyWidgets.getRoleColor(role);
            String label = PartyWidgets.formatMemberLabel(memberName, role);
            ButtonWidget<?> btn = PartyWidgets.createPlayerRow(memberId, label, color);
            return btn;
        }

        return new DropdownWidget<>("blpc.role." + memberId, PartyRole.class)
                .options(PartyRole.MEMBER, PartyRole.ADMIN)
                .optionToWidget(
                        (r, forSelected) -> roleOptionWidget(memberId, memberName, r, forSelected))
                .value(new EnumValue.Dynamic<>(PartyRole.class,
                        () -> {
                            PartyRole cur = party.getRole(memberId);
                            // OWNER role is not a Dropdown option; clamp to MEMBER for display.
                            return cur == PartyRole.OWNER ? PartyRole.MEMBER : cur;
                        },
                        r -> {
                            if (r == party.getRole(memberId)) return;
                            ModNetwork.INSTANCE.sendToServer(
                                    MessagePartyAction.changeRole(memberName + ":" + r.name()));
                            party.setRole(memberId, r);
                            ClientPartyCache.fireSyncListeners();
                        }))
                .widthRel(1f).height(PartyWidgets.BTN_H)
                .addTooltipLine(IKey.lang("blpc.party.tooltip.moderator"));
    }

    private static IWidget roleOptionWidget(UUID memberId, String memberName, PartyRole role,
                                            boolean forSelected) {
        if (forSelected) {
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
        // Menu items render inside MenuPanel (context_menu theme):
        // theme provides "menu" background, #404040 text, no shadow.
        String roleStr = IKey.lang("blpc.party.role." + role.name().toLowerCase(Locale.ROOT)).get();
        return IKey.str(roleStr).alignment(Alignment.Center)
                .asWidget().widthRel(1f);
    }
}
