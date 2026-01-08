package com.sysnote8.bquclaim.claim;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.sysnote8.bquclaim.BQuClaim;
import com.sysnote8.bquclaim.Tags;

/**
 * EventHandler for claimed land
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public class ClaimEventHandler {

    private static boolean needsCancelEvent(Chunk chunk, UUID playerUUID) {
        return !BQuClaim.claimManager.canPlayerInteractChunk(chunk, playerUUID);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityAttacked(AttackEntityEvent event) {
        Chunk chunk = event.getTarget().getEntityWorld().getChunk(event.getTarget().getPosition());
        UUID playerUUID = event.getEntity().getUniqueID();
        if (needsCancelEvent(chunk, playerUUID)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (needsCancelEvent(event.getWorld().getChunk(event.getPos()), event.getEntity().getUniqueID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (needsCancelEvent(event.getWorld().getChunk(event.getPos()), event.getEntity().getUniqueID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreakBlock(BlockEvent.BreakEvent event) {
        if (needsCancelEvent(event.getWorld().getChunk(event.getPos()), event.getPlayer().getUniqueID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof EntityPlayer player) {
            if (needsCancelEvent(event.getWorld().getChunk(event.getPos()), player.getUniqueID())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (needsCancelEvent(event.getWorld().getChunk(event.getPos()), event.getEntity().getUniqueID())) {
            event.setCanceled(true);
        }
    }
}
