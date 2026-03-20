package com.github.gtexpert.bquclaim.gui;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import org.lwjgl.input.Keyboard;

public class ModKeyBindings {

    public static KeyBinding keyOpenMap;
    public static KeyBinding toggleMinimap;

    public static void init() {
        // "M" キーでマップを開く設定
        keyOpenMap = new KeyBinding("key.chunkmod.open_map", Keyboard.KEY_M, "key.categories.chunkmod");
        ClientRegistry.registerKeyBinding(keyOpenMap);
        // Use N for minimap toggle to avoid collision with open-map (M)
        toggleMinimap = new KeyBinding("key.chunkmod.toggle", Keyboard.KEY_N, "key.categories.chunkmod");
        ClientRegistry.registerKeyBinding(toggleMinimap);
    }
}
