package com.github.gtexpert.blpc.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.BLPCMod;
import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

import io.netty.buffer.ByteBuf;

public class MessagePartyAction implements IMessage {

    public static final int ACTION_CREATE = 0;
    public static final int ACTION_DISBAND = 1;
    public static final int ACTION_RENAME = 2;
    public static final int ACTION_INVITE = 3;
    public static final int ACTION_ACCEPT_INVITE = 4;
    public static final int ACTION_KICK_OR_LEAVE = 5;
    public static final int ACTION_CHANGE_ROLE = 6;
    public static final int ACTION_TOGGLE_BQU_LINK = 7;

    private int action;
    private String stringArg;

    public MessagePartyAction() {}

    public MessagePartyAction(int action, String stringArg) {
        this.action = action;
        this.stringArg = stringArg;
    }

    public static MessagePartyAction create(String name) {
        return new MessagePartyAction(ACTION_CREATE, name);
    }

    public static MessagePartyAction disband() {
        return new MessagePartyAction(ACTION_DISBAND, "");
    }

    public static MessagePartyAction rename(String newName) {
        return new MessagePartyAction(ACTION_RENAME, newName);
    }

    public static MessagePartyAction invite(String username) {
        return new MessagePartyAction(ACTION_INVITE, username);
    }

    public static MessagePartyAction acceptInvite(int partyId) {
        return new MessagePartyAction(ACTION_ACCEPT_INVITE, String.valueOf(partyId));
    }

    public static MessagePartyAction kickOrLeave(String username) {
        return new MessagePartyAction(ACTION_KICK_OR_LEAVE, username);
    }

    public static MessagePartyAction changeRole(String usernameAndRole) {
        return new MessagePartyAction(ACTION_CHANGE_ROLE, usernameAndRole);
    }

    public static MessagePartyAction toggleBQuLink(boolean linked) {
        return new MessagePartyAction(ACTION_TOGGLE_BQU_LINK, linked ? "true" : "false");
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        stringArg = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, stringArg);
    }

    public static class Handler implements IMessageHandler<MessagePartyAction, IMessage> {

        @Override
        public IMessage onMessage(MessagePartyAction msg, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                IPartyProvider provider = PartyProviderRegistry.get();

                boolean success = false;
                switch (msg.action) {
                    case ACTION_CREATE:
                        String name = msg.stringArg.trim();
                        if (name.isEmpty()) name = "New Party";
                        success = provider.createParty(player, name);
                        break;
                    case ACTION_DISBAND:
                        success = provider.disbandParty(player);
                        break;
                    case ACTION_RENAME:
                        String newName = msg.stringArg.trim();
                        if (!newName.isEmpty()) {
                            success = provider.renameParty(player, newName);
                        }
                        break;
                    case ACTION_INVITE:
                        success = provider.invitePlayer(player, msg.stringArg);
                        break;
                    case ACTION_ACCEPT_INVITE:
                        try {
                            int partyId = Integer.parseInt(msg.stringArg);
                            success = provider.acceptInvite(player, partyId);
                        } catch (NumberFormatException ignored) {}
                        break;
                    case ACTION_KICK_OR_LEAVE:
                        success = provider.kickOrLeave(player, msg.stringArg);
                        break;
                    case ACTION_CHANGE_ROLE:
                        String[] parts = msg.stringArg.split(":", 2);
                        if (parts.length == 2) {
                            success = provider.changeRole(player, parts[0], parts[1]);
                        }
                        break;
                    case ACTION_TOGGLE_BQU_LINK:
                        boolean linked = "true".equals(msg.stringArg);
                        PartyManagerData pmData = PartyManagerData.getInstance();
                        if (linked) {
                            // Check if BQu party exists and if we are the owner
                            String bquRole = provider.getRole(player.getUniqueID());
                            if (bquRole != null && !"OWNER".equals(bquRole)) {
                                // Player is in a BQu party but not owner — cannot link
                                BLPCMod.LOGGER.info("Player {} cannot link BQu: not party owner (role={})",
                                        player.getName(), bquRole);
                                break;
                            }
                            pmData.setBQuLinked(player.getUniqueID(), true);
                            // If no BQu party exists, create one from self-mod data
                            Party selfParty = pmData.getPartyByPlayer(player.getUniqueID());
                            if (selfParty != null && provider.getPartyName(player.getUniqueID()) == null) {
                                provider.createParty(player, selfParty.getName());
                            }
                        } else {
                            pmData.setBQuLinked(player.getUniqueID(), false);
                        }
                        success = true;
                        break;
                }

                if (success) {
                    provider.syncToAll();
                }
            });
            return null;
        }
    }
}
