package com.github.gtexpert.blpc.client.gui.party;

import java.util.*;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
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
 * FTB Utilities style: shows ALL party members in a single list.
 * ADMINs shown in green, MEMBERs in gray, OWNER in gold.
 * OWNER can click to promote MEMBER -> ADMIN or demote ADMIN -> MEMBER.
 * Cannot change OWNER or self.
 */
public class ModeratorsPanel {

    public static final String PANEL_ID = "blpc.party.moderators";

    public static ModularPanel build(Party party) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        PartyRole myRole = party.getRole(playerId);
        boolean isOwner = myRole == PartyRole.OWNER;

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(PanelSizes.STANDARD_W, PanelSizes.STANDARD_H);

        PanelBuilder.addHeader(panel, "blpc.party.moderators_title");

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

        PanelBuilder.addList(panel, list);

        PartyWidgets.addPartyRefreshListener(panel, party.getPartyId(), ModeratorsPanel::build);

        return panel;
    }

    private static ButtonWidget<?> createRow(Map.Entry<UUID, PartyRole> entry, Party party,
                                             boolean isOwner, UUID myId) {
        UUID memberId = entry.getKey();
        PartyRole role = entry.getValue();
        String memberName = PartyWidgets.getDisplayName(memberId);
        String roleStr = IKey.lang("blpc.party.role." + role.name().toLowerCase()).get();

        int color = role == PartyRole.MEMBER ? GuiColors.GRAY_LIGHT : PartyWidgets.getRoleColor(role);

        String label = memberName + " [" + roleStr + "]";

        ButtonWidget<?> btn = new ButtonWidget<>();
        btn.widthRel(1f).height(PanelSizes.BTN_H).padding(0);
        btn.hoverBackground(new Rectangle().color(GuiColors.HOVER));
        btn.child(Flow.row()
                .widthRel(1f).heightRel(1f)
                .padding(4, 0, 0, 0)
                .childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new PlayerFaceDrawable(memberId).asWidget()
                        .size(PanelSizes.FACE_SIZE, PanelSizes.FACE_SIZE))
                .child(IKey.str(label).color(color).shadow(true).alignment(Alignment.CenterLeft)
                        .asWidget().expanded()));

        if (isOwner && !memberId.equals(myId) && role != PartyRole.OWNER) {
            String newRole = switch (role) {
                case MEMBER -> "ADMIN";
                case ADMIN -> "MEMBER";
                default -> null;
            };
            if (newRole != null) {
                PartyRole newPartyRole = PartyRole.fromName(newRole);
                btn.onMousePressed(b -> {
                    ModNetwork.INSTANCE.sendToServer(
                            MessagePartyAction.changeRole(memberName + ":" + newRole));
                    if (newPartyRole != null) {
                        party.setRole(memberId, newPartyRole);
                        ClientPartyCache.fireSyncListeners();
                    }
                    return true;
                });
            }
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.moderator"));
        }

        return btn;
    }
}
