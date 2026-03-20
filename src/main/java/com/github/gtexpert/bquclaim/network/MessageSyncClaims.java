package com.github.gtexpert.bquclaim.network;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.bquclaim.chunk.ClientCache;

import io.netty.buffer.ByteBuf;

public class MessageSyncClaims implements IMessage {

    private int x, z;
    private UUID owner;
    private String name;
    private boolean isForceLoaded;

    public MessageSyncClaims() {} // 必須

    public MessageSyncClaims(int x, int z, UUID owner, String name, boolean isForceLoaded) {
        this.x = x;
        this.z = z;
        this.owner = owner;
        this.name = name;
        this.isForceLoaded = isForceLoaded;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.z = buf.readInt();
        // ownerがnull（Unclaim）の場合はUUIDを飛ばすための判定
        if (buf.readBoolean()) {
            this.owner = new UUID(buf.readLong(), buf.readLong());
            this.name = ByteBufUtils.readUTF8String(buf);
            // Read force flag if present
            this.isForceLoaded = buf.readBoolean();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(z);
        boolean hasOwner = owner != null;
        buf.writeBoolean(hasOwner);
        if (hasOwner) {
            buf.writeLong(owner.getMostSignificantBits());
            buf.writeLong(owner.getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, name);
            // Write force flag so clients can render force-loaded state
            buf.writeBoolean(isForceLoaded);
        }
    }

    public static class Handler implements IMessageHandler<MessageSyncClaims, IMessage> {

        @Override
        public IMessage onMessage(MessageSyncClaims message, MessageContext ctx) {
            // クライアント側で実行
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientCache.update(message.x, message.z, message.owner, message.name, message.isForceLoaded);
            });
            return null;
        }
    }
}
