package com.github.gtexpert.blpc;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.gtexpert.blpc.api.util.Mods;
import com.github.gtexpert.blpc.client.gui.KeyInputHandler;
import com.github.gtexpert.blpc.client.gui.MinimapHUD;
import com.github.gtexpert.blpc.client.gui.ModKeyBindings;
import com.github.gtexpert.blpc.module.ModuleManager;
import com.github.gtexpert.blpc.module.Modules;

@Mod(modid = Tags.MODID,
     version = Tags.VERSION,
     name = Tags.MODNAME,
     acceptedMinecraftVersions = "[1.12.2]",
     dependencies = "required-after:" + Mods.Names.MODULAR_UI + ";" + "after:" + Mods.Names.BETTER_QUESTING + ";" +
             "after:" + Mods.Names.JOURNEY_MAP + ";")
public class BLPCMod {

    public static BLPCMod INSTANCE;
    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    private ModuleManager moduleManager;

    @EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        INSTANCE = this;
        moduleManager = ModuleManager.getInstance();
        moduleManager.registerContainer(new Modules());
        moduleManager.setup(event.getASMHarvestedData(),
                Loader.instance().getConfigDir());
        moduleManager.onConstruction(event);
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        moduleManager.onPreInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        moduleManager.onInit(event);
        if (event.getSide().isClient()) {
            ModKeyBindings.init();
            MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
            MinecraftForge.EVENT_BUS.register(new MinimapHUD());
        }
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        moduleManager.onPostInit(event);
    }

    @EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        moduleManager.onServerAboutToStart(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        moduleManager.onServerStarting(event);
    }

    @EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        moduleManager.onServerStarted(event);
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        moduleManager.onServerStopping(event);
    }
}
