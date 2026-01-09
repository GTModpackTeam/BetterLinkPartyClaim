package com.sysnote8.bquclaim.gui;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import org.lwjgl.input.Keyboard;

public class ModKeyBindings {

    public static KeyBinding keyOpenMap;

    public static void init() {
        // "M" キーでマップを開く設定
        keyOpenMap = new KeyBinding("key.chunkmod.open_map", Keyboard.KEY_M, "key.categories.chunkmod");
        ClientRegistry.registerKeyBinding(keyOpenMap);
    }
}
