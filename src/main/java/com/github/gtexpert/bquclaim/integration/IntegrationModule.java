package com.github.gtexpert.bquclaim.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.bquclaim.Tags;
import com.github.gtexpert.bquclaim.api.modules.TModule;
import com.github.gtexpert.bquclaim.module.BaseModule;
import com.github.gtexpert.bquclaim.module.Modules;

@TModule(
         moduleID = Modules.MODULE_INTEGRATION,
         containerID = Tags.MODID,
         name = "BQuClaim Mod Integration",
         description = "General BQuClaim Integration Module. Disabling this disables all integration modules.")
public class IntegrationModule extends BaseModule {

    public static final Logger logger = LogManager.getLogger("BQuClaim Mod Integration");

    @NotNull
    @Override
    public Logger getLogger() {
        return logger;
    }
}
