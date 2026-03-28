package com.github.gtexpert.blpc.integration.jmap;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.chunk.ClientCache;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;

import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.model.ShapeProperties;
import journeymap.client.api.model.TextProperties;
import journeymap.client.api.util.PolygonHelper;

@ClientPlugin
public class BLPCJourneyMapPlugin implements IClientPlugin {

    private static final String OVERLAY_GROUP = "BLPC Claims";
    private static final int COLOR_OWN = 0x00FF00;
    private static final int COLOR_PARTY = 0x00FFFF;
    private static final int COLOR_OTHER = 0xFF0000;
    private static final float FILL_OPACITY = 0.35f;
    private static final float STROKE_OPACITY = 0.6f;

    private IClientAPI api;
    private static BLPCJourneyMapPlugin instance;
    private final Map<String, PolygonOverlay> activeOverlays = new HashMap<>();

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        instance = this;
        api.subscribe(getModId(), EnumSet.of(
                ClientEvent.Type.DISPLAY_UPDATE,
                ClientEvent.Type.MAPPING_STARTED,
                ClientEvent.Type.MAPPING_STOPPED));
    }

    @Override
    public String getModId() {
        return Tags.MODID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        switch (event.type) {
            case DISPLAY_UPDATE:
            case MAPPING_STARTED:
                refreshOverlays(event.dimension);
                break;
            case MAPPING_STOPPED:
                clearOverlays();
                break;
            default:
                break;
        }
    }

    static BLPCJourneyMapPlugin getInstance() {
        return instance;
    }

    void refreshOverlays(int dimension) {
        if (api == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        UUID playerUUID = mc.player.getUniqueID();
        Set<String> currentKeys = new HashSet<>();

        for (ClaimedChunkData claim : ClientCache.getAll()) {
            String key = claim.x + "," + claim.z;
            currentKeys.add(key);

            int areaColor = resolveAreaColor(claim, playerUUID);
            int textColor = resolveTextColor(claim);
            String title = buildTitle(claim);

            PolygonOverlay existing = activeOverlays.get(key);
            if (existing != null) {
                existing.setShapeProperties(createShapeProperties(areaColor));
                existing.setTitle(title);
                existing.getTextProperties().setColor(textColor);
                try {
                    api.show(existing);
                } catch (Exception ignored) {}
            } else {
                PolygonOverlay overlay = createOverlay(claim, dimension, areaColor, textColor, title);
                activeOverlays.put(key, overlay);
                try {
                    api.show(overlay);
                } catch (Exception ignored) {}
            }
        }

        // Remove overlays for chunks no longer claimed
        activeOverlays.entrySet().removeIf(entry -> {
            if (!currentKeys.contains(entry.getKey())) {
                api.remove(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private void clearOverlays() {
        if (api == null) return;
        api.removeAll(getModId(), DisplayType.Polygon);
        activeOverlays.clear();
    }

    private PolygonOverlay createOverlay(ClaimedChunkData claim, int dimension,
                                         int areaColor, int textColor, String title) {
        ShapeProperties shape = createShapeProperties(areaColor);

        PolygonOverlay overlay = new PolygonOverlay(
                getModId(),
                "claim_" + claim.x + "_" + claim.z,
                dimension,
                shape,
                PolygonHelper.createChunkPolygon(claim.x, 70, claim.z));
        overlay.setOverlayGroupName(OVERLAY_GROUP);
        overlay.setTitle(title);
        if (!claim.partyName.isEmpty()) {
            overlay.setLabel(claim.partyName);
        }

        TextProperties text = new TextProperties()
                .setMinZoom(4)
                .setMaxZoom(8)
                .setColor(textColor)
                .setBackgroundOpacity(0.6f)
                .setFontShadow(true);
        overlay.setTextProperties(text);

        return overlay;
    }

    private static ShapeProperties createShapeProperties(int color) {
        return new ShapeProperties()
                .setStrokeColor(color)
                .setStrokeOpacity(STROKE_OPACITY)
                .setStrokeWidth(1.5f)
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

    private static String buildTitle(ClaimedChunkData claim) {
        StringBuilder sb = new StringBuilder();
        sb.append(claim.ownerName);
        if (!claim.partyName.isEmpty()) {
            sb.append(" [").append(claim.partyName).append("]");
        }
        if (claim.isForceLoaded) {
            sb.append(" (Force Loaded)");
        }
        return sb.toString();
    }
}
