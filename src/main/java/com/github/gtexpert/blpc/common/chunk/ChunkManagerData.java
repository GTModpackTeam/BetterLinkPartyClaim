package com.github.gtexpert.blpc.common.chunk;

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Server-side chunk claim storage. Singleton, persisted by {@link com.github.gtexpert.blpc.common.BLPCSaveHandler}.
 * Claims are keyed by "{@code x,z}" string and grouped per-party in save files.
 */
public class ChunkManagerData {

    private static ChunkManagerData instance;

    private final Map<String, ClaimedChunkData> claims = new HashMap<>();

    public static ChunkManagerData getInstance() {
        if (instance == null) {
            instance = new ChunkManagerData();
        }
        return instance;
    }

    public static void reset() {
        instance = new ChunkManagerData();
    }

    public static String chunkKey(int x, int z) {
        return x + "," + z;
    }

    public ClaimedChunkData getClaim(int x, int z) {
        return claims.get(chunkKey(x, z));
    }

    public void setClaim(int x, int z, UUID owner, String name, String partyName, boolean isForceLoaded) {
        String key = chunkKey(x, z);
        if (owner == null) {
            claims.remove(key);
        } else {
            claims.put(key, new ClaimedChunkData(x, z, owner, name, partyName, isForceLoaded));
        }
    }

    public Collection<ClaimedChunkData> getAllClaims() {
        return Collections.unmodifiableCollection(claims.values());
    }

    public List<ClaimedChunkData> getClaimsByOwner(UUID owner) {
        return claims.values().stream()
                .filter(d -> d.ownerUUID.equals(owner))
                .collect(Collectors.toList());
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
}
