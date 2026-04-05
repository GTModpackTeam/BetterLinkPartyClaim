package com.github.gtexpert.blpc.client.gui.party.widget;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ListWidget;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.client.gui.party.PanelSizes;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;

/**
 * Reusable party selection dialog.
 * <p>
 * Displays a scrollable list of all known parties (except excluded ones).
 * Selecting a party invokes the {@code onSelect} callback and closes the dialog.
 * <p>
 * Usage:
 *
 * <pre>
 * {@code
 * PartySelectDialog.builder("blpc.party.dialog.add_ally")
 *     .excluded(excludedIds)
 *     .onSelect(selectedId -> { ... })
 *     .build();
 * }
 * </pre>
 */
public final class PartySelectDialog {

    private PartySelectDialog() {}

    public static Builder builder(String panelId) {
        return new Builder(panelId);
    }

    public static class Builder {

        private final String panelId;
        private Set<UUID> excluded = Collections.emptySet();
        private Consumer<UUID> onSelect = uuid -> {};

        Builder(String panelId) {
            this.panelId = panelId;
        }

        /** Party IDs to hide from the list. */
        public Builder excluded(Set<UUID> excluded) {
            this.excluded = excluded;
            return this;
        }

        /** Called with the selected party's UUID when the user clicks a row. */
        public Builder onSelect(Consumer<UUID> onSelect) {
            this.onSelect = onSelect;
            return this;
        }

        /** Builds the dialog. */
        public Dialog<Void> build() {
            Dialog<Void> dialog = new Dialog<>(panelId);
            dialog.setDisablePanelsBelow(true);
            dialog.setCloseOnOutOfBoundsClick(true);
            dialog.size(PanelSizes.DIALOG_W, PanelSizes.SELECT_H);

            dialog.child(IKey.lang(panelId).color(GuiColors.WHITE).shadow(true)
                    .asWidget().pos(8, 6));
            dialog.child(ButtonWidget.panelCloseButton());

            Set<UUID> excludedFinal = this.excluded;
            Consumer<UUID> onSelectFinal = this.onSelect;

            var visible = ClientPartyCache.getAllParties().stream()
                    .filter(p -> !excludedFinal.contains(p.getPartyId()))
                    .collect(Collectors.toList());

            dialog.child(new ListWidget<>()
                    .left(8).right(8).top(22).bottom(4)
                    .crossAxisAlignment(Alignment.CrossAxis.START)
                    .children(visible, p -> {
                        UUID pId = p.getPartyId();
                        String pName = p.getName();
                        return new ButtonWidget<>().height(16).padding(4, 0, 0, 0)
                                .overlay(IKey.str(pName).alignment(Alignment.CenterLeft))
                                .onMousePressed(btn -> {
                                    onSelectFinal.accept(pId);
                                    dialog.closeWith(null);
                                    return true;
                                });
                    }));
            return dialog;
        }
    }
}
