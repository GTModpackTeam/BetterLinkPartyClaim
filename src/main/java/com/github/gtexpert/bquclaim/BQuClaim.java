package com.github.gtexpert.bquclaim;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.gtexpert.bquclaim.chunk.TicketManager;
import com.github.gtexpert.bquclaim.gui.KeyInputHandler;
import com.github.gtexpert.bquclaim.gui.MinimapHUD;
import com.github.gtexpert.bquclaim.gui.ModKeyBindings;
import com.github.gtexpert.bquclaim.network.ModNetwork;

@Mod(modid = Tags.MODID,
     version = Tags.VERSION,
     name = Tags.MODNAME,
     acceptedMinecraftVersions = "[1.12.2]",
     dependencies = "required-after:betterquesting;required-after:modularui;after:journeymap;")
public class BQuClaim {

    public static BQuClaim INSTANCE;
    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        INSTANCE = this;
        ModNetwork.init();
        ForgeChunkManager.setForcedChunkLoadingCallback(INSTANCE, new TicketManager());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            ModKeyBindings.init();
            MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
            MinecraftForge.EVENT_BUS.register(new MinimapHUD());
        }
    }
}
