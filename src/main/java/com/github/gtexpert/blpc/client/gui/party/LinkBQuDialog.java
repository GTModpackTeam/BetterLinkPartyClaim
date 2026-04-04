package com.github.gtexpert.blpc.client.gui.party;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.Dialog;

import com.github.gtexpert.blpc.client.gui.party.widget.ConfirmDialog;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;

/**
 * BQu link confirmation dialog (panel ID: {@value #PANEL_ID}).
 * <p>
 * Built using {@link ConfirmDialog} template. On confirmation, sends a
 * BQu link request to the server.
 */
public class LinkBQuDialog {

    public static final String PANEL_ID = "blpc.party.dialog.link_bqu";

    /** Builds the BQu link confirmation dialog. */
    public static Dialog<Boolean> build(ModularPanel parentPanel) {
        return ConfirmDialog.builder(PANEL_ID)
                .title("blpc.party.link_bqu_title")
                .message("blpc.party.link_bqu_msg")
                .closeParent(false)
                .onConfirm(() -> {
                    PartyWidgets.setLocalBQuLinked(true);
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.toggleBQuLink(true));
                })
                .build(parentPanel);
    }
}
