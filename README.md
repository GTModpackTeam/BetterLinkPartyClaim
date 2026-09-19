<p align="center"><img src="https://github.com/GTModpackTeam/BetterLinkPartyClaim/blob/main/src/main/resources/assets/blpc/logo.png" alt="Logo" width="128" height="128"></p>
<h1 align="center">BetterLinkPartyClaim</h1>
<h1 align="center">
    <a href="https://www.curseforge.com/minecraft/mc-mods/better-link-party-claim"><img src="https://img.shields.io/badge/Available%20for-MC%201.12.2%20-informational?style=for-the-badge" alt="Supported Versions"></a>
    <a href="https://github.com/GTModpackTeam/BetterLinkPartyClaim/blob/main/LICENSE"><img src="https://img.shields.io/github/license/GTModpackTeam/BetterLinkPartyClaim?style=for-the-badge" alt="License"></a>
    <a href="https://discord.gg/xBwHpZyZdW"><img src="https://img.shields.io/discord/945647524855812176?color=5464ec&label=Discord&style=for-the-badge" alt="Discord"></a>
    <br>
    <a href="https://www.curseforge.com/minecraft/mc-mods/better-link-party-claim"><img src="https://cf.way2muchnoise.eu/1530505.svg?badge_style=for_the_badge" alt="CurseForge"></a>
    <a href="https://modrinth.com/mod/better-link-party-claim"><img src="https://img.shields.io/modrinth/dt/better-link-party-claim?logo=modrinth&label=&suffix=%20&style=for-the-badge&color=2d2d2d&labelColor=5ca424&logoColor=1c1c1c" alt="Modrinth"></a>
    <a href="https://github.com/GTModpackTeam/BetterLinkPartyClaim/releases"><img src="https://img.shields.io/github/downloads/GTModpackTeam/BetterLinkPartyClaim/total?sort=semver&logo=github&label=&style=for-the-badge&color=2d2d2d&labelColor=545454&logoColor=FFFFFF" alt="GitHub"></a>
</h1>

## About

BetterLinkPartyClaim (BLPC) is a chunk claiming mod with a server-authoritative party system for Minecraft 1.12.2. Optional integration with BetterQuesting Unofficial and JourneyMap.

See [CurseForge](https://www.curseforge.com/minecraft/mc-mods/better-link-party-claim) or [Modrinth](https://modrinth.com/mod/better-link-party-claim) for full details and downloads.

## Features

### Chunk Claiming
- Full-screen chunk map (**M** key) with async terrain rendering and party color overlays
- Drag selection for bulk claim/unclaim/force-load operations
- One-click "unclaim all" / "unload all" buttons

### Party System
- Server-authoritative parties with Owner/Admin/Member roles
- Per-action trust levels (block edit, block interact, attack entity, item use)
- Party-vs-party allies and enemies
- Free-to-join toggle, party color, description, explosion protection

### Party Menu
- Tabbed ModularUI party manager with searchable player lists
- Open directly via **P** key — no need to open the map first
- Addons hub for per-mod integration settings

### JourneyMap Integration (v6+)
- Claim overlays drawn directly on JourneyMap's map
- Toggle button on JourneyMap's fullscreen map
- BLPC settings adjustable from JourneyMap's Addon Options screen
- Team waypoint sharing — the party owner's waypoints appear as a locked "BLPC Party" group for all members

### BetterQuesting Integration
- Link your BLPC party to a BQu party with a single toggle
- Quest sharing works unchanged

### Notifications
- Toast notifications for party events and claim-limit failures
- Transit notifications for member returns, ally visits, enemy entry/exit
- Area effects for enemies and defenders (configurable)
- Protection status HUD indicator

### Chat Commands
- `/blpc list`, `info`, `me`, `here`, `claims`, `invites`, `accept`, `decline`, `leave`
- Admin: `/blpc admin move-owner`, `kick`, `disband`

## Requirements

- Minecraft 1.12.2, Forge
- **Required:** [ModularUI](https://www.curseforge.com/minecraft/mc-mods/modularui) 3.1.5+
- **Optional:** [BetterQuesting Unofficial](https://www.curseforge.com/minecraft/mc-mods/better-questing-unofficial) 4.3.2+
- **Optional:** [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) 6.0.8+

## For Developers

BLPC exposes an addon-facing API — custom party backends, party/claim lifecycle events,
Addons-menu settings panels, and more. See [`DEVELOPER.md`](DEVELOPER.md).

## Credits

- Built on [Better Questing Unofficial](https://www.curseforge.com/minecraft/mc-mods/better-questing-unofficial) party system
- Uses [ModularUI](https://github.com/CleanroomMC/ModularUI) for in-game UI rendering
