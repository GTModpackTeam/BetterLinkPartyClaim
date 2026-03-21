package com.github.gtexpert.blpc.common.chunk;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

public class ClaimedChunkData {

    public final int x, z;
    public final UUID ownerUUID;
    public final String ownerName;
    public final String partyName;
    public boolean isForceLoaded;

    public ClaimedChunkData(int x, int z, UUID ownerUUID, String ownerName, String partyName, boolean isForceLoaded) {
        this.x = x;
        this.z = z;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.partyName = partyName;
        this.isForceLoaded = isForceLoaded;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("z", z);
        tag.setUniqueId("owner", ownerUUID);
        tag.setString("name", ownerName);
        tag.setString("party", partyName);
        tag.setBoolean("force", isForceLoaded);
        return tag;
    }

    public static ClaimedChunkData fromNBT(NBTTagCompound tag) {
        int x = tag.getInteger("x");
        int z = tag.getInteger("z");
        String team = tag.hasKey("party") ? tag.getString("party") : "";
        boolean force = tag.getBoolean("force");
        return new ClaimedChunkData(x, z, tag.getUniqueId("owner"), tag.getString("name"), team, force);
    }
}
