# NPC base entity and race framework

- **Pod:** npc
- **Lead:** Opus 3
- **Status:** draft
- **Depends on:** `specs/core/core-apis.md` (faction registry, faction relations, alignment attachment, alignment events, registers, datagen hooks), `specs/world/dimension-and-mapgen.md` (biome ids, structure markers, roads)

## Summary

Every person in Middle-earth (hobbits, men, and later elves, dwarves and orcs) is built on one shared NPC base. The base gives each NPC a race (body, name style, base stats), exactly one faction, a generated name, equipment, a daily routine, speech, and a reaction to each player that comes from that player's alignment with the NPC's faction. Players care because the world feels inhabited and because their alignment visibly matters: a hobbit greets a friend of the Shire, runs from a known killer, and Shirriffs come running when one is attacked. This spec also defines the hooks that trading and hiring plug into later, NPC spawn and despawn rules, and the first two races: hobbits (Shire, Bree) and men (Bree-land).

## Player-facing behavior

1. **Meeting an NPC.** NPCs wander near their home or spawn point. Within 8 blocks they sometimes turn to look at the player. Looking straight at one shows its nameplate: name plus role ("Tomlo Brandybuck, Farmer"), colored green (friendly), white (neutral) or red (hostile) for that viewer.
2. **Talking.** Right-click (or sneak-right-click on a trader) and the NPC says a line in chat to that player only: `<Tomlo Brandybuck> Fine weather for the barley, isn't it?`. The line depends on the player's reaction band, the time of day and the NPC's role. Example lines in this spec are ours; all speech is written new.
3. **Reactions by alignment.** A player in the hostile band is attacked on sight by guards and avoided by civilians, who refuse to talk ("Off with you! We know what you've done."). A neutral player can talk but not trade or hire. A friendly player can talk, trade and (later) hire.
4. **Hitting a friendly NPC.** The first accidental hit from a neutral or friendly player gives a warning line and no fight. A second hit within 10 s provokes the NPC: it fights back (or flees, if it is a hobbit civilian) and guards of its faction and allied factions nearby join in. Killing it costs alignment.
5. **Faction warfare.** Guards attack NPCs of enemy factions and vanilla hostile monsters that come near. Civilians flee from both.
6. **Day and night.** By day civilians work and wander; at dusk structure-bound NPCs walk home and stay near the door; guards keep patrolling. Talking at night gives night lines ("Lock the gate, it's late.").
7. **Trading and hiring.** A trader role NPC opens a trade screen for a friendly player (trade system: later spec). Until that spec ships, it says it has nothing to sell today. Hiring is defined only as an interface here.

## Rules and numbers

All numbers are tuning placeholders unless marked fixed. Ticks: 20 per second.

### Race and faction model

- A **race** is a body and culture: model, hitbox, base attributes, name bank, speech set, default temperament. Races are code-defined in the `npc` package (not a datapack registry), because each needs a model and renderer.
- A **faction** is a political allegiance owned by core (`lotrrevival:hobbit`, `lotrrevival:bree`, ...).
- A race can serve several factions (hobbits serve both `hobbit` and `bree`). **Each NPC has exactly one faction**, fixed by its entity type. It never changes at runtime and is not stored per entity (no editing via NBT).
- An **entity type** = race + faction + combat class (`civilian` or `guard`). A civilian type has **roles** (farmer, villager, innkeeper, traveller) stored on the entity and synced to the client. Roles change equipment, routine, title, speech and the trader flag, not the AI class.

### G2 entity types

| Entity id | Race | Faction | Class | Roles | Hireable later |
| --- | --- | --- | --- | --- | --- |
| `lotrrevival:hobbit` | hobbit | hobbit | civilian | villager, farmer, innkeeper, traveller | no |
| `lotrrevival:hobbit_shirriff` | hobbit | hobbit | guard | - | yes |
| `lotrrevival:bree_hobbit` | hobbit | bree | civilian | villager, farmer, innkeeper | no |
| `lotrrevival:bree_man` | man | bree | civilian | villager, farmer, innkeeper, traveller | no |
| `lotrrevival:bree_guard` | man | bree | guard | - | yes |

Role weights for wild spawns: villager 60, farmer 30, traveller 10. Innkeeper never spawns wild; only structures assign it.

### Race base stats

| Stat | Hobbit | Man | Notes |
| --- | --- | --- | --- |
| Max health | 16 | 20 | Guards: +4 |
| Hitbox (w x h) | 0.45 x 1.25 | 0.6 x 1.8 | Eye height 1.05 / 1.62 |
| Movement speed attribute | 0.24 | 0.23 | Vanilla zombie is 0.23 |
| Wander speed multiplier | 0.8 | 0.8 | |
| Flee speed multiplier | 1.5 | 1.3 | |
| Unarmed attack damage | 1 | 2 | Weapon damage added from held item |
| Follow / sense range | 24 | 24 | Blocks |
| Knockback resistance | 0 | 0 | Guards 0.1 |
| Base armor | 0 | 0 | Worn armor counts normally |
| Temperament (civilian) | flee | fight back, flee below 30% HP | Guards always fight |
| XP on player kill | 1-3 | 1-3 | Guards 3-5 |

### Shared behavior

| Thing | Value | Notes |
| --- | --- | --- |
| Look at player | within 8 blocks, 2% chance per tick to start, hold 2-4 s | Also turns to face a player who is talking to it |
| Idle wander | every 120 ticks avg, up to 10 blocks | Clamped to home radius when it has a home |
| Swim | yes; avoids water when pathing if possible | |
| Doors | opens and closes wooden doors and fence gates | Never iron doors |
| Out-of-combat regen | 1 HP per 100 ticks after 200 ticks with no damage | |
| Nameplate | shown on crosshair within 8 blocks, color by viewer's band | Not always-on, so villages aren't a wall of text |
| Picks up items | no | Avoids griefing and despawn exploits |
| Fall damage, fire, drowning | vanilla | |
| Melee attack cooldown | 20 ticks | Damage from held item's attack damage attribute or race unarmed |
| Ranged attacks | none at G2 | Archers come with later races |

### Names

- Each race has name banks per gender: first names and family names. Hobbits: `First Family` ("Hamfast Proudfoot"). Men of Bree: `First Family` with a Bree-style surname ("Barliman Heathertoe" style; banks are our own lists of Tolkien-flavored names, Tolkien names allowed).
- Gender is picked 50/50 at spawn and only selects the name bank and voice lines. One model per race at G2.
- Banks are datapack JSON owned by npc: `data/lotrrevival/lotrrevival/npc_names/<race>_<gender>_first.json` and `<race>_family.json` (lists of strings). Reloadable.
- The name is rolled once at spawn, persisted on the entity, and never re-rolled. A vanilla name tag overrides the displayed name and sets persistence.
- Title shown after the name: the role or class lang key (`Farmer`, `Shirriff`, `Bree Guard`). Villager role has no title.

### Speech

- Trigger: right-click (main hand, empty or any item that has no use on entities). Cooldown 60 ticks per player per NPC; spam gives nothing.
- Delivery: system chat message to the interacting player only, formatted `<Name> line`. No broadcast.
- Categories: `friendly`, `neutral`, `hostile`, `night`, `provoked`, `flee`, `trader_none` (no wares yet), `warning` (first hit), plus role-specific variants (`farmer_friendly`, `innkeeper_friendly`, ...).
- Selection order: provoked > hostile > night (only if not hostile, 30% chance) > role-specific > band generic. Random line within the category.
- Ambient lines: none at G2 (chat spam). Speech bubbles are an open question.
- Lines are lang keys `npc.lotrrevival.<race>.<category>.<n>` produced by npc pod datagen; 4 or more lines per category per race. All text written new for this repo.

### Reaction bands

The NPC computes a band for each player from `a` = the player's alignment with the NPC's faction (one faction only; allies and enemies affect alignment changes, which core handles, not the band).

| Band | Condition | Guards | Civilians | Talk | Trade | Hire |
| --- | --- | --- | --- | --- | --- | --- |
| Hostile | `a < hostile_below` | attack on sight (16 blocks, line of sight) | flee if within 8 blocks | hostile lines | no | no |
| Neutral | `hostile_below <= a < friendly_at` | ignore | ignore | neutral lines | no | no |
| Friendly | `a >= friendly_at` | ignore; defend the player if an enemy NPC attacks them within 16 blocks | ignore | friendly lines | yes, if `a >= trade_min` | yes, if `a >= hire_min` (later) |

| Threshold | Default | Source |
| --- | --- | --- |
| `hostile_below` | 0 | Per faction, from core (see Data and APIs). Some later factions (Mordor, Woodland Realm) set it above 0 |
| `friendly_at` | +10 | Per faction, from core; default if core omits it |
| `trade_min` | `friendly_at` | Per trader role, overridable in the trade spec |
| `hire_min` | +50 | Per hireable type, defined by the hiring spec |

- Bands are re-evaluated for the current target and for nearby players every 20 ticks, reading the server-side alignment attachment. No caching across ticks beyond that.
- Creative and spectator players are never targeted and always treated as at least neutral for talk.
- On Peaceful difficulty NPCs still spawn and talk but never target players.

### Provocation and penalties

- **Warning hit:** a neutral or friendly player's first melee or projectile hit on an NPC: damage applies, the NPC says a `warning` line, and no fight starts. A second hit within 200 ticks provokes it. Hostile-band players provoke on the first hit.
- **Provoked:** the NPC records `(player, expiry = now + 1200 ticks)`. A provoked guard or man civilian targets that player regardless of band; a hobbit civilian flees for 200 ticks. Each new hit refreshes the expiry.
- **Call for help:** when provoked or hit by a hostile-band player, the NPC alerts NPCs within 16 blocks whose faction is the same or allied (core relation). Guards and man civilians become provoked against the attacker; hobbit civilians flee. Alerts do not chain (an alerted NPC does not re-alert).
- **Ending:** provocation ends at expiry, when the player dies, logs out or changes dimension, or when the player is more than 32 blocks away for 200 ticks.
- **Alignment penalty:** none for hits. Killing an NPC calls core's kill-alignment API with the NPC's faction and the type's `kill_value`. Core applies the loss to that faction and the ripple to allies and enemies.

| Type class | `kill_value` |
| --- | --- |
| Civilian | 1 |
| Guard | 2 |
| Innkeeper / other trader role | 3 |

Killing an NPC of an enemy faction gains alignment through the same core call (the NPC pod reports the kill; core decides gain or loss per faction and area of influence).

### NPC vs NPC faction warfare

- Guards target, within 16 blocks and line of sight: NPCs whose faction is an **enemy** of theirs (core relation), any vanilla `minecraft:monster` category mob except creepers (guards avoid creepers by keeping 4 blocks away and letting them pass), and players in the hostile band.
- Target priority: whoever attacked me > hostile player > enemy NPC > monster. Nearest wins ties.
- Civilians never start fights. They flee from enemy NPCs, monsters within 8 blocks, and hostile-band players.
- NPCs never target NPCs of the same, allied or friend factions, and never target neutral factions unless attacked.
- At G2 `hobbit` and `bree` are allies, so in practice guards fight monsters and hostile players. Faction warfare becomes visible when enemy factions land at G3.
- Friendly fire: an NPC that hits a same-faction NPC by accident (sweep or misplaced swing) does not provoke it.

### Day/night routine

| Time (day ticks) | Structure-bound civilians | Wild civilians | Guards | Innkeeper |
| --- | --- | --- | --- | --- |
| 0-11999 (day) | wander within home radius (default 16) | wander freely, travellers walk toward a random point 32-64 blocks away | patrol within home radius (24) or freely | stays within 6 of home |
| 12000-12999 (dusk) | path to home point | keep wandering | patrol | stays within 6 |
| 13000-23999 (night) | stay within 4 of home, indoors if home is indoors | wander within 8 of current spot | patrol | stays within 6 |

- Farmers in daytime prefer standing near farmland within home radius (cosmetic; no crop harvesting at G2).
- If home cannot be reached in 600 ticks the NPC stops trying until the next dusk.

### Equipment

| Type / role | Main hand | Armor |
| --- | --- | --- |
| Hobbit villager | none | none |
| Hobbit / Bree farmer | hoe (placeholder `minecraft:wooden_hoe`) | none |
| Innkeeper | none (later: mug) | none |
| Traveller | none (later: walking staff) | leather boots |
| Hobbit Shirriff | club (placeholder `minecraft:wooden_sword`) | leather cap |
| Bree guard | sword (placeholder `minecraft:iron_sword`) | leather chest and helmet |

- Equipment drop chance: 0% at G2 (no free gear farming).
- Placeholders are swapped for core items (hobbit club, Bree sword, Bree armor) when core registers them. The loadout table is data in the npc pod so the swap is one change.

### Spawning

**Wild spawns** use an npc pod spawner, not vanilla mob categories (so NPCs don't eat the monster or creature caps and can apply faction rules).

| Thing | Value |
| --- | --- |
| Attempt interval | every 200 ticks per player in `lotrrevival:middle_earth` |
| Attempt position | random column 24-64 blocks from the player, top solid block |
| Surface | grass, dirt, path, podzol, sand; sky visible; not in water or lava |
| Light | block light + sky light >= 8 at spawn spot |
| Time | civilians and travellers: day only (0-11999). Guards: any time |
| Distance to any player | >= 24 blocks |
| Group size | civilians 1-3, guards 1-2 |
| Local cap | max 8 wild NPCs of any faction within 64 blocks of the spawn spot |
| Global cap | max 40 wild NPCs per player-loaded area (counted like vanilla per-chunk caps) |
| Inside structures | wild spawner skips columns inside a structure bounding box |

Spawn tables are npc pod datapack JSON keyed by biome id: `data/lotrrevival/lotrrevival/npc_spawns/<biome>.json`, each entry `{type, weight, group_min, group_max, roles?}`. G2 tables:

| Biome | Entries (weight) |
| --- | --- |
| `lotrrevival:shire` | `hobbit` 10, `hobbit_shirriff` 2 |
| `lotrrevival:bree_land` | `bree_man` 8, `bree_hobbit` 4, `bree_guard` 2 |

Biomes without a table spawn no NPCs. Travellers spawn only within 8 blocks of a block in tag `lotrrevival:road` if the world pod provides it; otherwise anywhere in the biome.

**Structure-bound spawns.** Structures from the world pod place NPC spawn markers (convention: a structure block in DATA mode with metadata `lotrrevival:npc <type> [role=<role>] [count=<n>] [home=<radius>]`). When the structure generates, the npc pod turns each marker into a **spawn anchor** stored in the chunk's attachment and spawns `count` NPCs (default 1) with that home point and radius (default 16).

| Thing | Value |
| --- | --- |
| Anchor check | every 600 ticks while the chunk is loaded |
| Respawn | if fewer than `count` living NPCs belong to the anchor, spawn one |
| Respawn conditions | no player within 16 blocks and none with line of sight; 24000 ticks (1 day) since that NPC died |
| Light/time | ignored for anchors |
| Persistence | anchor NPCs never despawn |

### Despawning

- Wild NPCs follow vanilla monster rules: instant despawn when no player within 128 blocks; after 600 ticks with no player within 32 blocks, 1/800 chance per tick.
- Never despawn: structure-bound NPCs, name-tagged NPCs, NPCs that are hired (later), NPCs with a trade in progress, NPCs currently targeting or provoked.

### Death drops

| Type | Loot (loot table, datagen) |
| --- | --- |
| Hobbit civilians | 0-1 bread (placeholder food), looting +1 |
| Man civilians | 0-1 bread, looting +1 |
| Guards | 0-1 bread |
| Any, killed by player | 0-2 `lotrrevival:silver_coin` once core registers it (currency placeholder) |

Equipment does not drop. XP only when killed by a player.

### Trading hook

- Trader roles: innkeeper at G2. Later specs add more.
- Interaction order on right-click: if trader and player band is friendly and `a >= trade_min` and not provoked: post `NpcInteractEvent(kind = TRADE)`; if nothing handles it, say a `trader_none` line. Otherwise talk.
- Sneak-right-click always talks, so players can hear trader lines.
- Currency: `lotrrevival:silver_coin` (core item). The trade spec owns prices, stock and the screen.

### Hiring hook (interface only)

A hireable NPC exposes: hire requirement (`hire_min` alignment, coin cost), an owner (player UUID or none), a command state (follow, guard position, none), and a flag for whether it currently counts as hired. The base only guarantees: hired NPCs never despawn, never target their owner (even if the owner's band drops to hostile), still pick other targets by their own faction's rules, and carry owner and state through save/load. Everything else belongs to the hiring spec.

## Edge cases

- **Multiplayer:** bands are per player. One NPC can be friendly to A and hostile to B; it targets B only. Speech goes only to the interacting player. Provocation is per player. Nameplate color is computed client-side from the viewer's own synced alignment.
- **Band change mid-fight:** if a non-provoked target's band rises out of hostile (quest reward, command), the NPC drops the target at the next 20-tick check.
- **Player death, logout, dimension change:** clears provocation against that player. NPCs don't follow through portals.
- **NPC through portal:** NPCs can't use portals (avoids faction NPCs leaking into the Overworld).
- **Unloaded chunk with anchor:** no respawn while unloaded; on load the next 600-tick check catches up with at most one spawn per anchor per check.
- **Killing an NPC with no player involved** (lava, monster, another NPC): no alignment change. Kills by a player's tamed wolf or projectile count as the player's kill.
- **Killing a hostile NPC as a friendly player** (rogue member of your own faction attacking you): still costs alignment; there are no "rogue" members at G2.
- **Name banks missing or empty after a bad datapack:** fall back to race-name-only ("Hobbit") and log one warning.
- **Spawn table refers to an unknown entity id:** skip entry, log one warning.
- **Commands:** `/summon lotrrevival:bree_man` gives a wild NPC with random role (not innkeeper) and a fresh name. Spawn eggs behave the same.
- **Interaction with other systems:** alignment (core) is read, never written except via the kill API; trading and hiring (progression and npc later) plug in through the event and hireable interface; structures (world) place markers only.

## Data and APIs

**Needed from core (`specs/core/core-apis.md`):**

1. Faction datapack registry key and lookup by id; shared constants for `lotrrevival:hobbit` and `lotrrevival:bree`.
2. Faction relation query between two factions (at least ally, friend, neutral, enemy) with helpers "is allied or friend" and "is enemy".
3. Per-faction thresholds `hostile_below` (default 0) and `friendly_at` (default +10) as optional faction JSON fields, or confirmation that npc owns them (see Open questions).
4. Server-side read of a player's alignment with a faction from the alignment attachment.
5. Client-side read of the local player's alignments (synced), for nameplate colors.
6. A kill-alignment API: "player P killed a member of faction F worth V", where core applies the loss or gain, ally/enemy ripple and area-of-influence rules, and fires the alignment change event.
7. The alignment change event (npc uses it in GameTests and to drop targets early; optional at runtime).
8. Rank lookup by alignment value (optional, for speech flavor later).
9. Access through `ModRegistries` to register entity types, attributes, data attachment types (chunk attachment for spawn anchors), and the npc reload listeners (name banks, spawn tables, loadouts).
10. Spawn egg items for the 5 entity types (core owns items) or permission for npc to register them via `ModRegistries.ITEMS`.
11. Item `lotrrevival:silver_coin` (currency placeholder) and later faction weapons and armor.
12. Entity type tag keys in core: `lotrrevival:npcs`, `lotrrevival:hobbits`, `lotrrevival:men`.
13. A datagen hook so npc providers (lang, loot tables, entity tags, spawn egg models) run from `DataGenerators`.
14. The dimension key `lotrrevival:middle_earth`.

**Needed from world (`specs/world/dimension-and-mapgen.md`):** biome ids `lotrrevival:shire` and `lotrrevival:bree_land`; the structure marker convention above (or an agreed alternative); optional block tag `lotrrevival:road`.

**Owned by npc (this spec):**

- Entity types and attributes listed above; renderers and models under `client/npc/`.
- Synced entity fields: role, gender, name (string), name-roll seed. Persisted: those plus home pos/radius, anchor id, hire data (later).
- Datapack folders: `npc_names/`, `npc_spawns/`, `npc_loadouts/` under `data/<ns>/lotrrevival/`.
- Chunk attachment `lotrrevival:npc_spawn_anchors` (list of anchor pos, type, role, count, home radius, NPC UUIDs, last-death times).
- `NpcInteractEvent` on the NeoForge event bus (kinds: TALK, TRADE, HIRE), cancelable, posted by the npc pod for other pods to handle.
- Hireable interface (fields above).
- No packets at G2 (speech uses chat).

## Out of scope

- Trade screen, prices, stock, currency exchange (progression trader spec).
- Hiring, commands, unit leveling, warhorns (npc hiring spec).
- Mounts, ranged combat, weapon stat tables (npc combat spec).
- Invasions, mini-quests, Red Book (later specs).
- Hobbit children, marriage, NPC farming or crop harvesting, NPC sleeping in beds.
- Races other than hobbits and men; Rangers of the North and bandits.
- Speech bubbles above heads and voiced audio.

## Acceptance tests

- [ ] GameTest: a player with `hobbit` alignment -5 near a `hobbit_shirriff` is targeted within 40 ticks; with alignment 0 it is not targeted after 100 ticks.
- [ ] GameTest: a hobbit civilian near a player at -5 alignment moves away from the player (distance increases by at least 3 blocks within 60 ticks).
- [ ] GameTest: two players, A at +20 and B at -20 `bree`; a `bree_guard` targets B and never A.
- [ ] GameTest: a friendly player hits a `bree_man` once: no target set; hits again within 200 ticks: target set to that player, and a `bree_guard` within 16 blocks also targets the player.
- [ ] GameTest: provocation clears after 1200 ticks without further hits.
- [ ] GameTest: a player kills a `hobbit`; core's kill-alignment API is called once with faction `hobbit` and value 1 (verify via the alignment change event).
- [ ] GameTest: a `bree_guard` attacks a zombie within 16 blocks; a `bree_man` civilian flees from it.
- [ ] GameTest: a `hobbit_shirriff` and a `bree_guard` (allied factions) placed together do not target each other for 200 ticks.
- [ ] GameTest: a structure anchor with `count=2` respawns a killed NPC after the 1-day timer when no player is near, and not before.
- [ ] GameTest: a wild NPC with no player within 128 blocks is removed; an anchor NPC is not.
- [ ] GameTest: name persists across save/load (write NBT, reload, compare name and role).
- [ ] GameTest: right-click on an innkeeper by a friendly player posts `NpcInteractEvent(TRADE)`; by a neutral player posts TALK.
- [ ] Playtest: walk through the Shire and Bree-land by day and night; NPC density feels lived-in but not crowded (target 3-8 NPCs in view in a village area).
- [ ] Playtest: names and lines read well and nothing repeats annoyingly in a 10-minute visit.

## Open questions

1. Do `hostile_below` and `friendly_at` live in core's faction JSON (preferred) or in an npc-owned per-faction settings file?
2. Speech: chat only, or also a short overhead bubble (needs a client packet)? Default chat only.
3. Should a neutral player be able to trade at a markup (the original lets neutral players do some interactions) or is trade friendly-only? Default friendly-only.
4. Should hits on friendly NPCs ever cost alignment (e.g. -0.5 per provoking hit) to stop "beat but don't kill" griefing? Default no.
5. Spawn eggs: core-owned items or npc-registered via `ModRegistries.ITEMS`?
6. Structure marker format: DATA structure blocks (proposed) vs a dedicated invisible marker block from core. Needs world and core sign-off.
7. Equipment drop chance: keep 0%, or a small chance (e.g. 5%) once faction gear exists to reward warfare.
8. One model per race with gender only in names, or separate female models at G3?

## Tickets

Filled in by the lead once the spec is approved.
