package com.github.gtexpert.blpc.core;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.relauncher.Side;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

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
        if (ModConfig.party.autoCreateServerParty) {
            PartyManagerData pmData = PartyManagerData.getInstance();
            boolean exists = pmData.getAllParties().stream()
                    .anyMatch(p -> p.getName().equals(ModConfig.party.serverPartyName));
            if (!exists) {
                Party serverParty = new Party(UUID.randomUUID(), ModConfig.party.serverPartyName,
                        System.currentTimeMillis());
                serverParty.setFreeToJoin(ModConfig.party.autoCreatedPartyFreeToJoin);

                String ownerUUIDStr = ModConfig.party.autoCreatedPartyOwnerUUID;
                if (!ownerUUIDStr.isEmpty()) {
                    try {
                        UUID ownerUUID = UUID.fromString(ownerUUIDStr);
                        serverParty.addMember(ownerUUID, PartyRole.OWNER);
                    } catch (IllegalArgumentException e) {
                        ModLog.PARTY.warn("Invalid owner UUID in config: {}", ownerUUIDStr);
                    }
                }

                pmData.addParty(serverParty);
                BLPCSaveHandler.INSTANCE.markDirty();
                ModLog.PARTY.info("Auto-created server party: {}", ModConfig.party.serverPartyName);
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
