package com.github.gtexpert.blpc.common.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
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
        PartyManagerData data = getPartyData();
        if (data == null) return false;
        Party party = data.getPartyByPlayer(playerA);
        return party != null && party.isMember(playerB);
    }

    @Override
    @Nullable
    public String getPartyName(UUID playerUUID) {
        Party party = getEffectiveParty(playerUUID);
        return party != null ? party.getName() : null;
    }

    @Override
    public List<UUID> getPartyMembers(UUID playerUUID) {
        Party party = getEffectiveParty(playerUUID);
        return party != null ? party.getMemberUUIDs() : Collections.emptyList();
    }

    @Override
    @Nullable
    public String getRole(UUID playerUUID) {
        Party party = getEffectiveParty(playerUUID);
        if (party == null) return null;
        PartyRole role = party.getRole(playerUUID);
        return role != null ? role.name() : null;
    }

    @Override
    @Nullable
    public UUID getPartyId(UUID playerUUID) {
        Party party = getEffectiveParty(playerUUID);
        return party != null ? party.getPartyId() : null;
    }

    @Override
    @Nullable
    public Party getEffectiveParty(UUID playerUUID) {
        PartyManagerData data = getPartyData();
        return data != null ? data.getPartyByPlayer(playerUUID) : null;
    }

    @Override
    @Nullable
    public Party findByName(String name) {
        PartyManagerData data = getPartyData();
        if (data == null) return null;
        return data.getAllParties().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> allPartyNames() {
        PartyManagerData data = getPartyData();
        if (data == null) return Collections.emptyList();
        return data.getAllParties().stream()
                .map(Party::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<Party> getAllParties() {
        PartyManagerData data = getPartyData();
        return data != null ? new ArrayList<>(data.getAllParties()) : Collections.emptyList();
    }

    @Override
    public List<Party> pendingInvitesFor(UUID playerUUID) {
        PartyManagerData data = getPartyData();
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
        PartyManagerData data = PartyManagerData.getInstance();
        if (data.getPartyByPlayer(player.getUniqueID()) != null) return false;
        data.createParty(name, player.getUniqueID());
        return true;
    }

    @Override
    public boolean disbandParty(EntityPlayerMP player) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = getEffectiveParty(player.getUniqueID());
        if (party == null) return false;
        PartyRole role = party.getRole(player.getUniqueID());
        if (role == null || !role.canDisband()) return false;

        data.removeParty(party.getPartyId());
        ChunkManagerData.getInstance().releaseAllMemberClaims(party.getMemberUUIDs(), player.world);
        return true;
    }

    @Override
    public boolean renameParty(EntityPlayerMP player, String newName) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = getEffectiveParty(player.getUniqueID());
        if (party == null) return false;
        PartyRole role = party.getRole(player.getUniqueID());
        if (role == null || !role.canEditName()) return false;
        party.setName(newName);
        return true;
    }

    @Override
    public boolean invitePlayer(EntityPlayerMP inviter, String targetUsername) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = getEffectiveParty(inviter.getUniqueID());
        if (party == null) return false;
        PartyRole role = party.getRole(inviter.getUniqueID());
        if (role == null || !role.canInvite()) return false;
        MinecraftServer server = inviter.getServer();
        if (server == null) return false;
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetUsername);
        if (target == null) return false;
        UUID targetId = target.getUniqueID();
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
        PartyManagerData data = PartyManagerData.getInstance();
        UUID playerId = player.getUniqueID();
        if (data.getPartyByPlayer(playerId) != null) return false;
        Party party = data.getParty(partyId);
        if (party == null) return false;
        if (!party.hasInvite(playerId)) return false;
        party.removeInvite(playerId);
        data.addMember(partyId, playerId, PartyRole.MEMBER);
        return true;
    }

    @Override
    public boolean kickOrLeave(EntityPlayerMP actor, String targetUsername) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = getEffectiveParty(actor.getUniqueID());
        if (party == null) return false;
        MinecraftServer server = actor.getServer();
        if (server == null) return false;

        UUID targetId;
        if (targetUsername.equals(actor.getName())) {
            targetId = actor.getUniqueID();
            if (party.getRole(targetId) == PartyRole.OWNER) return false;
        } else {
            UUID resolved = party.findMemberByUsername(server, targetUsername);
            if (resolved == null) return false;
            targetId = resolved;
            PartyRole actorRole = party.getRole(actor.getUniqueID());
            PartyRole targetRole = party.getRole(targetId);
            if (actorRole == null || targetRole == null) return false;
            if (!actorRole.canKick(targetRole)) return false;
        }

        data.removeMember(party.getPartyId(), targetId);
        return true;
    }

    @Override
    public boolean changeRole(EntityPlayerMP actor, String targetUsername, String newRole) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = getEffectiveParty(actor.getUniqueID());
        if (party == null) return false;
        MinecraftServer server = actor.getServer();
        if (server == null) return false;

        UUID targetId = party.findMemberByUsername(server, targetUsername);
        if (targetId == null) return false;
        PartyRole actorRole = party.getRole(actor.getUniqueID());
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
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        PartyManagerData data = PartyManagerData.getInstance();
        ModNetwork.INSTANCE.sendToAll(new PartySync(data.serializeForSync()));
    }

    @Override
    public void syncToPlayer(EntityPlayerMP player) {
        ModNetwork.INSTANCE.sendTo(new PartySync(serializeForClient()), player);
    }

    @Override
    public NBTTagCompound serializeForClient() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return new NBTTagCompound();
        return PartyManagerData.getInstance().serializeForSync();
    }

    @Nullable
    private static PartyManagerData getPartyData() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null ? PartyManagerData.getInstance() : null;
    }
}
