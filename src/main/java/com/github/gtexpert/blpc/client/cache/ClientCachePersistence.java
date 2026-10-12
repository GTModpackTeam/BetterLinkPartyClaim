package com.github.gtexpert.blpc.client.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.ClientClaimCache;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;

/**
 * Persists {@link ClientClaimCache} and {@link ClientPartyCache} to disk, keyed by the
 * currently connected server ({@link ClientCacheKey}), so the map/party UI can show the
 * last-known state immediately after reconnecting instead of an empty screen while the
 * server's fresh sync is in flight.
 * <p>
 * Writes are debounced: every cache change schedules a single delayed write, and a change
 * that arrives before the delay elapses cancels and reschedules it. This keeps disk I/O off
 * the hot path (a bulk {@code SyncAllClaims}/{@code PartySync} triggers many individual
 * cache updates) while still surviving anything short of a hard process kill.
 * <p>
 * {@link ClientClaimCache} and {@link ClientPartyCache} are plain, non-thread-safe collections
 * that the rest of the codebase only ever touches from the client main thread (GUI click
 * handlers, {@code MainThreadMessageHandler}-based sync handlers). Every method here that reads
 * or mutates them is therefore required to run on the main thread too: public entry points hop
 * onto it via {@link Minecraft#addScheduledTask} before touching cache state, and the debounce
 * timer (which fires on a background thread) only decides *when* to hop back, never touches the
 * caches itself.
 */
@SideOnly(Side.CLIENT)
public final class ClientCachePersistence {

    private static final long DEBOUNCE_MS = 2000;
    private static final String CLAIMS_FILE = "claims.dat";
    private static final String PARTIES_FILE = "parties.dat";

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BLPC-ClientCacheSave");
        t.setDaemon(true);
        return t;
    });
    private static final ScheduledExecutorService IO_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BLPC-ClientCacheIO");
        t.setDaemon(true);
        return t;
    });

    private static ScheduledFuture<?> pendingSave;
    private static final Runnable claimListener = ClientCachePersistence::scheduleSave;
    private static final Runnable partyListener = ClientCachePersistence::scheduleSave;

    // Captured at connect and reused by saves: a save deferred past disconnect can't rely on
    // ClientCacheKey.current(), which may already read null (e.g. singleplayer world unload).
    private static volatile String connectedKey;

    private ClientCachePersistence() {}

    /** Registers the debounced auto-save listeners. Must be called on the main thread. */
    public static void register() {
        ClientClaimCache.addChangeListener(claimListener);
        ClientPartyCache.addSyncListener(partyListener);
    }

    /** Unregisters the debounced auto-save listeners. Must be called on the main thread. */
    public static void unregister() {
        ClientClaimCache.removeChangeListener(claimListener);
        ClientPartyCache.removeSyncListener(partyListener);
        connectedKey = null;
    }

    /**
     * Loads any previously saved cache for the current connection. Must be called on the main
     * thread, before the server's fresh sync arrives (so the fresh sync naturally overwrites
     * this best-effort snapshot). Also captures the current cache key for the lifetime of the
     * connection, so subsequent saves don't depend on live connection state.
     */
    public static void loadForCurrentServer() {
        connectedKey = ClientCacheKey.current();
        if (connectedKey == null) return;

        File dir = serverDir(connectedKey);
        loadClaims(new File(dir, CLAIMS_FILE));
        loadParties(new File(dir, PARTIES_FILE));
    }

    /** Cancels any pending debounced save. Safe to call from any thread. */
    public static synchronized void cancelPending() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
    }

    /**
     * Immediately snapshots and persists the current cache contents, bypassing the debounce
     * delay. Must be called on the main thread (typically on disconnect, before the caches are
     * cleared).
     */
    public static void saveNow() {
        cancelPending();
        snapshotAndWrite();
    }

    /** Debounce-timer callback (background thread) — only re-arms the main-thread hop. */
    private static synchronized void scheduleSave() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
        }
        pendingSave = SCHEDULER.schedule(
                () -> Minecraft.getMinecraft().addScheduledTask(ClientCachePersistence::snapshotAndWrite),
                DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    /** Builds the NBT snapshot on the calling (main) thread, then hands file I/O to a background thread. */
    private static void snapshotAndWrite() {
        String key = connectedKey;
        if (key == null) return;

        NBTTagCompound claimsNbt = buildClaimsNBT();
        NBTTagCompound partiesNbt = buildPartiesNBT();

        IO_EXECUTOR.execute(() -> {
            File dir = serverDir(key);
            dir.mkdirs();
            writeCompressedAtomic(new File(dir, CLAIMS_FILE), claimsNbt);
            writeCompressedAtomic(new File(dir, PARTIES_FILE), partiesNbt);
        });
    }

    private static File serverDir(String key) {
        return new File(Minecraft.getMinecraft().gameDir, "blpc/cache/" + key);
    }

    // --- Claims ---

    private static void loadClaims(File file) {
        if (!file.exists()) return;
        try (FileInputStream fis = new FileInputStream(file)) {
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(fis);
            NBTTagList list = nbt.getTagList("claims", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                ClaimedChunkData d = ClaimedChunkData.fromNBT(list.getCompoundTagAt(i));
                if (d == null) continue;
                ClientClaimCache.update(d.x, d.z, d.dim, d.ownerUUID, d.ownerName, d.partyName, d.isForceLoaded);
            }
        } catch (IOException e) {
            ModLog.IO.warn("Failed to load cached claims from {}", file.getName(), e);
        }
    }

    private static NBTTagCompound buildClaimsNBT() {
        NBTTagList list = new NBTTagList();
        for (ClaimedChunkData claim : ClientClaimCache.getAll()) {
            list.appendTag(claim.toNBT());
        }
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("claims", list);
        return nbt;
    }

    // --- Parties ---

    private static void loadParties(File file) {
        if (!file.exists()) return;
        try (FileInputStream fis = new FileInputStream(file)) {
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(fis);
            ClientPartyCache.loadFromNBT(nbt);
        } catch (IOException e) {
            ModLog.IO.warn("Failed to load cached parties from {}", file.getName(), e);
        }
    }

    private static NBTTagCompound buildPartiesNBT() {
        NBTTagCompound nbt = new NBTTagCompound();

        NBTTagList partyList = new NBTTagList();
        for (Party party : ClientPartyCache.getAllParties()) {
            partyList.appendTag(party.toNBT());
        }
        nbt.setTag("parties", partyList);

        NBTTagList linkedList = new NBTTagList();
        for (UUID uuid : ClientPartyCache.getBQuLinkedPlayers()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId("uuid", uuid);
            linkedList.appendTag(entry);
        }
        nbt.setTag("bquLinked", linkedList);

        return nbt;
    }

    // --- I/O (background thread only) ---

    // Unlike BLPCSaveHandler's server-side sibling of the same name, this intentionally skips
    // fos.getFD().sync() before rename: this cache is a best-effort UX convenience, not
    // authoritative data — the server remains the source of truth and re-syncs on reconnect, so
    // losing the last few buffered KB on a hard crash is an acceptable trade for cheaper writes.
    private static void writeCompressedAtomic(File file, NBTTagCompound nbt) {
        File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            CompressedStreamTools.writeCompressed(nbt, fos);
        } catch (IOException e) {
            ModLog.IO.warn("Failed to write client cache file {}", file.getName(), e);
            tmpFile.delete();
            return;
        }
        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ModLog.IO.warn("Failed to finalize client cache file {}", file.getName(), e);
        }
    }
}
