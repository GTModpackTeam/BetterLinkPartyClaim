All Java code in this project MUST follow these conventions. Violations are build-breaking or merge-blocking.

## Java 25 Syntax (Mandatory)

- ALWAYS use arrow-form switch expressions (`case X -> { ... }`) — never colon+break.
- ALWAYS use pattern matching instanceof (`if (obj instanceof MyClass mc)`) — never separate cast.
- Use `var` for local variables where type is obvious from context. Do NOT use `var` for primitives, ambiguous types, or fields.
- Combine related cases with multi-label (`case A, B, C -> { ... }`).

## Imports

- ALWAYS use `import` statements — never FQCN inline references. Spotless enforces ordering.

## Network Messages

- Wire protocol IDs are stable — NEVER renumber or insert.
- New party operations: append `ACTION_*` to `PartyAction` + factory method + `case` in `PartyAction.Handler.dispatch()` + private method. Do NOT add a new top-level wire ID.
- New client notifications: append `KIND_*`/`EVENT_*` to `ClientNotify` + `toBytes`/`fromBytes` arm + `BLPCToast` case.
- New top-level packet (rare): IMessage in `common/network/`, handler in `client/network/<Name>ClientHandler.java` with `@SideOnly(Side.CLIENT)`. Append to BOTH `ModNetwork.CLIENT_BOUND_MESSAGES` AND `ClientPacketHandlers.installAll()` in identical order.
- NBT-payload S→C messages MUST extend `NbtMessage`.

## Side Boundary

- Server-side handlers: `common/network/`.
- Client-side (S→C) handlers: `client/network/` with `@SideOnly(Side.CLIENT)`.
- IMessage classes in `common/network/` MUST NOT reference any `@SideOnly` types in their bytecode.

## Party Mutations

- ALWAYS use player UUID to identify the acting party — no partyId parameter (except `acceptInvite`).
- Dispatcher actions fail-soft: on `false`, `dispatch()` rolls back via `syncToPlayer(actor)`.

## GUI

- Open screens through `client/gui/Screens` (`openMap()`, `openPartyDirect()`, `partyMain(...)`) — NEVER `ClientGUI.open(new …)` ad-hoc.
- Reuse shared drawables from `client/gui/BLPCGuiTextures`.
- NEVER inline `0x…` color literals — use `BLPCColors` (semantic) or `GuiColors` (vanilla-context). Only exception: dynamic per-party `getColor()` ARGB composition.
- Use `PartyWidgets` utility methods and size constants — NEVER hard-code button heights, face sizes, or row indents.
- Use `PartyMenuBuilder` fluent API for `MainPanel` menu entries (`.navHandler`/`.visible` chain, not `if` blocks).
- Live-update panels: read fresh `Party` via `livePartyRef` / `getParty(partyId)` — NEVER hold a captured `Party` across syncs.

## Key Bindings

- Register in `KeyInputHandler.init()` (called from `init`, FMLInitializationEvent — NOT `preInit`).
- Use `KeyConflictContext.IN_GAME` and the shared `key.categories.blpc` category.

## Logging

- Use `ModLog.*` categories (`ROOT`, `IO`, `PARTY`, `MODULE`, `SYNC`, `BQU`, `MIGRATION`, `UI`, `PROTECTION`).

## Build

- NEVER edit `build.gradle` (auto-generated). Config: `buildscript.properties`, deps: `dependencies.gradle`.
- Run `./gradlew spotlessApply` after editing Java files, before committing.
