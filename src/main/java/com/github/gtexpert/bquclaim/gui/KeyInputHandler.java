package com.github.gtexpert.bquclaim.gui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.factory.ClientGUI;

import com.github.gtexpert.bquclaim.ModConfig;
import com.github.gtexpert.bquclaim.Tags;

public class KeyInputHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (ModKeyBindings.keyOpenMap.isPressed()) {
            if (Minecraft.getMinecraft().currentScreen == null) {
                ClientGUI.open(new ChunkMapScreen());
            }
        }
        if (ModKeyBindings.toggleMinimap.isPressed()) {
            ModConfig.showMinimap = !ModConfig.showMinimap;
            ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }
    }
}
