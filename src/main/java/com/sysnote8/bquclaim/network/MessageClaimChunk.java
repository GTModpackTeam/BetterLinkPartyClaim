package com.sysnote8.bquclaim.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.sysnote8.bquclaim.chunk.ChunkManagerData;

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

                if (message.mode == 0) {
                    // Claim処理
                    data.setClaim(message.x, message.z, player.getUniqueID(), player.getName());
                    player.sendMessage(
                            new TextComponentString("Chunk [" + message.x + ", " + message.z + "] claimed!"));
                } else {
                    // Unclaim処理
                    data.setClaim(message.x, message.z, null, "");
                    player.sendMessage(
                            new TextComponentString("Chunk [" + message.x + ", " + message.z + "] unclaimed!"));
                }

                // 更新を全員（または周辺のプレイヤー）に同期
                // ※nullを送ることでクライアント側のClientCacheからも削除される
                ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(
                        message.x,
                        message.z,
                        message.mode == 0 ? player.getUniqueID() : null,
                        player.getName()));
            });
            return null;
        }
    }
}
