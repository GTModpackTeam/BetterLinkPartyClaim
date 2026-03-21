package com.github.gtexpert.blpc.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.github.gtexpert.blpc.BLPCMod;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

/**
 * File-based persistence handler for BLPC data.
 * <p>
 * Data is stored in {@code world/betterlink/pc/} following the FTB Lib pattern:
 *
 * <pre>
 * betterlink/pc/
 * ├── config.dat          — metadata (migration flag, BQu link flags)
 * ├── parties/
 * │   └── &lt;id&gt;.dat       — per-party NBT (gzip compressed)
 * └── claims/
 *     ├── global.dat      — claims from players without a party
 *     └── &lt;id&gt;.dat       — per-party claims
 * </pre>
 *
 * Loaded on {@code FMLServerStartingEvent}, saved on {@code FMLServerStoppingEvent}
 * and {@code WorldEvent.Save}.
 */
public class BLPCSaveHandler {

    public static final BLPCSaveHandler INSTANCE = new BLPCSaveHandler();

    private File dataDir;
    private File partiesDir;
    private File claimsDir;

    private BLPCSaveHandler() {}

    public void initWorldDir(MinecraftServer server) {
        File worldDir = server.getEntityWorld().getSaveHandler().getWorldDirectory();
        dataDir = new File(worldDir, "betterlink/pc");
        partiesDir = new File(dataDir, "parties");
        claimsDir = new File(dataDir, "claims");
        dataDir.mkdirs();
        partiesDir.mkdirs();
        claimsDir.mkdirs();
    }

    // --- Config ---

    public void loadConfig(PartyManagerData data) {
        File file = new File(dataDir, "config.dat");
        if (!file.exists()) return;
        try (FileInputStream fis = new FileInputStream(file)) {
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(fis);
            data.readConfigNBT(nbt);
            BLPCMod.LOGGER.debug("Loaded config from {}", file);
        } catch (IOException e) {
            BLPCMod.LOGGER.error("Failed to load config", e);
        }
    }

    public void saveConfig(PartyManagerData data) {
        File file = new File(dataDir, "config.dat");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            NBTTagCompound nbt = new NBTTagCompound();
            data.writeConfigNBT(nbt);
            CompressedStreamTools.writeCompressed(nbt, fos);
        } catch (IOException e) {
            BLPCMod.LOGGER.error("Failed to save config", e);
        }
    }

    // --- Parties (one file per party) ---

    public void loadParties(PartyManagerData data) {
        File[] files = partiesDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return;
        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(fis);
                Party party = Party.fromNBT(nbt);
                data.addParty(party);
            } catch (IOException e) {
                BLPCMod.LOGGER.error("Failed to load party from {}", file, e);
            }
        }
        BLPCMod.LOGGER.debug("Loaded {} parties from {}", data.getAllParties().size(), partiesDir);
    }

    public void saveParties(PartyManagerData data) {
        File[] oldFiles = partiesDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (oldFiles != null) {
            for (File f : oldFiles) f.delete();
        }
        for (Party party : data.getAllParties()) {
            File file = new File(partiesDir, party.getPartyId() + ".dat");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                CompressedStreamTools.writeCompressed(party.toNBT(), fos);
            } catch (IOException e) {
                BLPCMod.LOGGER.error("Failed to save party {}", party.getPartyId(), e);
            }
        }
    }

    // --- Claims (per-party files) ---

    public void loadClaims(ChunkManagerData data) {
        File[] files = claimsDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return;
        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                NBTTagCompound nbt = CompressedStreamTools.readCompressed(fis);
                NBTTagList list = nbt.getTagList("claims", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < list.tagCount(); i++) {
                    ClaimedChunkData claim = ClaimedChunkData.fromNBT(list.getCompoundTagAt(i));
                    data.setClaim(claim.x, claim.z, claim.ownerUUID, claim.ownerName, claim.partyName,
                            claim.isForceLoaded);
                }
            } catch (IOException e) {
                BLPCMod.LOGGER.error("Failed to load claims from {}", file, e);
            }
        }
        BLPCMod.LOGGER.debug("Loaded claims from {}", claimsDir);
    }

    public void saveClaims(ChunkManagerData chunkData, PartyManagerData partyData) {
        File[] oldFiles = claimsDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (oldFiles != null) {
            for (File f : oldFiles) f.delete();
        }

        Map<Integer, NBTTagList> partyLists = new HashMap<>();
        NBTTagList globalList = new NBTTagList();

        for (ClaimedChunkData claim : chunkData.getAllClaims()) {
            Party party = partyData.getPartyByPlayer(claim.ownerUUID);
            if (party != null) {
                partyLists.computeIfAbsent(party.getPartyId(), k -> new NBTTagList()).appendTag(claim.toNBT());
            } else {
                globalList.appendTag(claim.toNBT());
            }
        }

        saveClaimList(new File(claimsDir, "global.dat"), globalList);
        for (Map.Entry<Integer, NBTTagList> entry : partyLists.entrySet()) {
            saveClaimList(new File(claimsDir, entry.getKey() + ".dat"), entry.getValue());
        }
    }

    private void saveClaimList(File file, NBTTagList list) {
        if (list.tagCount() == 0) return;
        try (FileOutputStream fos = new FileOutputStream(file)) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("claims", list);
            CompressedStreamTools.writeCompressed(nbt, fos);
        } catch (IOException e) {
            BLPCMod.LOGGER.error("Failed to save claims to {}", file, e);
        }
    }

    // --- Full save/load ---

    public void loadAll(MinecraftServer server) {
        initWorldDir(server);
        ChunkManagerData.reset();
        PartyManagerData.reset();
        PartyManagerData partyData = PartyManagerData.getInstance();
        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        loadConfig(partyData);
        loadParties(partyData);
        loadClaims(chunkData);
    }

    public void saveAll() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || dataDir == null) return;
        PartyManagerData partyData = PartyManagerData.getInstance();
        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        saveConfig(partyData);
        saveParties(partyData);
        saveClaims(chunkData, partyData);
    }
}
