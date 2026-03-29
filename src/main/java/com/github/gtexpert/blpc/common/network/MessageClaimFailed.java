package com.github.gtexpert.blpc.common.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.gtexpert.blpc.client.gui.widget.BLPCToast;

import io.netty.buffer.ByteBuf;

/**
 * S→C packet: notify the client that a claim or force-load attempt failed due to a limit.
 */
public class MessageClaimFailed implements IMessage {

    private String reason;
    private int current;
    private int max;

    public MessageClaimFailed() {}

    public MessageClaimFailed(String reason, int current, int max) {
        this.reason = reason;
        this.current = current;
        this.max = max;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        reason = ByteBufUtils.readUTF8String(buf);
        current = buf.readInt();
        max = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, reason);
        buf.writeInt(current);
        buf.writeInt(max);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<MessageClaimFailed, IMessage> {

        @Override
        public IMessage onMessage(MessageClaimFailed msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                BLPCToast toast = BLPCToast.builder()
                        .fromClaimFailed(msg.reason, msg.current, msg.max)
                        .build();
                Minecraft.getMinecraft().getToastGui().add(toast);
            });
            return null;
        }
    }
}
