# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BQuClaim is a Minecraft 1.12.2 Forge mod that adds chunk claiming tied to BetterQuesting parties. Players can claim chunks, and party members share claim permissions. It uses ModularUI for GUI and optionally integrates with JourneyMap.

## Build System

This project uses RetroFuturaGradle (RFG) with the GTNH Buildscripts. The main build logic is in `build.gradle` (auto-generated, **do not edit**). Mod-specific configuration lives in `buildscript.properties`.

### Common Commands

```bash
./gradlew build              # Full build (includes spotlessApply)
./gradlew runClient          # Launch Minecraft client with the mod
./gradlew runServer          # Launch Minecraft server with the mod
./gradlew spotlessApply      # Auto-format code
./gradlew spotlessCheck      # Check formatting without fixing
./gradlew test               # Run JUnit 5 tests
```

Spotless is enabled and enforced. Run `spotlessApply` before committing. Formatting rules come from `spotless.importorder` (local) and `spotless.eclipseformat.xml` (fetched via Blowdryer from GTModpackTeam/Buildscripts).

## Architecture

Base package: `com.github.gtexpert.bquclaim`

- **`BQuClaim`** — Main mod class (`@Mod` entry point). Registers network, keybindings, and event handlers. Client-side handlers are registered conditionally via `event.getSide().isClient()`.
- **`BQPartyHelper`** — Queries BetterQuesting API to check if two players share a party. Central to the claim permission model.
- **`ModConfig`** — Forge `@Config` for mod settings.
- **`chunk/`** — Server-side claim data layer.
  - `ChunkManagerData` — `WorldSavedData` subclass that persists claims to NBT. Claims keyed by `"x,z"` string.
  - `ClaimedChunkData` — Value object (chunk coords, owner UUID/name, force-loaded flag).
  - `ClientCache` — Client-side in-memory mirror of claim data, populated via network sync.
  - `TicketManager` — Forge `ForgeChunkManager` callback for force-loaded chunks.
- **`network/`** — Client-server messaging via `SimpleNetworkWrapper`.
  - `MessageClaimChunk` (C→S), `MessageSyncClaims` / `MessageSyncAllClaims` / `MessageSyncConfig` (S→C).
  - `PlayerLoginHandler` — Syncs claim and config data to players on join.
- **`gui/`** — Client-side UI (keybindings, chunk map screen, minimap HUD). Uses ModularUI widgets.
  - `ChunkMapScreen` / `ChunkMapWidget` — Full-screen chunk map for claiming.
  - `MinimapHUD` — In-game overlay showing nearby claims.
  - `ModKeyBindings` / `KeyInputHandler` — Keybind registration and input handling.
- **`map/`** — Map rendering utilities (async chunk rendering, color mapping, texture caching).

### Key Dependencies

| Dependency | Role |
|---|---|
| BetterQuesting Unofficial | Party API for shared claims |
| ModularUI | GUI framework (widgets, screens) |
| JourneyMap API | Optional overlay integration |
| MixinBooter | Mixin support on 1.12.2 |

Dependencies are declared in `dependencies.gradle`. Use `rfg.deobf()` for obfuscated mod jars.

### Network Protocol

Messages use incrementing discriminator IDs registered in `ModNetwork.init()`. Adding new messages must append to the end to preserve ID ordering.

### Data Persistence

Claims are stored as `WorldSavedData` (NBT) via `ChunkManagerData`. The NBT key `"force"` replaced the older `"is_force_loaded"` — both are read for backwards compatibility.
