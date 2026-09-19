---
name: blpc-overview
description: >-
  Architecture overview for the BLPC project (parent skill).
  Detailed references are split into feature-specific skills (blpc-network, blpc-party, blpc-gui, blpc-integration-jmap, blpc-config).
user-invocable: false
---

# BLPC Architecture Reference

Base package: `com.github.gtexpert.blpc`.

## Related Skills

| Skill | Content |
|---|---|
| `blpc-network` | Network layer (wire protocol, PartyAction dispatch, ClientNotify) |
| `blpc-party` | Party system (Provider SPI, Trust, BQu integration, persistence) |
| `blpc-gui` | GUI/UI (panel catalog, color conventions, widgets, sync patterns, commands) |
| `blpc-integration-jmap` | JourneyMap integration (v2 API, overlays, Waypoint Team Sync) |
| `blpc-integration-bqu` | BetterQuesting integration (BQuPartyProvider, link/unlink, Mixin) |
| `blpc-config` | Configuration (ModConfig, Chunk Transit, Mixins) |

## Build System

RetroFuturaGradle (RFG) with GTNH Buildscripts. **Do not edit `build.gradle`** (auto-generated). Mod-specific config: `buildscript.properties`. Dependencies: `dependencies.gradle`. Debug flags: `debug_bqu`, `debug_jmap`, `debug_all` in `buildscript.properties`. Spotless enforced (formatting: `spotless.importorder` local + `spotless.eclipseformat.xml` via Blowdryer).

| Dependency | Role | Required? |
|---|---|---|
| ModularUI | GUI framework | Yes |
| BetterQuesting Unofficial | Party system backend (when present) | Optional (module) |
| JourneyMap API (`journeymap-api-forge:1.12.2-2.0.0`, `compileOnlyApi`) | v2 overlay/waypoint/options API | Optional |
| JourneyMap mod jar (`compileOnly`, not runtime-required) | Compile-time target for `AddonOptionsManager` reference | Optional |

MixinBooter is pinned to **v10.7** (`mixinProviderSpec` in `buildscript.properties`) — v11.5 causes dev-environment loading failures.

## Java 25 Syntax (Mandatory)

Jabel (`enableModernJavaSyntax = true`) compiles Java 25 features to JVM 8 bytecode.

| Feature | Requirement | Example |
|---|---|---|
| **Switch expressions** | Always use arrow form (`->`) | `case X -> { ... }` |
| **Pattern matching instanceof** | Always use instead of separate cast | `if (obj instanceof MyClass mc)` |
| **`var`** | Use for local variables where type is obvious | `var entry : map.entrySet()` |
| **Multi-label case** | Combine related cases | `case A, B, C -> { ... }` |

Do NOT use `var` for: primitives, ambiguous types, or fields.

## Module System

Annotation-driven module framework:

- **`api/modules/`** — `IModule`, `TModule` (annotation), `IModuleContainer`, `ModuleContainer`, `ModuleStage`, `IModuleManager`.
- **`module/`** — `ModuleManager` (ASM scanning, dependency resolution, config-driven enable/disable), `Modules` (container + module ID constants), `BaseModule`.
- **`core/CoreModule`** — `@TModule(coreModule=true)`. Registers network packets, ForgeChunkManager callback, and `DefaultPartyProvider`.
- **`integration/IntegrationModule`** — Parent gate for all integration submodules.
- **`integration/IntegrationSubmodule`** — Abstract base for mod-specific integrations.

Modules discovered at FML Construction via `@TModule`. `modDependencies` gates on `Loader.isModLoaded()`. Module enable/disable: `config/blpc/modules.cfg`.

## Naming Conventions

- **Panel IDs:** `blpc.map`, `blpc.party`, `blpc.map.dialog.confirm`, `blpc.party.dialog.invite`
- **Lang keys:** `blpc.map.*` for map screen, `blpc.party.*` for party screen
- **Mod ID constants:** `api/util/Mods.Names`

## Package Layout

**Start here:** `api/BLPCAPI` is the central access point and discoverability index — one façade documenting every subsystem and addon extension point (`partyProvider()`, `moduleManager()`, `MODID`). Read it first.

- **`api/`** — Public addon-facing surface. `BLPCAPI` (façade/index), `modules/`, `party/` (SPI + domain types), `event/` (`ChunkModifiedEvent`, `PartyEvent`), `util/` (`Mods`, `ModUtility`, `PartyQueryUtil`, `EnumUtils`), `integration/` (`IntegrationPanelRegistry`).
- **`common/party/`** — `PartyManagerData`, `DefaultPartyProvider`, `ClientPartyCache`.
- **`common/chunk/`** — `ChunkManagerData`, `ClaimedChunkData`, `ClientClaimCache`, `TicketManager`.
- **`common/waypoint/`** — `PartyWaypointData`, `WaypointManagerData`, `ClientWaypointCache`.
- **`common/network/`** — IMessage contracts (see `blpc-network`).
- **`client/network/`** — S→C handlers `@SideOnly(Side.CLIENT)` (see `blpc-network`).
- **`client/gui/`** — ModularUI screens (see `blpc-gui`).
- **`client/input/`** — `KeyInputHandler`. Two keybinds, category `key.categories.blpc`, both `KeyConflictContext.IN_GAME`: `open_map` (M) → `Screens.openMap()`, `open_party` (P) → `Screens.openPartyDirect()`. Registered in `init()` (FMLInitializationEvent).
- **`client/map/`** — Async chunk rendering, texture caching, claim overlay.
- **`client/cache/`** — `ClientCacheKey` + `ClientCachePersistence` (debounced NBT snapshot for reconnect).
- **`integration/jmap/`** — JourneyMap v2 API integration (see `blpc-integration-jmap`).
- **`integration/bqu/`** — BetterQuesting integration (see `blpc-integration-bqu`).

## Localization

Lang files in `src/main/resources/assets/blpc/lang/`: `en_us.lang` and `ja_jp.lang`. Both cover keybindings, commands, map UI, party UI, roles, trust, protection, allies/enemies, tooltips, search, transit notifications, party event/claim failure notifications, addon panels, and Fair Play config.

## Adding a New Integration Module

1. Create `integration/<modid>/` package.
2. Create module class extending `IntegrationSubmodule` with `@TModule(modDependencies=Mods.Names.THE_MOD)`.
3. Add module ID constant to `Modules.java`.
4. Add mod ID to `Mods` enum and `Mods.Names`.
5. (Optional) Register settings: `IntegrationPanelRegistry.register(...)` for panel-backed or `registerAction(...)` for action-only.
