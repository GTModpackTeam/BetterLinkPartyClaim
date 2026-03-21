package com.github.gtexpert.bquclaim.integration.bqu;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.github.gtexpert.bquclaim.Tags;
import com.github.gtexpert.bquclaim.api.modules.TModule;
import com.github.gtexpert.bquclaim.api.party.PartyProviderRegistry;
import com.github.gtexpert.bquclaim.api.util.Mods;
import com.github.gtexpert.bquclaim.integration.IntegrationSubmodule;
import com.github.gtexpert.bquclaim.module.Modules;

@TModule(
         moduleID = Modules.MODULE_BQU,
         containerID = Tags.MODID,
         modDependencies = Mods.Names.BETTER_QUESTING,
         name = "BQuClaim BetterQuesting Integration",
         description = "BetterQuesting Integration Module. Enables party-based claim sharing.")
public class BQuModule extends IntegrationSubmodule {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        PartyProviderRegistry.register(new BQPartyProvider());
    }
}
