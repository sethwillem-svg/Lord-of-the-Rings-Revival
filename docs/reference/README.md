# Reference

Behavior notes used to write specs. Everything here is **written in our own words**: facts, numbers and rules, never code, identifiers, assets or lang text from any other mod.

## Legacy (LOTR Mod 36.15, 1.7.10)

The original mod is All Rights Reserved. These notes were written by pod leads (the clean-room "dirty team") from studying it. Implementers may read these notes; never the original.

| File | Covers | Pod |
| --- | --- | --- |
| [factions-alignment.md](legacy/factions-alignment.md) | 25 factions, relations, kill values and spread, ranks, pledging, conquest, drain | Core |
| [brewing.md](legacy/brewing.md) | Barrel, strength tiers, 29 drinks, vessels, drunkenness | Core |
| [biomes.md](legacy/biomes.md) | 169 biomes by region: climate, height, trees, spawns, invasions | World |
| [structures.md](legacy/structures.md) | Villages, forts, camps, ruins, fixed placements, loot | World |
| [world-map-travel.md](legacy/world-map-travel.md) | Scale, portals, 274 waypoints, fast travel, 56 roads, calendar | World |
| [npcs.md](legacy/npcs.md) | Shared NPC behavior, every NPC type by faction | NPCs |
| [hiring-units.md](legacy/hiring-units.md) | Hiring costs, unit lists, commands, leveling | NPCs |
| [combat-trade.md](legacy/combat-trade.md) | Weapons, armor, mounts, coins, traders, invasions | NPCs |
| [achievements-titles.md](legacy/achievements-titles.md) | 668 achievements, ~527 titles, how they're earned | Progression |
| [mini-quests.md](legacy/mini-quests.md) | Quest types, limits, rewards, bounties | Progression |
| [banners-fellowships-menu.md](legacy/banners-fellowships-menu.md) | Banner protection, fellowships, menu and map screen | Progression |

Each file ends with "Differences from our draft spec" where relevant. Matching Legacy is a choice, not a requirement; where we do, specs should cite the note.

## Adding notes

- Leads only. Study the original outside this repo (a local decompile or playing the game); never commit it.
- Describe behavior and numbers as design rules. No code or pseudo-code, no class/method/field names or paths, no copied text beyond proper names.
- Start each file with the standard header used in `legacy/`.
