package com.github.gtexpert.bquclaim.core;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.bquclaim.BQuClaim;
import com.github.gtexpert.bquclaim.Tags;
import com.github.gtexpert.bquclaim.api.modules.IModule;
import com.github.gtexpert.bquclaim.api.modules.TModule;
import com.github.gtexpert.bquclaim.common.chunk.TicketManager;
import com.github.gtexpert.bquclaim.common.network.ModNetwork;
import com.github.gtexpert.bquclaim.module.Modules;

@TModule(
         moduleID = Modules.MODULE_CORE,
         containerID = Tags.MODID,
         name = "BQuClaim Core",
         description = "Core module of BQuClaim",
         coreModule = true)
public class CoreModule implements IModule {

    public static final Logger logger = LogManager.getLogger(Tags.MODNAME + " Core");

    @Override
    public @NotNull Logger getLogger() {
        return logger;
    }

    @Override
    public void registerPackets() {
        ModNetwork.init();
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ForgeChunkManager.setForcedChunkLoadingCallback(BQuClaim.INSTANCE, new TicketManager());
    }
}
