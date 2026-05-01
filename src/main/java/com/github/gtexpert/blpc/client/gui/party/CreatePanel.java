package com.github.gtexpert.blpc.client.gui.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
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
 * Party creation panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * Shown when the player has no party. Top area has a name input + Create button.
 * Below is a scrollable list of:
 * <ul>
 * <li>Parties that have invited the player (click to accept)</li>
 * <li>Free-to-join parties (click to join)</li>
 * </ul>
 * Hovering shows the party description if set.
 */
public class CreatePanel {

    public static final String PANEL_ID = "blpc.party.create";

    public static ModularPanel build() {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(PartyWidgets.STANDARD_W, PartyWidgets.STANDARD_H);

        PartyWidgets.addHeader(panel, "blpc.party.create_title");

        final TextFieldWidget[] fieldRef = new TextFieldWidget[1];
        Runnable doCreate = () -> {
            String name = fieldRef[0].getText().trim();
            if (!name.isEmpty()) {
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.create(name));
                panel.closeIfOpen();
            }
        };

        TextFieldWidget nameField = PartyWidgets.createEnterSubmitTextField(doCreate);
        fieldRef[0] = nameField;
        nameField.setMaxLength(32);
        nameField.size(PartyWidgets.STANDARD_W - 80, 14);
        nameField.setText(IKey.lang(Party.DEFAULT_NAME_KEY).get());

        panel.child(Flow.row()
                .childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .left(8).right(8).top(24).height(14)
                .child(nameField)
                .child(new ButtonWidget<>().size(50, 14)
                        .overlay(IKey.lang("blpc.party.create"))
                        .onMousePressed(btn -> {
                            doCreate.run();
                            return true;
                        })));

        List<PartyEntry> entries = collectAvailableParties(playerId);

        panel.child(new ListWidget<>()
                .left(8).right(8).top(44).bottom(8)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .children(entries, entry -> createPartyRow(entry, panel)));

        PartyWidgets.addSyncCloseListener(panel);

        return panel;
    }

    private static List<PartyEntry> collectAvailableParties(UUID playerId) {
        List<PartyEntry> result = new ArrayList<>();

        for (Party party : ClientPartyCache.getAllParties()) {
            if (party.isMember(playerId)) continue;

            boolean invited = party.hasInvite(playerId);
            boolean freeToJoin = party.isFreeToJoin();

            if (invited || freeToJoin) {
                String displayName = party.getName();
                result.add(new PartyEntry(party.getPartyId(), displayName,
                        party.getDescription(), invited));
            }
        }

        result.sort((a, b) -> {
            if (a.invited != b.invited) return a.invited ? -1 : 1;
            return a.displayName.compareToIgnoreCase(b.displayName);
        });
        return result;
    }

    private static ButtonWidget<?> createPartyRow(PartyEntry entry, ModularPanel panel) {
        int color = entry.invited ? GuiColors.GREEN : GuiColors.GRAY_LIGHT;
        String label = entry.invited ? entry.displayName + " [" + IKey.lang("blpc.party.invited_label").get() + "]" :
                entry.displayName;

        ButtonWidget<?> btn = new ButtonWidget<>();
        btn.widthRel(1f).height(PartyWidgets.BTN_H).padding(4, 0, 0, 0);
        btn.hoverBackground(new Rectangle().color(GuiColors.HOVER));
        btn.overlay(IKey.str(label).color(color).shadow(true).alignment(Alignment.CenterLeft));

        if (!entry.description.isEmpty()) {
            btn.addTooltipLine(IKey.str(entry.description));
        }
        if (entry.invited) {
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.accept_invite"));
        } else {
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.join_free"));
        }

        UUID partyId = entry.partyId;
        btn.onMousePressed(b -> {
            if (entry.invited) {
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.acceptInvite(partyId));
            } else {
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.joinFreeParty(partyId));
            }
            UUID myId = Minecraft.getMinecraft().player.getUniqueID();
            Party joinParty = ClientPartyCache.getParty(partyId);
            if (joinParty != null) {
                joinParty.addMember(myId, PartyRole.MEMBER);
            }
            ClientPartyCache.fireSyncListeners();
            return true;
        });

        return btn;
    }

    private static class PartyEntry {

        final UUID partyId;
        final String displayName;
        final String description;
        final boolean invited;

        PartyEntry(UUID partyId, String displayName, String description, boolean invited) {
            this.partyId = partyId;
            this.displayName = displayName;
            this.description = description;
            this.invited = invited;
        }
    }
}
