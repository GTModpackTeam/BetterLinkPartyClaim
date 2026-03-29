package com.github.gtexpert.blpc.integration.bqu;

import java.util.Collections;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.TModule;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.util.Mods;
import com.github.gtexpert.blpc.integration.IntegrationSubmodule;
import com.github.gtexpert.blpc.module.Modules;

@TModule(
         moduleID = Modules.MODULE_BQU,
         containerID = Tags.MODID,
         modDependencies = Mods.Names.BETTER_QUESTING,
         name = "BLPC BetterQuesting Integration",
         description = "BetterQuesting Integration Module. Uses BQu party system and migrates existing parties.")
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

    @Override
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            BQMigrationHelper.migrateIfNeeded(server.getEntityWorld());
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
