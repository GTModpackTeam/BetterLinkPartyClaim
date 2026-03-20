package com.github.gtexpert.bquclaim.chunk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

public class ChunkManagerData extends WorldSavedData {

    private static final String DATA_NAME = "MyChunkClaims";
    private final Map<String, ClaimedChunkData> claims = new HashMap<>();

    public ChunkManagerData(String name) {
        super(name);
    }

    public static ChunkManagerData get(World world) {
        MapStorage storage = world.getMapStorage();
        assert storage != null;
        ChunkManagerData data = (ChunkManagerData) storage.getOrLoadData(ChunkManagerData.class, DATA_NAME);
        if (data == null) {
            data = new ChunkManagerData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public static String chunkKey(int x, int z) {
        return x + "," + z;
    }

    public ClaimedChunkData getClaim(int x, int z) {
        return claims.get(chunkKey(x, z));
    }

    public void setClaim(int x, int z, UUID owner, String name, boolean isForceLoaded) {
        String key = chunkKey(x, z);
        if (owner == null) {
            claims.remove(key);
        } else {
            claims.put(key, new ClaimedChunkData(x, z, owner, name, isForceLoaded));
        }
        markDirty();
    }

    public int countClaims(UUID owner) {
        int count = 0;
        for (ClaimedChunkData d : claims.values()) {
            if (d.ownerUUID.equals(owner)) count++;
        }
        return count;
    }

    public int countForceLoads(UUID owner) {
        int count = 0;
        for (ClaimedChunkData d : claims.values()) {
            if (d.ownerUUID.equals(owner) && d.isForceLoaded) count++;
        }
        return count;
    }

    public NBTTagCompound serializeAll() {
        NBTTagCompound all = new NBTTagCompound();
        for (Map.Entry<String, ClaimedChunkData> entry : claims.entrySet()) {
            all.setTag(entry.getKey(), entry.getValue().toNBT());
        }
        return all;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("list", Constants.NBT.TAG_COMPOUND);
        claims.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            ClaimedChunkData d = ClaimedChunkData.fromNBT(list.getCompoundTagAt(i));
            claims.put(chunkKey(d.x, d.z), d);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (ClaimedChunkData d : claims.values()) {
            list.appendTag(d.toNBT());
        }
        nbt.setTag("list", list);
        return nbt;
    }
}
