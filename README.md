# The Lord of the Rings: Revival

> **Unofficial fan project.** Not affiliated with, endorsed by, or connected to the Tolkien Estate, Middle-earth
> Enterprises, the authors of the original LOTR Mod, or the authors of the Middle-earth mod. Non-commercial.

## Status: regrouping (2026-11-01)

The from-scratch NeoForge mod was cleared out. The plan now is an **add-on for the Middle-earth mod** (Jukoz and
ObliviousCrab, NeoForge 1.21.1 backport branch): PulseLib rendering and our own models and animations for its NPCs,
then a nemesis system and a node-based map. The earlier mod code, men, gear, model generator, plan and other specs
are in git history (before this commit) if anything is needed again.

What is kept:

| Path | What |
| --- | --- |
| `tools/anim-viewer/` | Mocap to Blockbench rig pipeline: browser viewer, retargeter, clip library (Quaternius UAL, CMU mocap, local Unity packs), Kimodo and HY-Motion generation, PulseLib export. See its README. |
| `tools/anim/` | Python reference retargeter and the parity check against the viewer. |
| `src/main/art/blockbench/` | 104 orc, olog and troll faction models (eight factions, plus low-detail copies), the muster-pack rig. |
| `specs/core/rendering-animation.md` | PulseLib decision, one rig per race, clip names, animation LOD, render budgets. |
| `specs/world/waypoints-travel.md` | Waypoints, fast travel and roads: the starting point for the node-based map. |
| `specs/npc/npc-base.md`, `specs/npc/hiring-trading.md` | NPC behaviour a nemesis system would build on. |

## The animation viewer

```sh
./tools/anim-viewer/view.sh mordor_orc      # serve the repo and open the viewer on an orc
```

## Licences

Code: LGPL-3.0 (`LICENSE`, `COPYING.LESSER`). Original art: `ASSETS_LICENSE`. Third-party notices: `NOTICE`.
The Middle-earth mod is All Rights Reserved: an add-on may depend on it, but its code and assets are never copied
into this repo.
