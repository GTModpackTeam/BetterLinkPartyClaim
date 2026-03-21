package com.github.gtexpert.blpc.client.gui;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

public class PartyScreen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 180;
    private static final int ROW_H = 12;

    public static ModularPanel buildAsPanel(UUID playerId, Runnable onRefresh) {
        Party myParty = ClientPartyCache.getPartyByPlayer(playerId);

        ModularPanel panel = new ModularPanel("blpc.party");
        panel.size(PANEL_W, PANEL_H);

        if (myParty == null) {
            buildCreateView(panel, onRefresh);
        } else {
            buildManageView(panel, myParty, playerId, onRefresh);
        }

        return panel;
    }

    private static void buildCreateView(ModularPanel panel, Runnable onRefresh) {
        TextFieldWidget nameField = new TextFieldWidget();
        nameField.size(140, 14).pos(8, 28);
        nameField.setText(IKey.lang(Party.DEFAULT_NAME_KEY).get());

        panel.child(IKey.lang("blpc.party.create_title").color(0xFFFFFFFF).shadow(true)
                .asWidget().pos(8, 8))
                .child(nameField)
                .child(new ButtonWidget<>().size(50, 14).pos(154, 28)
                        .overlay(IKey.lang("blpc.party.create"))
                        .onMousePressed(btn -> {
                            String name = nameField.getText().trim();
                            if (!name.isEmpty()) {
                                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.create(name));
                                if (onRefresh != null) {
                                    ClientPartyCache.setOnSyncCallback(onRefresh);
                                }
                            }
                            panel.closeIfOpen();
                            return true;
                        }))
                .child(ButtonWidget.panelCloseButton());
    }

    private static void buildManageView(ModularPanel panel, Party party, UUID playerId, Runnable onRefresh) {
        panel.child(IKey.str(party.getName()).color(0xFFFFFFFF).shadow(true)
                .asWidget().pos(8, 8));

        panel.child(ButtonWidget.panelCloseButton());

        // Member list
        int y = 26;
        Map<UUID, PartyRole> members = party.getMembers();
        for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
            UUID memberId = entry.getKey();
            PartyRole role = entry.getValue();
            String memberName = getMemberDisplayName(memberId);
            String roleStr = role.name().substring(0, 1) + role.name().substring(1).toLowerCase();

            panel.child(IKey.str(memberName + " [" + roleStr + "]")
                    .color(getRoleColor(role)).shadow(true)
                    .asWidget().pos(8, y));

            y += ROW_H;
            if (y > PANEL_H - 40) break;
        }

        int btnY = PANEL_H - 22;

        boolean bquAvailable = PartyProviderRegistry.hasNativeScreen();
        boolean bquLinked = ClientPartyCache.isBQuLinked(playerId);

        if (bquAvailable && bquLinked) {
            // BQu linked: only "Manage Party" button (opens BQu native screen)
            panel.child(new ButtonWidget<>().size(100, 16).pos((PANEL_W - 100) / 2, btnY)
                    .overlay(IKey.lang("blpc.party.open_native"))
                    .onMousePressed(btn -> {
                        panel.closeIfOpen();
                        Minecraft.getMinecraft().addScheduledTask(PartyProviderRegistry::openNativeScreen);
                        return true;
                    }));
        } else if (bquAvailable && !bquLinked) {
            // BQu available but not linked: self-managed buttons + "Link BQu" button
            buildSelfManagedButtons(panel, party, playerId, btnY);
            panel.child(new ButtonWidget<>().size(80, 16).pos(PANEL_W - 88, btnY - 20)
                    .overlay(IKey.lang("blpc.party.link_bqu"))
                    .onMousePressed(btn -> {
                        ModNetwork.INSTANCE.sendToServer(MessagePartyAction.toggleBQuLink(true));
                        panel.closeIfOpen();
                        Minecraft.getMinecraft().addScheduledTask(PartyProviderRegistry::openNativeScreen);
                        return true;
                    }));
        } else {
            buildSelfManagedButtons(panel, party, playerId, btnY);
        }
    }

    private static void buildSelfManagedButtons(ModularPanel panel, Party party, UUID playerId, int btnY) {
        PartyRole myRole = party.getRole(playerId);
        boolean isOwner = myRole == PartyRole.OWNER;

        if (myRole != null && myRole.canInvite()) {
            panel.child(new ButtonWidget<>().size(50, 16).pos(8, btnY)
                    .overlay(IKey.lang("blpc.party.invite"))
                    .onMousePressed(btn -> {
                        openInviteDialog(panel, party);
                        return true;
                    }));
        }

        if (myRole != null && myRole != PartyRole.OWNER) {
            panel.child(new ButtonWidget<>().size(50, 16).pos(62, btnY)
                    .overlay(IKey.lang("blpc.party.leave"))
                    .onMousePressed(btn -> {
                        ModNetwork.INSTANCE.sendToServer(MessagePartyAction.kickOrLeave(
                                Minecraft.getMinecraft().player.getName()));
                        panel.closeIfOpen();
                        return true;
                    }));
        }

        if (isOwner) {
            panel.child(new ButtonWidget<>().size(50, 16).pos(PANEL_W - 58, btnY)
                    .overlay(IKey.lang("blpc.party.disband"))
                    .onMousePressed(btn -> {
                        openDisbandConfirmDialog(panel, party);
                        return true;
                    }));
        }
    }

    private static void openDisbandConfirmDialog(ModularPanel parentPanel, Party party) {
        IPanelHandler.simple(parentPanel, (pp, player) -> {
            Dialog<Boolean> dialog = new Dialog<>("blpc.party.dialog.disband_confirm", result -> {
                if (Boolean.TRUE.equals(result)) {
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.disband());
                    parentPanel.closeIfOpen();
                }
            });
            dialog.setDisablePanelsBelow(true);
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(220, 70)
                    .child(IKey.lang("blpc.party.disband_confirm_title").color(0xFFFFFFFF).shadow(true)
                            .asWidget().top(6).left(8))
                    .child(IKey.lang("blpc.party.disband_confirm_msg").color(0xFFAAAAAA).shadow(true)
                            .asWidget().top(18).left(8))
                    .child(new ButtonWidget<>().size(80, 16).pos(10, 48)
                            .overlay(IKey.lang("blpc.party.disband_yes"))
                            .onMousePressed(btn -> {
                                dialog.closeWith(true);
                                return true;
                            }))
                    .child(new ButtonWidget<>().size(80, 16).pos(130, 48)
                            .overlay(IKey.lang("blpc.party.disband_no"))
                            .onMousePressed(btn -> {
                                dialog.closeWith(false);
                                return true;
                            }));
            return dialog;
        }, true).openPanel();
    }

    private static void openInviteDialog(ModularPanel parentPanel, Party party) {
        IPanelHandler.simple(parentPanel, (pp, player) -> {
            Dialog<Void> inviteDialog = new Dialog<>("blpc.party.dialog.invite");
            inviteDialog.setCloseOnOutOfBoundsClick(true);

            TextFieldWidget inviteField = new TextFieldWidget();
            inviteField.size(120, 14).pos(8, 24);

            inviteDialog.size(180, 60)
                    .child(IKey.lang("blpc.party.invite_title").color(0xFFFFFFFF).shadow(true)
                            .asWidget().pos(8, 6))
                    .child(inviteField)
                    .child(new ButtonWidget<>().size(40, 14).pos(132, 24)
                            .overlay(IKey.lang("blpc.party.send"))
                            .onMousePressed(btn -> {
                                String username = inviteField.getText().trim();
                                if (!username.isEmpty()) {
                                    ModNetwork.INSTANCE.sendToServer(
                                            MessagePartyAction.invite(username));
                                }
                                inviteDialog.closeIfOpen();
                                return true;
                            }))
                    .child(ButtonWidget.panelCloseButton());
            return inviteDialog;
        }, true).openPanel();
    }

    private static String getMemberDisplayName(UUID uuid) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.player.getUniqueID().equals(uuid)) {
            return mc.player.getName();
        }
        if (mc.getConnection() != null) {
            NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getGameProfile().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private static int getRoleColor(PartyRole role) {
        switch (role) {
            case OWNER:
                return 0xFFFFAA00;
            case ADMIN:
                return 0xFF55FF55;
            default:
                return 0xFFFFFFFF;
        }
    }
}
