package com.sysnote8.bquclaim.api.claim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sysnote8.bquclaim.api.chunk.ChunkData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimMap {
    private static final Logger logger = LogManager.getLogger(ClaimMap.class);
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type mapType = new TypeToken<Map<Integer, ArrayList<ChunkData>>>(){}.getType();
    protected ConcurrentHashMap<Integer, ArrayList<ChunkData>> claimMapByParty = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<ChunkData, Integer> claimMapByChunkData = new ConcurrentHashMap<>();

    protected final File claimJsonFile;

    public ClaimMap(File claimJsonFile) {
        this.claimJsonFile = claimJsonFile;
        load();
    }

    protected void load() {
        if(!claimJsonFile.exists()) {
            logger.warn("Claim data wasn't found. Load default one.");
            return;
        }

        logger.info("Loading claim data...");
        try(Reader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(claimJsonFile.toPath()), StandardCharsets.UTF_8))) {
            claimMapByParty = new ConcurrentHashMap<>(gson.fromJson(reader, mapType));
        } catch (IOException e) {
            logger.error("Failed to load json.", e);
            return;
        }

        logger.info("Re-Mapping claim data...");
        claimMapByParty.forEach((partyId, chunkList) -> {
            chunkList.forEach(chunk -> {
                claimMapByChunkData.put(chunk, partyId);
            });
        });

        logger.info("Claim data loaded!");
    }

    protected void save() {
        logger.info("Writing claim data...");
        try(OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(claimJsonFile.toPath()), StandardCharsets.UTF_8)) {
            gson.toJson(claimMapByParty, writer);
        } catch (Exception e) {
            logger.error("Failed to write json.", e);
            return;
        }

        logger.info("Claim data saved!");
    }

    public boolean setClaim(ChunkData chunkData, int partyId) {
        Integer oldPartyId = claimMapByChunkData.get(chunkData);
        if(oldPartyId != null) return false; // If already claimed, this action will be canceled.
        claimMapByChunkData.put(chunkData, partyId);
        claimMapByParty.compute(partyId, (id, data) -> {
            if(data == null) {
                ArrayList<ChunkData> claims = new ArrayList<>();
                claims.add(chunkData);
                return claims;
            } else {
                data.add(chunkData);
                return data;
            }
        });
        return true;
    }

    public boolean removeClaim(ChunkData chunkData) {
        Integer oldPartyId = claimMapByChunkData.get(chunkData);
        if(oldPartyId == null) return false; // If not claimed yet, this action will be canceled.
        claimMapByChunkData.remove(chunkData);
        claimMapByParty.compute(oldPartyId, (id, data) -> {
            if(data == null) return null;
            data.remove(chunkData);
            return data.isEmpty() ? null: data;
        });
        return true;
    }

    public boolean isClaimed(ChunkData chunkData) {
        return claimMapByChunkData.containsKey(chunkData);
    }

    @Nullable
    public Integer getClaimPartyId(ChunkData chunkData) {
        return claimMapByChunkData.get(chunkData);
    }

    public List<ChunkData> getClaimsByParty(int partyId) {
        return claimMapByParty.getOrDefault(partyId, new ArrayList<>());
    }
}
