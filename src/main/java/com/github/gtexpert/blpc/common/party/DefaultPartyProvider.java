package com.github.gtexpert.blpc.common.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.network.message.PartySync;

/**
 * Self-managed party provider using {@link PartyManagerData}.
 * Registered as the default provider by {@code CoreModule}.
 * Used when BetterQuesting is not present, or when BQu linking is OFF.
 */
public class DefaultPartyProvider implements IPartyProvider {

    // --- Query ---

    @Override
    public boolean areInSameParty(UUID playerA, UUID playerB) {
        var data = getPartyData();
        if (data == null) return false;
        var party = data.getPartyByPlayer(playerA);
        return party != null && party.isMember(playerB);
    }

    @Override
    @Nullable
    public String getPartyName(UUID playerUUID) {
        var party = getEffectiveParty(playerUUID);
        return party != null ? party.getName() : null;
    }

    @Override
    public List<UUID> getPartyMembers(UUID playerUUID) {
        var party = getEffectiveParty(playerUUID);
        return party != null ? party.getMemberUUIDs() : Collections.emptyList();
    }

    @Override
    @Nullable
    public String getRole(UUID playerUUID) {
        var party = getEffectiveParty(playerUUID);
        if (party == null) return null;
        var role = party.getRole(playerUUID);
        return role != null ? role.name() : null;
    }

    @Override
    @Nullable
    public UUID getPartyId(UUID playerUUID) {
        var party = getEffectiveParty(playerUUID);
        return party != null ? party.getPartyId() : null;
    }

    @Override
    @Nullable
    public Party getEffectiveParty(UUID playerUUID) {
        var data = getPartyData();
        return data != null ? data.getPartyByPlayer(playerUUID) : null;
    }

    @Override
    @Nullable
    public Party findByName(String name) {
        var data = getPartyData();
        if (data == null) return null;
        return data.getAllParties().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> allPartyNames() {
        var data = getPartyData();
        if (data == null) return Collections.emptyList();
        return data.getAllParties().stream()
                .map(Party::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<Party> getAllParties() {
        var data = getPartyData();
        return data != null ? new ArrayList<>(data.getAllParties()) : Collections.emptyList();
    }

    @Override
    public List<Party> pendingInvitesFor(UUID playerUUID) {
        var data = getPartyData();
        if (data == null) return Collections.emptyList();
        List<Party> result = new ArrayList<>();
        for (Party party : data.getAllParties()) {
            if (party.hasInvite(playerUUID)) result.add(party);
        }
        return result;
    }

    @Override
    public long countClaims(UUID partyId) {
        return ChunkManagerData.getInstance().countClaimsForParty(partyId);
    }

    // --- Mutation (all delegate to PartyManagerData for reverse-index consistency) ---

    // --- Mutation ---

    @Override
    public boolean createParty(EntityPlayerMP player, String name) {
        var data = PartyManagerData.getInstance();
        if (data.getPartyByPlayer(player.getUniqueID()) != null) return false;
        data.createParty(name, player.getUniqueID());
        return true;
    }

    @Override
    public boolean disbandParty(EntityPlayerMP player) {
        var data = PartyManagerData.getInstance();
        var party = getEffectiveParty(player.getUniqueID());
        if (party == null) return false;
        var role = party.getRole(player.getUniqueID());
        if (role == null || !role.canDisband()) return false;

        data.removeParty(party.getPartyId());
        ChunkManagerData.getInstance().releaseAllMemberClaims(party.getMemberUUIDs(), player.world);
        return true;
    }

    @Override
    public boolean renameParty(EntityPlayerMP player, String newName) {
        var data = PartyManagerData.getInstance();
        var party = getEffectiveParty(player.getUniqueID());
        if (party == null) return false;
        var role = party.getRole(player.getUniqueID());
        if (role == null || !role.canEditName()) return false;
        party.setName(newName);
        return true;
    }

    @Override
    public boolean invitePlayer(EntityPlayerMP inviter, String targetUsername) {
        var data = PartyManagerData.getInstance();
        var party = getEffectiveParty(inviter.getUniqueID());
        if (party == null) return false;
        var role = party.getRole(inviter.getUniqueID());
        if (role == null || !role.canInvite()) return false;
        var server = inviter.getServer();
        if (server == null) return false;
        var target = server.getPlayerList().getPlayerByUsername(targetUsername);
        if (target == null) return false;
        var targetId = target.getUniqueID();
        if (party.isMember(targetId)) return false;
        if (data.getPartyByPlayer(targetId) != null) return false;
        party.addInvite(targetId, System.currentTimeMillis() + 300000L);
        inviter.sendMessage(new TextComponentTranslation("blpc.party.invite_sent", targetUsername));
        target.sendMessage(
                new TextComponentTranslation("blpc.party.invite_received", inviter.getName(), party.getName()));
        return true;
    }

    @Override
    public boolean acceptInvite(EntityPlayerMP player, UUID partyId) {
        var data = PartyManagerData.getInstance();
        var playerId = player.getUniqueID();
        if (data.getPartyByPlayer(playerId) != null) return false;
        var party = data.getParty(partyId);
        if (party == null) return false;
        if (!party.hasInvite(playerId)) return false;
        party.removeInvite(playerId);
        data.addMember(partyId, playerId, PartyRole.MEMBER);
        return true;
    }

    @Override
    public boolean kickOrLeave(EntityPlayerMP actor, String targetUsername) {
        var data = PartyManagerData.getInstance();
        var party = getEffectiveParty(actor.getUniqueID());
        if (party == null) return false;
        var server = actor.getServer();
        if (server == null) return false;

        UUID targetId;
        if (targetUsername.equals(actor.getName())) {
            targetId = actor.getUniqueID();
            if (party.getRole(targetId) == PartyRole.OWNER) return false;
        } else {
            var resolved = party.findMemberByUsername(server, targetUsername);
            if (resolved == null) return false;
            targetId = resolved;
            var actorRole = party.getRole(actor.getUniqueID());
            var targetRole = party.getRole(targetId);
            if (actorRole == null || targetRole == null) return false;
            if (!actorRole.canKick(targetRole)) return false;
        }

        data.removeMember(party.getPartyId(), targetId);
        return true;
    }

    @Override
    public boolean changeRole(EntityPlayerMP actor, String targetUsername, String newRole) {
        var data = PartyManagerData.getInstance();
        var party = getEffectiveParty(actor.getUniqueID());
        if (party == null) return false;
        var server = actor.getServer();
        if (server == null) return false;

        var targetId = party.findMemberByUsername(server, targetUsername);
        if (targetId == null) return false;
        var actorRole = party.getRole(actor.getUniqueID());
        if (actorRole == null || !actorRole.canChangeRole()) return false;
        PartyRole role;
        try {
            role = PartyRole.valueOf(newRole);
        } catch (IllegalArgumentException e) {
            return false;
        }
        data.setRole(party.getPartyId(), targetId, role);
        return true;
    }

    // --- Sync ---

    // --- Sync ---

    @Override
    public void syncToAll() {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        var data = PartyManagerData.getInstance();
        ModNetwork.INSTANCE.sendToAll(new PartySync(data.serializeForSync()));
    }

    @Override
    public void syncToPlayer(EntityPlayerMP player) {
        ModNetwork.INSTANCE.sendTo(new PartySync(serializeForClient()), player);
    }

    @Override
    public NBTTagCompound serializeForClient() {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return new NBTTagCompound();
        return PartyManagerData.getInstance().serializeForSync();
    }

    @Nullable
    private static PartyManagerData getPartyData() {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null ? PartyManagerData.getInstance() : null;
    }
}
