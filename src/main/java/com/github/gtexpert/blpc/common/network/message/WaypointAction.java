package com.github.gtexpert.blpc.common.network.message;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.waypoint.PartyWaypointData;
import com.github.gtexpert.blpc.common.waypoint.WaypointManagerData;

import io.netty.buffer.ByteBuf;

/**
 * C→S: Add/update or remove a party-shared waypoint. Sent by the JourneyMap integration when
 * {@code WaypointStoreMixin} detects the local player added, edited, or deleted a waypoint on
 * JourneyMap's own screen. The server re-broadcasts the change to every other online member of
 * the sender's party via {@code WaypointSync}.
 * <p>
 * <b>Authorization:</b> only the party {@link PartyRole#OWNER} may mutate shared waypoints —
 * every other member can view them but a mutation attempt is rejected and rolled back (the
 * server sends the actor a corrective {@code WaypointSync} reflecting the waypoint's actual
 * current state, undoing their local JourneyMap edit/delete).
 */
public class WaypointAction implements IMessage {

    public static final int ACTION_ADD_OR_UPDATE = 0;
    public static final int ACTION_REMOVE = 1;

    private int action;
    private String waypointId;
    private String name;
    private int dimension;
    private int x, y, z;
    private int color;

    public WaypointAction() {}

    public static WaypointAction addOrUpdate(String waypointId, String name, int dimension, int x, int y, int z,
                                             int color) {
        WaypointAction msg = new WaypointAction();
        msg.action = ACTION_ADD_OR_UPDATE;
        msg.waypointId = waypointId;
        msg.name = name;
        msg.dimension = dimension;
        msg.x = x;
        msg.y = y;
        msg.z = z;
        msg.color = color;
        return msg;
    }

    public static WaypointAction remove(String waypointId) {
        WaypointAction msg = new WaypointAction();
        msg.action = ACTION_REMOVE;
        msg.waypointId = waypointId;
        return msg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        waypointId = ByteBufUtils.readUTF8String(buf);
        if (action == ACTION_ADD_OR_UPDATE) {
            name = ByteBufUtils.readUTF8String(buf);
            dimension = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            color = buf.readInt();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, waypointId);
        if (action == ACTION_ADD_OR_UPDATE) {
            ByteBufUtils.writeUTF8String(buf, name);
            buf.writeInt(dimension);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(color);
        }
    }

    public static class Handler implements IMessageHandler<WaypointAction, IMessage> {

        private static final DefaultPartyProvider SELF_PROVIDER = new DefaultPartyProvider();

        /** Matches the PartyWidgets party-name input cap; a shared waypoint name is user-facing text, not data. */
        private static final int MAX_NAME_LENGTH = 32;
        /** JourneyMap's own waypoint id is short (name/coords-derived); this only bounds a hostile client. */
        private static final int MAX_WAYPOINT_ID_LENGTH = 128;
        /** Backstop against a single (self-appointed OWNER of a solo party) client growing this file/map unbounded. */
        private static final int MAX_WAYPOINTS_PER_PARTY = 200;

        @Override
        public IMessage onMessage(WaypointAction message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                if (message.waypointId == null || message.waypointId.isEmpty() ||
                        message.waypointId.length() > MAX_WAYPOINT_ID_LENGTH) {
                    return;
                }

                EntityPlayerMP player = ctx.getServerHandler().player;
                UUID playerId = player.getUniqueID();

                // Re-derived per request — see PartyAction.Handler#dispatch for why isLinkedParty
                // (a live check) is used instead of a per-player flag.
                IPartyProvider provider = PartyProviderRegistry.get();
                boolean playerBQuLinked = provider.isLinkedParty(playerId);
                IPartyProvider activeProvider = playerBQuLinked ? provider : SELF_PROVIDER;

                // getPartyId(), not PartyManagerData#getPartyByPlayer() — a BQu-linked member who
                // joined entirely through BQu's own UI may have no BLPC-side Party record at all,
                // and that record's id isn't guaranteed identical across members anyway (see
                // BQuPartyProvider#serializeForClient). getPartyId() is.
                UUID partyId = activeProvider.getPartyId(playerId);
                if (partyId == null) return;

                WaypointManagerData data = WaypointManagerData.getInstance();

                if (!isOwner(playerId, activeProvider)) {
                    rollback(data, partyId, message.waypointId, player);
                    return;
                }

                switch (message.action) {
                    case ACTION_ADD_OR_UPDATE -> {
                        if (message.name == null || message.name.isEmpty() || message.name.length() > MAX_NAME_LENGTH) {
                            return;
                        }
                        if (data.countWaypoints(partyId) >= MAX_WAYPOINTS_PER_PARTY &&
                                data.getWaypoint(partyId, message.waypointId) == null) {
                            rollback(data, partyId, message.waypointId, player);
                            return;
                        }
                        PartyWaypointData waypoint = new PartyWaypointData(message.waypointId, playerId, message.name,
                                message.dimension, message.x, message.y, message.z, message.color);
                        data.setWaypoint(partyId, waypoint);
                        broadcast(activeProvider, playerId,
                                WaypointSync.addOrUpdate(waypoint.waypointId, waypoint.ownerUUID, waypoint.name,
                                        waypoint.dimension, waypoint.x, waypoint.y, waypoint.z, waypoint.color));
                    }
                    case ACTION_REMOVE -> {
                        data.removeWaypoint(partyId, message.waypointId);
                        broadcast(activeProvider, playerId, WaypointSync.remove(message.waypointId));
                    }
                    default -> ModLog.SYNC.warn("Unknown WaypointAction.action {} from {}", message.action, playerId);
                }
                BLPCSaveHandler.INSTANCE.markDirty();
            });
            return null;
        }

        /**
         * Delegates entirely to {@code activeProvider}, never a directly-fetched BLPC-side
         * {@link Party}: when BQu-linked, BLPC's mirrored {@code Party} only ever pushes roles
         * into BQu (see {@code ensureNativePartyWithMembers}) and never pulls them back, so it
         * can silently keep reporting a stale OWNER after ownership changes through BQu's own
         * screen. {@code activeProvider} is already resolved to the real source of truth (BQu or
         * self-managed) by the caller, so a single {@code getRole} call is correct either way.
         */
        private static boolean isOwner(UUID playerId, IPartyProvider activeProvider) {
            return PartyRole.fromName(activeProvider.getRole(playerId)) == PartyRole.OWNER;
        }

        /**
         * Non-owner attempted a mutation: undo their local JourneyMap edit/delete by sending back
         * the waypoint's actual current state (or a removal, if it was never shared in the first
         * place).
         */
        private void rollback(WaypointManagerData data, UUID partyId, String waypointId, EntityPlayerMP actor) {
            PartyWaypointData waypoint = data.getWaypoint(partyId, waypointId);
            if (waypoint != null) {
                ModNetwork.INSTANCE.sendTo(
                        WaypointSync.addOrUpdate(waypoint.waypointId, waypoint.ownerUUID, waypoint.name,
                                waypoint.dimension, waypoint.x, waypoint.y, waypoint.z, waypoint.color),
                        actor);
                return;
            }
            ModNetwork.INSTANCE.sendTo(WaypointSync.remove(waypointId), actor);
        }

        /**
         * Sends the sync to every other online party member, resolved from the same
         * {@code activeProvider} used for authorization — not the possibly-stale BLPC
         * {@link Party#getMemberUUIDs()} — so a BQu-linked party's real current membership is used.
         */
        private void broadcast(IPartyProvider activeProvider, UUID actorId, WaypointSync sync) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            ModNetwork.broadcastToOtherMembers(activeProvider, actorId, server, sync);
        }
    }
}
