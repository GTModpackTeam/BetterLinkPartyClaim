---
name: blpc-party
description: Party system reference for BLPC — Provider SPI, Trust levels, data persistence. See blpc-integration-bqu for BetterQuesting integration.
user-invocable: false
---

# BLPC Party System

## Party Provider SPI

Party management is abstracted via `IPartyProvider`, allowing transparent switching between self-managed parties and BQu's party system:

- **`api/party/IPartyProvider`** — Full interface: query methods (`areInSameParty`, `getPartyName`, `getPartyMembers`, `getRole`; `default` methods `findByName`, `allPartyNames`, `pendingInvitesFor`) and mutation methods (`createParty`, `disbandParty`, `renameParty`, `invitePlayer`, `acceptInvite`, `kickOrLeave`, `changeRole`, `syncToAll`). Most mutations identify party via player UUID. Exception: `acceptInvite(player, partyId)`.
  - **`getPartyId(UUID)`** / **`getEffectiveParty(UUID)`** — `default` methods returning `null`. `getPartyId` = stable storage key; `getEffectiveParty` = fully-populated `Party` for protection/claims/transit handlers.
  - **`isLinkedParty(UUID)`** — `default` returning `false`, used for routing mutating actions between providers.
- **`PartyProviderRegistry`** — Priority-based registry. `PRIORITY_LOW=-100`, `PRIORITY_DEFAULT=0`, `PRIORITY_HIGH=100`. Higher wins.
- **`DefaultPartyProvider`** — Self-managed, backed by `PartyManagerData`. Registered at `PRIORITY_DEFAULT`.
- **`BQuPartyProvider`** — BQu implementation, registered at `PRIORITY_HIGH` when BQu present. See `blpc-integration-bqu` for details.

**Offline-member resolution:** `Party.findMemberByName(String)` resolves a member's UUID from their cached display name (`playerNames`, populated from `UsernameCache` — survives logout, unlike the live player list). `Party.findMemberByUsername(MinecraftServer, String)` layers a live-player-list fallback on top for a member not yet name-resolved. `DefaultPartyProvider.kickOrLeave`/`changeRole` and `PartyAction.transferOwnership` all resolve their target through this — so kick, re-rank, and ownership transfer work against offline members, not just online ones.

## Trust Level System

**TrustLevel enum** (ascending): `NONE` < `ALLY` < `MEMBER` < `MODERATOR` < `OWNER`.

**TrustAction enum** (per-party configurable):

| Action | NBT Key | Forge Events |
|---|---|---|
| `BLOCK_EDIT` | `blockEdit` | `BreakEvent`, `EntityPlaceEvent`, `FarmlandTrampleEvent` |
| `BLOCK_INTERACT` | `blockInteract` | `RightClickBlock`, `EntityInteract`, `EntityInteractSpecific` |
| `ATTACK_ENTITY` | `attackEntity` | `AttackEntityEvent` |
| `USE_ITEM` | `useItem` | `RightClickItem` |

Settings panel cycles each through `NONE -> ALLY -> MEMBER`. Additional: FakePlayer trust, explosion protection, free-to-join.

## Data Persistence

File-based persistence (FTB Lib style) under `world/betterlink/pc/`:

```
world/betterlink/pc/
├── config.dat          # bquLinkedPlayers set
├── backup/
│   ├── parties/
│   └── claims/
├── parties/
│   ├── 0.dat           # one compressed NBT file per party
│   └── ...
├── claims/
│   ├── global.dat      # claims for players with no party
│   ├── 0.dat           # claims for party 0
│   └── ...
└── waypoints/
    ├── 0.dat           # shared waypoints for party 0
    └── ...
```

`BLPCSaveHandler` uses atomic write + backup-swap for crash safety. `loadAll` at `ServerStarting`, `saveAll` at `WorldSave` + `ServerStopping`.

## BQu Integration

See `blpc-integration-bqu` for BQuPartyProvider details, link/unlink/disband flow, and NetPartyActionMixin.
