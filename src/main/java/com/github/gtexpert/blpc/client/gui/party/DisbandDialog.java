package com.github.gtexpert.blpc.client.gui.party;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.Dialog;

import com.github.gtexpert.blpc.client.gui.party.widget.ConfirmDialog;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;

/**
 * Disband confirmation dialog (panel ID: {@value #PANEL_ID}).
 * <p>
 * Built using {@link ConfirmDialog} template. On confirmation, sends a disband
 * message and clears the client-side party cache. MainPanel transitions to
 * {@link CreatePanel} automatically when the server sync arrives.
 */
public class DisbandDialog {

    public static final String PANEL_ID = "blpc.party.dialog.disband";

    /** Builds the disband confirmation dialog. */
    public static Dialog<Boolean> build(ModularPanel parentPanel) {
        return ConfirmDialog.builder(PANEL_ID)
                .title("blpc.party.disband_confirm_title")
                .message("blpc.party.disband_confirm_msg")
                .yesLabel("blpc.party.disband_yes")
                .noLabel("blpc.party.disband_no")
                .closeParent(false)
                .onConfirm(() -> {
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.disband());
                    PartyWidgets.clearLocalPartyData();
                })
                .build(parentPanel);
    }
}
