package com.github.gtexpert.blpc.core;

import java.util.Arrays;
import java.util.List;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.relauncher.Side;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.BLPCMod;
import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.IModule;
import com.github.gtexpert.blpc.api.modules.TModule;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.common.chunk.TicketManager;
import com.github.gtexpert.blpc.common.command.BLPCCommand;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.module.Modules;

@TModule(
         moduleID = Modules.MODULE_CORE,
         containerID = Tags.MODID,
         name = "BLPC Core",
         description = "Core module of BLPC",
         coreModule = true)
public class CoreModule implements IModule {

    @Override
    public @NotNull Logger getLogger() {
        return ModLog.ROOT;
    }

    @Override
    public void registerPackets() {
        ModNetwork.init();
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ForgeChunkManager.setForcedChunkLoadingCallback(BLPCMod.INSTANCE, new TicketManager());
        PartyProviderRegistry.register(new DefaultPartyProvider());
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new CoreEventHandler.ClientHandler());
        }
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        BLPCSaveHandler.INSTANCE.loadAll(event.getServer());
        event.registerServerCommand(new BLPCCommand());
    }

    @Override
    public void serverStopping(FMLServerStoppingEvent event) {
        BLPCSaveHandler.INSTANCE.saveAll();
    }

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return Arrays.asList(CoreEventHandler.class, ChunkProtectionHandler.class);
    }
}
