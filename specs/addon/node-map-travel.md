# Node map, waypoints and fast travel (Middle-earth add-on)

- **Project:** 2 of 4 for the add-on (after hiring and reputation; before nemesis and world events, and structures).
- **Status:** draft (2026-11-05), written by the lead; the maintainer reviews it. Design choices the lead made alone are marked **(choice)**.
- **Builds on:** the Middle-earth mod (NeoForge 1.21.1, branch `neoforge-fixes`) and its Seven Stars API, through their public classes only. Both are All Rights Reserved: the add-on depends on their jar and never copies their code, data, textures or lang. Node names are Tolkien place names; every id, description and line of text here is our own.
- **Depends on:** `specs/addon/hiring-reputation.md` (reputation scores in `lotr_revival:army`, and its rule that units on Follow travel with their owner), `specs/addon/debug-logging.md` (loggers `nodemap` and `travel`).
- **Ideas from:** `specs/archive/world/waypoints-travel.md` (the old standalone spec). Kept: fixed and custom waypoints, faction waypoints needing reputation 0 or more, stand-still charge-up, companions, hostile check, arrival search. Dropped: the ring portal, our own dimension, respawning (Middle-earth already respawns players at their faction spawn) and road generation (Middle-earth owns world gen). Changed: travel follows a graph of routes, and the cost is a cooldown and hunger based on the route length, instead of the old per-waypoint clock.

## Summary

Middle-earth is about 96,000 blocks across. The node map turns its places into a travel network: **nodes** (towns, strongholds, crossings, ruins, landmarks) joined by **routes** (roads, paths, rivers, wild country). Players discover nodes by going there; a discovered node tells them about its neighbours. From a known node they can fast travel along known routes to any other known node: stand still for 10 seconds, and they arrive with their mount, pets, led animals and (through the hiring system) their following units. Longer journeys leave them hungrier and put travel on a longer cooldown. Faction towns refuse players their faction dislikes. Players may also make a few camps of their own. All of it is drawn on Middle-earth's own map screen, as a layer on top of it.

## What Middle-earth already has (and what we reuse)

Read from its public classes. We extend these; we do not rebuild them.

| Middle-earth piece | What it does | How the add-on uses it |
| --- | --- | --- |
| `gui.map.MapScreen` (client) | The map screen: a `FullscreenToggeableMapWidget` in public field `mapWidget`, public flags `isFullscreen`, `playerIsInDimension`, `playerBlockPos`. It draws its own buttons and does not call `super.render`. | We draw our layer and panel in NeoForge `ScreenEvent.Render.Post` and take clicks in `ScreenEvent.MouseButtonPressed.Pre` when `event.getScreen() instanceof MapScreen`. |
| `gui.map.MapScreenController` | Opens `MapScreen` client-side; handles ME's op teleport key (`N`) and fullscreen key (`M`). | Untouched. Our "open map" key asks the server, which sends ME's own `PacketOpenMapScreen` (below). |
| `network.packets.S2C.PacketOpenMapScreen(canTeleport)` | Server tells a client to open the map; sent by `MiddleEarthMapItem` with `PermissionsME.checkMapTeleport`. | Our server handler sends the same packet, so op teleport keeps working on maps opened with our key. |
| `gui.utils.widgets.map.MapWidget` | Public `getMapPointFromWorldCoordinate(Vector2d)` (block x/z to screen; **mutates its argument**), `getCurrentMapRatio(mouseX, mouseY)` (screen to 0..1 map ratio, null outside), `isOutsideBounds(...)`, `instantCenterOnRatio(...)`, `moveTo(...)`. Its bounds (`startX`, `startY`, `getWidth()`, `getHeight()`) are protected. | Projection, hit tests, culling and "centre on node". One read-only accessor mixin for the bounds (see Data and APIs). |
| `MapMarkerWidget`, `MapMarkerType` | Marker buttons for ME's spawn picker; fixed enum and ME's own marker texture. | **Not used** (choice): the enum cannot be extended without a mixin and the texture is theirs. We draw our own markers from our own texture. |
| `world.map.MiddleEarthMapConfigs` | `REGION_SIZE` 3,000 (map image pixels), `FULL_MAP_SIZE` 96,000 (blocks). | Map size constants. |
| `world.map.MiddleEarthMapUtils` | `getWorldCoordinateFromInitialMap(u, v)` (map pixel to block; 32 blocks per pixel today), `isWorldCoordinateInBorder(x, z)`. | Converts node positions written in map pixels; rejects nodes outside the map. |
| `world.map.MiddleEarthMapRuntime` | `getBiome(x, z)` from the map data. | Data check in tests: starter nodes stand on land. |
| `world.dimension.ModDimensions` | `isInMiddleEarth(level)`, `ME_WORLD_KEY`, `findSafeMiddleEarthTeleportTarget(level, vec, player)` (loads the chunk, finds a safe landing). `transitionPlayer` dismounts the player. | Dimension checks and the arrival search. We do not call `transitionPlayer` (it would drop the mount); we teleport within the dimension ourselves. |
| `event.PlayerRespawnEvents` | Respawns players in Middle-earth at their faction spawn. | Nothing to add. |
| `PlayerDataService.getPlayerSpawnData` / `SpawnData.getWorldCoordinateBlockPos()` | The player's chosen faction spawn. | "Home" reveal on first entry (below). |
| Faction registry (`DynamicRegistriesME.FACTION`, ids like `middle-earth:gondor`, `middle-earth:longbeards.erebor`) | Factions. | Node owners. |

Middle-earth has no waypoints, routes or fast travel of its own (checked on `neoforge-fixes`, 2026-11-05).

## Player-facing behaviour

### The map

1. The Middle-earth map opens as before (ME's map item) or with our key **Open travel map** (`B` by default, choice). Both show the node layer.
2. Nodes are small markers in our own art, one icon per kind (town, stronghold, crossing, ruin, landmark, camp). Routes are lines between them: road solid brown, path dashed brown, river blue, wild dotted grey.
3. Node states, per player:
   - **Unknown:** not drawn.
   - **Rumoured:** outline marker and name in grey; its routes to known nodes are drawn faint. Cannot be travelled to.
   - **Known:** filled marker. Can be travelled to and from.
   - **Barred:** known, with a red mark; the tooltip says "<Faction> will not receive you." Cannot be travelled to, from or through.
   - **Custom:** the player's own camps, in their own icon.
4. Hovering a node shows its name, kind, owning faction, description and state. Names are drawn next to markers only when zoomed in far enough that markers are at least 24 screen pixels apart, or on hover.
5. Clicking a node selects it and opens the **node panel** (right of the map; over the map's left edge in fullscreen): name, description, state, distance along the route, cooldown it will cause, hunger it will cost, and a **Travel** button. With a node selected, the route there from where the player stands is highlighted in gold. The button is greyed out with the reason when travel is not possible.
6. The panel's **Camps** tab lists the player's custom nodes with **Make camp here**, **Rename** and **Remove** buttons.
7. A player outside Middle-earth sees the layer, but the Travel button says "Only in Middle-earth".

### Discovery

1. Coming within a node's discovery radius (96 blocks by default) makes it **known**: "Discovered: Bree." in the action bar and a short sound. Every direct route neighbour of it that is not hidden becomes **rumoured**.
2. On a player's first entry into Middle-earth after the add-on is installed, nodes within 1,000 blocks of their faction spawn become known, and their neighbours rumoured, so there is somewhere to travel from day one (choice).
3. Nodes marked `start_known` are known to everyone from the start (none in the starter set).
4. **Hidden** nodes are never rumoured; they appear only when visited (Rivendell, Henneth Annûn and Dol Guldur in the starter set).
5. A route is usable when both its ends are known; it is drawn when at least one end is known and the other is rumoured or known.

### Fast travel

1. Select a destination node and press **Travel** (or `/lotr_revival node travel <node>`). The request is refused, with the reason in chat, when:
   - fast travel is off in the config;
   - the player is not in Middle-earth;
   - the player is not within 64 blocks of a known, usable node (the **departure node**; choice: travel goes from node to node, not from anywhere);
   - the destination is not known, is barred, or no longer exists;
   - there is no chain of usable routes from the departure to the destination through known, unbarred nodes ("No known road leads there"; "Your road passes through <Node>, where <Faction> will not receive you" when only a barred node is in the way);
   - the travel cooldown is still running ("You must rest mm:ss more before travelling");
   - the player's food level is below 6 (survival and adventure);
   - the player is in combat: any mob within 16 blocks has the player as its target, or the player hurt or was hurt by a living entity in the last 5 seconds (creative and spectator are exempt);
   - the player is sleeping, or is a passenger of a vehicle someone else steers.
2. Otherwise a **10-second charge** starts: "Travelling to <Node>. Stand still." A bar on the HUD fills; the last 5 seconds also count down in the action bar.
3. The charge is **cancelled** ("Fast travel cancelled.") by moving more than 0.05 blocks from the start spot on any axis (for a mounted player: the mount moving), jumping, taking damage, attacking, death, logout, dimension change, getting into a bed, mounting or dismounting, using an item that starts a use animation (eating, drawing a bow), or a new request (which starts its own charge). Nothing is spent.
4. When the charge completes, every check runs again; a failure cancels with that reason. Otherwise the player arrives at the destination with their companions; hunger and the cooldown are applied; "You arrive at <Node>." The arrival makes the node known (it already was) and posts `NodeTravelEvent.Arrive` so other systems can react.
5. **Companions** (choice, after the old spec, ranges shortened):
   - the player's mount, if it is a living entity: the player arrives in the saddle. Boats, minecarts and other non-living vehicles stay behind and the player is dismounted;
   - mobs on a lead held by the player, within 16 blocks;
   - the player's tamed pets that are not ordered to sit, within 32 blocks;
   - hired units: **the hiring system moves them**, by its own rule (units on Follow within 32 blocks travel with their owner; see `hiring-reputation.md`, Units, Travel). The node map does not redefine that rule; it posts the arrival event and hiring listens.
   - Other players never come along. A player riding as a passenger on the traveller's mount is dismounted first and stays.

### Custom nodes (camps)

1. **Make camp here** marks the player's block position as a custom node, with a name of their choice (1-32 characters after trimming).
2. Refused when: not in Middle-earth; the player already has the maximum (5, choice: a flat number, not tied to achievements, which Middle-earth does not have); another of their camps is within 48 blocks; there is no known fixed node within 3,000 blocks to link to ("Too far from any known place").
3. A camp is linked by **wild** routes to the 2 nearest known, fixed, unbarred nodes within 3,000 blocks. The links are worked out again each time the graph is used, so discovering more nodes can give a camp better links.
4. Camps can be renamed and removed. They are private: only their owner sees and uses them. A camp counts as a known node for departures (standing within 64 blocks of it) and as a destination.

## Rules and numbers

All numbers are config values (`lotr_revival-common.toml`, section `nodemap` / `travel`) unless marked as data.

### Positions

| Thing | Value | Notes |
| --- | --- | --- |
| Node position in data | `"map": [u, v]` (Middle-earth map image pixels, decimals allowed) **or** `"block": [x, z]` | Exactly one. `map` is converted with `MiddleEarthMapUtils.getWorldCoordinateFromInitialMap` (32 blocks per pixel today), so nodes follow ME if it rescales its world |
| Out of the map | node skipped with WARN | `MiddleEarthMapUtils.isWorldCoordinateInBorder(x, z)` false |
| Arrival, fixed node | `ModDimensions.findSafeMiddleEarthTeleportTarget(level, (x, 64, z), player)` | Generates the chunk; finds a safe landing. If it returns empty, the trip is cancelled ("No safe place to arrive at <Node>") and nothing is spent |
| Arrival, custom node | the stored block, if feet and head are free and the block below is solid; else as for a fixed node | |
| Companion placement | random free spot within 3 blocks horizontally of the arrival, up to 8 tries, else the arrival spot | Fall distance reset for everyone moved |

### Discovery

| Thing | Default | Config key |
| --- | --- | --- |
| Check interval | 20 ticks per player in Middle-earth | `nodemap.discover_interval` |
| Discovery radius | 96 blocks horizontal; a node's `discover_radius` overrides | `nodemap.discover_radius` |
| Home reveal on first entry | known within 1,000 blocks of the faction spawn | `nodemap.home_reveal_radius` (0 turns it off) |
| Rumours | direct route neighbours of a newly known node, except hidden ones | |

### Routes and path length

| Route kind | Length factor | Drawn as |
| --- | --- | --- |
| `road` | 1.0 | solid brown, 2 px |
| `river` | 0.8 | solid blue, 2 px |
| `path` | 1.25 | dashed brown, 1 px |
| `wild` | 1.6 | dotted grey, 1 px |

- **Route length** = horizontal length of the polyline `from`, `via` points, `to` (block units) x the kind's factor. Factors are config (`travel.factor.<kind>`).
- **Journey length L** = the smallest sum of route lengths from the departure node to the destination (Dijkstra), over usable routes whose nodes are known and not barred. A departure from beside a node starts at that node; the few blocks to it are ignored.
- Routes are two-way. Two routes between the same pair are both kept; the shorter wins.

### Cost

| Thing | Default | Config key |
| --- | --- | --- |
| Enabled | true | `travel.enabled` |
| Departure radius | 64 blocks from a known usable node | `travel.depart_radius` |
| Charge | 200 ticks | `travel.charge_ticks` |
| Cooldown | `min(max, base + L x per_block)`: base 600 ticks, 0.2 ticks per block, max 12,000 ticks | `travel.cooldown_base`, `travel.cooldown_per_block`, `travel.cooldown_max` |
| Hunger | exhaustion `min(cap, L x 0.002)`, cap 40 (4 exhaustion = 1 food point) | `travel.exhaustion_per_block`, `travel.exhaustion_cap` |
| Minimum food to travel | 6 | `travel.min_food` |
| Coin cost | 0 copper per 1,000 blocks (off) | `travel.coin_per_1000` (paid like hiring: Middle-earth coins, smallest first, with change) |
| Hostile range | 16 blocks | `travel.hostile_range` |
| Combat tag | 100 ticks since the player last hurt or was hurt by a living entity | `travel.combat_tag_ticks` |

Worked values (defaults): L = 0 (neighbouring node a few blocks away): cooldown 600 ticks (0:30), exhaustion 0. L = 10,000: cooldown 2,600 (2:10), exhaustion 20 (5 food points). L = 50,000: cooldown 10,600 (8:50), exhaustion 40 (capped, 10 food points). L = 100,000: cooldown 12,000 (capped, 10:00).

- The cooldown counts down only while the player is online, in any dimension, and is saved with them. Creative and spectator players have no cooldown, hunger or coin cost.
- Hunger is applied as exhaustion, so saturation is used first, as with any work.

### Faction nodes

| Thing | Value | Notes |
| --- | --- | --- |
| Usable | reputation with the node's faction >= 0 | From `lotr_revival:army` (hiring spec). Sub-faction nodes (`longbeards.erebor`) use the sub-faction's own score. A Hostile tier (below 0) bars it |
| Checked | at request, at the end of the charge, and when building the panel | A score that drops during the charge cancels the trip |
| Barred nodes | cannot be departure, destination or a node passed through | Choice: you cannot slip through an enemy town on the road |
| Unknown faction id | the node is treated as unowned, with a WARN at load | |

### Custom nodes

| Thing | Default | Config key |
| --- | --- | --- |
| Maximum per player | 5 | `nodemap.custom_max` |
| Name | 1-32 characters after trimming; shown literally, never translated | |
| Spacing | 48 blocks from the player's other camps | `nodemap.custom_spacing` |
| Link range | 3,000 blocks to known fixed nodes; 2 nearest | `nodemap.custom_link_range`, `nodemap.custom_links` |
| Limit lowered by config | existing camps stay usable; making new ones is refused until under the limit | |

## Edge cases

- **Multiplayer:** nodes and routes are the same for everyone; known, rumoured, camps and cooldown are per player. Several players may charge at once. Two players on one mount: only the one who steers can travel; the passenger is dismounted and stays. Another player standing within the hostile range does not block travel unless they hurt or were hurt by the traveller in the last 5 seconds (combat tag). Op teleport on ME's map (`N`) is untouched and ignores all of this.
- **Death, logout, dimension change:** each cancels a running charge. The node data is copied on death and saved with the player. The cooldown keeps counting in every dimension while online; not while offline (choice: simpler than the old offline credit).
- **Map opened from the overworld:** layer drawn, travel refused. Middle-earth's map shows the player marker only in Middle-earth; ours follows the same flag (`playerIsInDimension`).
- **Datapack changes:** nodes and routes are datapack registries, so they change on world load, not on `/reload`. A removed node disappears from the map; its id stays in the player's known set (harmless, ignored). A route naming an unknown node is skipped with a WARN. A camp whose links are all gone shows "No known road" until the player discovers a node within link range.
- **Arrival blocked:** ME's safe-landing search decides; if it finds nothing, the trip is cancelled before anything is spent and the player stays where they were.
- **Unloaded companions:** only loaded entities come along; no chunks are loaded to look for them.
- **Mounted charge:** a mount standing still is "not moving"; a mount that wanders cancels the charge. The mount's own passengers that are not players come along with it.
- **Leads on fences:** a leashed mob tied to a fence is not held by the player and stays.
- **Hired units:** if the hiring system is not loaded yet or not listening, units simply stay behind; the trip still works.
- **Disguise (hiring spec):** a disguise does not change reputation, so a disguised player is still barred from that faction's nodes (choice; see Open questions).
- **Overlapping tooltips:** ME draws its coordinates and biome tooltip at the cursor; ours is drawn above the marker, so the two do not cover each other.
- **ME map changes:** the layer depends on `MapScreen.mapWidget`, `isFullscreen`, `playerIsInDimension` and `MapWidget`'s projection methods. If ME renames them, the add-on fails to compile against the new jar, which is the intended early warning.

## Data and APIs

### Datapack registries (synced to clients)

Registered with `DataPackRegistryEvent.NewRegistry`, with a network codec, so clients receive them on join.

**`lotr_revival:node`** at `data/<namespace>/lotr_revival/node/<id>.json`:

```json
{
  "name": { "translate": "node.lotr_revival.edoras", "fallback": "Edoras" },
  "description": { "translate": "node.lotr_revival.edoras.desc", "fallback": "A golden hall on a green hill, ringed by a dike and a wall of thorn." },
  "kind": "town",
  "map": [1234.5, 678.0],
  "faction": "middle-earth:rohan",
  "discover_radius": 96,
  "hidden": false,
  "start_known": false
}
```

- `kind`: `town`, `stronghold`, `crossing`, `ruin`, `landmark`. (`camp` is for custom nodes only.)
- `map` or `block` (see Positions); `faction`, `discover_radius`, `hidden`, `start_known` optional.
- The example's numbers are placeholders, not Edoras's place.

**`lotr_revival:route`** at `data/<namespace>/lotr_revival/route/<id>.json`:

```json
{ "from": "lotr_revival:bree", "to": "lotr_revival:weathertop", "kind": "road", "via": [[1240.0, 676.0], [1260.0, 672.0]] }
```

- `via` optional: map pixels (or `via_block` in blocks), for drawing bends and measuring length.

### Server code

- **`NodeGraph`** is built from the two registries on `ServerStartedEvent` and on the client when the registries arrive: node index (by id and by 512-block grid cell), adjacency with route lengths, Dijkstra. Custom nodes are added per query for their owner. Read-only once built.
- **`NodeStanding`**: `int reputation(ServerPlayer, ResourceLocation faction)`, implemented over the hiring system's reputation (`lotr_revival:army`). Tests replace it with a fixed map.
- **`ArrivalFinder`**: wraps `ModDimensions.findSafeMiddleEarthTeleportTarget`; tests replace it with a top-block finder, because game tests run in the overworld near the origin, which is outside ME's map border.
- **`TravelService`**: requests, checks, charges (one per player, ticked on `PlayerTickEvent.Post`), cancels (events: `LivingIncomingDamageEvent`, `AttackEntityEvent`, `LivingEntityUseItemEvent.Start`, `PlayerSleepInBedEvent`, `EntityMountEvent`, logout, death, dimension change), arrival, companions, cost. It runs only when `ModDimensions.isInMiddleEarth(level)`, except through the test constructor.
- **Teleport:** dismount the player, `teleportTo` the mount and the player in the same level, then remount; `ServerPlayer.teleportTo` with the player's yaw and pitch. Not `ModDimensions.transitionPlayer` (it dismounts and changes dimension).

### Player attachment `lotr_revival:nodemap`

Serialized by codec, `copyOnDeath`.

| Field | Type | Notes |
| --- | --- | --- |
| `known` | set of node ids | |
| `rumoured` | set of node ids | Never also in `known` |
| `home_revealed` | bool | Home reveal done |
| `camps` | list of `{ id: int, name: string, pos: BlockPos, created: long game time }` | At most 64 stored, whatever the config |
| `next_camp_id` | int | Never reused |
| `cooldown` | int ticks left | 0 = ready |
| `last_node` | optional node ref | Last arrival |
| `trips` | int | Completed trips (statistics, and future achievements) |

A **node ref** is `fixed:<namespace:path>` or `camp:<id>`, on the wire and in commands.

### Event

`nodemap.api.NodeTravelEvent.Arrive` on `NeoForge.EVENT_BUS`, posted after the player has arrived: player, from position and dimension, to position, departure ref, destination ref, journey length L. Not cancellable. The hiring system listens and moves its following units (its own ticket; this spec does not touch hiring code). A `NodeTravelEvent.Depart` (cancellable, posted at the end of the charge before anything moves) lets other systems veto a trip with a reason.

### Packets (`CustomPacketPayload`, registered in core)

| Id | Direction | Content | Sent when |
| --- | --- | --- | --- |
| `lotr_revival:node_state` | to client | known, rumoured, barred (node id and faction), camps with their links, cooldown, last node, limit | login, respawn, dimension change, any change to the data, map opened |
| `lotr_revival:travel_status` | to client | state (`charging`, `cancelled`, `arrived`, `refused`), target ref, ticks left, reason component | charge start, every 20 ticks, end |
| `lotr_revival:open_node_map` | to server | none | our key pressed; the server answers with ME's `PacketOpenMapScreen(PermissionsME.checkMapTeleport(player))` and a fresh `node_state` |
| `lotr_revival:travel_request` | to server | destination ref | Travel button |
| `lotr_revival:travel_cancel` | to server | none | Cancel button in the HUD bar or panel |
| `lotr_revival:camp_edit` | to server | op `create` (name) / `rename` (id, name) / `delete` (id) | Camps tab |

Server handlers: sender alive, at most 10 requests per second, names capped at 32 characters, everything re-checked on the server. Barred state is computed on the server and sent, so the client never needs reputation data.

### Client code

- **`NodeMapOverlay`** subscribes to `ScreenEvent.Render.Post` and `ScreenEvent.MouseButtonPressed.Pre` / `MouseScrolled.Pre` for `MapScreen` only. It projects nodes with `mapWidget.getMapPointFromWorldCoordinate(new Vector2d(x, z))` (always a new vector), clips to the map with a scissor rectangle, draws routes (line segments as thin quads), then markers, then the hover tooltip and the panel. A click on a marker or the panel is consumed (event cancelled) so ME does not start a drag; any other click goes to ME as before.
- **Zoom level** is not public in ME; label culling uses the screen distance between two projected points instead.
- **One mixin:** `MapWidgetAccessor`, a read-only `@Accessor`/`@Invoker` for `startX`, `startY`, `getWidth()`, `getHeight()` of `MapWidget`, used for the scissor and the panel's position. It changes no behaviour. Without it, culling would fall back to `isOutsideBounds` per marker and lines could spill over the frame. (Choice; needs the maintainer's approval per AGENTS.md.)
- **Key:** `key.lotr_revival.open_node_map`, default `B`, category `key.categories.lotr_revival`.
- **Art:** our own marker sheet `assets/lotr_revival/textures/gui/nodemap/markers.png` (6 kinds x 4 states, 9 x 9 each) and panel frame. Nothing from ME's `map_markers.png` or `map_ui.png`.

### Commands (`/lotr_revival node`)

| Command | Permission | Effect |
| --- | --- | --- |
| `list [player]` | 0 self, 2 others | Known, rumoured and barred nodes, camps, cooldown |
| `discover <players> <node or all>` / `forget <players> <node or all>` | 2 | Changes known nodes |
| `cooldown <players> <ticks>` | 2 | Sets the cooldown |
| `travel <ref>` | 0 | Same as the Travel button, all rules apply |
| `camp create <name>` / `rename <id> <name>` / `delete <id>` | 0 | Same as the Camps tab |
| `path <from ref> <to ref> [player]` | 2 | Prints the route chain, L, cooldown and hunger (debugging) |

### Starter set (data)

37 nodes and 41 routes in `addon/src/main/resources/data/lotr_revival/lotr_revival/`. Names are Tolkien's; descriptions are ours. Factions are Middle-earth faction ids (namespace `middle-earth`). **Positions are set in the data ticket by reading Middle-earth's map in game** (its cursor tooltip shows block coordinates), never by copying Middle-earth's spawn data or other files. Kinds: T town, S stronghold, C crossing, R ruin, L landmark. H = hidden.

| Id | Name | Kind | Faction | Description (our own) |
| --- | --- | --- | --- | --- |
| `grey_havens` | Grey Havens | T | none | White quays on the Gulf of Lune, where grey ships wait for the West. |
| `michel_delving` | Michel Delving | T | shire | The chief town of the Shire, its halls dug into the White Downs. |
| `hobbiton` | Hobbiton | T | shire | Round doors and tidy gardens on the slopes of the Hill. |
| `bucklebury` | Bucklebury | T | shire | The Brandybucks' village across the river, behind the High Hay. |
| `brandywine_bridge` | Brandywine Bridge | C | none | A stone bridge of many arches carrying the road over the river. |
| `bree` | Bree | T | none | A crossroads village of men and hobbits under Bree-hill. |
| `weathertop` | Weathertop | R | none | A bald hill crowned with the broken ring of an old watchtower. |
| `last_bridge` | Last Bridge | C | none | Three stone arches over the Hoarwell, the last sure crossing for many leagues. |
| `ford_of_bruinen` | Ford of Bruinen | C | none | A shallow, fast ford where the road leaves the wild for the hidden valley. |
| `rivendell` | Rivendell | T, H | none | A deep green valley of falling water and old wisdom, hard to find on purpose. |
| `fornost` | Fornost | R | none | Grass-grown walls of a northern king's city, abandoned for an age. |
| `annuminas` | Annúminas | R | none | Fallen towers by the shore of Lake Evendim. |
| `tharbad` | Tharbad | R | none | Rotting piers and a ruined bridge where the old road crossed the Greyflood. |
| `high_pass` | The High Pass | S | goblin_town | A narrow way over the mountains, watched from caves beneath. |
| `doors_of_durin` | Doors of Durin | S | moria | A sheer cliff by a dark lake, where a hidden door opens on a word. |
| `dimrill_gate` | Dimrill Gate | S | moria | The great eastern gate of Khazad-dûm, above the Dimrill Dale. |
| `caras_galadhon` | Caras Galadhon | T | lothlorien | A city of lamps and stairways in the crowns of golden trees. |
| `carrock` | The Carrock | L | none | A great rock standing in the Anduin, with steps cut to its flat top. |
| `gundabad` | Mount Gundabad | S | hobgoblin_tribes.gundabad | A grim peak at the north end of the mountains, hollowed into a warren. |
| `elvenking_halls` | Halls of the Elvenking | S | woodland_realm | A cavern palace behind a great gate, beside the forest river. |
| `dale` | Dale | T | dale | A bright town of bells and markets beneath the Lonely Mountain. |
| `erebor` | Erebor | S | longbeards.erebor | The Lonely Mountain, its front gate guarded by stone and dwarves. |
| `dol_guldur` | Dol Guldur | S, H | mordor | A dark hill in the south of the forest where a shadow once dwelt. |
| `argonath` | Argonath | L | none | Two stone kings on the river, hands raised in warning to the north. |
| `dead_marshes` | Dead Marshes | L | none | Pools and reeds and pale lights over the faces of an old battle. |
| `fords_of_isen` | Fords of Isen | C | none | Stony shallows where the river runs out of the gap below the mountains. |
| `isengard` | Isengard | S | isengard | A ring of rock around a black tower, its gardens turned to pits and forges. |
| `helms_deep` | Helm's Deep | S | rohan | A walled gorge and a keep that has never fallen to a siege. |
| `edoras` | Edoras | T | rohan | A golden hall on a green hill, ringed by a dike and a wall of thorn. |
| `minas_tirith` | Minas Tirith | T | gondor | Seven white walls climbing the shoulder of Mount Mindolluin. |
| `osgiliath` | Osgiliath | R | gondor | The broken city on both banks of the Anduin, held at great cost. |
| `henneth_annun` | Henneth Annûn | S, H | gondor | A hidden refuge behind a curtain of falling water. |
| `pelargir` | Pelargir | T | gondor | An old harbour town on the lower Anduin, full of ships and rope. |
| `dol_amroth` | Dol Amroth | T | gondor | A sea-castle on a headland, home of a proud and fair-spoken line. |
| `minas_morgul` | Minas Morgul | S | mordor | A pale tower in a poisoned vale, glowing faintly at night. |
| `morannon` | The Black Gate | S | mordor | Iron doors between two towers, shutting the pass into the dark land. |
| `barad_dur` | Barad-dûr | S | mordor | A fortress of iron and shadow on the plain of Gorgoroth. |

(`rivendell`, `dol_guldur` and `henneth_annun` are hidden; 23 of the 37 are faction nodes.)

Routes (`a - b`, kind): `grey_havens - michel_delving` road; `michel_delving - hobbiton` road; `hobbiton - brandywine_bridge` road; `brandywine_bridge - bucklebury` path; `brandywine_bridge - bree` road; `bree - weathertop` road; `weathertop - last_bridge` road; `last_bridge - ford_of_bruinen` road; `ford_of_bruinen - rivendell` path; `rivendell - high_pass` path; `high_pass - carrock` path; `bree - fornost` road; `fornost - annuminas` road; `bree - tharbad` road; `tharbad - fords_of_isen` road; `rivendell - doors_of_durin` wild; `tharbad - doors_of_durin` wild; `doors_of_durin - dimrill_gate` road; `dimrill_gate - caras_galadhon` path; `caras_galadhon - argonath` river; `carrock - caras_galadhon` river; `carrock - elvenking_halls` path; `carrock - gundabad` wild; `carrock - dol_guldur` wild; `elvenking_halls - dale` river; `dale - erebor` road; `fords_of_isen - isengard` road; `fords_of_isen - helms_deep` path; `helms_deep - edoras` road; `edoras - caras_galadhon` wild; `edoras - minas_tirith` road; `minas_tirith - osgiliath` road; `osgiliath - henneth_annun` wild; `osgiliath - minas_morgul` road; `minas_tirith - pelargir` road; `pelargir - dol_amroth` road; `argonath - dead_marshes` wild; `dead_marshes - morannon` wild; `morannon - barad_dur` road; `minas_morgul - barad_dur` road.

`via` points are added in the data ticket where a straight line would cross mountains or sea on the map.

## Logging

Loggers `nodemap` and `travel` through `LotrLog.get(...)`, per `debug-logging.md`.

| Level | Logger | Event |
| --- | --- | --- |
| INFO | nodemap | Loaded: node and route counts, ids per namespace; config values in use; graph built (nodes, routes, components) |
| WARN | nodemap | Node outside the map border; unknown faction id; route with an unknown end; route of zero length; starter node on water (test only) |
| DBG | nodemap | `discover: player=<uuid> node=<id> dist=<d> rumoured=[ids]`; `home reveal: player=<uuid> spawn=x,y,z known=[ids]`; `camp create|rename|delete: player=<uuid> id=<n> pos=x,y,z links=[ids]` or the refusal reason; `path: from=<ref> to=<ref> L=<len> chain=[ids]` or `no path (blocked by <id>)` |
| DBG | nodemap (client) | Overlay: map opened, nodes drawn / culled counts (once per open, not per frame), click on `<ref>` |
| INFO | travel | Trip completed: player, from ref, to ref, L, cooldown, exhaustion, companions moved (counts by kind) |
| DBG | travel | `request: player=<uuid> to=<ref> -> charging|refused(<reason>)`; `cancel: player=<uuid> reason=<moved|damage|...>`; `arrive: player=<uuid> at=x,y,z mount=<uuid|none> leashed=<n> pets=<n>`; `arrival search failed: node=<id>` |
| WARN | travel | Arrival search failed; a companion could not be placed and was left behind |
| Summary (every 600 ticks, debug on) | nodemap / travel | discoveries, camps made; requests, refusals by reason, cancels by reason, trips |

## Out of scope

- Sharing camps with other players (no fellowships in Middle-earth yet).
- Sea routes and ships; travel time passing in the world; random encounters on the road.
- Conquest or control changing who owns a node (nemesis and world events project may add it through `NodeTravelEvent.Depart`).
- Achievements or titles for travel (the `trips` counter leaves room).
- Generating roads, signposts or buildings at nodes (Middle-earth owns world gen; structures project may place things at nodes later).
- Travel to or from the overworld.

## Acceptance tests

Game tests in `nodemap/test/`, `@GameTest(template = "empty")`, with fixture nodes injected into a test `NodeGraph` at block positions inside the test area, the fixed-map `NodeStanding` and the top-block `ArrivalFinder`. Mock players are removed in a `finally`.

- [ ] Unit: map pixel `[1500, 1500]` converts to block `[48000, 48000]` with the current ME constants; a node at block `[-10, 5]` is rejected as outside the border.
- [ ] GameTest (data): the starter registries hold 37 nodes and 41 routes; every route's ends exist; every faction id exists in ME's faction registry; no node id repeats. Optional (needs ME map data in the run directory): every starter node's biome is not `ocean`, `river` or a `*_pond`/`*_sea` biome.
- [ ] Unit: graph fixture A-B (road 1,000), B-C (wild 1,000, length 1,600), A-D (road 1,500), D-C (road 1,500), E alone. A to C gives A-B-C, L = 2,600; with B barred it gives A-D-C, L = 3,000; with B and D barred it is refused naming a barred node; A to E is "no path".
- [ ] Unit: cooldown for L = 0, 10,000, 50,000, 100,000 is 600, 2,600, 10,600, 12,000 ticks; exhaustion for L = 10,000 and 50,000 is 20 and 40.
- [ ] GameTest: discovery. A mock player 90 blocks from a node (radius 96) knows it within 20 ticks; at 100 blocks it does not. Its non-hidden neighbour becomes rumoured; its hidden neighbour does not.
- [ ] GameTest: home reveal marks nodes within 1,000 blocks of a faked spawn as known, once.
- [ ] GameTest: faction. A `middle-earth:shire` node is barred at reputation -1 and usable at 0; a trip whose only path passes it is refused with its name.
- [ ] GameTest: refusals. Not within 64 blocks of a known node; destination rumoured; cooldown 1 tick left; food level 5; a zombie targeting the player 10 blocks away (and not at 20 blocks); hurt 3 seconds ago (and not 6 seconds ago). A creative player ignores the zombie and has no cooldown afterwards.
- [ ] GameTest: charge. Moving the player 1 block at tick 100 cancels with nothing spent; standing still 200 ticks arrives at the destination, sets the cooldown for the fixture's L, adds the exhaustion, increments `trips`, and posts one `NodeTravelEvent.Arrive` with the right from, to and L (test listener).
- [ ] GameTest: companions. A player riding a horse arrives riding it; a sheep on the player's lead 10 blocks away comes; a tamed wolf not sitting comes; a sitting wolf and a sheep tied to a fence stay; a boat stays and the player arrives on foot.
- [ ] GameTest: a cancelling `NodeTravelEvent.Depart` stops the trip with its reason and spends nothing.
- [ ] GameTest: camps. Making a 6th camp at limit 5 is refused; names of 0 and 33 characters are refused; a camp 30 blocks from another is refused; a camp with no known fixed node within 3,000 blocks is refused; a valid camp links to the 2 nearest known fixed nodes and can be travelled to; rename and delete work.
- [ ] GameTest: persistence. The attachment survives a codec round trip and a death copy unchanged.
- [ ] Playtest: open the map with `B` and with ME's map item; see nodes, routes, rumours; hover and click; op teleport with `N` still works. Discover Bree, travel back to Hobbiton on a horse with a dog and two hired units on Follow (after the hiring ticket listens to the event); get barred from a Mordor node; make, rename and remove a camp; fullscreen and zoom keep the layer aligned.

## Tickets

Each 0.5-2 days. Paths are under `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/` unless they start with `addon/src/main/resources/`. Every ticket includes its own tests and logging lines.

| # | Ticket | Files | Days | Blocked by |
| --- | --- | --- | --- | --- |
| N1 | Config and coordinates | `nodemap/NodeMapConfig.java`, `nodemap/MapCoords.java` (map pixel and block conversion over `MiddleEarthMapUtils`, border check) | 0.5 | none |
| N2 | Node and route registries | `nodemap/data/NodeDef.java`, `nodemap/data/RouteDef.java`, `nodemap/data/NodeKind.java`, `nodemap/data/RouteKind.java`, `nodemap/data/NodeRegistries.java` (datapack registries with codecs, synced; load logging and WARNs) | 1 | N1 |
| N3 | Graph and paths | `nodemap/graph/NodeGraph.java` (index, grid, adjacency, Dijkstra, camp links), `nodemap/graph/PathResult.java`, `nodemap/graph/NodeRef.java` | 1.5 | N2 |
| N4 | Player data and access rules | `nodemap/state/NodeMapAttachment.java` (codec, copy on death), `nodemap/state/NodeAccess.java` (known / rumoured / barred), `nodemap/state/NodeStanding.java` (over hiring's reputation; test stub) | 1 | N3, hiring reputation ticket |
| N5 | Discovery | `nodemap/discovery/NodeDiscovery.java` (tick check, rumours, home reveal, action bar message) | 1 | N4 |
| N6 | Travel rules and cost | `nodemap/travel/TravelCost.java` (pure), `nodemap/travel/TravelChecks.java` (refusal reasons, hostile and combat tag), `nodemap/travel/CombatTagTracker.java` | 1 | N4 |
| N7 | Charge, arrival and companions | `nodemap/travel/TravelService.java`, `nodemap/travel/ArrivalFinder.java`, `nodemap/travel/CompanionMover.java`, `nodemap/api/NodeTravelEvent.java` | 2 | N6 |
| N8 | Camps | `nodemap/camp/CampService.java` (create, rename, delete, limits, links) | 1 | N4 |
| N9 | Packets and commands | `nodemap/net/NodeMapPayloads.java` and the payload records (`NodeStatePayload`, `TravelStatusPayload`, `OpenNodeMapPayload`, `TravelRequestPayload`, `TravelCancelPayload`, `CampEditPayload`), `nodemap/command/NodeCommands.java` | 1.5 | N5, N7, N8 |
| N10 | Client state, key and HUD | `client/nodemap/ClientNodeState.java`, `client/nodemap/NodeMapKeys.java`, `client/nodemap/TravelHud.java` (charge bar, countdown) | 1 | N9 |
| N11 | Map layer | `client/nodemap/NodeMapOverlay.java` (render and click events on `MapScreen`, routes, markers, tooltip, label culling), `mixin/client/MapWidgetAccessor.java` (read-only; maintainer approval) | 2 | N10 |
| N12 | Node panel and camps tab | `client/nodemap/NodePanel.java`, `client/nodemap/CampsTab.java` | 1.5 | N11 |
| N13 | Art | `addon/src/main/resources/assets/lotr_revival/textures/gui/nodemap/markers.png`, `panel.png` (our own) | 0.5 | none |
| N14 | Starter data | `addon/src/main/resources/data/lotr_revival/lotr_revival/node/*.json` (37), `.../route/*.json` (41), English names and descriptions (lang through datagen, or `fallback` text until the add-on has datagen); positions read from the in-game map | 1.5 | N2 |
| N15 | End-to-end tests | `nodemap/test/NodeMapGameTests.java`, `nodemap/test/TravelGameTests.java`, `nodemap/test/CampGameTests.java` (anything not covered in N3-N9) | 1 | N9 |
| (hiring) | Hiring listens to `NodeTravelEvent.Arrive` and moves following units by its own rule | hiring package; owned by the hiring session | 0.5 | N7 |

Waves: N1, N13; then N2, N14; then N3; then N4; then N5, N6, N8; then N7; then N9; then N10, the hiring listener; then N11; then N12, N15.

## Open questions

1. **Travel from anywhere or only from a node?** This spec requires standing within 64 blocks of a known node (choice), which makes the network matter. The old spec allowed travel from anywhere. Alternative: allow from anywhere with the walk to the nearest known node added to L at the `wild` factor.
2. **Cost model.** Cooldown plus hunger by route length (choice), coin cost off by default. Would the maintainer rather have coins on by default (Middle-earth's copper, like hiring), or the old per-waypoint clock that shortens with use?
3. **Disguise.** Should a hiring-spec disguise as faction F let the player use F's nodes while it lasts? This spec says no.
4. **Default key `B`** for opening the map, or unbound? Middle-earth uses `J`, `N`, `M`; hiring uses `R` and `0`-`9`.
5. **The accessor mixin** on `MapWidget` (read-only bounds). Approve it, or accept per-marker culling with no clipping of route lines at the frame edge?
6. **Node positions.** May the data ticket read Middle-earth's map in game to place the starter nodes (as specified), and is it acceptable that this ties our data to their current map layout? If ME later adds its own waypoints, should we read theirs instead of shipping our own set?
7. **Home reveal radius** of 1,000 blocks: enough for every faction spawn to have at least one node? To check once positions are in.
8. **Hidden nodes** (Rivendell, Henneth Annûn, Dol Guldur): should a hidden faction node also need reputation > 0 (Trusted) to be discovered at all?
9. **Camps limit** of 5 flat: tie it to the best reputation tier instead (for example 3 + 1 per tier above Neutral)?
