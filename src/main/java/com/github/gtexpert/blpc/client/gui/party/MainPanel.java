package com.github.gtexpert.blpc.client.gui.party;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;

import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Main party menu panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * Entry point for all party management. Conditionally shows buttons
 * based on the player's role and BQu link status:
 * <ul>
 * <li>Settings (includes allies/enemies management), Transfer - ADMIN+ only</li>
 * <li>Members, Invite - hidden when BQu-linked (managed via BQu screen)</li>
 * <li>Open BQu Party Screen - shown when BQu-linked</li>
 * <li>Link/Unlink BQu, Leave, Disband - bottom-pinned buttons</li>
 * </ul>
 * If the player has no party, delegates to {@link CreatePanel}.
 */
public class MainPanel {

    public static final String PANEL_ID = "blpc.party";
    private static final int W = 220;
    private static final int H = 180;

    public static ModularPanel build(UUID playerId) {
        Party party = ClientPartyCache.getPartyByPlayer(playerId);
        boolean bquAvailable = PartyProviderRegistry.hasNativeScreen();
        boolean bquLinked = ClientPartyCache.isBQuLinked(playerId);

        // Auto-fix: bquLinked but no party -> clear stale flag
        if (bquLinked && party == null) {
            ClientPartyCache.setLocalBQuLinked(playerId, false);
            bquLinked = false;
        }

        if (party == null) {
            return CreatePanel.build();
        }

        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(W, H);

        String displayName = party.getName();
        panel.child(IKey.str(displayName).color(GuiColors.WHITE).shadow(true)
                .asWidget().pos(8, 8));
        panel.child(ButtonWidget.panelCloseButton());

        PartyRole myRole = party.getRole(playerId);
        boolean canManage = myRole != null && myRole.canInvite();
        boolean isOwner = myRole == PartyRole.OWNER;

        // Menu buttons in scrollable list (same structure as all other panels)
        int menuTop = 26;
        @SuppressWarnings("rawtypes")
        ListWidget menuList = new ListWidget();
        menuList.left(8).right(8).top(menuTop).bottom(26);
        menuList.crossAxisAlignment(Alignment.CrossAxis.START);

        // Settings
        if (canManage) {
            menuList.child(createMenuButton(IKey.lang("blpc.party.settings"), panel,
                    () -> SettingsPanel.build(party), "blpc.party.tooltip.settings"));
        }

        // Members
        menuList.child(createMenuButton(IKey.lang("blpc.party.members"), panel,
                () -> MembersPanel.build(party), "blpc.party.tooltip.members"));

        // Moderators
        menuList.child(createMenuButton(IKey.lang("blpc.party.moderators"), panel,
                () -> ModeratorsPanel.build(party), "blpc.party.tooltip.moderators"));

        // Transfer Ownership
        if (isOwner) {
            menuList.child(createMenuButton(IKey.lang("blpc.party.transfer"), panel,
                    () -> TransferOwnerDialog.build(panel), "blpc.party.tooltip.transfer"));
        }

        // BQu Manage Party
        if (bquAvailable && bquLinked) {
            menuList.child((ButtonWidget<?>) new ButtonWidget<>().size(W - 16, 18)
                    .padding(4, 0, 0, 0)
                    .overlay(IKey.lang("blpc.party.open_native").alignment(Alignment.CenterLeft))
                    .addTooltipLine(IKey.lang("blpc.party.tooltip.open_native"))
                    .onMousePressed(btn -> {
                        panel.closeIfOpen();
                        Minecraft.getMinecraft().addScheduledTask(PartyProviderRegistry::openNativeScreen);
                        return true;
                    }));
        }

        panel.child(menuList);

        // Bottom buttons (pinned to bottom)
        int btnY = H - 24;

        if (bquAvailable && bquLinked && canManage) {
            panel.child(PartyWidgets.createActionButton(
                    IKey.lang("blpc.party.unlink_bqu"), "Open Unlink dialog",
                    () -> PartyWidgets.openSubPanel(panel, UnlinkBQuDialog.build(panel)))
                    .size(80, 16).pos(8, btnY));
        } else if (bquAvailable && !bquLinked && canManage) {
            panel.child(PartyWidgets.createActionButton(
                    IKey.lang("blpc.party.link_bqu"), "Open Link dialog",
                    () -> PartyWidgets.openSubPanel(panel, LinkBQuDialog.build(panel)))
                    .size(80, 16).pos(8, btnY));
        }

        if (isOwner) {
            panel.child(PartyWidgets.createActionButton(
                    IKey.lang("blpc.party.disband"), "Open Disband dialog",
                    () -> PartyWidgets.openSubPanel(panel, DisbandDialog.build(panel)))
                    .size(50, 16).pos(W - 58, btnY));
        }

        return panel;
    }

    private static ButtonWidget<?> createMenuButton(IKey label, ModularPanel parent,
                                                    PanelFactory factory, String tooltipKey) {
        ButtonWidget<?> btn = (ButtonWidget<?>) new ButtonWidget<>().size(W - 16, 18)
                .padding(4, 0, 0, 0)
                .overlay(label.alignment(Alignment.CenterLeft))
                .onMousePressed(b -> {
                    PartyWidgets.openSubPanel(parent, factory.create());
                    return true;
                });
        if (tooltipKey != null) {
            btn.addTooltipLine(IKey.lang(tooltipKey));
        }
        return btn;
    }

    @FunctionalInterface
    interface PanelFactory {

        ModularPanel create();
    }
}
