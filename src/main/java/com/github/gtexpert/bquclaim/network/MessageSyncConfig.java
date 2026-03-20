package com.github.gtexpert.bquclaim.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.bquclaim.ModConfig;

import io.netty.buffer.ByteBuf;

public class MessageSyncConfig implements IMessage {

    private int maxClaims;
    private int maxForce;

    public MessageSyncConfig() {}

    public MessageSyncConfig(int maxClaims, int maxForce) {
        this.maxClaims = maxClaims;
        this.maxForce = maxForce;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.maxClaims = buf.readInt();
        this.maxForce = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.maxClaims);
        buf.writeInt(this.maxForce);
    }

    public static class Handler implements IMessageHandler<MessageSyncConfig, IMessage> {

        @Override
        public IMessage onMessage(MessageSyncConfig message, MessageContext ctx) {
            // クライアント側のメモリ上の設定値をサーバーの値で上書き
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ModConfig.maxClaimsPerPlayer = message.maxClaims;
                ModConfig.maxForceLoadsPerPlayer = message.maxForce;
            });
            return null;
        }
    }
}
