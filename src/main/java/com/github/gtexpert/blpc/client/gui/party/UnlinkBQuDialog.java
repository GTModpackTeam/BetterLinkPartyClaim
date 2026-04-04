package com.github.gtexpert.blpc.client.gui.party;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.Dialog;

import com.github.gtexpert.blpc.client.gui.party.widget.ConfirmDialog;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;

/**
 * BQu unlink confirmation dialog (panel ID: {@value #PANEL_ID}).
 * <p>
 * Built using {@link ConfirmDialog} template. On confirmation, sends a
 * BQu unlink request to the server.
 */
public class UnlinkBQuDialog {

    public static final String PANEL_ID = "blpc.party.dialog.unlink_bqu";

    /** Builds the BQu unlink confirmation dialog. */
    public static Dialog<Boolean> build(ModularPanel parentPanel) {
        return ConfirmDialog.builder(PANEL_ID)
                .title("blpc.party.unlink_bqu_title")
                .message("blpc.party.unlink_bqu_msg")
                .closeParent(false)
                .onConfirm(() -> {
                    PartyWidgets.setLocalBQuLinked(false);
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.toggleBQuLink(false));
                })
                .build(parentPanel);
    }
}
