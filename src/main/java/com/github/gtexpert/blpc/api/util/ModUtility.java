package com.github.gtexpert.blpc.api.util;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.Tags;

/**
 * Utility for creating namespaced ResourceLocations.
 */
public class ModUtility {

    public static @NotNull ResourceLocation id(String path) {
        return new ResourceLocation(Tags.MODID, path);
    }

    /** Alias of {@link #id(String)} — mirrors {@code id(String)} for discoverability. */
    public static @NotNull ResourceLocation blpcId(String path) {
        return id(path);
    }
}
