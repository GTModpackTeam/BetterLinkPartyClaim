package com.github.gtexpert.bquclaim.api.util;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.bquclaim.Tags;

public class ModUtility {

    public static @NotNull ResourceLocation id(String path) {
        return new ResourceLocation(Tags.MODID, path);
    }
}
