package com.github.gtexpert.blpc.common.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.common.ModConfig;

import io.netty.buffer.ByteBuf;

/** S→C: Syncs server config (max claims/force-loads) to client. */
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
            // Override client-side config values with server values
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ModConfig.claims.maxClaimsPerPlayer = message.maxClaims;
                ModConfig.claims.maxForceLoadsPerPlayer = message.maxForce;
            });
            return null;
        }
    }
}
