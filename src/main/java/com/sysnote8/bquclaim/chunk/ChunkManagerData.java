package com.sysnote8.bquclaim.chunk;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChunkManagerData extends WorldSavedData {

    private static final String DATA_NAME = "MyChunkClaims";
    public final Map<String, ClaimedChunkData> claims = new HashMap<>();

    public ChunkManagerData(String name) {
        super(name);
    }

    public static ChunkManagerData get(World world) {
        MapStorage mapStorage = world.getMapStorage();
        assert mapStorage != null;
        ChunkManagerData data = (ChunkManagerData) mapStorage.getOrLoadData(ChunkManagerData.class, DATA_NAME);
        if (data == null) {
            data = new ChunkManagerData(DATA_NAME);
            mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void setClaim(int x, int z, UUID owner, String name, boolean isForceLoaded) {
        if (owner == null) claims.remove(x + "," + z);
        else claims.put(x + "," + z, new ClaimedChunkData(x, z, owner, name, isForceLoaded));
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("list", Constants.NBT.TAG_COMPOUND);
        claims.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            String key = c.getInteger("x") + "," + c.getInteger("z");
            claims.put(key, new ClaimedChunkData(c.getInteger("x"),
                    c.getInteger("z"),
                    c.getUniqueId("owner"),
                    c.getString("name"),
                    c.getBoolean("is_force_loaded")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (ClaimedChunkData d : claims.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("x", d.x);
            c.setInteger("z", d.z);
            c.setUniqueId("owner", d.ownerUUID);
            c.setString("name", d.ownerName);
            c.setBoolean("is_force_loaded", d.isForceLoaded);
            list.appendTag(c);
        }
        nbt.setTag("list", list);
        return nbt;
    }
}
