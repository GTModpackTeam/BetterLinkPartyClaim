package com.github.gtexpert.blpc.common;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.github.gtexpert.blpc.Tags;

@Config(modid = Tags.MODID, name = Tags.MODID + "/" + Tags.MODID)
public class ModConfig {

    @Config.LangKey("config.blpc.claims")
    public static final Claims claims = new Claims();

    @Config.LangKey("config.blpc.party")
    public static final Party party = new Party();

    @Config.LangKey("config.blpc.data")
    public static final Data data = new Data();

    public static class Claims {

        @Config.Name("Max Claims Per Player")
        @Config.Comment("Maximum chunks a player can claim.")
        @Config.RangeInt(min = 0, max = 10000)
        public int maxClaimsPerPlayer = 1000;

        @Config.Name("Max Force Loads Per Player")
        @Config.Comment("Maximum chunks a player can force load.")
        @Config.RangeInt(min = 0, max = 1000)
        public int maxForceLoadsPerPlayer = 64;
    }

    public static class Party {

        @Config.Name("Auto Create Party (Singleplayer)")
        @Config.Comment("Automatically create a party named after the player in singleplayer.")
        public boolean autoCreatePartySingleplayer = true;

        @Config.Name("Auto Create Party (Multiplayer)")
        @Config.Comment("Automatically create a party named after the player in multiplayer.")
        public boolean autoCreatePartyMultiplayer = false;

        @Config.Name("Auto Create Server Party")
        @Config.Comment("Automatically create a shared party on server start.")
        public boolean autoCreateServerParty = false;

        @Config.Name("Server Party Name")
        @Config.Comment("Name for the auto-created server party.")
        public String serverPartyName = "server";

        @Config.Name("Auto-Created Party: Free to Join")
        @Config.Comment("Enable free-to-join (auto-join) on auto-created parties.")
        public boolean autoCreatedPartyFreeToJoin = true;

        @Config.Name("Auto-Created Party: Owner UUID")
        @Config.Comment("UUID of the player who owns auto-created parties. Leave empty for server-owned.")
        public String autoCreatedPartyOwnerUUID = "";
    }

    public static class Data {

        @Config.Name("Merge Offline/Online Data")
        @Config.Comment("Merge chunk data when switching between offline and online mode.")
        public boolean mergeOfflineOnlineData = true;
    }

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
