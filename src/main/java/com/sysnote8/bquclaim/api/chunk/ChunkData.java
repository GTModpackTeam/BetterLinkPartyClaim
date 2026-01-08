package com.sysnote8.bquclaim.api.chunk;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.Chunk;

import java.util.Optional;

/**
 * Because raw chunk data is too big for serialize to json.
 */
public class ChunkData {
    public static final String TAG_DIM = "dim";
    public static final String TAG_CHUNK_X = "chunk_x";
    public static final String TAG_CHUNK_Z = "chunk_z";
    public final int x;
    public final int z;
    public final int dimId;

    public ChunkData(int dimId, int chunkX, int chunkZ) {
        this.dimId = dimId;
        this.x = chunkX;
        this.z = chunkZ;
    }

    public boolean equals(ChunkData data) {
        return this.x == data.x && this.z == data.z && this.dimId == data.dimId;
    }

    public boolean equals(Chunk chunk, int dimId) {
        return this.x == chunk.x && this.z == chunk.z && this.dimId == dimId;
    }

    public NBTTagCompound toNBTTag() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger(TAG_DIM, dimId);
        compound.setInteger(TAG_CHUNK_X, x);
        compound.setInteger(TAG_CHUNK_Z, z);
        return compound;
    }

    public static Optional<ChunkData> fromNBT(NBTTagCompound compound) {
        if (!compound.hasKey(TAG_DIM) || !compound.hasKey(TAG_CHUNK_X) || !compound.hasKey(TAG_CHUNK_Z))
            return Optional.empty();
        return Optional.of(new ChunkData(compound.getInteger(TAG_DIM), compound.getInteger(TAG_CHUNK_X), compound.getInteger(TAG_CHUNK_Z)));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        ChunkData chunkData = (ChunkData) o;
        return x == chunkData.x && z == chunkData.z && dimId == chunkData.dimId;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + z;
        result = 31 * result + dimId;
        return result;
    }

    @Override
    public String toString() {
        return "ChunkData{x=" + x + ", z=" + z + ", dimId=" + dimId + "}";
    }

    public static ChunkData fromChunk(Chunk chunk) {
        return new ChunkData(chunk.getWorld().provider.getDimension(), chunk.x, chunk.z);
    }
}
