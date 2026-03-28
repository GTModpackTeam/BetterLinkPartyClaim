package com.github.gtexpert.blpc.client.gui.party.widget;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.github.gtexpert.blpc.client.gui.GuiColors;

/**
 * Reusable confirmation dialog template.
 * <p>
 * Produces a {@link Dialog Dialog&lt;Boolean&gt;} with a title, message, and
 * yes/no buttons. On confirmation ({@code true}), the provided callback runs.
 * <p>
 * By default, the parent panel is closed on confirmation. Call
 * {@link Builder#closeParent(boolean)} to override this behavior.
 * <p>
 * Usage:
 *
 * <pre>
 * {@code
 * ConfirmDialog.builder("blpc.party.dialog.disband")
 *     .title("blpc.party.disband_confirm_title")
 *     .message("blpc.party.disband_confirm_msg")
 *     .yesLabel("blpc.party.disband_yes")
 *     .noLabel("blpc.party.disband_no")
 *     .onConfirm(() -> { ... })
 *     .build(parentPanel);
 * }
 * </pre>
 */
public final class ConfirmDialog {

    private ConfirmDialog() {}

    /** Creates a new builder for a confirmation dialog. */
    public static Builder builder(String panelId) {
        return new Builder(panelId);
    }

    public static class Builder {

        private final String panelId;
        private String titleKey = "";
        private String messageKey = "";
        private String yesKey = "blpc.map.yes";
        private String noKey = "blpc.map.no";
        private Runnable onConfirm = () -> {};
        private boolean closeParentOnConfirm = true;
        private int width = 220;
        private int height = 70;

        Builder(String panelId) {
            this.panelId = panelId;
        }

        /** Lang key for the dialog title. */
        public Builder title(String langKey) {
            this.titleKey = langKey;
            return this;
        }

        /** Lang key for the dialog message. */
        public Builder message(String langKey) {
            this.messageKey = langKey;
            return this;
        }

        /** Lang key for the "Yes" button. Defaults to {@code blpc.map.yes}. */
        public Builder yesLabel(String langKey) {
            this.yesKey = langKey;
            return this;
        }

        /** Lang key for the "No" button. Defaults to {@code blpc.map.no}. */
        public Builder noLabel(String langKey) {
            this.noKey = langKey;
            return this;
        }

        /** Action to run when the user confirms. */
        public Builder onConfirm(Runnable action) {
            this.onConfirm = action;
            return this;
        }

        /**
         * Whether to close the parent panel on confirmation. Defaults to {@code true}.
         * <p>
         * Set to {@code false} for actions where the user should continue
         * interacting with the parent (e.g. Unlink BQu).
         */
        public Builder closeParent(boolean close) {
            this.closeParentOnConfirm = close;
            return this;
        }

        /** Dialog size in pixels. Defaults to 220×70. */
        public Builder size(int w, int h) {
            this.width = w;
            this.height = h;
            return this;
        }

        /**
         * Builds the dialog.
         *
         * @param parentPanel the parent panel (closed on confirm if {@link #closeParent} is true)
         */
        public Dialog<Boolean> build(ModularPanel parentPanel) {
            Runnable confirmAction = this.onConfirm;
            boolean shouldCloseParent = this.closeParentOnConfirm;
            String id = this.panelId;
            // Confirm/cancel logging is handled by DialogMixin
            Dialog<Boolean> dialog = new Dialog<>(panelId, result -> {
                if (Boolean.TRUE.equals(result)) {
                    confirmAction.run();
                    if (shouldCloseParent) {
                        parentPanel.closeIfOpen();
                    }
                }
            });
            dialog.setDisablePanelsBelow(true);
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(width, height);

            // Title + message in a column with automatic spacing
            dialog.child(Flow.col()
                    .childPadding(4)
                    .crossAxisAlignment(Alignment.CrossAxis.START)
                    .left(8).right(8).top(6)
                    .child(IKey.lang(titleKey).color(GuiColors.WHITE).shadow(true)
                            .asWidget())
                    .child(IKey.lang(messageKey).color(GuiColors.GRAY).shadow(true)
                            .asWidget()));

            // Yes/No buttons pinned to the bottom
            Flow buttonRow = Flow.row()
                    .childPadding(8)
                    .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                    .left(8).right(8).bottom(6).height(16);

            buttonRow.child(new ButtonWidget<>().size(80, 16)
                    .overlay(IKey.lang(yesKey))
                    .onMousePressed(btn -> {
                        dialog.closeWith(true);
                        return true;
                    }));
            buttonRow.child(new ButtonWidget<>().size(80, 16)
                    .overlay(IKey.lang(noKey))
                    .onMousePressed(btn -> {
                        dialog.closeWith(false);
                        return true;
                    }));

            dialog.child(buttonRow);

            return dialog;
        }
    }
}
