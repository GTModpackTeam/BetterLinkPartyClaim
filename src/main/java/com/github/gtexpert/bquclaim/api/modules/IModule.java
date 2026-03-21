package com.github.gtexpert.bquclaim.api.modules;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.event.*;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public interface IModule {

    @NotNull
    default Set<ResourceLocation> getDependencyUids() {
        return Collections.emptySet();
    }

    default void construction(FMLConstructionEvent event) {}

    default void preInit(FMLPreInitializationEvent event) {}

    default void init(FMLInitializationEvent event) {}

    default void postInit(FMLPostInitializationEvent event) {}

    default void loadComplete(FMLLoadCompleteEvent event) {}

    default void serverAboutToStart(FMLServerAboutToStartEvent event) {}

    default void serverStarting(FMLServerStartingEvent event) {}

    default void serverStarted(FMLServerStartedEvent event) {}

    default void serverStopping(FMLServerStoppingEvent event) {}

    default void serverStopped(FMLServerStoppedEvent event) {}

    default void registerPackets() {}

    @NotNull
    default List<Class<?>> getEventBusSubscribers() {
        return Collections.emptyList();
    }

    default boolean processIMC(FMLInterModComms.IMCMessage message) {
        return false;
    }

    @NotNull
    Logger getLogger();
}
