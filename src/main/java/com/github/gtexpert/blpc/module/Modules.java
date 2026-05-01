package com.github.gtexpert.blpc.module;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.IModuleContainer;
import com.github.gtexpert.blpc.api.modules.ModuleContainer;

/**
 * BLPC's module container — also the canonical registry of module IDs.
 * <p>
 * Constants here are referenced from each module's {@link com.github.gtexpert.blpc.api.modules.TModule}
 * annotation and from dependency declarations, keeping IDs typo-safe and
 * grep-able. The container ID itself is the mod ID, so all built-in modules
 * live under the {@code blpc:} namespace.
 */
@ModuleContainer
public class Modules implements IModuleContainer {

    public static final String MODULE_CORE = "core";
    public static final String MODULE_INTEGRATION = "integration";

    // Integration modules
    public static final String MODULE_BQU = "bqu_integration";
    public static final String MODULE_JMAP = "jmap_integration";

    @Override
    public String getID() {
        return Tags.MODID;
    }
}
