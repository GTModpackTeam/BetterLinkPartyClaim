# AGENTS.md

BLPC — Minecraft 1.12.2 Forge mod. Chunk claiming with party-based sharing. Optional BetterQuesting integration.

## Build

RetroFuturaGradle (RFG v2) + GTNH Buildscripts. **Do not edit `build.gradle`** (auto-generated). Config: `buildscript.properties`.

```bash
./gradlew build              # Full build (includes spotlessCheck)
./gradlew runClient          # Launch Minecraft client with the mod
./gradlew runServer          # Launch Minecraft server with the mod
./gradlew spotlessApply      # Auto-format code (run before committing)
./gradlew spotlessCheck      # Check formatting without fixing
./gradlew test               # Run JUnit 5 tests
```

## Rules

- `.agents/rules/code-conventions.md` — Java 25 syntax (arrow switch, pattern instanceof, `var`), imports, side boundary, network wire protocol, GUI color conventions, key bindings, logging categories.
- `.agents/rules/build-verify.md` — build verification steps (spotless → build → test → runClient).
- `.agents/rules/integration-rules.md` — BQu, JourneyMap, and AddonRegistry integration rules.
- `.agents/rules/doc-accuracy.md` — documentation must match code. Verification checklist.
- `.agents/rules/review-checklist.md` — code review checklist for all PRs.

### Skills

- `.agents/skills/blpc-overview/SKILL.md` — Architecture overview (parent skill, package layout, conventions, data schemas, UI patterns, config, etc.).
- `.agents/skills/blpc-network/SKILL.md` — Network layer (wire protocol, PartyAction dispatch, ClientNotify).
- `.agents/skills/blpc-party/SKILL.md` — Party system (Provider SPI, Trust levels, data persistence, server party).
- `.agents/skills/blpc-gui/SKILL.md` — GUI/UI (panel catalog, color conventions, widgets, sync patterns, commands).
- `.agents/skills/blpc-integration-bqu/SKILL.md` — BetterQuesting integration (BQuPartyProvider, link/unlink flow, Mixin).
- `.agents/skills/blpc-integration-jmap/SKILL.md` — JourneyMap v2 API integration (overlays, addon buttons, OptionsRegistry, Waypoint Team Sync).
- `.agents/skills/blpc-config/SKILL.md` — Configuration (ModConfig, Chunk Transit, Mixins).

### Documentation

- `DEVELOPER.md` — Addon developer guide. AddonRegistry (modId dedup), PartyBackend SPI, QueryPartyUtil, PartyEvent/ChunkModifiedEvent, Module Framework, utility helpers.
- `CHANGELOG.md` — Release notes.
