package com.github.gtexpert.blpc.integration;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.TModule;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.module.BaseModule;
import com.github.gtexpert.blpc.module.Modules;

/**
 * Umbrella module for third-party integrations.
 * <p>
 * Carries no logic of its own — every {@link IntegrationSubmodule} declares it
 * as a dependency, so disabling this module in {@code modules.cfg} disables
 * all integrations at once (BetterQuesting, JourneyMap, ...).
 */
@TModule(
         moduleID = Modules.MODULE_INTEGRATION,
         containerID = Tags.MODID,
         name = "BLPCMod Mod Integration",
         description = "General BLPCMod Integration Module. Disabling this disables all integration modules.")
public class IntegrationModule extends BaseModule {

    @NotNull
    @Override
    public Logger getLogger() {
        return ModLog.ROOT;
    }
}
