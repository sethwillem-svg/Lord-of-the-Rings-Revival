# World structures

- **Pod:** world
- **Lead:** Opus 2
- **Status:** draft, revision 2 (the maintainer's rule for parity: "match Legacy"; additions are marked **[Ours]**). Revision 2 applies the maintainer's answers on PR #184: procedural first with NBT for landmarks, plus vetted third-party templates (section 11b); overlaps allowed as in Legacy, with venue and index rules for them (section 2e); Shire hamlets and bounder posts; Hobbiton 160-200 blocks east of the landing; bandit hideouts in Bree-land, the Lone-lands and the Shire edges; village lanes; standards breakable only by the holder's enemies; guarded mine entrances.
- **Depends on:** `specs/world/dimension-and-mapgen.md` (canvas, biome ids, `has_structure` tags, landmark corridors, NovoAtlas terrain), `specs/world/waypoints-travel.md` (map source, road network, road feature and bridges, landing point), `specs/core/core-apis.md` (factions, `LandProtection`, registers, datagen rules, `/lotr` root), `specs/npc/npc-base.md` (NPC types, roles, home leash, spawn rules), `specs/progression/quests-fellowships-banners.md` (faction banner block, banner designs, structure banners). Draft specs that depend on this one: `specs/npc/nemesis.md` (PR #182: event venues, venue tags, `core.api.StructureVenues`, density target) and `specs/world/territory.md` (PR #183: held structures, standards, garrison swaps, strongholds, villages changing hands, wild provinces).
- **Sources:** `docs/reference/legacy/structures.md` (every Legacy structure type with its biomes and chances, loot, fixed placements, garrisons, banners; our own words), `docs/reference/legacy/biomes.md`, `docs/reference/legacy/world-map-travel.md`. All layouts, templates, names and text are our own.
- **Tags:** **[Legacy]** = behaviour and numbers taken from Legacy 36.15. **[Ours]** = our addition, mostly to serve the nemesis and territory specs.

## Summary

Middle-earth is full of places built by its peoples and left by older ones: hobbit holes and taverns, Bree-land hamlets, Gondor watchforts, orc camps, ranger watchtowers, barrows, broken Arnor towers. This spec ports Legacy's whole structure catalogue (about 104 scattered types, 11 village cultures with about 130 building pieces, and about 15 special generators) with Legacy's per-biome chances, garrisons and loot, onto NeoForge 1.21.1 worldgen over the NovoAtlas image terrain.

It then adds what the war systems need. Every structure declares a **faction owner** and a **venue kind** (outpost, castle, camp, watchtower, tower, ruin, hideout, bridge, village), carried as structure tags. Holdable sites fly **standards** (faction banners at recorded spots) and have **garrison slots** that territory can hand to a new holder. Villages are settlements with **guard posts**. Strongholds and landmarks sit at fixed map positions authored in the map source. A World-side **structure index** answers `core.api.StructureVenues` queries without generating terrain.

The recommended build approach is **procedural first**: structures are original Java builders on a small shared building kit, run by vanilla's structure system (structure sets, starts, pieces, terrain adaptation, `/locate`). Hand-built NBT templates are used only for a short list of landmarks that deserve hand art. Section 11 explains why.

## Player-facing behavior

1. **Exploring.** Each region has its own builds. The Shire is dotted with hobbit holes, taverns, windmills and farms; Bree-land has hamlets with hedges and a guard house; the Lone-lands have ranger camps, broken towers, hill-men and orc camps; the Barrow-downs are crowded with stone-ringed mounds. Structures sit on the land as it is: they follow slopes with foundations and do not appear on water, steep ground or roads.
2. **People live in them.** Inns have innkeepers, smithies smiths, forts captains and soldiers. Garrisons of forts, camps and towers refill a few minutes after they are killed, but never while a player stands close by. Villages have residents and guards that also refill.
3. **Loot.** Chests hold loot that fits the place: larders in hobbit holes, weapons in forts, old gear and coins in ruins, grave goods in barrows. Some grave chests wake a guardian when opened.
4. **Flags.** Forts, camps and towers fly their faction's standard at the gate and on the roof. When a province falls (territory), those standards change to the conqueror's and so does the garrison. Only the holder's enemies (alignment below 0) can tear a standard down; a broken standard is not replaced.
5. **Fixed places.** Bree and its three hamlets, the Brandywine Bridge, Bucklebury Ferry, Hobbiton and (later) every faction capital are at the same spot in every world, matching the map.
6. **Events find them.** World events (nemesis spec) stage ambushes at bridges, raids on villages, duels in castles and rescues in hideouts, so the land's structures are where the war happens.
7. **Operators** can place any structure with `/lotr structure place <id>` and inspect one with `/lotr structure info`.

## 1. Catalogue

### 1a. What every structure declares

| Field | Meaning | Example |
| --- | --- | --- |
| `id` | `lotrrevival:<region>/<name>` | `lotrrevival:eriador/ranger_camp` |
| `class` | `settlement`, `fort`, `camp`, `tower`, `home`, `ruin`, `tomb`, `dungeon`, `landmark`, `decoration` | `camp` |
| `faction` | the builders' faction, or none (ruins, decoration) | `lotrrevival:dunedain_north` |
| `venues` | 0+ venue kinds (section 6); written as structure tags `#lotrrevival:event_venues/<kind>` | `[camp]` |
| `holdable` | true for outposts, castles, camps, watchtowers and towers with a faction (territory section 5) | true |
| `footprint` | width x length x height in blocks, target +/- 30% (from the Legacy catalogue) | 20 x 20 x 6 |
| `ground` | max height spread over the footprint, water allowed or not, underground or surface | spread 6, dry |
| `placement` | scattered (biome chances), village grid, fixed, or special (section 2) | scattered |
| `garrison` | garrison slots (section 5) | 1 slot: ranger soldiers 3 : archers 1, cap 12 |
| `residents` | one-time NPCs (section 5) | captain (command table room) |
| `loot` | loot table per chest kind (section 10) | `lotrrevival:chests/ranger_camp` |
| `standards` | number of standard spots (section 4) | 2 |
| `tables` | crafting stations: faction table, command table, forge, oven | ranger table, command table |

### 1b. Venue mapping rules [Ours]

| Venue kind | Which structures | Holdable |
| --- | --- | --- |
| `outpost` | a faction's small fortified post with a garrison: watchforts, hill-forts, dwarf strongholds and mine entrances, Ithilien hideout, bounder posts... | yes |
| `castle` | a faction's keep or fortress: the "fort" variants of village cultures, Black Uruk fort, Dale fortress, all stronghold landmarks | yes |
| `camp` | permanent worldgen camps: ranger, orc (Mordor, Gundabad, Angmar, Dol Guldur, Uruk), corsair, mercenary, desert, Dorwinion camps and warg pits | yes |
| `watchtower` | small lookouts (footprint under 10 x 10, garrison cap <= 6): turrets, beacon towers, Rohan, Dale, ranger, Harnennor watchtowers, wood-elf trunk towers, bounder posts | yes |
| `tower` | larger standing towers: Mordor, Angmar, Dol Guldur, orc slaver, dwarven, high-elven, Tower Hills towers | yes |
| `ruin` | abandoned builds with open floor >= 7 x 7: stone ruins, ruined towers and forts, ruined, burnt and rotten houses, barrows, Rhudaur castle, pyramids | no |
| `hideout` | lairs: corsair coves, bandit hideouts [Ours] | no |
| `bridge` | road crossings of mapped rivers and lakes, and landmark bridges (section 9) | no |
| `village` | every settlement: village cultures, scattered villages (Morwaith, Dale, hill-men), fixed villages, Shire hamlets [Ours] | by the village rules (section 7) |

- A structure under 7 x 7 (small stone ruin, obelisk, picnic bench, beacon pyre) is never a venue.
- Homes and civilian buildings outside villages (hobbit holes, elf houses, smithies, taverns, farms) are not venues.
- Underground and underwater structures (orc dungeons, troll hoards, dwarven mine corridors, underwater ruin) are never venues; a mine **entrance** can be.

### 1c. The catalogue by region

Chances ("1 in N chunks" per biome) and grid settings are **[Legacy]** and are listed per biome in `docs/reference/legacy/structures.md` sections 3-6; datagen copies them into each structure's chance table (section 2a). This table assigns our ids, venue kinds and factions. "Phase" is the delivery phase in "Tickets": S1 is the pilot window (Shire to the Lone-lands), S2 the region it belongs to when that region is painted.

**Eriador pilot (S1)**

| Our id (under `lotrrevival:`) | Class | Faction | Venues | Notes |
| --- | --- | --- | --- | --- |
| `shire/hobbit_hole`, `shire/hobbit_burrow`, `shire/hobbit_house` | home | hobbit | - | Family, larder, study, treasure chests, oven, hobbit table |
| `shire/hobbit_tavern` | home | hobbit | - | Innkeeper, hobbits, a Shirriff; about 37 x 20 |
| `shire/hobbit_farm`, `shire/hobbit_windmill` | home | hobbit | - | Farmer and farmhands |
| `shire/picnic_bench` | decoration | hobbit | - | A picnicking hobbit |
| `shire/hamlet` **[Ours]** | settlement | hobbit | village | 4-7 hobbit holes and houses around a green, one tavern 1 in 2, one bounder post as guard post. Grid 12, claim 3 chunks; chance shire 0.35, shire_woodlands, shire_moors, white_downs 0.25 |
| `shire/bounder_post` **[Ours]** | fort | hobbit | watchtower | A small hedged watch-house on a rise, 7 x 7; garrison Bounders cap 3. 1 in 700 in every Shire biome |
| `bree/hamlet`, `bree/village` | settlement | bree | village | Legacy Bree culture (section 3 of the reference): houses, hobbit burrows, inn, market, smithy, stables, hedges and gates, guard house as guard post |
| `bree/bree`, `bree/staddle`, `bree/combe`, `bree/archet` | settlement | bree | village | Fixed (section 8). Bree is walled, gatehouse and captain's office; Bree is a stronghold province (territory) |
| `bree/bandit_hideout` **[Ours]** | dungeon | ruffians | hideout | A dug-in lair under a thicket or ruined cottage, 9 x 9, 3-5 ruffians (garrison cap 5, faction `ruffians`, not holdable by territory), loot chest of stolen goods. 1 in 1,200 in bree_land, chetwood; 1 in 2,000 in lone_lands; 1 in 2,500 in the Shire edges (shire_moors, shire_marshes, shire_woodlands, white_downs) |
| `eriador/ranger_camp` | camp | dunedain_north | camp | Tents, captain, command table, ranger table; garrison cap 12 |
| `eriador/ranger_watchtower` | tower | dunedain_north | watchtower | 9 x 9 x 25, garrison cap 8 |
| `eriador/dunedain_village` | settlement | dunedain_north | village | The Angle (later region), palisade, lodge, smithy |
| `eriador/ruined_dunedain_tower` | ruin | - | ruin | Arnor gear, coins |
| `eriador/rhudaur_castle` | ruin | - | ruin | Large ruined castle with tower, dungeon and house chests |
| `eriador/gundabad_camp` | camp | gundabad | camp | Gundabad mercenary captain, orc trader, tents, Gundabad table; garrison cap 12 |
| `eriador/hillman_village`, `eriador/hillman_house` | settlement / home | angmar | village / - | Hill-men houses, chieftain house with Angmar table |
| `eriador/tower_hills_tower` | tower | high_elf | tower | Tall white tower, Lindon style |
| `eriador/barrow` | tomb | - | ruin | Stone-ringed mound, burial chamber, guardian chest (wight) |
| `blue_mountains/stronghold` | fort | blue_mountains | outpost | Cut into rock, forge, command table, larder; garrison cap 8 |
| `blue_mountains/mine` | dungeon | blue_mountains | outpost (entrance only) | Legacy mine; **[Ours, maintainer decision]** the surface entrance gets a garrison slot (dwarf guards cap 4) and a standard, so it is a holdable outpost and a mine-defence venue. Same for every dwarven mine in later regions |
| `blue_mountains/dwarf_house`, `blue_mountains/smithy` | home | blue_mountains | - | Carved into rock (4 tries per chunk, y 70-150) |
| `ruin/stone_ruin` (styles: stone, arnor) | ruin | - | ruin | Broken walls in the local stone; chest 1 in 3. **[Ours]** Arnor style also 1 in 800 in tower_hills (density) |
| `ruin/small_stone_ruin` | ruin | - | - | Under 7 x 7 |
| `ruin/ruined_house`, `ruin/burnt_house`, `ruin/rotten_house` | ruin | - | ruin | Abandoned cottages, ruined-house chest |
| `eriador/old_forest_glade` **[Ours]** | landmark | - | - | The heart glade the territory spec needs (fixed, section 8) |
| `eriador/barrow_cairns` **[Ours]** | decoration | - | - | Cairn stones for the territory spec's barrow calming, placed by the barrow builder around 1 barrow in 4 |

**Later regions (S2)**, one ticket group per region, in the order the regions are painted. Each row expands into the Legacy types listed in the reference, with the venue kind given here.

| Region | Structures (our ids under `lotrrevival:<region>/`) and venue kinds |
| --- | --- |
| Lindon, Eregion, Rivendell | high_elf_house, hall, forge (-); high_elven_tower (tower); rivendell_house, hall, forge (-); ruined_high_elven_turret, ruined_eregion_forge (ruin); underwater_elven_ruin (-) |
| Misty and Grey Mountains, Angmar, Ettenmoors | angmar_camp (camp), angmar_tower (tower), angmar_shrine (-), angmar_warg_pit (camp), ruined_dwarven_tower (ruin), orc_dungeon (-), troll_hoard (-), dwarven_mine_ruined (-), blasted_land (-) |
| Rhovanion: Mirkwood, Dale, Erebor, Iron Hills | wood_elf_tower (watchtower), wood_elf_house, forge, platform (-), ruined_wood_elf_tower (ruin); dol_guldur_tower (tower), camp (camp), altar (-), spider_pit (-); dale_village (village), dale_watchtower (watchtower), dale_fortress (castle); dwarven_tower (tower), dwarf_house, smithy (-), dwarven_mine (outpost entrance) |
| Lothlórien | galadhrim_flet, lord_flet (-, built into mallorns), galadhrim_forge (-) |
| Dunland, Isengard | dunland_hill_fort (outpost), dunlending_house, tavern (-), campfire (-); uruk_camp (camp), uruk_warg_pit (camp); Isengard ring wall (landmark, fixed [Legacy]) |
| Rohan | rohan_village (village), rohan_fort (castle), rohan_watchtower (watchtower), rohan_barrow (ruin), ruined_rohan_watchtower (ruin) |
| Gondor and fiefs | gondor_village, town (village), gondor_fort (castle), watchfort per fief (outpost), turret (watchtower), beacon_tower (watchtower), ruined_beacon_tower, ruined_gondor_tower, gondor_ruins, obelisk (ruin / -), ithilien_hideout (outpost); Rammas Echor (landmark, fixed [Legacy]) |
| Mordor, Nurn | mordor_camp (camp), mordor_warg_pit (camp), mordor_tower (tower), black_uruk_fort (castle), spider_pit (-), orc_slaver_tower (tower), nurn_wheat_farm (-) |
| Rhûn, Dorwinion | rhun_village, town (village), rhun_fort (castle), rhun_tower (tower); dorwinion_camp (camp), house, elf_house, brewery, garden, bath (-) |
| Harad, Umbar, Far Harad | harnennor_village (village), harnennor_fort (castle), harnennor_tower (watchtower); southron_village, town (village), southron_fort (castle); umbar_village, town, fort; nomad_camp (village), desert_camp (camp); corsair_camp (camp), corsair_cove (hideout); gulf_village, town (village), war_camp (camp); morwaith_village (village), merc_camp (camp); taurethrim_village (village), taurethrim_pyramid (ruin); harad_pyramid, ruined_fort (ruin), harad_obelisk, mumak_skeleton (-) |
| Easter eggs (fixed [Legacy]) | marsh hut, the lone northern house, the southern ticket booth, the Mordor cherry tree (all original builds of our own; same map pixels as Legacy, converted to our heightmap pixels) |

## 2. Placement

### 2a. Scattered structures [Legacy]

| Rule | Value | Notes |
| --- | --- | --- |
| Chance | each structure has a table `biome (or biome tag) -> N`: a chunk whose centre column is in that biome rolls 1 in N | [Legacy] numbers from the reference |
| Roll | seeded from world seed, chunk x/z and the structure's id hash, so rolls are deterministic and independent per structure | [Legacy] |
| Position | a random column in the chunk (0-15, 0-15) and a random rotation | [Legacy] |
| Overlaps | allowed: every structure that wins its roll and passes its own checks generates, even if another structure already sits there or in the same chunk. Section 2e says how venues, garrisons and the index cope | [Legacy] (maintainer decision) |
| Ground | every footprint column (sampled every 2 blocks, plus corners) must be dry natural surface: the top block is not water, lava or ice and not leaves; the spread between highest and lowest sample must be <= `ground.spread` (default 8; Legacy used 4-12 by type) | [Legacy]. Heights come from the generator's base height (image terrain), so the check runs before any block exists |
| Foundations | after placing, walls and floors fill down to the ground with the structure's foundation block, at most 12 blocks; below that the column is skipped | [Legacy] |
| Terrain adaptation | `beard_thin` for surface builds, `none` for underground and tree-borne builds | Vanilla. Works because NovoAtlas uses the vanilla noise chunk generator |
| Roads (hard) | dropped if a road edge is within 16 blocks of the chunk centre | [Legacy]. Uses the road network index (`world.road`) |
| Landmarks and landing (hard) | dropped if its box comes within a fixed landmark's `radius` + 64 blocks, or within 96 blocks of the Middle-earth landing point | **[Ours]** |
| Banner-protected land (hard) | dropped if its box intersects the cube of any protecting banner (player or structure banner), read from a thread-safe snapshot of the banner index (`LandProtection.anyProtectedIn(level, box)`, new, see Data and APIs) | **[Ours]**. Blocks are only ever placed in newly generated chunks, but a banner's cube can reach into them |
| Villages | not a keep-out: scattered structures may land inside a village, as in Legacy | [Legacy] |
| Axis cross | none | Legacy's 32-block exclusion cross around x = 0 and z = 0 is not kept; the landing rule replaces it |
| Variants | none of our biomes have vineyard or orchard variants yet; when they do, they get `#lotrrevival:no_structures` | [Legacy] |

### 2b. Villages [Legacy]

| Rule | Value |
| --- | --- |
| Grid | per culture: cell size 10-16 chunks, jitter 1-2 chunks, chance per biome (reference section 3). One candidate per cell |
| Claim | 3-6 chunks radius by culture (Bree 4, Gondor 5, Rhûn 6...); fixed villages use their own radius (Bree 13) |
| Biomes | every chunk of the claim must be in one of the culture's village biomes |
| Keep-outs (hard) | dropped when a road, a landmark or a fixed mountain is within (claim + 8 blocks) x 1.41 of the centre, so random villages never sit on roads [Legacy]; **[Ours]** also dropped within 512 blocks of the landing point or when the claim intersects a protecting banner's cube. Villages may overlap scattered structures and other cultures' villages, as in Legacy |
| Layout | the culture's layout plan picks a type (hamlet, village, town, fort by weight), places a centre piece and building plots along its streets, each plot rotated to face a street |
| Terrain | each plot is flattened to the plot's median height (`beard_box`), streets follow the ground and use the culture's road style (`world.road` styles) |
| Access lane **[Ours]** | a village within 256 blocks of a road lays a 3-wide lane in its street style from its nearest gate to the nearest road point (straight line, following the ground, bridging water like a road). This gives caravans and patrols a path between settlements. The lane is part of the village and never enters another structure's box |

### 2c. Fixed structures and landmarks [Legacy + Ours]

- Authored in the map source: `src/main/map/landmarks.geojson` gains optional properties `structure` (structure id), `rotation` (0, 90, 180, 270), `radius` (claim and corridor radius in blocks), `stronghold` (true for a province's stronghold build) and `faction`. A landmark without `structure` stays a named point as today.
- The map build (buildSrc, next to `TravelJson`) turns every landmark with a `structure` into a structure set with the placement `lotrrevival:fixed` (exactly one start at that block position) and a corridor entry for `map_corridors` (detail suppressed inside `radius`, ramp over 64 blocks, as `dimension-and-mapgen.md` already says).
- Validator: the structure id exists; position inside a painted window and >= 1,024 blocks from the canvas edge; `expect_biome` matches; two fixed structures' radii do not overlap; no fixed structure within 128 blocks of the landing point except the landing itself.
- Fixed structures skip the ground check; the corridor gives them flat land.

### 2d. Special placements [Legacy]

| Kind | Rule |
| --- | --- |
| Rock-carved houses (dwarves) | 4 tries per chunk at a random column in y 70-130 (Iron Hills) or 70-150 (Blue Mountains) where a rock face is found; carves the house into the rock |
| Underground (orc dungeons, troll hoards) | 6 tries per chunk in their biomes, at a random height under the surface, needs a cavity |
| Tree-borne (flets, wood-elf platforms) | placed by the giant-tree feature into the crown of the tree, not as structures (features, decoration step) |
| Mines | a surface entrance at a scattered position (1 in 150 chunks); corridors branch underground up to about 6 pieces deep (vanilla jigsaw depth limit of our own pool, or procedural pieces) |
| Underwater ruin | 1 in 200 chunks in deep sea west of Lindon, on the sea floor |

### 2e. Overlapping structures [Ours rules on Legacy behaviour]

Overlaps are allowed (2a), so two structures can share ground: a ranger camp across a ruin, a ruffian hideout under a hamlet's edge. Blocks simply interleave, as in Legacy. The war systems need one clear answer per place, so the index resolves overlaps:

| Rule | Value |
| --- | --- |
| Core box | each record has a **core box**: the declared footprint at ground level, inset by 2 blocks, from 2 below the ground to the top of the build. For a village, the core area is its claim circle |
| Who competes | only records with at least one venue kind. Homes, decoration and non-venue ruins never shadow anything and are never shadowed |
| Overlap | two competing records overlap when their core boxes (or a core box and a village claim) intersect |
| Winner | a deterministic rank, independent of load order: villages first, then the larger core area (x z), then class order `castle` > `outpost` > `camp` > `tower` > `watchtower` > `hideout` > `ruin` > `bridge`, then the lower record id |
| Loser | marked `shadowed_by` the winner. A shadowed record is **not a venue** (`find` skips it, `at` returns the winner), is **not holdable** (territory ignores it; its standards stay as decoration and are never swapped) and does not count for the density audit |
| Garrisons of a loser | its `garrison` slots are **disabled** when its faction is an enemy of the winner's faction (core relations), so two hostile garrisons never respawn into each other forever. Otherwise they keep running. `once` and `guardian` slots always run |
| Late discovery | a record can become shadowed when an overlapping, higher-ranked record is indexed later (its start lies in a chunk loaded later). Then: it stops being returned as a venue, territory drops its hold entry, and NPCs already spawned stay (they are not removed); its disabled slots spawn no more |
| Chains | rank is applied pairwise; a record shadowed by any higher-ranked overlapping record is shadowed. A shadowed record does not shadow others |
| Bridges | bridge sites compete like other venues, but a landmark bridge always wins over a generated site on the same crossing |
| Hard exclusions still apply | fixed landmarks (plus 64 blocks), roads within 16 blocks, banner-protected land and the landing point keep every random structure out (2a, 2b) |

The density audit's 60% factor also covers losses to shadowing; the `/lotr structure density` sampler counts only unshadowed venues.

## 3. Generation order and NovoAtlas

1. Structure starts (vanilla `STRUCTURE_STARTS` stage) use the NovoAtlas biome source and the generator's base heights, so ground checks see the image terrain before blocks exist.
2. Terrain noise with structure terrain adaptation (beardifier).
3. Structure pieces place blocks (surface structures step), then the road feature (which skips structure boxes, `waypoints-travel.md`), then vegetation.
- No vanilla structures spawn in Middle-earth (our biomes are in no `#minecraft:has_structure/*` tag). Our structures use `#lotrrevival:has_structure/<id>` biome tags generated from their chance tables, so `/locate structure` works.
- Structures never generate in the overworld through worldgen, but `/lotr structure place` works in any dimension.

## 4. Standards [Ours, built on Legacy's decorative banners]

| Rule | Value |
| --- | --- |
| What | a **standard** is the faction banner block owned by progression (`lotrrevival:faction_banner` standing or wall form) with the holder's default design, placed on an ordinary block (never on bronze, silver or gold), so it never protects land. Legacy's structure banners were decoration too |
| Where | at recorded spots chosen by the builder: gate, roof or tower top, captain's room. 1-4 per holdable structure; 1 per village guard post |
| Recorded | each standard spot (position, standing or wall, facing) is saved in the structure's index record (section 6) |
| Placed design | the current holder's design at generation time (territory: a structure generated after a flip uses the current holder) |
| Breakable | **only by players whose alignment with the current holder is below 0** (the holder's enemies tear down its flag; its friends and neutrals cannot). Creative-mode operators always can. Explosions, mobs and NPCs never break a standard. A broken standard drops a banner item of that design and is not restored; later swaps skip a spot whose block is no longer our standard (territory rule). Enforced by the `FactionStandards` backend (progression), which marks standards on the banner block entity (a `standard` flag, distinct from the protecting structure-banner flag) |
| Swap | territory replaces the design at a spot through `core.api.FactionStandards` (below); this spec only places and records |
| Protecting structure banner | only the jungle pyramid (later) places a protecting structure banner, radius 64, as in Legacy |

`core.api.FactionStandards` **[Ours, pod:core holder, progression backend]**: `place(level, pos, faction, wall, facing)`, `factionAt(level, pos)` (empty if the block is not a standard), `replace(level, pos, faction)` (keeps form and facing), and the break rule above. World never imports progression classes. The holder used for the break check comes from `StructureVenues.at(level, pos)` (current holder of the record that owns the spot).

## 5. Garrisons and residents [Legacy numbers]

Structures do not spawn NPCs themselves. They record **slots**; the NPC pod's structure population service fills them (World cannot depend on NPC classes).

| Slot field | Meaning | Legacy values |
| --- | --- | --- |
| `mode` | `garrison` (refills), `once` (spawned on first load, never replaced), `guardian` (spawned when a linked chest is first opened) | |
| `pos` | anchor, relative to the structure | |
| `roles` | weighted roles: `soldier`, `archer`, `captain`, `guard`, `cavalry`, `resident`, `trader:<kind>`, `smith`, `innkeeper`, `beast` (wargs, spiders) | two types at 2 : 1 for soldier / archer mixes |
| `follows_holder` | true: the NPC faction is the structure's current holder; false: the generation faction (residents, traders) | |
| `cap` | max NPCs of the slot's types inside the count box | forts and camps 12 (8-16), towers 6-8, watchtowers 4, Black Uruk fort 24, Bree hamlet guards 8 |
| `count_box` | half-size around the anchor | 24 around, 12 up and down (forts and camps); 40-64 for villages |
| `spread` | spawn positions around the anchor | 12-32 |
| `leash` | home radius | 16-64 (most 32) |
| `interval` | ticks between attempts | 3,600 (3 min) |
| `no_player` | no attempt while a player is within | 24 blocks |
| `enemy_block` | natural spawns of factions hostile to the slot's faction are blocked within | 60 blocks for garrisons, 64 for villages |
| `mounts` | default, mounted, unmounted | cavalry slots mounted |

- Population rules (NPC pod): an attempt runs only when every chunk of the count box is loaded; 16 position tries per attempt on a solid block with two free blocks above; spawned NPCs are persistent with home = anchor (or spawn spot) and `leash`; `once` NPCs spawn when the structure's chunks are first loaded and tick (not during worldgen); a `once` NPC killed is gone for good. Garrisons do not spawn inside land where `LandProtection.blocksFactionSpawn` is true for their faction.
- Roles map to entity types per faction through data `data/lotrrevival/lotrrevival/garrison_roster/<faction>.json` (NPC pod), so a holder change needs no structure change. Village residents use the residents' faction's roster.
- Captains with command tables are `once` residents of the captain's room; their table is a core block (territory's command table).

## 6. Structure index and the `StructureVenues` backend [Ours]

### Index

| Rule | Value |
| --- | --- |
| Storage | SavedData `lotrrevival_structures` in each level that has our structures (Middle-earth; any level where an operator placed one), bucketed by 512 x 512 block region |
| Record | id (`<structure id>@<start chunk x>,<start chunk z>`), structure id, class, venue kinds, generation faction, bounds (box of all pieces), centre (centre of the main piece at ground level), province (territory lookup of the centre), standard spots, slots, village data (claim, guard posts), landmark and stronghold flags, first-seen game time |
| Filled when | the first full chunk that holds the start **or a reference** to it is loaded on the server thread. A reference resolves the start from its chunk only if that chunk is already at least at the structure-start stage in memory or on disk; otherwise the record is retried on the next load. **Never generates chunks** |
| Stable | records are never removed when blocks are broken. A record whose structure id is no longer registered (data pack removed) stays but is reported as inactive |
| Holder | not stored here; territory keeps holder and epoch per record id in its own data. **Required change to the territory spec (PR #183):** its `lotrrevival_territory_holds` entries must be cut to **structure id + holder + epoch**; bounds, centre, province, generation faction and standard spots are read from this index |
| Overlaps | each record stores its core box and `shadowed_by` (section 2e) |
| Bridges | virtual records for bridge venues (section 9), activated when the chunk at their centre is first loaded |

### `core.api.StructureVenues` (core holder, World backend)

| Query | Answer |
| --- | --- |
| `find(level, centre, kind, minDistance, maxDistance, limit)` | venues of that kind whose centre is in the band, nearest to the band middle first; at most `limit` (default 8). Reads only the index |
| `get(level, id)` | one venue |
| `at(level, pos)` | the venue whose bounds contain the position, if any |
| `garrisonsNear(level, pos, radius)` | records with slots, for the NPC pod's population service and its enemy-spawn block |
| Venue record | id, structure id, kinds, bounds, centre, owning faction (current holder for holdable sites, generation faction otherwise, none for ruins), `hasResidents` (any slot), `protected` (`LandProtection` says any player banner covers the centre or a corner), landmark and stronghold flags, standard spots, slots |

- Budget: a `find` touches at most the regions overlapping the band (<= 16 for a 512-block band) and at most 256 records; results cached per (region, kind) for 5 minutes, invalidated when a record is added in that region. This meets the nemesis spec's limits (one lookup per roll, results cached per 512-block region for 5 min).
- `protected` is computed at query time, never cached.

### Density target [Ours, from the nemesis spec]

In settled and contested land of every region: on average **>= 2 venues within 256 blocks** of any point (one per 400 chunks), at least one outpost and one camp per faction region, a castle (stronghold) per faction, watchtowers or towers along borders, ruins and hideouts in wild land, bridges on mapped road crossings. Wild-owner provinces (Old Forest, Fangorn) are exempt: they have their own landmark and no peoples.

Expected venues within 256 blocks for the pilot biomes, from the chance tables, assuming 60% of scattered rolls pass the ground check and 50% of village candidates are accepted:

| Biome | Legacy set only | With [Ours] additions | Additions |
| --- | --- | --- | --- |
| shire | 0.6 | 2.3 | hamlets 0.35, bounder posts 1/700 |
| shire_woodlands, shire_moors, white_downs | 0.6 | 2.0 | hamlets 0.25, bounder posts 1/700 |
| shire_marshes | 1.9 (rotten houses) | 2.6 | bounder posts 1/700 |
| bree_land, chetwood | 3.8 | 4.2 | bandit hideouts 1/1,200 |
| midgewater_marshes | 4.8 | 4.8 | - |
| lone_lands | 5.3 | 5.7 | bandit hideouts 1/2,000 |
| barrow_downs | 81 (barrows) | 81 | - |
| tower_hills | 1.4 | 2.0 | Arnor stone ruins 1/800 |
| ered_luin_foothills | 1.2 | 4.4 | garrisoned mine entrances (Legacy mines, 1/150) |
| old_forest | 0 | 0 | exempt (wild) |

A datagen-time **density audit** computes this table for every biome from the structure data and fails the build when a non-exempt biome with a painted region is below 2.0 (tunable; it prints the table). An operator command samples the real index (section 13).

## 7. Villages as settlements [Legacy + Ours]

| Rule | Value |
| --- | --- |
| Record | a village is one index record: claim radius, culture, residents' faction, guard posts, layout type (hamlet, village, town, fort) |
| Guard posts | every village has >= 1 guard post: a gatehouse, guard house, watch post or the culture's watchtower piece. Hamlets 1, villages 1-2, towns 2-3 (Legacy towns had 3 guard spawners). Each guard post has 1 standard spot and 1 garrison slot (`guard` roles, `follows_holder`, cap 8, count box 40) [Legacy numbers] |
| Residents | one or more `garrison`-mode resident slots that refill the townsfolk ([Legacy]: Bree hamlet men 20 and hobbits 6 in a 40-block box; towns up to 100 in an 80-block box), not `follows_holder` |
| Traders, smiths, innkeepers, captains | `once` slots in their buildings |
| Fort type | a village culture's "fort" layout (Gondor, Rohan, Rhûn, Harad...) is a `castle` venue, holdable by the fort rules, not a village |
| Venue | `village`; held by territory's village rules (guards swap, residents stay) |

## 8. Strongholds and landmarks [Ours, Legacy fixed builds included]

| Landmark | Structure | Kind | Phase | Notes |
| --- | --- | --- | --- | --- |
| Bree, Staddle, Combe, Archet | `bree/bree` and hamlets | village | S1 | [Legacy] fixed villages; Bree claim 208 blocks. Positions from our `landmarks.geojson` (already present) |
| Hobbiton | `shire/hobbiton` | village (stronghold) | S1 | A larger Shire hamlet with the Shirriff house as guard post; centred **160-200 blocks east** of the landing point (maintainer decision), exact pixel chosen in the map source so the claim stays clear of the landing (>= 128 blocks) and of the East Road |
| Brandywine Bridge | `shire/brandywine_bridge` | bridge | S1 | Three-arch stone bridge over the Brandywine; road feature skips its box |
| Bucklebury Ferry | `shire/bucklebury_ferry` | decoration | S1 | Landing stages on both banks and a raft |
| Old Forest heart glade | `eriador/old_forest_glade` | - | S1 | For territory's calming offering |
| Weathertop | `eriador/weathertop_ruin` | ruin | S2 (Lone-lands east) | Ring of broken walls on the hilltop |
| Tower Hills towers | none fixed | - | - | `dimension-and-mapgen.md` lists them as landmarks; we follow Legacy instead (scattered, 1 in 600 in tower_hills). A fixed tallest tower can be added later as a landmark |
| Faction capitals (one per faction at least) | `<region>/<faction>_stronghold` | castle (stronghold) | S2 | Mithlond, Thorin's Halls, Rivendell, Caras Galadhon, the Elvenking's halls, Erebor, Dale, Edoras, Helm's Deep, Minas Tirith, Dol Amroth, Isengard (Legacy ring wall, radius 400), Barad-dûr, Minas Morgul, Dol Guldur, Mount Gundabad, Carn Dûm, Umbar and one per Harad and Rhûn faction. Legacy had only waypoints here (plus two walls), so these builds are ours. Start each as a procedural keep sized 48-96 blocks; replace with hand-built NBT when art exists |
| Rammas Echor | `gondor/rammas_echor` | - | S2 | [Legacy] ring wall radius 500 around Minas Tirith, top y 85 |
| Easter eggs | four builds | - | S2 | [Legacy] positions |

- Stronghold builds are `castle` venues with `stronghold: true`; territory never swaps them.
- Every stronghold has >= 2 standard spots and >= 2 garrison slots (gate guard, keep guard), cap 16 each.

## 9. Bridges [Ours, on the road-bridge work in #87 / PR #180]

- Road bridges are built by the road feature (`waypoints-travel.md`, #87). They are not structures; this spec only makes the large ones **venues**.
- The map build computes **bridge sites** from the map source: every crossing of a road polyline with a river polyline (`water.geojson`, width >= 8 blocks) or a lake polygon, plus every landmark with kind bridge. Each site: id `bridge/<road id>/<n>`, centre (the crossing point), bounds (road width + 4 across, river width + 16 along, from y water - 4 to deck + 6), the road id and the river or lake name. Shipped as generated JSON with the roads.
- The index adds sites as virtual records, kind `bridge`, no faction, no slots; a site becomes findable when its centre chunk is first loaded.
- Small stream bridges that the road feature builds where it happens to find water are not venues.
- River-only footbridges away from roads: out of scope.

## 10. Loot [Legacy contents, datagen]

- Loot tables are generated by a World loot provider (wrapped in a delegating provider with its own name, per the repo rules) at `data/lotrrevival/loot_table/chests/<table>.json`.
- One table per Legacy chest kind (reference section 7 and the per-structure "contents" columns): larder, house, treasure, military per faction, ruin, barrow, Rohan barrow, ruined Dúnedain tower, Gondor ruins, dwarven mine corridor, orc dungeon, corsair, Mirkwood travellers, pyramid, underwater ruin, bandit stash [Ours]. Rolls as in Legacy (3-10).
- Items not yet in core use the closest existing item and a `TODO(core item)` comment in the provider; a ticket per region swaps them when core adds the item.
- Chests get their table and a seed from the structure's random when placed, so loot rolls when first opened (vanilla).
- Guardian chests: the chest is recorded with a `guardian` slot (barrow-wight, pyramid wraith); the first opening by a player triggers the slot once.

## 11. Technical approach

### Options

| | A. Vanilla jigsaw + NBT templates | B. Procedural builders (Legacy-like) | C. Hybrid: procedural first, NBT for landmarks |
| --- | --- | --- | --- |
| How content is made | build in a running game with structure blocks, save `.nbt`; or design in an external voxel tool | Java code against a small building kit | B for the catalogue, A for a short list of hand-art landmarks |
| Agent speed | slow: agents have no game client; Blockbench (#10) makes entity and block models, not structures; every build needs a human or an artist session in-game | fast: a fort is a 200-400 line builder, about 1 day per type with tests; reviewable as a diff | fast for 95% of content |
| Variation per instance | per piece only (rotation, processors, jigsaw pools) | full: random rooms, palettes, decay, fief colours, like Legacy | full |
| Matching Legacy | layouts would need re-modelling by hand | direct: Legacy's builds are procedural, so footprints, room lists and randomness map 1:1 (with our own layouts) | direct |
| Terrain fit | template sits on a flattened plot | foundations per column, sits on slopes like Legacy | both |
| Review | binary `.nbt`; needs a viewer | code diff plus generated previews (below) | both |
| Data-driven tuning | high | chances, loot and slots are data; layouts are code | high where it matters |
| Hand-art quality | best | good, but builders are less "artistic" | best for landmarks |

### Recommendation: C

1. **One structure type** `lotrrevival:built`. Its JSON names a **builder id** plus the metadata of section 1a (faction, venues, footprint, ground, chances, slots, loot, standards). Builders are Java classes registered by id in `world.structure.builder`.
2. **One piece type** `lotrrevival:built_piece` that stores builder id, origin, rotation, seed and parameters (a few bytes in the chunk). Placing a piece re-runs the builder deterministically and writes only blocks inside the chunk being generated (vanilla piece contract). Villages and big forts are several pieces (one per building or wing) so each piece stays under 48 x 48.
3. **The building kit** (`world.structure.kit`): rotated local coordinates, palettes (materials per culture and per fief, with random variants: cracked, mossy, rotten), `fill`, `walls`, `floor`, `roof` shapes, stairs and slabs, doors, windows, `foundation` (fill down), `decay(p)` for ruins, `chest(table)`, `standard()`, `slot(...)`, `table(kind)`, `bed`, `light`. Builders write into a **sink**: the level (worldgen), the GameTest level, or an in-memory grid.
4. **Templates** `lotrrevival:template` pieces load our own `.nbt` files (`data/lotrrevival/structure/landmark/...`). Standards, slots, chests and lights are marked with vanilla data-marker structure blocks (`standard`, `slot:<name>`, `loot:<table>`), turned into the same metadata as builders. Used for landmarks once an artist or Seth builds them; until then the landmark uses a builder.
5. **Previews**: a GameTest runs every registered builder in the in-memory sink with 3 seeds and 4 rotations, checks invariants (inside the declared footprint, standards and slot anchors inside bounds, every chest has a loot table, at least one door reachable from outside, no block entities outside bounds), and writes a top view and an isometric PNG per builder to `build/structure-previews/`. CI uploads that folder; PRs link the images. This is how reviewers and Seth see a structure without a client.
6. **Export** (dev only): `/lotr structure export <id> [seed]` writes a builder's output as `.nbt` so an artist can touch it up in-game and promote it to a template.

Why: agents can produce and review Java quickly and cannot build in-game; Legacy is procedural, so parity in size, room lists, random variation and terrain fit comes naturally; the vanilla structure framework still gives us chunk-safe placement, terrain adaptation, structure tags, `/locate` and saved starts. Hand art goes where players will stare at it (landmarks, capitals).

**Clean-room rule for builders:** layouts are designed new for this repo. Builders match the catalogue's footprint (+/- 30%), room list, contents and garrison, never Legacy's block-by-block layout. Implementers never see Legacy code; leads review against the reference notes only.

### 11b. Third-party templates [Ours, maintainer decision]

Templates found online may be used for landmarks and capitals (and as a starting point for any template piece), under these rules:

| Rule | Value |
| --- | --- |
| Allowed licences | an **explicit** licence that lets us adapt the work and ship it with our CC BY-NC-SA 4.0 assets: **CC0, CC BY, CC BY-SA, CC BY-NC, CC BY-NC-SA** (version 4.0, or 3.0 where the source only offers that) |
| Share-alike note | a CC BY-SA source stays CC BY-SA after our changes (it cannot become NC); its credits entry says so. CC0, CC BY and CC BY-NC sources may be shipped under our CC BY-NC-SA 4.0 |
| Never | anything with no licence or only "free to use" wording; anything from the original LOTR Mod (Legacy, Renewed) or any other All Rights Reserved source; anything ripped from servers, server maps or world downloads; anything whose uploader is not clearly the author |
| Credits | every template gets an entry in `ASSETS_CREDITS.md` (new, repo root; `NOTICE` points to it): our file path, source URL, author, licence with link, date retrieved, and our changes. The PR that adds the template also adds the entry |
| Conversion | converted to our format before use: our block palette (vanilla and `lotrrevival:` blocks only; other mods' blocks replaced), our data markers (`standard`, `slot:<name>`, `loot:<table>`, light and door markers), no entities or command blocks, no signs or books with the source's text, sized to the catalogue footprint. The converted file goes to `data/lotrrevival/structure/thirdparty/<id>.nbt` with the original kept outside the repo |
| Lead check | before an implementer uses a template, the World lead checks the licence (the page and the licence text, archived link in the ticket) and approves it in the ticket. Implementers never pick sources themselves |
| Review | the template's PR shows the preview PNGs and the credits diff; the human maintainer approves art like any other asset |

### Registries and data

| Item | Owner | Notes |
| --- | --- | --- |
| `STRUCTURE_PLACEMENTS` and `STRUCTURE_PROCESSORS` DeferredRegisters | core (ask, `pod:core`) | `ModRegistries` has structure types and pieces only |
| Structure types `lotrrevival:built`, `lotrrevival:template` | world | on `ModRegistries.STRUCTURE_TYPES` |
| Piece types `lotrrevival:built_piece`, `lotrrevival:template_piece` | world | `ModRegistries.STRUCTURE_PIECES` |
| Placements `lotrrevival:biome_chance`, `lotrrevival:village_grid`, `lotrrevival:fixed` | world | on the new register |
| Structures, structure sets | world datagen (`DatapackBuiltinEntriesProvider` via `addRegistries`) | fixed sets from the map build |
| Structure tags `#lotrrevival:event_venues/<kind>`, `#lotrrevival:holdable`, `#lotrrevival:landmark` | world datagen, one writer | tag keys as constants in core `ModTags` |
| Biome tags `#lotrrevival:has_structure/<id>`, `#lotrrevival:no_structures` | world datagen | |
| Loot tables `lotrrevival:chests/*` | world datagen | |
| Garrison rosters | npc data | `garrison_roster/<faction>.json` |
| `core.api.StructureVenues`, `core.api.FactionStandards` | core holders; world and progression backends | |
| Command table block, faction tables, forges, ovens | core | builders fall back to a vanilla stand-in until a block exists |

## 12. Performance

| Budget | Value |
| --- | --- |
| Start check | per chunk, every scattered structure listed for the chunk's biome rolls (a hash, no allocation); only winners run the spacing and ground checks (<= 25 height samples) |
| Village check | per chunk, one grid lookup per culture whose biomes are near; road distance from the road network's 64-block cells |
| Piece placement | a builder run per touched chunk; target <= 2 ms per piece per chunk on the CI machine, measured by the preview GameTest |
| Index | about 300 bytes per record; loaded per region on demand |
| Population service | scans index records in loaded chunks every 20 ticks, each slot acts once per `interval`, staggered by record id |
| Chunk size | piece data only (no block lists in chunk NBT) |

## Edge cases

- **Multiplayer:** garrisons never refill within 24 blocks of any player; two players opening a guardian chest at once wake one guardian.
- **Chunk borders:** a structure always places its whole build across all chunks it touches, because each piece re-runs deterministically; a piece never reaches more than 3 chunks from its start chunk.
- **Overlaps:** builds interleave block by block; later-placed pieces in the same chunk overwrite earlier ones (vanilla order). Venue, holding and garrison effects follow section 2e.
- **Partly generated structures:** chunks generated before the structure existed (an update adds a type) never get it. Existing worlds only get new structures in new chunks.
- **Player builds and griefing:** blocks broken or replaced stay that way; the index keeps the record; events skip venues with too little open floor (nemesis spec).
- **Ground check fails:** nothing generates; the density audit accounts for it with the 60% factor, and the sampler measures the real rate.
- **Structure in two provinces:** its province is the one holding its centre (territory).
- **Structure under a player banner:** placed normally; `protected` is reported to events; garrisons of enemy factions do not spawn inside.
- **Standard broken or replaced:** only the holder's enemies (alignment below 0) can break it; not restored; later swaps skip that spot. A standard on a shadowed record has no holder: it falls back to its generation faction for the break rule.
- **Data pack removes a structure:** existing starts in chunks fail to load their type and are dropped by vanilla with a log line; the index marks the record inactive.
- **Landing point and spawn:** no scattered structure within 96 blocks and no village within 512 blocks of the landing point; the ring portal's return portal is never built over.
- **Rivers and roads:** structures never place on water; roads skip structure boxes; village lanes bridge water like roads.
- **Death, logout, dimension change:** not relevant to structures; garrison NPCs follow NPC rules.
- **Peaceful difficulty:** garrisons still spawn (NPC base rule).
- **Operator placement in the overworld:** works; the index records it in that level; no `has_structure` biome check applies to `/lotr structure place`.
- **Upgrading from Legacy worlds:** not supported.

## Data and APIs

- Structure JSON fields as section 1a; codecs in `world.structure`.
- Map source: `landmarks.geojson` new properties (section 2c); generated fixed structure sets and bridge sites (buildSrc).
- SavedData `lotrrevival_structures`.
- Config `lotrrevival-world-server.toml` `[structures]`: `enabled` (true), `density_multiplier` (1.0, scales every 1/N), `garrisons` (true), `garrison_interval_ticks` (3600), `garrison_no_player_range` (24), `density_audit_min` (2.0, datagen side).
- Commands (under `/lotr structure`, op level 2): `place <id> [rotation] [seed]`, `info` (the record you stand in), `list [kind] [radius]`, `density [radius]` (samples the index around you and prints venues per kind), `export <id> [seed]` (dev).
- Core asks (`pod:core`): the two registers, `StructureVenues` and `FactionStandards` holders, tag keys, command table and culture tables, and `LandProtection.anyProtectedIn(level, box)`, safe to call from worldgen threads (progression answers it from an immutable banner snapshot published on the server thread).
- Progression ask: `FactionStandards` backend (placement, replacement, the standard flag and the enemies-only break rule) and the thread-safe `anyProtectedIn` answer.
- NPC asks: structure population service, garrison rosters, role ids, guardian slots.

## Out of scope

- Hand-built NBT art for capitals (a template slot exists; art comes later).
- Siege mechanics, players claiming structures, structures rebuilding after griefing.
- Utumno and its dimension.
- Footbridges away from roads, ships.
- Map icons for discovered structures (progression may add them later from the index).

## Acceptance tests

The GameTest server has no Middle-earth level. Tests use the overworld test level, fake biome and height functions, the in-memory sink and stub backends.

- [ ] GameTest: scattered roll is deterministic per (seed, chunk, id); two structures winning in the same chunk both generate.
- [ ] GameTest: overlap ranking is independent of load order (index the pair both ways); a shadowed record is not found as a venue, not holdable, and its garrison slot is disabled only when its faction is an enemy of the winner's; late discovery shadows a record and keeps existing NPCs.
- [ ] GameTest: banner keep-out drops a start whose box touches a stub protected cube.
- [ ] GameTest: ground check rejects water, leaves and spread above the limit; accepts a slope within it; foundations fill down to ground.
- [ ] GameTest: road keep-out (16 blocks), landmark keep-out (radius + 64), landing keep-outs (96 / 512), village claim keep-out, using a fake road network.
- [ ] GameTest: village grid picks one candidate per cell, rejects candidates near roads and outside culture biomes, and the access lane reaches the road.
- [ ] GameTest: `lotrrevival:fixed` places exactly one start at the listed position.
- [ ] GameTest (preview): every builder, 3 seeds x 4 rotations, stays in its footprint, has standards and slots inside bounds, chests with loot tables, a reachable door; previews written.
- [ ] GameTest: a small builder placed in the test level through the level sink matches the in-memory result block for block.
- [ ] GameTest: index fills on chunk load from a start and from a reference, never generates a chunk, ids are stable; `find` honours band, kind, limit and the record budget; cache invalidates on add.
- [ ] GameTest: venue owner reports the holder for holdable sites and none for ruins; `protected` follows a stub `LandProtection`.
- [ ] GameTest: `FactionStandards` stub records place and replace; a standard spot holding another block is reported as missing.
- [ ] GameTest (progression): a standard breaks for a mock player at -1 with the holder, not at 0 or above, not by explosion; creative always.
- [ ] GameTest (NPC pod): garrison slot refills to cap after 3,600 ticks, not while a mock player is within 24 blocks, `once` slots never refill, guardian slot fires once.
- [ ] Datagen check: density audit table passes for the painted pilot biomes.
- [ ] Map build: validator rejects a fixed structure outside a window, too near the canvas edge, overlapping another or near the landing point; bridge sites are computed for the East Road's Brandywine crossing.
- [ ] Playtest: walk the East Road from Hobbiton to Bree: hobbit holes and taverns but none on the road; a Shire hamlet and a bounder post off the road; the Brandywine Bridge landmark; Bree with guards; a ranger camp and a ruined tower in the Lone-lands; a barrow with a waking guardian; a Bree hamlet guard post's standard and guards.

## Tickets

Proposed; opened after this spec is approved. Days are agent days. Gate is the milestone. Order within a phase is the priority order.

### Phase S0: framework (G3)

| # | Pod | Ticket | Days | Agent | Blocked by |
| --- | --- | --- | --- | --- | --- |
| S0-1 | core | Registers `STRUCTURE_PLACEMENTS` and `STRUCTURE_PROCESSORS`; holders `core.api.StructureVenues` (venue record, queries) and `core.api.FactionStandards`; tag keys `event_venues/*`, `holdable`, `landmark`, `has_structure/*`, `no_structures`; command table block if not yet present | 1 | Opus 1 | - |
| S0-2 | world | Building kit: sinks (level, in-memory), rotation, palettes, shapes, foundations, decay, chests, standards, slots; preview GameTest writing PNGs; CI artifact upload of `build/structure-previews/` (touches the workflow: needs Opus 1) | 2 | sonnet-3 | - |
| S0-3 | world | `lotrrevival:built` structure type and piece, metadata codec, ground check, terrain adaptation, structure and `has_structure` datagen helpers, `/lotr structure place|info` | 2 | sonnet-3 | S0-1, S0-2 |
| S0-4 | world | Placements: `biome_chance` (independent rolls, overlaps allowed), `village_grid` (grid, claim, keep-outs, culture biomes), hard keep-outs (roads, landmarks, landing, banner cubes via `anyProtectedIn`), `density_multiplier` config | 2 | sonnet-4 | S0-1 |
| S0-5 | world | Structure index SavedData, fill on chunk load (starts and references, no generation), core boxes and overlap ranking (section 2e), `StructureVenues` backend, cache, `/lotr structure list|density` | 2 | sonnet-5 | S0-1, S0-3 |
| S0-6 | progression | `FactionStandards` backend over the faction banner block (standard flag, enemies-only break rule); thread-safe `LandProtection.anyProtectedIn` from a banner snapshot | 1 | Opus 4 | S0-1 |
| S0-7 | npc | Structure population service: garrison, once and guardian slots with the Legacy rules; garrison rosters data and datagen for pilot factions; enemy spawn block from `garrisonsNear` | 2 | sonnet-7 | S0-1, S0-5 |
| S0-8 | world | Map source: `landmarks.geojson` structure properties, validator rules, generated fixed structure sets and corridor entries; bridge sites from road and river crossings; `lotrrevival:fixed` placement | 2 | sonnet-4 | S0-1 |
| S0-9 | world | Loot provider and the pilot loot tables (larder, house, treasure, ruin, barrow, ranger, orc camp, Bree market, bandit stash) | 1 | sonnet-6 | - |
| S0-10 | world | Density audit at datagen (fails under the minimum for painted biomes) and the village lane builder | 1 | sonnet-5 | S0-4 |
| S0-11 | world | Vetted third-party template intake (section 11b): `ASSETS_CREDITS.md` with its format and a `NOTICE` pointer, a licence checklist in `docs/tooling.md`, an intake issue template (source URL, author, licence link, archived copy, lead approval box), the `template` piece type with data markers, and a converter dev command that maps a source `.nbt` to our palette and markers and rejects foreign blocks and entities | 1.5 | sonnet-6 | S0-3 |

### Phase S1: venue structures the nemesis and territory specs need, in the pilot window (G3)

| # | Ticket | Days | Agent | Blocked by |
| --- | --- | --- | --- | --- |
| S1-1 | Ruins: stone ruin (stone, Arnor styles), small stone ruin, ruined, burnt and rotten houses | 1.5 | sonnet-6 | S0-3, S0-9 |
| S1-2 | Ranger camp (camp) and ranger watchtower (watchtower), standards and slots | 1.5 | sonnet-3 | S0-3, S0-7 |
| S1-3 | Bree village culture: plots, streets, houses, burrows, inn, smithy, market, stables, guard house (guard post); hamlet and village layouts | 2 | sonnet-4 | S0-3, S0-4 |
| S1-4 | Bree fixed: Bree (walls, gatehouse, captain's office), Staddle, Combe, Archet at their landmarks | 2 | sonnet-4 | S1-3, S0-8 |
| S1-5 | Shire homes: hobbit hole, burrow, house, tavern, farm, windmill, picnic bench | 2 | sonnet-6 | S0-3, S0-9 |
| S1-6 | Shire hamlet (village) and bounder post (watchtower) [Ours]; Hobbiton fixed | 1.5 | sonnet-6 | S1-5, S0-8 |
| S1-7 | Ruined Dúnedain tower and Rhudaur castle (ruin) | 1.5 | sonnet-3 | S1-1 |
| S1-8 | Barrows with guardian chests and cairns (ruin) | 1.5 | sonnet-5 | S0-7, S0-9 |
| S1-9 | Gundabad camp (camp) and hill-men village and house (village) | 2 | sonnet-7 | S0-3, S0-7 |
| S1-10 | Bandit hideout (hideout, `ruffians`) [Ours] in Bree-land, the Lone-lands and the Shire edges | 1 | sonnet-5 | S0-3, S0-7 |
| S1-11 | Tower Hills tower (tower); Blue Mountains stronghold (outpost), dwarf house, smithy | 2 | sonnet-3 | S0-3 |
| S1-12 | Dwarven mine with garrisoned, standard-flying entrance (outpost) | 2 | sonnet-7 | S1-11 |
| S1-13 | Landmarks: Brandywine Bridge (bridge), Bucklebury Ferry, Old Forest heart glade | 1.5 | sonnet-4 | S0-8 |
| S1-14 | Playtest checklist `docs/playtest/structures-pilot.md` and screenshots (human) | 0.5 | Seth | S1-1..13 |

### Phase S2: the rest of Legacy's catalogue (G3 to G4, as regions are painted)

One group per region, each 3-6 tickets of 1-2 days, opened when the region's biomes land: Lindon, Eregion and Rivendell; Misty Mountains, Angmar and Ettenmoors; Rhovanion (Mirkwood, Dale, Erebor, Iron Hills); Lothlórien; Dunland and Isengard (incl. the ring wall); Rohan; Gondor and its fiefs (incl. Rammas Echor and the beacon chain); Mordor and Nurn; Rhûn and Dorwinion; Harad, Umbar and Far Harad (incl. the jungle pyramid's protecting banner); faction capitals (procedural keeps first); easter eggs; NBT promotion of landmarks when art exists.

### Dependency summary for the other specs

- Nemesis (PR #182) ticket 27 becomes S0-1, tickets 29-31 become S0-5, the venue tags in S0-3 and S1-1..13; its density target is section 6.
- Territory (PR #183) ticket 9 is this spec; ticket 13 (held structures and swaps) needs S0-5 and S0-6; ticket 12c (calming) needs S1-8 and S1-13.
- **Required change to PR #183** (to be made on that branch by its author, not here): held-structure data in `lotrrevival_territory_holds` becomes structure id + holder + epoch only; everything else comes from the structure index; territory ignores shadowed records (section 2e).

## Maintainer decisions (PR #184)

1. Procedural builders first, NBT for landmarks and capitals; vetted third-party templates allowed under section 11b.
2. Overlaps allowed as in Legacy; section 2e resolves venues, holding and garrisons; hard exclusions kept (landmarks, roads within 16 blocks, banner-protected land, landing).
3. Shire hamlets and bounder posts: yes.
4. Hobbiton centred 160-200 blocks east of the landing point.
5. Bandit hideouts held by `ruffians`, in Bree-land, the Lone-lands and the Shire edges.
6. Village lanes to the nearest road within 256 blocks: yes.
7. Standards breakable only by players with alignment below 0 with the holder.
8. Small dwarf guard at mine entrances, so they are outposts.

## Open questions for the maintainer

None at the moment.
