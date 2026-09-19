Code review checklist. Apply to ALL code changes before merge.

## Architecture & Conventions

- Module pattern compliance (`@TModule`, `IntegrationSubmodule`, etc.)
- Panel ID naming: `blpc.<area>`, `blpc.<area>.dialog.<name>`
- Lang key naming: `blpc.<area>.*`
- Wire protocol stability: no renumbering, no inserting
- NBT-payload S→C messages extend `NbtMessage`
- Side boundary: S→C handlers in `client/network/` with `@SideOnly(Side.CLIENT)`; IMessage classes in `common/network/` reference no client-only types

## Code Quality

- No duplicate logic — use existing templates (`ConfirmDialog`, `InputDialog`, `LiveSearchableList`, `PartyWidgets.*`)
- `PartyWidgets` utility methods and size constants used where applicable
- Live-update panels read fresh `Party` via `livePartyRef` — never hold captured `Party` across syncs
- `MainPanel` menu entries use `PartyMenuBuilder` fluent API
- Proper `ModLog` categories for logging
- Trust level / trust action consistency

## Java 25 Syntax

- Switch expressions (arrow form), pattern matching instanceof, `var`, multi-label case — ALL mandatory

## Comments & Javadoc

- Public API classes (`api/` package) have Javadoc
- Non-obvious logic has "why" comments
- No stale comments or commented-out code

## Consistency

- Same problem solved the same way across codebase
- Consistent error handling, naming, import style, class structure, collection usage

## Safety

- No security issues (command injection, improper permission checks)
- Commands check permission levels correctly
- Forge event handlers respect `enableProtection` config toggle
- NBT read/write backwards compatibility maintained

## Integration

- BQu integration only through `integration/bqu/` via `BQuPartyProvider`
- Optional mods gated with `@TModule(modDependencies=...)`
- JMap v2 API only (`journeymap.api.v2.*`), `@JourneyMapPlugin`, event registries
- JMap settings via `OptionsRegistry`, waypoint groups via `WaypointFactory`
- `IntegrationPanelRegistry.register()` for panels, `registerAction()` for action-only entries

## Review Output

Report by severity: CRITICAL (must fix) → WARNING (should fix) → SUGGESTION (consider).
Each finding: file path, line number, description, suggested fix.
Verdict: PASS, PASS_WITH_WARNINGS, or FAIL.
