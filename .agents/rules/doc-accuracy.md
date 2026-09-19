Documentation MUST accurately reflect the current codebase. Detect and fix drift immediately.

- Classes, packages, paths in docs MUST exist in code.
- `@TModule` annotations match documentation.
- Wire-protocol ID table matches `ModNetwork.init()`, `CLIENT_BOUND_MESSAGES`, `ClientPacketHandlers.installAll()`.
- `PartyAction` `ACTION_*` constants match `dispatch()` case arms.
- `JMapPlugin` uses `@JourneyMapPlugin`, `OptionsRegistry`, `WaypointFactory`.
- `ModConfig` fields match documented table.
- Panel IDs and file mappings exist in code.
- `en_us.lang` and `ja_jp.lang` cover same keys.
