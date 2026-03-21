package com.github.gtexpert.blpc.common.network;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.common.chunk.ClientCache;

import io.netty.buffer.ByteBuf;

public class MessageSyncClaims implements IMessage {

    private int x, z;
    private UUID owner;
    private String name;
    private String partyName;
    private boolean isForceLoaded;

    public MessageSyncClaims() {} // 必須

    public MessageSyncClaims(int x, int z, UUID owner, String name, String partyName, boolean isForceLoaded) {
        this.x = x;
        this.z = z;
        this.owner = owner;
        this.name = name;
        this.partyName = partyName;
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
            this.partyName = ByteBufUtils.readUTF8String(buf);
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
            ByteBufUtils.writeUTF8String(buf, partyName);
            buf.writeBoolean(isForceLoaded);
        }
    }

    public static class Handler implements IMessageHandler<MessageSyncClaims, IMessage> {

        @Override
        public IMessage onMessage(MessageSyncClaims message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientCache.update(message.x, message.z, message.owner, message.name, message.partyName,
                        message.isForceLoaded);
            });
            return null;
        }
    }
}
