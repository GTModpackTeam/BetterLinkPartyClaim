package com.github.gtexpert.blpc.client.gui.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.client.gui.BLPCColors;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.network.message.PartyAction;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;

/**
 * Create-or-join panel shown when the player has no party. Lists pending
 * invites and free-to-join parties (full ones grayed). On join, transitions
 * to {@link MainPanel} via {@code reopener} when supplied.
 */
public class CreatePanel {

    public static final String PANEL_ID = "blpc.party.create";

    public static ModularPanel build() {
        return build(null);
    }

    public static ModularPanel build(IPanelHandler reopener) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();

        ModularPanel panel = new ModularPanel(PartyWidgets.uniquePanelId(PANEL_ID));
        panel.size(PartyWidgets.STANDARD_W, PartyWidgets.STANDARD_H);

        PartyWidgets.addHeader(panel, "blpc.party.create_title");

        final TextFieldWidget[] fieldRef = new TextFieldWidget[1];
        Runnable doCreate = () -> {
            String name = fieldRef[0].getText().trim();
            if (!name.isEmpty()) {
                ModNetwork.INSTANCE.sendToServer(PartyAction.create(name));
                transitionToMain(panel, reopener);
            }
        };

        TextFieldWidget nameField = PartyWidgets.createEnterSubmitTextField(doCreate);
        fieldRef[0] = nameField;
        nameField.setMaxLength(32);
        nameField.size(PartyWidgets.STANDARD_W - 80, PartyWidgets.INPUT_H);
        nameField.setText(IKey.lang(Party.DEFAULT_NAME_KEY).get());

        panel.child(Flow.row()
                .childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .left(8).right(8).top(24).height(PartyWidgets.INPUT_H)
                .child(nameField)
                .child(new ButtonWidget<>().size(PartyWidgets.SUBMIT_BTN_W, PartyWidgets.INPUT_H)
                        .overlay(PartyWidgets.buttonLabel(IKey.lang("blpc.party.create")))
                        .onMousePressed(btn -> {
                            doCreate.run();
                            return true;
                        })));

        @SuppressWarnings("unchecked")
        ListWidget<IWidget, ?> list = (ListWidget<IWidget, ?>) new ListWidget<>()
                .left(8).right(8).top(44).bottom(8)
                .crossAxisAlignment(Alignment.CrossAxis.START);
        panel.child(list);

        rebuild(list, playerId, panel, reopener);

        PartyWidgets.addSyncRefreshListener(panel, () -> {
            if (ClientPartyCache.getPartyByPlayer(playerId) != null) {
                transitionToMain(panel, reopener);
                return;
            }
            rebuild(list, playerId, panel, reopener);
        });

        return panel;
    }

    private static void transitionToMain(ModularPanel panel, IPanelHandler reopener) {
        panel.closeIfOpen();
        if (reopener != null) {
            // Defer so close settles before the handler builds a fresh panel.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                reopener.deleteCachedPanel();
                reopener.openPanel();
            });
        }
    }

    private static void rebuild(ListWidget<IWidget, ?> list, UUID playerId, ModularPanel panel,
                                IPanelHandler reopener) {
        list.removeAll();
        for (PartyEntry entry : collectAvailableParties(playerId)) {
            list.child(createPartyRow(entry, panel, reopener));
        }
    }

    private static List<PartyEntry> collectAvailableParties(UUID playerId) {
        List<PartyEntry> result = new ArrayList<>();

        for (Party party : ClientPartyCache.getAllParties()) {
            if (party.isMember(playerId)) continue;

            boolean invited = party.hasInvite(playerId);
            boolean freeToJoin = party.isFreeToJoin();

            if (invited || freeToJoin) {
                String displayName = party.getName();
                boolean full = !party.canAddMember();
                result.add(new PartyEntry(party.getPartyId(), displayName,
                        party.getDescription(), invited, full));
            }
        }

        result.sort((a, b) -> {
            if (a.invited != b.invited) return a.invited ? -1 : 1;
            // Available before full within each invited/non-invited bucket.
            if (a.full != b.full) return a.full ? 1 : -1;
            return a.displayName.compareToIgnoreCase(b.displayName);
        });
        return result;
    }

    private static ButtonWidget<?> createPartyRow(PartyEntry entry, ModularPanel panel, IPanelHandler reopener) {
        int color;
        String label;
        if (entry.full) {
            color = BLPCColors.subtext();
            label = entry.displayName + " [" + IKey.lang("blpc.toast.party_full").get() + "]";
        } else if (entry.invited) {
            color = BLPCColors.admin();
            label = entry.displayName + " [" + IKey.lang("blpc.party.invited_label").get() + "]";
        } else {
            color = BLPCColors.inactive();
            label = entry.displayName;
        }

        ButtonWidget<?> btn = new ButtonWidget<>();
        btn.widthRel(1f).height(PartyWidgets.BTN_H).padding(PartyWidgets.ROW_INDENT, 0, 0, 0);
        btn.overlay(PartyWidgets.rowLabel(IKey.str(label), color));

        if (!entry.description.isEmpty()) {
            btn.addTooltipLine(IKey.str(entry.description));
        }
        if (entry.full) {
            btn.addTooltipLine(IKey.lang("blpc.toast.party_full"));
        } else if (entry.invited) {
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.accept_invite"));
        } else {
            btn.addTooltipLine(IKey.lang("blpc.party.tooltip.join_free"));
        }

        if (entry.full) return btn;

        UUID partyId = entry.partyId;
        PartyAction action = entry.invited ? PartyAction.acceptInvite(partyId) :
                PartyAction.joinFreeParty(partyId);
        btn.onMousePressed(b -> PartyWidgets.sendAndApply(action, partyId,
                p -> p.addMember(Minecraft.getMinecraft().player.getUniqueID(), PartyRole.MEMBER)));

        return btn;
    }

    private static class PartyEntry {

        final UUID partyId;
        final String displayName;
        final String description;
        final boolean invited;
        final boolean full;

        PartyEntry(UUID partyId, String displayName, String description, boolean invited, boolean full) {
            this.partyId = partyId;
            this.displayName = displayName;
            this.description = description;
            this.invited = invited;
            this.full = full;
        }
    }
}
