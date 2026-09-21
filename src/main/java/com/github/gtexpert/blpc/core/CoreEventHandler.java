package com.github.gtexpert.blpc.core;

import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.client.cache.ClientCachePersistence;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.ClientClaimCache;
import com.github.gtexpert.blpc.common.chunk.TicketManager;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.waypoint.ClientWaypointCache;

public class CoreEventHandler {

    @SubscribeEvent
    public static void onWorldSave(WorldEvent.Save event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            BLPCSaveHandler.INSTANCE.saveIfDirty();
            for (Party party : PartyProviderRegistry.get().getAllParties()) {
                party.cleanExpiredInvites();
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ChunkManagerData.getInstance().flushPending();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ChunkTransitHandler.onPlayerLogout(event.player.getUniqueID());

        if (event.player.world.isRemote) return;
        if (ModConfig.claims.allowOfflineChunkLoading) return;

        UUID playerId = event.player.getUniqueID();
        Party party = PartyProviderRegistry.get().getEffectiveParty(playerId);
        if (party == null) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        // The logging-out player is still counted as online at this point, so check <= 1
        if (party.countOnlineMembers(server) > 1) return;

        Set<UUID> memberIds = new java.util.HashSet<>(party.getMemberUUIDs());
        for (ClaimedChunkData claim : ChunkManagerData.getInstance().getAllClaims()) {
            if (claim.isForceLoaded && memberIds.contains(claim.ownerUUID)) {
                for (WorldServer ws : server.worlds) {
                    if (ws.provider.getDimension() == claim.dim) {
                        TicketManager.unforceChunk(ws, claim.x, claim.z);
                    }
                }
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler {

        @SubscribeEvent
        public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            // Forge posts this from the Netty I/O thread, not the client main thread — every
            // other handler in this codebase (see MainThreadMessageHandler) hops onto the main
            // thread before touching ClientClaimCache/ClientPartyCache, which are plain
            // non-thread-safe collections.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                // Load before registering listeners so the load itself doesn't trigger a
                // redundant save.
                ClientCachePersistence.loadForCurrentServer();
                ClientCachePersistence.register();
            });
        }

        @SubscribeEvent
        public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            // Same off-main-thread caveat as onClientConnect above (abnormal disconnects such as
            // a timeout can fire this from the Netty thread too).
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientCachePersistence.saveNow();
                // clearAll() below already drops every registered listener (including these),
                // but unregister explicitly so this stays correct even if that changes.
                ClientCachePersistence.unregister();
                ClientClaimCache.clearAll();
                ClientPartyCache.clearAll();
                ClientWaypointCache.clearAll();
            });
        }
    }
}
