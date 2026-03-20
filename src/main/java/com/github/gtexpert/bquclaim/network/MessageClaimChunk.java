package com.github.gtexpert.bquclaim.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.bquclaim.ModConfig;
import com.github.gtexpert.bquclaim.chunk.ChunkManagerData;
import com.github.gtexpert.bquclaim.chunk.ClaimedChunkData;
import com.github.gtexpert.bquclaim.chunk.TicketManager;

import io.netty.buffer.ByteBuf;

public class MessageClaimChunk implements IMessage {

    private int x;
    private int z;
    private int mode; // 0: Claim, 1: Unclaim

    // Forgeのパケットには必須の空コンストラクタ
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

    // サーバー側での処理担当
    public static class Handler implements IMessageHandler<MessageClaimChunk, IMessage> {

        @Override
        public IMessage onMessage(MessageClaimChunk message, MessageContext ctx) {
            // サーバーのスレッドで安全に実行
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                ChunkManagerData data = ChunkManagerData.get(player.world);

                String key = message.x + "," + message.z;
                ClaimedChunkData existing = data.claims.get(key);

                // Helper lambdas
                java.util.function.Supplier<Integer> countMyClaims = () -> {
                    int c = 0;
                    for (ClaimedChunkData d : data.claims.values()) {
                        if (d.ownerUUID.equals(player.getUniqueID())) c++;
                    }
                    return c;
                };

                java.util.function.Supplier<Integer> countMyForceLoads = () -> {
                    int c = 0;
                    for (ClaimedChunkData d : data.claims.values()) {
                        if (d.ownerUUID.equals(player.getUniqueID()) && d.isForceLoaded) c++;
                    }
                    return c;
                };

                if (message.mode == 0) { // Claim
                    if (existing != null) {
                        if (existing.ownerUUID.equals(player.getUniqueID())) {
                            player.sendMessage(new TextComponentString("You already own this chunk."));
                        } else {
                            player.sendMessage(new TextComponentString("Chunk already claimed by someone else."));
                        }
                    } else {
                        if (countMyClaims.get() >= ModConfig.maxClaimsPerPlayer) {
                            player.sendMessage(new TextComponentString("You have reached the claim limit."));
                        } else {
                            data.setClaim(message.x, message.z, player.getUniqueID(), player.getName(), false);
                            player.sendMessage(
                                    new TextComponentString("Chunk [" + message.x + ", " + message.z + "] claimed!"));
                            ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(message.x, message.z,
                                    player.getUniqueID(), player.getName(), false));
                        }
                    }
                } else if (message.mode == 1) { // Unclaim
                    if (existing == null) {
                        player.sendMessage(new TextComponentString("Chunk is not claimed."));
                    } else if (!existing.ownerUUID.equals(player.getUniqueID()) && !player.canUseCommand(2, "")) {
                        // canUseCommand(2, "") is a basic OP check fallback
                        player.sendMessage(new TextComponentString("You are not the owner of this chunk."));
                    } else {
                        if (existing.isForceLoaded) {
                            TicketManager.unforceChunk(player.world, message.x, message.z);
                        }
                        data.setClaim(message.x, message.z, null, "", false);
                        player.sendMessage(
                                new TextComponentString("Chunk [" + message.x + ", " + message.z + "] unclaimed!"));
                        ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(message.x, message.z, null, "", false));
                    }
                } else if (message.mode == 2) { // Toggle Force / Claim+Force
                    if (existing == null) {
                        // Claim+Force
                        if (countMyClaims.get() >= ModConfig.maxClaimsPerPlayer) {
                            player.sendMessage(new TextComponentString("You have reached the claim limit."));
                        } else if (countMyForceLoads.get() >= ModConfig.maxForceLoadsPerPlayer) {
                            player.sendMessage(new TextComponentString("You have reached the force-load limit."));
                        } else {
                            data.setClaim(message.x, message.z, player.getUniqueID(), player.getName(), true);
                            TicketManager.forceChunk(player.world, message.x, message.z, null);
                            player.sendMessage(new TextComponentString(
                                    "Chunk [" + message.x + ", " + message.z + "] claimed & force-loaded!"));
                            ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(message.x, message.z,
                                    player.getUniqueID(), player.getName(), true));
                        }
                    } else {
                        if (!existing.ownerUUID.equals(player.getUniqueID()) && !player.canUseCommand(2, "")) {
                            player.sendMessage(new TextComponentString("You are not the owner of this chunk."));
                        } else {
                            // Toggle
                            boolean newState = !existing.isForceLoaded;
                            if (newState) { // enabling
                                if (countMyForceLoads.get() >= ModConfig.maxForceLoadsPerPlayer) {
                                    player.sendMessage(
                                            new TextComponentString("You have reached the force-load limit."));
                                } else {
                                    existing.isForceLoaded = true;
                                    TicketManager.forceChunk(player.world, message.x, message.z, null);
                                    player.sendMessage(new TextComponentString("Force-load enabled."));
                                    data.markDirty();
                                    ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(message.x, message.z,
                                            existing.ownerUUID, existing.ownerName, true));
                                }
                            } else { // disabling
                                existing.isForceLoaded = false;
                                TicketManager.unforceChunk(player.world, message.x, message.z);
                                player.sendMessage(new TextComponentString("Force-load disabled."));
                                data.markDirty();
                                ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(message.x, message.z,
                                        existing.ownerUUID, existing.ownerName, false));
                            }
                        }
                    }
                }
            });
            return null;
        }
    }
}
