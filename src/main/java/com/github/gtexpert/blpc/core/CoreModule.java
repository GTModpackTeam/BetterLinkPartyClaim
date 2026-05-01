package com.github.gtexpert.blpc.core;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.relauncher.Side;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.mojang.authlib.GameProfile;

import com.github.gtexpert.blpc.BLPCMod;
import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.modules.IModule;
import com.github.gtexpert.blpc.api.modules.TModule;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.ModLog;
import com.github.gtexpert.blpc.common.chunk.TicketManager;
import com.github.gtexpert.blpc.common.command.BLPCCommand;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.PartyRole;
import com.github.gtexpert.blpc.module.Modules;

/**
 * Core module — always loaded, runs first.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Register network packets via {@link ModNetwork#init()}.</li>
 * <li>Wire up {@link TicketManager} for forced chunk loading.</li>
 * <li>Install {@link DefaultPartyProvider} as the baseline party provider
 * (later replaced by {@code BQuModule} when BetterQuesting is present).</li>
 * <li>Load and save world data through {@link BLPCSaveHandler}.</li>
 * <li>Auto-create the configured server party on {@code serverStarting}.</li>
 * <li>Register protection / transit / event handlers.</li>
 * </ul>
 */
@TModule(
         moduleID = Modules.MODULE_CORE,
         containerID = Tags.MODID,
         name = "BLPC Core",
         description = "Core module of BLPC",
         coreModule = true)
public class CoreModule implements IModule {

    @Override
    public @NotNull Logger getLogger() {
        return ModLog.ROOT;
    }

    @Override
    public void registerPackets() {
        ModNetwork.init();
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ForgeChunkManager.setForcedChunkLoadingCallback(BLPCMod.INSTANCE, new TicketManager());
        PartyProviderRegistry.register(new DefaultPartyProvider());
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new CoreEventHandler.ClientHandler());
        }
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        BLPCSaveHandler.INSTANCE.loadAll(event.getServer());

        // Auto-create server party on server start
        if (ModConfig.serverParty.enabled) {
            PartyManagerData pmData = PartyManagerData.getInstance();
            boolean exists = pmData.getAllParties().stream()
                    .anyMatch(p -> p.getName().equals(ModConfig.serverParty.name));
            if (!exists) {
                Party serverParty = new Party(UUID.randomUUID(), ModConfig.serverParty.name,
                        System.currentTimeMillis());
                serverParty.setFreeToJoin(ModConfig.serverParty.freeToJoin);

                String ownerName = ModConfig.serverParty.owner;
                GameProfile ownerProfile = null;
                if (!ownerName.isEmpty()) {
                    // Server start: player may be offline; resolve via profile cache
                    ownerProfile = event.getServer().getPlayerProfileCache()
                            .getGameProfileForUsername(ownerName);
                    if (ownerProfile != null) {
                        serverParty.addMember(ownerProfile.getId(), PartyRole.OWNER);
                    } else {
                        ModLog.PARTY.warn("Owner player '{}' not found in profile cache", ownerName);
                    }
                }

                for (String modName : ModConfig.serverParty.moderators) {
                    if (modName.isEmpty()) continue;
                    GameProfile modProfile = event.getServer().getPlayerProfileCache()
                            .getGameProfileForUsername(modName);
                    if (modProfile != null) {
                        serverParty.addMember(modProfile.getId(), PartyRole.ADMIN);
                    } else {
                        ModLog.PARTY.warn("Moderator player '{}' not found in profile cache", modName);
                    }
                }

                pmData.addParty(serverParty);

                // Auto-link to BQu if BQu is present and an owner was resolved
                if (Loader.isModLoaded("betterquesting") && ownerProfile != null) {
                    pmData.setBQuLinked(ownerProfile.getId(), true);
                    ModLog.PARTY.info("Auto-linked server party to BQu for owner {}", ownerName);
                }

                BLPCSaveHandler.INSTANCE.markDirty();
                ModLog.PARTY.info("Auto-created server party: {}", ModConfig.serverParty.name);
            }
        }

        event.registerServerCommand(new BLPCCommand());
    }

    @Override
    public void serverStopping(FMLServerStoppingEvent event) {
        BLPCSaveHandler.INSTANCE.saveAll();
    }

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return Arrays.asList(CoreEventHandler.class, ChunkProtectionHandler.class, ChunkTransitHandler.class);
    }
}
