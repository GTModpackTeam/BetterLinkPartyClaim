package com.github.gtexpert.blpc.api.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.cleanroommc.modularui.screen.ModularPanel;

/**
 * Registry of per-mod settings panels surfaced under the party menu's
 * <em>Addons</em> entry ({@code client.gui.AddonsPanel}). Each integration
 * module registers one {@link Entry} for its mod during client-side init, so
 * adding support for a new mod never requires touching the shared party UI.
 * <p>
 * This holder is intentionally not {@code @SideOnly}: integration modules invoke
 * {@link #register} from client-guarded init blocks via lazy method references
 * (mirroring {@code PartyProviderRegistry.registerNativeScreenOpener}), so the
 * client-only panel classes are never loaded on a dedicated server.
 */
public final class IntegrationPanelRegistry {

    /**
     * A single integration's settings entry — a labelled button in the Addons hub
     * that opens the mod's own settings panel.
     */
    public static final class Entry {

        private final String labelKey;
        private final String tooltipKey;
        private final BooleanSupplier available;
        private final Function<UUID, ModularPanel> factory;
        private final Runnable action;

        Entry(String labelKey, String tooltipKey, BooleanSupplier available,
              Function<UUID, ModularPanel> factory) {
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.available = available;
            this.factory = factory;
            this.action = null;
        }

        Entry(String labelKey, String tooltipKey, BooleanSupplier available, Runnable action) {
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.available = available;
            this.factory = null;
            this.action = action;
        }

        public String labelKey() {
            return labelKey;
        }

        /** Tooltip lang key, or {@code null} for no tooltip. */
        public String tooltipKey() {
            return tooltipKey;
        }

        public boolean isAvailable() {
            return available.getAsBoolean();
        }

        /** True when this entry opens a sub-panel; false when it runs an action instead. */
        public boolean hasPanel() {
            return factory != null;
        }

        public ModularPanel createPanel(UUID playerId) {
            return factory != null ? factory.apply(playerId) : null;
        }

        /** Runs the entry's action (e.g. opening an external settings screen). */
        public void runAction() {
            if (action != null) action.run();
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private IntegrationPanelRegistry() {}

    /**
     * Registers an integration's settings entry.
     *
     * @param labelKey   lang key for the button label (also serves as the panel title source)
     * @param tooltipKey lang key for the button tooltip, or {@code null}
     * @param available  runtime predicate — the entry is hidden when it returns {@code false}
     * @param factory    builds the mod's settings panel for the given player UUID
     */
    public static void register(String labelKey, String tooltipKey, BooleanSupplier available,
                                Function<UUID, ModularPanel> factory) {
        for (Entry e : ENTRIES) {
            if (e.labelKey.equals(labelKey)) {
                return;
            }
        }
        ENTRIES.add(new Entry(labelKey, tooltipKey, available, factory));
    }

    /**
     * Registers an action-only entry (no sub-panel). Clicking the button runs {@code action}
     * directly — useful for opening an external screen (e.g. JourneyMap's own settings UI).
     */
    public static void registerAction(String labelKey, String tooltipKey, BooleanSupplier available,
                                      Runnable action) {
        ENTRIES.add(new Entry(labelKey, tooltipKey, available, action));
    }

    /** Currently-available entries, in registration order. */
    public static List<Entry> available() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (e.isAvailable()) out.add(e);
        }
        return Collections.unmodifiableList(out);
    }

    /** True when at least one entry is currently available. */
    public static boolean hasAvailable() {
        for (Entry e : ENTRIES) {
            if (e.isAvailable()) return true;
        }
        return false;
    }
}
