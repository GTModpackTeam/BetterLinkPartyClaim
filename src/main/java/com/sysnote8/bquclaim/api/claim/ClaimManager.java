package com.sysnote8.bquclaim.api.claim;

import com.sysnote8.bquclaim.api.chunk.ChunkData;
import net.minecraft.world.chunk.Chunk;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ClaimManager {
    protected final ClaimMap claimMap;
    public ClaimManager(File claimJsonFile) {
        claimMap = new ClaimMap(claimJsonFile);
    }

    // Todo: handle party join/leave

    public boolean addClaim(ChunkData chunkData, int partyId) {
        return false;
    }

    public boolean getClaim(ChunkData chunkData) {
        return false;
    }

    public boolean updateClaim(ChunkData chunkData, int newOwnerPartyId) {
        return false;
    }

    public boolean removeClaim(ChunkData chunkData) {
        return false;
    }

    public List<ChunkData> getClaims(int partyId) {
        return Collections.emptyList();
    }

    public boolean isClaimed(Chunk chunk) {
        return isClaimed(ChunkData.fromChunk(chunk));
    }

    public boolean isClaimed(ChunkData chunk) {
        return false;
    }

    public boolean removePartyClaim(int partyId) {
        return false;
    }

    /**
     * Check player permission in target chunk
     * @param chunkData target chunk data
     * @param playerUuid uuid of player
     * @return is permitted for player
     */
    public boolean hasPermission(ChunkData chunkData, UUID playerUuid) {
        return false;
    }
}
