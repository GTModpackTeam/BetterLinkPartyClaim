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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.TrustAction;
import com.github.gtexpert.blpc.common.party.TrustLevel;

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

    private static boolean isChunkClaimed(int chunkX, int chunkZ) {
        return ChunkManagerData.getInstance().getClaim(chunkX, chunkZ) != null;
    }

    @Nullable
    private static Party getPartyForClaim(ClaimedChunkData claim) {
        return PartyManagerData.getInstance().getPartyByPlayer(claim.ownerUUID);
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
     * @return {@code true} if the action is allowed
     */
    private static boolean canPlayerActAt(@Nullable EntityPlayer player, BlockPos pos, TrustAction action) {
        if (!ModConfig.Defaults.enableProtection) return true;

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ClaimedChunkData claim = ChunkManagerData.getInstance().getClaim(chunkX, chunkZ);

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

        TrustLevel effectiveLevel = party.getEffectiveTrustLevel(player.getUniqueID());
        if (effectiveLevel == null) return false; // Enemy: null encodes "no trust"
        return effectiveLevel.isAtLeast(party.getTrustLevel(action));
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) return;
        if (isNameInList(event.getState().getBlock().getRegistryName(),
                ModConfig.protection.blockEditWhitelist))
            return;
        if (!canPlayerActAt(event.getPlayer(), event.getPos(), TrustAction.BLOCK_EDIT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isRemote) return;
        Entity entity = event.getEntity();
        EntityPlayer player = (entity instanceof EntityPlayer ep) ? ep : null;
        if (!canPlayerActAt(player, event.getPos(), TrustAction.BLOCK_EDIT)) {
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
        if (!canPlayerActAt(event.getEntityPlayer(), event.getPos(), TrustAction.BLOCK_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote) return;
        BlockPos playerPos = event.getEntityPlayer().getPosition();
        int cx = playerPos.getX() >> 4;
        int cz = playerPos.getZ() >> 4;
        if (isChunkClaimed(cx, cz)) {
            ItemStack held = event.getItemStack();
            if (!held.isEmpty() && isNameInList(held.getItem().getRegistryName(),
                    ModConfig.protection.itemUseBlacklist)) {
                event.setCanceled(true);
                return;
            }
        }
        if (!canPlayerActAt(event.getEntityPlayer(), playerPos, TrustAction.USE_ITEM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) return;
        if (!ModConfig.Defaults.enableProtection) return;

        Map<Long, Boolean> chunkProtectCache = new HashMap<>();

        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        affectedBlocks.removeIf(pos -> {
            long key = ChunkManagerData.chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            return chunkProtectCache.computeIfAbsent(key, k -> shouldProtectChunk(k));
        });

        Iterator<Entity> entityIt = event.getAffectedEntities().iterator();
        while (entityIt.hasNext()) {
            Entity entity = entityIt.next();
            long key = ChunkManagerData.chunkKey(
                    MathHelper.floor(entity.posX) >> 4,
                    MathHelper.floor(entity.posZ) >> 4);
            if (chunkProtectCache.computeIfAbsent(key, k -> shouldProtectChunk(k))) {
                entityIt.remove();
            }
        }
    }

    private static boolean shouldProtectChunk(long key) {
        int cx = (int) (key >> 32);
        int cz = (int) key;
        ClaimedChunkData claim = ChunkManagerData.getInstance().getClaim(cx, cz);
        if (claim == null) return false;
        Party party = getPartyForClaim(claim);
        return party == null || party.protectsExplosions();
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isRemote) return;
        if (!canPlayerActAt(event.getEntityPlayer(), event.getTarget().getPosition(),
                TrustAction.BLOCK_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getWorld().isRemote) return;
        if (!canPlayerActAt(event.getEntityPlayer(), event.getTarget().getPosition(),
                TrustAction.BLOCK_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntityPlayer().world.isRemote) return;
        Entity target = event.getTarget();
        if (!canPlayerActAt(event.getEntityPlayer(), target.getPosition(), TrustAction.ATTACK_ENTITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!ModConfig.Defaults.enableProtection || !ModConfig.Defaults.protectMobGriefing) return;
        Entity entity = event.getEntity();
        if (entity.world.isRemote) return;

        int chunkX = MathHelper.floor(entity.posX) >> 4;
        int chunkZ = MathHelper.floor(entity.posZ) >> 4;
        if (isChunkClaimed(chunkX, chunkZ)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getWorld().isRemote) return;
        if (!ModConfig.Defaults.enableProtection) return;

        Entity entity = event.getEntity();
        EntityPlayer player = (entity instanceof EntityPlayer ep) ? ep : null;

        if (player != null) {
            if (!canPlayerActAt(player, event.getPos(), TrustAction.BLOCK_EDIT)) {
                event.setCanceled(true);
            }
        } else {
            if (!ModConfig.Defaults.protectMobGriefing) return;
            int chunkX = event.getPos().getX() >> 4;
            int chunkZ = event.getPos().getZ() >> 4;
            if (isChunkClaimed(chunkX, chunkZ)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getWorld().isRemote) return;
        if (!ModConfig.Defaults.enableProtection || !ModConfig.Defaults.protectFluidFlow) return;

        BlockPos targetPos = event.getPos();
        BlockPos liquidPos = event.getLiquidPos();

        int targetChunkX = targetPos.getX() >> 4;
        int targetChunkZ = targetPos.getZ() >> 4;
        int sourceChunkX = liquidPos.getX() >> 4;
        int sourceChunkZ = liquidPos.getZ() >> 4;

        ClaimedChunkData targetClaim = ChunkManagerData.getInstance().getClaim(targetChunkX, targetChunkZ);
        ClaimedChunkData sourceClaim = ChunkManagerData.getInstance().getClaim(sourceChunkX, sourceChunkZ);

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

        BlockPos firePos = event.getPos();
        int fireChunkX = firePos.getX() >> 4;
        int fireChunkZ = firePos.getZ() >> 4;

        if (!isChunkClaimed(fireChunkX, fireChunkZ)) {
            for (EnumFacing side : event.getNotifiedSides()) {
                BlockPos neighbor = firePos.offset(side);
                int nChunkX = neighbor.getX() >> 4;
                int nChunkZ = neighbor.getZ() >> 4;
                if (isChunkClaimed(nChunkX, nChunkZ)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }
}
