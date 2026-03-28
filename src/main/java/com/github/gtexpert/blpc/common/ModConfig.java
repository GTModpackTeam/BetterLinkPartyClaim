package com.github.gtexpert.blpc.common;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.github.gtexpert.blpc.Tags;

@Config(modid = Tags.MODID,
        name = Tags.MODID + "/" + Tags.MODID,
        category = "BLPC Core")
public class ModConfig {

    @Config.Name("Max Claims Per Player")
    @Config.Comment("Maximum chunks a player can claim.")
    @Config.RangeInt(min = 0, max = 1000)
    public static int maxClaimsPerPlayer = 64;

    @Config.Name("Max Force Loads Per Player")
    @Config.Comment("Maximum chunks a player can force load.")
    @Config.RangeInt(min = 0, max = 100)
    public static int maxForceLoadsPerPlayer = 8;

    @Config.Name("Show Minimap")
    public static boolean showMinimap = true;

    @Config.Name("Enable Chunk Protection")
    @Config.Comment("Master toggle for all chunk protection features.")
    public static boolean enableProtection = true;

    @Config.Name("Protect Against Mob Griefing")
    @Config.Comment("Deny mob griefing (endermen, zombies) in claimed chunks.")
    public static boolean protectMobGriefing = true;

    @Config.Name("Protect Against Fire Spread")
    @Config.Comment("Block fire spread into claimed chunks from unclaimed chunks.")
    public static boolean protectFireSpread = true;

    @Config.Name("Protect Against Fluid Flow")
    @Config.Comment("Block fluid interaction effects (cobble/obsidian gen) into claimed chunks from unclaimed sources.")
    public static boolean protectFluidFlow = true;

    // Sync config values immediately when changed via in-game settings GUI
    @Mod.EventBusSubscriber(modid = Tags.MODID)
    private static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(Tags.MODID)) {
                ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
