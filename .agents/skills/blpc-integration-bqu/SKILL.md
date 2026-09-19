---
name: blpc-integration-bqu
description: BetterQuesting integration reference for BLPC — BQuPartyProvider, link/unlink flow, Mixin, settings panel.
user-invocable: false
---

# BLPC BetterQuesting Integration

Optional integration loaded by `BQuModule` (`@TModule(modDependencies = "betterquesting")`). ALL BQu API calls MUST go through `integration/bqu/` — never call BQu API from outside.

## BQuPartyProvider

Registered at `PRIORITY_HIGH` when BQu is present, replacing `DefaultPartyProvider`. Directly operates on BQu's `PartyManager`, `PartyInvitations`, and `NetPartySync`, with fallback to `DefaultPartyProvider` for players not in a BQu party.

**Design principle (Approach A):** BLPC integrates INTO BQu's party system. BLPC's UI sends operations that `BQuPartyProvider` translates into BQu API calls. BQu's quest sharing works unchanged.

### Key Methods

- **`isLinkedParty(UUID)`** — checks the player's current BQu party for any member with the link flag set (recognizes new joiners immediately, unlike the stale per-player flag). Used by `PartyAction.Handler.dispatch()`, `WaypointAction.Handler`, and `BLPCCommandHelper.activeProviderFor`.
- **`getEffectiveParty(UUID)`** — builds a merged `Party` via `buildMergedParty(DBEntry<IParty>)` (live BQu membership + BLPC-side settings from whichever member has a record, preferring owner's). Same helper backs `serializeForClient()`.
- **`getPartyId(UUID)`** — derives from BQu's integer party id via `Party.uuidFromIntId(...)`, identical for every member even without BLPC-side records.
- Party **settings mutations** (`ACTION_SET_TRUST_LEVEL`, `ACTION_SET_COLOR`, ally/enemy, etc.) intentionally read/write `PartyManagerData` directly — BLPC-only concepts with no BQu equivalent.

## Link/Unlink Flow

Toggled via `ToggleButton` in `MainPanel` with `BoolValue.Dynamic`:

1. Client: `PartyWidgets.setLocalBQuLinked()` + `fireSyncListeners()` (optimistic UI).
2. Client sends `PartyAction.toggleBQuLink()`.
3. Server verifies ADMIN+ and has a BQu party. If rejected, `syncToAll()` still called for rollback.
4. On success, updates `PartyManagerData.bquLinkedPlayers` and persists via `BLPCSaveHandler`.
5. `syncToAll()` broadcasts. Panels stay mounted and rebuild (live-update).

## Disband (BQu-linked)

`PartyAction.disband()`:
1. Server verifies OWNER (checks both BLPC and BQu roles when linked).
2. Releases claims, removes party from `PartyManagerData`, clears BQu link flags.
3. Persists and syncs. Actor excluded from `DISBANDED` toast.
4. Client: `panel.closeIfOpen()` + `PartyWidgets.clearLocalPartyData()`.

Disbanding does NOT touch the BQu party — manage BQu's party through BetterQuesting's own screen.

## NetPartyActionMixin

`mixins.blpc.betterquesting.json` — injects into BQu's `NetPartyAction.deleteParty()` to auto-unlink all affected players from BQu in BLPC's `PartyManagerData`. Prevents orphaned BQu links when a party is disbanded through BQu's own UI.

## BQuSettingsPanel

`integration/bqu/BQuSettingsPanel.java` — BQu link/unlink toggle + native party manager shortcut. Registered in Addons hub via `IntegrationPanelRegistry.register(...)` when `PartyProviderRegistry.hasNativeScreen()`.

## Persistence

`config.dat` in `world/betterlink/pc/` stores `bquLinkedPlayers` set (+ legacy migrated flag). Party data itself lives in BQu when linked — `PartyManagerData` only stores BLPC-specific settings (trust, color, allies/enemies).
