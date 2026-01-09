package com.sysnote8.bquclaim.network;

import com.sysnote8.bquclaim.chunk.ClientCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageSyncAllClaims implements IMessage {
    private NBTTagCompound data;

    public MessageSyncAllClaims() {}

    public MessageSyncAllClaims(NBTTagCompound data) {
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.data);
    }

    public static class Handler implements IMessageHandler<MessageSyncAllClaims, IMessage> {
        @Override
        public IMessage onMessage(MessageSyncAllClaims message, MessageContext ctx) {
            // クライアント側で受信
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientCache.clear(); // 一度リセット
                NBTTagCompound nbt = message.data;
                for (String key : nbt.getKeySet()) {
                    NBTTagCompound tag = nbt.getCompoundTag(key);
                    // ClientCacheに復元
                    ClientCache.update(
                            tag.getInteger("x"),
                            tag.getInteger("z"),
                            tag.getUniqueId("owner"),
                            tag.getString("name"),
                            tag.getBoolean("force")
                    );
                    // 強制ロード状態も同期する場合はここに追加
                    // ClientCache.setForceLoad(key, tag.getBoolean("force"));
                }
            });
            return null;
        }
    }
}
