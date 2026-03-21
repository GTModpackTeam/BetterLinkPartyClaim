# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BQuClaim is a Minecraft 1.12.2 Forge mod that adds chunk claiming with optional BetterQuesting party integration. Players can claim chunks, share access with party members, and force-load claimed chunks. It uses ModularUI for GUI. BetterQuesting is an optional dependency — the mod functions without it via a module system.

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

Base package: `com.github.gtexpert.bquclaim`

### Module System

The mod uses an annotation-driven module framework (same pattern as GTMoreTools/GTWoodProcessing/GTBeesMatrix):

- **`api/modules/`** — Framework interfaces: `IModule`, `TModule` (annotation), `IModuleContainer`, `ModuleContainer`, `ModuleStage`, `IModuleManager`.
- **`module/`** — `ModuleManager` (ASM scanning, dependency resolution, config-driven enable/disable), `Modules` (container + module ID constants), `BaseModule`.
- **`core/CoreModule`** — `@TModule(coreModule=true)`. Registers network packets and ForgeChunkManager callback.
- **`integration/IntegrationModule`** — Parent gate for all integration submodules. Disabling it disables all integrations.
- **`integration/IntegrationSubmodule`** — Abstract base for mod-specific integrations (depends on `MODULE_INTEGRATION`).

Modules are discovered at FML Construction via `@TModule` annotation scanning. The `modDependencies` field gates loading on `Loader.isModLoaded()` — the class is never loaded if the dependency is absent. Module enable/disable config is written to `config/bquclaim/modules.cfg`.

### Party Provider SPI

BetterQuesting integration is decoupled via an SPI:

- **`api/party/IPartyProvider`** — Interface for party membership checks and name lookup.
- **`api/party/PartyProviderRegistry`** — Holds the active provider (defaults to a no-op).
- **`integration/bqu/BQuModule`** — `@TModule(modDependencies="betterquesting")`. Registers `BQPartyProvider` on preInit.
- **`integration/bqu/BQPartyProvider`** — Implements `IPartyProvider` using BQ's `PARTY_DB` API.

Core code (`ChunkMapRenderer`, `MessageClaimChunk`) calls `PartyProviderRegistry.get()` — never imports BQ classes directly.

### Mod ID Constants

All mod IDs are centralized in `api/util/Mods.Names`. Use these constants in `@Mod(dependencies=...)`, `@TModule(modDependencies=...)`, and runtime checks via `Mods.BetterQuesting.isModLoaded()`.

### Package Layout

- **`common/chunk/`** — Claim data layer (both sides): `ChunkManagerData` (WorldSavedData/NBT), `ClaimedChunkData` (value object with owner UUID/name, party name, force-loaded flag), `ClientCache` (client-side mirror), `TicketManager` (ForgeChunkManager callback).
- **`common/network/`** — `SimpleNetworkWrapper` messages: `MessageClaimChunk` (C→S), `MessageSyncClaims`/`MessageSyncAllClaims`/`MessageSyncConfig` (S→C), `PlayerLoginHandler` (syncs on join).
- **`client/gui/`** — ModularUI screens: `ChunkMapScreen`/`ChunkMapWidget` (full-screen claim map), `MinimapHUD` (overlay), `ModKeyBindings`/`KeyInputHandler`.
- **`client/map/`** — Async chunk color computation, texture caching, claim overlay rendering.

### Key Dependencies

| Dependency | Role | Required? |
|---|---|---|
| ModularUI | GUI framework (widgets, screens) | Yes |
| BetterQuesting Unofficial | Party API for shared claims | Optional (module) |
| JourneyMap API | Optional overlay integration | Optional |

Dependencies are declared in `dependencies.gradle`. Use `rfg.deobf()` for obfuscated mod jars. Debug flags (`debug_bqu`, `debug_jmap`) in `buildscript.properties` control runtime inclusion.

### Network Protocol

Messages use incrementing discriminator IDs registered in `ModNetwork.init()`. New messages must be appended to the end to preserve ID ordering.

### Data Persistence

Claims are stored as `WorldSavedData` (NBT) via `ChunkManagerData`. The `ClaimedChunkData` includes a `partyName` field resolved server-side via `PartyProviderRegistry` and synced to clients. The NBT key `"force"` replaced the older `"is_force_loaded"` — both are read for backwards compatibility. The `"party"` key is optional for backwards compatibility with older saves.

### Adding a New Integration Module

1. Create `integration/<modid>/` package.
2. Create a module class extending `IntegrationSubmodule` with `@TModule(modDependencies=Mods.Names.THE_MOD)`.
3. Add module ID constant to `Modules.java`.
4. Add mod ID to `Mods` enum and `Mods.Names`.
