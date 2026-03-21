package com.github.gtexpert.blpc.module;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.IModuleContainer;
import com.github.gtexpert.blpc.api.modules.ModuleContainer;

@ModuleContainer
public class Modules implements IModuleContainer {

    public static final String MODULE_CORE = "core";
    public static final String MODULE_INTEGRATION = "integration";

    // Integration modules
    public static final String MODULE_BQU = "bqu_integration";

    @Override
    public String getID() {
        return Tags.MODID;
    }
}
