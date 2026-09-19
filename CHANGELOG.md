# Changelog

All notable changes to BetterLinkPartyClaim (BLPC) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [0.16.0]

### Added

- **AddonRegistry — unified add-on registration API**
  - New `AddonRegistry.register()` / `registerAction()` with a `modId` for deduplication.
  - Replaces direct `IntegrationPanelRegistry.register()` calls in built-in integrations (BQu, JourneyMap).
  - Third-party integrations can register an AddonPanel button without touching BLPC's shared code.

- **IntegrationPanelRegistry duplicate prevention**
  - Registering the same integration twice no longer creates a duplicate button — the registry now checks for existing entries by label key before adding.

- **ModuleManager duplicate setup guard**
  - Calling `setup()` a second time (which can happen when BLET shares the same ModuleManager instance) now skips the second load instead of duplicating modules.

### Fixed

- **`BQuPartyProvider.findByName()` returning `null`**
  - Added `findByName()`, `allPartyNames()`, and `pendingInvitesFor()` overrides that delegate to `DefaultPartyProvider` fallback.
  - This fixes server party lookup when a player has no BQu party but the server party is self-managed.

[0.16.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.16.0

* * *

## [0.15.3]

### Added

- **Server Party auto-join**
  - When **Server Party > Enable** and **Free to Join** are both enabled, new players are now automatically added to the configured Server Party as a MEMBER on login.
  - This makes the "Free to Join" flag actually functional — previously the flag was set on the party but no automatic join was performed.

[0.15.3]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.15.3

* * *

## [0.15.2]

### Added

- **Claiming/unclaiming a large area by dragging is now much smoother**
  - While you drag across the map, the chunks you're about to claim or unclaim are now highlighted so you can see exactly what will change before you let go — no more guessing.
  - Holding Ctrl while hovering the map now shows the chunk's coordinates in the tooltip, and hovering an unclaimed chunk shows "Wilderness" instead of a blank tooltip.
- **Chunk-crossing toasts now show up for everyone, not just invaders**
  - You'll now get a toast when you come home to your own land, visit an ally's land, or wander into a stranger's claim — not just when an enemy invades. Toasts about your own movements are worded for you ("You returned home") instead of reading like a report about someone else, and show the other player's face when relevant.
  - Walking around inside your own densely packed claims (e.g. a 3x3 base) no longer spams a toast at every single chunk border — only real crossings between different owners trigger one now.
- **Kicking, re-ranking, or transferring ownership to offline party members now works**
  - Previously you could only do this to someone currently online. Now it works the same whether they're logged in or not — no need to wait around for an inactive member to show up just to remove them.

### Changed

- **The always-on "Protected" text above your hunger bar is gone — the same info now comes through as a toast**
  - You'll still be told when you're standing on protected land (and now also when it's an ally's or a stranger's), just via the same toast notifications used for everything else instead of a separate on-screen indicator.
- **Opening the chunk map for the first time no longer causes a brief freeze**
  - On modpacks with a huge number of blocks, the very first time you opened the map screen could hang the game for a few seconds while it prepared the terrain colors. That preparation now happens quietly in the background instead.

[0.15.2]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.15.2

* * *

## [0.15.1]

> **Wire-protocol break.** Chunk claims now carry a dimension ID. Client and server must run the same version.

### Added

- **Chunk claims are now dimension-aware**
  - Claims in the Nether, End, and modded dimensions are tracked separately. A claim at (0, 0) in the Overworld no longer conflicts with (0, 0) in the Nether.
  - Protection checks, transit notifications, and area effects all respect the dimension the player is actually in.
- **Blocked claiming dimensions**
  - Server admins can now block chunk claiming in specific dimensions (e.g. The End) via the "Blocked Claiming Dimensions" config option. Players attempting to claim in a blocked dimension receive an in-game notification.
- **Nether/ceiling world map rendering**
  - The chunk map now renders correctly in the Nether and other dimensions with a ceiling. Instead of showing the bedrock ceiling as a black screen, the map scans downward from the player's Y level to show the actual terrain — matching FTB Utilities' approach.

### Changed

- **JourneyMap overlays are now dimension-filtered**
  - Claim overlays on JourneyMap only show claims for the dimension you're currently in. Nether claims no longer bleed onto the Overworld map.

### Fixed

- **Chunk protection not working correctly across dimensions**
  - All protection events (block edit, block interact, attack entity, item use, explosions, fire spread, fluid flow, mob griefing) now check the dimension of the event, not just the chunk coordinates.

[0.15.1]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.15.1

* * *

## [0.15.0]

> **JourneyMap v5 is no longer supported.** JourneyMap 6.0.0-beta.2 or later is now required.

### Added

- **Key bindings now appear in Minecraft's Controls screen**
  - A new "Better Link Party Claim" category shows up in Options → Controls, with two rebindable keys:
  - Open Chunk Map (default: M)
  - Open Party Menu (default: P) — jump straight to the party menu without opening the map first.
  - Keys coexist safely with JourneyMap and other mods — they only fire while in-game, not inside GUIs.
- **Claim overlay toggle button on JourneyMap's fullscreen map**
  - Opening JourneyMap's fullscreen map now shows a toggle button in the Addon area to show or hide claim overlays.
- **BLPC settings accessible from JourneyMap's own options**
  - Claim overlay visibility, team waypoint sharing, and sync interval can all be changed directly from JourneyMap's Addon Options screen.
  - The BLPC party menu → Addons → JourneyMap button now takes you straight to JourneyMap's settings instead of a separate panel.
- **Shared waypoints organized into a "BLPC Party" group**
  - The group cannot be deleted through JourneyMap's UI — if somehow removed, it is automatically restored.
  - Turning off waypoint sharing hides the group instead of deleting it, so turning it back on restores everything instantly. The group is only fully removed when you leave or disband the party.
- **Periodic waypoint sync**
  - Team waypoints are re-synced to JourneyMap every 5 seconds (100 ticks) by default. The interval is adjustable in JourneyMap's Addon Options — set to 0 for event-driven sync only.

### Changed

- **JourneyMap v6 support**
  - Fully updated to JourneyMap v6's new plugin API. JourneyMap v5 and earlier are no longer compatible.

### Fixed

- **Key bindings not showing in Minecraft's Controls screen**

[0.15.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.15.0

* * *

## [0.14.0]

### Added

- **Claim/party data persists across reconnects**
  - The chunk map and party menu now show your last-known claims and party info immediately after reconnecting to a server, instead of a blank screen while the server's fresh sync is in flight.
  - Cached separately per server/world, so switching between servers never mixes up their claim data.
- **Force-loaded areas stand out on JourneyMap**
  - Claim regions where every chunk is force-loaded now render with a bolder, fully opaque outline on JourneyMap, and the region label is now properly localized.
- **Fair play settings**
  - New config options let server admins tune area-control potion effects and transit toast notifications independently, for servers that want PvP without a home-field advantage.
  - Optional on-screen indicator shows whether you're currently standing in a claimed chunk and who owns it, so PvP fights always make protection status clear.
- **Team waypoint sharing on JourneyMap**
  - With JourneyMap installed, a party's waypoints can now be shared with every online member — only the party owner can add, move, or remove them, and members always see the up-to-date result on their own map.
  - Toggleable per-player in the Addons menu, under JourneyMap.

### Changed

- **Claiming a chunk now requires a party.** Chunk protection is a party-sharing feature, so you must create or join a party before claiming. Singleplayer is unaffected by default (a party is still auto-created on first login).

### Fixed

- **BQu-linked parties could drift out of sync with BLPC.** A player who joined an already-linked BetterQuesting party through BQu's own party screen (rather than BLPC's) was previously invisible to BLPC's protection, claim-limit, and party-management logic — they could be wrongly denied access to their own party's claims, get a separate personal claim limit instead of sharing the party's pool, and be unable to use party settings, disband, or unlink through BLPC's UI. Party membership is now resolved consistently between BQu and BLPC in all of these paths.

[0.14.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.14.0

* * *

## [0.13.0]

### Added

- **Addons menu**
  - A new "Addons" entry in the party menu gathers the settings for optional mod integrations (BetterQuesting, JourneyMap) in one place.
  - The list is searchable, matching the Members, Moderators, and Transfer Ownership screens.
- **JourneyMap claim overlays**
  - With JourneyMap installed, claimed chunks are shown directly on JourneyMap's own map instead of a separate BLPC minimap.
  - The overlay on/off toggle lives in the new Addons menu, under JourneyMap.

### Changed

- **BQu settings moved to the Addons menu**
  - The BQu Link toggle and the "Open BQu Party Manager" button have moved out of the party Settings screen and into the new Addons menu, under BetterQuesting.

### Removed

- **Minimap HUD**
  - The always-on minimap (`N` key) has been removed.
  - The full-screen chunk map (`M` key) is unaffected; JourneyMap users get claim overlays on their own map instead (see Added, above).

[0.13.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.13.0

* * *

## [0.12.0]

### Changed

- **Cleaner party UI**
  - The chunk-map theme-switch button has been removed.
  - Buttons no longer change color when you hover over them.
- **Searchable Transfer Ownership screen**
  - The Transfer Ownership screen now has a search box, matching the Members and Moderators lists, and shows a message when there is no one to transfer to.
- **Tidier claim display on JourneyMap**
  - Adjacent chunks owned by the same player now show as a single outlined area with one label, instead of a separate border and name on every chunk.

### Fixed

- **Hard-to-read party menu text**
  - Button labels now use clear, high-contrast text against the menu buttons.
  - Role names (Owner, Admin) and ally/enemy names display in bright, readable colors instead of dark, muddy ones.

[0.12.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.12.0

* * *

## [0.11.0]

### Changed

- **BQu Link now syncs the full member list**
  - Turning BQu Link ON makes all BQu party members visible in BLPC automatically.
  - Per-player opt-in is no longer required.
- **BQu party auto-created on link**
  - If no BQu party exists when BQu Link is toggled ON, one is created from the BLPC party's name, members, and roles.
  - If a BQu party already exists, any missing BLPC members are added to it.
- **Party screen stays open after BQu Link toggle**
  - Switching BQu Link ON or OFF no longer closes the party menu — the panel refreshes in place.
- **Disband only affects the BLPC party**
  - Disbanding no longer touches the BQu party.
  - Manage the BQu party through BetterQuesting's own screen.

### Fixed

- **Disband not working after re-creating a party**
  - After disbanding and creating a new party, the Disband button would not show the confirmation dialog.
- **Crash on world entry**
  - Entering a world with certain mod combinations could cause a crash.
- **BQu party appearing without linking**
  - Creating a party in BQu would make it show up in BLPC's party list even when BQu Link was OFF.

[0.11.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.11.0

* * *

## [0.10.0]

> **Wire-protocol break.** Client and server must run the same version — a mixed pair will not communicate notifications correctly.

### Changed

- **Live-update party UI**
  - Party panels now stay open and refresh in place when data changes, instead of closing on every sync.
  - Panels only close when the party is gone, permissions change, or ownership is lost.
- **Free-to-join / invite flow**
  - Joining a party from the create/join screen now opens the party menu directly.
  - Full parties are shown grayed out instead of hidden.

### Fixed

- **Stale party data in open panels**
  - After a disband, ownership transfer, or kick by another player, open panels could keep showing outdated state.
  - Panels now refresh or close correctly.
- **Stale values in the Settings panel**
  - Name, color, member count, and toggle states could show outdated values after a server sync.
  - All settings now read live data.
- **Silent join failures**
  - Trying to join a disbanded, no-longer-free, or expired-invite party now shows a toast instead of doing nothing.
- **Self-notification toasts**
  - The player who joins or disbands a party no longer receives their own toast notification.
- **UI desync on rejected actions**
  - When the server rejects a party action, the client now receives a corrective sync so the UI matches the actual state.
- **Moderators panel after promotion**
  - A player promoted to OWNER while the panel is open now sees the role-cycle controls without reopening.
- **Memory leaks**
  - Sub-panel handlers were accumulating on each menu rebuild.
  - Empty tracking sets were left behind on player logout.

[0.10.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.10.0

* * *

## [0.9.0]

### Changed

- **Network layer split by side**
  - Server-side and client-side network handlers are now separated to prevent dedicated-server class-loading issues.

### Fixed

- **Dedicated-server crash on party creation**
  - Creating a party on a dedicated server no longer crashes due to a missing client-only color method.

[0.9.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.9.0

* * *

## [0.8.0]

Initial release.

### Added

- **Chunk claiming**
  - Claim, unclaim, and force-load chunks via a full-screen map (`M` key) and a minimap HUD (`N` key to toggle).
  - Supports drag selection and bulk unclaim/unload buttons.
- **Party system**
  - Server-authoritative parties with three roles (Owner, Admin, Member) and a configurable member cap.
  - Persisted per world.
- **Trust levels**
  - Per-action trust settings (block edit, block interaction, attacking entities, item use) with levels from None to Owner.
  - A separate setting controls fake-player automation mods.
- **Allies and enemies**
  - Party-versus-party relations.
  - Allies share protection access; enemies are denied regardless of trust level.
- **Explosion protection**
  - Per-party toggle for claimed chunks.
- **Free-to-join parties**
  - Optional open-join mode with invitation flow, description, color, and display name.
- **Party manager UI**
  - Tabbed panels for party info, protection, allies, enemies, members, and invitations.
  - Searchable player/party lists with tooltips.
- **Toast notifications**
  - Party events: join, leave, kick, disband, ownership transfer, role change, BQu link/unlink, party full.
  - Claim-limit failures.
- **Transit notifications**
  - Alerts when a member returns home, an ally visits, or an enemy enters/leaves claimed territory.
- **BetterQuesting integration** (optional)
  - Opt-in switch to link a BLPC party to a BQu party.
  - Non-linked players are unaffected.
- **Chunk map rendering**
  - Async terrain colorization with player position, claim ownership, and party color overlays.
- **Chat commands**
  - Public: `/blpc list`, `info`, `me`, `here`, `claims`, `invites`, `accept`, `decline`, `leave`.
  - Operator: `/blpc admin move-owner`, `kick`, `disband`.
  - All commands support tab completion.
- **Localization**
  - English and Japanese translations.

### Compatibility

- Minecraft 1.12.2, Forge.
- Required: ModularUI 3.1.5+.
- Optional: BetterQuesting (party integration), JourneyMap (minimap integration).

[0.8.0]: https://github.com/gtexpert/BetterLinkPartyClaim/releases/tag/v0.8.0
