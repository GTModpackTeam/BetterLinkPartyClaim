package com.sysnote8.bquclaim.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class ModNetwork {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("chunkmod");

    public static void init() {
        int id = 0;
        // Client -> Server: Claim/Unclaimのリクエスト
        INSTANCE.registerMessage(MessageClaimChunk.Handler.class, MessageClaimChunk.class, id++, Side.SERVER);
        // Server -> Client: 領地情報の同期
        INSTANCE.registerMessage(MessageSyncClaims.Handler.class, MessageSyncClaims.class, id++, Side.CLIENT);
    }
}
