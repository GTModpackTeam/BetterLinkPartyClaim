package com.github.gtexpert.blpc.common.party;

import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

public class PartyManagerData {

    private static PartyManagerData instance;

    private final Map<Integer, Party> parties = new TreeMap<>();
    private final BitSet usedIds = new BitSet();
    private boolean migrated;
    private final Set<UUID> bquLinkedPlayers = new HashSet<>();

    public static PartyManagerData getInstance() {
        if (instance == null) {
            instance = new PartyManagerData();
        }
        return instance;
    }

    public static void reset() {
        instance = new PartyManagerData();
    }

    // --- CRUD ---

    public Party createParty(String name, UUID owner) {
        int id = nextId();
        Party party = new Party(id, name, System.currentTimeMillis());
        party.addMember(owner, PartyRole.OWNER);
        parties.put(id, party);
        usedIds.set(id);
        return party;
    }

    public void addParty(Party party) {
        parties.put(party.getPartyId(), party);
        usedIds.set(party.getPartyId());
    }

    public void removeParty(int partyId) {
        parties.remove(partyId);
        usedIds.clear(partyId);
    }

    @Nullable
    public Party getParty(int partyId) {
        return parties.get(partyId);
    }

    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    @Nullable
    public Party getPartyByPlayer(UUID playerUUID) {
        for (Party party : parties.values()) {
            if (party.isMember(playerUUID)) {
                return party;
            }
        }
        return null;
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
        NBTTagList linkedList = nbt.getTagList("bquLinked", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < linkedList.tagCount(); i++) {
            bquLinkedPlayers.add(linkedList.getCompoundTagAt(i).getUniqueId("uuid"));
        }
    }

    public void writeConfigNBT(NBTTagCompound nbt) {
        nbt.setBoolean("migrated", migrated);
        NBTTagList linkedList = new NBTTagList();
        for (UUID uuid : bquLinkedPlayers) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("uuid", uuid);
            linkedList.appendTag(tag);
        }
        nbt.setTag("bquLinked", linkedList);
    }

    private int nextId() {
        return usedIds.nextClearBit(0);
    }

    // --- Client sync ---

    public NBTTagCompound serializeAll() {
        NBTTagCompound all = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Party party : parties.values()) {
            list.appendTag(party.toNBT());
        }
        all.setTag("parties", list);
        NBTTagList linkedList = new NBTTagList();
        for (UUID uuid : bquLinkedPlayers) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("uuid", uuid);
            linkedList.appendTag(tag);
        }
        all.setTag("bquLinked", linkedList);
        return all;
    }
}
