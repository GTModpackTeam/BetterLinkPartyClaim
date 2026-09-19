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
| `additiveLimits` | boolean | true | Party limit = sum of members' limits |
| `allowOfflineChunkLoading` | boolean | true | Keep force-loaded chunks when all offline |
| `blockedClaimingDimensions` | int[] | [] | Dimension IDs where claiming disallowed |

Party required: `ClaimChunk.Handler.isPartyMissing` rejects → `ClientNotify.claimFailed(REASON_NO_PARTY)`.
Blocked dimensions: `ClaimChunk.Handler.isDimensionBlocked` → `REASON_DIMENSION_BLOCKED`.

### Party (`ModConfig.party`)

| Option | Type | Default |
|---|---|---|
| `autoCreatePartySingleplayer` | boolean | true |

### Server Party (`ModConfig.serverParty`)

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | false | Auto-create shared party on start |
| `name` | String | "Server" | Party name |
| `freeToJoin` | boolean | true | Open join (auto-join on login) |
| `owner` | String | "" | Owner player name |
| `moderators` | String[] | [] | Moderator names |

Auto-join: when `enabled` + `freeToJoin`, new players added as MEMBER via `PlayerLoginHandler`.

### Protection (`ModConfig.protection`)

| Option | Type | Default | Description |
|---|---|---|---|
| `blockEditWhitelist` | String[] | `[]` | Blocks bypassing edit protection |
| `blockInteractWhitelist` | String[] | `[]` | Blocks bypassing interact protection |
| `itemUseBlacklist` | String[] | `[]` | Items always blocked in claimed chunks |

### Data (`ModConfig.data`)

| Option | Type | Default |
|---|---|---|
| `mergeOfflineOnlineData` | boolean | true |

### Fair Play (`ModConfig.fairPlay`)

| Option | Type | Default | Description |
|---|---|---|---|
| `enableAreaEffects` | boolean | true | Potion effects for area control |
| `enableTransitNotify` | boolean | true | Toast on chunk entry/exit |

### Defaults (`ModConfig.Defaults`)

`enableProtection`=true, `protectMobGriefing`=true, `transitToastDuration`=3000ms, `enemyWeaknessAmplifier`=0, `enemyMiningFatigue`=true, `defenderResistanceAmplifier`=0.

## Chunk Transit

`ChunkTransitHandler` (`PlayerTickEvent.END`): detects crossings, sends `ClientNotify.chunkTransit()`, applies effects.

**Transit toasts** — third-person for party members, second-person for the transiting player:
- MEMBER: returned home / went exploring
- ALLY: came to visit / went home
- ENEMY: invaded / fled
- NONE: entering/leaving territory (transiting player only)

Dense cluster crossings within same party → no notification.

**Area Effects** (every 20 ticks): Enemy = Weakness + optional Mining Fatigue. Defender = Resistance + Strength.

## Mixins

MixinBooter v10.7. `BLPCMixinLoader` loads only `betterquesting` + `modularui` configs. No JourneyMap mixin.

- `NetPartyActionMixin` — auto-unlink on BQu party delete.
- `OverlayStackMixin` — ModularUI overlay compatibility.
