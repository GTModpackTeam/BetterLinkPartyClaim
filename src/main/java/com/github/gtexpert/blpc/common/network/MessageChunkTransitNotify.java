package com.github.gtexpert.blpc.common.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.gtexpert.blpc.client.gui.widget.BLPCToast;
import com.github.gtexpert.blpc.common.party.RelationType;

import io.netty.buffer.ByteBuf;

/**
 * S→C packet: notify the client that a player entered/left a claimed chunk.
 */
public class MessageChunkTransitNotify implements IMessage {

    private String playerName;
    private String relationName;
    private boolean entered;

    public MessageChunkTransitNotify() {}

    public MessageChunkTransitNotify(String playerName, RelationType relation, boolean entered) {
        this.playerName = playerName;
        this.relationName = relation.name();
        this.entered = entered;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        relationName = ByteBufUtils.readUTF8String(buf);
        entered = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, playerName);
        ByteBufUtils.writeUTF8String(buf, relationName);
        buf.writeBoolean(entered);
    }

    private static RelationType parseRelation(String name) {
        try {
            return RelationType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return RelationType.NONE;
        }
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<MessageChunkTransitNotify, IMessage> {

        @Override
        public IMessage onMessage(MessageChunkTransitNotify msg, MessageContext ctx) {
            final RelationType rel = parseRelation(msg.relationName);
            Minecraft.getMinecraft().addScheduledTask(() -> {
                BLPCToast toast = BLPCToast.builder()
                        .fromTransit(rel, msg.entered, msg.playerName)
                        .build();
                Minecraft.getMinecraft().getToastGui().add(toast);
            });
            return null;
        }
    }
}
