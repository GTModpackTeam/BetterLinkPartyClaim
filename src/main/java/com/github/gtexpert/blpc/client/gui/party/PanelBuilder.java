package com.github.gtexpert.blpc.client.gui.party;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;

import com.github.gtexpert.blpc.client.gui.GuiColors;

/** Common panel layout helpers to eliminate boilerplate across party panels. */
public final class PanelBuilder {

    private PanelBuilder() {}

    /**
     * Adds a centered title and a close button to the given panel.
     *
     * @param panel the panel to add to
     * @param title the title key (IKey)
     */
    public static void addHeader(ModularPanel panel, IKey title) {
        panel.child(title.color(GuiColors.WHITE).shadow(true)
                .asWidget().alignment(Alignment.Center).left(0).right(0).top(8).height(10));
        panel.child(ButtonWidget.panelCloseButton());
    }

    /**
     * Adds a centered title (looked up via lang key) and a close button.
     *
     * @param panel    the panel to add to
     * @param titleKey lang key for the title
     */
    public static void addHeader(ModularPanel panel, String titleKey) {
        addHeader(panel, IKey.lang(titleKey));
    }

    /**
     * Places the provided list starting at {@code top=22}.
     *
     * @param panel the panel to add to
     * @param list  the list widget to position and add
     */
    @SuppressWarnings("rawtypes")
    public static void addList(ModularPanel panel, ListWidget list) {
        list.left(8).right(8).top(22).bottom(8);
        list.crossAxisAlignment(Alignment.CrossAxis.START);
        panel.child(list);
    }
}
