---
name: blpc-gui
description: GUI/UI reference for BLPC — panel catalog, Screens constants, color conventions, widgets, sync patterns, commands.
user-invocable: false
---

# BLPC GUI / UI

## Party UI Panels

| Panel ID | File | Purpose |
|---|---|---|
| `blpc.party` | `MainPanel.java` | Party menu (`PartyMenuBuilder` fluent) |
| `blpc.party.create` | `CreatePanel.java` | Create-or-join (no party) |
| `blpc.party.settings` | `SettingsPanel.java` | Protection, ally/enemy (tabbed) |
| `blpc.party.members` | `MembersPanel.java` | Member list |
| `blpc.party.moderators` | `ModeratorsPanel.java` | Moderator promote/demote |
| `blpc.party.addons` | `AddonsPanel.java` | Addons hub — per-mod settings |
| `blpc.party.addons.bqu` | `BQuSettingsPanel.java` | BQu link/unlink + native manager |
| `blpc.party.addons.journeymap` | `JMapSettingsPanel.java` | Opens JMap AddonOptionsManager |
| `blpc.party.dialog.disband` | MainPanel (inline `ConfirmDialog`) | Disband confirmation |
| `blpc.party.dialog.transfer` | `TransferOwnerPanel.java` | Transfer ownership |

`MainPanel` pre-creates sub-panel handlers once per open. Handler closures re-read party via `livePartyRef`.

## Screens Constants

| Constant | Value | Source |
|---|---|---|
| `MAP` | `blpc.map` | `ChunkMapScreen` |
| `PARTY` | `blpc.party` | `MainPanel` |
| `PARTY_CREATE` | `blpc.party.create` | `CreatePanel` |
| `PARTY_SETTINGS` | `blpc.party.settings` | `SettingsPanel` |
| `PARTY_MEMBERS` | `blpc.party.members` | `MembersPanel` |
| `PARTY_MODERATORS` | `blpc.party.moderators` | `ModeratorsPanel` |
| `PARTY_TRANSFER` | `blpc.party.transfer` | `TransferOwnerPanel` |
| `PARTY_ADDONS` | `blpc.party.addons` | `AddonsPanel` |

Open via: `Screens.openMap()`, `Screens.openPartyDirect()`, `Screens.partyMain(...)`.

## Color & Styling

- **`BLPCColors`** — semantic colors (accessor methods). Single source of truth.
- **`GuiColors`** — fixed vanilla-context ARGB (`WHITE`, `GOLD`, `GREEN`, `RED`, `GRAY`, `DIVIDER`).
- NEVER inline `0x…` colors. Exception: per-party `getColor()` ARGB composition.
- Use `PartyWidgets` utilities/constants — NEVER hard-code dimensions.

## MUI Widget Patterns

- `CycleButtonWidget` + `IntValue.Dynamic` — multi-state (trust levels)
- `ToggleButton` + `BoolValue.Dynamic` — boolean settings
- `ListWidget` + `LiveSearchableList` — scrollable lists with live-update
- `Dialog<T>` — modal confirmations
- `Flow.col()` / `Flow.row()` — layout

## Sync Pattern

`ClientPartyCache.loadFromNBT()` replaces every `Party` — captured references go stale. Read via `getParty()` or `livePartyRef`.

Live-update default: `addSyncRefreshListener(panel, onSync)`.
Optimistic mutation: `sendAndApply(IMessage, partyId, Consumer<Party>)`.

## Reusable Templates

- `ConfirmDialog` (Yes/No), `InputDialog` (text + submit), `LiveSearchableList<T>`
- `PartyMenuBuilder` — fluent: `.navHandler`, `.nav`, `.widget`, `.tooltip`, `.visible`

## Commands

`/blpc` root (perm 0): `list`, `info`, `me`, `here`, `claims`, `invites`, `accept`, `decline`, `leave`, `admin`.
Admin (perm 3): `move-owner`, `kick`, `disband`.
