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

        /**
         * @deprecated Use {@link #Entry(String, String, String, BooleanSupplier, Function)} instead.
         *             Old entries get {@code modId=null} which prevents AddonRegistry dedup.
         */
        @Deprecated
        Entry(String labelKey, String tooltipKey, BooleanSupplier available,
              Function<UUID, ModularPanel> factory) {
            this.modId = null;
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.available = available;
            this.factory = factory;
            this.action = null;
        }

        Entry(String modId, String labelKey, String tooltipKey, BooleanSupplier available,
              Function<UUID, ModularPanel> factory) {
            this.modId = modId;
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.available = available;
            this.factory = factory;
            this.action = null;
        }

        /**
         * @deprecated Use {@link #Entry(String, String, String, BooleanSupplier, Runnable)} instead.
         */
        @Deprecated
        Entry(String labelKey, String tooltipKey, BooleanSupplier available,
              Runnable action) {
            this.modId = null;
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.available = available;
            this.factory = null;
            this.action = action;
        }

        /** Unique add-on identifier set when registered via AddonRegistry, or {@code null}. */
        public String getModId() {
            return modId;
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
     * Registers an integration's settings entry with a mod-specific id.
     *
     * @param modId      unique identifier for the mod (used for dedup)
     * @param labelKey   lang key for the button label
     * @param tooltipKey lang key for the button tooltip, or {@code null}
     * @param available  runtime predicate — the entry is hidden when it returns {@code false}
     * @param factory    builds the mod's settings panel for the given player UUID
     */
    public static void register(String modId, String labelKey, String tooltipKey,
                                BooleanSupplier available,
                                Function<UUID, ModularPanel> factory) {
        // Dedup by modId — same mod registered twice is silently ignored
        for (Entry e : ENTRIES) {
            if (modId.equals(e.getModId())) {
                return;
            }
        }
        ENTRIES.add(new Entry(modId, labelKey, tooltipKey, available, factory));
    }

    /**
     * Registers an action-only entry (no sub-panel). Clicking the button runs {@code action}
     * directly — useful for opening an external screen (e.g. JourneyMap's own settings UI).
     *
     * @param modId      unique identifier for the mod (used for dedup)
     * @param labelKey   lang key for the button label
     * @param tooltipKey lang key for the button tooltip, or {@code null}
     * @param available  runtime predicate — the entry is hidden when it returns {@code false}
     * @param action     action to run when the button is clicked
     */
    public static void registerAction(String modId, String labelKey, String tooltipKey,
                                      BooleanSupplier available,
                                      Runnable action) {
        for (Entry e : ENTRIES) {
            if (modId.equals(e.getModId())) {
                return;
            }
        }
        ENTRIES.add(new Entry(modId, labelKey, tooltipKey, available, action));
    }

    /** All registered entries, in registration order. */
    static List<Entry> getEntries() {
        return Collections.unmodifiableList(ENTRIES);
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
