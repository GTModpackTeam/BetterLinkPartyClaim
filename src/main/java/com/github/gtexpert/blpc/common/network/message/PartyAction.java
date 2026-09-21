package com.github.gtexpert.blpc.common.network.message;

import java.util.*;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.api.event.PartyEvent;
import com.github.gtexpert.blpc.api.party.*;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.waypoint.WaypointManagerData;

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
 * <b>Server handler:</b> {@link Handler}, registered in {@code ModNetwork}.
 * Use the static factory methods (e.g. {@link #create}, {@link #invite}) when
 * sending from the client — they encode arguments consistently with the
 * server-side decoder.
 */
public class PartyAction implements IMessage {

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

    public PartyAction() {}

    public PartyAction(int action, String stringArg) {
        this.action = action;
        this.stringArg = stringArg;
    }

    public int getAction() {
        return action;
    }

    public String getStringArg() {
        return stringArg;
    }

    public static PartyAction create(String name) {
        return new PartyAction(ACTION_CREATE, name);
    }

    public static PartyAction disband() {
        return new PartyAction(ACTION_DISBAND, "");
    }

    public static PartyAction rename(String newName) {
        return new PartyAction(ACTION_RENAME, newName);
    }

    public static PartyAction invite(String username) {
        return new PartyAction(ACTION_INVITE, username);
    }

    public static PartyAction acceptInvite(UUID partyId) {
        return new PartyAction(ACTION_ACCEPT_INVITE, partyId.toString());
    }

    public static PartyAction kickOrLeave(String username) {
        return new PartyAction(ACTION_KICK_OR_LEAVE, username);
    }

    public static PartyAction changeRole(String usernameAndRole) {
        return new PartyAction(ACTION_CHANGE_ROLE, usernameAndRole);
    }

    public static PartyAction toggleBQuLink(boolean linked) {
        return new PartyAction(ACTION_TOGGLE_BQU_LINK, linked ? "true" : "false");
    }

    public static PartyAction setExplosionProtection(boolean protect) {
        return new PartyAction(ACTION_TOGGLE_EXPLOSION_PROTECTION, protect ? "true" : "false");
    }

    public static PartyAction addAlly(UUID partyId) {
        return new PartyAction(ACTION_ADD_ALLY, partyId.toString());
    }

    public static PartyAction removeAlly(UUID partyId) {
        return new PartyAction(ACTION_REMOVE_ALLY, partyId.toString());
    }

    public static PartyAction addEnemy(UUID partyId) {
        return new PartyAction(ACTION_ADD_ENEMY, partyId.toString());
    }

    public static PartyAction removeEnemy(UUID partyId) {
        return new PartyAction(ACTION_REMOVE_ENEMY, partyId.toString());
    }

    public static PartyAction transferOwnership(String username) {
        return new PartyAction(ACTION_TRANSFER_OWNERSHIP, username);
    }

    public static PartyAction setTrustLevel(String actionAndLevel) {
        return new PartyAction(ACTION_SET_TRUST_LEVEL, actionAndLevel);
    }

    public static PartyAction setFakePlayerTrust(String level) {
        return new PartyAction(ACTION_SET_FAKEPLAYER_TRUST, level);
    }

    public static PartyAction setFreeToJoin(boolean free) {
        return new PartyAction(ACTION_SET_FREE_TO_JOIN, free ? "true" : "false");
    }

    public static PartyAction setColor(int color) {
        return new PartyAction(ACTION_SET_COLOR, Integer.toString(color));
    }

    public static PartyAction setDescription(String desc) {
        return new PartyAction(ACTION_SET_DESCRIPTION, desc);
    }

    public static PartyAction joinFreeParty(UUID partyId) {
        return new PartyAction(ACTION_JOIN_FREE_PARTY, partyId.toString());
    }

    public static PartyAction setMaxMembers(int max) {
        return new PartyAction(ACTION_SET_MAX_MEMBERS, Integer.toString(max));
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

    /**
     * Server-side dispatcher for {@link PartyAction}. Each {@code ACTION_*} discriminator
     * maps to a single private method below.
     * <p>
     * <b>Authorization:</b> the active provider is re-derived per request from
     * {@link IPartyProvider#isLinkedParty} so a malicious client cannot bypass BQu integration —
     * this checks the player's <em>current</em> native-party membership rather than a per-player
     * flag, so it stays correct for members who joined an already-linked party after the fact.
     * Role checks happen via {@link #getAdminParty} / {@link #getOrCreateSelfParty} in each
     * mutating action.
     * <p>
     * <b>Wire protocol stability:</b> this dispatcher only consumes the integer discriminator
     * and string payload defined by this class; the on-wire format lives above.
     */
    public static final class Handler implements IMessageHandler<PartyAction, IMessage> {

        private static final DefaultPartyProvider SELF_PROVIDER = new DefaultPartyProvider();

        @Override
        public IMessage onMessage(PartyAction msg, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> dispatch(msg, ctx));
            return null;
        }

        private static void dispatch(PartyAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            IPartyProvider provider = PartyProviderRegistry.get();
            // See class javadoc: isLinkedParty is a live check, not a per-player flag.
            boolean playerBQuLinked = provider.isLinkedParty(player.getUniqueID());
            IPartyProvider activeProvider = playerBQuLinked ? provider : SELF_PROVIDER;

            ActionContext c = new ActionContext(player, msg.getStringArg(), provider, SELF_PROVIDER, activeProvider,
                    new ArrayList<>());

            boolean success = switch (msg.getAction()) {
                case PartyAction.ACTION_CREATE -> createParty(c);
                case PartyAction.ACTION_DISBAND -> disbandParty(c);
                case PartyAction.ACTION_RENAME -> renameParty(c);
                case PartyAction.ACTION_INVITE -> invitePlayer(c);
                case PartyAction.ACTION_ACCEPT_INVITE -> acceptInvite(c);
                case PartyAction.ACTION_KICK_OR_LEAVE -> kickOrLeave(c);
                case PartyAction.ACTION_CHANGE_ROLE -> changeRole(c);
                case PartyAction.ACTION_TOGGLE_BQU_LINK -> toggleBQuLink(c);
                case PartyAction.ACTION_TOGGLE_FAKE_PLAYERS -> toggleFakePlayers(c);
                case PartyAction.ACTION_TOGGLE_EXPLOSION_PROTECTION -> toggleExplosionProtection(c);
                case PartyAction.ACTION_ADD_ALLY, PartyAction.ACTION_REMOVE_ALLY, PartyAction.ACTION_ADD_ENEMY, PartyAction.ACTION_REMOVE_ENEMY -> updateRelation(
                        c, msg.getAction());
                case PartyAction.ACTION_TRANSFER_OWNERSHIP -> transferOwnership(c);
                case PartyAction.ACTION_SET_TRUST_LEVEL -> setTrustLevel(c);
                case PartyAction.ACTION_SET_FAKEPLAYER_TRUST -> setFakePlayerTrust(c);
                case PartyAction.ACTION_SET_FREE_TO_JOIN -> setFreeToJoin(c);
                case PartyAction.ACTION_SET_COLOR -> setColor(c);
                case PartyAction.ACTION_SET_DESCRIPTION -> setDescription(c);
                case PartyAction.ACTION_SET_MAX_MEMBERS -> setMaxMembers(c);
                case PartyAction.ACTION_JOIN_FREE_PARTY -> joinFreeParty(c);
                default -> false;
            };

            if (success) {
                c.provider.syncToAll();
                BLPCSaveHandler.INSTANCE.markDirty();
                for (Runnable notification : c.pendingNotifications) {
                    notification.run();
                }
            } else if (msg.getAction() == PartyAction.ACTION_TOGGLE_BQU_LINK) {
                // BQu link toggle on failure may indicate provider drift — broadcast.
                c.provider.syncToAll();
            } else {
                // Roll back the actor's optimistic client mutation. Without this,
                // failures like joinFreeParty(party-full), acceptInvite(expired),
                // or kickOrLeave(role-mismatch) leave the client cache divergent
                // until some other action triggers a broadcast sync.
                c.provider.syncToPlayer(player);
            }
        }

        // -- Per-action handlers ------------------------------------------------------------

        private static boolean createParty(ActionContext c) {
            String name = c.stringArg.trim();
            if (name.isEmpty()) name = "New Party";
            if (name.length() > 32) name = name.substring(0, 32);
            UUID playerId = c.player.getUniqueID();
            if (MinecraftForge.EVENT_BUS.post(new PartyEvent.Pre.Created(name, playerId))) return false;
            if (!c.selfProvider.createParty(c.player, name)) return false;
            Party created = c.selfProvider.getEffectiveParty(playerId);
            if (created != null) {
                MinecraftForge.EVENT_BUS.post(
                        new PartyEvent.Post.Created(created.getPartyId(), created.getName(), playerId));
            }
            return true;
        }

        private static boolean disbandParty(ActionContext c) {
            UUID playerId = c.player.getUniqueID();
            PartyManagerData pm = PartyManagerData.getInstance();
            Party party = c.provider.getEffectiveParty(playerId);
            if (party == null) return false;

            PartyRole role = party.getRole(playerId);
            if (role != PartyRole.OWNER && !c.player.canUseCommand(2, "")) return false;

            UUID partyId = party.getPartyId();
            String partyName = party.getName();
            if (MinecraftForge.EVENT_BUS.post(new PartyEvent.Pre.Disbanded(partyId, partyName))) return false;

            List<UUID> members = new ArrayList<>(party.getMemberUUIDs());
            pm.removeParty(partyId);
            ChunkManagerData.getInstance().releaseAllMemberClaims(members, c.player.world);
            WaypointManagerData.getInstance().removeParty(partyId);
            for (UUID memberId : members) {
                pm.setBQuLinked(memberId, false);
            }
            MinecraftForge.EVENT_BUS.post(new PartyEvent.Post.Disbanded(partyId, partyName, members));
            MinecraftServer srv = c.player.getServer();
            // The party record is already gone — broadcast against the pre-disband member
            // snapshot, not a fresh provider lookup.
            c.pendingNotifications.add(() -> ModNetwork.broadcastToMembers(members, playerId, srv,
                    ClientNotify.partyEvent(ClientNotify.EVENT_DISBANDED, "", "")));
            return true;
        }

        private static boolean renameParty(ActionContext c) {
            String newName = c.stringArg.trim();
            if (newName.length() > 32) newName = newName.substring(0, 32);
            if (newName.isEmpty()) return false;
            return c.activeProvider.renameParty(c.player, newName);
        }

        private static boolean invitePlayer(ActionContext c) {
            Party inviterParty = c.provider.getEffectiveParty(c.player.getUniqueID());
            if (inviterParty != null && !inviterParty.canAddMember()) {
                notifyPlayer(c.player, ClientNotify.EVENT_PARTY_FULL, "", "");
                return false;
            }
            if (!c.activeProvider.invitePlayer(c.player, c.stringArg)) return false;

            MinecraftServer srv = c.player.getServer();
            if (srv != null) {
                EntityPlayerMP target = srv.getPlayerList().getPlayerByUsername(c.stringArg);
                if (target != null) {
                    Party party = c.provider.getEffectiveParty(c.player.getUniqueID());
                    String partyName = party != null ? party.getName() :
                            c.provider.getPartyName(c.player.getUniqueID());
                    String resolvedPartyName = partyName != null ? partyName : "";
                    String inviterName = c.player.getName();
                    c.pendingNotifications.add(() -> notifyPlayer(target, ClientNotify.EVENT_INVITE_RECEIVED,
                            inviterName, resolvedPartyName));
                }
            }
            return true;
        }

        private static boolean acceptInvite(ActionContext c) {
            UUID partyId;
            try {
                partyId = UUID.fromString(c.stringArg);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            Party targetParty = PartyManagerData.getInstance().getParty(partyId);
            if (targetParty == null) {
                // Party was disbanded after the invite was sent.
                notifyPlayer(c.player, ClientNotify.EVENT_JOIN_FAILED, "", "");
                return false;
            }
            if (!targetParty.canAddMember()) {
                notifyPlayer(c.player, ClientNotify.EVENT_PARTY_FULL, "", "");
                return false;
            }
            if (!c.activeProvider.acceptInvite(c.player, partyId)) {
                // Invite expired, revoked, or otherwise rejected by the provider.
                notifyPlayer(c.player, ClientNotify.EVENT_JOIN_FAILED, "", "");
                return false;
            }

            Party joinedParty = c.selfProvider.getEffectiveParty(c.player.getUniqueID());
            if (joinedParty != null) {
                UUID joinerId = c.player.getUniqueID();
                MinecraftForge.EVENT_BUS.post(
                        new PartyEvent.Post.MemberJoined(joinedParty.getPartyId(), joinedParty.getName(), joinerId));
                String joinerName = c.player.getName();
                MinecraftServer srv = c.player.getServer();
                IPartyProvider provider = c.activeProvider;
                c.pendingNotifications.add(() -> notifyPartyMembers(provider, joinerId,
                        ClientNotify.EVENT_MEMBER_JOINED, joinerName, "", srv, joinerId));
            }
            return true;
        }

        private static boolean kickOrLeave(ActionContext c) {
            Party party = c.selfProvider.getEffectiveParty(c.player.getUniqueID());
            boolean isSelf = c.stringArg.equals(c.player.getName());
            Map<UUID, PartyRole> membersCopy = party != null ? new HashMap<>(party.getMembers()) :
                    Collections.emptyMap();

            UUID targetUUID = null;
            if (isSelf) {
                targetUUID = c.player.getUniqueID();
            } else if (party != null) {
                MinecraftServer srv = c.player.getServer();
                for (var kv : membersCopy.entrySet()) {
                    EntityPlayerMP onlineMember = srv != null ? srv.getPlayerList().getPlayerByUUID(kv.getKey()) : null;
                    if (onlineMember != null && onlineMember.getName().equals(c.stringArg)) {
                        targetUUID = kv.getKey();
                        break;
                    }
                }
            }
            if (!c.activeProvider.kickOrLeave(c.player, c.stringArg)) return false;

            if (targetUUID != null) {
                PartyManagerData.getInstance().setBQuLinked(targetUUID, false);
                if (party != null) {
                    MinecraftForge.EVENT_BUS.post(new PartyEvent.Post.MemberLeft(
                            party.getPartyId(), party.getName(), targetUUID, !isSelf));
                }
            }
            if (party != null) {
                String event = isSelf ? ClientNotify.EVENT_MEMBER_LEFT : ClientNotify.EVENT_KICKED;
                UUID finalTarget = targetUUID;
                String targetName = c.stringArg;
                MinecraftServer srv = c.player.getServer();
                c.pendingNotifications.add(() -> {
                    for (var entry : membersCopy.entrySet()) {
                        if (finalTarget != null && entry.getKey().equals(finalTarget)) continue;
                        EntityPlayerMP member = srv != null ? srv.getPlayerList().getPlayerByUUID(entry.getKey()) :
                                null;
                        if (member != null) {
                            notifyPlayer(member, event, targetName, "");
                        }
                    }
                });
            }
            return true;
        }

        private static boolean changeRole(ActionContext c) {
            String[] parts = c.stringArg.split(":", 2);
            if (parts.length != 2) return false;

            Party actorParty = c.selfProvider.getEffectiveParty(c.player.getUniqueID());
            MinecraftServer srv = c.player.getServer();
            EntityPlayerMP target = srv != null ? srv.getPlayerList().getPlayerByUsername(parts[0]) : null;
            String oldRole = (actorParty != null && target != null) ?
                    String.valueOf(actorParty.getRole(target.getUniqueID())) : null;

            if (!c.activeProvider.changeRole(c.player, parts[0], parts[1])) return false;

            if (actorParty != null && target != null && oldRole != null) {
                MinecraftForge.EVENT_BUS.post(new PartyEvent.Post.RoleChanged(
                        actorParty.getPartyId(), actorParty.getName(), target.getUniqueID(), oldRole, parts[1]));
            }
            if (srv != null && target != null) {
                String targetName = parts[0];
                String newRole = parts[1];
                c.pendingNotifications.add(() -> notifyPlayer(target, ClientNotify.EVENT_ROLE_CHANGED,
                        targetName, newRole));
            }
            return true;
        }

        private static boolean toggleBQuLink(ActionContext c) {
            boolean linked = "true".equals(c.stringArg);
            PartyManagerData pm = PartyManagerData.getInstance();
            UUID playerId = c.player.getUniqueID();

            // c.provider.getRole(), not a raw PartyManagerData lookup — a member who joined after
            // the owner's original link action has no BLPC-side Party record of their own.
            if (!c.player.canUseCommand(2, "")) {
                PartyRole effectiveRole = PartyRole.fromName(c.provider.getRole(playerId));
                if (effectiveRole == null || !effectiveRole.canInvite()) return false;
            }

            if (linked) {
                Party currentParty = pm.getPartyByPlayer(playerId);
                if (currentParty == null) return false;
                if (!c.provider.ensureNativePartyWithMembers(c.player, currentParty)) return false;
                for (UUID memberId : c.provider.getPartyMembers(playerId)) {
                    pm.setBQuLinked(memberId, true);
                }
            } else {
                if (!c.provider.isLinkedParty(playerId)) return false;
                for (UUID memberId : c.provider.getPartyMembers(playerId)) {
                    pm.setBQuLinked(memberId, false);
                }
                getOrCreateSelfParty(c.player, c.provider);
            }
            Party party = pm.getPartyByPlayer(playerId);
            if (party != null) {
                String event = linked ? ClientNotify.EVENT_BQU_LINKED : ClientNotify.EVENT_BQU_UNLINKED;
                MinecraftServer srv = c.player.getServer();
                IPartyProvider provider = c.provider;
                c.pendingNotifications.add(() -> notifyPartyMembers(provider, playerId, event, "", "", srv, null));
            }
            return true;
        }

        /** Retained for wire-protocol stability — current UI uses {@code ACTION_SET_FAKEPLAYER_TRUST}. */
        private static boolean toggleFakePlayers(ActionContext c) {
            Party party = getOrCreateSelfParty(c.player, c.activeProvider);
            if (party == null) return false;
            PartyRole role = party.getRole(c.player.getUniqueID());
            if (role == null || !role.canInvite()) return false;
            TrustLevel next = switch (party.getFakePlayerTrustLevel()) {
                case NONE -> TrustLevel.ALLY;
                case ALLY -> TrustLevel.MEMBER;
                default -> TrustLevel.NONE;
            };
            party.setFakePlayerTrustLevel(next);
            return true;
        }

        private static boolean toggleExplosionProtection(ActionContext c) {
            return onAdminParty(c, p -> {
                p.setProtectExplosions("true".equals(c.stringArg));
                return true;
            });
        }

        private static boolean updateRelation(ActionContext c, int action) {
            return onAdminParty(c, party -> {
                UUID targetPartyId;
                try {
                    targetPartyId = UUID.fromString(c.stringArg);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                if (targetPartyId.equals(party.getPartyId())) return false;
                switch (action) {
                    case PartyAction.ACTION_ADD_ALLY -> party.addAlly(targetPartyId);
                    case PartyAction.ACTION_REMOVE_ALLY -> party.removeAlly(targetPartyId);
                    case PartyAction.ACTION_ADD_ENEMY -> party.addEnemy(targetPartyId);
                    case PartyAction.ACTION_REMOVE_ENEMY -> party.removeEnemy(targetPartyId);
                }
                return true;
            });
        }

        private static boolean transferOwnership(ActionContext c) {
            Party party = getOrCreateSelfParty(c.player, c.activeProvider);
            if (party == null) return false;
            PartyRole role = party.getRole(c.player.getUniqueID());
            if (role != PartyRole.OWNER && !c.player.canUseCommand(2, "")) return false;
            MinecraftServer srv = c.player.getServer();
            if (srv == null) return false;

            UUID targetId = party.findMemberByUsername(srv, c.stringArg);
            if (targetId == null) return false;

            PartyManagerData.getInstance().setRole(party.getPartyId(), targetId, PartyRole.OWNER);
            String newOwnerName = c.stringArg;
            String senderName = c.player.getName();
            EntityPlayerMP sender = c.player;
            UUID finalTargetId = targetId;
            c.pendingNotifications.add(() -> {
                EntityPlayerMP target = srv.getPlayerList().getPlayerByUUID(finalTargetId);
                if (target != null) {
                    notifyPlayer(target, ClientNotify.EVENT_OWNER_TRANSFERRED, newOwnerName, "");
                }
                notifyPlayer(sender, ClientNotify.EVENT_ROLE_CHANGED, senderName, "ADMIN");
            });
            return true;
        }

        private static boolean setTrustLevel(ActionContext c) {
            return onAdminParty(c, party -> {
                String[] parts = c.stringArg.split(":", 2);
                if (parts.length != 2) return false;
                TrustAction ta = TrustAction.fromNbtKey(parts[0]);
                TrustLevel tl = TrustLevel.fromName(parts[1]);
                if (ta == null || tl.ordinal() > TrustLevel.MEMBER.ordinal()) return false;
                party.setTrustLevel(ta, tl);
                return true;
            });
        }

        private static boolean setFakePlayerTrust(ActionContext c) {
            return onAdminParty(c, party -> {
                TrustLevel level = TrustLevel.fromName(c.stringArg);
                if (level.ordinal() > TrustLevel.MEMBER.ordinal()) return false;
                party.setFakePlayerTrustLevel(level);
                return true;
            });
        }

        private static boolean setFreeToJoin(ActionContext c) {
            return onAdminParty(c, p -> {
                p.setFreeToJoin("true".equals(c.stringArg));
                return true;
            });
        }

        private static boolean setColor(ActionContext c) {
            return onAdminParty(c, p -> {
                try {
                    p.setColor(Integer.parseInt(c.stringArg));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
        }

        private static boolean setDescription(ActionContext c) {
            return onAdminParty(c, p -> {
                String desc = c.stringArg.trim();
                if (desc.length() > 256) desc = desc.substring(0, 256);
                p.setDescription(desc);
                return true;
            });
        }

        private static boolean setMaxMembers(ActionContext c) {
            return onAdminParty(c, p -> {
                try {
                    p.setMaxMembers(Math.min(100, Math.max(0, Integer.parseInt(c.stringArg))));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
        }

        private static boolean joinFreeParty(ActionContext c) {
            UUID joinId;
            try {
                joinId = UUID.fromString(c.stringArg);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            PartyManagerData pm = PartyManagerData.getInstance();
            if (pm.getPartyByPlayer(c.player.getUniqueID()) != null) {
                // Already in a party — likely a stale CreatePanel click; cache rollback
                // via syncToPlayer in dispatch() handles the visual recovery.
                return false;
            }
            Party party = pm.getParty(joinId);
            if (party == null || !party.isFreeToJoin()) {
                // Party was deleted or stopped accepting free joins after the client
                // cached it. Surface a generic failure toast so the click is not silent.
                notifyPlayer(c.player, ClientNotify.EVENT_JOIN_FAILED, "", "");
                return false;
            }
            if (!party.canAddMember()) {
                notifyPlayer(c.player, ClientNotify.EVENT_PARTY_FULL, "", "");
                return false;
            }
            UUID joinerId = c.player.getUniqueID();
            pm.addMember(joinId, joinerId, PartyRole.MEMBER);
            MinecraftForge.EVENT_BUS
                    .post(new PartyEvent.Post.MemberJoined(party.getPartyId(), party.getName(), joinerId));
            String joinerName = c.player.getName();
            MinecraftServer srv = c.player.getServer();
            IPartyProvider provider = c.selfProvider;
            c.pendingNotifications.add(() -> notifyPartyMembers(provider, joinerId,
                    ClientNotify.EVENT_MEMBER_JOINED, joinerName, "", srv, joinerId));
            return true;
        }

        // -- Shared helpers -----------------------------------------------------------------

        private static Party getOrCreateSelfParty(EntityPlayerMP player, IPartyProvider provider) {
            Party party = provider.getEffectiveParty(player.getUniqueID());
            if (party == null) {
                String partyName = provider.getPartyName(player.getUniqueID());
                if (partyName != null) {
                    party = PartyManagerData.getInstance().createParty(partyName, player.getUniqueID());
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

        /** Runs {@code action} against the actor's party iff they are ADMIN+ there. */
        private static boolean onAdminParty(ActionContext c, Predicate<Party> action) {
            Party party = getAdminParty(c.player, c.activeProvider);
            return party != null && action.test(party);
        }

        /**
         * Notifies every online member of {@code anyMemberId}'s party, resolved through
         * {@code provider} (not a possibly-stale BLPC {@code Party.getMembers()}) so a
         * BQu-linked party's real current membership is used. Pass {@code excludeId} to skip
         * the actor's own "you joined" / similar toast.
         */
        private static void notifyPartyMembers(IPartyProvider provider, UUID anyMemberId, String eventType,
                                               String playerName, String extra, MinecraftServer server,
                                               UUID excludeId) {
            ClientNotify packet = ClientNotify.partyEvent(eventType, playerName, extra);
            ModNetwork.broadcastToMembers(provider, anyMemberId, excludeId, server, packet);
        }

        private static void notifyPlayer(EntityPlayerMP player, String eventType, String playerName, String extra) {
            ModNetwork.INSTANCE.sendTo(ClientNotify.partyEvent(eventType, playerName, extra), player);
        }

        /** Per-request state bundle, scoped to a single {@link #dispatch}. */
        private static final class ActionContext {

            final EntityPlayerMP player;
            final String stringArg;
            final IPartyProvider provider;
            final DefaultPartyProvider selfProvider;
            final IPartyProvider activeProvider;
            final List<Runnable> pendingNotifications;

            ActionContext(EntityPlayerMP player, String stringArg, IPartyProvider provider,
                          DefaultPartyProvider selfProvider, IPartyProvider activeProvider,
                          List<Runnable> pendingNotifications) {
                this.player = player;
                this.stringArg = stringArg;
                this.provider = provider;
                this.selfProvider = selfProvider;
                this.activeProvider = activeProvider;
                this.pendingNotifications = pendingNotifications;
            }
        }
    }
}
