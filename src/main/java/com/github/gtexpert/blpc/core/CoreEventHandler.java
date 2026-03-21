package com.github.gtexpert.blpc.core;

import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.github.gtexpert.blpc.common.BLPCSaveHandler;

public class CoreEventHandler {

    @SubscribeEvent
    public static void onWorldSave(WorldEvent.Save event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            BLPCSaveHandler.INSTANCE.saveAll();
        }
    }
}
