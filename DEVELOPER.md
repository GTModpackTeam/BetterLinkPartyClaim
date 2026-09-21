# BetterLinkPartyClaim — Developer API

BLPC exposes an addon-facing API for third-party mods that want to plug into its party
system, react to claim/party events, or add their own settings screen. The table below
shows current coverage:

| Area                          | API package                    | Status    |
|--------------------------------|--------------------------------|-----------|
| Custom party backend           | `api.party`                     | Available |
| Query party data                | `api.util` (`PartyQueryUtil`)   | Available |
| Party lifecycle events          | `api.event` (`PartyEvent`)      | Available |
| Claim lifecycle events          | `api.event` (`ChunkModifiedEvent`) | Available |
| Addons hub settings panel       | `api.integration` (`AddonRegistry`) | Available |
| Module framework                | `api.modules`                   | Available |

Start at [`api/BLPCAPI.java`](src/main/java/com/github/gtexpert/blpc/api/BLPCAPI.java) —
it's the central façade and package index that links
every subsystem described below.

Registry-based registration calls (`PartyProviderRegistry`, `AddonRegistry`)
should happen from your mod's `preInit`/`init`, guarded by whichever FML phase the
registry expects (noted per-section below) — see [Module Framework](#module-framework)
if your integration is itself driven by BLPC's module system.

---

## Party Backend SPI

### PartyProviderRegistry

Replace BLPC's party backend entirely — e.g. to back parties with your own guild/clan
system instead of BLPC's self-managed storage or BetterQuesting.

```java
// preInit, or wherever your mod wires up its own systems
PartyProviderRegistry.register(new MyPartyProvider(), PartyProviderRegistry.PRIORITY_HIGH);
```

- `PRIORITY_LOW` (-100) / `PRIORITY_DEFAULT` (0) / `PRIORITY_HIGH` (100) — a
  higher-priority registration wins; a lower one is silently ignored. BLPC's own
  `DefaultPartyProvider` registers at `PRIORITY_DEFAULT`, and `BQuPartyProvider`
  (BetterQuesting integration) at `PRIORITY_HIGH` when BetterQuesting is present — use
  `PRIORITY_HIGH` to take over from BQu, or `PRIORITY_LOW` to act as a fallback only.
- `unregister()` reverts to the internal no-op provider; `getRegisteredPriority()`
  returns the active registration's priority (`Integer.MIN_VALUE` if none).
- `registerNativeScreenOpener(Runnable)` / `unregisterNativeScreenOpener()` /
  `hasNativeScreen()` — optionally expose a shortcut to your own native party-management
  screen from BLPC's UI (this is how the BetterQuesting integration opens BQu's own party
  manager).

Implement `IPartyProvider`:

```java
public class MyPartyProvider implements IPartyProvider {
    @Override
    public boolean areInSameParty(UUID playerA, UUID playerB) { ... }

    @Override
    public String getPartyName(UUID playerUUID) { ... }

    // ... createParty, disbandParty, invitePlayer, kickOrLeave, changeRole, etc.

    @Override
    public void syncToAll() { ... }

    @Override
    public NBTTagCompound serializeForClient() { ... }
}
```

All mutation methods identify the acting party via the player's UUID — no explicit
party-ID parameter is needed except `acceptInvite(player, partyId)`, which targets a
*different* party than the one the player currently belongs to (or none).

#### Default methods on `IPartyProvider`

The following default methods are provided and may be overridden by implementations:

- **`findByName(String)`** — returns the party with the given name, or `null`. Default: `null`.
  `BQuPartyProvider` delegates to `DefaultPartyProvider` fallback so server-party lookups work
  even when the player has no BQu party.
- **`allPartyNames()`** — returns the names of all known parties. Default: empty list.
- **`pendingInvitesFor(UUID)`** — returns all parties with a pending invite for the player.
  Default: empty list.
- **`getOwner(UUID)`** — returns the owner UUID of the player's party, or `null`. Default:
  delegates to `getEffectiveParty(uuid).getOwner()`. Automatically works for `DefaultPartyProvider`
  and `BQuPartyProvider` without explicit overrides.
- **`getAllParties()`** — returns all known `Party` objects, or an empty list.
  Default: empty list. `DefaultPartyProvider` overrides to return all self-managed parties;
  `BQuPartyProvider` delegates to `DefaultPartyProvider` fallback.
- **`countClaims(UUID)`** — returns the number of chunk claims owned by members of the given party.
  Default: 0. `DefaultPartyProvider` and `BQuPartyProvider` override to delegate to
  `ChunkManagerData.countClaimsForParty(partyId)`.
- **`getPartyId(UUID)`** — returns a stable storage key for the player's party (identical for
  every member). Default: `null`.
- **`getEffectiveParty(UUID)`** — returns the fully-populated `Party` for authoritative
  server-side checks (trust resolution, claim limits, chunk-transit). Default: `null`.
- **`isLinkedParty(UUID)`** — `true` if the player is in a BQu-linked party. Default: `false`.
- **`ensureNativePartyWithMembers(UUID, UUID...)`** — ensures a native party exists with the
  given members. Returns `true` if created.

#### Performance Note

`PartyManagerData.getPartyByPlayer(UUID)` is **O(1)** thanks to a `playerToPartyId`
reverse index maintained on every mutation. This is used internally by `IPartyProvider`
implementations. Addons should use `IPartyProvider` / `PartyQueryUtil` methods
(instead of `PartyManagerData` directly) to benefit from this performance.

#### Query Utility — `PartyQueryUtil`

Addons should use `PartyQueryUtil` instead of referencing internal packages:

- **`PartyQueryUtil.provider()`** — shorthand for `PartyProviderRegistry.get()`.
- **`PartyQueryUtil.findByName(name)`** — find a party by display name.
- **`PartyQueryUtil.allPartyNames()`** — list all party names.
- **`PartyQueryUtil.pendingInvitesFor(uuid)`** — parties with pending invites.
- **`PartyQueryUtil.getOwner(uuid)`** — owner UUID of the player's party.
- **`PartyQueryUtil.resolveName(server, party, uuid)`** — human-readable name for a UUID.
- **`BLPCCommandHelper.resolveParty(player)`** — resolves a player's party from command context.

### Default methods are additive — existing callers are not affected

All new default methods (`getOwner`, `getAllParties`, `countClaims`) are **pure additions**.
Existing code that calls `PartyProviderRegistry.get().getEffectiveParty(uuid)` continues to
work unchanged. Addons that already override the methods they need see no breaking changes;
addons that don't override get sensible defaults.

### Server Party

When `ModConfig.serverParty.enabled` and `ModConfig.serverParty.freeToJoin` are both enabled,
`PlayerLoginHandler.onPlayerLogin()` automatically joins new players (who have no native party)
to the configured server party as `MEMBER`. The server party is looked up by name via
`findByName()` — implementations like `BQuPartyProvider` delegate this to `DefaultPartyProvider`
fallback for non-linked players.

Configure in `config/blpc/blpc.cfg`:

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Auto-create sha…4117 chars truncated…gistry (Recommended)

Third-party integrations MUST use `AddonRegistry` — a `@SideOnly(CLIENT)` façade with
`modId` deduplication that replaces raw `IntegrationPanelRegistry` calls. Requires a
[ModularUI](https://github.com/CleanroomMC/ModularUI) dependency, since the factory
builds a `ModularPanel`.

```java
// From a client-guarded init block — lazy method reference so the panel class
// is never loaded on a dedicated server.
if (event.getSide().isClient()) {
    AddonRegistry.register(
        "mymod",                   // modId for deduplication
        "mymod.addons.label",      // button label lang key
        "mymod.addons.tooltip",    // tooltip lang key, or null for no tooltip
        () -> true,                // availability predicate — hide the entry when false
        MySettingsPanel::build);   // UUID playerId -> ModularPanel
}
```

Registering the same `modId` twice is safe — the second call is silently ignored.

If your integration doesn't need its own `ModularPanel` — e.g. it just opens another
mod's native settings screen — register an action-only entry instead with
`registerAction(...)`:

```java
AddonRegistry.registerAction(
    "mymod",                   // modId for deduplication
    "mymod.addons.label",
    null,                       // tooltip lang key, or null
    () -> true,                 // availability predicate
    MySettingsOpener::open);    // Runnable — no UUID/ModularPanel involved
```

`AddonsPanel` distinguishes the two via `Entry.hasPanel()`: panel entries open a
sub-panel through `IPanelHandler`, action entries just invoke `Runnable.run()` on click.
See `integration/jmap/JMapSettingsPanel.java`, which uses `registerAction()` to jump
straight to JourneyMap's own Addon Options screen.

### IntegrationPanelRegistry

The shared registry that backs `AddonsPanel`. New integrations should use
`AddonRegistry` instead — `IntegrationPanelRegistry` is kept for backward compatibility
with existing built-in integrations (BQu, JourneyMap).

---

## Module Framework

### @TModule / IModule

BLPC's own optional integrations (BetterQuesting, JourneyMap) are built on this
framework, and third-party mods can use it too for conditional, dependency-aware
feature loading — though a plain `@Mod` with its own lifecycle works just as well if you
don't need BLPC's config-gated enable/disable.

```java
@TModule(
    moduleID = "my_integration",
    containerID = "mymod",
    modDependencies = "othermod",   // skipped entirely if "othermod" isn't loaded
    name = "My Mod Integration",
    description = "Adds X when Other Mod is present.")
public class MyIntegrationModule implements IModule {

    @Override
    public void preInit(FMLPreInitializationEvent event) { ... }

    @Override
    public void init(FMLInitializationEvent event) { ... }

    @NotNull
    @Override
    public Logger getLogger() {
        return MyMod.LOGGER;
    }
}
```

Discovered automatically via ASM scanning at FML Construction — no manual registration
call. Lifecycle hooks run in FML-stage order (`construction` → `registerPackets` →
`preInit` → `init` → `postInit` → `loadComplete` → server start/stop hooks), each with a
no-op default so a module only overrides what it needs. `getDependencyUids()` can
require sibling modules by `ResourceLocation`; `modDependencies()` requires Forge mods by
ID. Users can still disable any module via `config/<modid>/modules.cfg` unless
`coreModule() = true`.

**Important:** if you use your own `containerID` (e.g. `containerID = "mymod"` instead
of `"blpc"`), that container **must** include exactly one module with
`coreModule = true` — otherwise `ModuleManager` throws an `IllegalStateException` at
FML Construction and the game will not start. If you only have one module, mark it as
the core module. If you register under `containerID = "blpc"` (BLPC's own container),
BLPC's own `CoreModule` already satisfies this requirement.

`@TModule(coreModule = true)` also means the module cannot be disabled via
`modules.cfg`, so use it deliberately, not by default.

---

## Utility helpers

- **`ModUtility.id(path)`** / **`ModUtility.blpcId(path)`** — build a
  `ResourceLocation` namespaced under BLPC's mod ID.
- **`Mods`** — lazy-cached `Loader.isModLoaded()` checks for BLPC's own soft
  dependencies (`BetterQuesting`, `ModularUI`, `JourneyMap`); `Mods.Names` holds the raw
  mod-ID strings. `Mods.Names.JOURNEY_MAP` presence alone isn't a version guarantee —
  BLPC's JourneyMap integration targets the v2 API and requires JourneyMap **v6+**
  (`@Mod`'s `after:journeymap@[1.12.2-6.0.0-beta.2,)` constraint); an older JourneyMap
  install loads as absent from the integration's perspective.
- **`EnumUtils.parseOrDefault(Class<E>, name, default)`** — `Enum.valueOf` that falls
  back to a default instead of throwing on an unrecognized or `null` name. Used
  throughout `api.party`'s `fromName` parsers; reach for it instead of writing another
  try/catch `valueOf`.
