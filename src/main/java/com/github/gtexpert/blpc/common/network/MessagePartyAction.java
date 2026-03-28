package com.github.gtexpert.blpc.common.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.PartyRole;
import com.github.gtexpert.blpc.common.party.TrustAction;
import com.github.gtexpert.blpc.common.party.TrustLevel;

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
    public static final int ACTION_TOGGLE_FAKE_PLAYERS = 8;
    public static final int ACTION_TOGGLE_EXPLOSION_PROTECTION = 9;
    public static final int ACTION_ADD_ALLY = 10;
    public static final int ACTION_REMOVE_ALLY = 11;
    public static final int ACTION_ADD_ENEMY = 12;
    public static final int ACTION_REMOVE_ENEMY = 13;
    public static final int ACTION_TRANSFER_OWNERSHIP = 14;
    public static final int ACTION_SET_TRUST_LEVEL = 15;
    public static final int ACTION_SET_FAKEPLAYER_TRUST = 16;
    public static final int ACTION_SET_FREE_TO_JOIN = 17;
    public static final int ACTION_SET_COLOR = 18;
    public static final int ACTION_SET_TITLE = 19;
    public static final int ACTION_SET_DESCRIPTION = 20;
    public static final int ACTION_JOIN_FREE_PARTY = 21;

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

    public static MessagePartyAction toggleFakePlayers() {
        return new MessagePartyAction(ACTION_TOGGLE_FAKE_PLAYERS, "");
    }

    public static MessagePartyAction toggleExplosionProtection() {
        return new MessagePartyAction(ACTION_TOGGLE_EXPLOSION_PROTECTION, "");
    }

    public static MessagePartyAction addAlly(String username) {
        return new MessagePartyAction(ACTION_ADD_ALLY, username);
    }

    public static MessagePartyAction removeAlly(String username) {
        return new MessagePartyAction(ACTION_REMOVE_ALLY, username);
    }

    public static MessagePartyAction addEnemy(String username) {
        return new MessagePartyAction(ACTION_ADD_ENEMY, username);
    }

    public static MessagePartyAction removeEnemy(String username) {
        return new MessagePartyAction(ACTION_REMOVE_ENEMY, username);
    }

    public static MessagePartyAction transferOwnership(String username) {
        return new MessagePartyAction(ACTION_TRANSFER_OWNERSHIP, username);
    }

    public static MessagePartyAction setTrustLevel(String actionAndLevel) {
        return new MessagePartyAction(ACTION_SET_TRUST_LEVEL, actionAndLevel);
    }

    public static MessagePartyAction setFakePlayerTrust(String level) {
        return new MessagePartyAction(ACTION_SET_FAKEPLAYER_TRUST, level);
    }

    public static MessagePartyAction setFreeToJoin(boolean free) {
        return new MessagePartyAction(ACTION_SET_FREE_TO_JOIN, free ? "true" : "false");
    }

    public static MessagePartyAction setColor(int color) {
        return new MessagePartyAction(ACTION_SET_COLOR, Integer.toString(color));
    }

    public static MessagePartyAction setTitle(String title) {
        return new MessagePartyAction(ACTION_SET_TITLE, title);
    }

    public static MessagePartyAction setDescription(String desc) {
        return new MessagePartyAction(ACTION_SET_DESCRIPTION, desc);
    }

    public static MessagePartyAction joinFreeParty(int partyId) {
        return new MessagePartyAction(ACTION_JOIN_FREE_PARTY, String.valueOf(partyId));
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

    private static Party getOrCreateSelfParty(EntityPlayerMP player, IPartyProvider provider) {
        PartyManagerData pmData = PartyManagerData.getInstance();
        Party party = pmData.getPartyByPlayer(player.getUniqueID());
        if (party == null) {
            String partyName = provider.getPartyName(player.getUniqueID());
            if (partyName != null) {
                party = pmData.createParty(partyName, player.getUniqueID());
            }
        }
        return party;
    }

    public static class Handler implements IMessageHandler<MessagePartyAction, IMessage> {

        @Override
        public IMessage onMessage(MessagePartyAction msg, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                IPartyProvider provider = PartyProviderRegistry.get();
                // When not BQu-linked, use self-managed provider to avoid
                // accidentally creating/modifying BQu parties
                boolean playerBQuLinked = PartyManagerData.getInstance()
                        .isBQuLinked(player.getUniqueID());
                DefaultPartyProvider selfProvider = new DefaultPartyProvider();
                IPartyProvider activeProvider = playerBQuLinked ? provider : selfProvider;

                boolean success = false;
                switch (msg.action) {
                    case ACTION_CREATE: {
                        String name = msg.stringArg.trim();
                        if (name.isEmpty()) name = "New Party";
                        if (name.length() > 64) name = name.substring(0, 64);
                        success = selfProvider.createParty(player, name);
                        break;
                    }
                    case ACTION_DISBAND: {
                        // Ensure self-managed party exists (may need to create from BQu data)
                        getOrCreateSelfParty(player, provider);
                        PartyManagerData pmDisband = PartyManagerData.getInstance();
                        Party disbandParty = pmDisband.getPartyByPlayer(player.getUniqueID());
                        if (disbandParty != null) {
                            PartyRole disbandRole = disbandParty.getRole(player.getUniqueID());
                            if (disbandRole != PartyRole.OWNER && !player.canUseCommand(2, "")) {
                                break;
                            }
                        }
                        List<UUID> disbandMembers = disbandParty != null ?
                                new ArrayList<>(disbandParty.getMemberUUIDs()) : Collections.emptyList();
                        boolean disbanded = selfProvider.disbandParty(player);
                        if (disbanded) {
                            for (UUID memberId : disbandMembers) {
                                pmDisband.setBQuLinked(memberId, false);
                            }
                            success = true;
                        }
                        break;
                    }
                    case ACTION_RENAME: {
                        String newName = msg.stringArg.trim();
                        if (newName.length() > 64) newName = newName.substring(0, 64);
                        if (!newName.isEmpty()) {
                            success = activeProvider.renameParty(player, newName);
                        }
                        break;
                    }
                    case ACTION_INVITE:
                        success = activeProvider.invitePlayer(player, msg.stringArg);
                        break;
                    case ACTION_ACCEPT_INVITE:
                        try {
                            int partyId = Integer.parseInt(msg.stringArg);
                            success = activeProvider.acceptInvite(player, partyId);
                        } catch (NumberFormatException ignored) {}
                        break;
                    case ACTION_KICK_OR_LEAVE:
                        success = activeProvider.kickOrLeave(player, msg.stringArg);
                        break;
                    case ACTION_CHANGE_ROLE: {
                        String[] parts = msg.stringArg.split(":", 2);
                        if (parts.length == 2) {
                            success = activeProvider.changeRole(player, parts[0], parts[1]);
                        }
                        break;
                    }
                    case ACTION_TOGGLE_BQU_LINK: {
                        boolean linked = "true".equals(msg.stringArg);
                        PartyManagerData pmLink = PartyManagerData.getInstance();
                        Party linkParty = pmLink.getPartyByPlayer(player.getUniqueID());
                        // Permission check: if player has an existing party, they need ADMIN+ role
                        if (linkParty != null) {
                            PartyRole linkRole = linkParty.getRole(player.getUniqueID());
                            if (linkRole != null && !linkRole.canInvite() && !player.canUseCommand(2, "")) {
                                break;
                            }
                        }
                        if (linked) {
                            boolean hasBQuParty = provider.hasNativeParty(player.getUniqueID());
                            if (!hasBQuParty) {
                                break;
                            }
                            pmLink.setBQuLinked(player.getUniqueID(), true);
                        } else {
                            // Only unlink if currently linked
                            if (!pmLink.isBQuLinked(player.getUniqueID())) break;
                            pmLink.setBQuLinked(player.getUniqueID(), false);
                            getOrCreateSelfParty(player, provider);
                        }
                        success = true;
                        break;
                    }
                    case ACTION_TOGGLE_FAKE_PLAYERS: {
                        // Legacy: cycle NONE -> ALLY -> MEMBER -> NONE
                        Party toggleParty = getOrCreateSelfParty(player, activeProvider);
                        if (toggleParty != null) {
                            PartyRole toggleRole = toggleParty.getRole(player.getUniqueID());
                            if (toggleRole != null && toggleRole.canInvite()) {
                                TrustLevel cur = toggleParty.getFakePlayerTrustLevel();
                                TrustLevel next = cur == TrustLevel.NONE ? TrustLevel.ALLY :
                                        cur == TrustLevel.ALLY ? TrustLevel.MEMBER : TrustLevel.NONE;
                                toggleParty.setFakePlayerTrustLevel(next);
                                success = true;
                            }
                        }
                        break;
                    }
                    case ACTION_TOGGLE_EXPLOSION_PROTECTION: {
                        Party toggleParty = getOrCreateSelfParty(player, activeProvider);
                        if (toggleParty != null) {
                            PartyRole toggleRole = toggleParty.getRole(player.getUniqueID());
                            if (toggleRole != null && toggleRole.canInvite()) {
                                toggleParty.setProtectExplosions(!toggleParty.protectsExplosions());
                                success = true;
                            }
                        }
                        break;
                    }
                    case ACTION_ADD_ALLY:
                    case ACTION_REMOVE_ALLY:
                    case ACTION_ADD_ENEMY:
                    case ACTION_REMOVE_ENEMY: {
                        Party allyParty = getOrCreateSelfParty(player, activeProvider);
                        if (allyParty == null) break;
                        PartyRole allyRole = allyParty.getRole(player.getUniqueID());
                        if (allyRole == null || !allyRole.canInvite()) break;
                        MinecraftServer srv = player.getServer();
                        if (srv == null) break;
                        EntityPlayerMP target = srv.getPlayerList()
                                .getPlayerByUsername(msg.stringArg);
                        if (target == null) break;
                        UUID targetId = target.getUniqueID();
                        if (allyParty.isMember(targetId)) break;
                        switch (msg.action) {
                            case ACTION_ADD_ALLY:
                                allyParty.addAlly(targetId);
                                break;
                            case ACTION_REMOVE_ALLY:
                                allyParty.removeAlly(targetId);
                                break;
                            case ACTION_ADD_ENEMY:
                                allyParty.addEnemy(targetId);
                                break;
                            case ACTION_REMOVE_ENEMY:
                                allyParty.removeEnemy(targetId);
                                break;
                        }
                        success = true;
                        break;
                    }
                    case ACTION_TRANSFER_OWNERSHIP: {
                        Party tParty = getOrCreateSelfParty(player, activeProvider);
                        if (tParty == null) break;
                        PartyRole tRole = tParty.getRole(player.getUniqueID());
                        if (tRole != PartyRole.OWNER && !player.canUseCommand(2, "")) break;
                        MinecraftServer srv = player.getServer();
                        if (srv == null) break;
                        EntityPlayerMP target = srv.getPlayerList()
                                .getPlayerByUsername(msg.stringArg);
                        if (target == null) break;
                        if (!tParty.isMember(target.getUniqueID())) break;
                        tParty.setRole(target.getUniqueID(), PartyRole.OWNER);
                        success = true;
                        break;
                    }
                    case ACTION_SET_TRUST_LEVEL: {
                        Party trustParty = getOrCreateSelfParty(player, activeProvider);
                        if (trustParty == null) break;
                        PartyRole trustRole = trustParty.getRole(player.getUniqueID());
                        if (trustRole == null || !trustRole.canInvite()) break;
                        String[] tp = msg.stringArg.split(":", 2);
                        if (tp.length == 2) {
                            TrustAction ta = TrustAction.fromNbtKey(tp[0]);
                            TrustLevel tl = TrustLevel.fromName(tp[1]);
                            if (ta != null && tl.ordinal() <= TrustLevel.MEMBER.ordinal()) {
                                trustParty.setTrustLevel(ta, tl);
                                success = true;
                            }
                        }
                        break;
                    }
                    case ACTION_SET_FAKEPLAYER_TRUST: {
                        Party fpParty = getOrCreateSelfParty(player, activeProvider);
                        if (fpParty == null) break;
                        PartyRole fpRole = fpParty.getRole(player.getUniqueID());
                        if (fpRole == null || !fpRole.canInvite()) break;
                        TrustLevel fpLevel = TrustLevel.fromName(msg.stringArg);
                        if (fpLevel.ordinal() <= TrustLevel.MEMBER.ordinal()) {
                            fpParty.setFakePlayerTrustLevel(fpLevel);
                            success = true;
                        }
                        break;
                    }
                    case ACTION_SET_FREE_TO_JOIN: {
                        Party fjParty = getOrCreateSelfParty(player, activeProvider);
                        if (fjParty == null) break;
                        PartyRole fjRole = fjParty.getRole(player.getUniqueID());
                        if (fjRole == null || !fjRole.canInvite()) break;
                        fjParty.setFreeToJoin("true".equals(msg.stringArg));
                        success = true;
                        break;
                    }
                    case ACTION_SET_COLOR: {
                        Party cParty = getOrCreateSelfParty(player, activeProvider);
                        if (cParty == null) break;
                        PartyRole cRole = cParty.getRole(player.getUniqueID());
                        if (cRole == null || !cRole.canInvite()) break;
                        try {
                            cParty.setColor(Integer.parseInt(msg.stringArg));
                            success = true;
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                    case ACTION_SET_TITLE: {
                        Party titleParty = getOrCreateSelfParty(player, activeProvider);
                        if (titleParty == null) break;
                        PartyRole titleRole = titleParty.getRole(player.getUniqueID());
                        if (titleRole == null || !titleRole.canInvite()) break;
                        String title = msg.stringArg.trim();
                        if (title.length() > 64) title = title.substring(0, 64);
                        titleParty.setTitle(title);
                        success = true;
                        break;
                    }
                    case ACTION_SET_DESCRIPTION: {
                        Party descParty = getOrCreateSelfParty(player, activeProvider);
                        if (descParty == null) break;
                        PartyRole descRole = descParty.getRole(player.getUniqueID());
                        if (descRole == null || !descRole.canInvite()) break;
                        String desc = msg.stringArg.trim();
                        if (desc.length() > 256) desc = desc.substring(0, 256);
                        descParty.setDescription(desc);
                        success = true;
                        break;
                    }
                    case ACTION_JOIN_FREE_PARTY: {
                        try {
                            int joinId = Integer.parseInt(msg.stringArg);
                            PartyManagerData pmJoin = PartyManagerData.getInstance();
                            // Player must not already be in a party
                            if (pmJoin.getPartyByPlayer(player.getUniqueID()) != null) break;
                            Party joinParty = pmJoin.getParty(joinId);
                            if (joinParty == null || !joinParty.isFreeToJoin()) break;
                            joinParty.addMember(player.getUniqueID(), PartyRole.MEMBER);
                            success = true;
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }

                if (success) {
                    provider.syncToAll();
                    BLPCSaveHandler.INSTANCE.markDirty();
                }
            });
            return null;
        }
    }
}
