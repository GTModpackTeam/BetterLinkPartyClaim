---
name: blpc-config
description: Configuration, Chunk Transit, Mixins reference for BLPC.
user-invocable: false
---

# BLPC Configuration & Systems

## Server Configuration (ModConfig)

Forge `@Config` at `common/ModConfig.java`. Auto-syncs on in-game change.

### Claims (`ModConfig.claims`)

| Option | Type | Default | Description |
|---|---|---|---|
| `maxClaimsPerPlayer` | int (0–10000) | 1000 | Max chunks claimable per player |
| `maxForceLoadsPerPlayer` | int (0–10000) | 64 | Max force-loaded chunks per player |
| `additiveLimits` | boolean | true | Party claim limit = sum of members' limits |
| `allowOfflineChunkLoading` | boolean | true | Keep force-loaded chunks when all offline |
| `blockedClaimingDimensions` | int[] | [] | Dimension IDs where chunk claiming is disallowed (e.g. 1 for The End) |

**Party required to claim:** `ClaimChunk.Handler.isPartyMissing` rejects claims without a party. Rejection sends `ClientNotify.claimFailed(REASON_NO_PARTY, ...)`.

**Blocked dimensions:** `ClaimChunk.Handler.isDimensionBlocked` rejects new claims/force-loads whose `dim` is listed in `blockedClaimingDimensions`. Rejection sends `ClientNotify.claimFailed(REASON_DIMENSION_BLOCKED, ...)`.

### Party (`ModConfig.party`)

| Option | Type | Default |
|---|---|---|
| `autoCreatePartySingleplayer` | boolean | true |

### Server Party (`ModConfig.serverParty`)

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | false | Auto-create shared party on server start |
| `name` | String | "Server" | Party name |
| `freeToJoin` | boolean | true | Open join |
| `owner` | String | "" | Owner player name (empty = server-owned) |
| `moderators` | String[] | [] | Moderator player names |

### Data (`ModConfig.data`)

| Option | Type | Default |
|---|---|---|
| `mergeOfflineOnlineData` | boolean | true |

### Fair Play (`ModConfig.fairPlay`)

| Option | Type | Default | Description |
|---|---|---|---|
| `enableAreaEffects` | boolean | true | Potion effects for area control |
| `enableTransitNotify` | boolean | true | Toast notifications on chunk entry/exit — the only on-screen claim-protection indicator |

### Internal Defaults (`ModConfig.Defaults`)

| Constant | Value | Description |
|---|---|---|
| `enableProtection` | true | Master protection toggle |
| `protectMobGriefing` | true | Prevent mob griefing |
| `protectFireSpread` | true | Prevent fire spread |
| `protectFluidFlow` | true | Prevent fluid flow |
| `transitToastDuration` | 3000 | Toast duration (ms) |
| `enemyWeaknessAmplifier` | 0 | Weakness level (0 = I) |
| `enemyMiningFatigue` | true | Mining fatigue for enemies |
| `defenderResistanceAmplifier` | 0 | Resistance level (0 = I) |

## Chunk Transit System

Players receive toast notifications and potion effects when entering/leaving claimed chunks.

- **`RelationType`** — `MEMBER`, `ALLY`, `ENEMY`, `NONE`.
- **`ChunkTransitHandler`** — `PlayerTickEvent.END` listener. Detects chunk crossings, sends `ClientNotify.chunkTransit(...)`, applies effects.
- **`BLPCToast`** — `IToast` with Builder. Factory methods: `fromTransit`, `fromPartyEvent`, `fromClaimFailed`.

### Notification Messages

Third-party wording (seen by other party members watching someone cross their claim boundary):

| Relation | Enter | Leave |
|---|---|---|
| MEMBER | "%s returned home" | "%s went exploring" |
| ALLY | "%s came to visit" | "%s went home" |
| ENEMY | "Invaded by %s" | "%s fled" |

Second-person wording (`ClientNotify.isSelf()` — sent to the transiting player about their own crossing, `blpc.transit.<relation>.<direction>.self` keys) so the toast doesn't read like a report about someone else:

| Relation | Enter | Leave |
|---|---|---|
| MEMBER | "You're back on protected land" | "You left your protected land" |
| ALLY | "Entering %s's territory (Ally)" | "Leaving %s's territory (Ally)" |
| ENEMY | "You invaded %s's territory" | "You left %s's territory" |
| NONE (unrelated claim owner) | "Entering %s's territory" | "Leaving %s's territory" |

Moving between two chunks claimed by the same party (e.g. inside a dense claim cluster) is not treated as a boundary crossing — no notification, no area-effect re-trigger.

NONE toasts go only to the transiting player (informational — no party members notified, no area effects) and are always second-person. MEMBER/ALLY/ENEMY toasts go to the claim's party members (third-person) plus the transiting player themself (second-person, always — not just for ENEMY).

### Area Effects (every 20 ticks)

- **Enemy debuff**: Weakness + optional Mining Fatigue. Removed on leaving.
- **Defender buff**: Resistance + Strength. Active while enemies present.

## Mixins

MixinBooter (`ILateMixinLoader`, pinned to **v10.7**). Conditional late-stage injection:

- **`BLPCMixinLoader`** — Loads mixin configs for `BetterQuesting` and `ModularUI` only. No JourneyMap entry (waypoint detection uses v2 `WaypointEvent` API).
- **`NetPartyActionMixin`** — Injects into BQu's `NetPartyAction.deleteParty()` to auto-unlink affected players.
- **`OverlayStackMixin`** — ModularUI overlay compatibility.

Configs: `mixins.blpc.betterquesting.json`, `mixins.blpc.modularui.json`. No `mixins.blpc.journeymap.json` (removed in v0.15.0).
