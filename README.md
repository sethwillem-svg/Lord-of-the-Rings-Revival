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

## The add-on (`addon/`)

NeoForge 1.21.1 add-on for the Middle-earth mod (branch `neoforge-fixes`), with [PulseLib](https://github.com/ArcAnc/PulseLib)
1.21.1-1.2.0. It plays our mocap clips on Middle-earth's NPCs (idle, walk, run, hurt, death, sword and unarmed attacks, per race:
man, elf, dwarf, hobbit, orc) and can draw them with PulseLib's instanced GPU renderer. It compiles against your local
Middle-earth jar but never bundles or commits it.

```sh
node tools/anim-viewer/bake_vanilla.js                # re-bake the clips (preview them: ./tools/anim-viewer/view.sh npc)
cd addon && ./gradlew build -PmeJar=$HOME/middle-earth/middle-earth/build/libs/<jar>   # -> build/libs/lotr_revival-0.1.0.jar
./gradlew runClient -PmeJar=...                       # or put both jars (and PulseLib) in a NeoForge 21.1 mods folder
```

Renderer benchmark, in game:

| Command | What it does |
| --- | --- |
| `/lotrbench spawn 200` | a grid of 200 random NPCs in front of you, AI off (`/lotrbench spawn 200 <type> true` for one type with AI) |
| `/lotrrender theirs` | Middle-earth's renderer: skin layers + 3D hair, ears, nose, feet, armour, held items |
| `/lotrrender vanilla` | our skin layers through Minecraft's CPU model path |
| `/lotrrender pulse` | the same skin layers through PulseLib (default) |
| `/lotrbench clear` | remove the spawned NPCs |

The meter bottom-left shows the renderer, NPCs in view and frame time, FPS and 1% low over 5 s (`/lotrrender hud` hides it).
`vanilla` vs `pulse` compares the engines on identical geometry; `theirs` shows what armour and 3D features add.
`./addon/bench.sh` runs the whole comparison unattended (200 NPCs; `./addon/bench.sh 400` for another count): it builds
the add-on, opens the game, creates a flat test world, measures each renderer, quits and prints `addon/run/bench_results.txt`.

## The animation viewer

```sh
./tools/anim-viewer/view.sh mordor_orc      # serve the repo and open the viewer on an orc
```

## Licences

Code: LGPL-3.0 (`LICENSE`, `COPYING.LESSER`). Original art: `ASSETS_LICENSE`. Third-party notices: `NOTICE`.
The Middle-earth mod is All Rights Reserved: an add-on may depend on it, but its code and assets are never copied
into this repo.
