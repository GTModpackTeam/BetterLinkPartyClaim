package com.github.gtexpert.blpc.client.cache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Derives a filesystem-safe identifier for the server the client is currently connected to,
 * so per-server claim/party caches never mix data between different worlds or servers.
 */
@SideOnly(Side.CLIENT)
public final class ClientCacheKey {

    private ClientCacheKey() {}

    /**
     * Returns a sanitized identifier for the current connection, or {@code null} if not
     * connected to any world (e.g. on the main menu).
     */
    public static String current() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isIntegratedServerRunning()) {
            IntegratedServer server = mc.getIntegratedServer();
            if (server == null) return null;
            return "sp_" + sanitize(server.getFolderName());
        }
        ServerData data = mc.getCurrentServerData();
        if (data == null || data.serverIP == null) return null;
        return "mp_" + sanitize(data.serverIP);
    }

    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
