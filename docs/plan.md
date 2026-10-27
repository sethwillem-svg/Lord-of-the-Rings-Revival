# Parity plan

Full plan with diagrams: <https://claude.ai/artifact/5Md58kYQhefWaqEwDcPpJU> (private to the maintainer).

## Goal

Reach gameplay parity with LOTR Mod Legacy 36.15 on NeoForge 1.21.1 in about 26 weeks, built from scratch by 4 Opus leads and 10 Sonnet implementers, with one human integrator and playtester.

## Team

| Pod | Lead | Implementers |
| --- | --- | --- |
| Core and content | Opus 1 (integrator) | S1 blocks and datagen, S2 items, food, armor |
| World | Opus 2 | S3 biomes south and east, S4 biomes north and west, S5 structures |
| NPCs and combat | Opus 3 | S6 races and models, S7 AI and combat, S8 hiring and invasions |
| Progression | Opus 4 | S9 quests and achievements, S10 banners, traders, GUI |

## Gates

| Gate | Week | Done when |
| --- | --- | --- |
| G1 | 3 | Registries, datagen, attachments, alignment API and networking merged and documented |
| G2 | 8 | Shire to Bree region signed off; hobbits and men with AI; item and block backlog cleared |
| G3 | 16 | All 170 biomes, every race and faction NPC, mounts, map GUI |
| G4 | 24 | Hiring, invasions, quests, achievements, titles, banners, fellowships, traders |

## Scope baseline (Legacy 36.15)

| Area | Legacy count |
| --- | --- |
| Factions | 25 |
| Waypoints | 276 |
| Biomes | 170 |
| Named block variants | ~1,350 |
| Items (non-block) | ~900 |
| Named NPCs and mobs | ~456 |
| Achievements | ~1,000 |
| Structure types | dozens |

Counts come from the original jar's lang keys and are for sizing only.
