---
name: blpc-overview
description: Detailed architecture reference for the BLPC project. Injected as shared knowledge into all QA team agents.
user-invocable: false
---

# BLPC Architecture Reference

Base package: `com.github.gtexpert.blpc`

## Module System

Annotation-driven module framework (same pattern as GTMoreTools/GTWoodProcessing/GTBeesMatrix):

- **`api/modules/`** — `IModule`, `TModule` (annotation), `IModuleContainer`, `ModuleContainer`, `ModuleStage`, `IModuleManager`.
- **`module/`** — `ModuleManager` (ASM scanning, dependency resolution, config-driven enable/disable), `Modules` (container + module ID constants), `BaseModule`.
- **`core/CoreModule`** — `@TModule(coreModule=true)`. Registers network packets, ForgeChunkManager callback, and default `PartyProviderRegistry`.
- **`integration/IntegrationModule`** — Parent gate for all integration submodules.
- **`integration/IntegrationSubmodule`** — Abstract base for mod-specific integrations.

Modules are discovered at FML Construction via `@TModule` annotation scanning. The `modDependencies` field gates loading on `Loader.isModLoaded()`. Module enable/disable config: `config/blpc/modules.cfg`.

## Party Provider SPI

Party management is abstracted via `IPartyProvider`, allowing transparent switching between self-managed parties and BQu's party system:

- **`api/party/IPartyProvider`** — Full interface with query methods (`areInSameParty`, `getPartyName`, `getPartyMembers`, `getRole`) and mutation methods (`createParty`, `disbandParty`, `renameParty`, `invitePlayer`, `acceptInvite`, `kickOrLeave`, `changeRole`, `syncToAll`). All mutation methods identify the party via the acting player's UUID — there is no `partyId` parameter on mutation calls.
- **`api/party/PartyProviderRegistry`** — Holds the active provider.
- **`common/party/DefaultPartyProvider`** — Self-managed implementation backed by `PartyManagerData`. Registered by `CoreModule`.
- **`integration/bqu/BQPartyProvider`** — BQu implementation that directly operates on BQu's `PartyManager`, `PartyInvitations`, and `NetPartySync`, with fallback to `DefaultPartyProvider` for players not in a BQu party. When BQu is present, this **replaces** the default provider — no data duplication.

**Design principle (Approach A):** When BQu is present, BLPC integrates INTO BQu's party system. BLPC's UI sends operations that `BQPartyProvider` translates into BQu API calls. BQu's quest sharing works unchanged.

## Naming Conventions

- **Panel IDs:** `blpc.map`, `blpc.party`, `blpc.map.dialog.confirm`, `blpc.party.dialog.invite`
- **Lang keys:** `blpc.map.*` for map screen, `blpc.party.*` for party screen
- **Mod ID constants:** `api/util/Mods.Names`

## Package Layout

- **`common/party/`** — Party data: `Party`, `PartyRole`, `PartyManagerData`, `DefaultPartyProvider`, `ClientPartyCache`.
- **`common/chunk/`** — Claim data: `ChunkManagerData`, `ClaimedChunkData`, `ClientCache`, `TicketManager`.
- **`common/network/`** — Messages: `MessageClaimChunk` (C→S), `MessagePartyAction` (C→S party ops), `MessageSyncClaims`/`MessageSyncAllClaims`/`MessageSyncConfig`/`MessagePartySync` (S→C), `PlayerLoginHandler`.
- **`client/gui/`** — ModularUI: `ChunkMapScreen`/`ChunkMapWidget`, party panels in `party/` subpackage (`MainPanel`, `CreatePanel`, `SettingsPanel`, etc.), `MinimapHUD`, `ModKeyBindings`/`KeyInputHandler`.
- **`client/map/`** — Async chunk rendering, texture caching, claim overlay.

## Data Persistence

BLPC uses **file-based persistence** (FTB Lib style) instead of `WorldSavedData`. All data is managed by `BLPCSaveHandler.INSTANCE` and stored under `world/betterlink/pc/`:

```
world/betterlink/pc/
├── config.dat          # migrated flag + bquLinkedPlayers set
├── parties/
│   ├── 0.dat           # one compressed NBT file per party (keyed by partyId)
│   └── ...
└── claims/
    ├── global.dat      # claims belonging to players with no party
    ├── 0.dat           # claims belonging to members of party 0
    └── ...
```

`BLPCSaveHandler.loadAll(server)` is called by `CoreModule.serverStarting()` (FMLServerStartingEvent). `saveAll()` is called by both `CoreEventHandler.onWorldSave()` (WorldEvent.Save) and `CoreModule.serverStopping()` (FMLServerStoppingEvent). Neither `ChunkManagerData` nor `PartyManagerData` is a `WorldSavedData` subclass — they are plain singletons reset via their `reset()` static methods.

Claims: `ClaimedChunkData` includes `partyName` resolved server-side via `PartyProviderRegistry`. NBT key `"party"` for party name.

Parties (self-managed mode only): `PartyManagerData`. Not used for storage when BQu is the active backend.

## Trust Level System

Trust levels control who can interact with claimed chunks. Each party configures the minimum trust level required per action.

**TrustLevel enum** (ascending privilege):

| Value | Description |
|---|---|
| `NONE` | Outsiders with no relationship to the party |
| `ALLY` | Explicitly added to the party's ally list |
| `MEMBER` | Regular party member |
| `MODERATOR` | Maps from `PartyRole.ADMIN` |
| `OWNER` | Party creator / current owner |

**TrustAction enum** (configurable per-party):

| Action | NBT Key | Forge Events |
|---|---|---|
| `BLOCK_EDIT` | `blockEdit` | `BreakEvent`, `EntityPlaceEvent`, `FarmlandTrampleEvent` |
| `BLOCK_INTERACT` | `blockInteract` | `RightClickBlock`, `EntityInteract`, `EntityInteractSpecific` |
| `ATTACK_ENTITY` | `attackEntity` | `AttackEntityEvent` |
| `USE_ITEM` | `useItem` | `RightClickItem` |

The Settings panel cycles each action through `NONE -> ALLY -> MEMBER`. Additional per-party settings: FakePlayer trust level (same cycle), explosion protection (boolean toggle), free-to-join (boolean toggle).

## Party UI Panels

| Panel ID | File | Purpose |
|---|---|---|
| `blpc.party` | `MainPanel.java` | Party menu |
| `blpc.party.create` | `CreatePanel.java` | Create party |
| `blpc.party.settings` | `SettingsPanel.java` | Protection settings, ally/enemy management |
| `blpc.party.members` | `MembersPanel.java` | Member list |
| `blpc.party.moderators` | `ModeratorsPanel.java` | Moderator promote/demote |
| `blpc.party.dialog.invite` | `InviteDialog.java` | Invite player |
| `blpc.party.dialog.disband` | `DisbandDialog.java` | Disband confirmation |
| `blpc.party.dialog.link_bqu` | `LinkBQuDialog.java` | Link BQu confirmation |
| `blpc.party.dialog.unlink_bqu` | `UnlinkBQuDialog.java` | Unlink BQu confirmation |
| `blpc.party.dialog.transfer` | `TransferOwnerDialog.java` | Transfer ownership |
| `blpc.party.dialog.rename` | SettingsPanel (InputDialog) | Rename party |
| `blpc.party.dialog.description` | SettingsPanel (InputDialog) | Edit party description |
| `blpc.party.dialog.add_ally` | SettingsPanel (Dialog) | Select ally party to add |
| `blpc.party.dialog.add_enemy` | SettingsPanel (Dialog) | Select enemy party to add |

## Color Conventions

All GUI colors are defined as ARGB constants in `client/gui/GuiColors`:

| Constant | Value | Matches | Usage |
|---|---|---|---|
| `WHITE` | `0xFFFFFFFF` | `TextFormatting.WHITE` (§f) | Titles, default text |
| `GOLD` | `0xFFFFAA00` | `TextFormatting.GOLD` (§6) | OWNER role, section headers |
| `GREEN` | `0xFF55FF55` | `TextFormatting.GREEN` (§a) | ADMIN role, active items |
| `RED` | `0xFFFF5555` | `TextFormatting.RED` (§c) | Warnings, limit exceeded |
| `GRAY` | `0xFFAAAAAA` | `TextFormatting.GRAY` (§7) | Sub-text, messages |
| `GRAY_LIGHT` | `0xFFCCCCCC` | — | Inactive items, non-members |

`GuiColors` is at the `client/gui` package level — shared by all GUI components (party panels, chunk map, minimap). Party-specific role color logic is in `PartyWidgets.getRoleColor(PartyRole)`.

For Minecraft formatting codes in tooltip strings, use `TextFormatting` enum constants (e.g. `TextFormatting.GREEN + "text"`) instead of raw `\u00a7X` escape sequences.

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

## BQu Link/Unlink/Disband Flow

**Link** (`MessagePartyAction.toggleBQuLink(true)`):
1. Server receives action, verifies player is ADMIN+ in their BLPC party.
2. Adds player UUID to `PartyManagerData.bquLinkedPlayers` set.
3. `BLPCSaveHandler` persists the updated set to `config.dat`.
4. `syncToAll()` broadcasts updated party state (including link flags) to all clients.
5. Client-side `MainPanel` switches to BQu-linked UI (member list + "Open BQu Party Screen" button).

**Unlink** (`MessagePartyAction.toggleBQuLink(false)`):
1. Server receives action, verifies player is ADMIN+.
2. Removes player UUID from `bquLinkedPlayers` set.
3. Persists and syncs as above.
4. Client-side UI reverts to full self-managed mode (Invite / Leave / Disband).

**Disband** (`MessagePartyAction.disband()`):
1. Server receives action, verifies player is OWNER.
2. Removes the party from `PartyManagerData`, clears BQu link flag for all members.
3. Releases all chunk claims associated with the party.
4. Persists and syncs.
5. Client immediately clears `ClientPartyCache` and resets BQu link flag for instant feedback.

## MUI Widget Patterns

| Widget | Usage | Notes |
|---|---|---|
| `CycleButtonWidget` + `IntValue.Dynamic` + `IKey.dynamic()` | Multi-state settings (trust levels) | `stateCount()` sets number of states; overlay label updates dynamically |
| `ToggleButton` + `BoolValue.Dynamic` | Boolean settings (explosions, free-to-join) | `overlay(false, ...)` / `overlay(true, ...)` for state-dependent labels |
| `ListWidget` | Scrollable lists (members, allies, enemies) | `children(iterable, mapper)` for data-driven population |
| `Dialog<T>` | Modal confirmations (disband, link/unlink BQu) | `closeWith(result)` triggers the result consumer and closes; extends `ModularPanel` |
| `Flow.col()` / `Flow.row()` | Automatic vertical/horizontal layout | `childPadding(n)` for spacing; eliminates manual `y += ROW_H` positioning |

For ModularUI API details, consult the ModularUI source code or documentation.

## UI Reusable Templates

All party panels use builder-pattern templates in `client/gui/party/widget/`:

- **`ConfirmDialog`** — Yes/No confirmation dialog (`Dialog<Boolean>`). Used by: DisbandDialog, LinkBQuDialog, UnlinkBQuDialog.
- **`InputDialog`** — Text field + submit dialog (`Dialog<Void>`). Used by: InviteDialog, TransferOwnerDialog, SettingsPanel (rename/description).
- **`PlayerListPanel`** — Scrollable player list with optional Add/Remove (`ModularPanel`). Used by: MembersPanel, ModeratorsPanel.

**Allies/Enemies Management**: Handled directly in SettingsPanel via inline ListWidget children (not via PlayerListPanel). Each section displays removable entries with a single "Add" button that opens a party selection dialog.

Shared color constants in `client/gui/GuiColors`:
- `WHITE`, `GOLD`, `GREEN`, `RED`, `GRAY`, `GRAY_LIGHT` — ARGB constants matching Minecraft TextFormatting palette

Shared utilities in `client/gui/party/PartyWidgets`:
- `getDisplayName(UUID)` — resolve player UUID to display name
- `getRoleColor(PartyRole)` — ARGB color for party role (OWNER=gold, ADMIN=green, default=white)
- `openSubPanel(parent, child)` — open a sub-panel exclusively
- `reopenPanel(current, factory)` — close and reopen a panel (for list refresh)
- `createActionButton(label, name, action)` — button with click handler

## Commands

`/blpc move-owner <partyId> <newOwner>` — Op-only (permission level 3) command to transfer party ownership. Registered in `CoreModule` via `FMLServerStartingEvent`. Lang key: `command.blpc.move_owner.success`.

## Mixins

Uses MixinBooter (`ILateMixinLoader`) for conditional late-stage injection:

- **`BLPCMixinLoader`** — Loads mixin configs conditionally based on mod presence.
- **`PartyManagerMixin`** — Injects into BQu's `NetPartyAction.deleteParty()` to auto-unlink all affected players from BQu in BLPC's `PartyManagerData`. Prevents orphaned BQu links.

Config: `src/main/resources/mixins.blpc.betterquesting.json`.

## Server Configuration (ModConfig)

Forge `@Config` at `common/ModConfig.java`. Auto-syncs when changed in-game.

| Option | Type | Default | Description |
|---|---|---|---|
| `maxClaimsPerPlayer` | int (0–1000) | 64 | Max chunks claimable per player |
| `maxForceLoadsPerPlayer` | int (0–100) | 8 | Max force-loaded chunks per player |
| `showMinimap` | boolean | true | Toggle minimap HUD |
| `enableProtection` | boolean | true | Master protection toggle |
| `protectMobGriefing` | boolean | true | Prevent mob griefing in claims |
| `protectFireSpread` | boolean | true | Prevent fire spread in claims |
| `protectFluidFlow` | boolean | true | Prevent fluid flow into claims |

## BQu Migration

`BQMigrationHelper` runs once on first server start with BQu present. Iterates all BQu parties, creates corresponding BLPC `Party` objects with role mapping (`BQPartyProvider.mapRole()`), and sets a migration flag in `config.dat` to prevent re-running.

## Localization

Lang files in `src/main/resources/assets/blpc/lang/`: `en_us.lang` and `ja_jp.lang`. Both cover keybindings, commands, map UI, party UI, roles, trust actions/levels, protection settings, allies/enemies, tooltips, and search.

## Adding a New Integration Module

1. Create `integration/<modid>/` package.
2. Create a module class extending `IntegrationSubmodule` with `@TModule(modDependencies=Mods.Names.THE_MOD)`.
3. Add module ID constant to `Modules.java`.
4. Add mod ID to `Mods` enum and `Mods.Names`.
