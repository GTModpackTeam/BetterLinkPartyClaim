package com.github.gtexpert.blpc.integration.jmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.ClientClaimCache;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.DisplayUpdateEvent;
import journeymap.api.v2.client.event.FullscreenDisplayEvent;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.fullscreen.ThemeButtonDisplay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.event.CommonEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.common.event.common.WaypointEvent;
import journeymap.api.v2.common.event.common.WaypointGroupEvent;
import journeymap.api.v2.common.option.BooleanOption;
import journeymap.api.v2.common.option.IntegerOption;
import journeymap.api.v2.common.option.OptionCategory;
import journeymap.api.v2.common.option.OptionsRegistry;

/**
 * Draws claim overlays on JourneyMap and provides a fullscreen toggle button.
 * Contiguous chunks owned by the same player are merged into a single polygon
 * (outer perimeter + holes) with one label, instead of one bordered-and-labelled
 * box per chunk.
 */
@JourneyMapPlugin(apiVersion = "2.0.0", dependencies = {})
public class JMapPlugin implements IClientPlugin {

    private static final String OVERLAY_GROUP = "BLPC Claims";
    private static final int CLAIM_Y = 70;
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int COLOR_OWN = 0x00FF00;
    private static final int COLOR_PARTY = 0x00FFFF;
    private static final int COLOR_OTHER = 0xFF0000;
    private static final float FILL_OPACITY = 0.35f;
    private static final float STROKE_OPACITY = 0.6f;
    private static final float STROKE_WIDTH = 1.5f;
    private static final float FORCE_LOADED_STROKE_OPACITY = 1.0f;
    private static final float FORCE_LOADED_STROKE_WIDTH = 3.0f;

    private static final ResourceLocation BUTTON_ICON = new ResourceLocation(Tags.MODID,
            "textures/gui/claim_overlay_icon.png");

    private IClientAPI api;
    private static JMapPlugin instance;
    private BooleanOption overlaysOption;
    private final Map<String, PolygonOverlay> activeOverlays = new HashMap<>();

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        instance = this;
        ClientEventRegistry.DISPLAY_UPDATE_EVENT.subscribe(getModId(), this::onDisplayUpdate);
        ClientEventRegistry.MAPPING_EVENT.subscribe(getModId(), this::onMappingEvent);
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(getModId(), this::onAddonButtons);
        CommonEventRegistry.WAYPOINT_GROUP_EVENT.subscribe(getModId(), this::onWaypointGroupEvent);
        CommonEventRegistry.WAYPOINT_EVENT.subscribe(getModId(), this::onWaypointEvent);
        registerOptions();
    }

    private void registerOptions() {
        OptionCategory category = new OptionCategory(getModId(), Tags.MODID.toUpperCase(), Tags.MODNAME);
        overlaysOption = new BooleanOption(category, "showClaimOverlays",
                I18n.format("blpc.addons.journeymap.overlays_option"), true);
        BooleanOption waypointSharing = new BooleanOption(category, "waypointSharing",
                I18n.format("blpc.addons.journeymap.waypoints_option"), true);
        IntegerOption syncInterval = new IntegerOption(category, "waypointSyncInterval",
                I18n.format("blpc.addons.journeymap.sync_interval_option"), 100, 0, 6000);
        OptionsRegistry.register(getModId(), overlaysOption);
        OptionsRegistry.register(getModId(), waypointSharing);
        OptionsRegistry.register(getModId(), syncInterval);
        JMapClientConfig.init(overlaysOption, waypointSharing, syncInterval);
    }

    @Override
    public String getModId() {
        return Tags.MODID;
    }

    private void onDisplayUpdate(DisplayUpdateEvent event) {
        refreshOverlays(event.dimension);
    }

    private void onMappingEvent(MappingEvent event) {
        switch (event.getStage()) {
            case MAPPING_STARTED -> refreshOverlays(event.dimension);
            case MAPPING_STOPPED -> clearOverlays();
        }
    }

    private void onAddonButtons(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        ThemeButtonDisplay display = event.getThemeButtonDisplay();
        display.addThemeToggleButton(
                I18n.format("blpc.addons.journeymap.overlays_on"),
                I18n.format("blpc.addons.journeymap.overlays_off"),
                BUTTON_ICON,
                JMapClientConfig.isShowClaimOverlays(),
                button -> {
                    boolean toggled = Boolean.TRUE.equals(button.getToggled());
                    try {
                        overlaysOption.set(toggled);
                    } catch (Exception ignored) {}
                    refreshFromSettings();
                });
    }

    private void onWaypointGroupEvent(WaypointGroupEvent event) {
        if (event.getContext() != WaypointGroupEvent.Context.DELETED) return;
        if (!Tags.MODID.equals(event.getGroup().getModId())) return;
        if (!JMapWaypointSyncHandler.GROUP_NAME.equals(event.getGroup().getName())) return;
        JMapWaypointSyncHandler.refreshFromSettings();
    }

    private void onWaypointEvent(WaypointEvent event) {
        if (event.getContext() != WaypointEvent.Context.DELETED) return;
        if (!Tags.MODID.equals(event.getWaypoint().getModId())) return;
        JMapWaypointSyncHandler.refreshFromSettings();
    }

    static JMapPlugin getInstance() {
        return instance;
    }

    static IClientAPI getApi() {
        return instance != null ? instance.api : null;
    }

    /** Re-applies overlays after a settings toggle: redraws when enabled, clears when disabled. */
    static void refreshFromSettings() {
        if (instance == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        instance.refreshOverlays(mc.player.dimension);
    }

    void refreshOverlays(int dimension) {
        if (api == null) return;

        if (!JMapClientConfig.isShowClaimOverlays()) {
            if (!activeOverlays.isEmpty()) clearOverlays();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        UUID playerUUID = mc.player.getUniqueID();

        Map<UUID, List<ClaimedChunkData>> byOwner = new HashMap<>();
        for (ClaimedChunkData claim : ClientClaimCache.getAll()) {
            if (claim.dim != dimension) continue;
            byOwner.computeIfAbsent(claim.ownerUUID, k -> new ArrayList<>()).add(claim);
        }

        Set<String> currentKeys = new HashSet<>();
        for (Map.Entry<UUID, List<ClaimedChunkData>> entry : byOwner.entrySet()) {
            buildOwnerRegions(entry.getValue(), playerUUID, dimension, currentKeys);
        }

        activeOverlays.entrySet().removeIf(e -> {
            if (!currentKeys.contains(e.getKey())) {
                api.remove(e.getValue());
                return true;
            }
            return false;
        });
    }

    private void buildOwnerRegions(List<ClaimedChunkData> claims, UUID playerUUID, int dimension,
                                   Set<String> currentKeys) {
        Map<Long, ClaimedChunkData> cells = new HashMap<>();
        for (ClaimedChunkData c : claims) {
            cells.put(cell(c.x, c.z), c);
        }

        ClaimedChunkData sample = claims.get(0);
        int areaColor = resolveAreaColor(sample, playerUUID);
        int textColor = resolveTextColor(sample);

        Set<Long> remaining = new HashSet<>(cells.keySet());
        while (!remaining.isEmpty()) {
            Set<Long> component = floodFill(remaining.iterator().next(), remaining);

            boolean allForceLoaded = true;
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            for (long cellKey : component) {
                ClaimedChunkData c = cells.get(cellKey);
                if (!c.isForceLoaded) allForceLoaded = false;
                minX = Math.min(minX, c.x);
                minZ = Math.min(minZ, c.z);
            }

            List<MapPolygon> loops = traceLoops(component);
            if (loops.isEmpty()) continue;
            MapPolygon outer = loops.remove(largestLoopIndex(loops));

            String key = sample.ownerUUID + ":" + minX + "," + minZ;
            currentKeys.add(key);
            String title = buildTitle(sample, allForceLoaded);
            showRegion(key, dimension, outer, loops, areaColor, textColor, title, allForceLoaded);
        }
    }

    private void showRegion(String key, int dimension, MapPolygon outer, List<MapPolygon> holes,
                            int areaColor, int textColor, String title, boolean forceLoaded) {
        PolygonOverlay existing = activeOverlays.get(key);
        if (existing != null) {
            existing.setOuterArea(outer);
            existing.setHoles(holes.isEmpty() ? null : holes);
            existing.setShapeProperties(createShapeProperties(areaColor, forceLoaded));
            existing.setTitle(title);
            existing.getTextProperties().setColor(textColor);
            try {
                api.show(existing);
            } catch (Exception ignored) {}
            return;
        }

        PolygonOverlay overlay = new PolygonOverlay(getModId(), dimension,
                createShapeProperties(areaColor, forceLoaded), outer, holes.isEmpty() ? null : holes);
        overlay.setOverlayGroupName(OVERLAY_GROUP);
        overlay.setTitle(title);
        overlay.setDisplayOrder(1000);
        overlay.setTextProperties(new TextProperties()
                .setColor(textColor)
                .setBackgroundOpacity(0.6f)
                .setFontShadow(true));
        activeOverlays.put(key, overlay);
        try {
            api.show(overlay);
        } catch (Exception ignored) {}
    }

    private void clearOverlays() {
        if (api == null) return;
        api.removeAll(getModId(), DisplayType.Polygon);
        activeOverlays.clear();
    }

    // --- Geometry ---

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static Set<Long> floodFill(long start, Set<Long> remaining) {
        Set<Long> component = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(start);
        remaining.remove(start);
        while (!queue.isEmpty()) {
            long c = queue.poll();
            component.add(c);
            int cx = (int) (c >> 32);
            int cz = (int) c;
            long[] neighbors = { cell(cx + 1, cz), cell(cx - 1, cz), cell(cx, cz + 1), cell(cx, cz - 1) };
            for (long n : neighbors) {
                if (remaining.remove(n)) queue.add(n);
            }
        }
        return component;
    }

    private static List<MapPolygon> traceLoops(Set<Long> cells) {
        Map<Long, Deque<Long>> edges = new HashMap<>();
        for (long c : cells) {
            int cx = (int) (c >> 32);
            int cz = (int) c;
            if (!cells.contains(cell(cx, cz + 1))) addEdge(edges, corner(cx, cz + 1), corner(cx + 1, cz + 1));
            if (!cells.contains(cell(cx + 1, cz))) addEdge(edges, corner(cx + 1, cz + 1), corner(cx + 1, cz));
            if (!cells.contains(cell(cx, cz - 1))) addEdge(edges, corner(cx + 1, cz), corner(cx, cz));
            if (!cells.contains(cell(cx - 1, cz))) addEdge(edges, corner(cx, cz), corner(cx, cz + 1));
        }

        List<MapPolygon> loops = new ArrayList<>();
        while (!edges.isEmpty()) {
            long startCorner = edges.keySet().iterator().next();
            List<long[]> corners = new ArrayList<>();
            long cur = startCorner;
            do {
                Deque<Long> ends = edges.get(cur);
                long next = ends.poll();
                if (ends.isEmpty()) edges.remove(cur);
                corners.add(decode(cur));
                cur = next;
            } while (cur != startCorner);

            List<long[]> simplified = dropCollinear(corners);
            if (simplified.size() < 3) continue;
            loops.add(toPolygon(simplified));
        }
        return loops;
    }

    private static void addEdge(Map<Long, Deque<Long>> edges, long start, long end) {
        edges.computeIfAbsent(start, k -> new ArrayDeque<>()).add(end);
    }

    private static int largestLoopIndex(List<MapPolygon> loops) {
        int best = 0;
        double bestArea = -1;
        for (int i = 0; i < loops.size(); i++) {
            double a = Math.abs(signedArea(loops.get(i)));
            if (a > bestArea) {
                bestArea = a;
                best = i;
            }
        }
        return best;
    }

    private static double signedArea(MapPolygon poly) {
        List<BlockPos> pts = poly.getPoints();
        double area = 0;
        for (int i = 0; i < pts.size(); i++) {
            BlockPos a = pts.get(i);
            BlockPos b = pts.get((i + 1) % pts.size());
            area += (double) a.getX() * b.getZ() - (double) b.getX() * a.getZ();
        }
        return area / 2.0;
    }

    private static List<long[]> dropCollinear(List<long[]> corners) {
        int n = corners.size();
        List<long[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long[] prev = corners.get((i - 1 + n) % n);
            long[] cur = corners.get(i);
            long[] next = corners.get((i + 1) % n);
            long cross = (cur[0] - prev[0]) * (next[1] - cur[1]) - (cur[1] - prev[1]) * (next[0] - cur[0]);
            if (cross != 0) out.add(cur);
        }
        return out;
    }

    private static MapPolygon toPolygon(List<long[]> corners) {
        int startIdx = 0;
        for (int i = 1; i < corners.size(); i++) {
            long[] c = corners.get(i);
            long[] s = corners.get(startIdx);
            if (c[0] < s[0] || (c[0] == s[0] && c[1] > s[1])) startIdx = i;
        }
        List<BlockPos> points = new ArrayList<>(corners.size());
        for (int i = 0; i < corners.size(); i++) {
            long[] c = corners.get((startIdx + i) % corners.size());
            points.add(new BlockPos((int) c[0] * BLOCKS_PER_CHUNK, CLAIM_Y, (int) c[1] * BLOCKS_PER_CHUNK));
        }
        return new MapPolygon(points);
    }

    private static long corner(int gx, int gz) {
        return ((long) gx << 32) | (gz & 0xFFFFFFFFL);
    }

    private static long[] decode(long c) {
        return new long[] { (int) (c >> 32), (int) c };
    }

    // --- Color / label ---

    private static ShapeProperties createShapeProperties(int color, boolean forceLoaded) {
        return new ShapeProperties()
                .setStrokeColor(color)
                .setStrokeOpacity(forceLoaded ? FORCE_LOADED_STROKE_OPACITY : STROKE_OPACITY)
                .setStrokeWidth(forceLoaded ? FORCE_LOADED_STROKE_WIDTH : STROKE_WIDTH)
                .setFillColor(color)
                .setFillOpacity(FILL_OPACITY);
    }

    private static int resolveAreaColor(ClaimedChunkData claim, UUID playerUUID) {
        if (claim.ownerUUID.equals(playerUUID)) {
            return COLOR_OWN;
        }
        Party localParty = ClientPartyCache.getPartyByPlayer(playerUUID);
        if (localParty != null && localParty.isMember(claim.ownerUUID)) {
            return COLOR_PARTY;
        }
        return COLOR_OTHER;
    }

    private static int resolveTextColor(ClaimedChunkData claim) {
        Party ownerParty = ClientPartyCache.getPartyByPlayer(claim.ownerUUID);
        if (ownerParty != null) {
            return ownerParty.getColor() & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    private static String buildTitle(ClaimedChunkData claim, boolean allForceLoaded) {
        StringBuilder sb = new StringBuilder();
        sb.append(claim.ownerName);
        if (!claim.partyName.isEmpty()) {
            sb.append(" [").append(claim.partyName).append("]");
        }
        if (allForceLoaded) {
            sb.append(I18n.format("blpc.addons.journeymap.force_loaded_suffix"));
        }
        return sb.toString();
    }
}
