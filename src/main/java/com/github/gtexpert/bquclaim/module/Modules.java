package com.github.gtexpert.bquclaim.module;

import com.github.gtexpert.bquclaim.Tags;
import com.github.gtexpert.bquclaim.api.modules.IModuleContainer;
import com.github.gtexpert.bquclaim.api.modules.ModuleContainer;

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
