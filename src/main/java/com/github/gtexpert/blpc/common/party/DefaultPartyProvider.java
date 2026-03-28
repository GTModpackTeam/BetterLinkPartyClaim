package com.github.gtexpert.blpc.common.party;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.network.MessagePartySync;
import com.github.gtexpert.blpc.common.network.ModNetwork;

/**
 * Self-managed party provider using {@link PartyManagerData}.
 * Registered as the default provider by {@code CoreModule}.
 * Used when BetterQuesting is not present, or when BQu linking is OFF.
 */
public class DefaultPartyProvider implements IPartyProvider {

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
        PartyManagerData data = getPartyData();
        if (data == null) return null;
        Party party = data.getPartyByPlayer(playerUUID);
        return party != null ? party.getName() : null;
    }

    @Override
    public List<UUID> getPartyMembers(UUID playerUUID) {
        PartyManagerData data = getPartyData();
        if (data == null) return Collections.emptyList();
        Party party = data.getPartyByPlayer(playerUUID);
        return party != null ? party.getMemberUUIDs() : Collections.emptyList();
    }

    @Override
    @Nullable
    public String getRole(UUID playerUUID) {
        PartyManagerData data = getPartyData();
        if (data == null) return null;
        Party party = data.getPartyByPlayer(playerUUID);
        if (party == null) return null;
        PartyRole role = party.getRole(playerUUID);
        return role != null ? role.name() : null;
    }

    @Override
    public boolean createParty(EntityPlayerMP player, String name) {
        PartyManagerData data = PartyManagerData.getInstance();
        UUID playerId = player.getUniqueID();
        if (data.getPartyByPlayer(playerId) != null) return false;
        data.createParty(name, playerId);
        return true;
    }

    @Override
    public boolean disbandParty(EntityPlayerMP player) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = data.getPartyByPlayer(player.getUniqueID());
        if (party == null) return false;

        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        for (UUID memberId : party.getMemberUUIDs()) {
            chunkData.releaseAllClaims(memberId, player.world);
        }

        data.removeParty(party.getPartyId());
        return true;
    }

    @Override
    public boolean renameParty(EntityPlayerMP player, String newName) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = data.getPartyByPlayer(player.getUniqueID());
        if (party == null) return false;
        party.setName(newName);
        return true;
    }

    @Override
    public boolean invitePlayer(EntityPlayerMP inviter, String targetUsername) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = data.getPartyByPlayer(inviter.getUniqueID());
        if (party == null) return false;
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
    public boolean acceptInvite(EntityPlayerMP player, int partyId) {
        PartyManagerData data = PartyManagerData.getInstance();
        UUID playerId = player.getUniqueID();
        if (data.getPartyByPlayer(playerId) != null) return false;
        Party party = data.getParty(partyId);
        if (party == null) return false;
        if (!party.hasInvite(playerId)) return false;
        party.removeInvite(playerId);
        party.addMember(playerId, PartyRole.MEMBER);
        return true;
    }

    @Override
    public boolean kickOrLeave(EntityPlayerMP actor, String targetUsername) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = data.getPartyByPlayer(actor.getUniqueID());
        if (party == null) return false;
        MinecraftServer server = actor.getServer();
        if (server == null) return false;

        UUID targetId;
        if (targetUsername.equals(actor.getName())) {
            targetId = actor.getUniqueID();
            if (party.getRole(targetId) == PartyRole.OWNER) return false;
        } else {
            EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetUsername);
            if (target == null) return false;
            targetId = target.getUniqueID();
            PartyRole actorRole = party.getRole(actor.getUniqueID());
            PartyRole targetRole = party.getRole(targetId);
            if (actorRole == null || targetRole == null) return false;
            if (!actorRole.canKick(targetRole)) return false;
        }

        party.removeMember(targetId);
        return true;
    }

    @Override
    public boolean changeRole(EntityPlayerMP actor, String targetUsername, String newRole) {
        PartyManagerData data = PartyManagerData.getInstance();
        Party party = data.getPartyByPlayer(actor.getUniqueID());
        if (party == null) return false;
        MinecraftServer server = actor.getServer();
        if (server == null) return false;
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetUsername);
        if (target == null) return false;
        if (!party.isMember(target.getUniqueID())) return false;
        PartyRole actorRole = party.getRole(actor.getUniqueID());
        if (actorRole == null || !actorRole.canChangeRole()) return false;
        PartyRole role;
        try {
            role = PartyRole.valueOf(newRole);
        } catch (IllegalArgumentException e) {
            return false;
        }
        party.setRole(target.getUniqueID(), role);
        return true;
    }

    @Override
    public void syncToAll() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        PartyManagerData data = PartyManagerData.getInstance();
        ModNetwork.INSTANCE.sendToAll(new MessagePartySync(data.serializeForSync()));
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
        if (server == null) return null;
        return PartyManagerData.getInstance();
    }
}
