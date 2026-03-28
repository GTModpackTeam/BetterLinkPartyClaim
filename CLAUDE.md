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

## Key Design Principles

- Base package: `com.github.gtexpert.blpc`
- **Party Provider SPI**: `IPartyProvider` abstracts party management. When BQu is present, `BQPartyProvider` replaces `DefaultPartyProvider` — no data duplication (Approach A).
- **Module system**: Annotation-driven (`@TModule`). Modules discovered at FML Construction via ASM scanning. `modDependencies` gates on `Loader.isModLoaded()`.
- **File-based persistence**: `BLPCSaveHandler` stores data under `world/betterlink/pc/` (not `WorldSavedData`).
- **Network protocol**: Messages use incrementing discriminator IDs in `ModNetwork.init()`. New messages must be appended.

## Key Dependencies

| Dependency | Role | Required? |
|---|---|---|
| ModularUI | GUI framework | Yes |
| BetterQuesting Unofficial | Party system backend (when present) | Optional (module) |
| JourneyMap API | Overlay integration | Optional |

Dependencies in `dependencies.gradle`. Debug flags (`debug_bqu`, `debug_jmap`) in `buildscript.properties`.

## Detailed Architecture

See `.claude/skills/blpc-overview/SKILL.md` for full architecture reference (package layout, naming conventions, data schemas, UI patterns, etc.). This is also injected into all QA team agents via the `skills` field.
