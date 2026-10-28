# Territory and conquest

- **Pod:** world
- **Lead:** Opus 2
- **Status:** draft (priority: the maintainer asked for this system to be prioritised)
- **Depends on:** `specs/world/dimension-and-mapgen.md` (canvas, biome map at 128 blocks/px, origin pixel (810, 730), projection), `specs/world/waypoints-travel.md` (waypoint regions, fixed waypoints, road polylines, map data pipeline), `specs/core/core-apis.md` (factions, relations, pledge, kill path and `FactionKillEvent`, `LandProtection`, payload and attachment rules, `/lotr` root), `specs/npc/npc-base.md` (spawn tables), `specs/npc/nemesis.md` (PR #182: world events and nemesis deaths emit control deltas through `core.api.TerritoryControl`), the progression map page. **Missing dependency:** a World structure spec (see Dependencies).
- **Sources:** behavior notes `docs/reference/legacy/territory.md` (conquest, in our own words), `factions-alignment.md` sections 6 and 8, `combat-trade.md` section 7, `biomes.md` "Spawning, conquest and invasions". Every name and line of text here is our own.
- **Beyond parity:** Legacy had conquest (a per-faction number on a square grid that changes NPC spawns) but no ownership. Provinces, owners, flips, borders, structure capture, road danger and invasion scaling are our own design. Each rule below is marked **[Legacy]** or **[Ours]**.

## Summary

Middle-earth is at war, and the land shows it. Two layers carry this:

1. **Conquest [Legacy].** A grid of 1,024 x 1,024-block squares stores a strength per faction. Pledged players raise their faction's strength by killing its enemies; strength spreads to nearby squares, cleanses the enemy's strength first, decays slowly when left alone, and changes which NPCs spawn. Same numbers as Legacy.
2. **Provinces [Ours].** The map is divided into named provinces built from our waypoint regions. Each province has an **owner** faction and a **control** value from 0 to 1,000. Kills, world events, nemesis deaths and invasions push control up or down. When an enemy breaks control to 0 and has built enough pressure, the province **flips**: borders move on the map, outposts and castles swap banners and garrisons, spawn tables shift to the occupier, roads grow more dangerous for the losers and invasions follow the front line. Strongholds never fall, provinces drift back to their rightful owner when nobody fights for them, and player land is never touched.

Players care because their fighting changes the map they look at, and because the world fights on without them.

## Player-facing behavior

1. **The map has borders.** The Map page (Progression) shows every province tinted in its owner's colour with borders between owners. Contested provinces are hatched. Hovering a province shows its name, owner, control bar, and the top challenger if any. Everyone sees borders; no pledge needed.
2. **Kills matter, as in Legacy.** A player pledged to a faction who kills one of its enemies sees the conquest gain next to the alignment gain. Every 50 points in a square, pledged players nearby get a notice. The same kill moves the province's control.
3. **The world moves on its own.** World events near players (caravans, warband clashes, war camps, beacons) and nemesis deaths push control. A chat line tells players of the involved factions when a province becomes contested.
4. **A province falls.** When a province flips, players in Middle-earth who are pledged to, or hold +100 or more with, the old or new owner (or their allies) read a chat line ("Mordor has taken North Ithilien from Gondor"). Players inside the province see a title. The map redraws. Over the following minutes the outposts and castles of the province fly the new owner's standard and are manned by its soldiers, the spawn mix changes toward the occupier, its roads become dangerous for the old owner's friends, and the old owner starts counter-invasions.
5. **Then it drifts back.** An occupied province slowly loses control and its rightful owner gains pressure, so unless the occupier keeps fighting, the land returns. Strongholds (Minas Tirith, Barad-dûr, Rivendell, Hobbiton...) never change hands.
6. **Legacy conquest map.** A pledged player can switch the Map page to a conquest heatmap for one faction at a time, with Legacy's colouring and view rules.
7. **Player land is safe.** Banner-protected land and anything a player built is never changed by a flip.

## Rules and numbers

### 1. Conquest grid [Legacy]

| Thing | Value | Notes |
| --- | --- | --- |
| Square | 8 x 8 biome px = 1,024 x 1,024 blocks | Our canvas has Legacy's scale and origin, so squares line up with Legacy's: square index = floor((biome px) / 8) counted from the image's top-left. 400 x 500 squares |
| Outside the canvas | one dummy square that never stores anything | |
| Stored | strength >= 0 per faction (playable alignment factions only), last-change time per square | Only non-empty squares are stored |
| Kill award | victim's kill value x world conquest rate x biome conquest gain rate at the **killer's** position | Kill value from `FactionKillEvent.getBaseValue()` after other listeners |
| Who earns | players pledged to a faction that gains alignment from the kill (relation `enemy`/`mortal_enemy` to the victim's faction) | Unpledged: nothing. Civilian kills: nothing for factions that disapprove of civilian kills |
| Hired-unit kill | x0.25 | Core's hire crediting (owner within 64 blocks) decides if it counts |
| Opt-out | per-player toggle "my kills make conquest", default on | |
| World conquest rate | 1.0 default, 0-100, per world | Set by command, saved with territory data |
| Biome gain rate | 1.0 default; 0.2, 0.5, 0.75 for listed biomes | Biome tags `#lotrrevival:conquest_rate/slow` (0.2), `/hard` (0.5), `/firm` (0.75). Legacy lists in `docs/reference/legacy/territory.md` section 2; pilot: `shire`, `shire_woodlands`, `shire_marshes`, `shire_moors`, `white_downs` are `slow` |
| Radial spread | every square with centre distance d <= 3.5 squares gets award x (1 - d / 3.5) | 37 squares |
| Cleansing | per square: if the victim's faction has strength there, reduce it by the square's share; the killer's faction gains nothing in that square | |
| Invasion defeated | 50, split evenly between contributors pledged to an enemy of the invader, applied as a kill award against the invader | Sent by the invasion system (section 6) |
| Decay | after 72,000 ticks (1 h) without a change, every faction in the square loses 1 per further 72,000 ticks | Any change restarts the clock for the whole square, locking in decay so far |
| Notices | crossing a multiple of 50 (or reaching 0) sends a notice to players in the same square within 200 blocks of the killer, pledged to that faction (or, when cleansing, to one of its enemies) | |
| Statistic | conquest earned per faction, per player | Counter for Progression |

### 2. Provinces [Ours]

A province is a set of grid squares. Provinces are built by datagen from the map, so they follow geography at square resolution.

| Thing | Value | Notes |
| --- | --- | --- |
| Square to region | each square belongs to the waypoint region with the most land pixels in it (ties: alphabetical region id). Squares with no land pixel belong to no province | Water, shore and rivers belong to no region (waypoints spec) |
| Seeds | every fixed waypoint of the region is a seed | |
| Square to province | nearest seed of its own region (square centre to waypoint, straight line); ties: alphabetical waypoint id | A region without fixed waypoints is one province |
| Minimum size | provinces with fewer than 4 squares merge into the neighbouring province of the same region they share most edges with | |
| Id and name | the seed waypoint's id and name (or the region's when the region is one province) | |
| Default owner | the seed waypoint's faction if it has one; otherwise the faction with the largest normal spawn share summed over the province's land pixels; otherwise none (wild) | Overridable in data |
| Tier | `stronghold`, `heartland`, `borderland` or `wild` | Default: wild if no owner; heartland if any biome in it is tagged `faction_home/<owner>`; otherwise borderland. Strongholds are listed by hand |
| Default control C0 | stronghold 1,000 (locked), heartland 750, borderland 400, wild 0 | Config |
| Adjacency | provinces sharing a square edge, across water too if the gap is at most 2 squares | Used by invasions and the UI |

**Pilot strongholds:** `hobbiton` (hobbit) and `bree` (bree). Planned later: Mithlond, Thorin's Halls, Rivendell, Caras Galadhon, Thranduil's halls, Erebor, Dale, Edoras, Helm's Deep, Minas Tirith, Dol Amroth, Isengard, Barad-dûr, Minas Morgul, Dol Guldur, Mount Gundabad, Carn Dûm, Umbar and the capital of each Harad and Rhûn faction. One per faction at least.

### 3. Province state and deltas [Ours]

Each province holds: owner (faction or none), control C (0-1,000), a **pressure** P per challenging faction (0-1,000), time of the last flip, and per-hour rate buckets.

A delta is (faction F, amount a, source, optional player). Applied in this order:

| F's relation to the owner O | a > 0 | a < 0 |
| --- | --- | --- |
| F = O, or `ally`/`friend` | C += a (max 1,000) | C -= abs(a) |
| `enemy` / `mortal_enemy` | C -= a, P[F] += a | P[F] -= abs(a) |
| `neutral` | ignored | ignored |
| province is wild (no owner) | P[F] += a | P[F] -= abs(a) |

| Source | Delta | Notes |
| --- | --- | --- |
| Player kill | the conquest gain or cleanse in the killer's centre square (section 1), for the pledge faction | So a warrior kill is +2 (x biome rate). Hired units x0.25 included |
| World event (`lotrrevival:world_event/<type>`) | catalogue amount x 10 (`event_point_scale`) | Nemesis spec already applies x1.5 player-decided and x0.5 off-screen before reporting |
| Nemesis death (`lotrrevival:nemesis_death`) | amount x 10 | i.e. -10 (Marked) to -50 (Overlord) for the nemesis's faction |
| Invasion defeated (`lotrrevival:invasion_defeated`) | -50 for the invader | Plus the Legacy grid award to contributors (section 1) |
| Invasion unopposed (`lotrrevival:invasion_unopposed`) | +40 for the invader | An invasion that ends because no player fought it (definition in the invasion spec) |
| Admin | any | Bypasses caps |

**Pressure fades** when the owner holds: see Drift.

**Flip.** After a delta, if C = 0 the province checks its challengers. A challenger's score is its own P plus one third of the P of each of its allies that is also an enemy of the owner. The top score flips the province when it is at least **250** (`flip_pressure`). Then: owner := that challenger, C := **250** (`flip_start_control`), all pressures reset to 0, last-flip time is now. A wild province is claimed the same way (C was already 0). If C is 0 but nobody reaches 250, the province stays at 0 and is "breaking".

**Contested** = C < 200, or the top challenger's score >= C / 2. Shown on the map; spawns and danger rules use it.

### 4. Safeguards [Ours]

| Safeguard | Value | Notes |
| --- | --- | --- |
| Strongholds | never flip; C never drops below 500 | Still take deltas (so challengers' pressure shows), and pressure there fades fast (x3 drift) |
| Flip cooldown | 3 h of server time (216,000 ticks) after a flip, C cannot go below 1 | Stops ping-pong |
| Province rate cap | the sum of abs(delta) applied to a province in any rolling 60 min is at most 250 | Twelve 5-min buckets; excess is dropped. Drift and admin are exempt |
| Player cap | one player's deltas to one province in a rolling 60 min are at most 100 | Kills and player-decided event bonuses are attributed to the player. Means a lone player needs about 4 h of focused fighting to break a borderland province without event help, and at least 3 players to hit the province cap |
| Faction loss cap | a faction loses at most 2 provinces per 24 h of server time; beyond that its C floor is 1 | Protects factions with no active players on big servers |
| Absent-defender slowdown | optional, `absent_defender_factor` default 1.0 (off) | When set below 1, player-attributed enemy deltas are scaled by it while no player pledged to the owner or its allies is online |
| Drift home | see Drift | Land returns to its rightful owner when left alone |
| Land and builds | territory never places, breaks or replaces blocks except the structure standards in section 5, and never inside banner-protected land | |
| Classic world type, conquest disabled | no grid and no provinces; holders answer defaults | Config `territory.enabled` |

**Drift**, every 24,000 ticks (one in-game day, 20 min):

| State | Change per drift step |
| --- | --- |
| Owner = default owner, C below C0 | C += 20 (to C0) |
| Owner = default owner, C above C0 | C -= 10 (to C0) |
| Owner = default owner | every P -= 20 (to 0); x3 in strongholds |
| Owner != default owner (occupied) | C -= 10; P[default owner] += 10. With nobody fighting, an occupied province flips back after about 25 steps (8 h of play) |
| Wild, claimed | C -= 10; when it reaches 0 the province is wild again (no owner) |
| Wild, unclaimed | every P -= 20 |

Drift runs on server time only, so a single-player world does not change while closed.

### 5. Effects of ownership [Ours unless marked]

**NPC spawns (NPC pod applies, World answers queries).**

| Rule | Value |
| --- | --- |
| Conquest groups [Legacy] | Each biome's spawn table gets faction groups with base weight and sensitivity (default 1.0); effective weight = base + round(0.2 x strength x sensitivity), where strength = the faction's square strength + 1/3 of each ally's (allies without their own group in the biome). Groups with base 0 only spawn with conquest. Entries can need a minimum strength (Legacy steps 25, 50, 100, 150, 500) |
| Conquest spawns [Legacy] | an NPC picked from the extra (conquest) weight ignores the darkness rule (trolls only if sun-proof) and the "near an enemy garrison" block; banner protection still applies |
| Occupation floor | in an occupied province, the owner's strength for spawn purposes is at least **200** in every square (`occupation_strength`) |
| Occupier with no group in the biome | its `occupation` spawn list (one per faction, warriors and patrols only, NPC pod data) is used as a base-0 group |
| Displaced owner | the default owner's groups in an occupied province have weight x0.25 (min 1 if base > 0) |
| Contested | no change beyond the grid |

**Holdable structures.** Outposts, castles, permanent camps, watchtowers and towers (structure venue kinds `outpost`, `castle`, `camp`, `watchtower`, `tower`) belong to a faction when generated. Bridges, ruins, hideouts and villages are not held.

| Rule | Value |
| --- | --- |
| Holder | a holdable structure's holder is its province's owner if the owner is a faction that can garrison that structure kind (data per faction), else its generation faction |
| Swap trigger | province flip (and flip-back) |
| What swaps | only the structure's **standard** blocks (faction standard banners placed by our structure templates at recorded positions) and its garrison spawner's faction. Walls, chests, decoration: never |
| Block check | a standard is replaced only if the block at its recorded position is still the standard we placed. Anything else there is left alone |
| Protection check | no swap at all while any part of the structure's bounds is banner-protected (`LandProtection.protectedAt`); it stays frozen with its old holder until protection ends |
| Loaded chunks | swapped at most 2 structures per tick, nearest players first |
| Unloaded chunks | swapped when the chunk loads (stored holder epoch older than the province's) |
| Old garrison | when the swap runs, old-holder garrison NPCs inside the bounds that are not hired, named or nemeses are removed (no drops, no alignment); the spawner then produces the new holder's garrison |
| Strongholds | their structures never swap |

**Roads [Ours].** Hostile NPC spawn attempts within 24 blocks of a road centreline are multiplied for the nearest player:

| Province state, from that player's side | Multiplier |
| --- | --- |
| Owner is the player's pledge faction or its ally/friend (unpledged: alignment >= 0 with the owner), not contested | 0.75 (patrolled) |
| No relation either way | 1.0 |
| Contested | 1.5 |
| Owner is an enemy of the player's pledge (unpledged: alignment < 0 with the owner) | 2.0 |

The same multiplier applies to bandit spawns, and world events use it to weight road ambushes and to forbid good-led road events (patrols, caravans) on roads of an evil-held province.

**Invasions [Ours].** For an invading faction F in a province (multipliers on the biome's invasion chance; Legacy's chance tiers otherwise unchanged):

| Case | Multiplier |
| --- | --- |
| F is the owner or its ally/friend | 0 (no invasions by the owners) |
| F is a challenger | x(1 + P[F] / 250), at most x3 |
| Province contested and F is an enemy of the owner | at least x1.5 |
| A neighbouring province is held by F | x1.25 |
| Counter-invasion: province occupied, F is the displaced default owner | F's invasion is added at the uncommon tier (30% per hour) even if the biome does not list it |

Banner protection still blocks invasions. The eligible-player rule (alignment below 0 with the invader) stays.

**Waypoints.**

| Rule | Value |
| --- | --- |
| Conquest unlock [Legacy] | an enemy faction's fixed waypoint is usable by a player whose pledge faction holds >= 500 grid strength in the waypoint's square, if that faction has a spawn group in the square's biomes |
| Occupation [Ours] | a fixed waypoint that belongs to a faction uses its **province owner** as its faction while the province is occupied. Gondor players lose Osgiliath's waypoint when Mordor holds it; Mordor players gain it |

### 6. Inputs from other pods

- **Nemesis and world events** (PR #182): `report` as defined there. Sources `lotrrevival:world_event/<type>`, `lotrrevival:nemesis_death`, `lotrrevival:invasion_leader` (treated like nemesis death). Events read `contested` for site weighting.
- **Invasions** (invasion spec, not written): report `lotrrevival:invasion_defeated` with the contributor list (for the Legacy grid award) and `lotrrevival:invasion_unopposed`. Read `invasionMultiplier` and `counterInvaders`.
- **Kills**: World listens to core's `FactionKillEvent` (after it is not cancelled). No core change needed.

### 7. Notifications

| When | Who | What |
| --- | --- | --- |
| Square crosses a multiple of 50 [Legacy] | see section 1 | chat notice with faction and value |
| Province becomes contested | players in it pledged to the owner side | chat line, once per province per 30 min |
| Flip | players in Middle-earth pledged to, or at +100 or more with, the old or new owner or their allies | chat line; title for players inside the province |
| Strong drift-back | same as flip | flip chat line naming the default owner as liberator |

Text is ours, in lang via World's lang provider.

## Edge cases

- **Multiplayer:** state is shared by the whole server. Caps (section 4) stop one player or one guild from turning the map over in an evening. Players of opposing sides push the same numbers; the per-player cap applies to each. Two flips in one tick on one province cannot happen (flip resets C to 250).
- **Single player:** no other players, so events, drift and invasions do most of the moving. Numbers aim for a borderland flip in about 2-3 hours of focused play with event help.
- **Death, logout, dimension change:** nothing is lost; territory is world data. Leaving Middle-earth stops kill awards. A logged-out player's opt-out flag persists (player attachment).
- **Pledge broken mid-fight:** later kills credit nothing until re-pledged. Earlier deltas stand.
- **Faction removed by a datapack:** its grid strength and pressure are dropped on load; provinces it owned revert to their default owner (or wild). A default owner that no longer exists makes the province wild.
- **Province layout changed by a datapack or a map repaint:** state for unknown provinces is dropped; new provinces start at their defaults. Grid squares are fixed by the canvas and keep their data.
- **Structures generated after a flip:** a newly generated holdable structure takes the current holder at generation.
- **Structure partly in two provinces:** its province is the one holding its centre.
- **Banner placed after a swap:** later swaps skip that structure while it is protected.
- **Peaceful:** grid awards from kills still work (no hostile NPCs, so rare); invasions and events off; drift continues.
- **Utumno and other dimensions:** no grid, no provinces.
- **`/lotr conquest rate 0`:** kills give nothing to grid or provinces; events still move provinces.
- **Interaction with Areas of influence:** separate system (core's reserved `InfluenceProvider`); not changed here. A later spec may let the province owner feed it.

## Data and APIs

### World data (World pod)

| Data | Where | Notes |
| --- | --- | --- |
| Province layout | `data/lotrrevival/lotrrevival/territory/<id>.json` (datapack registry `lotrrevival:territory`, server only) | `{ "name": <component>, "region": <region>, "default_owner": <faction>?, "tier": "...", "squares": [[x, z, runLength], ...] }`. Generated by datagen from the map, waypoints and stronghold list. Hand overrides in `src/main/resources` win |
| Strongholds | `src/main/resources/data/lotrrevival/lotrrevival/territory_strongholds.json` | `{ "strongholds": [<province id>, ...] }`, read by datagen |
| Biome gain rates | biome tags `#lotrrevival:conquest_rate/slow|hard|firm` | World datagen |
| Holdable kinds per faction | `data/lotrrevival/lotrrevival/territory_garrisons/<faction>.json` | Which venue kinds a faction can garrison (e.g. Hobbits: none; Mordor: outpost, camp, watchtower, tower, castle). Data for the structure spec |
| Territory state | `SavedData` `lotrrevival_territory` on the Middle-earth level | Version, conquest rate, provinces (owner, C, pressures, last flip, buckets, holder epoch, faction loss log), non-empty squares (strengths, last change) |
| Held structures | `SavedData` `lotrrevival_territory_holds` | Per holdable structure: id, bounds, centre, province, generation faction, standard positions, holder, epoch. Filled when the structure generates (structure spec) |
| Opt-out | player attachment `lotrrevival:conquest_kills` (boolean, default true, copied on death) | |

### Core holders (requested from core, `pod:core`)

`core.api.TerritoryControl` (server, `LandProtection` pattern; World registers the backend; every default is neutral):

| Method | Default | Meaning |
| --- | --- | --- |
| `report(level, pos, faction, amount, source)` | no-op | As fixed by the nemesis spec. Amount in event points; World scales |
| `reportInvasionDefeated(level, pos, invader, contributors)` | no-op | Contributors as player UUIDs |
| `contested(level, pos)` | false | |
| `owner(level, pos)` | empty | Province owner at the position |
| `control(level, pos)` | 0 | 0-1,000 |
| `conquestStrength(level, pos, faction)` | 0 | Grid strength incl. ally third and occupation floor, for spawn weighting |
| `spawnWeightFactor(level, pos, faction)` | 1.0 | 0.25 for the displaced owner |
| `roadDanger(level, pos, player)` | 1.0 | Section 5 |
| `invasionMultiplier(level, pos, faction)` | 1.0 | Section 5 |
| `counterInvaders(level, pos)` | empty | Factions to add at uncommon tier |

`core.api.TerritoryClient` (client, `WaypointClient` pattern; World's client code registers it; Progression's map reads it): `provinces()` (id, name, default owner, tier, adjacency), `provinceAtSquare(x, z)`, `state(id)` (owner, control, contested, top challenger and score), `version()` (bumps on change), `requestConquest(faction)`, `conquestGrid(faction)` (snapshot or empty, with a "not allowed: unpledged / needs rank X" reason).

`core.event.TerritoryFlipEvent(level, province, oldOwner, newOwner, cause)` posted by World after a flip (Progression achievements, NPC reactions).

`LandProtection.protectedAt(level, pos)`: true if any banner protects the position (default false). New default method on the existing holder.

Squares are 1,024 blocks; the conversion lives with core's map projection constants.

### Payloads (World defines, registered the way other World payloads are)

| Payload | Direction | Content | When |
| --- | --- | --- | --- |
| `world/territory_layout` | S2C | province table plus square-to-province index, run-length by row, with a hash | login, dimension entry if the client lacks that hash, datapack reload. Split into parts of at most 256 KB |
| `world/territory_state` | S2C | per province: owner index, control, contested flag, top challenger and score | full on login and dimension entry; changed provinces only, batched every 5 s, max 256 per payload |
| `world/conquest_request` | C2S | faction | at most 1 per 2 s per player |
| `world/conquest_grid` | S2C | faction, squares with strength > 0 (x, z, strength), or a refusal reason | reply; Legacy view rules: pledged only; own faction, allies, enemies; others need the rank two steps above the pledge rank |

### Commands (op level 2 unless noted)

- `/lotr conquest set|add|radial <faction> <amount> [x z]`, `/lotr conquest clear [x z]`, `/lotr conquest rate [value]` [Legacy]; `/lotr conquest kills on|off` (level 0, own toggle).
- `/lotr territory info [x z]` (level 0), `/lotr territory list`, `/lotr territory set_owner <province> <faction|none>`, `set_control <province> <0-1000>`, `pressure <province> <faction> <amount>`, `reset <province|all>`.

### Config (`lotrrevival-world-server.toml`, `[territory]`)

`enabled` (true), `default_conquest_rate` (1.0), `square_decay_grace_ticks` (72000), `square_decay_per_hour` (1), `spread_radius` (3.5), `hired_share` (0.25), `event_point_scale` (10), `invasion_defeated` (50), `invasion_unopposed` (40), `control_max` (1000), `default_control` per tier (1000/750/400/0), `stronghold_floor` (500), `flip_pressure` (250), `flip_start_control` (250), `flip_cooldown_ticks` (216000), `province_cap_per_hour` (250), `player_cap_per_hour` (100), `faction_losses_per_day` (2), `absent_defender_factor` (1.0), drift period and amounts (24000; +20/-10/-20/-10/+10), `contested_below` (200), `occupation_strength` (200), `displaced_weight` (0.25), road multipliers (0.75/1.0/1.5/2.0), road band (24), invasion multipliers, `structures_change_hands` (true), `swap_per_tick` (2).

### Performance

- Square to province: one short array of 400 x 500 (400 KB) built at load. Square strengths: hash map of non-empty squares; decay computed on read and baked on change (no ticking per square). Housekeeping drops empty squares every 6,000 ticks, staggered.
- Spawn, road and invasion queries are O(1) lookups; safe to call per spawn attempt.
- Drift: one pass over provinces (about 1,500 on the full map) every 24,000 ticks.
- Sync: deltas only, batched; heatmap on request with a rate limit.
- Structure swaps: budgeted per tick, queue saved with the data.
- Saves: dirty flag only.

## Out of scope

- Writing the structures themselves, their standards and garrison spawners (World structure spec, see Dependencies).
- Villages changing hands, sieges of strongholds, player-claimed provinces, taxes or tribute.
- Areas of influence (core's reserved `InfluenceProvider`).
- The map screen drawing (Progression) and the invasion system itself (NPC).
- Legacy's command-table block as the only way to see conquest (here the heatmap is a Map page mode; see Open questions).

## Dependencies

1. **World structure spec (`specs/world/structures.md`), not written yet.** Needed for: the venue kinds (outposts, castles, camps, bridges, ruins, watchtowers, towers, hideouts, villages) with their tags `#lotrrevival:event_venues/<kind>`, faction structure sets per region, placed faction standards at recorded positions, garrison spawners (with the NPC pod), the held-structure index filled at generation, and the `core.api.StructureVenues` backend the nemesis spec also needs. **Proposal: World writes it next**, before any ticket in section "Structures" below starts.
2. **Invasion spec (NPC, S8), not written.** Needs the invasion outcome reports and must read the multipliers. Until then provinces move only from kills, events and nemesis deaths.
3. **Nemesis spec (PR #182)**: `report` signature and sources must match; no other coupling.
4. **Progression map page**: draws borders and heatmap from `TerritoryClient`.

## Acceptance tests

The GameTest server has no Middle-earth level, so the territory service takes a level-independent layout and clock. Tests build a small fixture layout (a few provinces over a 10 x 10 square grid) on the test level through a test hook on the backend.

- [ ] Unit (build): province generation on a fixture map: squares assigned to the majority region, nearest seed, provinces under 4 squares merged, water-only squares unassigned, ids and default owners as specified; deterministic output.
- [ ] GameTest: kill award, radial shares (1.0 / 0.714 / 0.429 / 0.143 by distance), cleansing before gain, hired x0.25, unpledged and opted-out players give nothing, biome rate applied.
- [ ] GameTest: decay: nothing for the first 72,000 ticks, then -1 per 72,000 for every faction; a change restarts the clock.
- [ ] GameTest: invasion-defeated award of 50 split between pledged contributors.
- [ ] GameTest: delta table: owner side raises C, enemies lower C and build P, neutral ignored, wild provinces only gain P.
- [ ] GameTest: flip at C = 0 with score >= 250 (allies count one third), C becomes 250, pressures reset, `TerritoryFlipEvent` fires; no flip below 250.
- [ ] GameTest: strongholds never below 500 and never flip; flip cooldown keeps C >= 1; province cap 250/h and player cap 100/h drop the excess; faction loss cap.
- [ ] GameTest: drift steps for each state in section 4; an untouched occupied province flips back.
- [ ] GameTest: `report` with each nemesis source maps to the right delta; no backend = no error (holder default).
- [ ] GameTest: spawn queries: occupation floor 200, displaced factor 0.25, Legacy group weight formula and minimum-strength entries.
- [ ] GameTest: road and invasion multipliers for each row; counter-invaders listed.
- [ ] GameTest: SavedData round trip; unknown faction and unknown province dropped on load.
- [ ] GameTest (after the structure spec): standard swap replaces only unchanged standards, skips protected structures, removes the old garrison but not hired or named NPCs; lazy swap on chunk load.
- [ ] Unit: layout and state payload round trip; layout hash stable.
- [ ] Playtest: pledge to the Hobbits, fight the orcs that `/lotr territory pressure` brought in, watch the Shire border hatch, flip a pilot province with `/lotr territory`, see the map, waypoint and spawn changes, wait for drift-back; check that a bannered build in the province is untouched.

## Proposed tickets (not opened)

Priority: **high** (maintainer request). Gate: foundation in **G3** next to the map GUI, effects needing structures and invasions in **G4**. Order is the dependency order.

| # | Pod | Ticket | Days | Gate | Suggested agent | Needs |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | core | `core.api.TerritoryControl`, `core.api.TerritoryClient`, `TerritoryFlipEvent`, `LandProtection.protectedAt`, square size constant. Holders with neutral defaults and `swapForTesting` | 1 | G3 | sonnet-1 | - |
| 2 | world | Province generation in datagen: square assignment, seeds, merging, default owner and tier, stronghold list, `territory/*.json`, conquest-rate biome tags, validator checks, unit tests | 2 | G3 | sonnet-4 | waypoints data |
| 3 | world | Conquest grid [Legacy]: squares, kill listener, radial spread, cleansing, decay, rate, opt-out attachment, 50-point notices, stats counter, `/lotr conquest`, SavedData, GameTests | 2 | G3 | sonnet-3 | 1 |
| 4 | world | Province state: delta table, pressure, flips, contested, caps, cooldown, loss cap, drift, strongholds, `TerritoryControl` backend (all server queries), flip event and chat, `/lotr territory`, GameTests | 2 | G3 | sonnet-3 | 1, 2, 3 |
| 5 | world | Sync and client backend: layout and state payloads, heatmap request with view rules, `TerritoryClient` registration in `client/world` | 1.5 | G3 | sonnet-4 | 4 |
| 6 | progression | Map page territory layer: owner tint, borders, contested hatching, tooltip; Legacy heatmap mode for one faction with solid / striped / grey squares and relative scale | 2 | G3 | sonnet-10 | 5 |
| 7 | npc | Conquest-aware spawn tables: faction groups with base weight and sensitivity, minimum-strength entries, conquest spawns ignore darkness and garrison block, occupation lists, displaced factor, road-danger multiplier; datagen for pilot biomes | 2 | G3 | sonnet-7 | 1 |
| 8 | world | Waypoints: Legacy 500-strength unlock and occupied-province waypoint faction | 1 | G3 | sonnet-4 | 4 |
| 9 | world | **Spec**: World structure spec (venues, standards, held-structure index, `StructureVenues` backend) | (lead) | G3 | opus-2 | - |
| 10 | world | Held structures: index at generation, standard swaps (loaded and lazy), protection and block checks, swap queue | 2 | G4 | sonnet-5 | 9, 4 |
| 11 | npc | Garrison swap for held structures (old garrison removal, spawner faction) | 1 | G4 | sonnet-7 | 9, 10 |
| 12 | npc | Invasions: territory multipliers, counter-invasions, outcome reports (inside the invasion spec's tickets) | 1 | G4 | sonnet-8 | invasion spec, 4 |
| 13 | progression | Notifications UI (flip title, contested toast), conquest-kills toggle in the menu, territory achievements and the conquest-earned stat | 1 | G4 | sonnet-9 | 4, 5 |

## Open questions for the maintainer

1. **Province size.** Waypoint-seeded provinces (about 1-4 per region, named after places) as proposed, or Legacy's 1,024-block squares as the owned unit, or a hand-painted territory image?
2. **Numbers.** Borderland 400, heartland 750, flip at 0 with 250 pressure, 250 per province per hour, 100 per player per hour, drift-back in about 8 h of play: right feel?
3. **Villages.** Proposed: villages never change hands (only forts do). Should occupied villages get occupier guards instead?
4. **Wild land.** May factions claim wild provinces (Old Forest, Barrow-downs), as proposed?
5. **Conquest map access.** Legacy showed conquest only at a command-table block. Proposed: a Map page mode for pledged players. Keep the block requirement?
6. **Absent-defender slowdown** for servers: leave off by default?
7. **Unpledged players** never move territory (Legacy). Keep that?
