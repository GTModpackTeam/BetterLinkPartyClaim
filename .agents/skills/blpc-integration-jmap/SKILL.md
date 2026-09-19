---
name: blpc-integration-jmap
description: JourneyMap v2 API integration reference for BLPC — overlays, addon buttons, OptionsRegistry, Waypoint Team Sync.
user-invocable: false
---

# BLPC JourneyMap Integration (v2 API)

Requires JourneyMap v6+ (API v2, `journeymap.api.v2.*`). `@Mod` dependency: `after:journeymap@[1.12.2-6.0.0-beta.2,)`.

API dependency: `journeymap-api-forge:1.12.2-2.0.0` with `compileOnlyApi` scope — NEVER `api` (causes duplicate `ClientEventRegistry` class conflicts at runtime).

## JMapPlugin

`@JourneyMapPlugin(apiVersion = "2.0.0", dependencies = {})`, implements `journeymap.api.v2.client.IClientPlugin`.

`initialize(IClientAPI)` subscribes to:
- `ClientEventRegistry.DISPLAY_UPDATE_EVENT` / `MAPPING_EVENT` — redraw/clear claim overlays.
- `FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT` — fullscreen toggle button for overlay visibility.
- `CommonEventRegistry.WAYPOINT_GROUP_EVENT` / `WAYPOINT_EVENT` — guard against group/waypoint deletion (re-sync on DELETED).

`registerOptions()` registers three options under a `BLPC` `OptionCategory` via `OptionsRegistry`:
- `showClaimOverlays` (`BooleanOption`, default true)
- `waypointSharing` (`BooleanOption`, default true)
- `waypointSyncInterval` (`IntegerOption`, 0–6000, default 100)

These appear in JourneyMap's own Addon Options screen.

## JMapClientConfig

Backed by the `Option` instances registered above. `init(overlays, waypoints, syncInterval)` called from `registerOptions()`. Getters (`isShowClaimOverlays()`, `isWaypointSharingEnabled()`, `getWaypointSyncInterval()`) delegate to `Option.get()` with safe defaults before init.

## JMapSettingsPanel

No longer builds a `ModularPanel`. `open()` calls `mc.displayGuiScreen(new AddonOptionsManager(...))` to jump to JourneyMap's Addon Options. Registered via `IntegrationPanelRegistry.registerAction(...)` (action-only, no sub-panel).

## Claim Overlays

Contiguous chunks merged into single polygon (outer perimeter + holes). `PolygonOverlay(modId, dimension, shapeProperties, outer, holes)`. Colors: own=green, party=cyan, other=red. Force-loaded: bolder stroke.

`refreshOverlays(dimension)` triggered by:
- `JMapClaimSyncHandler` (change listener on `ClientClaimCache`)
- `DisplayUpdateEvent` / `MappingEvent` from JMap

## Waypoint Team Sync

Party-owned waypoints mirrored to all online members via a locked "BLPC Party" `WaypointGroup`.

**Permission model:** OWNER-only mutation, enforced server-side in `WaypointAction.Handler`.

**Outgoing flow (owner → server):**
1. `JMapWaypointOutgoing.register()` subscribes to `CommonEventRegistry.WAYPOINT_EVENT` (CREATE/UPDATE/DELETED). No Mixin needed — JMap v6 fires this natively.
2. Filters: remote-echoed changes (`applyingRemoteChange`), BLPC's own waypoints (`Tags.MODID`), non-owners, sharing-disabled. Death waypoints excluded by API design.
3. Sends `WaypointAction.addOrUpdate(...)` / `.remove(...)` to server.

**Server (`WaypointAction.Handler`):** validates, authorizes (OWNER-only with rollback), applies to `WaypointManagerData`, broadcasts `WaypointSync` to other members.

**Incoming flow:**
- `WaypointSyncClientHandler` / `SyncAllWaypointsClientHandler` → `ClientWaypointCache` → `JMapWaypointSyncHandler`
- Creates/updates locked "BLPC Party" group: `WaypointFactory.createWaypointGroup(Tags.MODID, GROUP_NAME)`, `setLocked(true)`, `setPersistent(false)`. Lookup via `getWaypointGroupByName(Tags.MODID, GROUP_NAME)`.
- Sharing disabled → `group.setEnabled(false)` (hidden, not deleted). Group only removed on party leave/disband.
- Group/waypoint deletion guard: `WaypointGroupEvent.DELETED` / `WaypointEvent.DELETED` for BLPC's modId → immediate re-sync.

**Periodic sync:** `JMapWaypointSyncHandler.onClientTick` re-applies every `getWaypointSyncInterval()` ticks (0 = disabled).

**Key classes:** `JMapPlugin`, `JMapClientConfig`, `JMapSettingsPanel`, `JMapClaimSyncHandler`, `JMapWaypointOutgoing`, `JMapWaypointSyncHandler`, `JMapModule`.
