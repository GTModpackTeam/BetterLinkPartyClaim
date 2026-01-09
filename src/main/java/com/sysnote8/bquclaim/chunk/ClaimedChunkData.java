package com.sysnote8.bquclaim.chunk;

import java.util.UUID;

public class ClaimedChunkData {

    public final int x;
    public final int z;
    public final UUID ownerUUID;
    public final String ownerName;

    public ClaimedChunkData(int x, int z, UUID ownerUUID, String ownerName) {
        this.x = x;
        this.z = z;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
    }
}
