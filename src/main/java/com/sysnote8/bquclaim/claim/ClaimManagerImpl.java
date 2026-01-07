package com.sysnote8.bquclaim.claim;

import com.sysnote8.bquclaim.api.chunk.ChunkData;
import com.sysnote8.bquclaim.api.claim.ClaimManager;

import java.util.Collections;
import java.util.List;

public class ClaimManagerImpl implements ClaimManager {
    // Todo: handle party join/leave
    @Override
    public boolean addClaim(ChunkData chunkData, int partyId) {
        return false;
    }

    @Override
    public boolean getClaim(ChunkData chunkData) {
        return false;
    }

    @Override
    public boolean updateClaim(ChunkData chunkData, int newOwnerPartyId) {
        return false;
    }

    @Override
    public boolean removeClaim(ChunkData chunkData) {
        return false;
    }

    @Override
    public List<ChunkData> getClaims(int partyId) {
        return Collections.emptyList();
    }

    @Override
    public boolean removePartyClaim(int partyId) {
        return false;
    }
}
