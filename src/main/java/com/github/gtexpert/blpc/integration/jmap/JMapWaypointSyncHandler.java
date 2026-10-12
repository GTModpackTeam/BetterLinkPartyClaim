package com.github.gtexpert.blpc.integration.jmap;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.waypoint.ClientWaypointCache;
import com.github.gtexpert.blpc.common.waypoint.PartyWaypointData;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import journeymap.client.waypoint.ClientWaypointImpl;
import journeymap.common.waypoint.WaypointGroupImpl;
import journeymap.common.waypoint.WaypointIcon;
import journeymap.common.waypoint.WaypointPos;
import journeymap.common.waypoint.WaypointSettings;
import journeymap.common.waypoint.WaypointStore;

/**
 * Mirrors {@link ClientWaypointCache} (party-shared waypoints synced from the server) onto a
 * locked JourneyMap {@link WaypointGroup} named "BLPC Party", so every online party member sees
 * the same shared markers on their map. The group is locked so users cannot accidentally delete
 * shared waypoints through JourneyMap's UI.
 * <p>
 * Sync triggers:
 * <ul>
 * <li>Immediately when {@link ClientWaypointCache} fires a change listener (event-driven).</li>
 * <li>Periodically every N client ticks (configurable via
 * {@code ModConfig.integration.jmapWaypointSyncInterval}; 0 disables periodic sync).</li>
 * </ul>
 * Lifecycle:
 * <ul>
 * <li>Created when shared waypoints arrive (party join, server sync on login).</li>
 * <li>Updated on every cache change or periodic tick.</li>
 * <li>Removed when the cache empties (party leave, disband) or sharing is disabled.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class JMapWaypointSyncHandler {

    static final String GROUP_NAME = "BLPC Party";
    private static final String GROUP_GUID = Tags.MODID + "_party";
    private static final String WP_ID_PREFIX = Tags.MODID + "_";

    private static JMapWaypointSyncHandler instance;

    private final Runnable waypointListener = this::onCacheChanged;
    private final Runnable partyListener = this::onCacheChanged;
    private int tickCounter;

    public void register() {
        instance = this;
        tickCounter = 0;
        ClientWaypointCache.addChangeListener(waypointListener);
        ClientPartyCache.addSyncListener(partyListener);
    }

    public void unregister() {
        ClientWaypointCache.removeChangeListener(waypointListener);
        ClientPartyCache.removeSyncListener(partyListener);
        if (instance == this) instance = null;
    }

    /** Re-applies the mirror after a settings toggle (see {@link JMapSettingsPanel}). */
    static void refreshFromSettings() {
        if (instance != null) instance.onCacheChanged();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int interval = JMapClientConfig.getWaypointSyncInterval();
        if (interval <= 0) return;
        if (++tickCounter >= interval) {
            tickCounter = 0;
            sync();
        }
    }

    private void onCacheChanged() {
        tickCounter = 0;
        sync();
    }

    private void sync() {
        JMapWaypointOutgoing.beginApplyingRemoteChange();
        try {
            applyToJourneyMap();
        } finally {
            JMapWaypointOutgoing.endApplyingRemoteChange();
        }
    }

    private void applyToJourneyMap() {
        IClientAPI api = JMapPlugin.getApi();
        if (api == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        boolean inParty = ClientPartyCache.getPartyByPlayer(mc.player.getUniqueID()) != null;

        if (!inParty) {
            removeGroupIfPresent(api);
            return;
        }

        boolean enabled = JMapClientConfig.isWaypointSharingEnabled();
        Collection<PartyWaypointData> sharedWaypoints = ClientWaypointCache.getAll();
        WaypointGroup group = ensureGroup(api);
        group.setEnabled(enabled);

        HashSet<String> existingIds = new HashSet<>(group.getWaypointIds());
        HashSet<String> wantedIds = new HashSet<String>();

        for (PartyWaypointData shared : sharedWaypoints) {
            String stableId = WP_ID_PREFIX + shared.waypointId;
            wantedIds.add(stableId);
            Waypoint ex = api.getWaypoint(Tags.MODID, stableId);
            if (ex != null) {
                ex.setName(shared.name);
                ex.setBlockPos(new BlockPos(shared.x, shared.y, shared.z));
                ex.setPrimaryDimension(shared.dimension);
                ex.setColor(shared.color);
            } else {
                ClientWaypointImpl wp = createWaypoint(shared, stableId, group.getGuid());
                group.addWaypoint(wp);
                WaypointStore.getInstance().putLocal(wp, false);
            }
        }

        for (String oldId : existingIds) {
            if (!wantedIds.contains(oldId)) {
                Waypoint old = api.getWaypoint(Tags.MODID, oldId);
                if (old != null) api.removeWaypoint(Tags.MODID, old);
            }
        }
    }

    private WaypointGroup ensureGroup(IClientAPI api) {
        for (WaypointGroup g : api.getWaypointGroups(Tags.MODID)) {
            if (GROUP_GUID.equals(g.getGuid())) return g;
        }

        WaypointGroupImpl group = new WaypointGroupImpl(Tags.MODID, GROUP_NAME, GROUP_GUID);
        group.setLocked(true);
        group.setPersistent(true);
        api.addWaypointGroup(group);
        return group;
    }

    private static ClientWaypointImpl createWaypoint(PartyWaypointData data, String guid, String groupGuid) {
        WaypointPos pos = new WaypointPos(new BlockPos(data.x, data.y, data.z), String.valueOf(data.dimension));
        TreeSet<String> dims = new TreeSet<>(java.util.Collections.singleton(String.valueOf(data.dimension)));
        return new ClientWaypointImpl(
                data.name,
                "1",
                Tags.MODID,
                guid,
                Tags.MODID,
                groupGuid,
                pos,
                data.color,
                new WaypointIcon(),
                new WaypointSettings(true, false, true),
                dims,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private void removeGroupIfPresent(IClientAPI api) {
        for (WaypointGroup g : api.getWaypointGroups(Tags.MODID)) {
            if (GROUP_GUID.equals(g.getGuid())) {
                api.removeWaypointGroup(g, true);
                return;
            }
        }
    }
}
