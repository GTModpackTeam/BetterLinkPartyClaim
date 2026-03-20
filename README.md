# BQuClaim

BQuClaim is a Minecraft Forge mod for Minecraft 1.12.2 that provides chunk claiming integrated with the BetterQuesting party system. Players can claim chunks, share access with party members, and optionally force-load claimed chunks. It includes a full-screen claim map (ModularUI) and a small minimap HUD.

## Features

- Claim and unclaim chunks via an in-game map UI.
- Force-load claimed chunks (respecting per-player limits).
- Party-aware permissions: members of the same BetterQuesting party are treated as allies.
- Client-side minimap HUD showing nearby chunks and claims.
- Network sync of claim data between server and clients.
- Async chunk map rendering with texture caching for performance.

## Requirements

- Minecraft 1.12.2
- Minecraft Forge (recommended for 1.12.2)
- BetterQuesting (required)
- ModularUI (required)
- JourneyMap (optional integration)

## Installation

Place the built mod jar into your `mods` folder for both client and server (if running a server). Build the project using Gradle as described below.

## Building

This repo uses a Gradle-based build (RetroFuturaGradle / GTNH buildscripts). Common commands:

- `./gradlew build` - Full build (formats code with Spotless)
- `./gradlew runClient` - Launch client with the mod
- `./gradlew runServer` - Launch dedicated server
- `./gradlew spotlessApply` - Apply formatting rules

See `build.gradle` and `buildscript.properties` for build configuration.

## Configuration

Mod configuration is exposed via Forge `@Config` (`com.github.gtexpert.bquclaim.ModConfig`). Available options:

- `maxClaimsPerPlayer` (int, default 64) — maximum number of chunks a player may claim.
- `maxForceLoadsPerPlayer` (int, default 8) — maximum number of chunks a player may force-load.
- `showMinimap` (boolean, default true) — toggle the minimap HUD.

Changes made from the in-game config GUI are synced immediately.

## Usage

- Default keybindings:
  - `M` — Open the full-screen chunk map.
  - `N` — Toggle minimap HUD on/off.

- Map controls (full-screen):
  - Left click — Claim a chunk.
  - Shift + Left click — Claim and force-load a chunk.
  - Right click — Unclaim a chunk.
  - Dragging with mouse performs bulk operations across dragged chunks.
  - The UI also provides buttons to redraw the map, unclaim all your chunks, or unload all your force-loaded chunks.

- Claim permissions: only the chunk owner (or a server operator) can unclaim or toggle force-load. Players in the same BetterQuesting party are shown as allies on the map.

## Data persistence

Claims are stored server-side in `ChunkManagerData` (a `WorldSavedData` implementation). Each claimed chunk records:

- Chunk coordinates (x, z)
- Owner UUID and player name
- Force-loaded flag

The code reads both `force` and legacy `is_force_loaded` flags for backwards compatibility.

## Developer notes

- Base package: `com.github.gtexpert.bquclaim`.
- Network messages are implemented using `SimpleNetworkWrapper` in `ModNetwork`.
  - `MessageClaimChunk` (client -> server) — request to claim/unclaim/toggle force.
  - `MessageSyncClaims` / `MessageSyncAllClaims` / `MessageSyncConfig` (server -> client) — sync claim and config data.
- Map rendering:
  - `AsyncMapRenderer` computes a 16x16 color array for chunks off the main thread.
  - `TextureCache` creates `DynamicTexture` instances and caches them.
  - `ChunkMapRenderer` draws terrain + claim overlays and player icon.
- Force loading uses Forge's `ForgeChunkManager` with `TicketManager` to persist and restore forced chunks.

## Code pointers

- Mod entry: `src/main/java/com/github/gtexpert/bquclaim/BQuClaim.java`
- Claim storage: `src/main/java/com/github/gtexpert/bquclaim/chunk/ChunkManagerData.java`
- Network: `src/main/java/com/github/gtexpert/bquclaim/network/ModNetwork.java`
- GUI: `src/main/java/com/github/gtexpert/bquclaim/gui/` (map, widgets, HUD, keybinds)
- Map: `src/main/java/com/github/gtexpert/bquclaim/map/` (async renderer, texture cache)
- BetterQuesting party helper: `src/main/java/com/github/gtexpert/bquclaim/BQPartyHelper.java`

## Contributing

- Follow the project's formatting rules: run `./gradlew spotlessApply` before committing.
- When adding network messages, append them in `ModNetwork.init()` to preserve discriminator ordering.

## License

This project is licensed under the terms in the `LICENSE` file.
