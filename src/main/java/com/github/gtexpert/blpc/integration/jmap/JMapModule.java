package com.github.gtexpert.blpc.integration.jmap;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.integration.AddonRegistry;
import com.github.gtexpert.blpc.api.integration.IntegrationPanelRegistry;
import com.github.gtexpert.blpc.api.modules.TModule;
import com.github.gtexpert.blpc.api.util.Mods;
import com.github.gtexpert.blpc.integration.IntegrationSubmodule;
import com.github.gtexpert.blpc.modules.Modules;

/**
 * JourneyMap integration module.
 * <p>
 * Loaded only when {@code journeymap} is installed. On the client, hooks the
 * claim sync stream so JourneyMap displays per-chunk claim overlays, and mirrors
 * party-shared waypoints onto the local JourneyMap waypoint store via the v2 API.
 * Sync handlers are re-registered on every reconnect to recover from JourneyMap
 * resetting its overlay state on disconnect.
 */
@TModule(
         moduleID = Modules.MODULE_JMAP,
         containerID = Tags.MODID,
         modDependencies = Mods.Names.JOURNEY_MAP,
         name = "BLPC JourneyMap Integration",
         description = "JourneyMap Integration Module. Displays chunk claim overlays on JourneyMap.")
public class JMapModule extends IntegrationSubmodule {

    @SideOnly(Side.CLIENT)
    private JMapClaimSyncHandler syncHandler;

    @SideOnly(Side.CLIENT)
    private JMapWaypointSyncHandler waypointSyncHandler;

    @Override
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            syncHandler = new JMapClaimSyncHandler();
            syncHandler.register();
            waypointSyncHandler = new JMapWaypointSyncHandler();
            waypointSyncHandler.register();
            MinecraftForge.EVENT_BUS.register(waypointSyncHandler);
            JMapWaypointOutgoing.register();
            MinecraftForge.EVENT_BUS.register(this);
            AddonRegistry.registerAction(
                    "journeymap", "blpc.addons.journeymap", null, () -> true, JMapSettingsPanel::open);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (syncHandler != null) {
            syncHandler.unregister();
        }
        if (waypointSyncHandler != null) {
            waypointSyncHandler.unregister();
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (syncHandler != null) {
            syncHandler.unregister();
            syncHandler.register();
        }
        if (waypointSyncHandler != null) {
            waypointSyncHandler.unregister();
            waypointSyncHandler.register();
        }
    }

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return Collections.emptyList();
    }
}
