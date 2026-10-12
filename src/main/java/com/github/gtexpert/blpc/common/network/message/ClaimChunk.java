package com.github.gtexpert.blpc.common.network.message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.github.gtexpert.blpc.api.event.ChunkModifiedEvent;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.TicketManager;
import com.github.gtexpert.blpc.common.network.ModNetwork;

import io.netty.buffer.ByteBuf;

/** C→S: Request to claim/unclaim/force-load a chunk. */
public class ClaimChunk implements IMessage {

    public static final int MODE_CLAIM = 0;
    public static final int MODE_UNCLAIM = 1;
    public static final int MODE_TOGGLE_FORCE = 2;

    private static final int MAX_CHUNK_DISTANCE = 64;

    private int x;
    private int z;
    private int dim;
    private int mode;

    public ClaimChunk() {}

    public ClaimChunk(int x, int z, int dim, int mode) {
        this.x = x;
        this.z = z;
        this.dim = dim;
        this.mode = mode;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
        this.mode = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
        buf.writeInt(this.mode);
    }

    public static class Handler implements IMessageHandler<ClaimChunk, IMessage> {

        @Override
        public IMessage onMessage(ClaimChunk message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler)
                    .addScheduledTask(() -> processOne(message, ctx.getServerHandler().player));
            return null;
        }

        /**
         * Validates and applies a single chunk mode change. Shared by {@link ClaimChunk} and {@link Batch}.
         */
        void processOne(ClaimChunk message, EntityPlayerMP player) {
            // Validate chunk coordinates - must be within reasonable distance
            int playerChunkX = MathHelper.floor(player.posX) >> 4;
            int playerChunkZ = MathHelper.floor(player.posZ) >> 4;
            if (Math.abs(message.x - playerChunkX) > MAX_CHUNK_DISTANCE ||
                    Math.abs(message.z - playerChunkZ) > MAX_CHUNK_DISTANCE) {
                return;
            }

            ChunkManagerData data = ChunkManagerData.getInstance();
            ClaimedChunkData existing = data.getClaim(message.x, message.z, message.dim);
            UUID playerId = player.getUniqueID();

            switch (message.mode) {
                case MODE_CLAIM -> handleClaim(message, player, data, existing, playerId);
                case MODE_UNCLAIM -> handleUnclaim(message, player, data, existing, playerId);
                case MODE_TOGGLE_FORCE -> handleToggleForce(message, player, data, existing, playerId);
            }
        }

        private void handleClaim(ClaimChunk msg, EntityPlayerMP player,
                                 ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing != null) return;
            if (isDimensionBlocked(msg.dim, player)) return;
            if (isPartyMissing(playerId, player)) return;
            if (isClaimLimitReached(data, playerId, player)) return;
            if (MinecraftForge.EVENT_BUS.post(
                    new ChunkModifiedEvent.Pre.Claim(msg.x, msg.z, playerId))) {
                return;
            }

            String partyName = resolveTeamName(playerId);
            var claimed = new ClaimedChunkData(msg.x, msg.z, msg.dim, playerId, player.getName(), partyName, false);
            data.enqueueClaim(claimed);
            syncToAll(msg.x, msg.z, msg.dim, playerId, player.getName(), partyName, false);
            BLPCSaveHandler.INSTANCE.markDirty();
            MinecraftForge.EVENT_BUS.post(new ChunkModifiedEvent.Post.Claim(msg.x, msg.z, playerId));
        }

        private void handleUnclaim(ClaimChunk msg, EntityPlayerMP player,
                                   ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing == null) return;
            if (!isOwnerOrOp(existing, player, playerId)) return;
            if (MinecraftForge.EVENT_BUS.post(
                    new ChunkModifiedEvent.Pre.Unclaim(msg.x, msg.z, existing.ownerUUID))) {
                return;
            }

            if (existing.isForceLoaded) {
                TicketManager.unforceChunk(player.world, msg.x, msg.z);
            }
            data.setClaim(msg.x, msg.z, msg.dim, null, "", "", false);
            syncToAll(msg.x, msg.z, msg.dim, null, "", "", false);
            BLPCSaveHandler.INSTANCE.markDirty();
            MinecraftForge.EVENT_BUS.post(new ChunkModifiedEvent.Post.Unclaim(msg.x, msg.z, existing.ownerUUID));
        }

        private void handleToggleForce(ClaimChunk msg, EntityPlayerMP player,
                                       ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing == null) {
                if (isDimensionBlocked(msg.dim, player)) return;
                if (isPartyMissing(playerId, player)) return;
                if (isClaimLimitReached(data, playerId, player)) return;
                if (isForceLoadLimitReached(data, playerId, player)) return;
                if (MinecraftForge.EVENT_BUS.post(
                        new ChunkModifiedEvent.Pre.Claim(msg.x, msg.z, playerId))) {
                    return;
                }

                String partyName = resolveTeamName(playerId);
                boolean forced = TicketManager.forceChunk(player.world, msg.x, msg.z, null);
                var claimed = new ClaimedChunkData(
                        msg.x, msg.z, msg.dim, playerId, player.getName(), partyName, forced);
                data.enqueueClaim(claimed);
                syncToAll(msg.x, msg.z, msg.dim, playerId, player.getName(), partyName, forced);
                BLPCSaveHandler.INSTANCE.markDirty();
                MinecraftForge.EVENT_BUS.post(new ChunkModifiedEvent.Post.Claim(msg.x, msg.z, playerId));
                if (forced) {
                    MinecraftForge.EVENT_BUS.post(new ChunkModifiedEvent.Post.ForceLoad(msg.x, msg.z, playerId));
                }
            } else if (isOwnerOrOp(existing, player, playerId)) {
                toggleForceLoad(msg, player, data, existing, playerId);
            }
        }

        private void toggleForceLoad(ClaimChunk msg, EntityPlayerMP player,
                                     ChunkManagerData data, ClaimedChunkData existing, UUID playerId) {
            if (existing.isForceLoaded) {
                if (MinecraftForge.EVENT_BUS.post(
                        new ChunkModifiedEvent.Pre.Unforce(msg.x, msg.z, existing.ownerUUID))) {
                    return;
                }
                existing.isForceLoaded = false;
                TicketManager.unforceChunk(player.world, msg.x, msg.z);
                MinecraftForge.EVENT_BUS.post(
                        new ChunkModifiedEvent.Post.Unforce(msg.x, msg.z, existing.ownerUUID));
            } else {
                if (isForceLoadLimitReached(data, playerId, player)) return;
                if (MinecraftForge.EVENT_BUS.post(
                        new ChunkModifiedEvent.Pre.ForceLoad(msg.x, msg.z, existing.ownerUUID))) {
                    return;
                }
                boolean forced = TicketManager.forceChunk(player.world, msg.x, msg.z, null);
                if (!forced) return;
                existing.isForceLoaded = true;
                MinecraftForge.EVENT_BUS.post(
                        new ChunkModifiedEvent.Post.ForceLoad(msg.x, msg.z, existing.ownerUUID));
            }
            syncToAll(msg.x, msg.z, msg.dim, existing.ownerUUID, existing.ownerName, existing.partyName,
                    existing.isForceLoaded);
            BLPCSaveHandler.INSTANCE.markDirty();
        }

        /** Claiming requires a party — solo protection with no party to share/manage it is not supported. */
        private boolean isPartyMissing(UUID playerId, EntityPlayerMP player) {
            if (PartyProviderRegistry.get().getPartyId(playerId) != null) return false;
            ModNetwork.INSTANCE.sendTo(ClientNotify.claimFailed(ClientNotify.REASON_NO_PARTY, 0, 0), player);
            return true;
        }

        /** Some dimensions (e.g. The End) may be configured as off-limits for chunk claiming. */
        private boolean isDimensionBlocked(int dim, EntityPlayerMP player) {
            for (int blocked : ModConfig.claims.blockedClaimingDimensions) {
                if (blocked == dim) {
                    ModNetwork.INSTANCE.sendTo(
                            ClientNotify.claimFailed(ClientNotify.REASON_DIMENSION_BLOCKED, 0, 0), player);
                    return true;
                }
            }
            return false;
        }

        private boolean isClaimLimitReached(ChunkManagerData data, UUID playerId, EntityPlayerMP player) {
            return isLimitReached(data, playerId, player,
                    data::countClaims,
                    party -> data.countClaimsForParty(party.getPartyId()),
                    party -> party.sumClaimLimit(ModConfig.claims.maxClaimsPerPlayer),
                    ModConfig.claims.maxClaimsPerPlayer,
                    ClientNotify.REASON_CLAIM_LIMIT);
        }

        private boolean isForceLoadLimitReached(ChunkManagerData data, UUID playerId, EntityPlayerMP player) {
            return isLimitReached(data, playerId, player,
                    data::countForceLoads,
                    party -> data.countForceLoadsForParty(party.getPartyId()),
                    party -> party.sumForceLoadLimit(ModConfig.claims.maxForceLoadsPerPlayer),
                    ModConfig.claims.maxForceLoadsPerPlayer,
                    ClientNotify.REASON_FORCELOAD_LIMIT);
        }

        /**
         * Shared shape for claim/force-load limit checks: per-player counting, unless
         * {@link ModConfig.Claims#additiveLimits additiveLimits} is on and the player has a
         * party, in which case usage and the cap are pooled across the party instead. Resolves
         * the party via the active {@link PartyProviderRegistry} provider (not a raw
         * {@code PartyManagerData} lookup) so a BQu-linked player with no BLPC-side {@link Party}
         * record of their own still gets pooled with their real party instead of falling back to
         * a solo per-player cap.
         */
        private boolean isLimitReached(ChunkManagerData data, UUID playerId, EntityPlayerMP player,
                                       Function<UUID, Integer> perPlayerCount, Function<Party, Integer> perPartyCount,
                                       Function<Party, Integer> perPartyMax, int perPlayerMax, String reason) {
            Party party = ModConfig.claims.additiveLimits ?
                    PartyProviderRegistry.get().getEffectiveParty(playerId) : null;
            int used = party != null ? perPartyCount.apply(party) : perPlayerCount.apply(playerId);
            int max = party != null ? perPartyMax.apply(party) : perPlayerMax;
            if (used >= max) {
                ModNetwork.INSTANCE.sendTo(ClientNotify.claimFailed(reason, used, max), player);
                return true;
            }
            return false;
        }

        private boolean isOwnerOrOp(ClaimedChunkData claim, EntityPlayerMP player, UUID playerId) {
            return claim.ownerUUID.equals(playerId) || player.canUseCommand(2, "");
        }

        private String resolveTeamName(UUID playerId) {
            var name = PartyProviderRegistry.get().getPartyName(playerId);
            return name != null ? name : "";
        }

        private void syncToAll(
                               int x, int z, int dim, UUID owner, String name, String partyName,
                               boolean forceLoaded) {
            ModNetwork.INSTANCE.sendToAll(new SyncClaims(x, z, dim, owner, name, partyName, forceLoaded));
        }
    }

    /**
     * C→S: Request to claim/unclaim/force-load multiple chunks in one round trip. Used by the map
     * GUI's drag-select instead of sending one {@link ClaimChunk} per chunk.
     */
    public static class Batch implements IMessage {

        /** Drag-select on a GRID-sized map can't exceed this many chunks; also a sanity cap on inbound size. */
        private static final int MAX_CHUNKS = 1024;

        private int dim;
        private int mode;
        private int[] xs;
        private int[] zs;

        public Batch() {}

        public Batch(int dim, int mode, int[] xs, int[] zs) {
            this.dim = dim;
            this.mode = mode;
            this.xs = xs;
            this.zs = zs;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.dim = buf.readInt();
            this.mode = buf.readInt();
            int sent = Math.max(0, buf.readInt());
            int count = Math.min(sent, MAX_CHUNKS);
            this.xs = new int[count];
            this.zs = new int[count];
            for (int i = 0; i < count; i++) {
                xs[i] = buf.readInt();
                zs[i] = buf.readInt();
            }
            // A well-behaved client never exceeds MAX_CHUNKS; drain any excess (capped to what's
            // actually left in the buffer) so a malicious oversized count can't overflow the skip.
            buf.skipBytes((int) Math.min((long) (sent - count) * 8L, buf.readableBytes()));
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dim);
            buf.writeInt(mode);
            int count = Math.min(xs.length, MAX_CHUNKS);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeInt(xs[i]);
                buf.writeInt(zs[i]);
            }
        }

        public static class Handler implements IMessageHandler<Batch, IMessage> {

            private final ClaimChunk.Handler delegate = new ClaimChunk.Handler();

            @Override
            public IMessage onMessage(Batch message, MessageContext ctx) {
                EntityPlayerMP player = ctx.getServerHandler().player;
                FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                    for (ClaimChunk single : toSingleMessages(message)) {
                        delegate.processOne(single, player);
                    }
                });
                return null;
            }

            private List<ClaimChunk> toSingleMessages(Batch message) {
                List<ClaimChunk> messages = new ArrayList<>(message.xs.length);
                for (int i = 0; i < message.xs.length; i++) {
                    messages.add(new ClaimChunk(message.xs[i], message.zs[i], message.dim, message.mode));
                }
                return messages;
            }
        }
    }
}
