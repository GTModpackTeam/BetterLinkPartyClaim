---
name: blpc-overview
description: >-
  Architecture overview for the BLPC project (parent skill).
  Detailed references are split into feature-specific skills (blpc-network, blpc-party, blpc-gui, blpc-integration-jmap, blpc-config, addon).
user-invocable: false
---

# BLPC Architecture Reference

Base package: `com.github.gtexpert.blpc`.

## Related Skills

| Skill | Content |
|---|---|
| `blpc-network` | Network layer (wire protocol, PartyAction dispatch, ClientNotify) |
| `blpc-party` | Party system (Provider SPI, Trust, PartyRole, BQu integration, persistence, O(1) reverse index) |
| `blpc-gui` | GUI/UI (panel catalog, Screens constants, color conventions, widgets, sync patterns, commands) |
| `blpc-integration-jmap` | JourneyMap integration (v2 API, overlays, Waypoint Team Sync) |
| `blpc-integration-bqu` | BetterQuesting integration (BQuPartyProvider, link/unlink, Mixin) |
| `blpc-config` | Configuration (ModConfig, Chunk Transit, Mixins) |
| `addon` | AddonRegistry API, AddonsPanel, third-party integration registration |

## Build System

RetroFuturaGradle (RFG) + GTNH Buildscripts. **Do not edit `build.gradle`** (auto-generated). Config: `buildscript.properties`, deps: `dependencies.gradle`. Spotless enforced.

| Dependency | Role | Required? |
|---|---|---|
| ModularUI | GUI framework | Yes |
| BetterQuesting Unofficial | Party system backend | Optional |
| JourneyMap API (`compileOnlyApi`) | v2 overlay/waypoint/options | Optional |

MixinBooter v10.7. v11.5 causes dev-loading failures.

## Java 25 Syntax (Mandatory)

Arrow switch, pattern instanceof, `var` for obvious types, multi-label case. NEVER `var` for primitives/fields/ambiguous.

## Module System

- **`api/modules/`** — `IModule`, `@TModule`, `IModuleContainer`, `ModuleContainer`, `ModuleStage`, `IModuleManager`.
- **`module/`** — `ModuleManager` (ASM scanning, config-driven enable/disable), `Modules` (constants), `BaseModule`.
- **`core/CoreModule`** — `@TModule(coreModule=true)`. Registers network, ForgeChunkManager, `DefaultPartyProvider`.
- **`integration/`** — `IntegrationModule` (parent gate), `IntegrationSubmodule` (abstract base).

Discovered at FML Construction via `@TModule`. `modDependencies` gates on `Loader.isModLoaded()`. Enable/disable: `config/blpc/modules.cfg`.

## Package Layout

**Start here:** `api/BLPCAPI` — central access point and discoverability index. Read first.

- **`api/`** — Public addon surface. `BLPCAPI`, `modules/`, `party/` (`Party`, `PartyRole`, `TrustLevel`, `TrustAction`, `RelationType`, `IPartyProvider` with `getOwner`, `getAllParties`, `countClaims` default methods, `PartyProviderRegistry`), `event/` (`ChunkModifiedEvent`, `PartyEvent`), `util/` (`Mods`, `ModUtility`, `PartyQueryUtil` — query party data without depending on internal packages, `EnumUtils`), `integration/` (`AddonRegistry`, `IntegrationPanelRegistry`).
- **`common/party/`** — `PartyManagerData` (O(1) reverse index for `getPartyByPlayer`), `DefaultPartyProvider`, `ClientPartyCache`.
- **`common/chunk/`** — `ChunkManagerData`, `ClaimedChunkData`, `ClientClaimCache`, `TicketManager`.
- **`common/waypoint/`** — `PartyWaypointData`, `WaypointManagerData`, `ClientWaypointCache`.
- **`common/network/`** — IMessage contracts. `ModNetwork`, `NbtMessage`, `PlayerLoginHandler`.
- **`client/network/`** — S→C handlers `@SideOnly(CLIENT)`. `ClientPacketHandlers` (SPI installer).
- **`client/gui/`** — ModularUI screens. `Screens` (catalog + constants), `AddonsPanel`, `PartyWidgets` (`collectSortedMembers` with `roleFilter`), `PartyMenuBuilder`, `BLPCColors`, `GuiColors`.
- **`client/map/`** — Async chunk rendering. `ChunkMapScreen`, `ChunkMapWidget`, `AsyncMapRenderer`, `TextureCache`.
- **`client/input/`** — `KeyInputHandler` (open_map M, open_party P).
- **`client/cache/`** — `ClientCacheKey`, `ClientCachePersistence`.
- **`core/`** — `ChunkProtectionHandler`, `ChunkTransitHandler`, `CoreEventHandler` (uses `PartyProviderRegistry.get().getAllParties()`), `CoreModule`.
- **`mixins/`** — `BLPCMixinLoader`, `NetPartyActionMixin`, `OverlayStackMixin`.
- **`integration/`** — `BQuModule`/`BQuPartyProvider`, `JMapModule`/`JMapPlugin`. Addon registration via `AddonRegistry`.
- **`modules/`** — `BaseModule`, `ModuleManager`, `Modules`.

## API Usage Guidelines

- **Query operations**: Use `PartyQueryUtil` or `IPartyProvider` methods. Never call `PartyManagerData.getInstance().getPartyByPlayer()` from outside internal packages.
- **Mutation operations**: Use `PartyManagerData.addMember()`/`removeMember()`/`setRole()` — never direct `Party.addMember()`/`Party.removeMember()`. This maintains the O(1) reverse index.
- **Admin commands**: Use `BLPCCommandHelper.resolveParty(player)` instead of direct `PartyManagerData` calls.

## Localization

`en_us.lang` and `ja_jp.lang` — keybindings, commands, map UI, party UI, roles, trust, protection, allies/enemies, tooltips, search, transit, party events, addon panels, Fair Play config.

## Adding a New Integration Module

1. Create `integration/<modid>/` package.
2. Class extending `IntegrationSubmodule` with `@TModule(modDependencies=Mods.Names.THE_MOD)`.
3. Add module ID to `Modules.java`. Add mod ID to `Mods` enum + `Mods.Names`.
4. Register via `AddonRegistry.register()` or `registerAction()` with `modId` — NEVER raw `IntegrationPanelRegistry`.
