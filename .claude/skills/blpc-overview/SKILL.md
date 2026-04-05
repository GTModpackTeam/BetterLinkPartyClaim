---
name: blpc-overview
description: Detailed architecture reference for the BLPC project. Injected as shared knowledge into all QA team agents.
user-invocable: false
---

# BLPC Architecture Reference

Base package: `com.github.gtexpert.blpc`.

## Build System

RetroFuturaGradle (RFG) with GTNH Buildscripts. **Do not edit `build.gradle`** (auto-generated). Mod-specific config: `buildscript.properties`. Dependencies: `dependencies.gradle`. Debug flags: `debug_bqu`, `debug_jmap`, `debug_all` in `buildscript.properties`. Spotless enforced (formatting: `spotless.importorder` local + `spotless.eclipseformat.xml` via Blowdryer).

| Dependency | Role | Required? |
|---|---|---|
| ModularUI | GUI framework | Yes |
| BetterQuesting Unofficial | Party system backend (when present) | Optional (module) |
| JourneyMap API | Overlay integration | Optional |

## Java 17 Syntax (Mandatory)

Jabel (`enableModernJavaSyntax = true`) compiles Java 17 features to JVM 8 bytecode. **目的:** NPE削減（pattern matching で安全なキャスト）とコード量削減（switch expressions で冗長な break/cast を排除）。

| Feature | Requirement | Example |
|---|---|---|
| **Switch expressions** | Always use arrow form (`->`) instead of colon+break | `case X -> { ... }` or `var x = switch(v) { case A -> 1; };` |
| **Pattern matching instanceof** | Always use instead of separate cast | `if (obj instanceof MyClass mc)` not `if (obj instanceof MyClass) { MyClass mc = (MyClass) obj; }` |
| **`var`** | Use for local variables where type is obvious from context | `var entry : map.entrySet()`, `var list = new ArrayList<>(...)` |
| **Multi-label case** | Combine related cases | `case A, B, C -> { ... }` |

Do NOT use `var` for: primitives, ambiguous types (e.g. `Collections.emptyMap()`), or fields.

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

- **`api/party/IPartyProvider`** — Full interface with query methods (`areInSameParty`, `getPartyName`, `getPartyMembers`, `getRole`) and mutation methods (`createParty`, `disbandParty`, `renameParty`, `invitePlayer`, `acceptInvite`, `kickOrLeave`, `changeRole`, `syncToAll`). Most mutation methods identify the party via the acting player's UUID. Exception: `acceptInvite(player, partyId)` requires an explicit partyId since it targets a different party.
- **`api/party/PartyProviderRegistry`** — Holds the active provider.
- **`common/party/DefaultPartyProvider`** — Self-managed implementation backed by `PartyManagerData`. Registered by `CoreModule`.
- **`integration/bqu/BQPartyProvider`** — BQu implementation that directly operates on BQu's `PartyManager`, `PartyInvitations`, and `NetPartySync`, with fallback to `DefaultPartyProvider` for players not in a BQu party. When BQu is present, this **replaces** the default provider — no data duplication.

**Design principle (Approach A):** When BQu is present, BLPC integrates INTO BQu's party system. BLPC's UI sends operations that `BQPartyProvider` translates into BQu API calls. BQu's quest sharing works unchanged.

## Naming Conventions

- **Panel IDs:** `blpc.map`, `blpc.party`, `blpc.map.dialog.confirm`, `blpc.party.dialog.invite`
- **Lang keys:** `blpc.map.*` for map screen, `blpc.party.*` for party screen
- **Mod ID constants:** `api/util/Mods.Names`

## Package Layout

- **`common/party/`** — Party data: `Party`, `PartyRole`, `RelationType`, `PartyManagerData`, `DefaultPartyProvider`, `ClientPartyCache`.
- **`common/chunk/`** — Claim data: `ChunkManagerData`, `ClaimedChunkData`, `ClientCache`, `TicketManager`.
- **`common/network/`** — Messages: `MessageClaimChunk` (C→S), `MessagePartyAction` (C→S party ops), `MessageSyncClaims`/`MessageSyncAllClaims`/`MessageSyncConfig`/`MessagePartySync`/`MessageChunkTransitNotify`/`MessagePartyEventNotify`/`MessageClaimFailed` (S→C), `PlayerLoginHandler`.
- **`client/gui/`** — ModularUI: `ChunkMapScreen`/`ChunkMapWidget`, party panels in `party/` subpackage, standalone widgets in `widget/` subpackage (`BLPCToast`), `MinimapHUD`, `ModKeyBindings`/`KeyInputHandler`.
- **`client/map/`** — Async chunk rendering, texture caching, claim overlay.

## Data Persistence

BLPC uses **file-based persistence** (FTB Lib style) instead of `WorldSavedData`. All data is managed by `BLPCSaveHandler.INSTANCE` and stored under `world/betterlink/pc/`:

```
world/betterlink/pc/
├── config.dat          # bquLinkedPlayers set (+ legacy migrated flag)
├── backup/
│   ├── parties/        # most recent backup of parties/
│   └── claims/         # most recent backup of claims/
├── parties/
│   ├── 0.dat           # one compressed NBT file per party (keyed by partyId)
│   └── ...
└── claims/
    ├── global.dat      # claims belonging to players with no party
    ├── 0.dat           # claims belonging to members of party 0
    └── ...
```

`BLPCSaveHandler.loadAll(server)` is called by `CoreModule.serverStarting()` (FMLServerStartingEvent). `saveAll()` is called by both `CoreEventHandler.onWorldSave()` (WorldEvent.Save) and `CoreModule.serverStopping()` (FMLServerStoppingEvent). Neither `ChunkManagerData` nor `PartyManagerData` is a `WorldSavedData` subclass — they are plain singletons reset via their `reset()` static methods. `BLPCSaveHandler` uses atomic write (`writeCompressedAtomic`) and backup-swap (`backupAndSwap`) for crash-safe persistence.

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

**Link/Unlink** — toggled via `ToggleButton` in `MainPanel` with `BoolValue.Dynamic`:
1. Client calls `PartyWidgets.setLocalBQuLinked()` for optimistic UI update + `fireSyncListeners()` for instant MainPanel rebuild.
2. Client sends `MessagePartyAction.toggleBQuLink()` to server.
3. Server verifies player is ADMIN+ and has a BQu party (for link). If rejected, `syncToAll()` is still called to roll back the optimistic update.
4. On success, updates `PartyManagerData.bquLinkedPlayers` and persists via `BLPCSaveHandler`.
5. `syncToAll()` broadcasts to all clients. `MainPanel` rebuilds via `addAutoRefreshListener`.

**Disband** (`MessagePartyAction.disband()`):
1. Server verifies player is OWNER (checks both BLPC and BQu roles when BQu-linked).
2. Releases all chunk claims, removes party from `PartyManagerData`, clears BQu link flags.
3. Persists and syncs.
4. Client calls `PartyWidgets.clearLocalPartyData()` + `displayGuiScreen(null)` to close entire GUI immediately.

## MUI Widget Patterns

| Widget | Usage | Notes |
|---|---|---|
| `CycleButtonWidget` + `IntValue.Dynamic` + `IKey.dynamic()` | Multi-state settings (trust levels) | `stateCount()` sets number of states; overlay label updates dynamically |
| `ToggleButton` + `BoolValue.Dynamic` | Boolean settings (explosions, free-to-join) | `overlay(false, ...)` / `overlay(true, ...)` for state-dependent labels |
| `ListWidget` | Scrollable lists (members, allies, enemies) | `children(iterable, mapper)` for data-driven population. Children should use `.widthRel(1f).height(h)` or `.height(h)` only — avoid fixed-pixel `.size(w, h)` as the ListWidget's `.left(n).right(n)` auto-stretches children. |
| `Dialog<T>` | Modal confirmations (disband, map bulk actions) | `closeWith(result)` triggers the result consumer and closes; extends `ModularPanel` |
| `Flow.col()` / `Flow.row()` | Automatic vertical/horizontal layout | `childPadding(n)` for spacing; eliminates manual `y += ROW_H` positioning |

For ModularUI API details, consult the ModularUI source code at `/mnt/data/git/ModularUI`. Text input fields use `setMaxLength(32)` for user-facing name inputs (party name, player name).

## Client-Side Sync Pattern

Party panels receive real-time updates via `ClientPartyCache.loadFromNBT()` (triggered by `MessagePartySync` from server). Listeners are fired **immediately** when new data arrives — no tick-based coalescing.

`ClientPartyCache.fireSyncListeners()` can also be called directly for optimistic UI updates (e.g., after `PartyWidgets.setLocalBQuLinked()` or `clearLocalPartyData()`).

**Registration pattern** — use convenience helpers in `PartyWidgets`:

```java
// For panels that display a specific party:
PartyWidgets.addPartyRefreshListener(panel, party.getPartyId(), PanelName::build);

// For panels that don't track a specific party:
PartyWidgets.addAutoRefreshListener(panel, () -> PanelName.build(args));
```

**Panels with sync listeners:**

| Panel | Helper | Behavior on sync |
|---|---|---|
| `MainPanel` | `addAutoRefreshListener` | Rebuild with playerId |
| `SettingsPanel` | `addPartyRefreshListener` | Rebuild with refreshed party; close if disbanded |
| `MembersPanel` | `addPartyRefreshListener` | Rebuild with refreshed party; close if disbanded |
| `ModeratorsPanel` | `addPartyRefreshListener` | Rebuild with refreshed party; close if disbanded |
| `CreatePanel` | `addAutoRefreshListener` | Rebuild (refresh available parties list) |
| `TransferOwnerDialog` | manual `addSyncListener` | Close dialog |

**Panels without sync listeners**: `InviteDialog`, `DisbandDialog` — simple input/confirm dialogs

## UI Reusable Templates

### Dialog/Panel Templates (`client/gui/party/widget/`)

- **`ConfirmDialog`** — Yes/No confirmation dialog (`Dialog<Boolean>`). Default size: 220x70. Used by: DisbandDialog, ChunkMapScreen.
- **`InputDialog`** — Text field + submit dialog (`Dialog<Void>`). Default size: 220x70. Used by: InviteDialog, SettingsPanel (rename/description).
- **`PlayerListPanel`** — Reusable scrollable player list with Add/Remove (`ModularPanel`). Available for future use.
- **`PartySelectDialog`** — Party selection dialog (`Dialog<Void>`). Used by: SettingsPanel (add ally/enemy).

All dialog templates use a consistent width of 220px. Custom sizing available via `.size(w, h)`.

### Panel Infrastructure (`client/gui/party/`)

- **`PanelSizes`** — Shared size constants: `STANDARD_W/H` (220x180), `LARGE_W/H` (260x220), `DIALOG_W/H` (220x70), `SELECT_H` (120), `BTN_H` (18).
- **`PanelBuilder`** — Common layout helpers:
  - `addHeader(panel, titleKey)` — centered title (WHITE, shadow) + close button
  - `addHeader(panel, IKey)` — IKey variant for dynamic titles
  - `addList(panel, list)` — positions list widget (top=22, padded)

**Allies/Enemies Management**: Handled directly in SettingsPanel via inline ListWidget. Uses `PartySelectDialog` for adding allies/enemies.

### Shared Utilities

Shared color constants in `client/gui/GuiColors`:
- `WHITE`, `GOLD`, `GREEN`, `RED`, `GRAY`, `GRAY_LIGHT` — ARGB constants matching Minecraft TextFormatting palette

Shared utilities in `client/gui/party/PartyWidgets`:
- `getDisplayName(UUID)` — resolve player UUID to display name
- `getRoleColor(PartyRole)` — ARGB color for party role (OWNER=gold, ADMIN=green, default=white)
- `openSubPanel(ModularPanel parent, ModularPanel child)` — open a sub-panel exclusively
- `reopenPanel(ModularPanel current, Supplier<ModularPanel> factory)` — close and reopen a panel
- `addAutoRefreshListener(ModularPanel panel, Supplier<ModularPanel> rebuilder)` — register sync listener with auto-cleanup on close
- `addPartyRefreshListener(ModularPanel panel, UUID partyId, Function<Party, ModularPanel> rebuilder)` — partyId lookup + null-safe rebuild
- `setLocalBQuLinked(boolean linked)` — optimistic BQu link flag update for current player
- `clearLocalPartyData()` — optimistic cache clear after disband
- `createActionButton(IKey label, String actionName, Runnable action)` — button with click handler
- `createEnterSubmitTextField(Runnable onSubmit)` — text field that submits on Enter key press
- `resetSubPanelHandler()` — clear cached handler state when parent screen closes

## Commands

`/blpc move-owner <partyId> <newOwner>` — Op-only (permission level 3) command to transfer party ownership. Registered in `CoreModule` via `FMLServerStartingEvent`. Lang key: `command.blpc.move_owner.success`.

## Mixins

Uses MixinBooter (`ILateMixinLoader`) for conditional late-stage injection:

- **`BLPCMixinLoader`** — Loads mixin configs conditionally based on mod presence.
- **`PartyManagerMixin`** — Injects into BQu's `NetPartyAction.deleteParty()` to auto-unlink all affected players from BQu in BLPC's `PartyManagerData`. Prevents orphaned BQu links.

Config: `src/main/resources/mixins.blpc.betterquesting.json`.

## Server Configuration (ModConfig)

Forge `@Config` at `common/ModConfig.java`. Auto-syncs when changed in-game.

### Configurable (exposed in cfg file)

Uses nested subcategories via `@Config.LangKey` (`config.blpc.<category>`). Access pattern: `ModConfig.claims.maxClaimsPerPlayer`.

**Claims** (`ModConfig.claims`)

| Option | Type | Default | Description |
|---|---|---|---|
| `maxClaimsPerPlayer` | int (0–10000) | 1000 | Max chunks claimable per player |
| `maxForceLoadsPerPlayer` | int (0–10000) | 64 | Max force-loaded chunks per player |

**Party** (`ModConfig.party`)

| Option | Type | Default | Description |
|---|---|---|---|
| `autoCreatePartySingleplayer` | boolean | false | Auto-create party in singleplayer |

**Server Party** (`ModConfig.serverParty`)

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | false | Automatically create a shared party on server start |
| `name` | String | "Server" | Name for the auto-created server party |
| `freeToJoin` | boolean | true | Enable free-to-join on the server party |
| `owner` | String | "" | Player name who owns the server party; empty = server-owned |
| `moderators` | String[] | [] | Player names to assign as moderators (ADMIN role) |

**Data** (`ModConfig.data`)

| Option | Type | Default | Description |
|---|---|---|---|
| `mergeOfflineOnlineData` | boolean | true | Merge offline/online chunk data  |

### Internal defaults (`ModDefaults.java` — not in cfg)

| Constant | Value | Description |
|---|---|---|
| `showMinimap` | true | Minimap HUD default visibility (toggled at runtime via keybind) |
| `enableProtection` | true | Master protection toggle |
| `protectMobGriefing` | true | Prevent mob griefing in claims |
| `protectFireSpread` | true | Prevent fire spread in claims |
| `protectFluidFlow` | true | Prevent fluid flow into claims |
| `enableTransitNotify` | true | Toast notifications for chunk entry/exit |
| `transitToastDuration` | 3000 | Toast display duration (ms) |
| `enableAreaEffects` | true | Potion effects for enemies/defenders |
| `enemyWeaknessAmplifier` | 0 | Weakness amplifier (0 = level I) |
| `enemyMiningFatigue` | true | Mining fatigue for enemies |
| `defenderResistanceAmplifier` | 0 | Resistance amplifier (0 = level I) |

## Chunk Transit System

Players receive **toast notifications** when entering/leaving claimed chunks, and **potion effects** are applied based on relationship.

### Classes

- **`common/party/RelationType`** — Enum: `MEMBER`, `ALLY`, `ENEMY`, `NONE`.
- **`core/ChunkTransitHandler`** — `PlayerTickEvent.END` listener. Detects chunk boundary crossings (overworld only), sends notifications via `MessageChunkTransitNotify`, and applies area effects.
- **`common/network/MessageChunkTransitNotify`** — S→C packet. Serializes relation as `name()` string (not ordinal) for forward compatibility.
- **`client/gui/widget/BLPCToast`** — `IToast` implementation with Builder pattern. Factory methods: `fromTransit()` (chunk entry/exit), `fromPartyEvent()` (party events), `fromClaimFailed()` (claim limit errors).
- **`common/network/MessagePartyEventNotify`** — S→C packet for party events (join, leave, kick, disband, invite, transfer, role change, BQu link/unlink).
- **`common/network/MessageClaimFailed`** — S→C packet for claim/force-load limit errors.

### Notification Messages

| Relation | Enter | Leave |
|----------|-------|-------|
| MEMBER | `blpc.transit.member.enter` — "%s returned home" | `blpc.transit.member.leave` — "%s went exploring" |
| ALLY | `blpc.transit.ally.enter` — "%s came to visit" | `blpc.transit.ally.leave` — "%s went home" |
| ENEMY | `blpc.transit.enemy.enter` — "Invaded by %s" | `blpc.transit.enemy.leave` — "%s fled" |

Notifications are sent to all online party members of the claim owner. Enemies also receive their own notification.

### Area Effects

Applied every 20 ticks while player is in a claimed chunk:

- **Enemy debuff**: Weakness + optional Mining Fatigue. Removed immediately on leaving.
- **Defender buff**: Resistance + Strength. Only active while enemies are invading the party's territory. Expires naturally when all enemies leave.

`activeInvasions` map tracks which parties have enemy invaders. Cleaned up on player logout and enemy departure.

## Localization

Lang files in `src/main/resources/assets/blpc/lang/`: `en_us.lang` and `ja_jp.lang`. Both cover keybindings, commands, map UI, party UI, roles, trust actions/levels, protection settings, allies/enemies, tooltips, search, transit notifications (`blpc.transit.*`), and party event/claim failure notifications (`blpc.toast.*`).

## Adding a New Integration Module

1. Create `integration/<modid>/` package.
2. Create a module class extending `IntegrationSubmodule` with `@TModule(modDependencies=Mods.Names.THE_MOD)`.
3. Add module ID constant to `Modules.java`.
4. Add mod ID to `Mods` enum and `Mods.Names`.
