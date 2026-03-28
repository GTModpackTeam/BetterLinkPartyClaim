package com.github.gtexpert.blpc.client.gui.party;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ButtonWidget;

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
        switch (role) {
            case OWNER:
                return GuiColors.GOLD;
            case ADMIN:
                return GuiColors.GREEN;
            default:
                return GuiColors.WHITE;
        }
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
}
