package com.github.gtexpert.blpc.integration.bqu;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.TModule;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.util.Mods;
import com.github.gtexpert.blpc.integration.IntegrationSubmodule;
import com.github.gtexpert.blpc.module.Modules;

/**
 * BetterQuesting integration module.
 * <p>
 * Loaded only when {@code betterquesting} is installed. Replaces the
 * {@code DefaultPartyProvider} registered by {@code CoreModule} with a
 * {@link BQPartyProvider} that delegates to BQu's party system, and registers
 * the native BQu party screen as an alternative entry point on the client.
 */
@TModule(
         moduleID = Modules.MODULE_BQU,
         containerID = Tags.MODID,
         modDependencies = Mods.Names.BETTER_QUESTING,
         name = "BLPC BetterQuesting Integration",
         description = "BetterQuesting Integration Module. Uses BQu party system.")
public class BQuModule extends IntegrationSubmodule {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        PartyProviderRegistry.register(new BQPartyProvider());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            PartyProviderRegistry.registerNativeScreenOpener(BQuScreenHelper::openPartyScreen);
        }
    }

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            return Collections.singletonList(BQPartyEventHandler.class);
        }
        return Collections.emptyList();
    }
}
