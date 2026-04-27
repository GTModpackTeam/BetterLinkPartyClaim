package com.github.gtexpert.blpc.client.gui.party;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyRole;

/**
 * Shared utilities for party UI panels.
 * <p>
 * Provides common operations used across multiple party panels such as
 * player display name resolution, role color mapping, and panel navigation.
 */
public final class PartyWidgets {

    private PartyWidgets() {}

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

    /** Cached handler for exclusive sub-panel opening. */
    private static IPanelHandler subPanelHandler;
    /** The parent panel the handler was created for. */
    private static ModularPanel handlerParent;
    /** The panel to open on next handler invocation. */
    private static ModularPanel pendingChild;

    /**
     * Opens a sub-panel as a child of the given parent panel.
     * Closes any previously opened sub-panel first (exclusive opening).
     * Recreates the handler if the parent panel changes.
     */
    public static void openSubPanel(ModularPanel parent, ModularPanel child) {
        pendingChild = child;
        if (subPanelHandler != null && handlerParent == parent) {
            subPanelHandler.deleteCachedPanel();
        } else {
            if (subPanelHandler != null) {
                subPanelHandler.deleteCachedPanel();
            }
            subPanelHandler = IPanelHandler.simple(parent, (pp, player) -> pendingChild, true);
            handlerParent = parent;
        }
        subPanelHandler.openPanel();
    }

    /**
     * Resets the sub-panel handler. Call when the parent screen is closed
     * to avoid stale references.
     */
    public static void resetSubPanelHandler() {
        subPanelHandler = null;
        handlerParent = null;
        pendingChild = null;
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
     * Reopens a panel by closing the current one and opening a fresh instance.
     * Useful for refreshing list contents after add/remove operations.
     *
     * @param current      the currently open panel to close
     * @param panelFactory factory to create the replacement panel
     */
    public static void reopenPanel(ModularPanel current, java.util.function.Supplier<ModularPanel> panelFactory) {
        ModularPanel parent = current.getScreen().getMainPanel();
        current.closeIfOpen();
        openSubPanel(parent, panelFactory.get());
    }

    /**
     * Registers a sync listener that auto-rebuilds the panel when server data changes.
     * The listener is automatically removed when the panel closes.
     *
     * @param panel     the currently open panel
     * @param rebuilder factory that produces the replacement panel; may return {@code null} to just close
     */
    public static void addAutoRefreshListener(ModularPanel panel, Supplier<ModularPanel> rebuilder) {
        Runnable syncListener = () -> {
            if (!panel.isOpen()) return;
            ModularPanel newPanel = rebuilder.get();
            if (newPanel != null) {
                reopenPanel(panel, () -> newPanel);
            } else {
                panel.closeIfOpen();
            }
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
     * Registers a sync listener that rebuilds the panel when a specific party is updated.
     * Convenience wrapper around {@link #addAutoRefreshListener} for the common pattern of
     * looking up a party by ID and delegating to a panel builder function.
     *
     * @param panel     the currently open panel
     * @param partyId   the party UUID to look up on each sync
     * @param rebuilder factory that produces the replacement panel from the refreshed party
     */
    public static void addPartyRefreshListener(ModularPanel panel, UUID partyId,
                                               Function<Party, ModularPanel> rebuilder) {
        addAutoRefreshListener(panel, () -> {
            Party refreshed = ClientPartyCache.getParty(partyId);
            return refreshed != null ? rebuilder.apply(refreshed) : null;
        });
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
                .widthRel(1f).heightRel(1f)
                .child(searchBox)
                .child(list.widthRel(1f).expanded());
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
