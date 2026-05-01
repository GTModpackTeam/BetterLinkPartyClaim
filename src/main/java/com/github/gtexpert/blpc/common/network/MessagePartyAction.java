package com.github.gtexpert.blpc.common.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.PartyRole;
import com.github.gtexpert.blpc.common.party.TrustAction;
import com.github.gtexpert.blpc.common.party.TrustLevel;

import io.netty.buffer.ByteBuf;

/**
 * C→S packet for party operations. A single message multiplexes all party
 * mutations through an integer {@code action} discriminator and a
 * {@code stringArg} payload.
 * <p>
 * <b>Wire protocol stability:</b> the {@code ACTION_*} constants are part of
 * the on-wire format. Do not renumber existing actions; append new ones at the
 * end. Removing an action requires a coordinated client/server release.
 * <p>
 * <b>Authorization:</b> the server-side {@link Handler} re-derives the active
 * provider per request from {@link PartyManagerData#isBQuLinked} so a malicious
 * client cannot bypass BQu integration. Role checks are enforced via
 * {@link #getAdminParty} / {@link #getOrCreateSelfParty}.
 * <p>
 * Use the static factory methods (e.g. {@link #create}, {@link #invite}) when
 * sending from the client — they encode arguments consistently with the
 * server-side decoder.
 */
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
    public static final int ACTION_SET_DESCRIPTION = 19;
    public static final int ACTION_JOIN_FREE_PARTY = 20;
    public static final int ACTION_SET_MAX_MEMBERS = 21;

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

    public static MessagePartyAction acceptInvite(UUID partyId) {
        return new MessagePartyAction(ACTION_ACCEPT_INVITE, partyId.toString());
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

    public static MessagePartyAction setExplosionProtection(boolean protect) {
        return new MessagePartyAction(ACTION_TOGGLE_EXPLOSION_PROTECTION, protect ? "true" : "false");
    }

    public static MessagePartyAction addAlly(UUID partyId) {
        return new MessagePartyAction(ACTION_ADD_ALLY, partyId.toString());
    }

    public static MessagePartyAction removeAlly(UUID partyId) {
        return new MessagePartyAction(ACTION_REMOVE_ALLY, partyId.toString());
    }

    public static MessagePartyAction addEnemy(UUID partyId) {
        return new MessagePartyAction(ACTION_ADD_ENEMY, partyId.toString());
    }

    public static MessagePartyAction removeEnemy(UUID partyId) {
        return new MessagePartyAction(ACTION_REMOVE_ENEMY, partyId.toString());
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

    public static MessagePartyAction setDescription(String desc) {
        return new MessagePartyAction(ACTION_SET_DESCRIPTION, desc);
    }

    public static MessagePartyAction joinFreeParty(UUID partyId) {
        return new MessagePartyAction(ACTION_JOIN_FREE_PARTY, partyId.toString());
    }

    public static MessagePartyAction setMaxMembers(int max) {
        return new MessagePartyAction(ACTION_SET_MAX_MEMBERS, Integer.toString(max));
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

    private static Party getAdminParty(EntityPlayerMP player, IPartyProvider provider) {
        Party party = getOrCreateSelfParty(player, provider);
        if (party == null) return null;
        PartyRole role = party.getRole(player.getUniqueID());
        if (role == null || !role.canInvite()) return null;
        return party;
    }

    public static class Handler implements IMessageHandler<MessagePartyAction, IMessage> {

        private static final DefaultPartyProvider SELF_PROVIDER = new DefaultPartyProvider();

        @Override
        public IMessage onMessage(MessagePartyAction msg, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                IPartyProvider provider = PartyProviderRegistry.get();
                // When not BQu-linked, use self-managed provider to avoid
                // accidentally creating/modifying BQu parties
                boolean playerBQuLinked = PartyManagerData.getInstance()
                        .isBQuLinked(player.getUniqueID());
                DefaultPartyProvider selfProvider = SELF_PROVIDER;
                IPartyProvider activeProvider = playerBQuLinked ? provider : selfProvider;

                boolean success = false;
                // Pending notifications to dispatch after syncToAll()
                List<Runnable> pendingNotifications = new ArrayList<>();

                switch (msg.action) {
                    case ACTION_CREATE -> {
                        String name = msg.stringArg.trim();
                        if (name.isEmpty()) name = "New Party";
                        if (name.length() > 32) name = name.substring(0, 32);
                        success = selfProvider.createParty(player, name);
                    }
                    case ACTION_DISBAND -> {
                        // Ensure self-managed party exists (may need to create from BQu data)
                        getOrCreateSelfParty(player, provider);
                        PartyManagerData pmDisband = PartyManagerData.getInstance();
                        Party disbandParty = pmDisband.getPartyByPlayer(player.getUniqueID());
                        if (disbandParty == null) break;
                        PartyRole disbandRole = disbandParty.getRole(player.getUniqueID());
                        boolean isOwnerOrOp = (disbandRole == PartyRole.OWNER) || player.canUseCommand(2, "");
                        // BQu Link 状態では BQu 側のロールも確認する
                        if (!isOwnerOrOp && playerBQuLinked) {
                            String providerRole = provider.getRole(player.getUniqueID());
                            isOwnerOrOp = PartyRole.fromName(providerRole) == PartyRole.OWNER;
                        }
                        if (!isOwnerOrOp) break;
                        // Authorization verified — disband directly to bypass the internal
                        // role check in DefaultPartyProvider.disbandParty(), which would
                        // reject non-OWNER self-managed roles even when BQu-linked as OWNER.
                        List<UUID> disbandMembers = new ArrayList<>(disbandParty.getMemberUUIDs());
                        ChunkManagerData disbandChunks = ChunkManagerData.getInstance();
                        for (UUID memberId : disbandMembers) {
                            disbandChunks.releaseAllClaims(memberId, player.world);
                        }
                        pmDisband.removeParty(disbandParty.getPartyId());
                        for (UUID memberId : disbandMembers) {
                            pmDisband.setBQuLinked(memberId, false);
                        }
                        MinecraftServer disbandSrv = player.getServer();
                        pendingNotifications.add(() -> {
                            for (UUID memberId : disbandMembers) {
                                EntityPlayerMP disbandMember = disbandSrv != null ?
                                        disbandSrv.getPlayerList().getPlayerByUUID(memberId) : null;
                                if (disbandMember != null) {
                                    notifyPlayer(disbandMember, MessagePartyEventNotify.DISBANDED, "", "");
                                }
                            }
                        });
                        success = true;
                    }
                    case ACTION_RENAME -> {
                        String newName = msg.stringArg.trim();
                        if (newName.length() > 32) newName = newName.substring(0, 32);
                        if (!newName.isEmpty()) {
                            success = activeProvider.renameParty(player, newName);
                        }
                    }
                    case ACTION_INVITE -> {
                        Party invInviterParty = PartyManagerData.getInstance()
                                .getPartyByPlayer(player.getUniqueID());
                        if (invInviterParty != null && !invInviterParty.canAddMember()) {
                            notifyPlayer(player, MessagePartyEventNotify.PARTY_FULL, "", "");
                            break;
                        }
                        success = activeProvider.invitePlayer(player, msg.stringArg);
                        if (success) {
                            MinecraftServer invSrv = player.getServer();
                            if (invSrv != null) {
                                EntityPlayerMP invTarget = invSrv.getPlayerList()
                                        .getPlayerByUsername(msg.stringArg);
                                if (invTarget != null) {
                                    Party invParty = PartyManagerData.getInstance()
                                            .getPartyByPlayer(player.getUniqueID());
                                    String invPartyName = invParty != null ? invParty.getName() :
                                            provider.getPartyName(player.getUniqueID());
                                    String resolvedPartyName = invPartyName != null ? invPartyName : "";
                                    String inviterName = player.getName();
                                    pendingNotifications.add(() -> notifyPlayer(
                                            invTarget, MessagePartyEventNotify.INVITE_RECEIVED,
                                            inviterName, resolvedPartyName));
                                }
                            }
                        }
                    }
                    case ACTION_ACCEPT_INVITE -> {
                        try {
                            UUID partyId = UUID.fromString(msg.stringArg);
                            Party invTargetParty = PartyManagerData.getInstance().getParty(partyId);
                            if (invTargetParty != null && !invTargetParty.canAddMember()) {
                                notifyPlayer(player, MessagePartyEventNotify.PARTY_FULL, "", "");
                                break;
                            }
                            success = activeProvider.acceptInvite(player, partyId);
                            if (success) {
                                Party joinedParty = PartyManagerData.getInstance().getPartyByPlayer(
                                        player.getUniqueID());
                                if (joinedParty != null) {
                                    String joinerName = player.getName();
                                    MinecraftServer acceptSrv = player.getServer();
                                    pendingNotifications.add(() -> notifyPartyMembers(
                                            joinedParty, MessagePartyEventNotify.MEMBER_JOINED,
                                            joinerName, "", acceptSrv));
                                }
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                    case ACTION_KICK_OR_LEAVE -> {
                        Party klParty = PartyManagerData.getInstance()
                                .getPartyByPlayer(player.getUniqueID());
                        boolean isSelf = msg.stringArg.equals(player.getName());
                        Map<UUID, PartyRole> klMembersCopy = klParty != null ?
                                new HashMap<>(klParty.getMembers()) : Collections.emptyMap();
                        // Resolve target UUID before kick (target may be offline)
                        UUID klTargetUUID = null;
                        if (isSelf) {
                            klTargetUUID = player.getUniqueID();
                        } else if (klParty != null) {
                            for (var kv : klMembersCopy.entrySet()) {
                                EntityPlayerMP onlineMember = player.getServer() != null ?
                                        player.getServer().getPlayerList().getPlayerByUUID(kv.getKey()) : null;
                                if (onlineMember != null && onlineMember.getName().equals(msg.stringArg)) {
                                    klTargetUUID = kv.getKey();
                                    break;
                                }
                            }
                        }
                        success = activeProvider.kickOrLeave(player, msg.stringArg);
                        if (success) {
                            if (klTargetUUID != null) {
                                PartyManagerData.getInstance()
                                        .setBQuLinked(klTargetUUID, false);
                            }
                            if (klParty != null) {
                                String klEvent = isSelf ? MessagePartyEventNotify.MEMBER_LEFT :
                                        MessagePartyEventNotify.KICKED;
                                UUID klTargetId = klTargetUUID;
                                String klTargetName = msg.stringArg;
                                MinecraftServer klSrv = player.getServer();
                                pendingNotifications.add(() -> {
                                    for (var entry : klMembersCopy.entrySet()) {
                                        if (klTargetId != null && entry.getKey().equals(klTargetId))
                                            continue;
                                        EntityPlayerMP klMember = klSrv != null ?
                                                klSrv.getPlayerList().getPlayerByUUID(entry.getKey()) : null;
                                        if (klMember != null) {
                                            notifyPlayer(klMember, klEvent, klTargetName, "");
                                        }
                                    }
                                });
                            }
                        }
                    }
                    case ACTION_CHANGE_ROLE -> {
                        String[] parts = msg.stringArg.split(":", 2);
                        if (parts.length == 2) {
                            success = activeProvider.changeRole(player, parts[0], parts[1]);
                            if (success && player.getServer() != null) {
                                EntityPlayerMP roleTarget = player.getServer().getPlayerList()
                                        .getPlayerByUsername(parts[0]);
                                if (roleTarget != null) {
                                    String targetName = parts[0];
                                    String newRole = parts[1];
                                    pendingNotifications.add(() -> notifyPlayer(
                                            roleTarget, MessagePartyEventNotify.ROLE_CHANGED,
                                            targetName, newRole));
                                }
                            }
                        }
                    }
                    case ACTION_TOGGLE_BQU_LINK -> {
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
                        Party bquParty = PartyManagerData.getInstance()
                                .getPartyByPlayer(player.getUniqueID());
                        if (bquParty != null) {
                            String bquEvent = linked ? MessagePartyEventNotify.BQU_LINKED :
                                    MessagePartyEventNotify.BQU_UNLINKED;
                            MinecraftServer bquSrv = player.getServer();
                            pendingNotifications.add(() -> notifyPartyMembers(
                                    bquParty, bquEvent, "", "", bquSrv));
                        }
                        success = true;
                    }
                    case ACTION_TOGGLE_FAKE_PLAYERS -> {
                        // Retained for wire-protocol stability — current UI uses
                        // ACTION_SET_FAKEPLAYER_TRUST to set a level directly. This branch
                        // cycles NONE → ALLY → MEMBER → NONE for any legacy client still
                        // sending action=8.
                        Party toggleParty = getOrCreateSelfParty(player, activeProvider);
                        if (toggleParty != null) {
                            PartyRole toggleRole = toggleParty.getRole(player.getUniqueID());
                            if (toggleRole != null && toggleRole.canInvite()) {
                                TrustLevel cur = toggleParty.getFakePlayerTrustLevel();
                                TrustLevel next = switch (cur) {
                                    case NONE -> TrustLevel.ALLY;
                                    case ALLY -> TrustLevel.MEMBER;
                                    default -> TrustLevel.NONE;
                                };
                                toggleParty.setFakePlayerTrustLevel(next);
                                success = true;
                            }
                        }
                    }
                    case ACTION_TOGGLE_EXPLOSION_PROTECTION -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        party.setProtectExplosions("true".equals(msg.stringArg));
                        success = true;
                    }
                    case ACTION_ADD_ALLY, ACTION_REMOVE_ALLY, ACTION_ADD_ENEMY, ACTION_REMOVE_ENEMY -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        UUID targetPartyId;
                        try {
                            targetPartyId = UUID.fromString(msg.stringArg);
                        } catch (IllegalArgumentException e) {
                            break;
                        }
                        // Don't allow self-reference
                        if (targetPartyId.equals(party.getPartyId())) break;
                        switch (msg.action) {
                            case ACTION_ADD_ALLY -> party.addAlly(targetPartyId);
                            case ACTION_REMOVE_ALLY -> party.removeAlly(targetPartyId);
                            case ACTION_ADD_ENEMY -> party.addEnemy(targetPartyId);
                            case ACTION_REMOVE_ENEMY -> party.removeEnemy(targetPartyId);
                        }
                        success = true;
                    }
                    case ACTION_TRANSFER_OWNERSHIP -> {
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
                        String newOwnerName = target.getName();
                        String senderName = player.getName();
                        pendingNotifications.add(() -> {
                            notifyPlayer(target, MessagePartyEventNotify.OWNER_TRANSFERRED, newOwnerName, "");
                            notifyPlayer(player, MessagePartyEventNotify.ROLE_CHANGED, senderName, "ADMIN");
                        });
                        success = true;
                    }
                    case ACTION_SET_TRUST_LEVEL -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        String[] tp = msg.stringArg.split(":", 2);
                        if (tp.length == 2) {
                            TrustAction ta = TrustAction.fromNbtKey(tp[0]);
                            TrustLevel tl = TrustLevel.fromName(tp[1]);
                            if (ta != null && tl.ordinal() <= TrustLevel.MEMBER.ordinal()) {
                                party.setTrustLevel(ta, tl);
                                success = true;
                            }
                        }
                    }
                    case ACTION_SET_FAKEPLAYER_TRUST -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        TrustLevel fpLevel = TrustLevel.fromName(msg.stringArg);
                        if (fpLevel.ordinal() <= TrustLevel.MEMBER.ordinal()) {
                            party.setFakePlayerTrustLevel(fpLevel);
                            success = true;
                        }
                    }
                    case ACTION_SET_FREE_TO_JOIN -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        party.setFreeToJoin("true".equals(msg.stringArg));
                        success = true;
                    }
                    case ACTION_SET_COLOR -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        try {
                            party.setColor(Integer.parseInt(msg.stringArg));
                            success = true;
                        } catch (NumberFormatException ignored) {}
                    }
                    case ACTION_SET_DESCRIPTION -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        String desc = msg.stringArg.trim();
                        if (desc.length() > 256) desc = desc.substring(0, 256);
                        party.setDescription(desc);
                        success = true;
                    }
                    case ACTION_SET_MAX_MEMBERS -> {
                        Party party = getAdminParty(player, activeProvider);
                        if (party == null) break;
                        try {
                            int max = Integer.parseInt(msg.stringArg);
                            party.setMaxMembers(Math.min(100, Math.max(0, max)));
                            success = true;
                        } catch (NumberFormatException ignored) {}
                    }
                    case ACTION_JOIN_FREE_PARTY -> {
                        try {
                            UUID joinId = UUID.fromString(msg.stringArg);
                            PartyManagerData pmJoin = PartyManagerData.getInstance();
                            // Player must not already be in a party
                            if (pmJoin.getPartyByPlayer(player.getUniqueID()) != null) break;
                            Party joinParty = pmJoin.getParty(joinId);
                            if (joinParty == null || !joinParty.isFreeToJoin()) break;
                            if (!joinParty.canAddMember()) {
                                notifyPlayer(player, MessagePartyEventNotify.PARTY_FULL, "", "");
                                break;
                            }
                            joinParty.addMember(player.getUniqueID(), PartyRole.MEMBER);
                            String joinerName = player.getName();
                            MinecraftServer joinSrv = player.getServer();
                            pendingNotifications.add(() -> notifyPartyMembers(
                                    joinParty, MessagePartyEventNotify.MEMBER_JOINED,
                                    joinerName, "", joinSrv));
                            success = true;
                        } catch (IllegalArgumentException ignored) {}
                    }
                }

                if (success || msg.action == ACTION_TOGGLE_BQU_LINK) {
                    provider.syncToAll();
                }
                if (success) {
                    BLPCSaveHandler.INSTANCE.markDirty();
                    for (Runnable notification : pendingNotifications) {
                        notification.run();
                    }
                }
            });
            return null;
        }

        private static void notifyPartyMembers(Party party, String eventType, String playerName, String extra,
                                               MinecraftServer server) {
            if (server == null) return;
            MessagePartyEventNotify packet = new MessagePartyEventNotify(eventType, playerName, extra);
            for (UUID memberId : party.getMembers().keySet()) {
                EntityPlayerMP member = server.getPlayerList().getPlayerByUUID(memberId);
                if (member != null) ModNetwork.INSTANCE.sendTo(packet, member);
            }
        }

        private static void notifyPlayer(EntityPlayerMP player, String eventType, String playerName, String extra) {
            ModNetwork.INSTANCE.sendTo(new MessagePartyEventNotify(eventType, playerName, extra), player);
        }
    }
}
