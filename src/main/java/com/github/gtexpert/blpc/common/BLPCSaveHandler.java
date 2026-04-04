package com.github.gtexpert.blpc.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.TicketManager;
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
 * ├── backup/
 * │   ├── parties/        — most recent backup of parties/
 * │   └── claims/         — most recent backup of claims/
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
    private volatile boolean dirty = false;

    private BLPCSaveHandler() {}

    public void markDirty() {
        dirty = true;
    }

    public synchronized void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        saveAllInternal();
    }

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
        } catch (IOException e) {
            ModLog.IO.error("Failed to load config.dat", e);
        }
    }

    public void saveConfig(PartyManagerData data) {
        File tmpFile = new File(dataDir, "config.dat.tmp");
        File finalFile = new File(dataDir, "config.dat");
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            NBTTagCompound nbt = new NBTTagCompound();
            data.writeConfigNBT(nbt);
            CompressedStreamTools.writeCompressed(nbt, fos);
            try {
                fos.getFD().sync();
            } catch (SyncFailedException ignored) {
                ModLog.IO.debug("fsync not supported for config.dat.tmp");
            }
        } catch (IOException e) {
            ModLog.IO.error("Failed to write config.dat.tmp", e);
            tmpFile.delete();
            return;
        }
        finalFile.delete();
        if (!tmpFile.renameTo(finalFile)) {
            ModLog.IO.error("Failed to rename config.dat.tmp to config.dat");
            tmpFile.delete();
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
                ModLog.IO.error("Failed to load party file: {}", file.getName(), e);
            }
        }
    }

    public void saveParties(PartyManagerData data) {
        List<File> tmpFiles = new ArrayList<>();
        boolean allOk = true;

        for (Party party : data.getAllParties()) {
            File tmpFile = new File(partiesDir, party.getPartyId() + ".dat.tmp");
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                CompressedStreamTools.writeCompressed(party.toNBT(), fos);
                try {
                    fos.getFD().sync();
                } catch (SyncFailedException ignored) {
                    ModLog.IO.debug("fsync not supported for {}", tmpFile.getName());
                }
                tmpFiles.add(tmpFile);
            } catch (IOException e) {
                ModLog.IO.error("Failed to write party temp file: {}", tmpFile.getName(), e);
                allOk = false;
                break;
            }
        }

        if (!allOk) {
            for (File tmp : tmpFiles) tmp.delete();
            ModLog.IO.error("Party save aborted; old files preserved");
            return;
        }

        backupDir(partiesDir, "parties");

        File[] oldFiles = partiesDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (oldFiles != null) {
            for (File f : oldFiles) f.delete();
        }

        for (File tmp : tmpFiles) {
            String finalName = tmp.getName().replace(".dat.tmp", ".dat");
            File finalFile = new File(partiesDir, finalName);
            if (!tmp.renameTo(finalFile)) {
                ModLog.IO.error("Failed to rename party temp file: {}", tmp.getName());
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
                    if (claim == null) continue;
                    data.setClaim(claim.x, claim.z, claim.ownerUUID, claim.ownerName, claim.partyName,
                            claim.isForceLoaded);
                }
            } catch (IOException e) {
                ModLog.IO.error("Failed to load claim file: {}", file.getName(), e);
            }
        }
    }

    public void saveClaims(ChunkManagerData chunkData, PartyManagerData partyData) {
        Map<UUID, NBTTagList> partyLists = new HashMap<>();
        NBTTagList globalList = new NBTTagList();

        for (ClaimedChunkData claim : chunkData.getAllClaims()) {
            Party party = partyData.getPartyByPlayer(claim.ownerUUID);
            if (party != null) {
                partyLists.computeIfAbsent(party.getPartyId(), k -> new NBTTagList()).appendTag(claim.toNBT());
            } else {
                globalList.appendTag(claim.toNBT());
            }
        }

        // Build full map of filename -> NBTTagList for temp-write
        Map<String, NBTTagList> toWrite = new HashMap<>();
        if (globalList.tagCount() > 0) {
            toWrite.put("global", globalList);
        }
        for (var entry : partyLists.entrySet()) {
            if (entry.getValue().tagCount() > 0) {
                toWrite.put(entry.getKey().toString(), entry.getValue());
            }
        }

        // Write all temp files first
        List<File> tmpFiles = new ArrayList<>();
        boolean allOk = true;
        for (var entry : toWrite.entrySet()) {
            File tmpFile = new File(claimsDir, entry.getKey() + ".dat.tmp");
            if (!saveClaimListTemp(tmpFile, entry.getValue())) {
                allOk = false;
                break;
            }
            tmpFiles.add(tmpFile);
        }

        if (!allOk) {
            for (File tmp : tmpFiles) tmp.delete();
            ModLog.IO.error("Claims save aborted; old files preserved");
            return;
        }

        backupDir(claimsDir, "claims");

        // Swap: delete old .dat files then rename .tmp to .dat
        File[] oldFiles = claimsDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (oldFiles != null) {
            for (File f : oldFiles) f.delete();
        }

        for (File tmp : tmpFiles) {
            String finalName = tmp.getName().replace(".dat.tmp", ".dat");
            File finalFile = new File(claimsDir, finalName);
            if (!tmp.renameTo(finalFile)) {
                ModLog.IO.error("Failed to rename claim temp file: {}", tmp.getName());
            }
        }
    }

    private boolean saveClaimListTemp(File tmpFile, NBTTagList list) {
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("claims", list);
            CompressedStreamTools.writeCompressed(nbt, fos);
            try {
                fos.getFD().sync();
            } catch (SyncFailedException ignored) {
                ModLog.IO.debug("fsync not supported for {}", tmpFile.getName());
            }
            return true;
        } catch (IOException e) {
            ModLog.IO.error("Failed to write claim temp file: {}", tmpFile.getName(), e);
            tmpFile.delete();
            return false;
        }
    }

    // --- Backup helper ---

    private void backupDir(File sourceDir, String subDirName) {
        File backupDir = new File(dataDir, "backup/" + subDirName);
        backupDir.mkdirs();
        // Clear old backup
        File[] oldBackups = backupDir.listFiles();
        if (oldBackups != null) {
            for (File f : oldBackups) f.delete();
        }
        // Copy current .dat files to backup
        File[] currentFiles = sourceDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (currentFiles != null) {
            for (File src : currentFiles) {
                try {
                    Files.copy(src.toPath(), new File(backupDir, src.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    ModLog.IO.warn("Failed to backup file: {}", src.getName(), e);
                }
            }
        }
    }

    // --- Full save/load ---

    public synchronized void loadAll(MinecraftServer server) {
        initWorldDir(server);
        ChunkManagerData.reset();
        PartyManagerData.reset();
        TicketManager.reset();
        PartyManagerData partyData = PartyManagerData.getInstance();
        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        loadConfig(partyData);
        loadParties(partyData);
        // Migrate old player-UUID-based allies/enemies to party-UUID-based
        migrateAlliesToPartyBased(partyData);
        loadClaims(chunkData);
    }

    private void migrateAlliesToPartyBased(PartyManagerData partyData) {
        for (Party party : partyData.getAllParties()) {
            Set<UUID> oldAllies = new LinkedHashSet<>(party.getAllies());
            Set<UUID> oldEnemies = new LinkedHashSet<>(party.getEnemies());
            boolean changed = false;

            for (UUID id : oldAllies) {
                // Check if this UUID is already a party UUID
                if (partyData.getParty(id) != null) continue;
                // It's a player UUID — find their party
                Party playerParty = partyData.getPartyByPlayer(id);
                if (playerParty != null) {
                    party.removeAlly(id);
                    party.addAlly(playerParty.getPartyId());
                    changed = true;
                } else {
                    party.removeAlly(id);
                    changed = true;
                    ModLog.IO.info("Dropped orphaned ally player {} from party {}", id, party.getName());
                }
            }

            for (UUID id : oldEnemies) {
                if (partyData.getParty(id) != null) continue;
                Party playerParty = partyData.getPartyByPlayer(id);
                if (playerParty != null) {
                    party.removeEnemy(id);
                    party.addEnemy(playerParty.getPartyId());
                    changed = true;
                } else {
                    party.removeEnemy(id);
                    changed = true;
                    ModLog.IO.info("Dropped orphaned enemy player {} from party {}", id, party.getName());
                }
            }

            if (changed) {
                ModLog.IO.info("Migrated allies/enemies for party {} to party-based UUIDs", party.getName());
            }
        }
    }

    public synchronized void saveAll() {
        dirty = false;
        saveAllInternal();
    }

    private void saveAllInternal() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || dataDir == null) return;
        PartyManagerData partyData = PartyManagerData.getInstance();
        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        saveConfig(partyData);
        saveParties(partyData);
        saveClaims(chunkData, partyData);
    }
}
