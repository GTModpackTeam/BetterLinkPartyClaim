package com.github.gtexpert.blpc.api;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.IModuleManager;
import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.modules.ModuleManager;

/**
 * Central entry point for BLPC — {@link #partyProvider()} and {@link #moduleManager()}.
 * <p>
 * Addon authors should use {@code api.util.PartyQueryUtil} for queries,
 * {@code @TModule} for conditional loading, and {@code IntegrationPanelRegistry} for Addons hub panels.
 * Full API reference is in {@code DEVELOPER.md}.
 */
public final class BLPCAPI {

    /** The mod ID — stable public constant, decoupled from the build-generated {@code Tags}. */
    public static final String MODID = Tags.MODID;

    private BLPCAPI() {}

    /** The active party backend. Replace it via {@link PartyProviderRegistry#register(IPartyProvider)}. */
    public static IPartyProvider partyProvider() {
        return PartyProviderRegistry.get();
    }

    /** The module manager — query enabled modules and lifecycle stage. */
    public static IModuleManager moduleManager() {
        return ModuleManager.getInstance();
    }
}
