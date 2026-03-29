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
 * S→C packet: notify the client of a party event (join, leave, kick, disband, etc.).
 */
public class MessagePartyEventNotify implements IMessage {

    private String eventType;
    private String playerName;
    private String extraInfo;

    public MessagePartyEventNotify() {}

    public MessagePartyEventNotify(String eventType, String playerName, String extraInfo) {
        this.eventType = eventType;
        this.playerName = playerName;
        this.extraInfo = extraInfo != null ? extraInfo : "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        eventType = ByteBufUtils.readUTF8String(buf);
        playerName = ByteBufUtils.readUTF8String(buf);
        extraInfo = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, eventType);
        ByteBufUtils.writeUTF8String(buf, playerName);
        ByteBufUtils.writeUTF8String(buf, extraInfo);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<MessagePartyEventNotify, IMessage> {

        @Override
        public IMessage onMessage(MessagePartyEventNotify msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                BLPCToast toast = BLPCToast.builder()
                        .fromPartyEvent(msg.eventType, msg.playerName, msg.extraInfo)
                        .build();
                Minecraft.getMinecraft().getToastGui().add(toast);
            });
            return null;
        }
    }
}
