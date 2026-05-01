package com.github.gtexpert.blpc.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.network.MessageChunkTransitNotify;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.RelationType;

/**
 * Detects when players cross claimed chunk boundaries and:
 * <ul>
 * <li>Sends toast notifications to relevant party members</li>
 * <li>Applies/removes potion effects for area control</li>
 * </ul>
 */
public class ChunkTransitHandler {

    private static final Map<UUID, Long> previousChunk = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> activeInvasions = new ConcurrentHashMap<>();
    private static final int POTION_DURATION = 100;
    private static final int EFFECT_TICK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        if (player.world.isRemote) return;
        if (player.dimension != 0) return; // Claims are overworld only

        int cx = player.chunkCoordX;
        int cz = player.chunkCoordZ;
        long packed = pack(cx, cz);
        UUID playerId = player.getUniqueID();

        Long prev = previousChunk.put(playerId, packed);
        if (prev != null && prev == packed) {
            // Same chunk — only handle periodic area effects
            if (ModConfig.Defaults.enableAreaEffects && player.ticksExisted % EFFECT_TICK_INTERVAL == 0) {
                applyAreaEffects(player, cx, cz);
            }
            return;
        }

        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        PartyManagerData partyData = PartyManagerData.getInstance();

        if (prev != null) {
            int prevX = unpackX(prev);
            int prevZ = unpackZ(prev);
            ClaimedChunkData prevClaim = chunkData.getClaim(prevX, prevZ);
            if (prevClaim != null) {
                Party prevParty = partyData.getPartyByPlayer(prevClaim.ownerUUID);
                if (prevParty != null) {
                    RelationType rel = resolveRelation(prevParty, player);
                    if (rel != RelationType.NONE) {
                        if (ModConfig.Defaults.enableTransitNotify) {
                            sendNotifications(prevParty, player, rel, false);
                        }
                        if (rel == RelationType.ENEMY) {
                            onEnemyLeave(prevParty.getPartyId(), playerId, player);
                        }
                    }
                }
            }
        }

        ClaimedChunkData curClaim = chunkData.getClaim(cx, cz);
        if (curClaim != null) {
            Party curParty = partyData.getPartyByPlayer(curClaim.ownerUUID);
            if (curParty != null) {
                RelationType rel = resolveRelation(curParty, player);
                if (rel != RelationType.NONE) {
                    if (ModConfig.Defaults.enableTransitNotify) {
                        sendNotifications(curParty, player, rel, true);
                    }
                    if (rel == RelationType.ENEMY && ModConfig.Defaults.enableAreaEffects) {
                        onEnemyEnter(curParty.getPartyId(), playerId);
                    }
                }
            }
        }

        if (ModConfig.Defaults.enableAreaEffects) {
            applyAreaEffects(player, cx, cz);
        }
    }

    public static void onPlayerLogout(UUID playerId) {
        previousChunk.remove(playerId);
        for (Set<UUID> invaders : activeInvasions.values()) {
            invaders.remove(playerId);
        }
    }

    private static RelationType resolveRelation(Party claimParty, EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        if (claimParty.isMember(playerId)) {
            return RelationType.MEMBER;
        }

        Party playerParty = PartyManagerData.getInstance().getPartyByPlayer(playerId);
        if (playerParty == null) return RelationType.NONE;

        UUID playerPartyId = playerParty.getPartyId();
        if (claimParty.isAlly(playerPartyId)) return RelationType.ALLY;
        if (claimParty.isEnemy(playerPartyId)) return RelationType.ENEMY;
        return RelationType.NONE;
    }

    private static void sendNotifications(Party claimParty, EntityPlayerMP transitPlayer,
                                          RelationType relation, boolean entered) {
        MessageChunkTransitNotify packet = new MessageChunkTransitNotify(
                transitPlayer.getName(), relation, entered);

        for (UUID memberId : claimParty.getMembers().keySet()) {
            EntityPlayerMP member = getOnlinePlayer(memberId);
            if (member != null && !member.getUniqueID().equals(transitPlayer.getUniqueID())) {
                ModNetwork.INSTANCE.sendTo(packet, member);
            }
        }
        if (relation == RelationType.ENEMY) {
            ModNetwork.INSTANCE.sendTo(packet, transitPlayer);
        }
    }

    private static void onEnemyEnter(UUID partyId, UUID enemyId) {
        activeInvasions.computeIfAbsent(partyId, k -> ConcurrentHashMap.newKeySet()).add(enemyId);
    }

    private static void onEnemyLeave(UUID partyId, UUID enemyId, EntityPlayerMP enemy) {
        Set<UUID> invaders = activeInvasions.get(partyId);
        if (invaders != null) {
            invaders.remove(enemyId);
            if (invaders.isEmpty()) {
                activeInvasions.remove(partyId);
            }
        }
        // Remove debuffs immediately on leaving
        enemy.removePotionEffect(MobEffects.WEAKNESS);
        if (ModConfig.Defaults.enemyMiningFatigue) {
            enemy.removePotionEffect(MobEffects.MINING_FATIGUE);
        }
    }

    private static void applyAreaEffects(EntityPlayerMP player, int cx, int cz) {
        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        PartyManagerData partyData = PartyManagerData.getInstance();
        ClaimedChunkData claim = chunkData.getClaim(cx, cz);
        if (claim == null) return;

        Party claimParty = partyData.getPartyByPlayer(claim.ownerUUID);
        if (claimParty == null) return;

        RelationType rel = resolveRelation(claimParty, player);

        if (rel == RelationType.ENEMY) {
            player.addPotionEffect(new PotionEffect(
                    MobEffects.WEAKNESS, POTION_DURATION, ModConfig.Defaults.enemyWeaknessAmplifier, true, true));
            if (ModConfig.Defaults.enemyMiningFatigue) {
                player.addPotionEffect(new PotionEffect(
                        MobEffects.MINING_FATIGUE, POTION_DURATION, 0, true, true));
            }
        }

        // Defender buff: only active when enemies are present.
        if (rel == RelationType.MEMBER) {
            Set<UUID> invaders = activeInvasions.get(claimParty.getPartyId());
            if (invaders != null && !invaders.isEmpty()) {
                player.addPotionEffect(new PotionEffect(
                        MobEffects.RESISTANCE, POTION_DURATION, ModConfig.Defaults.defenderResistanceAmplifier, true,
                        true));
                player.addPotionEffect(new PotionEffect(
                        MobEffects.STRENGTH, POTION_DURATION, 0, true, true));
            }
        }
    }

    private static EntityPlayerMP getOnlinePlayer(UUID uuid) {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null ? server.getPlayerList().getPlayerByUUID(uuid) : null;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
