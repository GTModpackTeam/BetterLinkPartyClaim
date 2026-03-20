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

    public NBTTagCompound serializeAll() {
        NBTTagCompound all = new NBTTagCompound();
        for (Map.Entry<String, ClaimedChunkData> entry : claims.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            ClaimedChunkData d = entry.getValue();
            tag.setInteger("x", d.x);
            tag.setInteger("z", d.z);
            tag.setUniqueId("owner", d.ownerUUID);
            tag.setString("name", d.ownerName);
            tag.setBoolean("force", d.isForceLoaded);
            all.setTag(entry.getKey(), tag);
        }
        return all;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("list", Constants.NBT.TAG_COMPOUND);
        claims.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            String key = c.getInteger("x") + "," + c.getInteger("z");
            // Backwards compatibility: older saves might use "is_force_loaded" key while
            // newer code uses "force". Prefer "force" if present.
            boolean force = false;
            if (c.hasKey("force")) {
                force = c.getBoolean("force");
            } else if (c.hasKey("is_force_loaded")) {
                force = c.getBoolean("is_force_loaded");
            }
            claims.put(key, new ClaimedChunkData(c.getInteger("x"),
                    c.getInteger("z"),
                    c.getUniqueId("owner"),
                    c.getString("name"),
                    force));
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
            // Use unified key name "force" for newer saves
            c.setBoolean("force", d.isForceLoaded);
            list.appendTag(c);
        }
        nbt.setTag("list", list);
        return nbt;
    }
}
