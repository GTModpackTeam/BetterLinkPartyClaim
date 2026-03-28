package com.github.gtexpert.blpc.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.ClientCache;

import io.netty.buffer.ByteBuf;

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
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientCache.clear();
                for (String key : message.data.getKeySet()) {
                    ClaimedChunkData d = ClaimedChunkData.fromNBT(message.data.getCompoundTag(key));
                    if (d == null) continue;
                    ClientCache.update(d.x, d.z, d.ownerUUID, d.ownerName, d.partyName, d.isForceLoaded);
                }
            });
            return null;
        }
    }
}
