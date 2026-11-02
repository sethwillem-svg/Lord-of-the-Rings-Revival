# Hiring, reputation and disguise (Middle-earth add-on)

- **Project:** 1 of 4 for the add-on. Then: node map with waypoints and fast travel; nemesis system and world events (with parry and the attack warning marker); structures from licensed builds.
- **Status:** design approved section by section with the maintainer on 2026-11-02; this written spec awaits review.
- **Builds on:** the Middle-earth mod (NeoForge 1.21.1, branch `neoforge-fixes`) and its Seven Stars API, through their public classes only. Both are All Rights Reserved: the add-on depends on their jar and never copies their code, data or assets.
- **Supersedes for the add-on:** `specs/npc/hiring-trading.md`, which was written for the old standalone mod. The add-on uses Middle-earth's coins and leaves trading alone, because Middle-earth plans its own trading.
- **Design references (ideas only, no code):** Conqueror's Blade (squad control, formations with bonuses, leadership costs) and Villager Recruits / Frontier Cohorts (hirable units with formations; licences unconfirmed, so used as a feature checklist only).

## Summary

Players earn reputation with Middle-earth's factions by fighting their enemies, most of all their captains. With enough reputation, a faction's leaders sell its soldiers for coins. Hired units form squads that follow, hold and charge on command, in formations that give real combat bonuses. Units can be equipped, level up a little, travel with their owner and be revived at a recruiter. Wearing a full set of an enemy faction's armour disguises a player to that faction until a leader sees through it.

## Player-facing behaviour

1. A new player has +100 reputation with the faction chosen in Middle-earth's onboarding: enough to hire a few militia at once.
2. Killing NPCs changes reputation (kill rule below). The Reputation screen shows each faction's score, tier and progress.
3. Interacting with a faction's leader-rank NPC opens the **Recruit** menu: the faction's hireable NPC types with price, required tier and leadership points, plus that player's fallen units of this faction to revive.
4. A hire spawns the unit next to the recruiter as a new squad, or adds it to a chosen squad of the same faction. It follows its owner by default.
5. **Controls:** number keys 1-9 select a squad, 0 selects all. Hold the command key (`R` by default) for the command wheel: follow, hold here, charge, formation, dismiss. Quick keys for follow, hold and charge exist but are unbound by default. A small display shows the selected squad, its order, its formation and how many units are alive.
6. Interacting with one of your own units opens the **Unit** menu: name, level and stats, equipment slots (head, chest, legs, feet, main hand, off hand), squad, dismiss.
7. Following units come along on fast travel and dimension changes. Units holding a position stay.
8. A unit that dies drops nothing; its gear and level are kept in its record, and it can be revived at a recruiter of its faction for half its hire price.
9. Wearing four armour pieces that all belong to one enemy faction disguises you to that faction. Having hired units with you, attacking that faction, removing a piece, or a leader getting close all end it.

## Rules and numbers

All numbers are config values (`lotr_revival-common.toml`) unless marked as data.

### Reputation

| Thing | Value | Notes |
| --- | --- | --- |
| Start: chosen faction | +100 | Middle-earth onboarding choice; applied once, on first join after the add-on is installed |
| Start: factions HOSTILE to it | -100 | |
| Start: all others | 0 | |
| Range | -10,000 to +10,000 | clamped |

| Tier | From | Hiring | Army cap (leadership points) |
| --- | --- | --- | --- |
| Hostile | below 0 | recruiters refuse | 0 |
| Neutral | 0 | recruiters talk, no hiring | 0 |
| Trusted | 100 | militia | 4 |
| Respected | 300 | + soldiers | 8 |
| Honoured | 700 | + knights | 14 |
| Champion | 1,500 | + veterans | 22 |
| Hero | 3,000 | all | 30 |

- The army cap is the best tier's cap over all factions, never above the maximum (default 30).
- Units already hired stay if your cap drops, but you cannot hire again until you are under the cap.

### Kill rule

Killing an NPC of faction X. Middle-earth's affinities are ALLY, FRIENDLY and HOSTILE.

| Who | Change |
| --- | --- |
| X | -2 |
| each faction ALLY or FRIENDLY with X (not X itself) | -1 |
| X's sworn enemy | +2 |
| each other faction HOSTILE to X | +1 |

- Multipliers by the victim: leader rank x10; nemesis x25 (applied by the nemesis project through the same entry point). Other ranks x1. Civilians count like soldiers.
- Kills by a player's hired units credit the owner at 50%, rounded toward zero.
- Kills of an NPC whose faction is unknown, or by an unowned NPC, change nothing.
- Sub-factions: `longbeards.erebor` and `longbeards`, and `hobgoblin_tribes.gundabad` and `hobgoblin_tribes`, use their own affinity lists from Middle-earth's data; the sworn-enemy table names the sub-faction.

Example, a Gondor player kills a Moria goblin: Moria -2; Moria's allies and friends -1; Erebor +2 (Moria's sworn enemy); Gondor and every other faction HOSTILE to Moria +1.

### Sworn enemies (data)

Middle-earth has no "hates most" level, so the add-on keeps this table as datapack data: `data/lotr_revival/sworn_enemies/<faction path>.json` with `{ "faction": "middle-earth:gondor", "sworn_enemy": "middle-earth:mordor" }`. Overridable by datapacks; a faction with no file has no sworn enemy.

| Faction | Sworn enemy |
| --- | --- |
| gondor | mordor |
| mordor | gondor |
| rohan | isengard |
| isengard | rohan |
| longbeards.erebor (and longbeards) | hobgoblin_tribes.gundabad |
| hobgoblin_tribes.gundabad (and hobgoblin_tribes) | longbeards.erebor |
| moria | longbeards.erebor |
| lothlorien | moria |
| woodland_realm | goblin_town |
| goblin_town | woodland_realm |
| dale | hobgoblin_tribes.gundabad |
| shire | mordor |
| brigand, wild_goblins | none |

### Recruiting

- **Recruiters:** NPCs whose type is in the faction's LEADER rank pool, for example `gondor.leader`, `mordor.captain`, `isengard.leader`, `rohan.eorling_marshal`, `lothlorien.lord`, `woodland_realm.commander`, `longbeards.erebor.leader`, `hobgoblin_tribes.gundabad.leader`, `brigand.chieftain`. A datapack tag `lotr_revival:recruiters` (NPC type ids) can add to the set or replace it.
- **Roster:** the recruiter's faction's rank pools from Middle-earth's faction data, so new NPC types appear automatically.

| Middle-earth rank | Hireable from | Leadership points | Price (copper value) |
| --- | --- | --- | --- |
| CIVILIAN | never | - | - |
| MILITIA | Trusted | 1 | 30 |
| SOLDIER | Respected | 1 | 80 |
| KNIGHT | Honoured | 2 | 200 |
| VETERAN | Champion | 3 | 500 |
| LEADER | never (they are the recruiters) | - | - |

- **Coins:** Middle-earth's `copper_coin`, `silver_coin` and `gold_coin`, valued 1 / 10 / 100 copper (config). Payment takes coins from the inventory, smallest first, and gives change in the largest coins that fit.
- **Spawn:** next to the recruiter, on the nearest free standing spot within 4 blocks; if there is none, the hire is refused and nothing is paid.
- The recruiter must not be hostile to you, and you must not be disguised as its faction while hiring.

### Units

- A hired unit is a Middle-earth NPC of the hired type, with Middle-earth's normal skin, gear and name generation, plus add-on data: owner, squad, level, experience, rank, hire price.
- **Squads:** up to 9 per player. A purchase starts a new squad or joins an existing squad of the same faction. A squad belongs to one faction.
- **Levels:** 1 to 5. Experience 1 per kill, 5 per leader kill; thresholds 0 / 5 / 15 / 30 / 50. Each level above 1 gives +10% max health and +10% attack damage. Shown as stars above the unit.
- **Equipment:** the Unit menu edits the unit's armour and hand slots; items move between the player's inventory and the unit. Middle-earth's own gear generation does not run again on a hired unit.
- **Dismiss:** with a confirmation, the unit leaves your army for good and its equipment drops.
- **Death and revival:** on death, drops are suppressed and a fallen record is kept on the owner (type, level, experience, equipment, name, hire price, squad). Revival at a recruiter of the same faction costs half the hire price and spawns the unit with its record. Fallen records are kept until revived or discarded in the Recruit menu (at most 30 per player; the oldest is discarded beyond that).
- **Persistence:** units never despawn. When the owner logs out, units stay where they are; following units hold where they stand until the owner returns.
- **Travel:** on fast travel or a dimension change, units on Follow within 32 blocks of the owner teleport with them. A following unit more than 32 blocks away and out of the owner's sight teleports to its slot.

### Orders

| Order | Behaviour |
| --- | --- |
| Follow (default) | Keep formation slots around and behind the owner. Attack enemies within 8 blocks of the owner or the unit, then return. |
| Hold here | Walk to the block the owner is looking at (within 64 blocks), form up facing the owner's facing, stay. Fight enemies within 6 blocks of the slot, then return. |
| Charge | Attack the looked-at target, or the nearest enemy to the looked-at block within 16 blocks. When no enemies remain within 16 blocks, go back to the previous order. |
| Dismiss | The squad leaves your army for good, with a confirmation; equipment drops (see Units). |

- **Enemies:** NPCs that Middle-earth's diplomacy makes hostile to the owner's chosen faction, hostile monsters, anything that attacked the owner or the unit within the last 10 seconds, and the owner's current melee target. Never the owner, the owner's other units, or other players unless that player attacked the owner (no PvP initiation).

### Formations

| Formation | Bonus | Drawback | Who can use it | Stance clip |
| --- | --- | --- | --- | --- |
| Line | +10% melee damage | - | everyone | guard (`hold.sword`) |
| Shield wall | damage from the front -40%, arrows -60% (scaled by shield: light x0.5, medium x0.75, heavy x1) | -30% move speed; cannot charge | units with a shield in the off hand | shield brace (`defend.brace.shield`) |
| Spear line | +50% damage against enemies charging or moving faster than 0.2 blocks per tick toward the line, mounted ones included; knocks them back and cancels their charge bonus | -20% move speed; cannot charge | units holding a spear (Middle-earth `#middle-earth:spears`) | spear brace (new clip) |
| Column | +20% move speed | +15% damage taken | everyone | march (`Walk_Formal_Loop`) |
| Wedge | charge first hit +60% instead of +30% | +20% damage taken | melee units | guard |
| Loose | arrows and explosions -50% | -15% melee damage | everyone (meant for archers) | none |

- **Charge bonus:** charging units sprint at +30% speed. A unit's first hit after running at least 4 blocks deals +30% damage (+60% in wedge) and knocks the target back; a mounted unit's knockback is doubled.
- **"Front"** for shield wall means the attacker is within 60 degrees of the unit's facing.
- **Mixed squads:** units that cannot use the chosen formation stand in line behind those that can.
- **Slots:** spacing 1.5 blocks (shield wall and spear line 1.0, loose 3.0); relative to the owner when following (rotating with the owner's facing), fixed when holding. Several squads following line up one behind another. A blocked or unsafe slot (inside a block, over a drop of more than 3 blocks, in lava) moves to the nearest safe spot.
- **Banner bearer:** a unit holding a Middle-earth held banner gives units of its squad within 8 blocks +10% damage and -10% damage taken. Checked once per second; does not stack.

### Disguise

- **Armour factions:** at data load, each armour item is mapped to the factions whose NPC types list it in their gear. A worn set disguises you as faction F when all four pieces map to F and F is HOSTILE to your chosen faction.
- **While disguised:** F's NPCs do not pick you as a target through Middle-earth's diplomacy. Anything else still makes them hostile.
- **Ends when:** you have a hired unit within 32 blocks; you attack one of F's NPCs; you take off a piece; or one of F's leader-rank NPCs stays within 6 blocks of you for 3 seconds. Being discovered makes F's NPCs within 32 blocks target you, and you cannot disguise as F again for 5 minutes.
- Shown as a small mask icon on the HUD while active.

## Edge cases

- **Multiplayer:** units belong to one player. Other players' units are neither allies nor enemies unless their owners fight. A player can only command, equip or dismiss their own units. Kill credit goes to whoever dealt the killing blow (or the owner of the unit that did).
- **Death, logout, dimension change:** the owner's death does not affect units, which keep their order. Logout: units stay, following units hold. Dimension change: following units come along (see Travel); holding units stay.
- **Chunk unloading:** units in unloaded chunks are saved like other entities. A following unit left in an unloaded chunk is pulled to its owner the next time its chunk loads.
- **Recruiter dies or is a hired unit:** a dead recruiter is just gone. A leader you somehow own (not possible through hiring) cannot recruit.
- **Faction data changes:** if a datapack removes a faction or NPC type, its units stay as plain Middle-earth NPCs with our data. They are still owned, but cannot be revived (Recruit menu shows them as unavailable), and reputation for an unknown faction is kept but not shown.
- **Mounts:** a hired NPC that spawns with a mount (Middle-earth mount data) keeps it; the mount follows as its rider moves. Mounts are not units and count no leadership points.
- **Interaction with Middle-earth's diplomacy:** unchanged except that hired units never target their owner or the owner's units, and disguise blocks the disguised faction's diplomacy targeting.

## Data and APIs

- **Player data attachment `lotr_revival:army`:** reputation `Map<ResourceLocation, Integer>`; reputation initialised flag; squads (id, faction, order, formation, hold position and facing, unit UUIDs); fallen records; disguise cooldowns. Saved with the player; synced to its owner (reputation, squads summary).
- **Entity data attachment `lotr_revival:unit`** on Middle-earth `NpcEntity`: owner UUID, squad id, level, experience, rank, hire price, formation slot index. Saved with the entity; the owner UUID and level are synced to clients (stars, interaction).
- **Datapack:** `data/lotr_revival/sworn_enemies/*.json`; tag `data/lotr_revival/tags/middle-earth/npc_type/recruiters.json` (optional additions).
- **Config:** every number in Rules and numbers.
- **Packets (CustomPacketPayload):** C2S select squad, order (with target position or entity id), formation, dismiss, recruit, revive, equip; S2C reputation, squads summary, disguise state.
- **Middle-earth API used:** `NpcEntity` (type and faction ids, equipment), faction data and rank pools (`FactionLookup`, `Faction`), affinities (`initial_diplomacy`), NPC type gear, the diplomacy target goals (one mixin to exclude owners, their units and disguised players), coin items, held banner and shield items (`HeldBannerItem`, `CustomShieldItem` with `ShieldTypesME`), the `middle-earth:spears` item tag.
- **Seven Stars API used:** `MemoryModulesAPI` to register memories (owner, squad, hold position) where Middle-earth's NPC brain is active; its existing tasks (`DefendHomeTask`, `StrollAroundHomeTask`, `MoveTowardsPosMemoryTask`) for holding a position.
- **Our goals** (added on `EntityJoinLevelEvent` for owned units, above Middle-earth's wandering): follow-formation, hold-formation, charge, protect-owner, attack-owner's-target.
- **Our animator:** new baked clip slots for formation stances (`guard`, `shield_brace`, `spear_brace`, `march`) played while a unit in formation is idle or walking.
- **Kill hook:** one entry point `Reputation.onKill(killer, victim, multiplier)` that the nemesis project reuses.

## Out of scope

- Traders and our own coins (Middle-earth plans trading).
- Hold fire / fire at will, stances (aggressive, defensive, passive), unit abilities (spear brace as an active skill, volleys).
- Pavise line, two-rank archer lines, morale, resting in beds.
- Promotion of level-5 units to sergeants (nemesis project).
- Reputation changing who attacks whom beyond disguise.
- Quests or other reputation sources besides kills.

## Acceptance tests

- [ ] GameTest: kill rule. Killing a Moria NPC as a Gondor player gives Moria -2, Moria's friends -1, Erebor +2, other factions hostile to Moria +1; a leader kill gives x10; a unit's kill credits its owner at 50%.
- [ ] GameTest: tiers and cap. Scores map to tiers and caps; the cap is the best tier's cap, never above the maximum.
- [ ] GameTest: recruiting. A hire fails without enough coins, tier or free leadership points (nothing paid); succeeds otherwise, spawns the type, pays exactly with change.
- [ ] GameTest: death and revival. A unit's death keeps a fallen record with equipment and level; revival costs half and restores them.
- [ ] GameTest: persistence. Squads, orders, units and fallen records survive a save and reload.
- [ ] Unit test: formation slot positions for each formation, rotation with facing, and squad stacking.
- [ ] GameTest: formation bonuses. Shield wall reduces frontal and arrow damage by the configured share per shield weight; the charge first-hit bonus applies after a 4-block run only.
- [ ] GameTest: disguise. A full enemy set stops that faction's diplomacy targeting; a nearby leader for 3 s, a hired unit nearby, an attack or removing a piece ends it.
- [ ] Playtest: hire from three factions; every order; every formation and its stance; travel with units; revival; banner bearer; disguise in enemy territory.

## Tickets

Filled in by the implementation plan.
