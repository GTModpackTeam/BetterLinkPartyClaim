package com.github.gtexpert.blpc.api.modules;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.event.*;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * A self-contained feature unit dispatched by the BLPC module system.
 * <p>
 * Implementations are discovered automatically: annotate the class with
 * {@link TModule}, ensure it has a no-arg constructor, and the
 * {@link com.github.gtexpert.blpc.module.ModuleManager} will instantiate and
 * load it during FML Construction. Module loading respects:
 * <ul>
 * <li>{@link TModule#modDependencies()} — required Forge mods.</li>
 * <li>{@link #getDependencyUids()} — required sibling modules.</li>
 * <li>{@code config/<modid>/modules.cfg} — user-controlled enable/disable.</li>
 * </ul>
 * Lifecycle methods are invoked in declaration order matching the FML stages
 * defined in {@link ModuleStage}. Each callback has a no-op default, so a
 * module only overrides the hooks it needs.
 * <p>
 * Network packets must be registered in {@link #registerPackets()} (called
 * before {@link #preInit}). Event handler classes returned from
 * {@link #getEventBusSubscribers()} are registered on the Forge event bus
 * during {@code setup()}.
 */
public interface IModule {

    /**
     * @return module IDs that must be loaded before this one. Missing
     *         dependencies cause this module to be skipped.
     */
    @NotNull
    default Set<ResourceLocation> getDependencyUids() {
        return Collections.emptySet();
    }

    /** FML Construction — earliest hook. ASM data is available here. */
    default void construction(FMLConstructionEvent event) {}

    /** FML Pre-Init — register blocks, items, capabilities, recipes. */
    default void preInit(FMLPreInitializationEvent event) {}

    /** FML Init — register handlers, side-specific resources. */
    default void init(FMLInitializationEvent event) {}

    /** FML Post-Init — cross-mod integrations after all mods finished init. */
    default void postInit(FMLPostInitializationEvent event) {}

    /** FML Load Complete — final hook before world load. */
    default void loadComplete(FMLLoadCompleteEvent event) {}

    default void serverAboutToStart(FMLServerAboutToStartEvent event) {}

    default void serverStarting(FMLServerStartingEvent event) {}

    default void serverStarted(FMLServerStartedEvent event) {}

    default void serverStopping(FMLServerStoppingEvent event) {}

    default void serverStopped(FMLServerStoppedEvent event) {}

    /** Register {@link net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper} packets here. */
    default void registerPackets() {}

    /**
     * @return classes whose static handlers should be registered on
     *         {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}.
     */
    @NotNull
    default List<Class<?>> getEventBusSubscribers() {
        return Collections.emptyList();
    }

    /** @return true if the IMC message was consumed; subsequent modules are skipped. */
    default boolean processIMC(FMLInterModComms.IMCMessage message) {
        return false;
    }

    /** @return per-module logger used by the lifecycle dispatcher for diagnostic output. */
    @NotNull
    Logger getLogger();
}
