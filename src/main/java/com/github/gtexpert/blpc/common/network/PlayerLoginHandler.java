package com.github.gtexpert.blpc.common.network;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.event.PartyEvent;
import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.TicketManager;
import com.github.gtexpert.blpc.common.network.message.PartySync;
import com.github.gtexpert.blpc.common.network.message.SyncAllClaims;
import com.github.gtexpert.blpc.common.network.message.SyncAllWaypoints;
import com.github.gtexpert.blpc.common.network.message.SyncConfig;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.waypoint.PartyWaypointData;
import com.github.gtexpert.blpc.common.waypoint.WaypointManagerData;

/** Sends initial sync packets (claims, config, parties) to newly connected players. */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public class PlayerLoginHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote) return;

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        // Merge offline UUID -> online UUID if configured
        if (ModConfig.data.mergeOfflineOnlineData) {
            UUID onlineUUID = player.getUniqueID();
            UUID offlineUUID = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + player.getName()).getBytes(StandardCharsets.UTF_8));
            if (!onlineUUID.equals(offlineUUID)) {
                PartyManagerData pmData = PartyManagerData.getInstance();
                Party oldParty = pmData.getPartyByPlayer(offlineUUID);
                if (oldParty != null && pmData.getPartyByPlayer(onlineUUID) == null) {
                    PartyRole role = oldParty.getRole(offlineUUID);
                    if (role == null) role = PartyRole.MEMBER;
                    pmData.removeMember(oldParty.getPartyId(), offlineUUID);
                    pmData.addMember(oldParty.getPartyId(), onlineUUID, role);

                    ChunkManagerData chunkData = ChunkManagerData.getInstance();
                    chunkData.transferOwnership(offlineUUID, onlineUUID);

                    BLPCSaveHandler.INSTANCE.markDirty();
                    ModLog.MIGRATION.info("Merged offline UUID {} -> online UUID {} for player {}",
                            offlineUUID, onlineUUID, player.getName());
                    PartyProviderRegistry.get().syncToAll();
                }
            }
        }

        var pmData = PartyManagerData.getInstance();
        IPartyProvider activeProvider = PartyProviderRegistry.get();

        // Auto-join server party if configured
        if (!activeProvider.hasNativeParty(player.getUniqueID()) && ModConfig.serverParty.enabled) {
            Party serverParty = activeProvider.findByName(ModConfig.serverParty.name);
            if (serverParty != null && serverParty.isFreeToJoin()) {
                pmData.addMember(serverParty.getPartyId(), player.getUniqueID(), PartyRole.MEMBER);
                ModLog.PARTY.info("Auto-joined player {} to server party \"{}\" on login",
                        player.getName(), ModConfig.serverParty.name);
                BLPCSaveHandler.INSTANCE.markDirty();
                activeProvider.syncToAll();
            }
        }

        // Auto-create party if configured
        if (!activeProvider.hasNativeParty(player.getUniqueID())) {
            boolean isSingleplayer = player.getServer() != null && player.getServer().isSinglePlayer();
            boolean shouldCreate = isSingleplayer && ModConfig.party.autoCreatePartySingleplayer;
            if (shouldCreate) {
                if (activeProvider.createParty(player, player.getName())) {
                    activeProvider.syncToAll();
                }
                BLPCSaveHandler.INSTANCE.markDirty();
            }
        }

        // Re-force party chunks if this is the first member logging in after offline suppression
        if (!ModConfig.claims.allowOfflineChunkLoading) {
            Party party = activeProvider.getEffectiveParty(player.getUniqueID());
            if (party != null) {
                MinecraftServer server = player.getServer();
                if (server != null && party.countOnlineMembers(server) == 1) {
                    Set<UUID> memberIds = new HashSet<>(party.getMemberUUIDs());
                    for (ClaimedChunkData claim : ChunkManagerData.getInstance().getAllClaims()) {
                        if (claim.isForceLoaded && memberIds.contains(claim.ownerUUID)) {
                            for (WorldServer ws : server.worlds) {
                                if (ws.provider.getDimension() == claim.dim) {
                                    TicketManager.forceChunk(ws, claim.x, claim.z, null);
                                }
                            }
                        }
                    }
                }
            }
        }

        ChunkManagerData data = ChunkManagerData.getInstance();

        ModNetwork.INSTANCE.sendTo(new SyncAllClaims(data.serializeAll()), player);
        ModNetwork.INSTANCE.sendTo(
                new SyncConfig(ModConfig.claims.maxClaimsPerPlayer, ModConfig.claims.maxForceLoadsPerPlayer),
                player);

        ModNetwork.INSTANCE.sendTo(
                new PartySync(PartyProviderRegistry.get().serializeForClient()), player);

        // getPartyId(), not PartyManagerData#getPartyByPlayer() — see WaypointAction.Handler's
        // javadoc: a BQu-linked player who joined entirely through BQu's own UI may have no
        // BLPC-side Party record, and getPartyId() is the only id guaranteed stable across members.
        sendWaypointSync(player, activeProvider.getPartyId(player.getUniqueID()));
    }

    /** Mid-session join: sync the party's shared waypoints without waiting for a relog. */
    @SubscribeEvent
    public static void onMemberJoined(PartyEvent.Post.MemberJoined event) {
        EntityPlayerMP member = onlinePlayer(event.getMemberUUID());
        if (member == null) return;
        sendWaypointSync(member, PartyProviderRegistry.get().getPartyId(event.getMemberUUID()));
    }

    /** Left/kicked: clear the party's shared waypoints (null id sends an empty list). */
    @SubscribeEvent
    public static void onMemberLeft(PartyEvent.Post.MemberLeft event) {
        EntityPlayerMP member = onlinePlayer(event.getMemberUUID());
        if (member == null) return;
        sendWaypointSync(member, null);
    }

    /** Disbanded: every former member drops the now-orphaned shared waypoints. */
    @SubscribeEvent
    public static void onPartyDisbanded(PartyEvent.Post.Disbanded event) {
        for (UUID memberId : event.getMemberUUIDs()) {
            EntityPlayerMP member = onlinePlayer(memberId);
            if (member != null) sendWaypointSync(member, null);
        }
    }

    private static EntityPlayerMP onlinePlayer(UUID playerId) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return null;
        return server.getPlayerList().getPlayerByUUID(playerId);
    }

    /** Sends {@code player} the full shared-waypoint list for {@code partyId} (empty if null). */
    private static void sendWaypointSync(EntityPlayerMP player, UUID partyId) {
        NBTTagCompound waypointsData = new NBTTagCompound();
        NBTTagList waypointsList = new NBTTagList();
        if (partyId != null) {
            for (PartyWaypointData waypoint : WaypointManagerData.getInstance().getWaypoints(partyId)) {
                waypointsList.appendTag(waypoint.toNBT());
            }
        }
        waypointsData.setTag("waypoints", waypointsList);
        ModNetwork.INSTANCE.sendTo(new SyncAllWaypoints(waypointsData), player);
    }
}
