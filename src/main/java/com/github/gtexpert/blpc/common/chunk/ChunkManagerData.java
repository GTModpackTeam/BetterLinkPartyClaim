package com.github.gtexpert.blpc.common.chunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.network.message.SyncClaims;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

/**
 * Server-side chunk claim storage. Singleton, persisted by {@link com.github.gtexpert.blpc.common.BLPCSaveHandler}.
 * Claims are keyed by {@link ChunkKey} (x, z, dim) and grouped per-party in save files.
 */
public class ChunkManagerData {

    private static volatile ChunkManagerData instance;

    private final Map<ChunkKey, ClaimedChunkData> claims = new ConcurrentHashMap<>();
    private final Queue<ClaimedChunkData> pendingClaims = new ConcurrentLinkedQueue<>();

    public static synchronized ChunkManagerData getInstance() {
        if (instance == null) {
            instance = new ChunkManagerData();
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = new ChunkManagerData();
    }

    /** Composite key for a claimed chunk: chunk x/z coordinates plus dimension id. */
    public static final class ChunkKey {

        public final int x, z, dim;

        public ChunkKey(int x, int z, int dim) {
            this.x = x;
            this.z = z;
            this.dim = dim;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ChunkKey other && other.x == x && other.z == z && other.dim == dim;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z, dim);
        }

        @Override
        public String toString() {
            return dim + ":" + x + ":" + z;
        }
    }

    public static ChunkKey chunkKey(int x, int z, int dim) {
        return new ChunkKey(x, z, dim);
    }

    public ClaimedChunkData getClaim(int x, int z, int dim) {
        return claims.get(chunkKey(x, z, dim));
    }

    public void setClaim(int x, int z, int dim, UUID owner, String name, String partyName, boolean isForceLoaded) {
        ChunkKey key = chunkKey(x, z, dim);
        if (owner == null) {
            claims.remove(key);
        } else {
            claims.put(key, new ClaimedChunkData(x, z, dim, owner, name, partyName, isForceLoaded));
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
        return countMatching(d -> d.ownerUUID.equals(owner));
    }

    public int countForceLoads(UUID owner) {
        return countMatching(d -> d.ownerUUID.equals(owner) && d.isForceLoaded);
    }

    public int countClaimsForParty(UUID partyId) {
        Set<UUID> memberIds = memberIdsOf(partyId);
        if (memberIds == null) return 0;
        return countMatching(d -> memberIds.contains(d.ownerUUID));
    }

    public int countForceLoadsForParty(UUID partyId) {
        Set<UUID> memberIds = memberIdsOf(partyId);
        if (memberIds == null) return 0;
        return countMatching(d -> memberIds.contains(d.ownerUUID) && d.isForceLoaded);
    }

    private int countMatching(Predicate<ClaimedChunkData> filter) {
        return (int) claims.values().stream().filter(filter).count();
    }

    private static Set<UUID> memberIdsOf(UUID partyId) {
        var party = PartyManagerData.getInstance().getParty(partyId);
        return party != null ? new HashSet<>(party.getMemberUUIDs()) : null;
    }

    public void enqueueClaim(ClaimedChunkData data) {
        pendingClaims.add(data);
    }

    public void flushPending() {
        ClaimedChunkData d;
        while ((d = pendingClaims.poll()) != null) {
            claims.put(chunkKey(d.x, d.z, d.dim), d);
        }
    }

    /**
     * Removes all claims and force-loads for the given player, broadcasting unclaim messages.
     * <p>
     * Releases the force-load ticket in the claim's own dimension, since claims (and tickets)
     * are now tracked per-dimension.
     */
    public void releaseAllClaims(UUID owner, World world) {
        for (ClaimedChunkData claim : getClaimsByOwner(owner)) {
            if (claim.isForceLoaded) {
                for (WorldServer ws : FMLCommonHandler.instance().getMinecraftServerInstance().worlds) {
                    if (ws.provider.getDimension() == claim.dim) {
                        TicketManager.unforceChunk(ws, claim.x, claim.z);
                    }
                }
            }
            setClaim(claim.x, claim.z, claim.dim, null, "", "", false);
            ModNetwork.INSTANCE.sendToAll(new SyncClaims(claim.x, claim.z, claim.dim, null, "", "", false));
        }
    }

    /** Releases all claims for every UUID in the collection. Convenience wrapper over {@link #releaseAllClaims}. */
    public void releaseAllMemberClaims(Collection<UUID> memberIds, World world) {
        for (UUID memberId : memberIds) {
            releaseAllClaims(memberId, world);
        }
    }

    /**
     * Transfers all chunk claims from {@code oldOwner} to {@code newOwner}.
     * Used when merging offline/online UUIDs on player login.
     */
    public void transferOwnership(UUID oldOwner, UUID newOwner) {
        for (ClaimedChunkData claim : getClaimsByOwner(oldOwner)) {
            claims.put(
                    chunkKey(claim.x, claim.z, claim.dim),
                    new ClaimedChunkData(
                            claim.x, claim.z, claim.dim, newOwner, claim.ownerName, claim.partyName,
                            claim.isForceLoaded));
        }
    }

    public NBTTagCompound serializeAll() {
        NBTTagCompound all = new NBTTagCompound();
        for (Map.Entry<ChunkKey, ClaimedChunkData> entry : claims.entrySet()) {
            all.setTag(entry.getKey().toString(), entry.getValue().toNBT());
        }
        return all;
    }
}
