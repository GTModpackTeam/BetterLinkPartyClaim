All Java code MUST follow these conventions. Violations are build-breaking or merge-blocking.

## Java 25 Syntax (Mandatory)

- Arrow-form switch (`case X -> { ... }`), pattern instanceof (`if (obj instanceof T v)`), `var` for obvious types, multi-label case.
- NEVER use colon+break, separate cast, or `var` for primitives/fields/ambiguous types.

## Imports

- ALWAYS use `import` — never FQCN. Spotless enforces ordering.

## Network

- Wire IDs stable — NEVER renumber/insert.
- Party ops: append `ACTION_*` to `PartyAction` + factory + `case` in `dispatch()`. Notifications: append `KIND_*`/`EVENT_*` to `ClientNotify`.
- New IMessage: `common/network/` (no `@SideOnly` refs). Handler: `client/network/` with `@SideOnly(CLIENT)`.
- New S→C packet: append to `ModNetwork.CLIENT_BOUND_MESSAGES` AND `ClientPacketHandlers.installAll()` in identical order.
- NBT-payload S→C MUST extend `NbtMessage`.

## Side Boundary

- S→C handlers: `client/network/` with `@SideOnly(Side.CLIENT)`. `common/network/` MUST NOT reference `@SideOnly` types.

## Party

- Use player UUID to identify party (no `partyId` param except `acceptInvite`).
- Query methods: use `IPartyProvider` / `PartyQueryUtil` — e.g. `PartyQueryUtil.provider().getEffectiveParty(uuid)`, `PartyQueryUtil.resolveParty(player)`. NEVER `PartyManagerData.getInstance().getPartyByPlayer()` from outside internal packages.
- Mutation methods: use `PartyManagerData.addMember()`/`removeMember()`/`setRole()` — NEVER direct `Party.addMember()`/`Party.removeMember()` from outside `PartyManagerData`. This maintains the O(1) reverse index.
- Fail-soft: `dispatch()` rolls back via `syncToPlayer(actor)` on failure.

## GUI

- Open via `Screens` (`openMap()`, `openPartyDirect()`, `partyMain(...)`) — NEVER `ClientGUI.open(new …)` ad-hoc.
- Use `BLPCGuiTextures` drawables, `BLPCColors`/`GuiColors` — NEVER inline `0x…` colors (exception: per-party `getColor()` ARGB).
- Use `PartyWidgets` utilities/constants — NEVER hard-code dimensions.
- `MainPanel` uses `PartyMenuBuilder` fluent API.
- Live-update: read fresh `Party` via `livePartyRef` — NEVER hold captured `Party`.
- `PartyWidgets.collectSortedMembers(party, exclude, roleFilter)` — use `roleFilter` for panels that need to exclude certain roles (e.g. moderators exclude `PartyRole.OWNER`).

## Key Bindings

- Register in `KeyInputHandler.init()` (FMLInitializationEvent, NOT `preInit`).
- `KeyConflictContext.IN_GAME`, category `key.categories.blpc`.

## Logging

- `ModLog.*` categories: `ROOT`, `IO`, `PARTY`, `MODULE`, `SYNC`, `BQU`, `MIGRATION`, `UI`, `PROTECTION`.

## Build

- NEVER edit `build.gradle`. Config: `buildscript.properties`, deps: `dependencies.gradle`.
- `./gradlew spotlessApply` after editing Java, before committing.
