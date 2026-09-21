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
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.network.message.PartySync;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

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
public class BQuPartyProvider implements IPartyProvider {

    private final DefaultPartyProvider fallback = new DefaultPartyProvider();

    @Override
    public boolean areInSameParty(UUID playerA, UUID playerB) {
        try {
            for (var entry : QuestingAPI.getAPI(ApiReference.PARTY_DB).getEntries()) {
                IParty party = entry.getValue();
                if (party == null) continue;
                if (party.getStatus(playerA) != null && party.getStatus(playerB) != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            ModLog.BQU.debug("BQu party DB lookup failed in areInSameParty", e);
        }
        return fallback.areInSameParty(playerA, playerB);
    }

    @Override
    @Nullable
    public String getPartyName(UUID playerUUID) {
        var entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) return entry.getValue().getProperties().getProperty(NativeProps.NAME);
        return fallback.getPartyName(playerUUID);
    }

    @Override
    public List<UUID> getPartyMembers(UUID playerUUID) {
        var entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) return new ArrayList<>(entry.getValue().getMembers());
        return fallback.getPartyMembers(playerUUID);
    }

    @Override
    @Nullable
    public String getRole(UUID playerUUID) {
        var entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) {
            EnumPartyStatus status = entry.getValue().getStatus(playerUUID);
            return status != null ? status.name() : null;
        }
        return fallback.getRole(playerUUID);
    }

    /**
     * Derived from BQu's own integer party id, so it's identical for every member regardless of
     * whether any of them has ever had a BLPC-side {@link Party} record created (unlike
     * {@link #serializeForClient}'s display id, which prefers a member's self-managed party id
     * when one happens to exist).
     */
    @Override
    @Nullable
    public UUID getPartyId(UUID playerUUID) {
        var entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) return Party.uuidFromIntId(entry.getID());
        return fallback.getPartyId(playerUUID);
    }

    /**
     * Looks up the player's BQu party directly (O(1) via BQu's own player index) rather than
     * scanning every party like {@link #serializeForClient}, since this is called from hot
     * per-action checks (block break/interact, claim limits, chunk transit).
     */
    @Override
    @Nullable
    public Party getEffectiveParty(UUID playerUUID) {
        var entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry != null) return buildMergedParty(entry);
        return fallback.getEffectiveParty(playerUUID);
    }

    @Override
    public boolean hasNativeParty(UUID playerUUID) {
        return PartyManager.INSTANCE.getParty(playerUUID) != null;
    }

    @Override
    @Nullable
    public Party findByName(String name) {
        return fallback.findByName(name);
    }

    @Override
    public List<String> allPartyNames() {
        return fallback.allPartyNames();
    }

    @Override
    public List<Party> getAllParties() {
        return fallback.getAllParties();
    }

    @Override
    public List<Party> pendingInvitesFor(UUID playerUUID) {
        return fallback.pendingInvitesFor(playerUUID);
    }

    /**
     * Checks the player's <em>current</em> BQu party membership for any linked member, rather
     * than a per-player flag — a member who joined this same BQu party after the owner's link
     * action (typically through BQu's own party screen) is recognized without requiring the flag
     * to be separately propagated to them.
     */
    @Override
    public boolean isLinkedParty(UUID playerUUID) {
        var entry = PartyManager.INSTANCE.getParty(playerUUID);
        if (entry == null) return false;
        PartyManagerData pmData = PartyManagerData.getInstance();
        for (UUID memberId : entry.getValue().getMembers()) {
            if (pmData.isBQuLinked(memberId)) return true;
        }
        return false;
    }

    @Override
    public boolean ensureNativePartyWithMembers(EntityPlayerMP owner, Party blpcParty) {
        UUID ownerId = QuestingAPI.getQuestingUUID(owner);
        var entry = PartyManager.INSTANCE.getParty(ownerId);

        if (entry == null) {
            int partyId = PartyManager.INSTANCE.nextID();
            IParty bqParty = PartyManager.INSTANCE.createNew(partyId);
            bqParty.getProperties().setProperty(NativeProps.NAME, blpcParty.getName());
            bqParty.setStatus(ownerId, EnumPartyStatus.OWNER);
            for (var member : blpcParty.getMembers().entrySet()) {
                if (member.getKey().equals(ownerId)) continue;
                bqParty.setStatus(member.getKey(), mapToBQuRole(member.getValue()));
            }
            NetPartySync.sendSync(null, new int[] { partyId });
            return true;
        }

        IParty bqParty = entry.getValue();
        for (var member : blpcParty.getMembers().entrySet()) {
            UUID memberId = member.getKey();
            if (bqParty.getStatus(memberId) == null) {
                bqParty.setStatus(memberId, mapToBQuRole(member.getValue()));
            }
        }
        NetPartySync.quickSync(entry.getID());
        return true;
    }

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
        var entry = PartyManager.INSTANCE.getParty(playerId);

        if (entry == null) {
            return fallback.disbandParty(player);
        }

        IParty party = entry.getValue();
        EnumPartyStatus status = party.getStatus(playerId);
        if (status != EnumPartyStatus.OWNER && !player.canUseCommand(2, "")) return false;

        List<UUID> members = new ArrayList<>(party.getMembers());
        ChunkManagerData.getInstance().releaseAllMemberClaims(members, player.world);

        PartyManagerData pmData = PartyManagerData.getInstance();
        for (UUID memberId : members) {
            pmData.setBQuLinked(memberId, false);
        }
        BLPCSaveHandler.INSTANCE.markDirty();

        PartyManager.INSTANCE.removeID(entry.getID());
        PartyInvitations.INSTANCE.purgeInvites(entry.getID());
        // fallback.disbandParty also calls releaseAllClaims, but the second pass is a no-op
        // since claims were already released above. This is intentional to ensure the
        // self-managed party is properly removed from PartyManagerData.
        fallback.disbandParty(player);
        NetPartySync.sendSync(null, null);
        return true;
    }

    @Override
    public boolean renameParty(EntityPlayerMP player, String newName) {
        UUID playerId = QuestingAPI.getQuestingUUID(player);
        var entry = PartyManager.INSTANCE.getParty(playerId);

        if (entry == null) return fallback.renameParty(player, newName);

        EnumPartyStatus status = entry.getValue().getStatus(playerId);
        PartyRole role = BQuPartyProvider.mapRole(status);
        if (!role.canEditName() && !player.canUseCommand(2, "")) return false;

        entry.getValue().getProperties().setProperty(NativeProps.NAME, newName);
        NetPartySync.quickSync(entry.getID());
        return true;
    }

    @Override
    public boolean invitePlayer(EntityPlayerMP inviter, String targetUsername) {
        UUID inviterId = QuestingAPI.getQuestingUUID(inviter);
        var entry = PartyManager.INSTANCE.getParty(inviterId);

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
    public boolean acceptInvite(EntityPlayerMP player, UUID partyId) {
        int bquId = findBQuIntId(partyId);
        if (bquId < 0) return fallback.acceptInvite(player, partyId);
        IParty party = PartyManager.INSTANCE.getValue(bquId);
        if (party == null) return fallback.acceptInvite(player, partyId);

        UUID playerId = QuestingAPI.getQuestingUUID(player);
        if (PartyManager.INSTANCE.getParty(playerId) != null) return false;

        boolean accepted = PartyInvitations.INSTANCE.acceptInvite(playerId, bquId);
        if (accepted) {
            NetPartySync.quickSync(bquId);
            fallback.acceptInvite(player, partyId);
        }
        return accepted;
    }

    private int findBQuIntId(UUID partyId) {
        for (var entry : PartyManager.INSTANCE.getEntries()) {
            if (Party.uuidFromIntId(entry.getID()).equals(partyId)) {
                return entry.getID();
            }
        }
        return -1;
    }

    @Override
    public boolean kickOrLeave(EntityPlayerMP actor, String targetUsername) {
        UUID actorId = QuestingAPI.getQuestingUUID(actor);
        var entry = PartyManager.INSTANCE.getParty(actorId);

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
        var entry = PartyManager.INSTANCE.getParty(actorId);

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

    @Override
    public long countClaims(UUID partyId) {
        return ChunkManagerData.getInstance().countClaimsForParty(partyId);
    }

    @Override
    public void syncToAll() {
        NetPartySync.sendSync(null, null);
        autoUnlinkOrphanedPlayers();
        NBTTagCompound syncData = serializeForClient();
        ModNetwork.INSTANCE.sendToAll(new PartySync(syncData));
    }

    @Override
    public void syncToPlayer(EntityPlayerMP player) {
        ModNetwork.INSTANCE.sendTo(new PartySync(serializeForClient()), player);
    }

    /**
     * Merges one BQu party's live membership with settings/relations copied from whichever
     * member happens to have a BLPC-side {@link Party} record (preferring the BQu owner's, so
     * the owner's protection settings win if members disagree). Shared by
     * {@link #serializeForClient} (client display) and {@link #getEffectiveParty} (server-side
     * authoritative checks) so both stay in sync as this logic evolves.
     */
    private Party buildMergedParty(DBEntry<IParty> entry) {
        IParty bqParty = entry.getValue();
        PartyManagerData pmData = PartyManagerData.getInstance();

        UUID blpcPartyId = null;
        Party ownerSelfParty = null;
        Party fallbackSelfParty = null;
        for (UUID memberId : bqParty.getMembers()) {
            Party selfParty = pmData.getPartyByPlayer(memberId);
            if (selfParty != null) {
                if (blpcPartyId == null) blpcPartyId = selfParty.getPartyId();
                EnumPartyStatus status = bqParty.getStatus(memberId);
                if (status == EnumPartyStatus.OWNER) {
                    ownerSelfParty = selfParty;
                    blpcPartyId = selfParty.getPartyId();
                } else if (fallbackSelfParty == null) {
                    fallbackSelfParty = selfParty;
                }
            }
        }
        if (blpcPartyId == null) blpcPartyId = Party.uuidFromIntId(entry.getID());

        String bqName = bqParty.getProperties().getProperty(NativeProps.NAME);
        if (bqName == null) bqName = "Party " + blpcPartyId.toString().substring(0, 8);
        Party party = new Party(blpcPartyId, bqName, 0L);
        for (UUID memberId : bqParty.getMembers()) {
            party.addMember(memberId, mapRole(bqParty.getStatus(memberId)));
        }
        Party sourceSelfParty = ownerSelfParty != null ? ownerSelfParty : fallbackSelfParty;
        if (sourceSelfParty != null) {
            party.copySettingsFrom(sourceSelfParty);
        }
        return party;
    }

    @Override
    public NBTTagCompound serializeForClient() {
        NBTTagList list = new NBTTagList();
        Set<UUID> bquMembers = new HashSet<>();

        PartyManagerData pmData = PartyManagerData.getInstance();
        for (var entry : PartyManager.INSTANCE.getEntries()) {
            IParty bqParty = entry.getValue();
            if (bqParty == null) continue;
            if (bqParty.getMembers().isEmpty()) continue;

            boolean hasLinkedMember = false;
            for (UUID memberId : bqParty.getMembers()) {
                if (pmData.isBQuLinked(memberId)) {
                    hasLinkedMember = true;
                    break;
                }
            }
            if (!hasLinkedMember) continue;

            Party party = buildMergedParty(entry);
            bquMembers.addAll(bqParty.getMembers());
            party.resolvePlayerNames(PartyManagerData.getInstance()::getParty);
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

        // Built from bquMembers (live BQu membership of every linked party), not the raw
        // bquLinkedPlayers flag set — a member who joined a linked party after the owner's
        // link action has no flag of their own but is still genuinely linked. Included even
        // when empty so the client clears stale bquLinked state.
        NBTTagList bquLinkedList = new NBTTagList();
        for (UUID memberId : bquMembers) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("uuid", memberId);
            bquLinkedList.appendTag(tag);
        }
        root.setTag("bquLinked", bquLinkedList);

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

    static EnumPartyStatus mapToBQuRole(PartyRole role) {
        return switch (role) {
            case OWNER -> EnumPartyStatus.OWNER;
            case ADMIN -> EnumPartyStatus.ADMIN;
            case MEMBER -> EnumPartyStatus.MEMBER;
        };
    }

    static PartyRole mapRole(EnumPartyStatus status) {
        if (status == null) return PartyRole.MEMBER;
        return switch (status) {
            case OWNER -> PartyRole.OWNER;
            case ADMIN -> PartyRole.ADMIN;
            default -> PartyRole.MEMBER;
        };
    }
}
