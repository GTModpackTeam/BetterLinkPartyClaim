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
import com.github.gtexpert.blpc.api.modules.TModule;
import com.github.gtexpert.blpc.api.util.Mods;
import com.github.gtexpert.blpc.integration.IntegrationSubmodule;
import com.github.gtexpert.blpc.module.Modules;

/**
 * JourneyMap integration module.
 * <p>
 * Loaded only when {@code journeymap} is installed. On the client, hooks the
 * claim sync stream so JourneyMap displays per-chunk claim overlays. The
 * sync handler is re-registered on every reconnect to recover from JourneyMap
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

    @Override
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            syncHandler = new JMapClaimSyncHandler();
            syncHandler.register();
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (syncHandler != null) {
            syncHandler.unregister();
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (syncHandler != null) {
            syncHandler.unregister();
            syncHandler.register();
        }
    }

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return Collections.emptyList();
    }
}
