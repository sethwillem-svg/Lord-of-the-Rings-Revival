# Middle-earth dimension and map-based generation

- **Pod:** world
- **Lead:** Opus 2
- **Status:** draft
- **Depends on:** `specs/core/core-apis.md` (faction registry and ids, shared constants, attachments, tags; in progress), NovoAtlas 1.8.2+1.21.1 (NeoForge, LGPL-3.0), map art issue #9 (original drawings)

## Summary

Middle-earth is its own dimension, `lotrrevival:middle_earth`, generated from two painted images: a 16-bit heightmap that fixes the terrain shape and a biome map whose flat colours pick the biome for every 32x32-block cell. NovoAtlas turns those images into terrain; vanilla's noise pipeline only adds caves, aquifers and surface detail. Every world has the same geography: the Shire is always west of the Brandywine and Bree is always on the East Road. Seeds only vary trees, ores, small structures and caves. Roads, rivers and structures are added on top of the image terrain by this pod. The Progression pod's map GUI is rendered from the same images, so the in-game map always matches the terrain. G2 ships only the Shire-to-Bree pilot region on a full-size canvas. The rest of the canvas is ocean until later maps are drawn.

## Player-facing behavior

1. A player enters Middle-earth through a portal (placeholder below; `specs/world/portal.md` owns the real mechanic) and arrives at the **landing point** in the Shire, on or next to the East Road.
2. The terrain is fixed and hand-drawn. The Shire has low rolling green hills cut by the Brandywine. The Old Forest is a dark tangle east of the river, the Barrow-downs are bare round hills beyond it, and Bree-land sits at the crossing of the East Road and the Greenway. The Midgewater Marshes and the start of the Lone-lands lie further east.
3. Biome names show on F3 and in `/locate biome lotrrevival:<id>`. Biome borders follow the painted map: they are stepped at 32-block cells, softened by vanilla's grass and foliage colour blending.
4. Roads are visible worn paths (dirt path, coarse dirt and gravel, with more cobble near Bree). They follow the terrain, cross small streams on plank bridges, and run into landmark structures such as the Brandywine Bridge and Bree.
5. No vanilla hostile mobs (zombies, creepers, skeletons, spiders, witches, slimes, phantoms) spawn in Middle-earth. Passive vanilla animals do. Night and dark-place threats come from the NPC pod's mobs and factions per biome (table below).
6. Beyond the drawn canvas the world is endless ocean (edge pixels are clamped), never void.
7. The map GUI shows the same coastlines, rivers, biomes and roads the player walks through. One heightmap pixel is 8 blocks and one biome pixel is 32 blocks.

## Rules and numbers

### Scale and world size

| Thing | Value | Notes |
| --- | --- | --- |
| Heightmap resolution | 1 px = 8 x 8 blocks | `horizontal_scale.value = 8` |
| Heightmap interpolation | `bicubic` | Wiki recommends bicubic for scale 2-5 and lanczos above 5. Bicubic is the performance default; see Open questions |
| Biome map resolution | 1 px = 32 x 32 blocks | Exactly 1/4 of the heightmap in each axis (`biome_cell_color_map` samples one pixel per 4x4-block biome cell, scaled by 8) |
| Canvas, heightmap | 5120 x 4096 px | 16-bit grayscale PNG, lossless. About 42 MB in RAM (2 bytes per pixel) |
| Canvas, biome map | 1280 x 1024 px | 8-bit RGB PNG, flat colours, no anti-aliasing. About 5 MB in RAM |
| World size | 40,960 x 32,768 blocks | x in [-20480, 20480), z in [-16384, 16384) |
| Map origin | Image centre = block (0, 0) | `center_offset = [0, 0]`. Up in the image is north (-z) |
| Image wrapping | `clamp_to_edge` | Canvas border is deep ocean at least 64 heightmap px (512 blocks) wide, so clamping gives endless ocean |
| Hobbiton to Bree target | about 3,000 blocks by road | About 12 min on foot, about 4 min on horseback. Sets the overall scale (about 23 blocks per mile) |
| Shire width (W-E) | about 2,700 blocks | Reference for the map artist |

### Vertical scale

| Thing | Value | Notes |
| --- | --- | --- |
| Dimension height | `min_y = -64`, `height = 384`, `logical_height = 384` | Same as overworld; build limit 319 |
| `starting_y` | 0 | Black pixel (value 0) is y = 0 |
| `vertical_scale` | 0.005 | y = 0 + 0.005 x value, so 200 heightmap units = 1 block |
| Sea level | 63 | Set in our noise settings; heightmap value 12,600 |
| Max painted value | 60,000 (y = 300) | Leaves 19 blocks for trees and structures. Validator rejects higher values |
| Pilot water | All pilot rivers, lakes and marsh pools sit at sea level (y 63) | No `fluid_height_map` in the pilot. Rule for artists: dry land is never painted below y 64 (value 12,800), or it floods |
| Deep ocean floor | y 20-40 | Gulf of Lune, canvas border |

### Generator configuration

| Field | Value | Notes |
| --- | --- | --- |
| `generator.type` | `novoatlas:image_map` | |
| `generator.settings` | `lotrrevival:middle_earth` | Our noise settings: overworld copy, sea level 63, aquifers on, ore veins off, our own surface rules |
| `underground_density_function` | `novoatlas:no_cave_entrances` | Caves exist underground but do not break the painted surface |
| `enable_carvers` | `false` | Pilot keeps drawn terrain intact; ravines revisited later |
| `biome_source.type` | `novoatlas:biome_cell_color_map` | |
| `biome_source.default_biome` | `lotrrevival:ocean` | Used outside the map and for unmapped colours |
| `surface_biomes.strict` | `true` (required) | See edge cases: in 1.8.2 the non-strict nearest-colour fallback compares only the blue channel, so a stray colour picks an effectively random biome |
| `cave_biomes` | none in pilot | Later: dwarf mines and Moria layers |

### Pilot biome colour table (Shire to Bree)

Colours are key colours for the biome map only, chosen fresh for this repo. The GUI map uses a separate display colour per biome (biomes spec). Heights are painting guides in block y. Faction ids are provisional until `specs/core/core-apis.md` merges. Core owns the names, and this table updates to match.

| Hex | Biome id | Terrain and vegetation | y range | Factions that spawn |
| --- | --- | --- | --- | --- |
| `#1F3A93` | `lotrrevival:ocean` | Open sea, sand and gravel floor. Also the unpainted canvas | 20-55 floor | none |
| `#D8CB8E` | `lotrrevival:shore` | Sand and gravel beaches, sparse grass | 62-66 | none |
| `#3B7DD8` | `lotrrevival:river` | Brandywine, Water and Withywindle channels. Reeds, clay and gravel bed | bed 54-61 | none (shore biome's faction spawns allowed on banks) |
| `#2E64B5` | `lotrrevival:lake` | Still pools and small lakes, lily pads, reeds | bed 52-61 | none |
| `#78B048` | `lotrrevival:shire` | Rolling farmland and meadows, hedgerows, scattered oak and apple trees, many flowers | 66-95 | `lotrrevival:hobbit` |
| `#3E7A2C` | `lotrrevival:shire_woodlands` | Oak and birch woods with clearings, ferns (Green Hill Country, Woody End) | 68-100 | `lotrrevival:hobbit` (fewer), `lotrrevival:high_elf` (rare travellers) |
| `#5F8F6E` | `lotrrevival:shire_marshes` | Flat wet meadows and reed beds, shallow pools, willows (the Marish) | 63-67 | `lotrrevival:hobbit` |
| `#9A9A62` | `lotrrevival:shire_moors` | Open heath and gorse, exposed stone, few trees (North Moors) | 80-115 | `lotrrevival:hobbit` (rare) |
| `#1F4A24` | `lotrrevival:old_forest` | Very dense, dark old-growth forest, huge oaks and willows, thick undergrowth, little light at the floor | 70-95 | none; hostile trees (NPC pod, unaligned) |
| `#A3B56B` | `lotrrevival:barrow_downs` | Treeless round grassy hills, standing stones, mist in low ground | 75-125 | Barrow-wights at night (hostile; faction per core, likely `lotrrevival:angmar`) |
| `#9CC25A` | `lotrrevival:bree_land` | Mixed farmland and copses around Bree-hill | 68-115 | `lotrrevival:bree`, `lotrrevival:ranger_north` (rare) |
| `#2F6B3A` | `lotrrevival:chetwood` | Mixed oak and spruce forest east of Bree | 72-105 | `lotrrevival:ranger_north`, `lotrrevival:bree` (rare) |
| `#4F7A63` | `lotrrevival:midgewater_marshes` | Bog, pools and sedge, dead trees. Swarming insects (ambient) | 63-66 | `lotrrevival:ranger_north` (rare) |
| `#B8AE6E` | `lotrrevival:lone_lands` | Dry rolling grassland and scrub, rocky ridges toward the Weather Hills | 75-150 | `lotrrevival:ranger_north`; `lotrrevival:angmar` raiding parties at night (rare) |
| `#8FA866` | `lotrrevival:far_downs` | Low open downs, sheep-country grass, scattered stones | 80-115 | `lotrrevival:hobbit` (rare), `lotrrevival:bree` travellers (rare) |
| `#6E9A5A` | `lotrrevival:tower_hills` | Higher wooded hills topped by old elven towers (structure) | 90-145 | `lotrrevival:high_elf` |
| `#7A8270` | `lotrrevival:ered_luin_foothills` | Steep stony foothills, spruce and pine, exposed rock and gravel | 100-170 | `lotrrevival:blue_mountains`, `lotrrevival:high_elf` (rare) |

That is 17 biomes; the issue's 11 required regions are all present, plus water, shore and Chetwood. All biomes are tagged `#lotrrevival:is_middle_earth` and not `#minecraft:is_overworld`, so vanilla structures and mods keyed on the overworld tag skip them (see Edge cases). Each biome also gets the `#c:` convention tags that fit (for example `#c:is_forest`, `#c:is_swamp`) so seasons and compat mods still work.

### Roads

| Thing | Value | Notes |
| --- | --- | --- |
| Definition | Polyline per road, control points in **heightmap pixel coordinates** (floats) | Pixel coordinates stay valid if the scale changes later. Converted to blocks by the shared projection |
| Smoothing | Centripetal Catmull-Rom through control points, sampled every 1 block | Plus deterministic per-position jitter of plus or minus 1 block from world seed and position |
| Width | 3 blocks core plus a 1-block ragged edge on each side (50% per block) | `width` overridable per road |
| Materials | From the biome's road palette: Shire, Bree-land and Far Downs use 70% dirt path, 20% coarse dirt, 10% gravel; Bree-land swaps 15% to cobblestone within 128 blocks of Bree; Lone-lands use 50% dirt path, 30% gravel, 20% left as grass (fading road) | Palette lives on the road style, keyed by biome |
| Vertical | Replaces the top surface block; never digs more than 1 block or builds more than 1 block up. Clears plants and snow layers on the road | Steep sections stay rough, which is intended |
| Water crossing | Crossings of 24 blocks or less: plank bridge, deck at water level + 2, fence rails. Longer: road stops at the bank and a fixed landmark (for example the Brandywine Bridge) must cover it | Validator warns on uncovered long crossings |
| Structures | Road blocks never overwrite blocks inside a structure's bounding box; towns supply their own streets | |
| Pilot roads | Great East Road (canvas west edge, Michel Delving, Hobbiton, Bywater, Frogmorton, Brandywine Bridge, Bree, Weathertop direction to the pilot's east edge); Greenway (north-south through Bree); Bucklebury Ferry lane; three Shire lanes | Exact points come from the map art in #9 |
| Map | Every road with `show_on_map: true` is drawn on the GUI map | |

### Rivers

- Rivers are carved into the **heightmap** as channels and painted as `lotrrevival:river` in the biome map. Water fills to sea level automatically.
- A channel must be at least 2 heightmap px (16 blocks) wide, or bicubic smoothing fills it in. Narrower streams wait for a later `stream` feature (see Open questions).
- The river biome strip is at least 1 biome px (32 blocks) wide, so banks within about 8 blocks of the water may report `river`. This is acceptable.
- Rivers above sea level (upland lakes, Misty Mountains streams) need a `fluid_height_map`. It is out of the pilot and specced with the first region that needs it. When added, the fluid map is 0 everywhere except at painted water surfaces, so depressions never flood by accident.

### Structures on the image terrain

Two placement kinds, both owned by `specs/world/structures.md`:

1. **Landmarks** at fixed coordinates (Bree, Brandywine Bridge, Bucklebury Ferry, Weathertop ruin at the pilot's east edge, the elven towers on the Tower Hills). They use a world-pod placement type `lotrrevival:fixed` that takes a list of positions in heightmap pixel coordinates and generates exactly once at each. Terrain under them is flattened by vanilla's structure terrain adaptation, which NovoAtlas 1.8 supports.
2. **Scattered** structures (hobbit holes, barrow tombs, ranger camps, ruins) use vanilla `random_spread` structure sets, filtered by biome tags `#lotrrevival:has_structure/<name>`.

The generation order is fixed: structures, then roads, then biome decoration (trees and flowers skip road blocks).

### Ores and spawn tables

Ores are biome features from `specs/world/ores.md` (pilot: coal, iron, copper and tin everywhere underground; silver only in `ered_luin_foothills`). Ore blocks come from core. Per-biome passive animals are in the biome JSON. Faction NPC spawns follow the table above, in the format defined by `specs/world/spawn-tables.md`; NPC entity types come from the NPC pod. The `monster` spawn category holds no vanilla mobs in any Middle-earth biome.

### Reaching the dimension (placeholder)

- Until the portal spec lands: `/lotrrevival middleearth` (permission level 2) teleports the player to the landing point; run in Middle-earth, it returns them to their overworld spawn. A GameTest helper does the same.
- Landing point: a single block position in `data/lotrrevival/lotrrevival/middle_earth.json` (`landing_point`, heightmap pixel coords), placed in the Shire next to the East Road. The player is put on the highest solid block there.
- The real portal (original-mod-style ring portal, return trips, cooldowns) belongs to `specs/world/portal.md`. This spec only fixes the landing point and the dimension key.

### Map GUI (Progression pod) reading the same images

- The GUI does not read server datapack images at runtime: clients may not have them, and a 42 MB heightmap is too large to send.
- Instead, a **world-pod datagen provider** renders GUI map tiles at build time from the heightmap, the biome map, each biome's display colour and the road polylines. It writes them to `src/generated/resources/assets/lotrrevival/textures/gui/map/z<zoom>/<x>_<z>.png` (256 px tiles, zoom levels 0-3, zoom 3 = 1 tile px per 8 blocks) plus a small `map_index.json` (canvas size, tile counts, scale).
- Shading is a hillshade from the heightmap (light from the north-west) multiplied over the display colours, with water from biome ids, and roads drawn on top.
- Block-to-map conversion uses one shared projection: canvas size, blocks per pixel and origin. Those constants live in core (requested below) so World and Progression never disagree.
- If a server overrides the map datapack, client tiles will not match. That is acceptable and documented.

### When NovoAtlas becomes required

NovoAtlas flips from optional to required **in the same PR that adds `data/lotrrevival/dimension/middle_earth.json`**. Without NovoAtlas, that file names an unknown generator type and the world fails to load. That PR:

- bumps NovoAtlas to **1.8.2+1.21.1** (1.8.1 and 1.8.2 fix quart/block mix-ups in biome sampling that break the 4:1 biome map; 1.8.0 must not be used);
- changes `localRuntime` to a normal compile and runtime dependency in `build.gradle` (we use its type ids only in JSON; compile access is for GameTests);
- sets `type="required"` and `versionRange="[1.8.2,1.9)"` for `novoatlas` in `neoforge.mods.toml`, `side="BOTH"`;
- touches shared files `build.gradle`, `gradle.properties`, `DataGenerators` and the mods.toml template, so Opus 1 must review it.

## Edge cases

- **Multiplayer:** generation is server-side. Clients need NovoAtlas only because the mods.toml requires both sides (see Open questions). GUI tiles ship in our jar, so every client sees the same map.
- **Death, logout, dimension change:** logging out in Middle-earth logs back in there (vanilla). Dying in Middle-earth with no bed or anchor set there respawns at the landing point, not the overworld spawn. The respawn rule is implemented with the portal spec and uses a core attachment for each player's last Middle-earth position.
- **Unmapped colours:** with `strict: true`, any biome map colour not in the table becomes `lotrrevival:ocean`. The build validator fails on any unmapped pixel, so this only happens with a third-party datapack override. Never set `strict: false`: in 1.8.2 the nearest-colour fallback reads only the blue channel, so a stray green pixel can become a lake. Report this upstream.
- **Map edits after release:** terrain is a pure function of the images, so editing an already-generated area leaves seams at chunk borders in existing worlds. After the first public release, map edits are limited to areas marked "unreleased" on the canvas, or ship with a changelog note telling players to trim or regenerate affected regions.
- **Outside the canvas:** clamped edge pixels give endless deep ocean. A world border is not set because 1.21.1 shares one border across dimensions (see Open questions).
- **Structures across the map edge:** none. Landmark positions must lie at least 512 blocks inside the canvas (validated).
- **Vanilla structures:** none spawn, because our biomes are not in any `#minecraft:has_structure/*` tag. Strongholds, mineshafts, trial chambers and ancient cities are absent, and `/locate structure` for them fails.
- **Third-party mods** that add features or spawns to `#c:is_overworld` or `#minecraft:is_overworld` biomes skip Middle-earth. Mods keyed on plain `#c:` vegetation tags may still add content (accepted).
- **Same terrain in every world:** seeds change only decoration, scattered structures and caves. `/seed` does not affect landmarks or roads.
- **Performance:** each density evaluation samples the heightmap with bicubic interpolation. Chunk generation time is benchmarked (acceptance tests) and lanczos is only adopted if it stays in budget.
- **Interaction with other systems:** waypoints (Progression) and invasion zones (NPC) are positioned in heightmap pixel coordinates through the shared projection, never as raw block numbers copied from a test world.

## Data and APIs

### Repo layout (World pod)

| Path | Authored by | Content |
| --- | --- | --- |
| `src/main/resources/data/lotrrevival/novoatlas/heightmap/middle_earth.png` | Hand-drawn (#9) | 16-bit grayscale terrain, 5120 x 4096 |
| `src/main/resources/data/lotrrevival/novoatlas/biome_map/middle_earth.png` | Hand-drawn (#9) | 8-bit RGB key colours, 1280 x 1024 |
| `src/main/resources/data/lotrrevival/lotrrevival/road/<road>.json` | Hand-authored | `{ "style": "lotrrevival:<style>", "width": 3, "show_on_map": true, "points": [[px, pz], ...] }` |
| `src/main/resources/data/lotrrevival/lotrrevival/middle_earth.json` | Hand-authored | `{ "landing_point": [px, pz] }` |
| `src/generated/resources/data/lotrrevival/novoatlas/map_info/middle_earth.json` | Datagen | `height_map`, `starting_y: 0`, `scaling` (`vertical_scale: 0.005`, `horizontal_scale: {value: 8, interpolation: "bicubic"}`), `center_offset: [0, 0]`, `image_wrapping: "clamp_to_edge"`, `surface_biomes` (`map`, `strict: true`, `biomes` from the colour table) |
| `src/generated/resources/data/lotrrevival/dimension/middle_earth.json` | Datagen | Generator config above |
| `src/generated/resources/data/lotrrevival/dimension_type/middle_earth.json` | Datagen | Overworld-like: skylight, beds work, respawn anchors do not, natural, `min_y -64`, `height 384` |
| `src/generated/resources/data/lotrrevival/worldgen/noise_settings/middle_earth.json` | Datagen | See Generator configuration |
| `src/generated/resources/data/lotrrevival/worldgen/biome/*.json`, `tags/worldgen/biome/*.json` | Datagen | Pilot biomes and tags |
| `src/generated/resources/assets/lotrrevival/textures/gui/map/**` | Datagen | GUI tiles and `map_index.json` |

- The colour table is a single Java source of truth in the `world` package. Datagen writes `map_info` from it as plain JSON, so no NovoAtlas classes are needed at datagen time. Images are the only hand-made worldgen binaries, an allowed exception to datagen-first like textures.
- A **map validator** runs in `./gradlew build` (JUnit, no game needed) and fails when:
  - the heightmap is not 16-bit grayscale;
  - either image's sides are not multiples of 16;
  - the heightmap is not exactly 4x the biome map;
  - any value is over 60,000;
  - any biome map colour is not in the table;
  - the table has a duplicate colour or an unregistered biome id;
  - a road or landmark point is outside the canvas or within 512 blocks of its edge.

### Requested from core (via `pod:core` issues; do not implement in `world`)

- Faction registry and ids used above (`specs/core/core-apis.md`).
- Shared constants: dimension key `lotrrevival:middle_earth`, and map projection constants (heightmap canvas 5120 x 4096, 8 blocks per pixel, biome pixel 32 blocks, origin at image centre) with block-to-pixel conversion helpers used by World and Progression.
- Tag keys: `#lotrrevival:is_middle_earth`, `#lotrrevival:has_structure/<name>`.
- Registration through `ModRegistries`: a new datapack registry `lotrrevival:road` (needs Opus 1 approval), the `lotrrevival:fixed` structure placement type, and the road feature type.
- Player attachment for the last Middle-earth position and return position (portal spec).
- Ore blocks (tin, silver) for the ores spec.

## Out of scope

- The map images themselves (#9) and every region beyond Shire to Bree.
- Portal mechanics, return travel and respawn rules (`specs/world/portal.md`).
- Detailed biome decoration, surface rules and sub-biome variants (`specs/world/biomes-eriador.md`), structures, ores and spawn tables (their own specs).
- Fluid heightmap, upland lakes, cave biome layers (Moria, dwarf halls), custom sky, fog and weather.
- Map GUI behavior (Progression); this spec only supplies tiles and the projection.

## Acceptance tests

- [ ] Unit (build): map validator passes on the committed pilot images and fails on fixtures with an unmapped colour, a wrong size ratio or a value over 60,000.
- [ ] Unit (build): projection round-trips: block to pixel to block within 8 blocks; the image centre maps to (0, 0); the north edge is negative z.
- [ ] GameTest: the `lotrrevival:middle_earth` level exists, and its generator and biome source are the NovoAtlas types.
- [ ] GameTest: the biome at the landing point is `lotrrevival:shire`, and the biome at a fixture point on the Brandywine is `lotrrevival:river`.
- [ ] GameTest: the surface height at three fixture pixels equals the painted value x 0.005, give or take 2 blocks.
- [ ] GameTest: no vanilla monster appears in any Middle-earth biome's spawn list.
- [ ] Runtime test (CI): headless client joins, runs `/lotrrevival middleearth`, and generates 400 chunks around the landing point without errors.
- [ ] Benchmark: average chunk generation time in Middle-earth is at most 1.5x a vanilla overworld chunk on the same machine.
- [ ] Playtest: walk the East Road from the landing point to Bree. It is about 3,000 blocks, the road is unbroken except at landmarks, the Brandywine Bridge connects, F3 biome names match the painted map, and there are no vanilla hostiles at night.
- [ ] Playtest: GUI map tiles line up with the terrain at the Brandywine, Bree-hill and the Old Forest edge within one map pixel.

## Tickets

Filled in after approval. Proposed split (1-2 days each):

1. NovoAtlas 1.8.2 required: dependency bump, mods.toml, dimension, dimension type and noise settings datagen, placeholder command, blank ocean canvas (shared-file lock: `build.gradle`, `gradle.properties`, `DataGenerators`).
2. Colour table, pilot biome JSONs and tags datagen, and `map_info` datagen.
3. Map validator (JUnit) and projection unit tests (after the core constants land).
4. Road registry, road feature and road styles.
5. Fixed structure placement type (structures spec supplies the pieces).
6. GUI map tile renderer (datagen) and `map_index.json`, handed to Progression.

## Open questions

1. **Interpolation:** bicubic (fast) or lanczos with window 2 (smoother at scale 8, slower)? Decide after the chunk-time benchmark.
2. **Biome cell size:** 32-block cells give visibly stepped borders. Is that acceptable, or do we pay roughly 4x biome-map RAM for the full-resolution `novoatlas:color_map` source (8-block cells, about 80 MB)?
3. **Bundling:** ship NovoAtlas inside our jar (jar-in-jar; LGPL-3.0 allows it as a separate replaceable jar), or make players install it? Jar-in-jar is friendlier; needs Opus 1 and a NOTICES entry.
4. **Server-only NovoAtlas:** could NovoAtlas be required on the server only (`side="SERVER"`)? It needs a test that vanilla-plus-our-mod clients can join without it.
5. **Carvers:** enable vanilla ravines and caves with surface openings later, or keep the painted surface pristine everywhere?
6. **Streams narrower than 16 blocks:** a decoration feature that traces painted stream lines, or skip them?
7. **World edge:** add a soft barrier (fog, strong current, "Straight Road" flavour) at the canvas edge, or leave endless ocean?
8. **Undead factions:** do Barrow-wights and other undead belong to `lotrrevival:angmar` or to an unaligned hostile group? Core and NPC leads decide.
9. **Upstream:** report the nearest-colour channel bug to NovoAtlas (one-line fix) so non-strict mode becomes safe.

## Sources

- NovoAtlas repository and README: <https://github.com/TheDeathlyCow/novoatlas>
- NovoAtlas wiki, Formats (map info, scaling, interpolation, biome colour provider, chunk generator fields): <https://github.com/TheDeathlyCow/novoatlas/wiki/Formats>
- NovoAtlas wiki, Getting Started (image formats, 1/4-size biome map, north-up, `fluid_height_map`, 16-bit vertical scaling, interpolation guidance): <https://github.com/TheDeathlyCow/novoatlas/wiki/Getting-Started>
- NovoAtlas example datapacks (`avila-basic-example`, `avila-no-caves-example`, `avila-cave-biome-example`, `avila-blend-to-random-example`) and the built-in `novoatlas:caves`, `novoatlas:no_cave_entrances` and `novoatlas:no_caves` density functions, under `common/src/main/resources/` at tag `1.8.2+1.21.1`.
- NovoAtlas changelogs `1.8.0+1.21.1` (biome cell source, `center_offset`, `image_wrapping`, interpolation, fluid heightmaps), `1.8.1+1.21.1` and `1.8.2+1.21.1` (biome sampling fixes).
- NovoAtlas source at tag `1.8.2+1.21.1` (LGPL-3.0), read to confirm sampling behavior: height = `starting_y` + `vertical_scale` x raw sample; image centre at origin; the biome map is sampled per 4-block cell with the same horizontal scale; nearest-colour fallback channel bug.
- Modrinth version list for NovoAtlas on NeoForge 1.21.1 (1.8.2 published 2026-10-15): <https://api.modrinth.com/v2/project/novoatlas/version>
- Minecraft Wiki, Dimension definition and Noise settings: <https://minecraft.wiki/w/Dimension_definition>, <https://minecraft.wiki/w/Noise_settings>
- Original LOTR Mod: player-observable behavior only (separate dimension, fixed hand-drawn geography, roads between places, no vanilla hostile mobs). No code, assets, map image or lang strings were used.
