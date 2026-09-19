Documentation MUST accurately reflect the current codebase. Detect and fix drift immediately.

## Documentation Hierarchy

- `CLAUDE.md` — Concise project overview. MUST NOT be bloated.
- `.claude/skills/blpc-overview/SKILL.md` — Detailed architecture reference. Primary source of truth for patterns, conventions, class lists.
- `.claude/rules/*.md` — Enforced project rules.
- `DEVELOPER.md` — Addon developer guide.
- `CHANGELOG.md` — Release notes.

## Verification Points

- Classes, packages, and paths mentioned in docs MUST exist in code.
- `@TModule` annotations match documentation.
- Wire-protocol ID table matches `ModNetwork.init()`, `CLIENT_BOUND_MESSAGES`, and `ClientPacketHandlers.installAll()`.
- `PartyAction` `ACTION_*` constants match `dispatch()` case arms.
- `BLPCMixinLoader.modMixinsConfig` and `mixins.blpc.*.json` files match docs — there is NO WaypointStoreMixin or JourneyMap mixin config.
- JMap v2 API references use `journeymap.api.v2.*` packages (never old `journeymap.client.api.*`).
- `JMapPlugin` uses `@JourneyMapPlugin`, `OptionsRegistry`, `WaypointFactory` as documented.
- `ModConfig` fields match the documented table (names, types, defaults, ranges).
- Panel IDs and file mappings exist in code.
- `en_us.lang` and `ja_jp.lang` cover the same keys.
