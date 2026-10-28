# Middle-earth map source

The Middle-earth map is authored here as vector data and rasterized by the build. Nothing in this folder is an
image. `./gradlew build` (or any run, which needs `processResources`) turns it into the two images NovoAtlas
reads, under `build/generated/map/resources/data/lotrrevival/novoatlas/`:

| Image | Size | Format | Scale |
| --- | --- | --- | --- |
| `heightmap/middle_earth.png` | 6,400 x 8,000 | 16-bit grayscale, `y = value * 0.005` | 64 blocks per px |
| `biome_map/middle_earth.png` | 3,200 x 4,000 | 8-bit RGB key colours from the colour table | 128 blocks per px |

The relief image is still made by datagen (`ReliefImageProvider`) from the biome map. The generated images are
never committed. They are deterministic: the same source always produces the same pixels.

Spec: `specs/world/dimension-and-mapgen.md`. Code: `buildSrc/` (`Rasterizer`, `MapValidator`).

## Coordinates

Every coordinate is `[u, v]` in **heightmap pixels**, floats allowed: the same system the spec uses for roads,
rivers, landmarks and the landing point. `u` runs east (right), `v` runs south (down), measured from the
image's top-left corner. Block (0, 0), the Hobbiton landing area, is `[1620, 1460]`.

```
block x = 64 * (u - 1620)        block z = 64 * (v - 1460)
```

The biome map is half this resolution, so biome pixel = `u / 2`. One pixel is 64 blocks, so a coordinate `1.0`
off is 64 blocks.

## Format

GeoJSON-like: each file is a `FeatureCollection`, geometry types are `Polygon` (one ring, the closing point is
optional), `LineString` and `Point`, and every feature has `properties.name`. There is no projection, only the
pixel grid above.

| File | Features | Properties |
| --- | --- | --- |
| `landmass.geojson` | `Polygon` coastlines. A pixel inside any landmass is land, everything else is sea | `name` |
| `biomes.geojson` | `Polygon` biome areas, **painted in file order, later wins**. Top-level `base_height` maps each biome id to its base y in blocks | `name`, `biome` (id from the colour table) |
| `height.geojson` | `Point` hills (`kind: hill`, `height` blocks, `radius` px; a negative height is a dell) and `LineString` ridges (`kind: ridge`, `width` px half-width, `height` or per-vertex `heights`) | see left |
| `water.geojson` | `LineString` rivers (`kind: river`, `width` or per-vertex `widths` in blocks, `depth`) and `Polygon` lakes (`kind: lake`, `bed` y of the lake floor) | see left |
| `landmarks.geojson` | `Point` named places. `expect_biome` makes the validator check the biome there | `name`, `expect_biome` |
| `waypoints.geojson` | Top-level `regions` object plus `Point` waypoints, see "Waypoints and roads" below | `id`, `name`, `region`, `lore`, `faction`, `hidden` |
| `roads.geojson` | `LineString` roads, see "Waypoints and roads" below | `id`, `name`, `width`, `show_on_map`, `through` |

The painted windows live in `src/main/resources/data/lotrrevival/lotrrevival/map_regions.json`
(`{"regions": [{"id", "min": [u, v], "max": [u, v], "released"}]}`, `max` exclusive, even numbers). Source
features must stay inside a window and at least 16 px (1,024 blocks) from the canvas edge. **Everything outside
the windows is the placeholder ocean** (heightmap value 8,000 = y 40, biome `ocean`).

Biome ids and colours come from the colour table in
`src/main/java/io/github/sethwillemsvg/lotrrevival/world/MiddleEarthBiomes.java`; the build reads that file, so
add a new biome there (and in the biome datagen) first. `ocean` and `lake` are never painted by a polygon: the
rasterizer derives them from the landmass, lakes and the heights. `river` is never on the biome map.

## How the rasterizer builds the terrain

All steps run at heightmap resolution inside the window:

1. **Land mask** from the landmass polygons; lakes from the lake polygons.
2. **Land height**: each biome's `base_height` (from the top-most biome polygon), blended with a Gaussian of
   sigma 4 px over land only so biome borders do not cliff, plus the hills and ridges (Gaussians with
   sigma = radius / 3 or width / 3). Land never drops below y 64.
3. **Coast**: land fades to just above sea level over 5 px inland of any water, so beaches are gentle.
4. **Rivers**: land within 1.5 px of a river centre line is lowered to y 65 (blending back over 3 px). That is
   the valley the later `river_cut` density function carves the channel into. Rivers are stored as data only for now.
5. **Water**: sea floor y 40, reached 10 px (640 blocks) offshore; lake beds over 3 px.
6. **Biome map**: one pixel per 2 x 2 heightmap pixels. It is land if the block averages at least y 63.5, and then
   takes the top-most biome polygon at its centre; otherwise it is lake (two or more lake pixels) or ocean. Coast
   pixels in between become ocean, so the biome coast is within one biome pixel of the painted coast.

Every land pixel must be covered by a biome polygon, or the build fails and names the pixel. Put a large base
polygon first and overlay the detail on top of it.

## Waypoints and roads

Spec: `specs/world/waypoints-travel.md`. Both files are read by `TravelSource` and turned into datapack registry
JSON by `TravelJson`; they do not change the terrain. Ids are `snake_case` and unique. All names and lore are our
own words (waypoint names are Tolkien place names).

**`waypoints.geojson`** has a top-level `regions` object and the waypoints as `Point` features:

```json
"regions": { "bree_land": { "name": "Bree-land", "biomes": ["bree_land", "chetwood", "midgewater_marshes"] } },
"features": [ { "type": "Feature",
  "properties": { "id": "bree", "name": "Bree", "region": "bree_land", "faction": "bree",
                  "lore": "The chief village of Bree-land, where the East Road meets the Greenway." },
  "geometry": { "type": "Point", "coordinates": [1819.5, 1466] } } ]
```

- A region is a set of land biomes. Every land biome of the colour table is in exactly one region; `ocean`,
  `shore`, `lake` and `river` are in none. A new biome must be added to a region.
- A waypoint's biome map pixel must be one of its region's biomes. `faction` (a bare id from Core's 25 player
  factions) and `hidden` (default false) are optional.

**`roads.geojson`**: `LineString` features with `id`, `name`, `width` (half-width in blocks, default 4),
`show_on_map` (default true) and `through` (waypoint ids the road must pass, optional). Control points follow
the land: none on an `ocean` or `lake` pixel, all inside a painted window.

**Generated output.** `rasterizeMap` also writes one JSON per entry under
`build/generated/map/resources/data/lotrrevival/lotrrevival/{waypoint_region,waypoint,road}/<id>.json`, so the
files ship in the jar and are never committed. Names and lore are translatable components with an English
fallback (`waypoint.lotrrevival.<id>`, `waypoint.lotrrevival.<id>.lore`, `waypoint_region.lotrrevival.<id>`,
`road.lotrrevival.<id>`). Region ids, biome ids and faction ids are written as `lotrrevival:<id>`. Output is
deterministic: sorted keys, no trailing zeros, two-space indent.

## Validator

`./gradlew build` runs `validateMap` (part of `check`). It first runs the validator against deliberately broken
fixtures to prove it catches them, then checks the real map:

- heightmap is 6,400 x 8,000 16-bit grayscale; biome map is 3,200 x 4,000 RGB;
- every biome colour is in the table; the table has no duplicate colour; source biome ids exist and have a base height;
- painted y plus the biome's amplitude is at most 300 at every pixel;
- every pixel outside the painted windows is the placeholder (value 8,000 / ocean colour);
- source points, the landing point and landmarks are inside a window and 1,024 blocks from the canvas edge;
- land biomes do not average below sea level, lakes are below it, and the outer 16 px rim of a window is flat sea floor;
- every landmark's `expect_biome` matches, and the landing point is in the Shire;
- regions: no unknown or water biome, every land biome in exactly one region;
- waypoints: unique snake_case id, known region, inside a window and 1,024 blocks from the canvas edge, in one of
  its region's biomes, faction one of Core's 25 (the validator keeps a copy in `FactionIds`, which is compared
  with `core/faction/Factions.java` on every build, so adding a faction there fails the build until the copy is
  updated);
- roads: at least 2 points, none on a sea or lake pixel or outside a window, and every `through` waypoint within
  `width + 16` blocks of the control polyline.

The self-test also runs the waypoint rules against one broken fixture per rule, and generates one region, one
waypoint and one road from a fixed fixture and compares them byte for byte with
`buildSrc/src/test/resources/golden/`. If you change the JSON format on purpose, update those three files.

## Adding a region

1. Add a window to `map_regions.json` (the rim must be sea floor, so keep land 26 px inside the window).
2. Add landmass, biome, height and water features inside it, and any new biomes to the colour table first.
3. `./gradlew rasterizeMap validateMap` and look at the result.
