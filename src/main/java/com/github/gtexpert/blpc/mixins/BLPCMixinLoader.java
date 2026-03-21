package com.github.gtexpert.blpc.mixins;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraftforge.fml.common.Loader;

import com.google.common.collect.ImmutableMap;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.util.Mods;

import zone.rong.mixinbooter.ILateMixinLoader;

@SuppressWarnings("unused")
public class BLPCMixinLoader implements ILateMixinLoader {

    public static final Map<String, Boolean> modMixinsConfig = new ImmutableMap.Builder<String, Boolean>()
            .put(Mods.Names.BETTER_QUESTING, true)
            .build();

    @Override
    public List<String> getMixinConfigs() {
        if (modMixinsConfig.isEmpty()) return Collections.emptyList();
        return modMixinsConfig.keySet().stream()
                .map(mod -> "mixins." + Tags.MODID + "." + mod + ".json")
                .collect(Collectors.toList());
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        String[] parts = mixinConfig.split("\\.");
        if (parts.length < 3) return true;
        String modId = parts[2];
        if (!Loader.isModLoaded(modId)) return false;
        return modMixinsConfig.containsKey(modId) && modMixinsConfig.get(modId);
    }
}
