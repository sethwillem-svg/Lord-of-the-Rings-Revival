# Specs

Behavior specs for the Middle-earth add-on, one file per system in `addon/`. Implementers build only from these.

- Path: `specs/addon/<system>.md`, copied from [`_TEMPLATE.md`](_TEMPLATE.md).
- Describe behavior a player can observe: what happens, numbers, timings, edge cases. Every spec has a Logging section ([debug-logging.md](addon/debug-logging.md)).
- No code, data or assets from the original LOTR Mod or the Middle-earth mod. Naming Middle-earth's public classes as integration points is fine.
- `specs/archive/` holds the retired standalone-mod specs. Read them for ideas; never build from them.

| Spec | System |
| --- | --- |
| [hiring-reputation](addon/hiring-reputation.md) | Reputation, recruiting, squads, orders, formations, disguise |
| [combat-parry-warning](addon/combat-parry-warning.md) | Attack warning marker, parry |
| [node-map-travel](addon/node-map-travel.md) | Waypoint nodes, routes, fast travel |
| [nemesis-events](addon/nemesis-events.md) | Nemesis system, world events |
| [structures](addon/structures.md) | Structures from CC-licensed builds, event venues |
| [npc-animation-pulselib](addon/npc-animation-pulselib.md) | NPC animation on PulseLib |
| [debug-logging](addon/debug-logging.md) | Per-system logs, CI |
