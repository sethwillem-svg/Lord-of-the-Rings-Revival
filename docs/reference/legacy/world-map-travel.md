# Legacy reference: world, map and travel

Reference notes on the behavior of LOTR Mod Legacy 36.15 (Mevans, All Rights Reserved). Written in our own words for clean-room spec writing. Implementers may read these notes; never the original.

Scope: the Middle-earth dimension and its map scale, getting there (portals, world types, respawning), waypoints and fast travel, roads, and the calendar. The map image itself is not described beyond its scale facts.

## 1. Dimension facts

| Fact | Legacy value |
| --- | --- |
| Dimensions | Middle-earth (default id 100) and Utumno (101, a separate underground hell dimension entered from the far north) |
| World height | 1.7.10 standard: y 0-255 |
| Sea level | Water fills every open space at or below y 62 (vanilla 1.7.10 sea level); beaches and surface rules treat y 63 as sea level |
| Generation | A painted biome map (one pixel per biome cell) is read and then noise-zoomed; terrain height comes from each biome's base height and variation, not from a heightmap |
| Map scale | 1 map pixel = 128 x 128 blocks (2^7) |
| Map image | 3,200 x 4,000 pixels, so the canvas is 409,600 x 512,000 blocks |
| Origin | Map pixel (810, 730) is block (0, 0). Conversion: block x = (map x - 810 + 0.5) x 128, block z = (map z - 730 + 0.5) x 128. North is up / negative z |
| World extent | x from about -103,700 to +305,900, z from about -93,400 to +418,600. Everything outside the image is sea |
| Used land | About 290,000 blocks west-east (Lindon coast to the far east of Rhûn) and 430,000 north-south (Forodwaith to southern Far Harad) |
| Sample distances (straight line) | Hobbiton to Brandywine Bridge 4,900; Hobbiton to Bree 12,800; Bree to Weathertop 10,700; Mithlond to Bree 30,200; Hobbiton to Rivendell 45,800; Edoras to Minas Tirith 31,900; Hobbiton to Minas Tirith 102,000; Hobbiton to Mount Doom 110,300; Hobbiton to Umbar 133,300 |
| Spawn cap | NPC cap 100 per 196 loaded chunks; Utumno uses 500 |
| Vanilla hostiles | None spawn in Middle-earth |
| Day length | 48,000 ticks (40 minutes, twice vanilla) |
| Moon | 8 phases; every fourth full moon (every 32 days) is a lunar eclipse, which multiplies night invasion chances by 5 |
| Fixed features | Fixed mountains, roads, Bree villages, Isengard and Rammas Echor walls, a few easter eggs (structures.md). All can be turned off by config, and the "classic" world type has none |

### World types

- **Middle-earth world type**: a normal overworld, but every new player is sent straight into Middle-earth on first login (once per player).
- **Middle-earth Classic**: the same biomes generated randomly by noise (no map image), with no roads, fixed mountains, landmarks or fixed villages.

## 2. Getting to Middle-earth

### The ring portal (main route)

1. In the overworld, a player throws a **gold ring** into fire or lava. When a burning gold ring item exists within 16 blocks of a player, it is consumed, an explosion (power 3) goes off and a floating ring portal appears 3 blocks above.
2. Only **one** such portal can ever be made per world (a world flag). Its position is remembered as the overworld return point.
3. The portal grows for 120 ticks (6 seconds). Once full size, a player who walks into it (not riding, not ridden) is sent to Middle-earth with the usual portal delay; mobs and items touching it are sent straight through.
4. Arrival point in Middle-earth is always block (0, 0) on the top solid block, close to Hobbiton. The first arrival builds a matching ring portal there, which becomes Middle-earth's world spawn point. Walking into it sends you back to the overworld portal.
5. A portal entity found in any other dimension removes itself.

### Faction portals (alternative, alignment-gated)

| Portal | Build | Activation | Who can use it |
| --- | --- | --- | --- |
| Elven portal | 3 x 3 pool of still water on solid ground, ringed (corners excluded) by elven light-grass blocks | Drop an elanor or niphredil flower into the pool while having alignment 1 or more with Lothlórien or the High Elves | Players with alignment 1 or more with Lothlórien or High Elves; NPCs not hostile to them |
| Morgul portal | 3 x 3 pool of lava, ringed by any solid blocks | Drop a bone into it while having alignment 1 or more with Mordor, Angmar or Dol Guldur | Players with alignment 1 or more with Mordor, Angmar or Dol Guldur |

- Using either portal without the alignment sets you on fire and deals 2 damage instead.
- They link overworld and Middle-earth at the **same coordinates** (no scaling), like nether portals: the target side searches 128 blocks for an existing portal of that type and builds a new one if none is found.
- Portals can be disabled by config.

### Respawning

- Dying in Middle-earth respawns in Middle-earth (bed if valid, otherwise Middle-earth's spawn at the ring portal).
- **Middle-earth respawning** (on by default): if the death point is more than 5,000 blocks from your bed, or more than 2,000 from world spawn when you have no bed, you instead respawn 500-1,500 blocks from the death point in a random direction, on the surface. This keeps death from becoming a free trip home at Legacy's scale.
- You cannot set a respawn in Utumno; dying there returns you to Middle-earth at the Utumno entrance.

## 3. Waypoints

| Fact | Value |
| --- | --- |
| Fixed waypoints | 274, each with a name, a map position, a region and an owning faction (or unaligned). One is hidden (Meneltarma's summit) |
| Regions | 60 waypoint regions (Shire, Bree-land, Lone-lands, Gondor, Ithilien, Nurn and so on), each tied to one or more biomes |
| Unlocking | Entering any biome unlocks that biome's whole region at once; all its waypoints become known |
| Faction rule | Unaligned waypoints (161 of them: fords, bridges, ruins, hills) are usable by anyone who has unlocked the region. Faction waypoints (towns and strongholds) also need alignment of **0 or more** with that faction |
| Conquest rule | A waypoint of a faction your **pledged** faction is at war with can also be used if your pledged faction's conquest strength in that 1,024 x 1,024-block zone is 500 or more and conquest there is "effective" for you |
| Faction counts | Hobbits 22, Mordor 19, Near Harad 16, High Elves 10, Rhûn 10, Gondor 8, Taurethrim 5, Lothlórien 3, Fangorn 3, two each for Blue Mountains, Dunland, Isengard, Gundabad, Dale; one each for Bree, Angmar, Woodland Realm, Dol Guldur, Durin's Folk, Rohan, Dorwinion |
| Arrival | Top solid block at the waypoint's column (chunk is loaded first) |
| Map display | Waypoints, their lock state (locked, unlocked, unlocked by conquest) and lore text show on the map screen; fast travel is started from the map |

### Custom waypoints

- Players create their own at their current position with a name. Maximum = 5 + 1 per 5 Middle-earth achievements earned.
- Not allowed inside land protected by someone else's banner, and (config) not below a minimum y.
- Can be shared with fellowships; shared ones must be unlocked by the receiver (by visiting) before use.
- Count as waypoints for fast travel with their own use counter.

## 4. Fast travel

| Rule | Value |
| --- | --- |
| Start | Select an unlocked waypoint on the map screen |
| Cooldown model | A single "time since last travel" clock per player. A trip is allowed only if that clock exceeds the trip's required time; the clock resets to 0 on arrival |
| Required time | min + (max - min) x 0.9^uses, then multiplied by max(1, distance x 0.000012). Defaults: min 180 s (3 min), max 1,800 s (30 min); both are world settings. "uses" counts how often you have travelled to that specific waypoint, so favourite waypoints get cheaper (10 uses: about 12.4 min; 30 uses: about 4.1 min). Distance only matters beyond about 83,000 blocks |
| Cost | No item or currency cost; time is the cost |
| Under attack | Refused if any mob within 16 blocks is targeting you (creative mode exempt) |
| In bed | Refused while sleeping |
| Countdown | 10 seconds (200 ticks) after choosing. Any movement input, jumping or position change cancels it. Chat counts down the last 5 seconds. Bandits stealing from you also cancel it |
| Companions | Brings along everything within 256 blocks that is: your hired units set to follow you, your un-sat tamed pets, your Gollum (if not sitting), and any mob on your lead. Your mount comes with you and you are put back in the saddle. Riders stay on their mounts |
| Offline progress | On servers (not single-player), 10% of the time you spent offline is added to your travel clock when you log back in |
| Disable | Fast travel can be turned off by config |
| After travel | Your "last waypoint" is remembered |

## 5. Roads

| Fact | Value |
| --- | --- |
| Count | 56 named roads (63 segments), for example the East-West Road (Mithlond to the far east), Greenway / North-South Road, Arnor road, Elven way, Old Forest road to Buckland, Isengard road, Harad road, Morannon roads, Gondor south road, and several jungle roads in Far Harad |
| Definition | Each road is an ordered list of waypoints and extra map points. A smooth cubic spline is fitted through all points so the road curves naturally, sampled densely along its length |
| Width | Any block within 4 blocks of the spline is road, so roads are about 8-9 blocks wide |
| Depth | Road material replaces the top 4 blocks |
| Terrain | Within 32 blocks of a road, terrain height and roughness are blended toward the flat base height, so roads run through smoothed corridors. Variants, random rivers and scattered structures are suppressed near roads |
| Materials | Chosen by the biome the road block is in (below). Slabs are used on the top layer where the terrain noise dips, giving half-step edges |
| Repair | Some biomes have worn roads: each road block is only placed with a probability (0.3 to 0.92), leaving gaps of grass |
| Bridges | Where a road meets water, it becomes a bridge: deck height rises 1 block per 2 blocks of water depth, capped at 5 above the water surface, with slabs for half-steps; edge blocks get fence rails, and every 8 blocks along the edge a pillar runs down to the bed. Bridge materials vary (default stone-and-wood, Mirkwood wood, charred wood) |
| Villages | Village streets are separate paths in the culture's road style, laid by the village itself |

### Road styles by biome

| Style | Biomes | Look |
| --- | --- | --- |
| Path (default) | Every biome not listed | Dirt path, coarse dirt and gravel |
| Arnor | Eriador 92% repair, Trollshaws 70%, Lone-lands 40% | Mossy and cracked Arnor stone brick, slabs |
| High-elven | Lindon, Rivendell | Pale elven brick and slabs |
| High-elven ruined | Eregion 70% | Same, cracked and mossy |
| Galadhrim | Lothlórien | Elven brick |
| Wood-elven / ruined | Woodland Realm / Mirkwood 60% | Wood-elven brick |
| Dwarven | Blue Mountains, Iron Hills, Erebor, Red Mountains | Dwarven brick |
| Dale | Dale | Dale brick |
| Rohan mix | Rohan 90%, Adornland 80% | Rohan brick mixed with rock, dirt path and coarse dirt |
| Gondor | Pelennor | Gondor brick |
| Gondor mix | Gondor, Ithilien 70%, Harondor 60% | Gondor brick with mossy and cracked variants |
| Dol Amroth | Dor-en-Ernil | Dol Amroth brick |
| Mordor | Mordor, Dagorlad | Mordor brick |
| Dirt | Eastern Desolation | Coarse dirt |
| Rhûn | Rhúdel | Rhûn brick |
| Dorwinion | Dorwinion | Dorwinion brick |
| Harad | Great Desert 50%, Lostladen 30% | Harad brick |
| Harad path | Harnennor, Southron Coasts | Sand, sandstone, Harad brick and dirt path mix |
| Umbar | Umbar | Umbar brick |
| Gulf | Gulf of Harad | Red sandstone, sand and Gulf brick |
| Taurethrim | Far Harad Jungle 80%, Jungle Edge 50%, Taurethrim Clearing | Taurethrim brick |

## 6. Calendar and seasons

| Fact | Value |
| --- | --- |
| Calendar | Shire Reckoning. A new world starts on 22 Halimath, S.R. 1401 (Third Age 3001). The date advances one day each Middle-earth day (40 real minutes) and is saved with the world |
| Months | 12 months of 30 days plus the single days of Yule (2), Lithe (2), Midyear's Day and Overlithe (leap years only); Shire weekday names |
| Seasons | Spring (Astron to Forelithe), summer (Midyear's Day to Halimath), autumn (Winterfilth to Foreyule), winter (Yule to Rethe). Each season carries a small colour tint (summer warmer, autumn more orange, winter paler) |
| Effects | Seasons are mostly visual: grass and foliage tint, and some leaf effects only in spring and summer. They do not change crops, snow or biomes |
| Display | The date is shown to players in the GUI (exact screen not verified) and drives the eclipse cycle |

## 7. Notes for our design

- Legacy's scale (128 blocks per pixel, 100,000+ block journeys) makes fast travel and its cooldown the core travel loop. At our roughly 4x smaller scale the same formula would feel cheap; keep the "uses make it cheaper" curve, but tune min and max per our distances.
- The ring portal is a one-per-world landmark, not a craftable frame. Faction portals give an alignment-gated second route that we can copy as data (frame block, catalyst item, factions).
- Region unlock by entering a biome is simple and works with our biome map; waypoint faction checks only need "alignment at least 0".
- Middle-earth respawning (respawn near your death if far from your bed) solves long-distance death walks and is worth keeping even at our scale, with smaller thresholds.
