package com.github.gtexpert.blpc.integration.bqu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.network.MessagePartySync;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.PartyRole;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumPartyStatus;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.network.handlers.NetPartySync;
import betterquesting.questing.party.PartyInvitations;
import betterquesting.questing.party.PartyManager;

/**
 * BetterQuesting party provider. Delegates to BQu's {@code PartyManager} API.
 * <p>
 * All query methods follow a fallback chain: BQu first, then self-managed ({@link DefaultPartyProvider}).
 * Mutation methods operate on BQu when the player has a BQu party, otherwise fall back to self-managed.
 * Mutations also mirror to self-managed data to preserve chunk claim associations if BQu party is later deleted.
 */
public class BQPartyProvider implements IPartyProvider {

    private final DefaultPartyProvider fallback = new DefaultPartyProvider();

    // --- Query: BQu first, fallback to self-managed ---

    @Override
    public boolean areInSameParty(UUID playerA, UUID playerB) {
        try {
            for (DBEntry<IParty> entry : QuestingAPI.getAPI(ApiReference.PARTY_DB).getEntries()) {
                IParty party = entry.getValue();
                if (party == null) continue;
                if (party.getStatus(playerA) != null && party.getStatus(playerB) != null) {
                    return true;
                }
            }
        } catch (Exception e) {}
        return fallback.areInSameParty(playerA, playerB);
    }

    @Override
    @Nullable
    public String getPartyName(UUID playerUUID) {
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) return entry.getValue().getProperties().getProperty(NativeProps.NAME);
        return fallback.getPartyName(playerUUID);
    }

    @Override
    public List<UUID> getPartyMembers(UUID playerUUID) {
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) return new ArrayList<>(entry.getValue().getMembers());
        return fallback.getPartyMembers(playerUUID);
    }

    @Override
    @Nullable
    public String getRole(UUID playerUUID) {
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) {
            EnumPartyStatus status = entry.getValue().getStatus(playerUUID);
            return status != null ? status.name() : null;
        }
        return fallback.getRole(playerUUID);
    }

    @Override
    public boolean hasNativeParty(UUID playerUUID) {
        return PartyManager.INSTANCE.getParty(playerUUID) != null;
    }

    // --- Mutation: BQu first (by player UUID), fallback to self-managed ---

    @Override
    public boolean createParty(EntityPlayerMP player, String name) {
        UUID playerId = QuestingAPI.getQuestingUUID(player);
        if (PartyManager.INSTANCE.getParty(playerId) != null) return false;

        int partyId = PartyManager.INSTANCE.nextID();
        IParty party = PartyManager.INSTANCE.createNew(partyId);
        party.getProperties().setProperty(NativeProps.NAME, name);
        party.setStatus(playerId, EnumPartyStatus.OWNER);
        NetPartySync.sendSync(new EntityPlayerMP[] { player }, new int[] { partyId });
        fallback.createParty(player, name);
        return true;
    }

    @Override
    public boolean disbandParty(EntityPlayerMP player) {
        UUID playerId = QuestingAPI.getQuestingUUID(player);
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(playerId);

        if (entry == null) {
            return fallback.disbandParty(player);
        }

        IParty party = entry.getValue();
        EnumPartyStatus status = party.getStatus(playerId);
        if (status != EnumPartyStatus.OWNER && !player.canUseCommand(2, "")) return false;

        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        for (UUID memberId : party.getMembers()) {
            chunkData.releaseAllClaims(memberId, player.world);
        }

        PartyManagerData pmData = PartyManagerData.getInstance();
        for (UUID memberId : party.getMembers()) {
            pmData.setBQuLinked(memberId, false);
        }
        BLPCSaveHandler.INSTANCE.saveConfig(pmData);

        PartyManager.INSTANCE.removeID(entry.getID());
        PartyInvitations.INSTANCE.purgeInvites(entry.getID());
        fallback.disbandParty(player);
        NetPartySync.sendSync(null, null);
        return true;
    }

    @Override
    public boolean renameParty(EntityPlayerMP player, String newName) {
        UUID playerId = QuestingAPI.getQuestingUUID(player);
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(playerId);

        if (entry == null) return fallback.renameParty(player, newName);

        EnumPartyStatus status = entry.getValue().getStatus(playerId);
        if (status != EnumPartyStatus.OWNER && !player.canUseCommand(2, "")) return false;

        entry.getValue().getProperties().setProperty(NativeProps.NAME, newName);
        NetPartySync.quickSync(entry.getID());
        return true;
    }

    @Override
    public boolean invitePlayer(EntityPlayerMP inviter, String targetUsername) {
        UUID inviterId = QuestingAPI.getQuestingUUID(inviter);
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(inviterId);

        if (entry == null) return fallback.invitePlayer(inviter, targetUsername);

        EnumPartyStatus status = entry.getValue().getStatus(inviterId);
        if (status == null || status.ordinal() < EnumPartyStatus.ADMIN.ordinal()) {
            if (!inviter.canUseCommand(2, "")) return false;
        }

        MinecraftServer server = inviter.getServer();
        if (server == null) return false;
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetUsername);
        if (target == null) return false;
        UUID targetId = QuestingAPI.getQuestingUUID(target);
        if (entry.getValue().getStatus(targetId) != null) return false;
        if (PartyManager.INSTANCE.getParty(targetId) != null) return false;

        PartyInvitations.INSTANCE.postInvite(targetId, entry.getID(), 300000L);
        NetPartySync.sendSync(new EntityPlayerMP[] { target }, new int[] { entry.getID() });
        return true;
    }

    @Override
    public boolean acceptInvite(EntityPlayerMP player, int partyId) {
        IParty party = PartyManager.INSTANCE.getValue(partyId);
        if (party == null) return fallback.acceptInvite(player, partyId);

        UUID playerId = QuestingAPI.getQuestingUUID(player);
        if (PartyManager.INSTANCE.getParty(playerId) != null) return false;

        boolean accepted = PartyInvitations.INSTANCE.acceptInvite(playerId, partyId);
        if (accepted) {
            NetPartySync.quickSync(partyId);
            fallback.acceptInvite(player, partyId);
        }
        return accepted;
    }

    @Override
    public boolean kickOrLeave(EntityPlayerMP actor, String targetUsername) {
        UUID actorId = QuestingAPI.getQuestingUUID(actor);
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(actorId);

        if (entry == null) return fallback.kickOrLeave(actor, targetUsername);

        MinecraftServer server = actor.getServer();
        if (server == null) return false;

        IParty party = entry.getValue();
        if (targetUsername.equals(actor.getName())) {
            if (party.getStatus(actorId) == EnumPartyStatus.OWNER) return false;
            party.kickUser(actorId);
        } else {
            EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetUsername);
            if (target == null) return false;
            UUID targetId = QuestingAPI.getQuestingUUID(target);

            EnumPartyStatus actorStatus = party.getStatus(actorId);
            EnumPartyStatus targetStatus = party.getStatus(targetId);
            if (actorStatus == null || targetStatus == null) return false;
            if (!actor.canUseCommand(2, "") && actorStatus.ordinal() <= targetStatus.ordinal()) return false;

            party.kickUser(targetId);
        }

        NetPartySync.quickSync(entry.getID());
        return true;
    }

    @Override
    public boolean changeRole(EntityPlayerMP actor, String targetUsername, String newRole) {
        UUID actorId = QuestingAPI.getQuestingUUID(actor);
        DBEntry<IParty> entry = PartyManager.INSTANCE.getParty(actorId);

        if (entry == null) return fallback.changeRole(actor, targetUsername, newRole);

        EnumPartyStatus actorStatus = entry.getValue().getStatus(actorId);
        if (actorStatus != EnumPartyStatus.OWNER && !actor.canUseCommand(2, "")) return false;

        MinecraftServer server = actor.getServer();
        if (server == null) return false;
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetUsername);
        if (target == null) return false;
        UUID targetId = QuestingAPI.getQuestingUUID(target);
        if (entry.getValue().getStatus(targetId) == null) return false;

        EnumPartyStatus role;
        try {
            role = EnumPartyStatus.valueOf(newRole);
        } catch (IllegalArgumentException e) {
            return false;
        }

        entry.getValue().setStatus(targetId, role);
        NetPartySync.quickSync(entry.getID());
        return true;
    }

    // --- Sync ---

    @Override
    public void syncToAll() {
        NetPartySync.sendSync(null, null);
        autoUnlinkOrphanedPlayers();
        NBTTagCompound syncData = serializeForClient();
        ModNetwork.INSTANCE.sendToAll(new MessagePartySync(syncData));
    }

    @Override
    public NBTTagCompound serializeForClient() {
        NBTTagList list = new NBTTagList();
        Set<UUID> bquMembers = new HashSet<>();

        PartyManagerData pmData = PartyManagerData.getInstance();
        for (DBEntry<IParty> entry : PartyManager.INSTANCE.getEntries()) {
            IParty bqParty = entry.getValue();
            if (bqParty == null) continue;
            if (bqParty.getMembers().isEmpty()) continue;
            Party party = new Party(entry.getID(),
                    bqParty.getProperties().getProperty(NativeProps.NAME),
                    0L);
            boolean hasLinkedMember = false;
            for (UUID memberId : bqParty.getMembers()) {
                // Only include bquLinked members in BLPC's party view
                if (!pmData.isBQuLinked(memberId)) continue;
                EnumPartyStatus status = bqParty.getStatus(memberId);
                party.addMember(memberId, mapRole(status));
                bquMembers.add(memberId);
                hasLinkedMember = true;
            }
            if (!hasLinkedMember) continue;
            // Copy BLPC settings from self-managed party (prefer owner's party)
            Party ownerSelfParty = null;
            Party fallbackSelfParty = null;
            for (UUID memberId : party.getMemberUUIDs()) {
                Party selfParty = pmData.getPartyByPlayer(memberId);
                if (selfParty != null) {
                    if (party.getRole(memberId) == PartyRole.OWNER) {
                        ownerSelfParty = selfParty;
                        break;
                    }
                    if (fallbackSelfParty == null) {
                        fallbackSelfParty = selfParty;
                    }
                }
            }
            Party sourceSelfParty = ownerSelfParty != null ? ownerSelfParty : fallbackSelfParty;
            if (sourceSelfParty != null) {
                party.setTitle(sourceSelfParty.getTitle());
                party.setDescription(sourceSelfParty.getDescription());
                party.setColor(sourceSelfParty.getColor());
                party.setFreeToJoin(sourceSelfParty.isFreeToJoin());
                party.setFakePlayerTrustLevel(sourceSelfParty.getFakePlayerTrustLevel());
                party.setProtectExplosions(sourceSelfParty.protectsExplosions());
                for (com.github.gtexpert.blpc.common.party.TrustAction ta : com.github.gtexpert.blpc.common.party.TrustAction
                        .values()) {
                    party.setTrustLevel(ta, sourceSelfParty.getTrustLevel(ta));
                }
                for (UUID allyId : sourceSelfParty.getAllies()) {
                    party.addAlly(allyId);
                }
                for (UUID enemyId : sourceSelfParty.getEnemies()) {
                    party.addEnemy(enemyId);
                }
            }
            party.resolvePlayerNames();
            list.appendTag(party.toSyncNBT());
        }

        NBTTagCompound selfData = fallback.serializeForClient();
        NBTTagList selfList = selfData.getTagList("parties", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < selfList.tagCount(); i++) {
            Party selfParty = Party.fromNBT(selfList.getCompoundTagAt(i));
            boolean hasNonBQuMember = false;
            for (UUID memberId : selfParty.getMemberUUIDs()) {
                if (!bquMembers.contains(memberId)) {
                    hasNonBQuMember = true;
                    break;
                }
            }
            if (hasNonBQuMember) {
                list.appendTag(selfParty.toSyncNBT());
            }
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setTag("parties", list);

        // Always include bquLinked flags (even if empty) so client clears stale state
        root.setTag("bquLinked", selfData.getTagList("bquLinked", Constants.NBT.TAG_COMPOUND));

        return root;
    }

    private void autoUnlinkOrphanedPlayers() {
        PartyManagerData data = PartyManagerData.getInstance();
        for (UUID playerId : new ArrayList<>(data.getBQuLinkedPlayers())) {
            if (PartyManager.INSTANCE.getParty(playerId) == null) {
                data.setBQuLinked(playerId, false);
            }
        }
    }

    static PartyRole mapRole(EnumPartyStatus status) {
        if (status == null) return PartyRole.MEMBER;
        switch (status) {
            case OWNER:
                return PartyRole.OWNER;
            case ADMIN:
                return PartyRole.ADMIN;
            default:
                return PartyRole.MEMBER;
        }
    }
}
