# CLAUDE.md

BLPC — Minecraft 1.12.2 Forge mod. Chunk claiming with party-based sharing. Optional BetterQuesting integration.

## Build

RetroFuturaGradle (RFG) + GTNH Buildscripts. **Do not edit `build.gradle`** (auto-generated). Config: `buildscript.properties`.

```bash
./gradlew build              # Full build (includes spotlessCheck)
./gradlew runClient          # Launch Minecraft client with the mod
./gradlew runServer          # Launch Minecraft server with the mod
./gradlew spotlessApply      # Auto-format code (run before committing)
./gradlew spotlessCheck      # Check formatting without fixing
./gradlew test               # Run JUnit 5 tests
```

## Key Rules

- **Java 17 syntax mandatory** (Jabel → JVM 8): switch expressions (`->`), pattern matching `instanceof`, `var` for obvious types. Details in `.claude/skills/blpc-overview/SKILL.md`.
- **Imports**: Always use `import` statements, not FQCN. Spotless enforces ordering.
- **Network messages**: Append to `ModNetwork.init()`, never insert.

## Architecture

See `.claude/skills/blpc-overview/SKILL.md` for full reference (package layout, conventions, data schemas, UI patterns, config, etc.).
