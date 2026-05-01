package com.github.gtexpert.blpc.integration;

import java.util.Collections;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.api.util.ModUtility;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.module.BaseModule;
import com.github.gtexpert.blpc.module.Modules;

/**
 * Base class for third-party integration modules (BQu, JourneyMap, ...).
 * Declaring {@link IntegrationModule} as a hard dependency means turning
 * integrations off in config cascades to every submodule automatically.
 */
public abstract class IntegrationSubmodule extends BaseModule {

    private static final Set<ResourceLocation> DEPENDENCY_UID = Collections.singleton(
            ModUtility.id(Modules.MODULE_INTEGRATION));

    @NotNull
    @Override
    public Logger getLogger() {
        return ModLog.ROOT;
    }

    @NotNull
    @Override
    public Set<ResourceLocation> getDependencyUids() {
        return DEPENDENCY_UID;
    }
}
