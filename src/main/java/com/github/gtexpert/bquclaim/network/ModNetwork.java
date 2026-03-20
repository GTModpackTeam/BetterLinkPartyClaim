package com.github.gtexpert.bquclaim.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.github.gtexpert.bquclaim.Tags;

public class ModNetwork {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    public static void init() {
        int id = 0;
        // Client -> Server
        INSTANCE.registerMessage(MessageClaimChunk.Handler.class, MessageClaimChunk.class, id++, Side.SERVER);

        // Server -> Client
        INSTANCE.registerMessage(MessageSyncClaims.Handler.class, MessageSyncClaims.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(MessageSyncAllClaims.Handler.class, MessageSyncAllClaims.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(MessageSyncConfig.Handler.class, MessageSyncConfig.class, id++, Side.CLIENT);
    }
}
