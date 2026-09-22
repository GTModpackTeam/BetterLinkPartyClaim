package com.github.gtexpert.blpc.client.gui.party;

import java.util.UUID;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;

import com.github.gtexpert.blpc.api.integration.IntegrationPanelRegistry;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.client.gui.AddonsPanel;
import com.github.gtexpert.blpc.client.gui.BLPCColors;
import com.github.gtexpert.blpc.client.gui.party.widget.ConfirmDialog;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.network.message.PartyAction;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;

/**
 * Party main menu (panel ID: {@value #PANEL_ID}). Falls back to
 * {@link CreatePanel} when the player has no party.
 */
public class MainPanel {

    public static final String PANEL_ID = "blpc.party";

    public static ModularPanel build(UUID playerId) {
        return build(playerId, null);
    }

    /** {@code reopener} (nullable) re-invokes the parent factory after a join. */
    public static ModularPanel build(UUID playerId, IPanelHandler reopener) {
        Party party = ClientPartyCache.getPartyByPlayer(playerId);
        boolean bquLinked = ClientPartyCache.isBQuLinked(playerId);

        if (bquLinked && party == null) {
            ClientPartyCache.setLocalBQuLinked(playerId, false);
        }

        if (party == null) {
            return CreatePanel.build(reopener);
        }

        UUID partyId = party.getPartyId();

        ModularPanel panel = new ModularPanel(PartyWidgets.uniquePanelId(PANEL_ID));
        panel.size(PartyWidgets.STANDARD_W, PartyWidgets.STANDARD_H);

        panel.child(new ScrollingTextWidget(IKey.dynamic(() -> {
            Party p = ClientPartyCache.getParty(partyId);
            return p != null ? p.getName() : "";
        }))
                .color(BLPCColors.text()).shadow(BLPCColors.textShadow())
                .alignment(Alignment.Center).left(0).right(20).top(8).height(10));
        panel.child(ButtonWidget.panelCloseButton());

        @SuppressWarnings("rawtypes")
        ListWidget menuList = new ListWidget();
        menuList.left(8).right(8).top(26).bottom(26);
        menuList.crossAxisAlignment(Alignment.CrossAxis.START);
        panel.child(menuList);

        // Cache nav handlers for the panel's lifetime — IPanelHandler.simple registers
        // into panel.clientSubPanels with no removal API, so per-rebuild creation leaks.
        var partyRef = PartyWidgets.livePartyRef(partyId, party);
        NavHandlers nav = new NavHandlers(
                IPanelHandler.simple(panel, (pp, p) -> SettingsPanel.build(partyRef.get()), true),
                IPanelHandler.simple(panel, (pp, p) -> MembersPanel.build(partyRef.get()), true),
                IPanelHandler.simple(panel, (pp, p) -> ModeratorsPanel.build(partyRef.get()), true),
                IPanelHandler.simple(panel, (pp, p) -> TransferOwnerPanel.build(partyRef.get()), true),
                IPanelHandler.simple(panel, (pp, p) -> AddonsPanel.build(playerId), true));

        rebuildMenu(menuList, panel, partyId, playerId, nav);

        IPanelHandler disbandHandler = IPanelHandler.simple(
                panel,
                (pp, player) -> buildConfirmDialog(PartyWidgets.uniquePanelId("blpc.party.dialog.disband"), panel),
                true);
        panel.child(PartyWidgets.dialogButton(IKey.lang("blpc.party.disband"), disbandHandler)
                .size(50, 16).pos(PartyWidgets.STANDARD_W - 58, PartyWidgets.STANDARD_H - 24)
                .setEnabledIf(w -> isOwner(partyId, playerId)));

        PartyWidgets.addSyncRefreshListener(panel, () -> {
            if (ClientPartyCache.getPartyByPlayer(playerId) == null) {
                panel.closeIfOpen();
                return;
            }
            rebuildMenu(menuList, panel, partyId, playerId, nav);
        });

        return panel;
    }

    private static Dialog<Boolean> buildConfirmDialog(String panelId, ModularPanel parentPanel) {
        return ConfirmDialog.builder(panelId)
                .title("blpc.party.disband_confirm_title")
                .message("blpc.party.disband_confirm_msg")
                .yesLabel("blpc.party.disband_yes")
                .noLabel("blpc.party.disband_no")
                .closeParent(false)
                .onConfirm(() -> {
                    ModNetwork.INSTANCE.sendToServer(PartyAction.disband());
                    parentPanel.closeIfOpen();
                    PartyWidgets.clearLocalPartyData();
                })
                .build(parentPanel);
    }

    private static boolean isOwner(UUID partyId, UUID playerId) {
        Party p = ClientPartyCache.getParty(partyId);
        return p != null && p.getRole(playerId) == PartyRole.OWNER;
    }

    @SuppressWarnings("rawtypes")
    private static void rebuildMenu(ListWidget menuList, ModularPanel panel, UUID partyId,
                                    UUID playerId, NavHandlers nav) {
        menuList.removeAll();

        Party party = ClientPartyCache.getParty(partyId);
        if (party == null) return;

        var builder = PartyMenuBuilder.of(panel, party, playerId);

        builder.navHandler("blpc.party.settings", nav.settings)
                .tooltip("blpc.party.tooltip.settings")
                .visible(PartyMenuBuilder.MenuContext::canInvite)
                .navHandler("blpc.party.members", nav.members)
                .tooltip("blpc.party.tooltip.members")
                .navHandler("blpc.party.moderators", nav.moderators)
                .tooltip("blpc.party.tooltip.moderators")
                .navHandler("blpc.party.transfer", nav.transfer)
                .tooltip("blpc.party.tooltip.transfer")
                .visible(PartyMenuBuilder.MenuContext::isOwner)
                .navHandler("blpc.addons", nav.addons)
                .tooltip("blpc.addons.tooltip")
                .visible(c -> IntegrationPanelRegistry.hasAvailable())
                .buildInto(menuList);
    }

    private static final class NavHandlers {

        final IPanelHandler settings, members, moderators, transfer, addons;

        NavHandlers(IPanelHandler settings, IPanelHandler members,
                    IPanelHandler moderators, IPanelHandler transfer, IPanelHandler addons) {
            this.settings = settings;
            this.members = members;
            this.moderators = moderators;
            this.transfer = transfer;
            this.addons = addons;
        }
    }
}
