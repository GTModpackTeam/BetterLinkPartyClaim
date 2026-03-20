package com.github.gtexpert.bquclaim.chunk;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

public class ClaimedChunkData {

    public final int x, z;
    public final UUID ownerUUID;
    public final String ownerName;
    public boolean isForceLoaded;

    public ClaimedChunkData(int x, int z, UUID ownerUUID, String ownerName, boolean isForceLoaded) {
        this.x = x;
        this.z = z;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.isForceLoaded = isForceLoaded;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("z", z);
        tag.setUniqueId("owner", ownerUUID);
        tag.setString("name", ownerName);
        tag.setBoolean("force", isForceLoaded);
        return tag;
    }

    public static ClaimedChunkData fromNBT(NBTTagCompound tag) {
        int x = tag.getInteger("x");
        int z = tag.getInteger("z");
        boolean force = tag.hasKey("force") ? tag.getBoolean("force") : tag.getBoolean("is_force_loaded");
        return new ClaimedChunkData(x, z, tag.getUniqueId("owner"), tag.getString("name"), force);
    }
}
