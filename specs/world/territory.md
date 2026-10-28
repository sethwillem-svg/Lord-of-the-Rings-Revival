# Territory and conquest

- **Pod:** world
- **Lead:** Opus 2
- **Status:** draft, revision 3 (priority: the maintainer asked for this system to be prioritised). Revision 3: occupied villages also need >= 0 with the residents' own faction; wild marches may damage walls, journaled and restored; the Old Forest and the wights can be calmed; flip-speed numbers kept and marked for playtest tuning. Revision 2 applied the maintainer's answers on PR #183: provinces authored as GeoJSON polygons, villages change hands, wild owners that push back, heatmap on the map page and at a command table, an alignment gate for unpledged players, absent-defender slowdown on by default.
- **Depends on:** `specs/world/dimension-and-mapgen.md` (canvas, biome map at 128 blocks/px, origin pixel (810, 730), projection), `specs/world/waypoints-travel.md` (waypoint regions, fixed waypoints, road polylines, map data pipeline), `specs/core/core-apis.md` (factions, relations, pledge, kill path and `FactionKillEvent`, `LandProtection`, payload and attachment rules, `/lotr` root), `specs/npc/npc-base.md` (spawn tables), `specs/npc/nemesis.md` (PR #182: world events and nemesis deaths emit control deltas through `core.api.TerritoryControl`), the progression map page. **Missing dependency:** a World structure spec (see Dependencies).
- **Sources:** behavior notes `docs/reference/legacy/territory.md` (conquest, in our own words), `factions-alignment.md` sections 6 and 8, `combat-trade.md` section 7, `biomes.md` "Spawning, conquest and invasions". Every name and line of text here is our own.
- **Beyond parity:** Legacy had conquest (a per-faction number on a square grid that changes NPC spawns) but no ownership. Provinces, owners, flips, borders, structure capture, road danger and invasion scaling are our own design. Each rule below is marked **[Legacy]** or **[Ours]**.

## Summary

Middle-earth is at war, and the land shows it. Two layers carry this:

1. **Conquest [Legacy].** A grid of 1,024 x 1,024-block squares stores a strength per faction. Pledged players raise their faction's strength by killing its enemies; strength spreads to nearby squares, cleanses the enemy's strength first, decays slowly when left alone, and changes which NPCs spawn. Same numbers as Legacy.
2. **Provinces [Ours].** The map is divided into named provinces, drawn as polygons in our map source next to the biomes and roads. Each province has an **owner** faction and a **control** value from 0 to 1,000. Kills, world events, nemesis deaths and invasions push control up or down. When an enemy breaks control to 0 and has built enough pressure, the province **flips**: borders move on the map, outposts, castles and village guard posts swap banners and garrisons, spawn tables shift to the occupier, roads grow more dangerous for the losers and invasions follow the front line. Strongholds never fall, provinces drift back to their rightful owner when nobody fights for them, and player land is never touched. A few **wild** provinces (Fangorn, the Old Forest, the Barrow-downs) belong to the land itself: they can never be conquered, and they strike back at whoever tries.

Players care because their fighting changes the map they look at, and because the world fights on without them.

## Player-facing behavior

1. **The map has borders.** The Map page (Progression) shows every province tinted in its owner's colour with borders between owners. Contested provinces are hatched. Hovering a province shows its name, owner, control bar, and the top challenger if any. Everyone sees borders; no pledge needed.
2. **Kills matter, as in Legacy.** A player pledged to a faction who kills one of its enemies sees the conquest gain next to the alignment gain. Every 50 points in a square, pledged players nearby get a notice. The same kill moves the province's control.
3. **The world moves on its own.** World events near players (caravans, warband clashes, war camps, beacons) and nemesis deaths push control. A chat line tells players of the involved factions when a province becomes contested.
4. **A province falls.** When a province flips, players in Middle-earth who are pledged to, or hold +100 or more with, the old or new owner (or their allies) read a chat line ("Mordor has taken North Ithilien from Gondor"). Players inside the province see a title. The map redraws. Over the following minutes the outposts and castles of the province fly the new owner's standard and are manned by its soldiers, the spawn mix changes toward the occupier, its roads become dangerous for the old owner's friends, and the old owner starts counter-invasions.
5. **Then it drifts back.** An occupied province slowly loses control and its rightful owner gains pressure, so unless the occupier keeps fighting, the land returns. Strongholds (Minas Tirith, Barad-dûr, Rivendell, Hobbiton...) never change hands.
6. **Villages live under occupation.** When Bree-land falls, Bree's guards are replaced by the occupier's soldiers. The Bree folk and their traders stay, but now deal only with those the occupier tolerates.
7. **The wild fights back.** Felling trees, burning, or fighting for conquest in Fangorn or the Old Forest, or disturbing the Barrow-downs, makes the land angry: first warnings, then hunting Huorns, Ents or wights, and at worst a march out of the wild against the offenders' nearest fort.
8. **Legacy conquest map.** A player can switch the Map page to a conquest heatmap for one faction at a time, with Legacy's colouring and view rules, or use a command table block, which opens the map straight in that mode as in Legacy.
9. **Player land is safe.** Banner-protected land is never changed by a flip or by the wild. A wild march may batter walls around its target, but every block it breaks comes back exactly when the march ends.

## Rules and numbers

### 1. Conquest grid [Legacy]

| Thing | Value | Notes |
| --- | --- | --- |
| Square | 8 x 8 biome px = 1,024 x 1,024 blocks | Our canvas has Legacy's scale and origin, so squares line up with Legacy's: square index = floor((biome px) / 8) counted from the image's top-left. 400 x 500 squares |
| Outside the canvas | one dummy square that never stores anything | |
| Stored | strength >= 0 per faction (playable alignment factions only), last-change time per square | Only non-empty squares are stored |
| Kill award | victim's kill value x world conquest rate x biome conquest gain rate at the **killer's** position | Kill value from `FactionKillEvent.getBaseValue()` after other listeners |
| Who earns [Legacy + ours] | a player **pledged** to a faction that gains alignment from the kill (relation `enemy`/`mortal_enemy` to the victim's faction) earns full conquest for the pledge faction. An **unpledged** player earns conquest only for a faction that gains from the kill **and** with which they hold **+500 or more**: one faction per kill (the highest such alignment; ties: alphabetical), at **x0.5** | Legacy: pledged only. Civilian kills: nothing for factions that disapprove of civilian kills. See "Unpledged gate" below |
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

**Unpledged gate (maintainer decision: gate on pledge OR enough alignment).** The number is **+500**, config `territory.unpledged_min_alignment`. Why 500:

- Every ladder in core (A to I) has a rank at exactly 500, so it is always a named, visible milestone ("you are now trusted enough to fight for their land").
- On most ladders it is **two ranks above the pledge rank** (100 → 200/250 → 500). Legacy already uses "two ranks above your pledge" as the bar for seeing other factions' conquest, so it is the Legacy-shaped step for "trusted with the war without swearing to it".
- It is 5% of the ±10,000 range and 5x the pledge threshold (100). A new player cannot move borders on day one by drifting into a war zone; someone who has done real work for a faction can.
- The x0.5 share mirrors Legacy's halving of alignment gains for players above the pledge threshold who are not pledged, so pledging stays the better deal.
- Only one faction per kill, so a player liked by several allies (Gondor, Rohan, Dale) does not triple their effect.

### 2. Provinces [Ours]

Provinces are polygons authored in the map source (`src/main/map/provinces.geojson`), next to the biome, water and road data, and rasterized by `buildSrc` at build time like the rest of the map. The first draft is seeded automatically from the waypoint regions; then it is committed and tuned by hand.

#### File format: `src/main/map/provinces.geojson`

Same conventions as the other map files: a `FeatureCollection` in heightmap pixel coordinates `[u, v]` (64 blocks per px), `Polygon` geometry with one outer ring (closing point optional, no holes; use two provinces or a `MultiPolygon` for islands).

| Field | Where | Meaning |
| --- | --- | --- |
| `retired_ids` | top level | Ids that were used once and may never be reused (array) |
| `strongholds` | top level | Province ids that are strongholds (array). Replaces a separate stronghold file |
| `id` | feature | Stable `snake_case` id. Never renamed or reused once released: retire it instead |
| `name` | feature | Display name (Tolkien place name, usually the seed waypoint's) |
| `region` | feature | Waypoint region id it belongs to |
| `seed` | feature, optional | The waypoint it was seeded from (informational) |
| `owner` | feature, optional | Default owner faction id. Omitted: derived (below) |
| `wild` | feature, optional | Wild owner id (`ents`, `old_forest`, `barrow_wights`); makes the province wild (section 4b). Mutually exclusive with `owner` |
| `tier` | feature, optional | `stronghold` is set through `strongholds`; otherwise `heartland` or `borderland`. Omitted: derived (below) |

```json
{ "type": "FeatureCollection",
  "retired_ids": [],
  "strongholds": ["hobbiton", "bree"],
  "features": [
    { "type": "Feature",
      "properties": { "id": "hobbiton", "name": "Hobbiton", "region": "shire", "seed": "hobbiton" },
      "geometry": { "type": "Polygon", "coordinates": [[[1590, 1430], [1650, 1428], [1655, 1490], [1592, 1495]]] } } ] }
```

Derived values when omitted: default owner = the faction of the waypoints inside the province (most common; ties: alphabetical), else the faction with the largest normal spawn share over its land pixels. Tier = `heartland` if any land pixel's biome is tagged `faction_home/<owner>`, else `borderland`.

#### Seeding the first draft (a one-off tool, not part of the build)

`./gradlew seedProvinces` (buildSrc task) writes a draft `provinces.geojson`. It refuses to overwrite an existing file unless run with `-Pforce`. Output is deterministic.

1. Work on the heightmap pixel grid inside the painted windows, using the rasterizer's land mask and biome raster.
2. **Seeds:** every fixed waypoint of a region. A region without waypoints is one province seeded at its centroid.
3. **Cost-weighted Voronoi per region:** each land pixel of a region goes to the seed with the lowest path cost over that region's own pixels (so provinces never cross region edges). Step cost 1 per pixel, plus 40 for stepping across a river centre line and plus 1 per block of height climbed over ridge features, so borders settle on rivers and mountain crests where they are close.
4. **Clip to biome edges:** a province pixel whose biome differs from the seed's biome and borders a province of that biome's majority moves to it when that does not split a province (one pass).
5. **Merge small provinces:** under 1,024 px² (four 1,024-block squares) merge into the same-region neighbour with the longest shared border.
6. **Vectorize:** trace each province's outline along pixel edges, simplify with a tolerance of 1 px while keeping shared borders identical on both sides (simplify shared edges once), and extend coastal edges 8 px out to sea so coastlines are never gaps.
7. Write ids and names from the seed waypoints (region id for single-province regions), the region, and the seed.

After that, people edit the GeoJSON by hand (QGIS or a text editor) and commit it. Re-running the seeder is only for new painted windows: with `-Pwindow=<id>` it seeds just that window and appends.

#### Build step and validation

`rasterizeMap` reads `provinces.geojson` and writes, under `build/generated/map/resources/data/lotrrevival/lotrrevival/`:

- `territory/<id>.json` per province: name (component `territory.lotrrevival.<id>`), region, default owner or wild owner, tier, polygon (for the client map), bounding box, adjacency list.
- `territory_index.png`: 16-bit grayscale **at biome-map resolution** (3,200 x 4,000, 128 blocks per px); value = 1 + index of the province in id-sorted order, 0 = none. A biome pixel belongs to the province containing its centre (same half-open rule as the biome rasterizer). PNG compresses the flat areas to a small file; the server keeps it as run-length rows.
- `territory_index.json`: the id-sorted list and a hash of the image, used by the client sync.

`validateMap` (part of `check`) fails the build when, inside the painted windows:

- **Gaps:** a land biome pixel is in no province.
- **Overlaps:** a biome pixel centre is inside two provinces. Polygons may overlap open sea by less than one pixel's width at the coast; anything bigger is an overlap.
- **Waypoints:** a fixed waypoint is not inside exactly one province, or is inside a province of another region.
- **Region drift:** more than 5% of a province's land pixels are biomes of another region (allows hand-tuned borders to wander a little).
- **Ids:** missing, not `snake_case`, duplicated, or listed in `retired_ids`; once `map_regions.json` marks a window released, an id that existed in that window may not disappear without being added to `retired_ids` (checked against `src/main/map/provinces.lock`, a committed sorted id list the build rewrites only for unreleased windows).
- **Fields:** unknown region, unknown faction or wild owner, both `owner` and `wild`, a stronghold id that does not exist, a stronghold that is wild, a polygon with fewer than 3 points or self-intersecting.
- Like the other checks, the validator first proves itself against broken fixtures (a gap, an overlap, a waypoint in two provinces, a reused id).

#### Other province rules

| Thing | Value | Notes |
| --- | --- | --- |
| Default control C0 | stronghold 1,000 (locked), heartland 750, borderland 400 | Config, **tune in playtest**. Wild provinces: section 4b |
| Adjacency | provinces whose biome pixels touch (4-neighbour), plus across water up to 16 biome px (2 squares) | Used by invasions and the UI |
| Province of a position | the index pixel under it | Positions outside every window or in sea: none |
| Province and Legacy squares | independent: a square can span two provinces; grid strength is read per square, province state per province | |

**Pilot provinces:** seeded from the pilot waypoints. Strongholds `hobbiton` (hobbit) and `bree` (bree). Wild: `old_forest` (wild owner `old_forest`) and `barrow_downs` (`barrow_wights`). Planned strongholds later: Mithlond, Thorin's Halls, Rivendell, Caras Galadhon, Thranduil's halls, Erebor, Dale, Edoras, Helm's Deep, Minas Tirith, Dol Amroth, Isengard, Barad-dûr, Minas Morgul, Dol Guldur, Mount Gundabad, Carn Dûm, Umbar and the capital of each Harad and Rhûn faction. One per faction at least. Fangorn's provinces get wild owner `ents` when Rohan is painted.

### 3. Province state and deltas [Ours]

Each province holds: owner (faction or none), control C (0-1,000), a **pressure** P per challenging faction (0-1,000), time of the last flip, and per-hour rate buckets.

A delta is (faction F, amount a, source, optional player). Applied in this order:

| F's relation to the owner O | a > 0 | a < 0 |
| --- | --- | --- |
| F = O, or `ally`/`friend` | C += a (max 1,000) | C -= abs(a) |
| `enemy` / `mortal_enemy` | C -= a, P[F] += a | P[F] -= abs(a) |
| `neutral` | ignored | ignored |
| province is wild | never changes owner or control; see section 4b (wrath) | |
| province has no owner (only after its owner faction was removed by a datapack) | P[F] += a; C stays 0, so the flip rule claims it | P[F] -= abs(a) |

| Source | Delta | Notes |
| --- | --- | --- |
| Player kill | the conquest gain or cleanse in the killer's centre square (section 1), for the credited faction (pledge faction, or for unpledged players the gated faction) | So a warrior kill is +2 (x biome rate), +1 for a gated unpledged player. Hired units x0.25 included |
| World event (`lotrrevival:world_event/<type>`) | catalogue amount x 10 (`event_point_scale`) | Nemesis spec already applies x1.5 player-decided and x0.5 off-screen before reporting |
| Nemesis death (`lotrrevival:nemesis_death`) | amount x 10 | i.e. -10 (Marked) to -50 (Overlord) for the nemesis's faction |
| Invasion defeated (`lotrrevival:invasion_defeated`) | -50 for the invader | Plus the Legacy grid award to contributors (section 1) |
| Invasion unopposed (`lotrrevival:invasion_unopposed`) | +40 for the invader | An invasion that ends because no player fought it (definition in the invasion spec) |
| Admin | any | Bypasses caps |

**Pressure fades** when the owner holds: see Drift.

**Flip.** After a delta, if C = 0 the province checks its challengers. A challenger's score is its own P plus one third of the P of each of its allies that is also an enemy of the owner. The top score flips the province when it is at least **250** (`flip_pressure`, tune in playtest). Then: owner := that challenger, C := **250** (`flip_start_control`, tune in playtest), all pressures reset to 0, last-flip time is now. If C is 0 but nobody reaches 250, the province stays at 0 and is "breaking". Wild provinces never flip.

**Contested** = C < 200, or the top challenger's score >= C / 2. Shown on the map; spawns and danger rules use it.

### 4. Safeguards [Ours]

Flip-speed numbers in this section and in sections 2-3 (C0 per tier, flip pressure and start control, cooldown, province and player caps, loss cap, drift amounts) are the maintainer-approved **defaults, to tune in playtest**. All are config.

| Safeguard | Value | Notes |
| --- | --- | --- |
| Strongholds | never flip; C never drops below 500 | Still take deltas (so challengers' pressure shows), and pressure there fades fast (x3 drift) |
| Flip cooldown | 3 h of server time (216,000 ticks) after a flip, C cannot go below 1 | Stops ping-pong |
| Province rate cap | the sum of abs(delta) applied to a province in any rolling 60 min is at most 250 | Twelve 5-min buckets; excess is dropped. Drift and admin are exempt |
| Player cap | one player's deltas to one province in a rolling 60 min are at most 100 | Kills and player-decided event bonuses are attributed to the player. Means a lone player needs about 4 h of focused fighting to break a borderland province without event help, and at least 3 players to hit the province cap |
| Faction loss cap | a faction loses at most 2 provinces per 24 h of server time; beyond that its C floor is 1 | Protects factions with no active players on big servers |
| Absent-defender slowdown | **on by default**: `absent_defender_factor` 0.5 | Player-attributed enemy deltas to a province are x0.5 while its owner side has **defenders** but none is online. Defenders = players pledged to the owner or its allies/friends who logged in within the last 14 days (`defender_window_days`). A side with no defenders at all (always the case in single player) is not protected, so the rule only matters where real people hold that side. Set the factor to 1.0 to turn it off |
| Drift home | see Drift | Land returns to its rightful owner when left alone |
| Land and builds | territory never places, breaks or replaces blocks except the structure standards in section 5 and the temporary, journaled march damage in section 4b; never inside banner-protected land. March damage is always restored exactly, and a player block is never permanently lost | |
| Classic world type, conquest disabled | no grid and no provinces; holders answer defaults | Config `territory.enabled` |

**Drift**, every 24,000 ticks (one in-game day, 20 min); amounts tune in playtest:

| State | Change per drift step |
| --- | --- |
| Owner = default owner, C below C0 | C += 20 (to C0) |
| Owner = default owner, C above C0 | C -= 10 (to C0) |
| Owner = default owner | every P -= 20 (to 0); x3 in strongholds |
| Owner != default owner (occupied) | C -= 10; P[default owner] += 10. With nobody fighting, an occupied province flips back after about 25 steps (8 h of play) |
| Wild | wrath decays (section 4b) |

Drift runs on server time only, so a single-player world does not change while closed.

### 4b. Wild provinces and the wild's push-back [Ours]

Some land belongs to no people. A wild province has a **wild owner** instead of a faction. It is always held (control fixed at 1,000), never flips, takes no pressure, never swaps structures, and its owner never invades. Instead it keeps **wrath** and strikes back, in the spirit of the Ents' march on Isengard.

| Wild owner | Provinces | Acts through | Friend of | Hostile to |
| --- | --- | --- | --- | --- |
| `ents` | Fangorn (when painted) | Ents and Huorns (`fangorn` faction NPCs) | players and factions with alignment >= 0 with `fangorn`; never angered by kills of orcs, wargs or Isengard and Mordor men there | anyone who fells trees or fights for conquest there; Isengard and Mordor first |
| `old_forest` | the Old Forest | the forest's Huorn-like hostile trees (`hostile_trees` group) | nobody; Tom Bombadil's land is not ours to spec | everyone who cuts, burns or fights for conquest there |
| `barrow_wights` | the Barrow-downs | Barrow-wights (`unaligned_hostile` group) | nobody | everyone who fights for conquest there, digs into barrows, or camps there at night |

**Wrath** is kept per wild province, separately for each **player** and each **faction**, from 0 to 1,000.

| Wrath source (inside the wild province) | Player wrath | Faction wrath |
| --- | --- | --- |
| Any conquest the player would have earned there (grid award or province delta, before caps) | x2 of the amount | x2, to the credited faction |
| World event, nemesis or invasion delta for a faction there | - | x2 of abs(amount x 10) |
| Felling a log block (Ents, Old Forest) | +2 per log | +1 to the player's pledge faction |
| Lighting fire or a fire spreading from the player's action into leaves or logs (Ents, Old Forest) | +10 per event, once per 5 s | +5 |
| Killing one of the wild owner's creatures (Ent, Huorn, hostile tree, wight) | +25 | +10 |
| Breaking blocks of a barrow structure (Barrow-downs) | +5 per block | +2 |
| Sleeping or setting a respawn point there at night (Barrow-downs) | +50 | - |
| Players with alignment >= +100 with `fangorn` (Ents only) | x0.25 on all player wrath | - |

Wrath decays by **50 per drift step** (20 min) and by 200 at once when its holder leaves the wild province and its neighbours for a full in-game day. Wrath of an offline player keeps decaying.

**Stages** (per player, using the higher of the player's own wrath and their pledge faction's wrath):

| Stage | Wrath | What happens near the player (inside the province, or within 256 blocks of its edge for stage 4) |
| --- | --- | --- |
| 1 Watchful | 100-299 | Warnings only: chat lines in our words once per 10 min ("The trees lean closer"), ambient sounds, mist. Wild-owner spawns near the player x1.5 |
| 2 Stirring | 300-599 | Wild-owner spawns near the player x3, and they target the player on sight even if normally passive (Ents and Huorns turn hostile to that player only). Old Forest: hostile trees root the player (slowness IV and no jumping for 3 s, 20 s cooldown) when within 3 blocks. Barrow-downs: barrow-fog at night (darkness effect, mounts refuse to move for 2 s at a time) |
| 3 Wrathful | 600-899 | **Hunt**: every 5 min a party seeks out the player: Ents (2 Ents + 3 Huorns), Old Forest (5 hostile trees around the player), Barrow-downs (3-5 wights rising from the ground at any time of day). Grid strength of the player's credited faction in the province's squares is cleansed by 50 per hunt that reaches the player |
| 4 March | 900+, or **faction** wrath 900+ | **March**, once per 3 in-game days per wild province: a host leaves the wild toward the nearest held structure (outpost, camp, castle, watchtower, tower or village guard post) of the most-wrathful faction within 512 blocks of the province edge. Ents: 6-10 Ents and Huorns; Old Forest: the trees spread a ring of 8-12 hostile trees around the target; Barrow-downs: a wight host of 6-8 with a wight lord, at night only. The host fights the garrison. If it wins (garrison dead, or 8 min pass with the host alive), that faction loses 100 control (or gains -100 pressure) in the target's province and 50 in each province adjacent to the wild, and its grid strength in the wild province's squares is cleared. All faction and player wrath in the province then drops by half |

Rules for every stage:

- **Only marches change blocks**, and only temporarily (below). Stages 1-3 never touch blocks. Banner-protected land keeps every host out (`LandProtection.blocksInvasion` with the wild owner's group) and protected structures are never targets.
- The march is a world event to the NPC pod: it runs on the world-events framework's chunk tickets and budgets, shows on the map, and reports its control deltas through `TerritoryControl.report` with source `lotrrevival:wild_march/<owner>`.
- Peaceful: wrath is still tracked but no stage above 1 happens.
- Wild-owner creatures killed in a march do not raise wrath further for that march.

**March damage (maintainer decision: marches may damage walls; everything is restored).** Uses the same journal rules as the nemesis spec's war camps.

| Limit | Value |
| --- | --- |
| Where | only inside the target structure's bounds expanded by 8 blocks, only in chunks under the march's event tickets; never in a stronghold province; never at a position where `LandProtection.protectedAt` is true (checked per block) |
| What the host does | Ents and Huorns break wall, door, gate, fence and roof blocks to reach the garrison. Old Forest: roots burst up, replacing ground and floor blocks with a root block (our own block, no drops). Wights: doors and gates rime over and shatter (broken), and torches and lanterns go out (replaced by air) |
| Never touched | blocks with a block entity (chests, banners, signs, spawners, beds...), fluids, blocks tagged `#lotrrevival:march_proof` (bedrock, obsidian, reinforced and our faction standards), blocks harder than 50, unbreakable blocks, and anything below y 5 |
| How much | at most 64 changed positions per march for Ents, 48 for the Old Forest, 16 for wights (config); at most 1 change per 2 s per host member |
| Journal first | each original block state is written to the journal (SavedData, saved before the change) before the change happens |
| No loot, no fire | a broken position drops nothing and gives no XP; no fire and no explosions; pistons cannot move journaled root blocks; a journaled root block broken by anyone drops nothing |
| Restore | exactly, on march end (after the host has left or despawned), on server stop (before tickets are released) and on startup recovery (leftover journals: force-load, restore, release, delete). A position is restored if it holds air or our replacement block. If a player has since put something else there, that player's block stays and the original block is given back as an item at the spot (so no player-placed block is ever permanently lost) |
| Players inside | a restore never places a block inside a player or an entity's bounding box; it waits up to 30 s, then pushes the entity up out of the block |
| Off-screen | a march resolved off-screen changes no blocks |

**Calming the wild (maintainer decision: all three can be calmed).** Calming lowers the player's own wrath; faction wrath falls by a quarter of what its members calm.

| Wild owner | How to calm | Amount | Limit |
| --- | --- | --- | --- |
| `ents` | plant saplings in Fangorn (the sapling must still be there 5 min later) | -100 per sapling | 300 per in-game day |
| `old_forest` | **leave the trees alone**: a full in-game day spent mostly inside the forest (at least 10 min of it) without felling, burning or striking any tree | -100 per such day | once per in-game day |
| `old_forest` | **offer at its heart**: at the forest's heart glade (a World landmark: a ring of ancient trees around a mossy stone) place an offering on the stone: a sapling, bone meal, or a full water bottle poured out; the offering is taken (consumed) | -50 per offering | 200 per in-game day; nothing while the player holds an axe or a lit torch in either hand |
| `barrow_wights` | **return what was taken**: items looted from barrows carry a mark (a data component set by the barrow loot tables). Using a marked item on a barrow's cairn stone gives it back (consumed) | -20 per item | 300 per in-game day |
| `barrow_wights` | **lay the dead to rest**: by daylight, place a small flower on a cairn stone of a barrow | -75 per cairn | each cairn once per 3 in-game days |

Stage 4 marches are not stopped by calming once started; calming during a march only counts after it ends.

**NPC types this needs (NPC pod):** Ent and Huorn (`fangorn` faction; Huorn as a slow ambusher that looks like a tree until it moves), the Old Forest's Huorn-like hostile tree (`hostile_trees` pseudo-faction), the Barrow-wight and a Barrow-wight lord (`unaligned_hostile` pseudo-faction), plus the effects "rooted" and "barrow-fog". The pseudo-factions are already requested from core in the mapgen spec. Our own designs and art.

**Other blocks and data this needs:** a root block and the `#lotrrevival:march_proof` tag (core), the barrow-treasure data component (core) and its loot-table function, barrow cairn stones and barrows with loot (structure spec), the Old Forest heart glade landmark (World, structure spec), and the journal service shared with war camps (NPC pod's world-events framework).

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

**Holdable structures.** Outposts, castles, permanent camps, watchtowers and towers (structure venue kinds `outpost`, `castle`, `camp`, `watchtower`, `tower`) belong to a faction when generated. **Villages** are held too, by the special rules below. Bridges, ruins and hideouts are not held.

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

**Villages under occupation (maintainer decision: villages change hands).**

| Rule | Value |
| --- | --- |
| What swaps | the village's **guards** only: its guard posts' standards and the guard spawner's faction (guards, watchmen, militia, bounders; the NPC pod marks these roles). The occupier's guards replace them by the same swap rules as forts (old guards removed when the swap runs, new ones from the spawner) |
| What stays | residents, farmers, traders, smiths, innkeepers and hire captains keep their own faction, home, names and inventories. Killing one still counts against their own faction |
| Trade, hire and quests under occupation (two gates) | a resident deals with a player only if **both** hold: (1) **occupier gate**: the player has the resident's normal threshold with the **occupier** (e.g. a Bree trader needing +0 now needs +0 with Mordor); (2) **residents' gate**: the player has alignment **>= 0 with the residents' own faction** (maintainer decision), even where the resident's normal threshold is lower. Failing (1) gives an occupation refusal line, failing (2) the residents' usual refusal. So Bree folk under Mordor serve only players Mordor tolerates and Bree does not hate |
| Who residents fight | nobody new: they are civilians and do not rise against the occupier. Occupier guards defend the village and attack players hostile to the occupier |
| Prices | unchanged |
| Hired units from an occupied village | units hired before the flip stay with their owners. New hires come from the residents' faction's roster, gated as above |
| Back to the owner | when the province flips back, the old guards return by the same swap, and gating returns to the residents' faction |
| Landmark towns | landmark settlements (Bree, Staddle, Combe, Archet) follow the same rules unless their province is a stronghold (Bree is: its guards never change) |

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

**Conquest heatmap: Map page mode and command table (maintainer decision: both).**

| Rule | Value |
| --- | --- |
| Map page mode [Ours] | the Map page gets a "Conquest" mode beside the normal and territory layers: one faction at a time, chosen from a scrolling faction list grouped by map region |
| Command table [Legacy] | a core block (a war table with a map on it, our own model and art) that, when used, opens the Map page directly in Conquest mode centred on the table, as Legacy's table did. Shift-use cycles the start zoom. Craftable (recipe by core) and placed in our captains' rooms by the structure spec. Only works in Middle-earth and when territory is enabled |
| Drawing [Legacy] | one square per grid square; colour intensity relative to the strongest square in view (min 10% for any non-zero square) with a key; **solid** where the faction has spawn groups in the square's biomes, **striped** where only an ally does, **grey** where neither. Hover shows the value and that note. Province borders are drawn on top as thin lines |
| Who may view [Legacy + ours] | a pledged player: own faction, its allies and its enemies; any other faction at the rank two steps above the pledge rank. An unpledged player: factions they hold +500 or more with (the same gate as earning) and those factions' allies and enemies. Others get the reason ("pledge or reach +500 with ...") |
| Data | snapshot on request, refreshed by the player (button) at most every 2 s; not live |
| Achievement [Legacy] | viewing your own faction's conquest for the first time (Progression) |

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
| Wild wrath stage rises | the player | stage warning line (section 4b) |
| Wild march starts / ends | players within 512 blocks of the target and players of the targeted faction | chat line and a map marker (world-event marker) |

Text is ours, in lang via World's lang provider.

## Edge cases

- **Multiplayer:** state is shared by the whole server. Caps (section 4) stop one player or one guild from turning the map over in an evening. Players of opposing sides push the same numbers; the per-player cap applies to each. Two flips in one tick on one province cannot happen (flip resets C to 250).
- **Single player:** no other players, so events, drift and invasions do most of the moving. Numbers aim for a borderland flip in about 2-3 hours of focused play with event help.
- **Death, logout, dimension change:** nothing is lost; territory is world data. Leaving Middle-earth stops kill awards. A logged-out player's opt-out flag persists (player attachment).
- **Pledge broken mid-fight:** later kills credit only through the unpledged gate (+500, x0.5). Earlier deltas stand.
- **Unpledged player at +500 with two enemies of the victim:** only the highest alignment is credited.
- **Village trader mid-trade when the province flips:** the open screen closes; the next opening uses the new gating.
- **Hired unit from a village whose province flips:** stays hired; only new hires are gated by the occupier.
- **Wild wrath and fellowships:** wrath is per player; a fellowship does not share it, but a hunt party attacks whoever fights it.
- **Wild march target gone** (destroyed, protected, or chunk unloaded and unloadable within the chunk budget): the march is resolved off-screen as a win if the host was alive and the target unprotected, else cancelled.
- **Faction removed by a datapack:** its grid strength, pressure and wrath are dropped on load; provinces it owned revert to their default owner. A default owner that no longer exists makes the province ownerless (borderland at C 0, claimable like any province whose owner is gone).
- **Province layout changed by a datapack or a map repaint:** state for unknown provinces is dropped; new provinces start at their defaults; a province id kept across the change keeps its state even if its polygon moved. Retired ids are never reused, so old state is never applied to a different place. Grid squares are fixed by the canvas and keep their data.
- **Position on a province border:** the index pixel under it decides (128-block resolution), so a structure, waypoint or player is always in exactly one province.
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
| Province source | `src/main/map/provinces.geojson` (+ `provinces.lock`) | Hand-tuned polygons, committed; section 2 |
| Province layout | `territory/<id>.json`, `territory_index.png`, `territory_index.json` under `build/generated/map/resources/data/lotrrevival/lotrrevival/` | Written by `rasterizeMap`, never committed (like waypoints). Loaded server side by a World reload listener; datapacks may override the files, and the sync hash covers them |
| Wild wrath | inside `lotrrevival_territory` | Per wild province: player UUID -> wrath, faction -> wrath, last march time, last hunt per player |
| Defender log | inside `lotrrevival_territory` | Player UUID -> pledge faction and last login, for the absent-defender rule (entries older than the window are pruned) |
| Occupation gating | `TerritoryControl.gatingFaction` (below) | NPC pod reads it for residents' trade, hire and mini-quest checks |
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
| `gatingFaction(level, pos, residentFaction)` | the resident's faction | The occupier while the village's province is occupied (section 5). The NPC pod applies it as the occupier gate and keeps the residents' >= 0 gate itself |
| `wildStage(level, pos, player)` | 0 | Wild wrath stage 0-4 for spawn and AI decisions (section 4b) |
| `wildHostileTo(level, pos, player)` | false | True from stage 2: the wild owner's creatures treat the player as an enemy |

`core.api.TerritoryClient` (client, `WaypointClient` pattern; World's client code registers it; Progression's map reads it): `provinces()` (id, name, default owner or wild owner, tier, polygon in heightmap px, adjacency), `provinceAt(blockX, blockZ)`, `openConquestMode` (a flag the command table sets so the Map page opens in Conquest mode at a position), `state(id)` (owner, control, contested, top challenger and score), `version()` (bumps on change), `requestConquest(faction)`, `conquestGrid(faction)` (snapshot or empty, with a "not allowed: unpledged / needs rank X" reason).

`core.event.TerritoryFlipEvent(level, province, oldOwner, newOwner, cause)` posted by World after a flip (Progression achievements, NPC reactions). `core.event.WildMarchEvent(level, province, wildOwner, targetFaction, outcome)` for Progression achievements.

**Command table block** (core owns blocks): `lotrrevival:command_table`, our own model and art, recipe by core. Using it sends the player a World payload that opens the Map page in Conquest mode centred on the table (through `TerritoryClient`).

`LandProtection.protectedAt(level, pos)`: true if any banner protects the position (default false). New default method on the existing holder.

Squares are 1,024 blocks; the conversion lives with core's map projection constants.

### Payloads (World defines, registered the way other World payloads are)

| Payload | Direction | Content | When |
| --- | --- | --- | --- |
| `world/territory_layout` | S2C | province table (ids, names, owners, tiers, polygons, adjacency) plus the index as run-length rows at biome resolution, with the hash from `territory_index.json` | login, dimension entry if the client lacks that hash, datapack reload. Split into parts of at most 256 KB |
| `world/territory_state` | S2C | per province: owner index, control, contested flag, top challenger and score | full on login and dimension entry; changed provinces only, batched every 5 s, max 256 per payload |
| `world/conquest_request` | C2S | faction | at most 1 per 2 s per player |
| `world/conquest_grid` | S2C | faction, squares with strength > 0 (x, z, strength), or a refusal reason | reply; view rules in section 5 |
| `world/open_conquest_map` | S2C | table position | when a command table is used |

### Commands (op level 2 unless noted)

- `/lotr conquest set|add|radial <faction> <amount> [x z]`, `/lotr conquest clear [x z]`, `/lotr conquest rate [value]` [Legacy]; `/lotr conquest kills on|off` (level 0, own toggle).
- `/lotr territory info [x z]` (level 0), `/lotr territory list`, `/lotr territory wrath <province> [player|faction] [value]`, `/lotr territory march <province>` (forces a march), `/lotr territory set_owner <province> <faction|none>`, `set_control <province> <0-1000>`, `pressure <province> <faction> <amount>`, `reset <province|all>`.

### Config (`lotrrevival-world-server.toml`, `[territory]`)

`enabled` (true), `default_conquest_rate` (1.0), `square_decay_grace_ticks` (72000), `square_decay_per_hour` (1), `spread_radius` (3.5), `hired_share` (0.25), `event_point_scale` (10), `invasion_defeated` (50), `invasion_unopposed` (40), `control_max` (1000), `default_control` per tier (1000/750/400), `stronghold_floor` (500), `flip_pressure` (250), `flip_start_control` (250), `flip_cooldown_ticks` (216000), `province_cap_per_hour` (250), `player_cap_per_hour` (100), `faction_losses_per_day` (2), `absent_defender_factor` (0.5), `defender_window_days` (14), `unpledged_min_alignment` (500), `unpledged_share` (0.5), drift period and amounts (24000; +20/-10/-20/-10/+10), `contested_below` (200), `occupation_strength` (200), `displaced_weight` (0.25), road multipliers (0.75/1.0/1.5/2.0), road band (24), invasion multipliers, `structures_change_hands` (true), `swap_per_tick` (2), `villages_change_hands` (true), wild: `wild_enabled` (true), stage thresholds (100/300/600/900), wrath sources and multipliers (section 4b), `wrath_decay` (50), `hunt_interval_ticks` (6000), `march_cooldown_ticks` (72000), march sizes, `march_control_loss` (100/50).

### Performance

- Position to province: the index image kept as run-length rows (4,000 rows; a binary search within a row), a few hundred KB for the full map. A per-square cache (400 x 500 shorts) answers whole squares that lie inside one province without the search. Square strengths: hash map of non-empty squares; decay computed on read and baked on change (no ticking per square). Housekeeping drops empty squares every 6,000 ticks, staggered.
- Spawn, road and invasion queries are O(1) lookups; safe to call per spawn attempt.
- Drift: one pass over provinces (about 1,500 on the full map) every 24,000 ticks.
- Sync: deltas only, batched; heatmap on request with a rate limit.
- Structure swaps: budgeted per tick, queue saved with the data.
- Wild wrath: updated on the triggering event only; stages are checked once per 100 ticks per player inside or near a wild province. Hunts and marches use the world-events governor's budgets.
- Build: rasterizing provinces adds one point-in-polygon pass at biome resolution inside the windows, with a bounding-box prefilter.
- Saves: dirty flag only.

## Out of scope

- Writing the structures themselves, their standards and garrison spawners (World structure spec, see Dependencies).
- Sieges of strongholds, player-claimed provinces, taxes or tribute, residents rebelling against occupiers.
- Areas of influence (core's reserved `InfluenceProvider`).
- The map screen drawing (Progression) and the invasion system itself (NPC).

## Dependencies

1. **World structure spec (`specs/world/structures.md`), not written yet.** Needed for: the venue kinds (outposts, castles, camps, bridges, ruins, watchtowers, towers, hideouts, villages) with their tags `#lotrrevival:event_venues/<kind>`, faction structure sets per region, placed faction standards at recorded positions, garrison spawners (with the NPC pod), the held-structure index filled at generation, and the `core.api.StructureVenues` backend the nemesis spec also needs. **Proposal: World writes it next**, before any ticket in section "Structures" below starts.
2. **Invasion spec (NPC, S8), not written.** Needs the invasion outcome reports and must read the multipliers. Until then provinces move only from kills, events and nemesis deaths.
3. **Nemesis spec (PR #182)**: `report` signature and sources must match; no other coupling.
4. **Progression map page**: draws borders and heatmap from `TerritoryClient`.
5. **NPC types for the wild (NPC pod):** Ent, Huorn (`fangorn`), the Old Forest's hostile tree (`hostile_trees`), Barrow-wight and Barrow-wight lord (`unaligned_hostile`), effects "rooted" and "barrow-fog". Wild hunts and marches cannot ship before them.
6. **Village roles (NPC pod):** guards must be marked as a role apart from residents and traders, and village guard spawners must exist (structure spec + NPC pod).

## Acceptance tests

The GameTest server has no Middle-earth level, so the territory service takes a level-independent layout and clock. Tests build a small fixture layout (a few provinces over a 10 x 10 square grid) on the test level through a test hook on the backend.

- [ ] Unit (build): the seeder on a fixture map: cost-weighted Voronoi stays inside regions and prefers a fixture river as border, small provinces merge, shared borders identical on both sides, deterministic output.
- [ ] Unit (build): validator fixtures fail for a gap, an overlap, a waypoint in two provinces, a waypoint in another region's province, a reused retired id, a removed released id, a self-intersecting ring; the real map passes.
- [ ] Unit (build): the index image matches point-in-polygon at pixel centres; derived default owner and tier as specified.
- [ ] GameTest: kill award, radial shares (1.0 / 0.714 / 0.429 / 0.143 by distance), cleansing before gain, hired x0.25, opted-out players give nothing, unpledged players give nothing below +500 and x0.5 for exactly one faction at +500, biome rate applied.
- [ ] GameTest: decay: nothing for the first 72,000 ticks, then -1 per 72,000 for every faction; a change restarts the clock.
- [ ] GameTest: invasion-defeated award of 50 split between pledged contributors.
- [ ] GameTest: delta table: owner side raises C, enemies lower C and build P, neutral ignored; wild provinces never change owner or control.
- [ ] GameTest: wild wrath: sources add the listed amounts (Fangorn-friendly players x0.25, orc kills in Fangorn add nothing), decay per drift step, stages at 100/300/600/900, `wildHostileTo` from stage 2, march cooldown, march win applies the control loss and clears grid strength; stages 1-3 change no blocks.
- [ ] GameTest: march damage: respects the per-march limit, skips block entities, `march_proof`, protected positions and stronghold provinces; journals before each change; restores exactly on end and after a simulated restart from the saved journal; a player block placed on a broken spot stays and the original drops as an item; nothing drops when broken.
- [ ] GameTest: calming: sapling (kept 5 min), quiet forest day, heart-glade offering (refused with an axe or lit torch in hand), returning marked barrow items, flowers on a cairn by day; daily limits; faction wrath falls by a quarter.
- [ ] GameTest: occupied village gating needs both the occupier threshold and >= 0 with the residents' faction.
- [ ] GameTest: absent-defender: x0.5 only when the owner side has a defender seen within 14 days and none online; no defenders at all = no slowdown.
- [ ] GameTest: village gating: `gatingFaction` returns the occupier while occupied and the residents' faction after flip-back.
- [ ] GameTest: flip at C = 0 with score >= 250 (allies count one third), C becomes 250, pressures reset, `TerritoryFlipEvent` fires; no flip below 250.
- [ ] GameTest: strongholds never below 500 and never flip; flip cooldown keeps C >= 1; province cap 250/h and player cap 100/h drop the excess; faction loss cap.
- [ ] GameTest: drift steps for each state in section 4; an untouched occupied province flips back.
- [ ] GameTest: `report` with each nemesis source maps to the right delta; no backend = no error (holder default).
- [ ] GameTest: spawn queries: occupation floor 200, displaced factor 0.25, Legacy group weight formula and minimum-strength entries.
- [ ] GameTest: road and invasion multipliers for each row; counter-invaders listed.
- [ ] GameTest: SavedData round trip; unknown faction and unknown province dropped on load.
- [ ] GameTest (after the structure spec): standard swap replaces only unchanged standards, skips protected structures, removes the old garrison but not hired or named NPCs; lazy swap on chunk load.
- [ ] Unit: layout and state payload round trip; layout hash stable.
- [ ] GameTest: command table sends the open-map payload only in Middle-earth with territory enabled.
- [ ] Playtest: pledge to the Hobbits, fight the orcs that `/lotr territory pressure` brought in, watch the Shire border hatch, flip a pilot province with `/lotr territory`, see the map, waypoint and spawn changes, wait for drift-back; check that a bannered build in the province is untouched; trade with a Bree trader under occupation; fell trees in the Old Forest until the trees hunt you, then calm them at the heart glade; trigger a march with `/lotr territory march` and check the walls come back exactly; open the conquest map from a command table.

## Proposed tickets (not opened)

Priority: **high** (maintainer request). Gate: foundation in **G3** next to the map GUI, effects needing structures and invasions in **G4**. Order is the dependency order.

| # | Pod | Ticket | Days | Gate | Suggested agent | Needs |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | core | `core.api.TerritoryControl` (incl. `gatingFaction`, `wildStage`, `wildHostileTo`), `core.api.TerritoryClient`, `TerritoryFlipEvent`, `WildMarchEvent`, `LandProtection.protectedAt`, square size constant, root block, `#lotrrevival:march_proof` tag, barrow-treasure data component and loot function. Holders with neutral defaults and `swapForTesting` | 1.5 | G3 | sonnet-1 | - |
| 2 | world | **Provinces in the map pipeline**: `provinces.geojson` format and reader in `buildSrc`; the `seedProvinces` task (cost-weighted Voronoi from the region's waypoints, river and ridge costs, biome-edge clip, small-province merge, shared-edge vectorizing); run it on the pilot and **commit the draft**; rasterize to `territory/<id>.json`, `territory_index.png/.json`; validator (gaps, overlaps, waypoints in exactly one province of their region, region drift, stable ids, `retired_ids`, `provinces.lock`) with broken fixtures; map README section; conquest-rate biome tags | 2 | G3 | sonnet-4 | waypoints data |
| 2b | world | Hand-tune the pilot `provinces.geojson` (borders on the Brandywine, Bree-hill, the Old Forest edge, the Barrow-downs), set strongholds and wild owners; screenshot overlay for review | 0.5 | G3 | sonnet-4 (then the maintainer) | 2 |
| 3 | world | Conquest grid [Legacy]: squares, kill listener with the pledge-or-+500 gate, radial spread, cleansing, decay, rate, opt-out attachment, 50-point notices, stats counter, `/lotr conquest`, SavedData, GameTests | 2 | G3 | sonnet-3 | 1 |
| 4 | world | Province state: delta table, pressure, flips, contested, caps, cooldown, loss cap, absent-defender rule and defender log, drift, strongholds, `TerritoryControl` backend (all server queries), flip event and chat, `/lotr territory`, GameTests | 2 | G3 | sonnet-3 | 1, 2, 3 |
| 5 | world | Sync and client backend: layout and state payloads, heatmap request with view rules, open-map payload, `TerritoryClient` registration in `client/world` | 1.5 | G3 | sonnet-4 | 4 |
| 6 | progression | Map page territory layer (polygons, owner tint, borders, contested hatching, wild land style, tooltip) and the Conquest mode (one faction, solid / striped / grey squares, relative scale, view-rule messages, open-at-position from the command table) | 2 | G3 | sonnet-10 | 5 |
| 6b | core | Command table block: model, art, recipe, use action sends the open-map payload (World provides the payload) | 0.5 | G3 | sonnet-1 | 5 |
| 7 | npc | Conquest-aware spawn tables: faction groups with base weight and sensitivity, minimum-strength entries, conquest spawns ignore darkness and garrison block, occupation lists, displaced factor, road-danger multiplier; datagen for pilot biomes | 2 | G3 | sonnet-7 | 1 |
| 8 | world | Waypoints: Legacy 500-strength unlock and occupied-province waypoint faction | 1 | G3 | sonnet-4 | 4 |
| 9 | world | **Spec**: World structure spec (venues incl. village guard posts, standards, held-structure index, `StructureVenues` backend, command tables in captains' rooms) | (lead) | G3 | opus-2 | - |
| 10 | world | Wild wrath: sources (kills, conquest, felling, fire, barrow blocks, night camping), decay, stages, `wildStage` / `wildHostileTo`, sapling calming, `/lotr territory wrath`, GameTests | 1.5 | G3 | sonnet-3 | 4 |
| 11 | npc | Wild push-back: stage 1-3 behaviour (spawn multipliers, hostility flip for Ents and Huorns, rooted and barrow-fog effects, hunts) | 2 | G4 | sonnet-7 | 10, wild NPC types |
| 12 | npc | Wild marches as a world-event type (target choice, host composition, resolution, control report) | 1.5 | G4 | sonnet-8 | 11, world-events framework |
| 12b | npc | March damage: limits, per-block checks, journal-first changes, exact restore on end / stop / startup, item give-back, GameTests (reuses the war-camp journal) | 1.5 | G4 | sonnet-8 | 12 |
| 12c | world | Calming the wild: sapling, quiet-forest day, heart-glade offering, barrow treasure return, cairn flowers; daily limits; heart-glade landmark placement | 1.5 | G4 | sonnet-3 | 10, structure spec (barrows, cairns, glade), core marker component |
| 13 | world | Held structures and villages: index at generation, standard and guard-post swaps (loaded and lazy), protection and block checks, swap queue | 2 | G4 | sonnet-5 | 9, 4 |
| 14 | npc | Garrison and village-guard swap (old guard removal, spawner faction); residents' trade, hire and mini-quest gating: occupier gate through `gatingFaction` plus >= 0 with the residents' own faction, with both refusal lines | 1.5 | G4 | sonnet-7 | 9, 13 |
| 15 | npc | Invasions: territory multipliers, counter-invasions, outcome reports (inside the invasion spec's tickets) | 1 | G4 | sonnet-8 | invasion spec, 4 |
| 16 | progression | Notifications UI (flip title, contested toast, wild warnings), conquest-kills toggle in the menu, territory achievements (incl. Legacy's view-your-conquest and march outcomes) and the conquest-earned stat | 1 | G4 | sonnet-9 | 4, 5 |

## Open questions for the maintainer

Answered on PR #183. Revision 2: provinces as GeoJSON polygons; villages change hands; wild owners that push back; heatmap on the Map page and at a command table; unpledged players gated at +500; absent-defender slowdown on. Revision 3: occupied villages also need >= 0 with the residents' faction; marches may damage walls with exact restore; all three wild owners can be calmed; flip-speed defaults kept, tune in playtest. Still open:

1. **Unpledged gate.** +500 at x0.5, one faction per kill (rationale in section 1). OK, or full share?
2. **Fangorn and the `fangorn` faction.** Fangorn's land is wild (never conquered) while `fangorn` stays a pledgeable faction for alignment, hiring and the Ents' friendship. OK?
