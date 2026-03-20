package com.github.gtexpert.bquclaim.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import com.github.gtexpert.bquclaim.ModConfig;
import com.github.gtexpert.bquclaim.Tags;
import com.github.gtexpert.bquclaim.chunk.ChunkManagerData;

@Mod.EventBusSubscriber(modid = Tags.MODID)
public class PlayerLoginHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.player.world.isRemote) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            ChunkManagerData data = ChunkManagerData.get(player.world);

            // 全データをパケットにして送信
            ModNetwork.INSTANCE.sendTo(new MessageSyncAllClaims(data.serializeAll()), player);

            // ついでにConfigの最大値なども同期したい場合は、別のパケットや
            // このパケットの中にConfig用のタグを追加して送れば完璧です。
            ModNetwork.INSTANCE.sendTo(new MessageSyncConfig(
                    ModConfig.maxClaimsPerPlayer,
                    ModConfig.maxForceLoadsPerPlayer), player);
        }
    }
}
