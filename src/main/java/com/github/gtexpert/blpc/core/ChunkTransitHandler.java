package com.github.gtexpert.blpc.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.party.RelationType;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.network.message.ClientNotify;

/**
 * Detects when players cross claimed chunk boundaries and:
 * <ul>
 * <li>Sends toast notifications to relevant party members</li>
 * <li>Applies/removes potion effects for area control</li>
 * </ul>
 */
public class ChunkTransitHandler {

    private static final Map<UUID, Long> previousChunk = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> previousDim = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> activeInvasions = new ConcurrentHashMap<>();
    private static final int POTION_DURATION = 100;
    private static final int EFFECT_TICK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        if (player.world.isRemote) return;

        int dim = player.world.provider.getDimension();
        int cx = player.chunkCoordX;
        int cz = player.chunkCoordZ;
        long packed = pack(cx, cz);
        UUID playerId = player.getUniqueID();

        // Dimension changes (e.g. Nether portal) always count as a chunk transition, even when
        // the packed x/z happens to match the previous dimension's coordinates.
        Integer prevDim = previousDim.put(playerId, dim);
        Long prev = previousChunk.put(playerId, packed);
        boolean dimChanged = prevDim != null && prevDim != dim;
        if (!dimChanged && prev != null && prev == packed) {
            // Same chunk — only handle periodic area effects
            if (ModConfig.fairPlay.enableAreaEffects && player.ticksExisted % EFFECT_TICK_INTERVAL == 0) {
                applyAreaEffects(player, cx, cz, dim);
            }
            return;
        }

        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        IPartyProvider activeProvider = PartyProviderRegistry.get();

        Party prevParty = null;
        ClaimedChunkData prevClaim = null;
        if (prev != null) {
            int prevX = unpackX(prev);
            int prevZ = unpackZ(prev);
            int prevChunkDim = prevDim != null ? prevDim : dim;
            prevClaim = chunkData.getClaim(prevX, prevZ, prevChunkDim);
            if (prevClaim != null) {
                prevParty = activeProvider.getEffectiveParty(prevClaim.ownerUUID);
            }
        }

        ClaimedChunkData curClaim = chunkData.getClaim(cx, cz, dim);
        Party curParty = curClaim != null ? activeProvider.getEffectiveParty(curClaim.ownerUUID) : null;

        // Moving between two chunks claimed by the same party (e.g. inside a dense claim
        // cluster) isn't a real boundary crossing — skip leave/enter notifications entirely.
        boolean samePartyThroughout = prevParty != null && curParty != null &&
                prevParty.getPartyId().equals(curParty.getPartyId());

        if (!samePartyThroughout && prevClaim != null && prevParty != null) {
            RelationType rel = resolveRelation(prevParty, player, activeProvider);
            if (rel != RelationType.NONE) {
                if (ModConfig.fairPlay.enableTransitNotify) {
                    sendNotifications(prevParty, player, rel, false);
                }
                if (rel == RelationType.ENEMY) {
                    onEnemyLeave(prevParty.getPartyId(), playerId, player);
                }
            } else if (ModConfig.fairPlay.enableTransitNotify) {
                sendOutsiderNotification(player, prevClaim, prevParty, false);
            }
        }

        if (!samePartyThroughout && curClaim != null && curParty != null) {
            RelationType rel = resolveRelation(curParty, player, activeProvider);
            if (rel != RelationType.NONE) {
                if (ModConfig.fairPlay.enableTransitNotify) {
                    sendNotifications(curParty, player, rel, true);
                }
                if (rel == RelationType.ENEMY && ModConfig.fairPlay.enableAreaEffects) {
                    onEnemyEnter(curParty.getPartyId(), playerId);
                }
            } else if (ModConfig.fairPlay.enableTransitNotify) {
                sendOutsiderNotification(player, curClaim, curParty, true);
            }
        }

        if (ModConfig.fairPlay.enableAreaEffects) {
            applyAreaEffects(player, cx, cz, dim);
        }
    }

    public static void onPlayerLogout(UUID playerId) {
        previousChunk.remove(playerId);
        previousDim.remove(playerId);
        // Drop the entry entirely when the logout empties an invader set, so
        // activeInvasions doesn't accumulate empty Sets across long sessions.
        activeInvasions.values().removeIf(invaders -> {
            invaders.remove(playerId);
            return invaders.isEmpty();
        });
    }

    private static RelationType resolveRelation(Party claimParty, EntityPlayerMP player,
                                                IPartyProvider activeProvider) {
        UUID playerId = player.getUniqueID();
        if (claimParty.isMember(playerId)) {
            return RelationType.MEMBER;
        }

        Party playerParty = activeProvider.getEffectiveParty(playerId);
        if (playerParty == null) return RelationType.NONE;

        UUID playerPartyId = playerParty.getPartyId();
        if (claimParty.isAlly(playerPartyId)) return RelationType.ALLY;
        if (claimParty.isEnemy(playerPartyId)) return RelationType.ENEMY;
        return RelationType.NONE;
    }

    private static void sendNotifications(Party claimParty, EntityPlayerMP transitPlayer,
                                          RelationType relation, boolean entered) {
        ClientNotify othersPacket = ClientNotify.chunkTransit(
                transitPlayer.getName(), transitPlayer.getUniqueID(), relation, entered, "", false);

        for (UUID memberId : claimParty.getMembers().keySet()) {
            EntityPlayerMP member = getOnlinePlayer(memberId);
            if (member != null && !member.getUniqueID().equals(transitPlayer.getUniqueID())) {
                ModNetwork.INSTANCE.sendTo(othersPacket, member);
            }
        }
        // The transiting player always gets their own toast too (e.g. "You returned home"),
        // not just enemies invading someone else's claim — with second-person wording. The
        // claim's own party name is passed as ownerName so ALLY/ENEMY self-toasts can say whose
        // territory it is (MEMBER's self toast ignores it — it's always "your own" land).
        ClientNotify selfPacket = ClientNotify.chunkTransit(
                transitPlayer.getName(), transitPlayer.getUniqueID(), relation, entered, claimParty.getName(), true);
        ModNetwork.INSTANCE.sendTo(selfPacket, transitPlayer);
    }

    /**
     * Tells an unrelated player (no member/ally/enemy relation to the claim owner) whose claim
     * they just crossed into or out of — informational only, no defender/invader side-effects.
     */
    private static void sendOutsiderNotification(EntityPlayerMP transitPlayer, ClaimedChunkData claim,
                                                 Party claimParty, boolean entered) {
        String ownerName = claimParty.getName().isEmpty() ? claim.ownerName : claimParty.getName();
        ClientNotify packet = ClientNotify.chunkTransit(null, null, RelationType.NONE, entered, ownerName, true);
        ModNetwork.INSTANCE.sendTo(packet, transitPlayer);
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

    private static void applyAreaEffects(EntityPlayerMP player, int cx, int cz, int dim) {
        ChunkManagerData chunkData = ChunkManagerData.getInstance();
        IPartyProvider activeProvider = PartyProviderRegistry.get();
        ClaimedChunkData claim = chunkData.getClaim(cx, cz, dim);
        if (claim == null) return;

        Party claimParty = activeProvider.getEffectiveParty(claim.ownerUUID);
        if (claimParty == null) return;

        RelationType rel = resolveRelation(claimParty, player, activeProvider);

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
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
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
