package com.github.gtexpert.blpc.common.party;

import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * Server-side authoritative party storage.
 * <p>
 * Parties are persisted by {@link com.github.gtexpert.blpc.common.BLPCSaveHandler}
 * to {@code world/betterlink/pc/parties/&lt;id&gt;.dat}. The {@code bquLinkedPlayers}
 * set tracks which players have opted into BetterQuesting integration; lookup
 * via {@link #isBQuLinked(UUID)} drives provider selection in
 * {@link com.github.gtexpert.blpc.common.network.MessagePartyAction.Handler}.
 * <p>
 * The {@code migrated} flag records whether legacy (pre-FTB-Lib-layout) data
 * has already been imported, so migration is a one-time operation.
 */
public class PartyManagerData {

    private static volatile PartyManagerData instance;

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private boolean migrated;
    private final Set<UUID> bquLinkedPlayers = new HashSet<>();

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

    public Party createParty(String name, UUID owner) {
        UUID id = UUID.randomUUID();
        Party party = new Party(id, name, System.currentTimeMillis());
        party.addMember(owner, PartyRole.OWNER);
        parties.put(id, party);
        return party;
    }

    public void addParty(Party party) {
        parties.put(party.getPartyId(), party);
    }

    public void removeParty(UUID partyId) {
        parties.remove(partyId);
    }

    @Nullable
    public Party getParty(UUID partyId) {
        return parties.get(partyId);
    }

    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    @Nullable
    public Party getPartyByPlayer(UUID playerUUID) {
        return parties.values().stream()
                .filter(p -> p.isMember(playerUUID))
                .findFirst().orElse(null);
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
            party.resolvePlayerNames();
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
