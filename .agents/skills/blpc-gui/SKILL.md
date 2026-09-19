---
name: blpc-gui
description: GUI/UI reference for BLPC — panel catalog, color conventions, widget patterns, client-side sync, commands.
user-invocable: false
---

# BLPC GUI / UI

## Party UI Panels

| Panel ID | File | Purpose |
|---|---|---|
| `blpc.party` | `MainPanel.java` | Party menu (`PartyMenuBuilder` fluent composition) |
| `blpc.party.create` | `CreatePanel.java` | Create-or-join (no party) |
| `blpc.party.settings` | `SettingsPanel.java` | Protection, ally/enemy (tabbed) |
| `blpc.party.members` | `MembersPanel.java` | Member list |
| `blpc.party.moderators` | `ModeratorsPanel.java` | Moderator promote/demote |
| `blpc.party.addons` | `AddonsPanel.java` | Addons hub — per-mod settings |
| `blpc.party.addons.journeymap` | `JMapSettingsPanel.java` | Opens JMap AddonOptionsManager |
| `blpc.party.addons.bqu` | `BQuSettingsPanel.java` | BQu link/unlink + native manager |
| `blpc.party.dialog.disband` | MainPanel (inline `ConfirmDialog`) | Disband confirmation |
| `blpc.party.dialog.transfer` | `TransferOwnerPanel.java` | Transfer ownership |

`MainPanel` pre-creates nav sub-panel handlers once per open, reuses across `rebuildMenu`. Handler closures re-read party from cache by UUID via `livePartyRef`.

## Color Conventions

Two holders, split by surface:

- **`BLPCColors`** — semantic party/map colors. Single source of truth via accessor methods. `text()`, `buttonText()`, `owner()`, `admin()`, `warning()`, `subtext()`, `inactive()`, `divider()`, `mapBackground()`, `mapBorder()`, `mapSelection()`, `mapUnloaded()`, claim overlays (`claimOwn`/`claimParty`/`claimOther`/`claimHatching`/`claimBorder`), `partyArgb()`.
- **`GuiColors`** — fixed vanilla-context ARGB: `WHITE`, `GOLD`, `GREEN`, `RED`, `GRAY`, `DIVIDER`.

**Never inline `0x…` color literals** — only exception: dynamic per-party `getColor()` ARGB composition.

## ModLog Categories

| Category | Logger | Purpose |
|---|---|---|
| `ModLog.ROOT` | `blpc` | General |
| `ModLog.IO` | `blpc/IO` | File I/O |
| `ModLog.PARTY` | `blpc/Party` | Party operations |
| `ModLog.MODULE` | `blpc/Module` | Module system |
| `ModLog.SYNC` | `blpc/Sync` | Client sync |
| `ModLog.BQU` | `blpc/BQu` | BQu integration |
| `ModLog.MIGRATION` | `blpc/Migration` | Data migration |
| `ModLog.UI` | `blpc/UI` | Panel navigation |
| `ModLog.PROTECTION` | `blpc/Protection` | Chunk protection |

## MUI Widget Patterns

| Widget | Usage | Notes |
|---|---|---|
| `CycleButtonWidget` + `IntValue.Dynamic` | Multi-state settings (trust levels) | `length()` + `stateChild(i, ...)` |
| `ToggleButton` + `BoolValue.Dynamic` | Boolean settings | `overlay(false/true, ...)` |
| `ListWidget` + `LiveSearchableList` | Scrollable lists | `rebuild(Collection<T>)` for live-update — used by `MembersPanel`, `ModeratorsPanel`, `TransferOwnerPanel`, and `SettingsPanel`'s ally/enemy tabs |
| `Dialog<T>` | Modal confirmations | `closeWith(result)` |
| `Flow.col()` / `Flow.row()` | Layout | `childPadding(n)` |
| `IKey.dynamic` / `*Value.Dynamic` | Per-frame reactive state | Preferred over widget tree rebuild |

**`PartyWidgets`** is the single styling/factory source:
- **Dimensions**: `BTN_H`, `TAB_H`, `FACE_SIZE`, `ROW_INDENT`, `STANDARD_W/H`, `LARGE_W/H`, `DIALOG_W/H`
- **Labels**: `buttonLabel`, `buttonLabelLeft`, `rowLabel`
- **Widgets**: `dialogButton`, `toggleButton`, `createPlayerRow`, `faceRow`, `divider()`
- **Live-update**: `addSyncRefreshListener`, `closeIfTopMost`, `livePartyRef`, `sendAndApply`
- **Member lists**: `collectSortedMembers`, `byRoleThenName()`

## Client-Side Sync Pattern

`ClientPartyCache.loadFromNBT()` replaces every `Party` instance — a captured reference goes stale immediately. Read fresh via `getParty(partyId)` or `livePartyRef`.

**Live-update is the default.** Panels stay mounted across syncs. Use `addSyncRefreshListener(panel, onSync)`. Callback deferred via `addScheduledTask`.

**`LiveSearchableList<T>`** — search box + list + parallel filter. `buildContainer()` + `rebuild(Collection<T>)`.

**Optimistic mutation:** `sendAndApply(IMessage, partyId, Consumer<Party>)` — send, apply to cache, fire listeners.

## Reusable Templates

- **`ConfirmDialog`** — Yes/No (`Dialog<Boolean>`), 220×70.
- **`InputDialog`** — Text field + submit (`Dialog<Void>`), 220×70.
- **`LiveSearchableList<T>`** — search + list + filter.
- **`PartyMenuBuilder`** — fluent builder: `.navHandler`, `.nav`, `.widget`, `.tooltip`, `.visible`, `.buildInto`.
- **`TransferOwnerPanel`** — OWNER-only member picker.

## Commands

`/blpc` root tree (permission 0), registered by `CoreModule.serverStarting()`.

**Player subcommands** (extend `PlayerCommand`, perm 0): `list`, `info`, `me`, `here`, `claims`, `invites`, `accept`, `decline`, `leave`, `admin`.

**Admin subcommands** (extend `AdminSubCommand`, perm 3): `move-owner`, `kick`, `disband`.

Query helpers: `PartyQueryUtil` (API), `BLPCCommandHelper` (internal — `activeProviderFor`, `requirePartyByName`, `resolveOwnerName`).
