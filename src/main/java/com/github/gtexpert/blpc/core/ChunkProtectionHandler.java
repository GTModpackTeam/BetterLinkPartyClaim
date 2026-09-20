package com.github.gtexpert.blpc.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.party.TrustAction;
import com.github.gtexpert.blpc.api.party.TrustLevel;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;

/**
 * Central Forge event handler for chunk protection.
 * <p>
 * Subscribes to block, entity, explosion, and environmental events and denies
 * actions in claimed chunks based on the acting player's effective
 * {@link TrustLevel} versus the party's configured required level for the
 * corresponding {@link TrustAction}. OP players (permission level 2+) bypass
 * all protection checks.
 * <p>
 * Additional protections (mob griefing, fluid flow, fire spread, farmland
 * trampling) are gated by {@link com.github.gtexpert.blpc.common.ModConfig.Defaults ModConfig.Defaults}
 * flags.
 */
public class ChunkProtectionHandler {

    private static boolean isChunkClaimed(int chunkX, int chunkZ, int dim) {
        return ChunkManagerData.getInstance().getClaim(chunkX, chunkZ, dim) != null;
    }

    /**
     * Resolves the claim owner's effective party via the active {@link PartyProviderRegistry}
     * provider rather than reading {@code PartyManagerData} directly — a BQu-linked owner who
     * joined their party purely through BQu's own UI has no BLPC-side {@link Party} record, and
     * a raw {@code PartyManagerData} lookup would incorrectly resolve to "no party" for them.
     */
    @Nullable
    private static Party getPartyForClaim(ClaimedChunkData claim) {
        return PartyProviderRegistry.get().getEffectiveParty(claim.ownerUUID);
    }

    private static boolean isNameInList(@Nullable ResourceLocation name, String[] list) {
        if (name == null || list.length == 0) return false;
        String nameStr = name.toString();
        for (String entry : list) {
            if (nameStr.equals(entry)) return true;
        }
        return false;
    }

    /**
     * Determines whether a player is allowed to perform the given action at a position.
     * <p>
     * Returns {@code true} (allowed) when any of the following hold:
     * <ol>
     * <li>Protection is globally disabled ({@code ModConfig.Defaults.enableProtection == false})</li>
     * <li>The chunk is unclaimed</li>
     * <li>The player is an OP (permission level 2+)</li>
     * <li>The player is the claim owner</li>
     * <li>The player's effective trust level meets the party's required level for the action</li>
     * </ol>
     * FakePlayers are checked against the party's dedicated fake-player trust level.
     *
     * @param player the acting player, or {@code null} for non-player entities
     * @param pos    the block position to check
     * @param action the action being attempted
     * @param dim    the dimension id the position is in
     * @return {@code true} if the action is allowed
     */
    private static boolean canPlayerActAt(@Nullable EntityPlayer player, BlockPos pos, TrustAction action, int dim) {
        if (!ModConfig.Defaults.enableProtection) return true;

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ClaimedChunkData claim = ChunkManagerData.getInstance().getClaim(chunkX, chunkZ, dim);

        if (claim == null) return true;
        if (player == null) return false;
        if (player.canUseCommand(2, "")) return true;
        if (claim.ownerUUID.equals(player.getUniqueID())) return true;

        Party party = getPartyForClaim(claim);

        if (player instanceof FakePlayer) {
            if (party == null) return false;
            TrustLevel fakeLevel = party.getFakePlayerTrustLevel();
            return fakeLevel.isAtLeast(party.getTrustLevel(action));
        }

        if (party == null) return false;

        var playerParty = PartyProviderRegistry.get().getEffectiveParty(player.getUniqueID());
        var playerPartyId = playerParty != null ? playerParty.getPartyId() : null;
        TrustLevel effectiveLevel = party.getEffectiveTrustLevel(player.getUniqueID(), playerPartyId);
        if (effectiveLevel == null) return false; // Enemy: null encodes "no trust"
        return effectiveLevel.isAtLeast(party.getTrustLevel(action));
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) return;
        if (isNameInList(event.getState().getBlock().getRegistryName(),
                ModConfig.protection.blockEditWhitelist))
            return;
        int dim = event.getWorld().provider.getDimension();
        if (!canPlayerActAt(event.getPlayer(), event.getPos(), TrustAction.BLOCK_EDIT, dim)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isRemote) return;
        Entity entity = event.getEntity();
        EntityPlayer player = (entity instanceof EntityPlayer ep) ? ep : null;
        int dim = event.getWorld().provider.getDimension();
        if (!canPlayerActAt(player, event.getPos(), TrustAction.BLOCK_EDIT, dim)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote) return;
        IBlockState state = event.getWorld().getBlockState(event.getPos());
        if (isNameInList(state.getBlock().getRegistryName(),
                ModConfig.protection.blockInteractWhitelist))
            return;
        int dim = event.getWorld().provider.getDimension();
        if (!canPlayerActAt(event.getEntityPlayer(), event.getPos(), TrustAction.BLOCK_INTERACT, dim)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote) return;
        BlockPos playerPos = event.getEntityPlayer().getPosition();
        int cx = playerPos.getX() >> 4;
        int cz = playerPos.getZ() >> 4;
        int dim = event.getWorld().provider.getDimension();
        if (isChunkClaimed(cx, cz, dim)) {
            ItemStack held = event.getItemStack();
            if (!held.isEmpty() && isNameInList(held.getItem().getRegistryName(),
                    ModConfig.protection.itemUseBlacklist)) {
                event.setCanceled(true);
                return;
            }
        }
        if (!canPlayerActAt(event.getEntityPlayer(), playerPos, TrustAction.USE_ITEM, dim)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) return;
        if (!ModConfig.Defaults.enableProtection) return;

        int dim = event.getWorld().provider.getDimension();
        Map<Long, Boolean> chunkProtectCache = new HashMap<>();

        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        affectedBlocks.removeIf(pos -> {
            long key = packChunkXZ(pos.getX() >> 4, pos.getZ() >> 4);
            return chunkProtectCache.computeIfAbsent(key, k -> shouldProtectChunk(k, dim));
        });

        Iterator<Entity> entityIt = event.getAffectedEntities().iterator();
        while (entityIt.hasNext()) {
            Entity entity = entityIt.next();
            long key = packChunkXZ(
                    MathHelper.floor(entity.posX) >> 4,
                    MathHelper.floor(entity.posZ) >> 4);
            if (chunkProtectCache.computeIfAbsent(key, k -> shouldProtectChunk(k, dim))) {
                entityIt.remove();
            }
        }
    }

    private static long packChunkXZ(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static boolean shouldProtectChunk(long key, int dim) {
        int cx = (int) (key >> 32);
        int cz = (int) key;
        ClaimedChunkData claim = ChunkManagerData.getInstance().getClaim(cx, cz, dim);
        if (claim == null) return false;
        Party party = getPartyForClaim(claim);
        return party == null || party.protectsExplosions();
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isRemote) return;
        int dim = event.getWorld().provider.getDimension();
        if (!canPlayerActAt(event.getEntityPlayer(), event.getTarget().getPosition(),
                TrustAction.BLOCK_INTERACT, dim)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getWorld().isRemote) return;
        int dim = event.getWorld().provider.getDimension();
        if (!canPlayerActAt(event.getEntityPlayer(), event.getTarget().getPosition(),
                TrustAction.BLOCK_INTERACT, dim)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntityPlayer().world.isRemote) return;
        Entity target = event.getTarget();
        int dim = event.getEntityPlayer().world.provider.getDimension();
        if (!canPlayerActAt(event.getEntityPlayer(), target.getPosition(), TrustAction.ATTACK_ENTITY, dim)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!ModConfig.Defaults.enableProtection || !ModConfig.Defaults.protectMobGriefing) return;

        Entity entity = event.getEntity();
        World world = entity != null ? entity.world : null;

        if (world == null || world.isRemote) return;

        int chunkX = MathHelper.floor(entity.posX) >> 4;
        int chunkZ = MathHelper.floor(entity.posZ) >> 4;
        int dim = world.provider.getDimension();
        if (isChunkClaimed(chunkX, chunkZ, dim)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getWorld().isRemote) return;
        if (!ModConfig.Defaults.enableProtection) return;

        Entity entity = event.getEntity();
        EntityPlayer player = (entity instanceof EntityPlayer ep) ? ep : null;
        int dim = event.getWorld().provider.getDimension();

        if (player != null) {
            if (!canPlayerActAt(player, event.getPos(), TrustAction.BLOCK_EDIT, dim)) {
                event.setCanceled(true);
            }
        } else {
            if (!ModConfig.Defaults.protectMobGriefing) return;
            int chunkX = event.getPos().getX() >> 4;
            int chunkZ = event.getPos().getZ() >> 4;
            if (isChunkClaimed(chunkX, chunkZ, dim)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getWorld().isRemote) return;
        if (!ModConfig.Defaults.enableProtection || !ModConfig.Defaults.protectFluidFlow) return;

        int dim = event.getWorld().provider.getDimension();
        BlockPos targetPos = event.getPos();
        BlockPos liquidPos = event.getLiquidPos();

        int targetChunkX = targetPos.getX() >> 4;
        int targetChunkZ = targetPos.getZ() >> 4;
        int sourceChunkX = liquidPos.getX() >> 4;
        int sourceChunkZ = liquidPos.getZ() >> 4;

        ClaimedChunkData targetClaim = ChunkManagerData.getInstance().getClaim(targetChunkX, targetChunkZ, dim);
        ClaimedChunkData sourceClaim = ChunkManagerData.getInstance().getClaim(sourceChunkX, sourceChunkZ, dim);

        boolean targetClaimed = targetClaim != null;
        boolean sourceClaimed = sourceClaim != null;

        if (targetClaimed && (!sourceClaimed || !targetClaim.ownerUUID.equals(sourceClaim.ownerUUID))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getWorld().isRemote) return;
        if (!ModConfig.Defaults.enableProtection || !ModConfig.Defaults.protectFireSpread) return;

        IBlockState state = event.getState();
        if (state.getBlock() != Blocks.FIRE) return;

        int dim = event.getWorld().provider.getDimension();
        BlockPos firePos = event.getPos();
        int fireChunkX = firePos.getX() >> 4;
        int fireChunkZ = firePos.getZ() >> 4;

        if (!isChunkClaimed(fireChunkX, fireChunkZ, dim)) {
            for (EnumFacing side : event.getNotifiedSides()) {
                BlockPos neighbor = firePos.offset(side);
                int nChunkX = neighbor.getX() >> 4;
                int nChunkZ = neighbor.getZ() >> 4;
                if (isChunkClaimed(nChunkX, nChunkZ, dim)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }
}
