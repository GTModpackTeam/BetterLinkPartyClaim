package com.sysnote8.bquclaim.network;

import com.sysnote8.bquclaim.chunk.ChunkManagerData;
import com.sysnote8.bquclaim.chunk.ClaimedChunkData;
import com.sysnote8.bquclaim.chunk.TicketManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

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

                if (message.mode == 0 || message.mode == 2) {
                    // Claim処理
                    // Todo: If its already claimed, skip it.
                    data.setClaim(message.x, message.z, player.getUniqueID(), player.getName(), message.mode == 2);
                    player.sendMessage(
                            new TextComponentString("Chunk [" + message.x + ", " + message.z + "] claimed!"));
                } else {
                    // Unclaim処理
                    data.setClaim(message.x, message.z, null, "", false);
                    player.sendMessage(
                            new TextComponentString("Chunk [" + message.x + ", " + message.z + "] unclaimed!"));
                }

                // Handler内の処理
                if (message.mode == 2) {
                    ClaimedChunkData d = data.claims.get(message.x + "," + message.z);
                    if (d != null && d.ownerUUID.equals(player.getUniqueID())) {
                        // 強制ロードのON/OFFを切り替え
                        d.isForceLoaded = !d.isForceLoaded;

                        if (d.isForceLoaded) {
                            TicketManager.forceChunk(player.world, message.x, message.z, null);
                        } else {
                            TicketManager.unforceChunk(player.world, message.x, message.z);
                        }
                        data.markDirty();
                    }
                }

                // 更新を全員（または周辺のプレイヤー）に同期
                // ※nullを送ることでクライアント側のClientCacheからも削除される
                ModNetwork.INSTANCE.sendToAll(new MessageSyncClaims(
                        message.x,
                        message.z,
                        message.mode != 1 ? player.getUniqueID() : null,
                        player.getName(),
                        message.mode == 2));
            });
            return null;
        }
    }
}
