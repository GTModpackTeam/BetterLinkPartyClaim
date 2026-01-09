package com.sysnote8.bquclaim.chunk;

import java.util.UUID;

public class ClaimedChunkData {
    public final int x, z;
    public final UUID ownerUUID;
    public final String ownerName;
    public boolean isForceLoaded; // ★追加

    public ClaimedChunkData(int x, int z, UUID ownerUUID, String ownerName, boolean isForceLoaded) {
        this.x = x;
        this.z = z;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.isForceLoaded = isForceLoaded;
    }
}
