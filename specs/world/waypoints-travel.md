# Waypoints, fast travel, the ring portal and roads

- **Pod:** world
- **Lead:** Opus 2
- **Status:** approved (the maintainer pre-approved Legacy behavior: "match Legacy")
- **Depends on:** `specs/world/dimension-and-mapgen.md` (canvas, projection, map source, biome ids, road format sketch), `specs/core/core-apis.md` (alignment API, datapack registry convention, attachments, payload rules, `/lotr` root), `specs/progression/data-model.md` (`achievementCount`, Middle-earth menu and its Map page), core API `core.api.AchievementCounters`, core requests listed under "Requested from other pods"
- **Sources:** behavior notes `docs/reference/legacy/world-map-travel.md` sections 2-5 (our own words about Legacy 36.15). Numbers come from there. Waypoint names are Tolkien place names; every description, id and line of text here is our own.
- **Supersedes:** the planned `portal.md`, `roads.md` and `waypoints-and-travel.md` named in the mapgen spec, and that spec's proposed tickets 6 (roads) and the road half of 5 (`map_corridors`).

## Summary

At Legacy scale (Hobbiton to Bree is about 12,800 blocks), walking is for the road you are on and fast travel is how you cross regions. This spec covers the travel loop:

- **Waypoints.** Named places (towns, bridges, fords, ruins) are fixed on the map. Entering any biome of a region reveals all of that region's waypoints. Town waypoints belong to a faction and need alignment of 0 or more with it. Players also mark their own custom waypoints; how many depends on their achievement count.
- **Fast travel.** Pick a known waypoint, stand still for 10 seconds, and you, your mount, your following units, your pets and your leashed animals arrive there. The cost is time: one clock per player that must have run longer than the trip's required time, which falls the more often you visit that waypoint.
- **The ring portal.** The way in: throw a gold ring into fire in the overworld. Once per world, a ring portal rises from the flames; its twin stands at the Middle-earth landing point and leads back.
- **Middle-earth respawning.** Dying far from your bed puts you back down within a day's walk of where you fell, not at a spawn 50,000 blocks away.
- **Roads.** About 8-9 blocks wide, smooth curves through flattened corridors, built from the local style of stone or earth, bridging rivers.

Waypoints and roads are authored in the map source (`src/main/map/`) next to the coasts and biomes they sit on, checked by the map validator, and shipped as datapack registries.

## Player-facing behavior

### Waypoints

1. Waypoints are shown on the Map page of the Middle-earth menu (Progression owns the screen; this spec owns the data and rules). Each has a name, a short description, a position, a region and either an owning faction or none.
2. A waypoint is **locked** until its region is unlocked. Regions unlock when the player stands in any biome tied to the region: every waypoint of the region becomes known at once, with a chat line "New lands discovered: <Region>. Its waypoints are now on your map." A region unlocks once per player, forever.
3. An unlocked waypoint with no faction can be used by anyone. A **faction waypoint** (a town or stronghold) can only be used while the player's alignment with that faction is 0 or more. Below 0 it shows as known but barred, with the reason ("<Faction> will not receive you").
4. **Hidden** waypoints are not listed at all until their region is unlocked (no pilot waypoint is hidden; the flag exists for secret places later).
5. **Custom waypoints.** A player marks their current position with a name of their choice. They may hold **5 + 1 per 5 counted achievements** (5 + floor(n / 5)). Custom waypoints work like fixed ones for fast travel, with their own use counters. They can be renamed and deleted. They exist only in Middle-earth.

### Fast travel

1. The player selects a usable waypoint and asks to travel. The request is refused, with a chat reason, when:
   - fast travel is disabled by config;
   - the player is not in Middle-earth;
   - the waypoint is locked, barred by alignment, or no longer exists;
   - the travel clock is below the trip's required time ("You must wait mm:ss more before travelling to <Waypoint>");
   - any mob within 16 blocks is targeting the player (creative and spectator players are exempt);
   - the player is sleeping.
2. Otherwise a **10-second countdown** starts: "Travelling to <Waypoint> in 10 seconds. Stand still." The last 5 seconds count down in chat (5, 4, 3, 2, 1).
3. The countdown is **cancelled** by any change of position, by jumping, by death, logout, dimension change, getting into a bed, mounting or dismounting, or by a new travel request (which starts its own countdown). Cancelling prints "Fast travel cancelled." No time is spent.
4. When it completes, every refusal check runs again. If one fails, the trip is cancelled with that reason. Otherwise the player arrives at the waypoint with their companions (below), the travel clock resets to 0, the use counter for that waypoint goes up by 1, and the waypoint becomes the player's "last waypoint". Chat: "You arrive at <Waypoint>."
5. **Companions.** Everything within 256 blocks that is:
   - a hired unit that follows this player (set to follow, owned by them);
   - a tamed pet owned by this player that is not ordered to sit;
   - a mob on a lead held by this player;
   - the player's mount: the player arrives in the saddle.

   Companions arrive around the player. Anything riding a companion stays on it.
6. **Required time.** The clock must reach `(min + (max - min) x 0.9^uses) x max(1, distance x 0.000012)` seconds, where `uses` is how often this player has arrived at this waypoint and `distance` is the horizontal distance from the player to the waypoint. With defaults min 3 min, max 30 min: a first trip needs 30:00; after 10 visits to the same waypoint about 12:25, after 30 about 4:09. Distance only adds time beyond about 83,333 blocks (Hobbiton to Minas Tirith: x 1.22).
7. **The clock** counts up every tick the player is online, in any dimension. On a multiplayer server (not single-player), logging back in adds 10% of the real time spent offline.

### The ring portal

1. In the overworld, a player throws a **gold ring** (Core item) into fire, soul fire or lava. When a gold ring item touches one of these within 16 blocks of any player, and no ring portal has ever been made in this world, the ring is consumed, a blast goes off (power 3) and a **ring portal** begins to form 3 blocks above the fire.
2. The portal grows to full size over **6 seconds** (120 ticks) and cannot be used while growing.
3. A player who stands inside the full-size portal (walking under it is enough) for **4 seconds** (80 ticks; 1 tick in creative), not riding and not ridden, is sent to Middle-earth. Mobs and items that touch it pass through at once.
4. Arrivals in Middle-earth land beside the landing point (`middle_earth.json`, next to Hobbiton). The first arrival ever raises the **twin portal** above the landing point. Middle-earth's spawn point is there.
5. The twin portal leads back to the overworld portal.
6. Only one ring portal pair can ever be made per world. Later rings thrown into fire do nothing (the ring is fire-proof and stays there). Ops can reset this with `/lotr portal reset`.

### Middle-earth respawning

1. Dying in Middle-earth respawns the player in Middle-earth: at their Middle-earth bed if it is valid, otherwise at Middle-earth's spawn (the twin portal, or the landing point before any portal exists). An overworld bed is ignored for deaths in Middle-earth.
2. If the death point is more than **5,000 blocks** from that bed, or more than **2,000 blocks** from Middle-earth's spawn when there is no bed, the player instead respawns **500-1,500 blocks** from the death point in a random direction, on dry ground. The bed is not cleared.

### Roads

1. Roads are about 8-9 blocks wide and follow smooth curves between the places they join. The ground within 32 blocks of a road has its small hills smoothed away, so roads run through gentle corridors.
2. Road surfaces use the style of the biome they pass through: earth and gravel paths in most of the pilot, broken old stone in the Lone-lands with gaps where grass has taken the road back.
3. Where a road crosses water it becomes a wooden bridge on stone piers, rising higher over deeper water, with rails on both sides.
4. Roads never cut through buildings: landmarks and towns lay their own streets and bridges.

## Rules and numbers

### Waypoints

| Thing | Value | Notes |
| --- | --- | --- |
| Faction waypoint use | alignment(faction) >= 0 | `Alignment.get` from core. Checked at request and at arrival |
| Conquest exception | none for now | Legacy lets a pledged player use an enemy faction's waypoint where their faction's conquest is strong. Added with the conquest spec |
| Region unlock check | every 20 ticks per player in Middle-earth | Biome at the player's block position; jittered biome borders apply. River, lake, ocean and shore biomes belong to no region |
| Region unlock | all regions whose biome list contains that biome | Normally exactly one (validated). Fires `WaypointRegionUnlockedEvent` (Core) |
| Custom waypoint limit | `custom_base` + floor(n / `custom_per_achievements`) = 5 + floor(n / 5) | n = `AchievementCounters.count(player)`. Checked only when creating |
| Over the limit (count dropped) | Existing ones stay and stay usable; creating is refused until under the limit | |
| Custom name | 1-32 characters after trimming; any text; duplicates allowed | Stored as plain text, shown literally (never a translation key) |
| Custom position | the player's block position, Middle-earth only | Refused below `custom_min_y` (default: no limit). Protected-land check comes with the banners spec |
| Arrival, fixed waypoint | the highest solid non-leaf block at the waypoint column (`MOTION_BLOCKING_NO_LEAVES`); chunk generated first | If the 2 blocks above are not free, the nearest free spot within a 5 x 5 column area |
| Arrival, custom waypoint | the stored block position, if the feet and head blocks are free and the block below is solid; otherwise as for a fixed waypoint | |

### Fast travel

| Thing | Default | Config key (`lotrrevival-world-server.toml`) |
| --- | --- | --- |
| Enabled | true | `travel.enabled` |
| Min time | 180 s | `travel.min_seconds` |
| Max time | 1,800 s | `travel.max_seconds` |
| Use decay | 0.9 per use | `travel.use_decay` |
| Distance factor | 0.000012 per block | `travel.distance_factor` |
| Countdown | 200 ticks | `travel.countdown_ticks` |
| Hostile check range | 16 blocks | `travel.hostile_range` |
| Companion range | 256 blocks (3D distance) | `travel.companion_range` |
| Offline credit | 0.1 of offline time, multiplayer only | `travel.offline_fraction` |

- **Required ticks** = `ceil(20 x (min + (max - min) x decay^uses) x max(1, d x factor))`, with `d` the horizontal distance in blocks (x and z only) and `uses` capped at 1,000. Worked values (defaults, short distance): uses 0: 36,000 ticks (30:00); 1: 32,760 (27:18); 5: 22,732 (18:57); 10: 14,898 (12:25); 30: 4,974 (4:09). Distance 102,000 blocks with uses 0: 36,000 x 1.224 = 44,064 ticks.
- **Allowed** when `clock >= required`.
- **Clock** starts at 0 for a new player, +1 every server tick while online, saturates at 1,000,000. It resets to 0 only on a completed trip. `/lotr waypoint clock` can set it.
- **Offline credit**: on login, when the server is not single-player (a dedicated server; a LAN-opened single-player world counts as single-player), add `floor(offline_fraction x offline_seconds x 20)` ticks, where offline time is wall-clock time since the last logout. A missing or future logout time adds nothing.
- **Movement cancel**: the countdown records the player's position at start. It cancels when the player is more than 0.05 blocks from it on any axis (this also catches knockback, pushing and falling), when the player jumps, or on any cancel event listed under Player-facing behavior.
- **Targeted**: any `Mob` within `hostile_range` (3D) whose current attack target is the player.
- **Companions** (3D distance <= `companion_range`, same dimension, alive, loaded):
  - entities implementing Core's `TravelCompanion` that answer true for this player (the NPC pod implements it on hired units set to follow);
  - tamed animals owned by the player and not ordered to sit;
  - mobs whose lead is held by the player;
  - the player's vehicle.

  A companion that is itself a passenger is moved with its vehicle. Leads stay attached.
- **Placement**: the player at the arrival spot; the mount carries the player; each other companion at a random free spot within 3 blocks horizontally (up to 8 tries, then the player's spot). Fall distance is reset for everyone moved.
- After arrival: clock 0, `uses + 1` for that waypoint, `last_waypoint` set, `FastTravelEvent` posted (Core), full travel sync sent.

### Ring portal

| Thing | Value | Notes |
| --- | --- | --- |
| Catalyst | `lotrrevival:gold_ring` item entity touching fire, soul fire or lava (block tags `#minecraft:fire` or fluid lava) | The ring is fire-resistant, so it does not burn away first. Overworld only |
| Player in range | any player within 16 blocks of the ring | |
| Once per world | a flag on the overworld's level attachment | `/lotr portal reset` (permission 2) clears it |
| Blast | power 3 at the ring; damages entities; breaks no blocks by default | `portal.blast_breaks_blocks` (default false). Legacy's block damage is unverified, so we keep fire pits intact |
| Portal position | centre 3 blocks above the fire block | |
| Portal size | hit box 3 wide x 3 tall; the bottom sits 1.5 blocks above the fire block, so a player standing next to the fire touches it | Model and texture are our own (art ticket) |
| Growth | 120 ticks, scale 0 to 1, not usable while growing | Synced to clients for rendering |
| Player use | stands touching it for 80 ticks (survival/adventure) or 1 tick (creative/spectator); not a passenger and carrying no passengers | Leaving it resets the count |
| Other entities | non-player entities that touch it, are not passengers and carry no passengers are sent at once | |
| Re-entry guard | an entity that just arrived through a ring portal is ignored by every ring portal until it has stopped touching one for 60 ticks | Stops ping-pong |
| Middle-earth arrival | 4 blocks south (+z) of the landing point, top solid block | The twin portal floats 3 blocks above the landing point's top solid block |
| First arrival | builds the twin portal (full size at once) and stores its position | If the twin is missing on a later arrival, it is rebuilt at the stored position |
| Return arrival | 4 blocks south of the overworld portal, top solid block | If no overworld portal is stored (Middle-earth reached by command), the overworld spawn |
| Wrong dimension | a ring portal in any dimension other than the overworld or Middle-earth removes itself | |
| Durability | invulnerable to everything but `/kill`; never despawns; not pushable | |
| Middle-earth spawn | the twin portal's arrival point; before it exists, the landing point | One function so respawn and portal agree |

### Middle-earth respawning

| Thing | Default | Config key |
| --- | --- | --- |
| Enabled | true | `respawn.enabled` |
| Far from bed | 5,000 blocks | `respawn.bed_distance` |
| Far from spawn (no bed) | 2,000 blocks | `respawn.spawn_distance` |
| Offset from death point | 500-1,500 blocks, uniform, random direction | `respawn.min_offset`, `respawn.max_offset` |

- Distances are horizontal. "Bed" means the player's respawn point when it is in Middle-earth and still valid (vanilla bed rules); respawn anchors do not work there (dimension type).
- The random spot must be land on the painted map (not ocean, lake or river biome, top block not a fluid). Up to 16 tries; if all fail, the normal spot (bed or spawn).
- With `respawn.enabled` false, deaths in Middle-earth still respawn in Middle-earth (bed or spawn); only the far-death rule is off.

### Roads

| Thing | Value | Notes |
| --- | --- | --- |
| Curve | centripetal Catmull-Rom through the control points, sampled every block of arc length | End points are used as their own outer neighbours |
| Jitter | +-1 block sideways, smooth along the road | Value noise over arc length with a 24-block wavelength, seeded by the road id (never the world seed), so every world has the same roads |
| Width | every column within `width` (default 4) blocks of the centre line is road: about 8-9 blocks | `width` is the half-width, per road |
| Corridor | procedural detail x `mask`; `mask` = 0 within `width`, smoothstep up to 1 at `width + 32` | Density function `lotrrevival:map_corridors` around the detail term. The painted base is untouched |
| Depth | top 4 blocks of each road column | Top layer from the style's surface mix, the 3 below from its base block |
| Levelling | grass, flowers, ferns and snow layers above a road block are removed | Trees come later in decoration and skip road blocks |
| Half steps | if the mean surface height of the up to 8 neighbouring road columns is at least 0.5 above this column, a slab of the style goes on top | Gives half-step edges on slopes |
| Repair | each road column is placed with probability `repair` (per biome), otherwise left as it is | Deterministic hash of x and z, the same in every world |
| Structures | columns inside any structure piece's bounding box are skipped | Landmark bridges and town streets win |
| Order | after surface structures, before vegetation | Placed feature in every Middle-earth biome; trees and plants get a filter that rejects road surfaces |
| Look-up | road distance queries for any column cost O(segments in a 64-block grid cell) | Built once when the server starts, from the registry |

**Road styles (pilot).** Blocks are vanilla except the Arnor set, which Core adds; until it lands, vanilla stone bricks, mossy and cracked stone bricks stand in.

| Style | Biomes (repair) | Surface mix (top layer) | Base (3 below) | Slab |
| --- | --- | --- | --- | --- |
| `path` | every pilot biome not listed below (1.0) | dirt path 70%, coarse dirt 20%, gravel 10% | dirt | none: a half step stays a full block of the column's surface pick |
| `arnor` | `lone_lands` (0.4) | Arnor brick 50%, mossy 25%, cracked 25% | stone | the matching Arnor slab |

Later regions add styles from the Legacy table in the reference notes (Arnor in Eriador 0.92, high-elven, dwarven, Rohan, Gondor and so on). Styles and their biome mapping are a Java table in the `world` package.

**Bridges.** A road column whose surface is water becomes bridge:

| Thing | Value |
| --- | --- |
| Depth `d` | water surface y minus the top solid block under the water |
| Deck height | `min(d, 10) / 2` blocks above the water surface: whole blocks as full deck blocks, a remaining half as a bottom slab |
| Deck | one layer of oak planks (oak slab for the half step) |
| Rails | the outermost road columns (distance from centre > `width - 1`) get an oak fence on the deck |
| Piers | at edge columns where floor(arc length) is a multiple of 8, a column of stone bricks from the deck down to the bed |
| Water | left in place under the deck |
| Style | `stone_and_wood` (above) for every pilot biome. Later styles (dark wood, charred) are a column in the style table |

### Pilot data

**Regions.** Every land biome in the colour table belongs to exactly one region. Display names are ours.

| Region id | Name | Biomes |
| --- | --- | --- |
| `shire` | The Shire | `shire`, `shire_woodlands`, `shire_marshes`, `shire_moors`, `white_downs` |
| `old_forest` | The Old Forest | `old_forest` |
| `barrow_downs` | The Barrow-downs | `barrow_downs` |
| `bree_land` | Bree-land | `bree_land`, `chetwood`, `midgewater_marshes` |
| `lone_lands` | The Lone-lands | `lone_lands` |
| `tower_hills` | The Tower Hills | `tower_hills` |
| `ered_luin` | Ered Luin | `ered_luin_foothills` |

**Waypoints.** Coordinates are heightmap pixels `[u, v]` (64 blocks per px, Hobbiton = `[1620, 1460]`). Positions marked ~ are first guesses: the implementer moves each to the nearest sensible spot inside its region on the pilot map, and the validator is the judge.

| Id | Name | Region | Faction | Position | Description (our own) |
| --- | --- | --- | --- | --- | --- |
| `hobbiton` | Hobbiton | shire | `hobbit` | `[1620, 1460]` | Holes and gardens on the slopes of the Hill, beside the Water. |
| `bywater` | Bywater | shire | `hobbit` | ~`[1634, 1468]` | A busy village where the Water meets the East Road; known for its inn. |
| `overhill` | Overhill | shire | `hobbit` | ~`[1622, 1445]` | A quiet hamlet on the far side of the Hill. |
| `waymeet` | Waymeet | shire | `hobbit` | ~`[1588, 1470]` | A crossroads village in the Westfarthing where several lanes join the road. |
| `michel_delving` | Michel Delving | shire | `hobbit` | ~`[1500, 1470]` | The chief town of the Shire, dug into the White Downs. |
| `little_delving` | Little Delving | shire | `hobbit` | ~`[1505, 1440]` | A smaller cousin of Michel Delving, further along the Downs. |
| `needlehole` | Needlehole | shire | `hobbit` | ~`[1585, 1405]` | A village tucked among the woods of the north-west. |
| `tuckborough` | Tuckborough | shire | `hobbit` | ~`[1600, 1500]` | Old home of a great family, with many-tunnelled smials in the green hills. |
| `longbottom` | Longbottom | shire | `hobbit` | ~`[1590, 1585]` | Warm southern fields where the best pipe-weed grows. |
| `frogmorton` | Frogmorton | shire | `hobbit` | ~`[1662, 1462]` | A road village of the Eastfarthing with a well-kept inn. |
| `whitfurrows` | Whitfurrows | shire | `hobbit` | ~`[1680, 1462]` | Farmland and white furrows near the eastern end of the road. |
| `scary` | Scary | shire | `hobbit` | ~`[1655, 1415]` | A village by old quarries in the Eastfarthing hills. |
| `brockenborings` | Brockenborings | shire | `hobbit` | ~`[1672, 1400]` | Hobbit-holes among rough northern hills. |
| `stock` | Stock | shire | `hobbit` | ~`[1690, 1488]` | A marsh village at the edge of the Marish, close to the river. |
| `woodhall` | Woodhall | shire | `hobbit` | ~`[1660, 1525]` | A village beneath the eaves of the Woody End. |
| `bucklebury` | Bucklebury | shire | `hobbit` | ~`[1710, 1500]` | The chief village of Buckland, across the river from the rest of the Shire. |
| `brandywine_bridge` | Brandywine Bridge | shire | none | `[1696.6, 1459.5]` | The great stone bridge that carries the East Road over the river. |
| `bucklebury_ferry` | Bucklebury Ferry | shire | none | ~`[1700, 1495]` | A flat ferry that crosses the Brandywine below Stock. |
| `withywindle` | Withywindle | old_forest | none | ~`[1752, 1540]` | A dark, slow river winding through the heart of the Old Forest. |
| `barrow_downs` | Barrow-downs | barrow_downs | none | `[1795, 1550]` | Bare green hills crowned with ancient barrows and standing stones. |
| `bree` | Bree | bree_land | `bree` | `[1819.5, 1466]` | The chief village of Bree-land, where the East Road meets the Greenway. |
| `archet` | Archet | bree_land | `bree` | `[1826, 1442]` | The village on the edge of the Chetwood. |
| `staddle` | Staddle | bree_land | `bree` | `[1832, 1478]` | A village on the far slopes of Bree-hill, home to many hobbits. |
| `combe` | Combe | bree_land | `bree` | `[1842, 1490]` | A village in a deep valley east of the hill. |
| `chetwood` | Chetwood | bree_land | none | `[1868, 1416]` | A wide wood north-east of Bree, rarely free of outlaws. |
| `midgewater` | Midgewater Marshes | bree_land | none | `[1905, 1482]` | Endless bog and biting insects east of Bree. |
| `forsaken_inn` | Forsaken Inn | lone_lands | none | ~`[1958, 1462]` | The last inn on the East Road, a day's ride beyond Bree. |
| `tower_hills` | Tower Hills | tower_hills | none | `[1400, 1480]` | Green hills crowned by old white towers that look towards the sea. |

28 waypoints: 20 faction (16 `hobbit`, 4 `bree`), 8 unaligned. `ered_luin` has none in the pilot.

**Roads.** Control points are first guesses for #79 to refine on the pilot map; `through` lists waypoints the curve must pass within its width + 16 blocks of.

| Id | Name | Width | Control points (heightmap px) | Through |
| --- | --- | --- | --- | --- |
| `east_road` | The East Road | 4 | `[1360,1478] [1400,1478] [1450,1472] [1500,1470] [1588,1470] [1634,1468] [1662,1462] [1680,1462] [1696.6,1459.5] [1740,1462] [1790,1466] [1819.5,1466] [1860,1468] [1905,1470] [1958,1462] [1978,1460]` | `tower_hills`, `michel_delving`, `waymeet`, `bywater`, `frogmorton`, `whitfurrows`, `brandywine_bridge`, `bree`, `forsaken_inn` |
| `greenway` | The Greenway | 4 | `[1815,1300] [1818,1400] [1819.5,1466] [1826,1530] [1830,1620]` | `bree` |
| `hobbiton_lane` | Hobbiton Lane | 3 | `[1620,1460] [1627,1466] [1634,1468]` | `hobbiton`, `bywater` |
| `buckland_road` | Buckland Road | 3 | `[1700,1462] [1706,1480] [1710,1500]` | `bucklebury` |
| `marish_lane` | Marish Lane | 3 | `[1660,1525] [1676,1505] [1690,1488] [1700,1495]` | `woodhall`, `stock`, `bucklebury_ferry` |
| `southfarthing_road` | Southfarthing Road | 3 | `[1500,1470] [1560,1530] [1590,1585]` | `michel_delving`, `longbottom` |

## Edge cases

- **Multiplayer:** every player has their own regions, uses, clock and custom waypoints. Several players may count down at once. A pet or leashed mob belongs to one player only; a hired unit answers `TravelCompanion` for its owner only. Two players riding the same vehicle: only the driver's trip moves it; the passenger's countdown is cancelled by the movement.
- **Death, logout, dimension change:** the travel data is copied on death and saved with the player. Any of the three cancels a running countdown. The clock keeps counting in every dimension. Custom waypoints keep their Middle-earth positions while the player is in the overworld.
- **Datapack changes:** a waypoint removed from the registry disappears from the map; its use counter is kept (harmless) and a "last waypoint" pointing to it is ignored. A region removed from the registry stays in the player's unlocked set (ids only) and is ignored. A request for an unknown waypoint is refused.
- **Achievement count falls:** custom waypoints above the limit are kept and usable; creating new ones is refused.
- **Travel into ungenerated land:** the arrival chunk is generated synchronously (Legacy did the same); the waypoint column may take a moment.
- **Arrival blocked:** trees, structures or water at the waypoint column: the arrival search (5 x 5 columns) picks the nearest spot with solid ground and two free blocks; if none, the top block of the waypoint column anyway.
- **Unloaded companions:** only loaded entities come along. Chunks are not loaded to look for them.
- **Riding into the countdown:** a player who is riding is not moving if the mount stands still; the mount comes along and the player stays seated on arrival.
- **Ring portal and the placeholder command:** `/lotrrevival middleearth` keeps working for ops. Reaching Middle-earth by command does not build the twin portal; the first portal arrival does.
- **Portal flag and lost portals:** if the overworld portal entity is killed, the flag stays set (no second portal) until an op runs `/lotr portal reset`. The twin portal is rebuilt on the next arrival through the overworld portal.
- **Gold ring in the Nether or Middle-earth:** nothing happens.
- **Respawn search hits the sea:** the far-death offset can point into the placeholder ocean around the pilot island; those tries are skipped (land only), with the fallback above.
- **Roads and rivers:** where a road crosses a river polyline, the river cut runs first (it is part of the terrain), then the road feature bridges it.
- **Roads near the window edge:** road points must be on land inside a painted window (validated), so roads end before the sea rim.
- **Third-party datapacks** can add waypoints, regions and roads with the JSON formats below. Their positions are not validated by our build.

## Data and APIs

### Map source (authored, `src/main/map/`)

Two new files, read by the buildSrc map pipeline. Coordinates are heightmap pixels like every other map source file.

**`waypoints.geojson`**

```json
{
  "type": "FeatureCollection",
  "regions": {
    "shire": { "name": "The Shire", "biomes": ["shire", "shire_woodlands", "shire_marshes", "shire_moors", "white_downs"] }
  },
  "features": [
    { "type": "Feature",
      "properties": { "id": "bree", "name": "Bree", "region": "bree_land", "faction": "bree",
                      "lore": "The chief village of Bree-land, where the East Road meets the Greenway.", "hidden": false },
      "geometry": { "type": "Point", "coordinates": [1819.5, 1466] } }
  ]
}
```

`faction` and `hidden` are optional. Ids are `snake_case` and unique.

**`roads.geojson`**: `LineString` features with `properties` `id`, `name`, `width` (half-width, default 4), `show_on_map` (default true), `through` (waypoint ids, optional).

### Generated datapack registries

The rasterize task already writes into `build/generated/map/resources` (a resources source dir, not committed). It also writes one JSON per region, waypoint and road there, so the map source stays the single source of truth and the files ship in the jar. Datapacks can add or override entries.

| Registry (key reserved by Core) | Path | Content |
| --- | --- | --- |
| `lotrrevival:waypoint_region` | `data/lotrrevival/lotrrevival/waypoint_region/<id>.json` | `{ "name": <component>, "biomes": [<biome id>, ...] }` (a biome list or a `#tag`) |
| `lotrrevival:waypoint` | `data/lotrrevival/lotrrevival/waypoint/<id>.json` | `{ "name": <component>, "lore": <component>, "pos": [u, v], "region": <region id>, "faction": <faction id>?, "hidden": false }` |
| `lotrrevival:road` | `data/lotrrevival/lotrrevival/road/<id>.json` | `{ "name": <component>, "width": 4, "show_on_map": true, "points": [[u, v], ...] }` |

- Names and lore are written as translatable components with an English fallback, for example `{"translate": "waypoint.lotrrevival.bree", "fallback": "Bree"}`, so English needs no lang file and translators can add keys later.
- All three are synced to clients (`ModDataRegistries.registerSynced`), so the map page can draw them.
- Road styles are not in the registry: they come from the biome a road block is in (Java table).

### Map validator additions (buildSrc, runs in `./gradlew build`)

Fails when:
- a region lists a biome not in the colour table, or a land biome of the colour table is in no region or in two; water biomes (`ocean`, `lake`, `river`, `shore`) are in a region;
- a waypoint's id is duplicated, its region is unknown, its point is outside a painted window or within 1,024 blocks of the canvas edge, or the biome map pixel under it is not one of its region's biomes;
- a road has fewer than 2 points, a point lies on a sea or lake pixel or outside a window, or a `through` waypoint is further than `width + 16` blocks from the road's control polyline;
- a faction id is not one of Core's 25 faction ids (the validator keeps a copy of the list and a unit test compares it with Core's datagen table).

### World pod code (packages)

| Package | Content |
| --- | --- |
| `world.WorldConfig` | Server config `lotrrevival-world-server.toml` with every key in this spec |
| `world.waypoint` | Region and waypoint records and codecs, region index (biome to regions), travel data attachment, unlock tracker, required-time and availability rules (pure functions), custom waypoint service, `/lotr waypoint` commands |
| `world.travel` | Fast travel requests, countdowns, companions, arrival search, teleport |
| `world.portal` | Ring portal entity and its type, catalyst detection, portal level attachment, Middle-earth spawn, respawning |
| `world.road` | Road record and codec, spline, road network index, `lotrrevival:map_corridors`, road feature, styles, bridges |
| `world.network` | `world/*` payloads (one registrar class per subsystem) |
| `client/world` | Portal renderer, client travel mirror, `WaypointClient` implementation |

### Attachments

**`lotrrevival:travel`** (player, serialized by codec, `copyOnDeath`):

| Field | Type | Notes |
| --- | --- | --- |
| `regions` | set of region ids | Unlocked regions |
| `uses` | map waypoint id -> int | Arrivals per fixed waypoint, capped at 1,000 |
| `custom` | list of `{ id: int, name: string, pos: block pos, uses: int }` | Custom waypoints, at most 1,024 |
| `next_custom_id` | int | Never reused |
| `clock` | int ticks | Travel clock, saturating at 1,000,000 |
| `last` | optional waypoint ref | Last arrival |
| `last_logout` | long epoch ms | 0 when unknown |

A **waypoint ref** is `fixed:<namespace:path>` or `custom:<id>` on the wire and in commands.

**`lotrrevival:ring_portal`** (level attachment on the overworld): `made` (bool), `overworld_portal` (optional pos), `middle_earth_portal` (optional pos), `overworld_entity` and `middle_earth_entity` (optional UUIDs).

### Entities and features

- Entity type `lotrrevival:ring_portal` (world holder class on `ModRegistries.ENTITY_TYPES`), not summonable by players in survival, synced `growth` data.
- Feature `lotrrevival:road` (on `ModRegistries.FEATURES`), placed feature `lotrrevival:road` added to every Middle-earth biome in the step after surface structures.
- Density function type `lotrrevival:map_corridors` (needs Core's new `DENSITY_FUNCTION_TYPES` register): `{ "type": "lotrrevival:map_corridors", "input": <density function>, "falloff": 32 }` returns `input x mask(x, z)`. The noise settings wrap the detail term with it.
- Tree and plant placed features in Middle-earth biomes gain a vanilla `block_predicate_filter` that rejects a block below in `#lotrrevival:road_surfaces` (Core's block tag; World datagen adds the road blocks).

### Payloads (`world` pod, registered through `CoreNetwork.registrar`)

| Id | Direction | Content | Sent when |
| --- | --- | --- | --- |
| `world/travel_sync` | to client | regions, uses, custom list, clock, last, limit | login, respawn, dimension change, any change to the data; the client advances the clock locally between syncs |
| `world/travel_status` | to client | state (`countdown`, `cancelled`, `arrived`), target ref, ticks left, optional reason | countdown start, each second, end |
| `world/fast_travel_request` | to server | waypoint ref | player asks to travel (map page) |
| `world/custom_waypoint_edit` | to server | op `create` (name) / `rename` (id, name) / `delete` (id) | map page |

Client-to-server handlers follow Core's rules: sender alive, rate limit 20 per second, strings capped at 32 characters for names.

### Commands (under Core's `/lotr` root)

| Command | Permission | Effect |
| --- | --- | --- |
| `/lotr waypoint list [player]` | 0 self, 2 others | Unlocked regions; usable, barred and locked waypoints; custom waypoints; clock and the required time for each |
| `/lotr waypoint unlock <players> <region or all>` / `lock ...` | 2 | Changes unlocked regions |
| `/lotr waypoint clock <players> <ticks>` | 2 | Sets the clock (0 to 1,000,000) |
| `/lotr waypoint travel <ref>` | 0 | Same as the map page request, all rules apply. Stays as the keyboard route after the map lands |
| `/lotr waypoint custom create <name>` / `rename <id> <name>` / `delete <id>` | 0 | Same rules as the map page |
| `/lotr portal reset` | 2 | Clears the once-per-world flag (existing portal entities stay) |
| `/lotr portal info` | 2 | Flag and stored positions |

### Requested from other pods

**Core (`pod:core` issues):**
1. Registry keys in `ModDataRegistries`, generic in the value type like Progression's: `waypoint()`, `waypointRegion()`, `road()`. World binds codecs with `registerSynced`.
2. `ModRegistries.DENSITY_FUNCTION_TYPES` (`Registries.DENSITY_FUNCTION_TYPE`), also needed by the mapgen spec's `river_cut`.
3. `core.api.TravelCompanion`: `boolean travelsWith(ServerPlayer player)`, implemented by entities that should come along on fast travel (the NPC pod: hired units set to follow, owned by that player).
4. Events in `core.event`: `WaypointRegionUnlockedEvent` (player, region id) and `FastTravelEvent` (player, waypoint ref, from pos, to pos, distance, custom flag), posted by World after the fact, for Progression's travel achievements.
5. `core.api.WaypointClient` holder (the `AchievementCounters` pattern): client-side read and request facade that World implements and Progression's map page calls, so Progression never imports `world`. Needs: list of waypoint views (ref, name, lore, `u`, `v`, kind fixed/custom, state locked/usable/barred, barring faction, uses, required ticks), clock ticks, custom limit, countdown status, and `requestTravel(ref)`, `createCustom(name)`, `renameCustom(id, name)`, `deleteCustom(id)`.
6. Items and blocks: `lotrrevival:gold_ring` (stack size 16, fire-resistant, shaped recipe of 4 gold nuggets around an empty centre: nugget top, left, right and bottom), and the Arnor stone brick set (`arnor_brick`, `mossy_arnor_brick`, `cracked_arnor_brick`, each with slab and stairs), all in `#lotrrevival:road_surfaces` where they are road blocks.

**Progression (`pod:progression` issue):** the Map page waypoint layer (markers, lock/barred state, lore tooltip, required time against the clock), the travel button and countdown display, the custom waypoint editor, all through `WaypointClient`; travel-tab achievements from the two Core events.

**NPC (note, no ticket here):** hired units implement `TravelCompanion` in the hiring spec.

## Out of scope

- Faction portals (elven pool and Morgul lava pool), Utumno, the "Middle-earth" and "Classic" world types.
- The conquest exception for enemy faction waypoints (conquest spec).
- Sharing custom waypoints with fellowships (fellowships spec; the record leaves room for a `shared_with` list).
- The banner protected-land check for custom waypoints (banners spec, through a Core hook).
- Bandit theft cancelling a countdown (bandit spec; movement already cancels it in most cases).
- Waypoints, regions, road styles and roads outside the pilot window (each new painted region adds its own data).
- The Map page itself and its tile renderer (mapgen spec ticket 8, not yet filed).
- Village streets (structures spec) and landmark bridges.
- Moving Gollum along on fast travel (no Gollum yet).

## Acceptance tests

- [ ] Unit (build): the validator passes on the pilot data, and fails on fixtures with an unknown region, a waypoint in the wrong region's biome, a duplicate id, a land biome in no region, a road point in the sea and a `through` waypoint 200 blocks off the road.
- [ ] Unit (build): the generated JSON for one waypoint, one region and one road matches a checked-in expected file byte for byte.
- [ ] GameTest: the registries hold the 7 pilot regions, 28 waypoints and 6 roads; every waypoint's region exists.
- [ ] GameTest: required ticks for uses 0, 1, 10 and 30 at short distance are 36,000, 32,760, 14,898 and 4,974; at 102,000 blocks with uses 0 it is 44,064.
- [ ] GameTest: a mock player standing in `shire_woodlands` unlocks `shire` within 20 ticks; standing in `river` unlocks nothing.
- [ ] GameTest: a faction waypoint is barred at `hobbit` -1 and usable at 0.
- [ ] GameTest: with achievement count 9 the custom limit is 6; the 7th create is refused; with count dropped to 0 all 6 remain usable.
- [ ] GameTest: a request with clock below the required time is refused; a zombie targeting the player within 16 blocks blocks it; a creative player ignores the zombie.
- [ ] GameTest: moving the player 1 block during the countdown cancels it; standing still 200 ticks completes it, resets the clock to 0, increments uses and moves a sitting-false tamed wolf and a leashed sheep within 256 blocks, but not a sitting wolf or a sheep 300 blocks away.
- [ ] GameTest: a mounted player arrives still riding the mount.
- [ ] GameTest: offline credit adds 10% of a faked 1-hour logout on a dedicated-server flag and nothing in single-player.
- [ ] GameTest: a gold ring item placed in fire near a mock player in the overworld creates one ring portal and sets the flag; a second ring creates nothing; `/lotr portal reset` allows another.
- [ ] GameTest: a full-size portal sends a mock player to Middle-earth after 80 ticks of contact and builds the twin; the twin sends them back beside the overworld portal; an arriving player is not bounced back.
- [ ] GameTest: death 6,000 blocks from a Middle-earth bed respawns 500-1,500 blocks from the death point on land; death 1,000 blocks from the bed respawns at the bed; death in Middle-earth with only an overworld bed respawns in Middle-earth.
- [ ] GameTest: the spline through fixed control points gives the same samples in two runs; a column 3 blocks from a road centre is road and one 6 blocks away is not; the corridor mask is 0 at 4 blocks, 1 at 36 and between at 20.
- [ ] GameTest: at a road centre the surface is within 2 blocks of the painted base; road blocks are 4 deep; a tree feature placed on a road block fails.
- [ ] GameTest: a road over a 6-deep pool builds a deck 3 blocks above the water, fences on both edges and a pier every 8 blocks.
- [ ] Playtest: throw a ring into a campfire pit, watch the portal rise, cross, find Hobbiton nearby, and return.
- [ ] Playtest: walk the East Road from the landing point to Bree: Shire, then Bree-land unlock with their messages; the road is continuous; the Brandywine Bridge connects; fast travel back to Hobbiton after the clock allows it, with a horse and a dog.
- [ ] Playtest: die in the Lone-lands with a bed in Hobbiton and respawn within a day's walk of the death.

## Tickets

Milestone G2, `phase:2`. Each World ticket has its own package, so tickets in the same wave do not touch the same files (apart from one line each in `world.WorldMod`).

| # | Ticket | Agent | Blocked by |
| --- | --- | --- | --- |
| #77 | core: travel hooks (registry keys, density function register, `TravelCompanion`, travel events, `WaypointClient`) | Opus 1 | none |
| #78 | core: gold ring item and Arnor stone brick set | Opus 1 | none |
| #79 | world: waypoints, regions and roads in the map source (format, validator, generated JSON, pilot data) | sonnet-3 | none |
| #80 | world: `WorldConfig` and Middle-earth respawning | sonnet-4 | none |
| #81 | world: waypoint registries, travel attachment, region unlocking and travel rules | sonnet-4 | #77, #79, #80 |
| #82 | world: ring portal | sonnet-5 | #78, #80 |
| #83 | world: road registry, spline, road network index and `map_corridors` | sonnet-3 | #77, #79 |
| #84 | world: fast travel (requests, countdown, companions, arrival) | sonnet-4 | #81 |
| #85 | world: road surface feature, pilot road styles and road-safe vegetation | sonnet-3 | #83 |
| #86 | world: custom waypoints, travel sync and the `WaypointClient` facade | sonnet-5 | #81, #84 |
| #87 | world: road bridges | sonnet-3 | #85 |
| #88 | progression: Map page waypoint layer, fast travel and custom waypoint UI, travel achievements | Opus 4 | #77, #86 (and the Map page) |

Waves: #79 and #80 now (with #77 and #78 in Core); then #81, #82 and #83 in parallel; then #84 and #85; then #86 and #87; then #88.
