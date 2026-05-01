package com.github.gtexpert.blpc.client.gui.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.client.gui.PlayerFaceDrawable;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Shared utilities, constants, and layout helpers for party UI panels.
 * <p>
 * Consolidates panel size constants, common layout operations (header, list),
 * player display name resolution, role color mapping, and panel navigation.
 */
public final class PartyWidgets {

    public static final int STANDARD_W = 220;
    public static final int STANDARD_H = 180;
    public static final int LARGE_W = 260;
    public static final int LARGE_H = 220;
    public static final int DIALOG_W = 220;
    public static final int DIALOG_H = 70;
    public static final int BTN_H = 18;
    public static final int FACE_SIZE = 8;
    public static final int TAB_H = 16;

    private PartyWidgets() {}

    /**
     * Adds a centered title and a close button to the given panel.
     *
     * @param panel the panel to add to
     * @param title the title (IKey)
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

    /**
     * Resolves a player UUID to a display name.
     * <p>
     * Checks in order: party name cache (synced from server), local player,
     * network player info, then falls back to a truncated UUID string.
     */
    public static String getDisplayName(UUID uuid) {
        // Check party name cache first (covers offline players)
        for (Party p : ClientPartyCache.getAllParties()) {
            String cached = p.getPlayerName(uuid);
            if (cached != null) return cached;
        }
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

    /**
     * Returns the ARGB color for a party role.
     *
     * @return gold for OWNER, green for ADMIN, white for others
     */
    public static int getRoleColor(PartyRole role) {
        return switch (role) {
            case OWNER -> GuiColors.GOLD;
            case ADMIN -> GuiColors.GREEN;
            case MEMBER -> GuiColors.WHITE;
        };
    }

    /**
     * Creates a button that runs an action when clicked.
     * Button clicks are automatically logged by MUI mixin.
     *
     * @param label      the button label
     * @param actionName human-readable name (unused, for code readability)
     * @param action     the action to run on click
     * @return the configured button widget
     */
    public static ButtonWidget<?> createActionButton(IKey label, String actionName, Runnable action) {
        return (ButtonWidget<?>) new ButtonWidget<>()
                .overlay(label)
                .onMousePressed(btn -> {
                    action.run();
                    return true;
                });
    }

    /**
     * Registers a sync listener that closes the panel when server data changes.
     * The listener is automatically removed when the panel closes.
     * <p>
     * Skips closing while a sub-panel is on top: {@link ModularPanel#closeIfOpen()}
     * always cascades to {@code closeSubPanels()}, which would dismiss the user's
     * in-progress sub-panel (Settings, Members, etc.) on every server sync.
     * Dynamic widget bindings (BoolValue.Dynamic, IKey.dynamic, ...) keep visible
     * values fresh without rebuilding the panel.
     *
     * @param panel the currently open panel
     */
    public static void addSyncCloseListener(ModularPanel panel) {
        Runnable syncListener = () -> {
            if (!panel.isOpen()) return;
            if (panel.getScreen().getPanelManager().getTopMostPanel() != panel) return;
            panel.closeIfOpen();
        };
        ClientPartyCache.addSyncListener(syncListener);
        panel.onCloseAction(() -> ClientPartyCache.removeSyncListener(syncListener));
    }

    /** Optimistically sets the local BQu link flag for the current player. */
    public static void setLocalBQuLinked(boolean linked) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        ClientPartyCache.setLocalBQuLinked(playerId, linked);
        ClientPartyCache.fireSyncListeners();
    }

    /** Optimistically clears local party data (used after disband). */
    public static void clearLocalPartyData() {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        ClientPartyCache.setLocalBQuLinked(playerId, false);
        ClientPartyCache.clear();
        ClientPartyCache.fireSyncListeners();
    }

    /**
     * Adds a top-level tab row and paged content area to the given panel.
     * Tab row sits below the header (top=22), pages fill the remaining area.
     */
    public static void addTabs(ModularPanel panel, PagedWidget.Controller controller,
                               String[] labelKeys, IWidget[] pages) {
        var tabRow = Flow.row()
                .childPadding(2)
                .left(4).right(4).top(22).height(TAB_H);
        for (int i = 0; i < labelKeys.length; i++) {
            tabRow.child(new PageButton(i, controller).height(TAB_H).expanded()
                    .overlay(IKey.lang(labelKeys[i])));
        }
        var paged = new PagedWidget<>()
                .controller(controller)
                .left(4).right(4).top(40).bottom(4);
        for (IWidget page : pages) {
            paged.addPage(page);
        }
        panel.child(tabRow);
        panel.child(paged);
    }

    /**
     * Builds an inner (nested) tabbed widget — a tab row stacked above paged content
     * inside a {@link Flow#column()} that fills its parent.
     */
    public static IWidget buildInnerTabs(String[] labelKeys, IWidget[] pages) {
        var controller = new PagedWidget.Controller();
        var tabRow = Flow.row()
                .childPadding(2)
                .widthRel(1f).height(TAB_H);
        for (int i = 0; i < labelKeys.length; i++) {
            tabRow.child(new PageButton(i, controller).height(TAB_H).expanded()
                    .overlay(IKey.lang(labelKeys[i])));
        }
        var paged = new PagedWidget<>()
                .controller(controller)
                .widthRel(1f).expanded();
        for (IWidget page : pages) {
            paged.addPage(page);
        }
        return Flow.column()
                .widthRel(1f).heightRel(1f)
                .child(tabRow)
                .child(paged);
    }

    /**
     * Builds a searchable list of rows. If entries is empty, returns a single-row column
     * with the empty-state lang key. Otherwise wraps the list with a search text field
     * that filters rows by their extracted name. Always returns a {@link Flow} that fills
     * the parent (sizeRel(1f, 1f)) — supports {@code .margin(...)} for outer placement.
     */
    public static <T> Flow buildSearchableList(Collection<T> entries,
                                               Function<T, IWidget> rowFactory,
                                               Function<T, String> nameExtractor,
                                               String emptyStateKey) {
        @SuppressWarnings("unchecked")
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.widthRel(1f).heightRel(1f);
        list.crossAxisAlignment(Alignment.CrossAxis.START);

        var widgets = new ArrayList<IWidget>();
        var searchNames = new ArrayList<String>();
        for (T entry : entries) {
            IWidget row = rowFactory.apply(entry);
            widgets.add(row);
            searchNames.add(nameExtractor.apply(entry).toLowerCase(Locale.ROOT));
            list.child(row);
        }
        if (widgets.isEmpty()) {
            return Flow.column().child(IKey.lang(emptyStateKey).color(GuiColors.GRAY)
                    .asWidget().widthRel(1f).height(BTN_H).marginLeft(4));
        }
        return wrapWithSearchBox(list, widgets, searchNames);
    }

    /**
     * Wraps a list widget with a search text field that filters items by name.
     * Uses {@link ListWidget#collapseDisabledChild} (default {@code true}) to hide
     * non-matching items without removing them.
     *
     * @param list        the list widget containing all items
     * @param widgets     parallel list of widgets to toggle visibility on
     * @param searchNames parallel list of lowercase names to match against
     * @return a {@link Flow} column containing the search box and the filtered list
     */
    public static Flow wrapWithSearchBox(ListWidget<IWidget, ?> list,
                                         List<IWidget> widgets, List<String> searchNames) {
        String[] filterText = { "" };
        var searchBox = new TextFieldWidget()
                .widthRel(1f).height(14)
                .hintText(IKey.lang("blpc.party.search").get())
                .autoUpdateOnChange(true)
                .value(new StringValue.Dynamic(
                        () -> filterText[0],
                        text -> {
                            filterText[0] = text;
                            String lower = text.toLowerCase(Locale.ROOT);
                            for (int i = 0; i < widgets.size(); i++) {
                                widgets.get(i).setEnabled(lower.isEmpty() || searchNames.get(i).contains(lower));
                            }
                        }));

        return Flow.column()
                .child(searchBox)
                .child(list.widthRel(1f).expanded());
    }

    /**
     * Formats a member label as {@code "Name [Role]"}.
     * Returns just the name if role is {@code null}.
     */
    public static String formatMemberLabel(String name, PartyRole role) {
        if (role == null) return name;
        String roleStr = IKey.lang("blpc.party.role." + role.name().toLowerCase(Locale.ROOT)).get();
        return name + " [" + roleStr + "]";
    }

    /**
     * Creates a standard player-row button with face icon and label.
     * Callers chain {@code .onMousePressed()} and {@code .addTooltipLine()} as needed.
     */
    public static ButtonWidget<?> createPlayerRow(UUID uuid, String label, int color) {
        var btn = new ButtonWidget<>();
        btn.widthRel(1f).height(BTN_H).padding(0);
        btn.hoverBackground(new Rectangle().color(GuiColors.HOVER));
        btn.child(Flow.row()
                .widthRel(1f).heightRel(1f)
                .padding(4, 0, 0, 0)
                .childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new PlayerFaceDrawable(uuid).asWidget()
                        .size(FACE_SIZE, FACE_SIZE))
                .child(IKey.str(label).color(color).shadow(true).alignment(Alignment.CenterLeft)
                        .asWidget().expanded()));
        return btn;
    }

    /**
     * Creates a {@link TextFieldWidget} that calls {@code onSubmit} when the Enter key is pressed.
     * <p>
     * Shared between {@link com.github.gtexpert.blpc.client.gui.party.widget.InputDialog} and
     * {@link com.github.gtexpert.blpc.client.gui.party.CreatePanel} to avoid duplicating the
     * {@code onKeyPressed} override.
     *
     * @param onSubmit action to execute on Enter key press
     * @return a configured text field widget
     */
    public static TextFieldWidget createEnterSubmitTextField(Runnable onSubmit) {
        return new TextFieldWidget() {

            @Override
            public Interactable.Result onKeyPressed(char c, int keyCode) {
                if (keyCode == Keyboard.KEY_RETURN) {
                    onSubmit.run();
                    return Interactable.Result.SUCCESS;
                }
                return super.onKeyPressed(c, keyCode);
            }
        };
    }
}
