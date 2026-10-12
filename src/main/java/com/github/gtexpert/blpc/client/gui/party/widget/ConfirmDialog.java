package com.github.gtexpert.blpc.client.gui.party.widget;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.github.gtexpert.blpc.client.gui.party.PartyWidgets;

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
 * 
 * {
 *     &#64;code
 *     // One-liner: build + open immediately (scheduled on the render thread)
 *     ConfirmDialog.show(parentPanel, "my.addon.confirm_title", "my.addon.confirm_msg",
 *             () -> doSomething());
 *
 *     // Direct use: build() returns a Dialog&lt;Boolean&gt;, which is a ModularPanel —
 *     // it must be opened through an IPanelHandler, not opened directly
 *     IPanelHandler handler = IPanelHandler.simple(parentPanel,
 *             (pp, p) -> ConfirmDialog.builder(panelId)
 *                     .title("my.addon.confirm_title")
 *                     .message("my.addon.confirm_msg")
 *                     .onConfirm(() -> doSomething())
 *                     .build(parentPanel),
 *             true);
 *
 *     // Open via a button (e.g. click handler)
 *     panel.child(PartyWidgets.dialogButton(IKey.lang("my.addon.confirm"), handler));
 *
 *     // Or open immediately
 *     handler.openPanel();
 * }
 * </pre>
 */
public final class ConfirmDialog {

    private ConfirmDialog() {}

    /** Creates a new builder for a confirmation dialog. */
    public static Builder builder(String panelId) {
        return new Builder(panelId);
    }

    /**
     * One-liner for the common case: build a title/message/confirm dialog and open it
     * immediately, scheduled on the render thread. Skips the {@link IPanelHandler} boilerplate
     * needed to open a {@link Builder#build} result directly.
     *
     * @param parentPanel the panel the dialog is shown over (closed on confirm by default)
     * @param titleKey    lang key for the dialog title
     * @param messageKey  lang key for the dialog message
     * @param onConfirm   action to run when the user confirms
     */
    public static void show(ModularPanel parentPanel, String titleKey, String messageKey, Runnable onConfirm) {
        show(parentPanel, builder -> builder.title(titleKey).message(messageKey).onConfirm(onConfirm));
    }

    /**
     * Same as {@link #show(ModularPanel, String, String, Runnable)} but exposes the full
     * {@link Builder} (e.g. for custom {@code yesLabel}/{@code noLabel}/{@code closeParent}).
     */
    public static void show(ModularPanel parentPanel, Consumer<Builder> configurer) {
        Builder builder = builder(PartyWidgets.uniquePanelId("blpc.confirm_dialog"));
        configurer.accept(builder);
        IPanelHandler handler = IPanelHandler.simple(parentPanel, (pp, p) -> builder.build(parentPanel), true);
        Minecraft.getMinecraft().addScheduledTask(handler::openPanel);
    }

    public static class Builder {

        private final String panelId;
        private String titleKey = "";
        private String messageKey = "";
        private String yesKey = "blpc.map.yes";
        private String noKey = "blpc.map.no";
        private Runnable onConfirm = () -> {};
        private boolean closeParentOnConfirm = true;
        private int width = PartyWidgets.DIALOG_W;
        private int height = PartyWidgets.DIALOG_H;

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
         * Set to {@code false} for actions that close the screen themselves
         * (e.g. Disband, which calls {@code displayGuiScreen(null)}).
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
         * @return a {@link Dialog} panel — open it via {@link IPanelHandler#simple} (it is a
         *         {@link ModularPanel}, not an {@link IPanelHandler} itself)
         */
        public Dialog<Boolean> build(ModularPanel parentPanel) {
            Runnable confirmAction = this.onConfirm;
            boolean shouldCloseParent = this.closeParentOnConfirm;
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

            // Title + message
            dialog.child(PartyWidgets.dialogHeader(titleKey, messageKey));

            // Yes/No buttons pinned to the bottom
            Flow buttonRow = Flow.row()
                    .childPadding(8)
                    .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                    .left(8).right(8).bottom(6).height(PartyWidgets.CONFIRM_BTN_H);

            buttonRow.child(new ButtonWidget<>().size(PartyWidgets.CONFIRM_BTN_W, PartyWidgets.CONFIRM_BTN_H)
                    .overlay(PartyWidgets.buttonLabel(IKey.lang(yesKey)))
                    .onMousePressed(btn -> {
                        dialog.closeWith(true);
                        return true;
                    }));
            buttonRow.child(new ButtonWidget<>().size(PartyWidgets.CONFIRM_BTN_W, PartyWidgets.CONFIRM_BTN_H)
                    .overlay(PartyWidgets.buttonLabel(IKey.lang(noKey)))
                    .onMousePressed(btn -> {
                        dialog.closeWith(false);
                        return true;
                    }));

            dialog.child(buttonRow);

            return dialog;
        }
    }
}
