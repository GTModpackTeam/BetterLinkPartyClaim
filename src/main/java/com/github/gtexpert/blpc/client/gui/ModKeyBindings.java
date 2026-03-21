package com.github.gtexpert.blpc.client.gui;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import org.lwjgl.input.Keyboard;

public class ModKeyBindings {

    public static KeyBinding keyOpenMap;
    public static KeyBinding toggleMinimap;

    public static void init() {
        // "M" キーでマップを開く設定
        keyOpenMap = new KeyBinding("key.blpc.open_map", Keyboard.KEY_M, "key.categories.blpc");
        ClientRegistry.registerKeyBinding(keyOpenMap);
        // Use N for minimap toggle to avoid collision with open-map (M)
        toggleMinimap = new KeyBinding("key.blpc.toggle", Keyboard.KEY_N, "key.categories.blpc");
        ClientRegistry.registerKeyBinding(toggleMinimap);
    }
}
