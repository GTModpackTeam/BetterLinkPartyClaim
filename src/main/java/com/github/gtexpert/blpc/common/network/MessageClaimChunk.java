package com.github.gtexpert.blpc.common.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.TicketManager;

import io.netty.buffer.ByteBuf;

/** C→S: Request to claim/unclaim/force-load a chunk. */
public class MessageClaimChunk implements IMessage {

    public static final int MODE_CLAIM = 0;
    public static final int MODE_UNCLAIM = 1;
    public static final int MODE_TOGGLE_FORCE = 2;

    private static final int MAX_CHUNK_DISTANCE = 64;

    private int x;
    private int z;
    private int mode;

    public MessageClaimChunk() {}

    public MessageClaimChunk(int x, int z, int mode) {
        this.x = x;
        this.z = z;
        this.mode = mode;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.z = buf.readInt();
        this.mode = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.z);
        buf.writeInt(this.mode);
    }

    public static class Handler implements IMessageHandler<MessageClaimChunk, IMessage> {

        @Override
        public IMessage onMessage(MessageClaimChunk message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;

                // Validate chunk coordinates - must be within reasonable distance
                int playerChunkX = MathHelper.floor(player.posX) >> 4;
                int playerChunkZ = MathHelper.floor(player.posZ) >> 4;
                if (Math.abs(message.x - playerChunkX) > MAX_CHUNK_DISTANCE ||
                        Math.abs(message.z - playerChunkZ) > MAX_CHUNK_DISTANCE) {
                    return;
                }

                ChunkManagerData data = ChunkManagerData.getInstance();
                ClaimedChunkData existing = data.getClaim(message.x, message.z);
                UUID playerId = player.getUniqueID();

                switch (message.mode) {
                    case MODE_CLAIM -> handleClaim(message, player, data, existing, playerId);
                    case MODE_UNCLAIM -> handleUnclaim(message, player, data, existing, playerId);
                    case MODE_TOGGLE_FORCE -> handleToggleForce(message, player, data, existing, playerId);
                }
            });
            return null;
        }

        private void handleClaim(MessageClaimChunk msg, EntityPlayerMP player,
                                 ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing != null) return;
            if (data.countClaims(playerId) >= ModConfig.claims.maxClaimsPerPlayer) {
                ModNetwork.INSTANCE.sendTo(
                        new MessageClaimFailed("CLAIM_LIMIT", data.countClaims(playerId),
                                ModConfig.claims.maxClaimsPerPlayer),
                        player);
                return;
            }

            String partyName = resolveTeamName(playerId);
            data.setClaim(msg.x, msg.z, playerId, player.getName(), partyName, false);
            syncToAll(msg.x, msg.z, playerId, player.getName(), partyName, false);
            BLPCSaveHandler.INSTANCE.markDirty();
        }

        private void handleUnclaim(MessageClaimChunk msg, EntityPlayerMP player,
                                   ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing == null) return;
            if (!isOwnerOrOp(existing, player, playerId)) return;

            if (existing.isForceLoaded) {
                TicketManager.unforceChunk(player.world, msg.x, msg.z);
            }
            data.setClaim(msg.x, msg.z, null, "", "", false);
            syncToAll(msg.x, msg.z, null, "", "", false);
            BLPCSaveHandler.INSTANCE.markDirty();
        }

        private void handleToggleForce(MessageClaimChunk msg, EntityPlayerMP player,
                                       ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing == null) {
                // Claim + Force
                if (data.countClaims(playerId) >= ModConfig.claims.maxClaimsPerPlayer) {
                    ModNetwork.INSTANCE.sendTo(
                            new MessageClaimFailed("CLAIM_LIMIT", data.countClaims(playerId),
                                    ModConfig.claims.maxClaimsPerPlayer),
                            player);
                    return;
                }
                if (data.countForceLoads(playerId) >= ModConfig.claims.maxForceLoadsPerPlayer) {
                    ModNetwork.INSTANCE.sendTo(
                            new MessageClaimFailed("FORCELOAD_LIMIT", data.countForceLoads(playerId),
                                    ModConfig.claims.maxForceLoadsPerPlayer),
                            player);
                    return;
                }

                String partyName = resolveTeamName(playerId);
                boolean forced = TicketManager.forceChunk(player.world, msg.x, msg.z, null);
                data.setClaim(msg.x, msg.z, playerId, player.getName(), partyName, forced);
                syncToAll(msg.x, msg.z, playerId, player.getName(), partyName, forced);
                BLPCSaveHandler.INSTANCE.markDirty();
            } else if (isOwnerOrOp(existing, player, playerId)) {
                toggleForceLoad(msg, player, data, existing, playerId);
            }
        }

        private void toggleForceLoad(MessageClaimChunk msg, EntityPlayerMP player,
                                     ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing.isForceLoaded) {
                existing.isForceLoaded = false;
                TicketManager.unforceChunk(player.world, msg.x, msg.z);
            } else {
                if (data.countForceLoads(playerId) >= ModConfig.claims.maxForceLoadsPerPlayer) {
                    ModNetwork.INSTANCE.sendTo(
                            new MessageClaimFailed("FORCELOAD_LIMIT", data.countForceLoads(playerId),
                                    ModConfig.claims.maxForceLoadsPerPlayer),
                            player);
                    return;
                }
                boolean forced = TicketManager.forceChunk(player.world, msg.x, msg.z, null);
                if (!forced) return;  // Ticket acquisition failed, don't update state
                existing.isForceLoaded = true;
            }
            syncToAll(msg.x, msg.z, existing.ownerUUID, existing.ownerName, existing.partyName,
                    existing.isForceLoaded);
            BLPCSaveHandler.INSTANCE.markDirty();
        }

        private boolean isOwnerOrOp(ClaimedChunkData claim, EntityPlayerMP player, UUID playerId) {
            return claim.ownerUUID.equals(playerId) || player.canUseCommand(2, "");
        }

        private String resolveTeamName(UUID playerId) {
            String name = PartyProviderRegistry.get().getPartyName(playerId);
            return name != null ? name : "";
        }

        private void syncToAll(int x, int z, UUID owner, String name, String partyName, boolean forceLoaded) {
            ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(x, z, owner, name, partyName, forceLoaded));
        }
    }
}
