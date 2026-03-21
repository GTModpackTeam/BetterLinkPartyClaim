package com.github.gtexpert.bquclaim;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.gtexpert.bquclaim.api.util.Mods;
import com.github.gtexpert.bquclaim.client.gui.KeyInputHandler;
import com.github.gtexpert.bquclaim.client.gui.MinimapHUD;
import com.github.gtexpert.bquclaim.client.gui.ModKeyBindings;
import com.github.gtexpert.bquclaim.module.ModuleManager;
import com.github.gtexpert.bquclaim.module.Modules;

@Mod(modid = Tags.MODID,
     version = Tags.VERSION,
     name = Tags.MODNAME,
     acceptedMinecraftVersions = "[1.12.2]",
     dependencies = "required-after:" + Mods.Names.MODULAR_UI + ";" + "after:" + Mods.Names.BETTER_QUESTING + ";" +
             "after:" + Mods.Names.JOURNEY_MAP + ";")
public class BQuClaim {

    public static BQuClaim INSTANCE;
    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    private ModuleManager moduleManager;

    @EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        INSTANCE = this;
        moduleManager = ModuleManager.getInstance();
        moduleManager.registerContainer(new Modules());
        moduleManager.setup(event.getASMHarvestedData(),
                net.minecraftforge.fml.common.Loader.instance().getConfigDir());
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
}
