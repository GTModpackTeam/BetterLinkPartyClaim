Rules for mod integration code. All integration modules MUST follow these.

## BetterQuesting Integration

- ALL BQu API calls MUST go through `integration/bqu/` package via `BQuPartyProvider`.
- NEVER call BQu API directly from outside the integration package.

## JourneyMap Integration (v2 API, JourneyMap v6+)

- Use `journeymap.api.v2.*` ONLY — NEVER `journeymap.client.api.*` (v1).
- Plugin annotation: `@JourneyMapPlugin` — NEVER `@ClientPlugin`.
- Event subscription: via `ClientEventRegistry` / `FullscreenEventRegistry` / `CommonEventRegistry` — NEVER `api.subscribe()`.
- Per-client settings: register as `Option` instances via `OptionsRegistry` — NEVER ad-hoc static booleans.
- Shared waypoint groups: create via `WaypointFactory.createWaypointGroup(modId, name)`. `setLocked(true)`, `setPersistent(false)`. Look up via `getWaypointGroupByName(modId, name)` — NEVER by guid.
- Waypoint sharing disable: `group.setEnabled(false)` — NEVER delete. Delete only on leave/disband.
- JourneyMap API dependency: `compileOnlyApi` — NEVER `api` (causes `ClientEventRegistry` conflicts).
- `@Mod` dependencies: `after:journeymap@[1.12.2-6.0.0-beta.2,)`.
- NO `WaypointStoreMixin` (removed v0.15.0). Flag any new Mixin into JourneyMap internals as regression.

## Addons Hub

Third-party integrations MUST use `AddonRegistry`:

- Panel-backed: `AddonRegistry.register(modId, labelKey, tooltipKey, available, factory)`.
- Action-only: `AddonRegistry.registerAction(modId, labelKey, tooltipKey, available, action)`.
- `modId` provides deduplication. `AddonsPanel` handles `entry.hasPanel()` — do NOT bypass.

## MixinBooter

- Pinned to v10.7. `BLPCMixinLoader` loads `betterquesting` + `modularui` only — no JourneyMap entry.
