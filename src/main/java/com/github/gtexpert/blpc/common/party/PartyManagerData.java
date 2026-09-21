package com.github.gtexpert.blpc.common.party;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.common.network.message.PartyAction;

/**
 * Server-side authoritative party storage.
 * <p>
 * Parties are persisted by {@link com.github.gtexpert.blpc.common.BLPCSaveHandler}
 * to {@code world/betterlink/pc/parties/&lt;id&gt;.dat}. The {@code bquLinkedPlayers}
 * set tracks which players have opted into BetterQuesting integration; lookup
 * via {@link #isBQuLinked(UUID)} drives provider selection in
 * {@link PartyAction.Handler}.
 * <p>
 * The {@code migrated} flag records whether legacy (pre-FTB-Lib-layout) data
 * has already been imported, so migration is a one-time operation.
 * <p>
 * <b>Performance:</b> {@link #getPartyByPlayer} is O(1) thanks to the
 * {@link #playerToPartyId} reverse index maintained on every mutation.
 */
public class PartyManagerData {

    private static volatile PartyManagerData instance;

    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    /** Player UUID → party ID reverse index for O(1) lookup. */
    private final Map<UUID, UUID> playerToPartyId = new ConcurrentHashMap<>();
    private boolean migrated;
    private final Set<UUID> bquLinkedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static synchronized PartyManagerData getInstance() {
        if (instance == null) {
            instance = new PartyManagerData();
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = new PartyManagerData();
    }

    // --- CRUD ---

    /**
     * Registers all member-to-party mappings for a party.
     * Must be called after {@code party.addMember()} but before the party
     * is visible to queries (i.e. before {@code parties.put()}).
     */
    private void registerMembers(Party party, Map<UUID, PartyRole> members) {
        for (var entry : members.entrySet()) {
            playerToPartyId.put(entry.getKey(), party.getPartyId());
        }
    }

    /** Removes a single member from the reverse index. */
    private void unregisterMember(UUID playerUUID) {
        playerToPartyId.remove(playerUUID);
    }

    /** Removes all members of a party from the reverse index. */
    private void unregisterAllMembers(Party party) {
        for (var entry : party.getMembers().entrySet()) {
            playerToPartyId.remove(entry.getKey());
        }
    }

    /**
     * Adds a member to an existing party, updating the reverse index.
     * 
     * @return the party that was modified, or {@code null} if not found
     */
    @Nullable
    public Party addMember(UUID partyId, UUID playerUUID, PartyRole role) {
        var party = parties.get(partyId);
        if (party == null) return null;
        party.addMember(playerUUID, role);
        playerToPartyId.put(playerUUID, partyId);
        return party;
    }

    /**
     * Removes a member from a party, updating the reverse index.
     * 
     * @return the role the member had, or {@code null} if not a member
     */
    @Nullable
    public PartyRole removeMember(UUID partyId, UUID playerUUID) {
        var party = parties.get(partyId);
        if (party == null) return null;
        var role = party.getRole(playerUUID);
        party.removeMember(playerUUID);
        playerToPartyId.remove(playerUUID);
        return role;
    }

    /**
     * Changes a member's role within a party, preserving the reverse index.
     * 
     * @return the party that was modified, or {@code null} if not found
     */
    @Nullable
    public Party setRole(UUID partyId, UUID playerUUID, PartyRole role) {
        var party = parties.get(partyId);
        if (party == null) return null;
        party.setRole(playerUUID, role);
        return party;
    }

    /**
     * Removes a single member from the reverse index only (does not modify Party).
     * Used when a member leaves a BQu party but retains their BLPC party record.
     */
    public void unregisterMemberFromReverseIndex(UUID playerUUID) {
        playerToPartyId.remove(playerUUID);
    }

    /**
     * Rebuilds the reverse index for a party from its current members.
     * Used for lazy population after NBT load.
     */
    public void rebuildIndexForParty(Party party) {
        registerMembers(party, party.getMembers());
    }

    public Party createParty(String name, UUID owner) {
        UUID id = UUID.randomUUID();
        Party party = new Party(id, name, System.currentTimeMillis());
        party.addMember(owner, PartyRole.OWNER);
        registerMembers(party, party.getMembers());
        parties.put(id, party);
        return party;
    }

    public void addParty(Party party) {
        parties.put(party.getPartyId(), party);
        registerMembers(party, party.getMembers());
    }

    public void removeParty(UUID partyId) {
        var party = parties.get(partyId);
        if (party != null) {
            unregisterAllMembers(party);
        }
        parties.remove(partyId);
    }

    @Nullable
    public Party getParty(UUID partyId) {
        return parties.get(partyId);
    }

    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    /**
     * Returns the party containing the given player UUID, or {@code null} if none.
     * <p>
     * O(1) lookup via the reverse index {@link #playerToPartyId}.
     */
    @Nullable
    public Party getPartyByPlayer(UUID playerUUID) {
        var partyId = playerToPartyId.get(playerUUID);
        return partyId != null ? parties.get(partyId) : null;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public void setMigrated(boolean migrated) {
        this.migrated = migrated;
    }

    public boolean isBQuLinked(UUID playerUUID) {
        return bquLinkedPlayers.contains(playerUUID);
    }

    public Set<UUID> getBQuLinkedPlayers() {
        return Collections.unmodifiableSet(bquLinkedPlayers);
    }

    public void setBQuLinked(UUID playerUUID, boolean linked) {
        if (linked) {
            bquLinkedPlayers.add(playerUUID);
        } else {
            bquLinkedPlayers.remove(playerUUID);
        }
    }

    // --- Config NBT ---

    public void readConfigNBT(NBTTagCompound nbt) {
        migrated = nbt.getBoolean("migrated");
        bquLinkedPlayers.clear();
        playerToPartyId.clear();
        NBTTagList linkedList = nbt.getTagList("bquLinked", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < linkedList.tagCount(); i++) {
            bquLinkedPlayers.add(linkedList.getCompoundTagAt(i).getUniqueId("uuid"));
        }
        // Rebuild reverse index from all loaded parties
        for (var party : parties.values()) {
            rebuildIndexForParty(party);
        }
    }

    public void writeConfigNBT(NBTTagCompound nbt) {
        nbt.setBoolean("migrated", migrated);
        nbt.setTag("bquLinked", serializeBQuLinked());
    }

    // --- Client sync ---

    /** Serializes all data for file persistence (no player name cache). */
    public NBTTagCompound serializeAll() {
        NBTTagCompound all = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Party party : parties.values()) {
            list.appendTag(party.toNBT());
        }
        all.setTag("parties", list);
        all.setTag("bquLinked", serializeBQuLinked());
        return all;
    }

    /** Serializes all data for client sync (includes player name cache). */
    public NBTTagCompound serializeForSync() {
        NBTTagCompound all = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Party party : parties.values()) {
            party.resolvePlayerNames(this::getParty);
            list.appendTag(party.toSyncNBT());
        }
        all.setTag("parties", list);
        all.setTag("bquLinked", serializeBQuLinked());
        return all;
    }

    private NBTTagList serializeBQuLinked() {
        NBTTagList linkedList = new NBTTagList();
        for (UUID uuid : bquLinkedPlayers) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("uuid", uuid);
            linkedList.appendTag(tag);
        }
        return linkedList;
    }
}
