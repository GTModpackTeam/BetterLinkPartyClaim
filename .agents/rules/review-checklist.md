Code review checklist. Apply to ALL code changes before merge.

- Module pattern compliance (`@TModule`, `IntegrationSubmodule`)
- Panel ID: `blpc.<area>`, lang key: `blpc.<area>.*`
- Wire protocol: no renumbering/inserting, NBT-S→C extends `NbtMessage`
- Side boundary: S→C in `client/network/` with `@SideOnly(CLIENT)`, `common/network/` has no `@SideOnly` refs
- No duplicate logic — use `ConfirmDialog`, `InputDialog`, `LiveSearchableList`, `PartyWidgets.*`
- Live-update: read fresh `Party` via `livePartyRef`, never hold captured `Party`
- `MainPanel` uses `PartyMenuBuilder`, logging uses `ModLog.*`
- Java 25: arrow switch, pattern instanceof, `var`, multi-label — ALL mandatory
- Public API classes have Javadoc, non-obvious logic has "why" comments, no stale comments
- BQu integration only through `integration/bqu/` via `BQuPartyProvider`
- Optional mods gated with `@TModule(modDependencies=...)`
- JMap v2 only (`journeymap.api.v2.*`), `@JourneyMapPlugin`, event registries
- Addon registration via `AddonRegistry.register()`/`registerAction()` with `modId`
- `BQuPartyProvider.findByName()`, `allPartyNames()`, `pendingInvitesFor()` → `DefaultPartyProvider` fallback
- Query methods use `IPartyProvider` / `PartyQueryUtil` — NEVER `PartyManagerData.getInstance().getPartyByPlayer()` from outside internal packages
- Mutation methods use `PartyManagerData.addMember()`/`removeMember()`/`setRole()` — NEVER direct `Party.addMember()`/`Party.removeMember()` from outside `PartyManagerData`

Report: CRITICAL → WARNING → SUGGESTION. Each: file, line, description, fix.
