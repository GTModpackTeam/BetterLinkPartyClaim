package com.github.gtexpert.blpc.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.factory.ClientGUI;

import com.github.gtexpert.blpc.common.ModDefaults;

public class KeyInputHandler {

    /** Runtime toggle state for the minimap HUD. Initialised from the compile-time default. */
    private static boolean minimapVisible = ModDefaults.showMinimap;

    /** Returns whether the minimap HUD should be rendered in the current session. */
    public static boolean isMinimapVisible() {
        return minimapVisible;
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (ModKeyBindings.keyOpenMap.isPressed()) {
            if (Minecraft.getMinecraft().currentScreen == null) {
                ClientGUI.open(new ChunkMapScreen());
            }
        }
        if (ModKeyBindings.toggleMinimap.isPressed()) {
            minimapVisible = !minimapVisible;
        }
    }
}
