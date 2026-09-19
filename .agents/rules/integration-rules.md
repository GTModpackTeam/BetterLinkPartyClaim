Rules for mod integration code. All integration modules MUST follow these.

## BetterQuesting Integration

- ALL BQu API calls MUST go through `integration/bqu/` package via `BQuPartyProvider`.
- NEVER call BQu API directly from outside the integration package.

## JourneyMap Integration (v2 API, JourneyMap v6+)

- Use `journeymap.api.v2.*` packages ONLY — NEVER `journeymap.client.api.*` (v1).
- Plugin annotation: `@JourneyMapPlugin` — NEVER `@ClientPlugin`.
- Event subscription: via `ClientEventRegistry` / `FullscreenEventRegistry` / `CommonEventRegistry` — NEVER `api.subscribe()`.
- Per-client settings: register as `Option` instances via `OptionsRegistry` — NEVER ad-hoc static booleans.
- `JMapClientConfig` is backed by JMap v2 `Option` instances initialized in `JMapPlugin.registerOptions()`. Read values via `JMapClientConfig.isShowClaimOverlays()` etc. — they delegate to `Option.get()`.
- Shared waypoint groups: create via `WaypointFactory.createWaypointGroup(modId, name)`. Set `setLocked(true)`, `setPersistent(false)`. Look up existing groups via `api.getWaypointGroupByName(modId, name)` — NEVER by guid (it's random UUID).
- Waypoint sharing disable: use `group.setEnabled(false)` to hide — NEVER delete the group. Group deletion only on party leave/disband.
- JourneyMap API dependency: `compileOnlyApi` scope — NEVER change to `api` or `compileOnly` (causes runtime class conflicts with duplicate `ClientEventRegistry`).
- JourneyMap mod jar: `compileOnly` + conditional `runtimeOnly` (debug flags).
- `@Mod` dependencies: `after:journeymap@[1.12.2-6.0.0-beta.2,)` — v6+ required.
- There is NO `WaypointStoreMixin` — it was removed in v0.15.0. Waypoint change detection uses the v2 `CommonEventRegistry.WAYPOINT_EVENT` API. Flag any new Mixin into JourneyMap internals as a regression.

## Optional Mod Dependencies

- Gate with `Loader.isModLoaded()` or `@TModule(modDependencies=...)`.
- Integration modules MUST extend `IntegrationSubmodule`.

## Addons Hub (IntegrationPanelRegistry)

- Panel-backed entries: `IntegrationPanelRegistry.register(labelKey, tooltipKey, available, factory)`.
- Action-only entries (opening external screen): `IntegrationPanelRegistry.registerAction(labelKey, tooltipKey, available, action)`.
- `AddonsPanel` handles `entry.hasPanel()` branching — do NOT bypass this.

## MixinBooter

- Pinned to v10.7. v11.5 causes mod-loading failures in dev environments.
- `BLPCMixinLoader` loads configs for `betterquesting` and `modularui` only — no JourneyMap entry.
