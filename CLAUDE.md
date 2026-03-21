# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Better Link Party Claim (BLPC) is a Minecraft 1.12.2 Forge mod that adds chunk claiming with party-based sharing. It has its own party system and optionally integrates with BetterQuesting — when BQu is present, BLPC uses BQu's party system directly (no data duplication). It uses ModularUI for GUI.

## Build System

RetroFuturaGradle (RFG) with GTNH Buildscripts. **Do not edit `build.gradle`** (auto-generated). Mod-specific config lives in `buildscript.properties`.

### Common Commands

```bash
./gradlew build              # Full build (includes spotlessCheck)
./gradlew runClient          # Launch Minecraft client with the mod
./gradlew runServer          # Launch Minecraft server with the mod
./gradlew spotlessApply      # Auto-format code (run before committing)
./gradlew spotlessCheck      # Check formatting without fixing
./gradlew test               # Run JUnit 5 tests
```

Spotless is enforced. Formatting rules: `spotless.importorder` (local) and `spotless.eclipseformat.xml` (fetched via Blowdryer).

## Architecture

Base package: `com.github.gtexpert.blpc`

### Module System

Annotation-driven module framework (same pattern as GTMoreTools/GTWoodProcessing/GTBeesMatrix):

- **`api/modules/`** — `IModule`, `TModule` (annotation), `IModuleContainer`, `ModuleContainer`, `ModuleStage`, `IModuleManager`.
- **`module/`** — `ModuleManager` (ASM scanning, dependency resolution, config-driven enable/disable), `Modules` (container + module ID constants), `BaseModule`.
- **`core/CoreModule`** — `@TModule(coreModule=true)`. Registers network packets, ForgeChunkManager callback, and default `PartyProviderRegistry`.
- **`integration/IntegrationModule`** — Parent gate for all integration submodules.
- **`integration/IntegrationSubmodule`** — Abstract base for mod-specific integrations.

Modules are discovered at FML Construction via `@TModule` annotation scanning. The `modDependencies` field gates loading on `Loader.isModLoaded()`. Module enable/disable config: `config/blpc/modules.cfg`.

### Party Provider SPI

Party management is abstracted via `IPartyProvider`, allowing transparent switching between self-managed parties and BQu's party system:

- **`api/party/IPartyProvider`** — Full interface with query methods (`areInSameParty`, `getPartyName`, `getPartyMembers`, `getRole`) and mutation methods (`createParty`, `disbandParty`, `renameParty`, `invitePlayer`, `acceptInvite`, `kickOrLeave`, `changeRole`, `syncToAll`). All mutation methods identify the party via the acting player's UUID — there is no `partyId` parameter on mutation calls.
- **`api/party/PartyProviderRegistry`** — Holds the active provider.
- **`common/party/DefaultPartyProvider`** — Self-managed implementation backed by `PartyManagerData`. Registered by `CoreModule`.
- **`integration/bqu/BQPartyProvider`** — BQu implementation that directly operates on BQu's `PartyManager`, `PartyInvitations`, and `NetPartySync`, with fallback to `DefaultPartyProvider` for players not in a BQu party. When BQu is present, this **replaces** the default provider — no data duplication.

**Design principle (Approach A):** When BQu is present, BLPC integrates INTO BQu's party system. BLPC's UI sends operations that `BQPartyProvider` translates into BQu API calls. BQu's quest sharing works unchanged.

### BQu Linking Toggle (P4)

Players can individually toggle whether their party is backed by BQu. The linking state is stored in `PartyManagerData.bquLinkedPlayers` (a `Set<UUID>`) and persisted in `config.dat` via `BLPCSaveHandler`. Clients receive the current link flags in `MessageTeamSync` as part of the `serializeForClient` payload. `TeamScreen` branches its UI based on `ClientPartyCache.isBQuLinked(playerId)`:

| BQu installed | Link toggle | Party management UI |
|---|---|---|
| No | — | Full self-managed UI (Invite / Leave / Disband) |
| Yes | OFF | Full self-managed UI (uses local `PartyManagerData`) |
| Yes | ON | Member list + "Open BQu Party Screen" button |

Toggle changes are sent as `MessageTeamAction(ACTION_TOGGLE_BQU_LINK = 7)`.

### Naming Conventions

- **Panel IDs:** `blpc.map`, `blpc.party`, `blpc.map.dialog.confirm`, `blpc.party.dialog.invite`
- **Lang keys:** `blpc.map.*` for map screen, `blpc.party.*` for party screen
- **Mod ID constants:** `api/util/Mods.Names`

### Package Layout

- **`common/party/`** — Party data: `Party`, `PartyRole`, `PartyManagerData`, `DefaultPartyProvider`, `ClientPartyCache`.
- **`common/chunk/`** — Claim data: `ChunkManagerData`, `ClaimedChunkData`, `ClientCache`, `TicketManager`.
- **`common/network/`** — Messages: `MessageClaimChunk` (C→S), `MessageTeamAction` (C→S party ops), `MessageSyncClaims`/`MessageSyncAllClaims`/`MessageSyncConfig`/`MessageTeamSync` (S→C), `PlayerLoginHandler`.
- **`client/gui/`** — ModularUI: `ChunkMapScreen`/`ChunkMapWidget`, `TeamScreen` (party management panel), `MinimapHUD`, `ModKeyBindings`/`KeyInputHandler`.
- **`client/map/`** — Async chunk rendering, texture caching, claim overlay.

### Key Dependencies

| Dependency | Role | Required? |
|---|---|---|
| ModularUI | GUI framework | Yes |
| BetterQuesting Unofficial | Party system backend (when present) | Optional (module) |
| JourneyMap API | Overlay integration | Optional |

Dependencies in `dependencies.gradle`. Debug flags (`debug_bqu`, `debug_jmap`) in `buildscript.properties`.

### Network Protocol

Messages use incrementing discriminator IDs in `ModNetwork.init()`. New messages must be appended to preserve ID ordering.

### Data Persistence

BLPC uses **file-based persistence** (FTB Lib style) instead of `WorldSavedData`. All data is managed by `BLPCSaveHandler.INSTANCE` and stored under `world/betterlink/pc/`:

```
world/betterlink/pc/
├── config.dat          # migrated flag + bquLinkedPlayers set
├── parties/
│   ├── 0.dat           # one compressed NBT file per party (keyed by partyId)
│   └── ...
└── claims/
    ├── global.dat      # claims belonging to players with no party
    ├── 0.dat           # claims belonging to members of party 0
    └── ...
```

`BLPCSaveHandler.loadAll(server)` and `saveAll()` are called by `CoreEventHandler` on world load/save/unload. Neither `ChunkManagerData` nor `PartyManagerData` is a `WorldSavedData` subclass — they are plain singletons reset via their `reset()` static methods.

Claims: `ClaimedChunkData` includes `partyName` resolved server-side via `PartyProviderRegistry`. NBT key `"team"` for party name.

Parties (self-managed mode only): `PartyManagerData`. Not used for storage when BQu is the active backend.

### Adding a New Integration Module

1. Create `integration/<modid>/` package.
2. Create a module class extending `IntegrationSubmodule` with `@TModule(modDependencies=Mods.Names.THE_MOD)`.
3. Add module ID constant to `Modules.java`.
4. Add mod ID to `Mods` enum and `Mods.Names`.
