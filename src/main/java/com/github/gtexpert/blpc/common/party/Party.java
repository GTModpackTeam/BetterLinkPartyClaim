package com.github.gtexpert.blpc.common.party;

import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * Represents a party with members, roles, and invitations.
 * <p>
 * Persisted to {@code world/betterlink/pc/parties/<id>.dat} via
 * {@link com.github.gtexpert.blpc.common.BLPCSaveHandler}.
 * Invitations are memory-only and do not survive server restarts.
 */
public class Party {

    public static final String DEFAULT_NAME_KEY = "blpc.party.default_name";

    private final int partyId;
    private String name;
    private final Map<UUID, PartyRole> members = new LinkedHashMap<>();
    private final long createdAt;

    private final Map<UUID, Long> invites = new HashMap<>();

    public Party(int partyId, String name, long createdAt) {
        this.partyId = partyId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public int getPartyId() {
        return partyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // --- Members ---

    public Map<UUID, PartyRole> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public List<UUID> getMemberUUIDs() {
        return Collections.unmodifiableList(new ArrayList<>(members.keySet()));
    }

    @Nullable
    public PartyRole getRole(UUID uuid) {
        return members.get(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public void addMember(UUID uuid, PartyRole role) {
        members.put(uuid, role);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public void setRole(UUID uuid, PartyRole role) {
        if (!members.containsKey(uuid)) return;
        if (role == PartyRole.OWNER) {
            for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
                if (entry.getValue() == PartyRole.OWNER) {
                    entry.setValue(PartyRole.ADMIN);
                    break;
                }
            }
        }
        members.put(uuid, role);
    }

    @Nullable
    public UUID getOwner() {
        for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
            if (entry.getValue() == PartyRole.OWNER) {
                return entry.getKey();
            }
        }
        return null;
    }

    // --- Invites (memory only, not persisted) ---

    public void addInvite(UUID target, long expiryTimestamp) {
        invites.put(target, expiryTimestamp);
    }

    public boolean hasInvite(UUID target) {
        Long expiry = invites.get(target);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            invites.remove(target);
            return false;
        }
        return true;
    }

    public void removeInvite(UUID target) {
        invites.remove(target);
    }

    public void cleanExpiredInvites() {
        long now = System.currentTimeMillis();
        invites.entrySet().removeIf(e -> now > e.getValue());
    }

    // --- NBT ---

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", partyId);
        tag.setString("name", name);
        tag.setLong("created", createdAt);

        NBTTagList memberList = new NBTTagList();
        for (Map.Entry<UUID, PartyRole> entry : members.entrySet()) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setUniqueId("uuid", entry.getKey());
            memberTag.setString("role", entry.getValue().name());
            memberList.appendTag(memberTag);
        }
        tag.setTag("members", memberList);
        return tag;
    }

    public static Party fromNBT(NBTTagCompound tag) {
        int id = tag.getInteger("id");
        String name = tag.getString("name");
        long created = tag.getLong("created");
        Party party = new Party(id, name, created);

        NBTTagList memberList = tag.getTagList("members", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound memberTag = memberList.getCompoundTagAt(i);
            UUID uuid = memberTag.getUniqueId("uuid");
            PartyRole role;
            try {
                role = PartyRole.valueOf(memberTag.getString("role"));
            } catch (IllegalArgumentException e) {
                role = PartyRole.MEMBER;
            }
            party.addMember(uuid, role);
        }
        return party;
    }
}
