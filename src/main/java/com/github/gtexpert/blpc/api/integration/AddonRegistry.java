package com.github.gtexpert.blpc.api.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.cleanroommc.modularui.screen.ModularPanel;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Add-on registration API for BLPC integrations.
 * <p>
 * Use this instead of {@link IntegrationPanelRegistry} to register
 * third-party integrations (BQu, JourneyMap, ProjectE, ...). Each
 * add-on is identified by a {@code modId} string — when the same
 * modId is registered twice the second call is silently ignored,
 * keeping the registry idempotent across multiple initializations.
 * <p>
 * Registration must happen during {@code FMLInitializationEvent} on
 * the {@link Side#CLIENT} thread. Add-ons that depend on runtime
 * features (e.g. a mod being present, a party existing) can pass a
 * custom {@link BooleanSupplier} to control visibility.
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * // In JMapModule.init():
 * AddonRegistry.register(
 *     "journeymap", "blpc.addons.journeymap", "blpc.addons.journeymap.overlays_tooltip",
 *     () -> true,
 *     JMapSettingsPanel::build
 * );
 *
 * // Action-only (no panel, e.g. open external screen):
 * AddonRegistry.registerAction(
 *     "journeymap", "blpc.addons.journeymap", null,
 *     () -> JourneyMap.openMap()
 * );
 * }</pre>
 *
 * @see IntegrationPanelRegistry  (the concrete registry used by {@code AddonsPanel})
 */
@SideOnly(Side.CLIENT)
public final class AddonRegistry {

    private AddonRegistry() {}

    /**
     * Registers an add-on that opens a settings sub-panel.
     *
     * @param modId      unique identifier for the add-on mod (used for dedup)
     * @param labelKey   lang key for the button label
     * @param tooltipKey lang key for the button tooltip, or {@code null}
     * @param available  runtime predicate — the add-on is hidden when it returns {@code false}
     * @param factory    builds the add-on's settings panel for the given player UUID
     */
    public static void register(String modId, String labelKey, String tooltipKey,
                                BooleanSupplier available,
                                Function<UUID, ModularPanel> factory) {
        // Dedup by modId — same mod registered twice is silently ignored
        for (IntegrationPanelRegistry.Entry entry : IntegrationPanelRegistry.getEntries()) {
            if (modId.equals(entry.getModId())) {
                return;
            }
        }
        IntegrationPanelRegistry.register(modId, labelKey, tooltipKey, available, factory);
    }

    /**
     * Registers an add-on that runs an action (no sub-panel).
     *
     * @param modId      unique identifier for the add-on mod (used for dedup)
     * @param labelKey   lang key for the button label
     * @param tooltipKey lang key for the button tooltip, or {@code null}
     * @param available  runtime predicate — the add-on is hidden when it returns {@code false}
     * @param action     action to run when the button is clicked
     */
    public static void registerAction(String modId, String labelKey, String tooltipKey,
                                      BooleanSupplier available,
                                      Runnable action) {
        for (IntegrationPanelRegistry.Entry entry : IntegrationPanelRegistry.getEntries()) {
            if (modId.equals(entry.getModId())) {
                return;
            }
        }
        IntegrationPanelRegistry.registerAction(modId, labelKey, tooltipKey, available, action);
    }
}
