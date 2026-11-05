# Structures from licensed builds (Middle-earth add-on)

- **Project:** 4 of 4 for the add-on (after hiring and reputation; node map and fast travel; nemesis and world events).
- **Package:** `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/structures/`, tool in `tools/structure-import/`
- **Status:** draft (2026-11-05), written by the lead; the maintainer reviews after.
- **Depends on:** the Middle-earth mod (NeoForge 1.21.1, branch `neoforge-fixes`): its biome tags, structure sets, factions, NPC types, and its **structure manager and structure nest** blocks, which spawn and respawn faction NPCs. All used through public ids and classes only. `specs/addon/debug-logging.md` (logger `structures`). Feeds `specs/addon/nemesis-events.md` (event venues) and `specs/addon/hiring-reputation.md` (leaders living in outposts become recruiters).
- **Maintainer decision (2026-11-05):** builds come from community builds under **Creative Commons** licences. Which ones are accepted is set out below.
- **Clean-room:** Middle-earth and the original LOTR mod are All Rights Reserved. We never copy their structures, templates, code or data. Naming their ids (biome tags, structure set ids, block ids, NPC type ids, manager pool ids) as integration points is fine. A build that is an export of either mod's structures is refused, whatever licence its uploader claims.

## Summary

Middle-earth already has villages, watchtowers, camps and ruins, and spawns their residents through structure managers and nests. This project adds more places: outposts, castles, camps, bridges, ruins, watchtowers, towers and hideouts, built by the community and published under Creative Commons licences. A Python tool turns a licensed build (`.schem`, `.litematic` or vanilla `.nbt`) into a structure template. It swaps vanilla blocks for Middle-earth blocks, removes entities, command blocks and other unsafe content, and adds Middle-earth's own manager and nest blocks so the right faction's NPCs live in the build. The tool also keeps a licence log for each build and writes the attribution. In game, the builds generate in their faction's Middle-earth biomes from our own structure sets, which stay clear of Middle-earth's sets. Players see who made a build when they first enter it. Each build is tagged as an **event venue** kind, and other systems (first of all the nemesis world events) can look up venues in chunks that have already generated. Players care because Middle-earth's wild land gets more places with people in them, the world events have somewhere to happen, and every builder is credited.

## Player-facing behaviour

1. Exploring Middle-earth, players find new structures in faction lands: a Gondor outpost on a hill, an orc camp in Gundabad's wilds, a bridge over a river in Rohan, a ruined tower in Cardolan, a brigand hideout in the Anduin vales.
2. Faction builds have residents: Middle-earth NPCs of that faction (guards, archers, a leader at outposts and castles), spawned and respawned by Middle-earth's structure manager like its own villages. Killing them changes reputation as usual (hiring spec). Leaders in outposts are recruiters.
3. The first time in a login session that a player steps inside a community build, a grey chat line credits it: *"Ruined Watchtower" by Jane Doe, CC BY 4.0, modified. [source]*. The source link opens the build's page (the game asks for confirmation). Our own builds are not announced.
4. `/lotr_revival credits` lists every community build in the add-on. `/lotr_revival credits here` names the build you are standing in. `/lotr_revival credits mute` turns the entry line off for you (saved on the player).
5. World events (nemesis project) often happen in these structures and in Middle-earth's own watchtowers, camps, ruins and villages.
6. Containers in builds start empty. Builds are not protected: players can break them like any block.

## Rules and numbers

### Which licences are accepted

| Licence (SPDX) | Accepted | Output licence of our adapted template | Why |
| --- | --- | --- | --- |
| `CC0-1.0` | yes | CC0 1.0 | no conditions; we still credit the author as a courtesy |
| `CC-BY-4.0`, `CC-BY-3.0` | yes | CC BY 4.0 | attribution only; adaptations allowed |
| `CC-BY-SA-4.0`, `CC-BY-SA-3.0` | yes | CC BY-SA 4.0 | ShareAlike: an adaptation must use BY-SA 4.0 (or a compatible licence). It **cannot** become our `ASSETS_LICENSE` (CC BY-NC-SA 4.0), so these files keep BY-SA 4.0 as a named exception |
| `CC-BY-ND-*`, `CC-BY-NC-ND-*` | **no** | - | ND forbids sharing adaptations. Our conversion (palette swap, stripping, trimming, added nests) is an adaptation |
| `CC-BY-NC-*`, `CC-BY-NC-SA-*` | **no** (design choice; see Open questions) | - | see below |
| CC 2.x and older, CC ports with local terms, "free to use", "credit me", no licence | **no** | - | unclear terms, or all rights reserved by default |

**Why NC is excluded.** We checked the repo's licences. The code is LGPL-3.0-only (`neoforge.mods.toml`; `LICENSE` and `COPYING.LESSER` carry the GPL-3.0 and LGPL-3.0 texts), and it allows commercial redistribution. Our own assets are CC BY-NC-SA 4.0 (`ASSETS_LICENSE`). So a CC BY-NC-SA 4.0 build would technically match our assets licence, and a CC BY-NC build could be adapted under it. We still exclude NC, for three reasons:
- It would tie those files to NC for good. If the maintainer later moves the add-on's assets to a free licence (for example to match a partner project), every NC build would have to come out.
- Whether "noncommercial" covers mod platforms that pay creators (CurseForge rewards, Modrinth monetisation) is disputed. NC builds would make opting in to those programmes a legal risk.
- NC builds are mostly under older or ported licence versions, whose NC and SA terms do not combine cleanly.

**Output licence (design choice):** an adapted template keeps the source licence family at version 4.0 (CC0 stays CC0, BY becomes BY 4.0, BY-SA becomes BY-SA 4.0). We do not relicense CC0 or BY builds under our NC licence. This is simpler to explain, kinder to the authors, and needs one exception note in `NOTICE` for every licensed build rather than a mix of rules. Our own builds (the placeholder included) use `ASSETS_LICENSE`.

### Finding builds

1. **Where to look:** pages that state a licence for the download itself. Examples: a Git repository with a `LICENSE` file covering the schematic; a Modrinth project (SPDX licence field); a Planet Minecraft or forum post whose text names a CC licence for the download. A licence shown only on screenshots, or a licence for "the map" with the schematic hosted elsewhere, is not enough.
2. **Prefer:** generic building types that fit a faction (outposts, keeps, camps, bridges, watchtowers, ruins, hideouts) with a footprint of 48 x 48 or less, built from vanilla blocks or Middle-earth blocks.
3. **Refuse:**
   - reposts (the uploader must be the author, or the post must name the author and the licence the author chose);
   - exports of the original LOTR mod's or Middle-earth's structures;
   - builds that copy a film set closely (Open questions);
   - builds that need other mods' blocks besides Middle-earth.
4. **Record first:** any agent may add a **candidate** manifest (below). The download is saved under `tools/structure-import/builds/<build_id>/source/`, and a snapshot of the licence page goes into the Internet Archive (its URL goes in the manifest).
5. **Approval:** only the human maintainer sets `"status": "approved"` (with `approved_by` and `approved_on`). The tool refuses to build anything that is not approved, except our own work.
6. **Rejected candidates stay in the log** with a reason, so they are not reviewed twice.
7. **Withdrawal:** if an author asks for their build to be removed, we remove it in the next release even though CC licences cannot be revoked (status `withdrawn`, the date and the reason). Worlds that already generated it keep the blocks.

### Licence log: one manifest per build

`tools/structure-import/builds/<build_id>/build.json` is the source of truth. The tool generates the readable log `docs/licences/structures.md`, the credits block in `NOTICE`, and the in-game credits from these manifests. `build_id` is `snake_case`, unique and never reused.

```json
{
  "id": "cardolan_ruined_watchtower",
  "status": "approved",
  "title": "Ruined Watchtower",
  "author": "Jane Doe",
  "author_url": "https://example.org/u/janedoe",
  "co_authors": [],
  "source_url": "https://example.org/p/ruined-watchtower",
  "archive_url": "https://web.archive.org/web/20261105000000/https://example.org/p/ruined-watchtower",
  "licence": "CC-BY-4.0",
  "licence_url": "https://creativecommons.org/licenses/by/4.0/",
  "licence_evidence": "Licence stated in the download section of the project page",
  "attribution": null,
  "retrieved": "2026-11-05",
  "source_file": "source/ruined_watchtower.schem",
  "source_sha256": "<64 hex>",
  "own_work": false,
  "approved_by": "sethwillem-svg",
  "approved_on": "2026-11-06",
  "region": "wild",
  "kinds": ["ruin", "watchtower"],
  "faction": null,
  "palette": "ruin",
  "site": "land",
  "max_slope": 6,
  "ground_y": null,
  "weight": 2,
  "processors": null,
  "keep_sign_text": false,
  "keep_entities": [],
  "manager": null,
  "nests": [],
  "manual_changes": ["Removed the author's sign advertising a server"],
  "changes": ["(written by the tool)"],
  "output": "addon/src/main/resources/data/lotr_revival/structure/build/cardolan_ruined_watchtower.nbt",
  "output_licence": "CC-BY-4.0",
  "notes": ""
}
```

- `status`: `candidate`, `approved`, `imported` (set by the tool after a successful build), `rejected` (needs `notes`) or `withdrawn`.
- `attribution`: the author's requested credit text, if they gave one; used verbatim instead of the standard line.
- `changes`: written by the tool on each build, as a short list. Examples: "palette: 1,204 blocks swapped to Middle-earth blocks (ruin palette)", "removed 3 command blocks", "removed 12 entities", "emptied 4 containers", "cleared the text of 2 signs", "trimmed to 23 x 19 x 31", "outside air made structure void", "added a structure manager and 3 nests". Together with `manual_changes` this is the "indicate modifications" that CC BY and BY-SA require.
- `own_work: true` (our builds) needs no source URL or approval; licence `LicenseRef-ASSETS_LICENSE`.
- The manifest schema is a JSON Schema file `tools/structure-import/build.schema.json`; the tool validates against it.

### Attribution

| Where | What |
| --- | --- |
| `NOTICE` | a block between `# BEGIN structure credits (generated by tools/structure-import)` and `# END structure credits`: per build, the title, author, source URL, licence and licence URL, "modified" with the change summary, and the output licence. Also the exception sentence: "Structure templates under `data/lotr_revival/structure/build/` are not covered by ASSETS_LICENSE; each keeps the licence listed here." |
| `docs/licences/structures.md` | generated table of every manifest, rejected and withdrawn ones included: id, status, title, author, source, archive, licence, retrieved, approved, changes, output, output licence |
| in game | chat line on entry (below), `/lotr_revival credits`, the data map entry for the structure |
| `neoforge.mods.toml` | the description names `/lotr_revival credits` and `NOTICE` (one-time edit) |

**Standard line:** `"<title>" by <author> (<source_url>) is licensed under <licence> (<licence_url>). Modified for LOTR Revival: <changes summary>. The author does not endorse this mod.` For CC0, the line reads `"<title>" by <author> (<source_url>), CC0.`

**Entry announcement:** checked every 40 ticks per player (staggered by player id). The player must be inside the bounds of a structure that has a third-party credit. One chat line per build per player per login session. Off if the player has muted it or the server config turns it off.

### Conversion tool (`tools/structure-import/`)

Python 3.11+, standard library only (our own NBT reader and writer). Run it with `python3 -I tools/structure-import/import.py <command>`. Design choice: Python because the repo's other tools are Python or Node, the pipeline is file in, file out, and it runs in CI without the game. The one step that needs Minecraft (data-fixing old versions) runs in the add-on's dev mode (below).

| Command | Does |
| --- | --- |
| `inspect <file>` | prints the format, DataVersion, size, palette with counts, block entities and entities |
| `check <id>` | validates the manifest and the licence gate; checks `source_sha256` |
| `convert <id>` | reads the source into the tool's block grid and writes `work/<id>/raw.nbt` (vanilla structure NBT at the source's DataVersion) |
| `build <id>` / `build --all` | convert, upgrade check, remap, strip, fit, nests, limits; writes the template, the data JSON and credits, and updates `changes` and `status` |
| `extract-nests <id> <saved.nbt>` | reads manager and nest positions from a template saved in game (authoring flow below) into the manifest |
| `docs` | regenerates `docs/licences/structures.md` and the `NOTICE` block |
| `verify` | CI: rebuilds everything into a temp folder and fails if any committed output differs, or any approved or own-work manifest fails the gate |

`work/` is git-ignored. `tools/structure-import/blocks.json` (the block dump, below) is git-ignored too, because it is generated from the Middle-earth jar.

#### Input formats

| Format | Read | Notes |
| --- | --- | --- |
| Sponge `.schem` v2 | yes | `Palette` map, varint `BlockData`, index `x + z*Width + y*Width*Length`, `BlockEntities` with `Pos`, `Offset` ignored |
| Sponge `.schem` v3 | yes | `Schematic.Blocks{Palette, Data, BlockEntities}`; biomes ignored |
| Litematica `.litematic` | yes | every region merged into one box; negative region sizes normalised; `BlockStates` long array packed with `max(2, ceil(log2(palette size)))` bits per entry, **spanning long boundaries**; `TileEntities`; `Entities`; pending ticks dropped |
| Vanilla structure `.nbt` | yes | `palette` or the first of `palettes`; `blocks`; `entities` |
| MCEdit `.schematic` (numeric ids, before 1.13) | no | refused: "re-save in a 1.13+ editor" |
| Bedrock `.mcstructure` | no | refused |

All inputs are gzipped NBT (uncompressed is accepted as well).

#### Versions

| Source DataVersion | Handling |
| --- | --- |
| 3953-3955 (1.21-1.21.1) | used directly |
| 1519-3952 (1.13-1.20.x) | `build` stops and asks for the **upgrade step**: run `./gradlew runGameTestServer -PmeJar=<jar> -PstructureUpgrade=tools/structure-import/work`. The add-on loads every `work/*/raw.nbt` through vanilla's structure data fixer, writes `upgraded.nbt` next to it at the current DataVersion, and stops the server. `build` then continues from `upgraded.nbt` |
| below 1519, or above 3955 | refused |

#### Licence gate (automatic part)

The tool refuses `build` when:
- the status is not `approved` (or the build is not `own_work`);
- the licence is not in the accepted list;
- `author`, `source_url`, `archive_url`, `retrieved` or `licence_url` is missing;
- the sha256 does not match;
- the source has any `lotr:` block or block entity (original mod);
- the source has `middle-earth:structure_manager`, `middle-earth:orc_structure_manager` or `middle-earth:structure_nest` and is not `own_work`. These blocks only appear in Middle-earth's own generated structures, so their presence means an export.

The human checks in "Finding builds" stay with the maintainer.

#### Palette remapping

Palettes live in `tools/structure-import/palettes/<name>.json`: `base` (vanilla blocks we keep everywhere: doors, torches, glass, wool, plants, redstone parts), and `gondor`, `rohan`, `dale`, `dwarf`, `elf`, `shire`, `orc`, `ruin` and `neutral` (identity plus base). A palette can `extend` another.

```json
{
  "id": "gondor",
  "extends": "base",
  "blocks": { "minecraft:stone_bricks": "middle-earth:<gondor brick id>" },
  "families": [
    { "from": { "base": "minecraft:stone_bricks", "stairs": "minecraft:stone_brick_stairs", "slab": "minecraft:stone_brick_slab", "wall": "minecraft:stone_brick_wall" },
      "to":   { "base": "middle-earth:<id>", "stairs": "middle-earth:<id>", "slab": "middle-earth:<id>", "wall": "middle-earth:<id>" } }
  ],
  "random": { "minecraft:cobblestone": [["middle-earth:<id>", 3], ["middle-earth:<id>", 1]] },
  "keep": ["minecraft:*_door", "minecraft:torch", "minecraft:wall_torch"],
  "valuable_fallback": "minecraft:stone_bricks"
}
```

- Ids marked `<id>` are filled in from the block dump. Palettes name Middle-earth block ids; they contain no Middle-earth data.
- **Every** block in a build must be matched by a rule (`blocks`, `families`, `random`, `keep`, or the base palette). Unmatched blocks stop the build and the tool lists them, so nothing is swapped silently.
- **Properties** are copied by name when the target has that property and value (facing, half, shape, type, axis, waterlogged, open, hinge, and so on). Others fall back to the target's default, with one warning per block pair.
- `random` picks with a hash of the block position and the build id, so output is deterministic.
- **Validation:** every output id and property must exist in `blocks.json`, the **block dump**. Running `./gradlew runGameTestServer -PmeJar=<jar> -PblockDump=tools/structure-import/blocks.json` writes every registered block id with its properties and values, then stops. Tool unit tests use a small hand-written fixture instead.

#### Stripping (always on)

| Content | Result |
| --- | --- |
| `command_block`, `chain_command_block`, `repeating_command_block`, `structure_block`, `jigsaw`, `barrier`, `light`, `spawner`, `trial_spawner`, `vault`, `fire`, `soul_fire`, `tnt` | air (counted in `changes`) |
| `bedrock`, `end_portal_frame`, `end_portal`, `nether_portal`, `end_gateway`, `reinforced_deepslate`, `lava` | the build stops unless the palette maps the block |
| valuables: `beacon`, `conduit`, `dragon_egg`, `diamond_block`, `emerald_block`, `netherite_block`, `gold_block`, `iron_block`, `ancient_debris`, any ore | `valuable_fallback` (stops players looting a free beacon) |
| `player_head`, `player_wall_head` | air (skins belong to other people) |
| shulker boxes | barrel, empty |
| every entity | removed, except types in `keep_entities` (allowed: `painting`, `item_frame`, `glow_item_frame`, `armor_stand`), whose items and equipment are emptied; at most 32 kept |
| block entities: container items, `LootTable`, `LootTableSeed`, lectern book, jukebox record, chiseled bookshelf books, decorated pot item, brushable block item, `CustomName`, `Lock` | removed |
| sign and hanging sign text | cleared, unless `keep_sign_text` is true (the maintainer has read the text) |
| leaves | `persistent=true` (so they do not decay away from logs) |
| redstone components | kept; listed in the report for review |

#### Fitting to terrain

- **Trim** the box to the bounding box of blocks that are not air.
- **Ground layer:** from `ground_y` in the manifest, or detected: the highest layer where at least 60% of the footprint is natural ground (`grass_block`, `dirt`, `coarse_dirt`, `podzol`, `rooted_dirt`, `mud`, `sand`, `red_sand`, `gravel`, `stone`, `snow_block`, `dirt_path`) and the layer above has less than 60%. No such layer: `ground_y = 0`.
- **Natural ground at or below `ground_y`** becomes `structure_void`, so local terrain shows through. Built blocks (foundations, floors) stay.
- **Outside air** becomes `structure_void`: air connected to the box's sides or top through air, by a 6-way flood fill. Enclosed air (rooms) stays air, so the build clears terrain and trees inside itself.
- Placement puts the ground layer on the terrain's top solid block (offset `-(ground_y + 1)` from the surface heightmap).

#### Limits

| Thing | Limit | Over the limit |
| --- | --- | --- |
| Footprint (x, z) | 48 recommended; 96 maximum | warning above 48; refused above 96 |
| Height | 64 | refused |
| Non-air blocks | 60,000 | refused |
| Block entities | 256 | refused |
| Kept entities | 32 | refused |
| Output file | 256 KiB gzipped | refused |
| Source file kept in the repo | 1 MiB | refused; keep only the URL, archive and hash (`source_file: null`), and the maintainer runs `build` locally |
| Nests per build | 16 | refused |
| NPCs per build (sum of pool maxima) | 24 | refused |
| Distance nest to manager | 20 blocks (sphere) | refused (Middle-earth's managers only accept nests within 20 blocks) |

Multi-piece builds (splitting a large build into jigsaw pieces) are out of scope; one build is one template.

### Residents: Middle-earth's manager and nests

Middle-earth's templates carry one `middle-earth:structure_manager` (or `orc_structure_manager`) block and several `middle-earth:structure_nest` blocks. When placed, the manager loads its pool from Middle-earth's datapack registry `middle-earth:structure_manager_data`. Each nest subscribes to a manager within 20 blocks and then removes itself. The manager spawns and respawns the nest's NPCs, checks they are still alive at dusk and dawn, and respawns a nest once all its NPCs are gone and the delay has passed. **We use this machinery as it is** (design choice). Our builds carry the same blocks, so residents behave exactly like Middle-earth's own: same skins, gear, persistence and respawning, with no NPC code of ours.

- **Insertion by the tool**, from the manifest:
  ```json
  "manager": { "pos": [3, 1, 3], "block": "middle-earth:structure_manager", "facing": "north", "pool": "lotr_revival:gondor_watchtower" },
  "nests": [ { "pos": [3, 9, 3], "nest": "lotr_revival:gondor_watchtower.guard", "radius": 2 },
             { "pos": [1, 1, 5], "nest": "lotr_revival:gondor_watchtower.archer", "radius": 4 } ]
  ```
  - Positions are relative to the trimmed output.
  - The manager gets block entity data `structure_manager.Identifier` = pool, `structure_manager.ToInitialize` = true, `structure_manager.Enabled` = false.
  - Each nest gets block state `enabled=true` and data `structure_nest.ManagerId`, `structure_nest.NestId`, `structure_nest.SpawnRadius`, `structure_nest.IsEnabled` = true.
  - These are Middle-earth's public save keys, used as an interface.
  - The manager is a visible block (planks look; the orc one for orc builds), so the manifest places it where a crate or chest would fit.
- **Authoring flow** (for real builds): load the built template in a dev world with a structure block, place the manager and nests with Middle-earth's own screens (creative and op), save, then run `extract-nests <id> <saved.nbt>` to copy positions and settings into the manifest.
- **Our pools:** `addon/src/main/resources/data/lotr_revival/middle-earth/structure_manager_data/<pool>.json`, in Middle-earth's data format (fields `id`, `spawn_nest[]` with `id`, `respawn_tick_delay`, `bed_radius`, `npc_pool[]` with `entity_type`, `weight`, `amount`, `max_amount`, `faction_key`, `npc_identifier`). The values are our own. Builds may also name Middle-earth's existing pools (for example `middle-earth:gundabad_camp_pool`) if the residents fit.
- **Default pool shapes** (data; per faction, NPC types from that faction):

| Kind | Nests | Respawn |
| --- | --- | --- |
| outpost | leader x1; soldier 2-3; archer 1-2 | 72,000 ticks (3 days, as Middle-earth) |
| castle | leader x1; knight 1-2; soldier 3-4; archer 2-3; civilian 1-2 | 72,000 |
| camp | soldier 2-3; scout 1-2; for orc factions: a warg (Middle-earth `middle-earth:warg`) 0-1 | 72,000 |
| watchtower | guard 1-2 (top); archer 1 | 72,000 |
| tower | soldier 1-2; veteran 1 | 72,000 |
| bridge | guard 2 (one per end) | 72,000 |
| hideout | `brigand.thief` 1-2; `brigand.thug` 1-2; `brigand.chieftain` x1 | 72,000 |
| ruin | none by default; a ruin may hold a hideout pool | - |

- NPC ids use Middle-earth's form `middle-earth:<faction>.<type>` (for example `middle-earth:gondor.soldier`, `middle-earth:mordor.captain`, `middle-earth:brigand.chieftain`). A GameTest checks every id against Middle-earth's registries.
- Our nest ids get lang entries (`structure_nest.lotr_revival.<path>`) so Middle-earth's nest screen shows readable names.
- **Not used:** Middle-earth's `middle-earth:structure_event` registry (natural NPC spawning keyed by structure id). Our structures have no entry, so Middle-earth's default applies inside them (Out of scope).

### Placement: our structure type and structure sets

**Why not Middle-earth's own sets (design choice).** Middle-earth places its structures from one `random_spread` set per region (spacing 10-21 chunks), each a weighted list. Adding our builds to those lists would change which structure every grid cell picks. Existing worlds would then generate differently in new chunks, next to old ones. Changing their sets also means overriding their JSON files, which copies their data. Appending to their template pools (village houses) needs matching jigsaw connectors and is left for later. So we add **our own structure sets with their own salts**. They keep a set distance from Middle-earth's sets and leave Middle-earth's generation untouched.

**Structure type `lotr_revival:build`** (registered by us). It places one template through vanilla jigsaw placement (`size` 1, `max_distance_from_center` 80), plus these site checks, all from the chunk generator's base heights (no chunk is generated for a check):

| Check | Rule |
| --- | --- |
| Enabled | `structures.enabled`, the region enabled, the build not in `structures.disabled_builds` |
| Credit guard | a template under `build/` must have a `lotr_revival:structure_info` entry with a credit (or `own_work`); otherwise the structure never places and logs WARN once |
| Slope | height range over the 4 footprint corners and the centre is at most `max_slope` (default 6, manifest) |
| Water (`site: land`) | the centre and corners are not under water |
| River crossing (`site: river_crossing`, bridges) | in one of the 4 rotations: the centre column is water with a surface at most 3 blocks deep, both ends of the long axis are dry land, and the ends are at most 3 blocks apart in height; the build is rotated to that axis |
| Terrain adaptation | `beard_thin` (fills under foundations, clears a little above) |
| Height | start projected to `WORLD_SURFACE_WG`, offset `-(ground_y + 1)` |

**Placement type `lotr_revival:configured_spread`:** a random spread whose spacing and separation come from config (locked per world, below), with a list of exclusions (vanilla allows only one):

| Exclusion | Default |
| --- | --- |
| Middle-earth's set for the region (table) | no start within 3 chunks of a start of that set |
| every other `lotr_revival` set | no start within 2 chunks |

**Regions** (biome and faction mapping). Biomes come from our biome tags `lotr_revival:region/<region>`, generated with optional (`required: false`) references to Middle-earth's tags, so a datapack can change the mapping. Spacing, separation and on/off are config.

| Region | Faction (default for its builds) | Middle-earth biome tags (`#middle-earth:has_structure/...`) | Kept clear of set | Spacing / separation (chunks) | Palette |
| --- | --- | --- | --- | --- | --- |
| gondor | `middle-earth:gondor` | `gondor_biomes` | `middle-earth:gondor` | 24 / 10 | gondor |
| rohan | `middle-earth:rohan` | `rohan_biomes` | `middle-earth:rohan` | 24 / 10 | rohan |
| dale | `middle-earth:dale` | `dale_biomes` | `middle-earth:dale` | 24 / 10 | dale |
| erebor | `middle-earth:longbeards.erebor` | `longbeards_biomes` | `middle-earth:longbeards` | 28 / 12 | dwarf |
| lothlorien | `middle-earth:lothlorien` | `lothlorien_biomes` | `middle-earth:lothlorien` | 20 / 8 | elf |
| woodland_realm | `middle-earth:woodland_realm` | `woodland_realm_biomes` | `middle-earth:woodland_realm` | 20 / 8 | elf |
| shire | `middle-earth:shire` | `shire_biomes` | `middle-earth:shire` | 24 / 10 | shire |
| mordor | `middle-earth:mordor` | `mordor_biomes` | `middle-earth:mordor` | 24 / 10 | orc |
| isengard | `middle-earth:isengard` | `isengard_biomes` | `middle-earth:isengard` | 20 / 8 | orc |
| gundabad | `middle-earth:hobgoblin_tribes.gundabad` | `gundabad_wilderness` | `middle-earth:gundabad` | 24 / 10 | orc |
| brigand | `middle-earth:brigand` | `anduin_vales_biomes`, `dunland_biomes` | `middle-earth:anduin_vales` | 28 / 12 | ruin |
| wild | none | `ruins_biomes`, `granite_ruin_biomes`, `limestone_ruin_biomes`, `dolomite_ruin_biomes`, `andesite_ruin_biomes` | `middle-earth:cardolan` | 32 / 14 | ruin |

- Each region has one set `lotr_revival:<region>` holding its land builds (weights from manifests). If it has bridges, it also has a set `lotr_revival:<region>_crossings` (default 12 / 4, because most candidates fail the river check).
- A region with no imported builds gets no set.
- Salt: a stable hash of the set id, written into the JSON.
- Faction per build: manifest `faction`, else the region's default; `wild` builds have none.
- Only the Middle-earth dimension has these biomes, so nothing generates elsewhere.
- **Density:** with the defaults, about one of our builds per 24 x 24 chunks per region, on top of Middle-earth's own. Together with Middle-earth's watchtowers, camps and ruins (tagged as venues, below), this aims at the nemesis spec's target of at least 2 venues within 256 blocks of any point in wild land. Tune after playtest.

**World lock.** On a world's first start, the spacing, separation and salt per set are written to `SavedData` `lotr_revival_structures`. Later starts use the stored values. If the config differs, it logs a WARN and keeps the stored values, because changing the grid of a world that has generated would cut structures at old chunk borders. `/lotr_revival structures relock` (op level 4, asks to be run twice) applies the current config to new chunks.

### Config (`lotr_revival-common.toml`, section `[structures]`)

| Key | Default | Notes |
| --- | --- | --- |
| `enabled` | true | all our structure sets |
| `regions.<region>.enabled` | true | |
| `regions.<region>.spacing`, `.separation` | table above | locked per world |
| `regions.<region>.crossings_spacing`, `.crossings_separation` | 12, 4 | |
| `disabled_builds` | [] | build ids that never place |
| `exclusion.middle_earth_chunks` | 3 | |
| `exclusion.own_chunks` | 2 | |
| `credits.announce` | true | entry chat line |
| `venues.dimensions` | `["middle-earth:middle_earth"]` | levels the venue index runs in |
| `venues.probe_budget` | 64 | chunk status reads per second (index completion) |

### Event venues (API for the nemesis project and others)

- **Structure tags** `#lotr_revival:event_venues/<kind>` for the eight kinds `outpost`, `castle`, `camp`, `bridge`, `ruin`, `watchtower`, `tower`, `hideout`, plus `#lotr_revival:event_venues/village` for settlements. Our builds join the tags for their manifest `kinds`. Middle-earth's structures are added as optional entries (naming their ids). Datapacks can change all of it.

| Kind | Middle-earth structures added (`middle-earth:...`) |
| --- | --- |
| outpost | none yet (our builds) |
| castle | `dale_keep`, `longbeards_hall`, `woodland_realm_hall` |
| camp | `gundabad_camp`, `isengard_camp`, `mordor_firecamp`, `traveler_camp` |
| bridge | none (our builds) |
| ruin | `dolomite_ruins`, `dolomite_house_ruin`, `dolomite_ruin_arch`, `granite_ruin`, `granite_ruin_arch`, `granite_ruin_brick_house`, `granite_ruin_tower`, `andesite_ruin_tower`, `limestone_ruin` |
| watchtower | `dale_watch_tower`, `gondor_watch_tower`, `gundabad_watch_tower`, `isengard_watch_tower`, `longbeards_watch_tower`, `lothlorien_watch_tower`, `mordor_watch_tower`, `rohan_watch_tower` |
| tower | none yet (our builds) |
| hideout | `brigand_dungeon` |
| village | `gondor_village`, `rohan_village`, `dale_village`, `hobbit_village`, `woodland_realm_hamlet`, `lothlorien_houses` |

- **Structure info data map** `lotr_revival:structure_info` on `minecraft:worldgen/structure`, file `data/lotr_revival/data_maps/worldgen/structure/structure_info.json`. Per structure: `faction` (optional), `residents` (bool), `region` (optional), `ground_offset` (int), `build` (optional build id), `credit` (optional: title, author, source URL, licence, licence URL, changes summary, own work). The tool writes the entries for our builds. The entries for Middle-earth's structures in the table are hand-written data, with faction and residents from what the structure visibly contains: watchtowers and camps of a faction get that faction and `residents: true`; ruins get none and false; `brigand_dungeon` gets `middle-earth:brigand` and true.
- **Index (only generated chunks).** One `SavedData` `lotr_revival_venues` per level in `venues.dimensions`.
  - **Ingest:** on `ChunkEvent.Load` of a full chunk, each structure start that has its start in this chunk (the chunk's own starts) and is in any venue tag is added once. The entry holds the structure id, start chunk, bounding box, and one bit per chunk the bounds cover.
  - **Completion:**
    - a covered chunk sets its bit when it loads at full status;
    - at ingest, bits for covered chunks that are not loaded are filled by reading the chunk's saved status off-thread (`venues.probe_budget` reads per second, queued);
    - an entry is **complete** when every bit is set.
  - **Never generates terrain:** no read or lookup creates, loads or ticks a chunk.
  - Worlds created before the add-on fill their index as chunks load. `/lotr_revival venues reindex` (op) re-ingests the loaded chunks.
  - **Kinds and info** are resolved from the tags and the data map at lookup time, cached and cleared on datapack reload, so tag edits apply to old entries.
  - **Storage:** cells of 512 x 512 blocks, keyed by cell.
- **Lookup budget:** in memory only; the cells overlapping the search band; aim at under 0.2 ms with 10,000 entries. Server thread only.

```java
package io.github.sethwillemsvg.lotrrevival.structures.venue;

public enum VenueKind { OUTPOST, CASTLE, CAMP, BRIDGE, RUIN, WATCHTOWER, TOWER, HIDEOUT, VILLAGE;
    public TagKey<Structure> tag(); }                       // #lotr_revival:event_venues/<lower name>

public record Venue(ResourceLocation structureId, Set<VenueKind> kinds, BoundingBox bounds,
                    BlockPos centre, Optional<ResourceLocation> faction, boolean residents,
                    ChunkPos startChunk, boolean complete, Optional<String> buildId) {}

public final class StructureVenues {
    /** Venues of any of the kinds whose centre is minDist..maxDist blocks (horizontal) from `from`,
     *  sorted by |distance - (minDist + maxDist) / 2|, then by distance. Complete venues only unless
     *  the query asks otherwise. Never loads or generates chunks. Empty list outside venue dimensions. */
    public static List<Venue> find(ServerLevel level, BlockPos from, int minDist, int maxDist, VenueQuery query);
    public static Optional<Venue> at(ServerLevel level, BlockPos pos);   // venue whose bounds contain pos
    /** Up to `count` open floor spots inside the bounds (2 air over a solid block, not water, not in
     *  structure void columns), only in loaded chunks; empty if none. */
    public static List<BlockPos> openFloor(ServerLevel level, Venue venue, int count, RandomSource random);
}

public record VenueQuery(Set<VenueKind> kinds, Predicate<Optional<ResourceLocation>> faction,
                         boolean includeIncomplete, int limit) {
    public static VenueQuery of(VenueKind... kinds); // any faction, complete only, limit 16
}
```

- `centre`: the x and z of the bounds' centre; y is `bounds.minY + ground_offset` from the data map (0 if none).
- Venue cooldowns, event choice and chunk tickets belong to the nemesis project. Middle-earth has no land protection, so the old spec's "banner protection" field is not provided.

## Edge cases

- **Multiplayer:** credits are per player (session memory and the mute flag). Residents are shared world NPCs; anyone can fight them. The venue index is per level, shared.
- **Death, logout, dimension change:** nothing of ours is tied to the player except the session credit memory (cleared on logout) and the mute flag (saved). Leaving the Middle-earth dimension stops entry checks.
- **Existing worlds:** new builds and config changes apply to new chunks only. A new structure starting in a new chunk next to old chunks is cut where it reaches into the old ones; this is vanilla behaviour, and it is why the grid is locked per world.
- **A build removed in an update (withdrawn):** chunks that already generated keep its blocks. Stored starts of an unknown structure are ignored by vanilla (logged by vanilla). Its venue entries are dropped at load, with one WARN listing the count.
- **Middle-earth renames or removes a block:** a template naming a missing id would place air. The template GameTest fails in CI on Middle-earth's branch head, before release. At runtime, the loader logs one WARN per template with the missing ids.
- **Middle-earth changes its manager or nest save keys:** the nest GameTest fails. At runtime, nests that cannot subscribe are already logged by Middle-earth ("waiting for manager"). We do not try to repair them.
- **Our pool or an NPC type missing** (datapack removed it): the manager never initialises (Middle-earth retries); the pool GameTest catches it for our data, and a WARN at data load names the pool.
- **Manager block broken by a player:** Middle-earth's behaviour (the nests stop respawning). We add nothing.
- **Rotation and mirroring:** jigsaw placement rotates the template. Block states rotate with it, and nest and manager positions are blocks, so they move with it. Bridges pick the rotation that fits the river.
- **Chunk borders:** templates place across chunks during generation like Middle-earth's own; the manager and nests sit in the template, so they appear when their chunk generates.
- **Datapack reload:** venue kinds and structure info re-resolve. Placement data (sets, structures) needs a world restart, as in vanilla.
- **Interaction with other systems:**
  - Hiring: leaders in outposts and castles are recruiters like any other leader.
  - Reputation: killing residents counts normally.
  - Nemesis: residents of a venue's faction join events on their side (theirs to spec). War camps never use a structure.
- **Headless servers:** the credit check runs server side; chat components with links work on vanilla clients.

## Data and APIs

- **Registered by us:** structure type `lotr_revival:build`; structure placement type `lotr_revival:configured_spread`; data map type `lotr_revival:structure_info` (on `Registries.STRUCTURE`); `SavedData` `lotr_revival_structures` (world lock, overworld storage) and `lotr_revival_venues` (per level); player data attachment `lotr_revival:credits_muted` (bool, saved).
- **Commands:** `/lotr_revival credits [here|mute|unmute]` (all players); `/lotr_revival structures relock`, `/lotr_revival venues reindex`, `/lotr_revival venues list [kind] [radius]` (op).
- **Dev modes on `runGameTestServer`:** `-PblockDump=<file>` and `-PstructureUpgrade=<dir>` (system properties `lotr_revival.blockDump`, `lotr_revival.structureUpgrade`). Each runs once at server start, then stops the server before the tests run.
- **Generated data** (by the tool; never hand-edited; `verify` in CI), under `addon/src/main/resources/data/lotr_revival/`:
  - `structure/build/<build_id>.nbt`
  - `worldgen/structure/<build_id>.json`
  - `worldgen/template_pool/build/<build_id>.json`
  - `worldgen/structure_set/<region>[_crossings].json`
  - `tags/worldgen/biome/region/<region>.json`
  - `tags/worldgen/structure/event_venues/<kind>.json` (our builds merged with the Middle-earth entries from `tools/structure-import/venues_middle_earth.json`)
  - `data_maps/worldgen/structure/structure_info.json`
  - lang entries for nests in `assets/lotr_revival/lang/en_us.json` (between markers, if the file is not generated by other code)
- **Hand-written data:** `data/lotr_revival/middle-earth/structure_manager_data/*.json` (our pools); `tools/structure-import/regions.json` (the region table); palettes.
- **Docs and notices (generated):** `docs/licences/structures.md`; the `NOTICE` block.
- **Middle-earth used:**
  - Ids: biome tags `#middle-earth:has_structure/*`, structure set ids (exclusions), structure ids (venue tags), block ids (palettes), `middle-earth:structure_manager`, `middle-earth:orc_structure_manager` and `middle-earth:structure_nest` with their block entity save keys, the datapack registry `middle-earth:structure_manager_data` and its JSON format, the `middle-earth:npc` entity and NPC type and faction ids, and the dimension `middle-earth:middle_earth`.
  - Classes: `ModDimensions.ME_WORLD_KEY`, `DynamicRegistriesME.STRUCTURE_MANAGER_DATA`, `DynamicRegistriesME.NPC_TYPE` and `FACTION` (GameTests), `StructureManagerDataLookup` (pool check), `NpcEntity` (resident checks in tests).
  - No mixins.
- **Seven Stars API:** not needed.

## Logging

Logger `LotrLog.get("structures")`; debug with `lotr_revival.debug=structures`. The Python tool prints its own report to the console and to `work/<id>/import.log` (not `LotrLog`).

| Level | When | Example |
| --- | --- | --- |
| INFO | data load: builds, sets, regions with **effective** spacing and separation (locked or new), pools, venue tag sizes, credits | `[structures] loaded 3 builds, 2 sets (gondor 24/10 locked, wild 32/14 new), 4 pools, venues: camp=5 watchtower=9 ...` |
| INFO | venue index per level at load and save | `[structures] venues middle-earth:middle_earth: 214 entries (188 complete)` |
| WARN | config differs from the world lock; a build without credit (never placed); a template with unknown block ids; a missing pool, NPC type or faction; withdrawn structures dropped from the index; the probe queue full (probes delayed) | `[structures] world lock keeps gondor spacing 24 (config says 30)` |
| ERROR | exceptions loading or saving the index or the lock, or in the dev modes, with stack traces | |
| DBG | a candidate start: set, chunk, biome, checks with values, result | `[DBG structures] start lotr_revival:placeholder_watchtower set=lotr_revival:gondor chunk=12,-4 slope=4/6 water=no -> placed tick=48213` |
| DBG | skips with reason (`excluded middle-earth:gondor within 3`, `slope 9/6`, `water`, `disabled`, `no credit`, `no river`) | |
| DBG | venue ingest, completion, reindex | `[DBG structures] venue + middle-earth:gondor_watch_tower start=40,-7 bounds=... bits 3/4` |
| DBG | a lookup: inputs, candidates scanned, results, time | `[DBG structures] find from=640,70,-112 band=64..256 kinds=[camp,ruin] scanned=17 -> 2 (0.04 ms)` |
| DBG | a credit line shown | `[DBG structures] credit cardolan_ruined_watchtower -> player=3f2a` |
| DBG summary (every 600 ticks) | starts placed and skipped by reason, venues added and completed, probes done and queued, lookups and their average time, credits shown | |

No per-tick lines; the credit checks and probes are counted in the summary.

## Out of scope

- Splitting large builds into several jigsaw pieces; builds over 96 x 96 x 64.
- Adding our builds to Middle-earth's structure sets or village pools.
- Underground structures (Moria, Goblin Town), ocean and Umbar coast builds (Umbar can be a region later).
- Loot: containers start empty. Loot tables per kind are a later ticket.
- Entries in Middle-earth's `structure_event` registry (natural spawns inside our structures).
- Protection of structures, structure decay or ruin processors of our own (manifests may name existing processor lists, Middle-earth's included).
- Pre-1.13 schematics and Bedrock structures.
- A web catalogue of candidate builds.

## Acceptance tests

GameTests in `structures/test/`, `@GameTest(template = "empty")`, headless (`runGameTestServer`). Each logs `[test] START/PASS/FAIL`. Mock players are removed in a `finally`.

- [ ] **`TemplateTests.allBuildTemplatesValid`:**
  - every template under `lotr_revival:build/` loads;
  - its raw palette ids all exist in the block registry (read from the NBT, so missing blocks are not hidden as air);
  - size within the limits;
  - no stripped block types, no entities outside the allowed list, no container items or loot tables, nests within 20 blocks of the manager.
- [ ] **`TemplateTests.everyBuildHasCredit`:** every `lotr_revival:build` structure has a `structure_info` entry with a credit (or own work) whose licence is in the accepted list; every credit names an existing template.
- [ ] **`PoolTests.ourPoolsResolve`:** every `lotr_revival` entry in `middle-earth:structure_manager_data` loads; every `npc_identifier` is in `DynamicRegistriesME.NPC_TYPE`; every `faction_key` in `FACTION`; every nest id named by a template exists in its pool.
- [ ] **`ResidentTests.placeholderSpawnsGondorGuards`** (timeout 200 ticks):
  - place the placeholder template on a stone floor next to the test origin;
  - the manager block is there with `ToInitialize` true;
  - within 200 ticks both nest blocks are gone (subscribed), and at least 2 `middle-earth:npc` entities of faction `middle-earth:gondor` are within the bounds.
- [ ] **`PlacementTests.spreadMaths`:** for a fixed seed, salt, spacing and separation, `configured_spread` gives the same candidate chunks as a reference table; no two candidates are closer than the separation; an exclusion predicate removes candidates within N chunks.
- [ ] **`PlacementTests.siteRules`:** the slope, water and river-crossing checks on synthetic height and fluid columns (pass and fail cases, all 4 rotations).
- [ ] **`PlacementTests.worldLock`:** the first lock stores the config values; a second start with other config keeps the stored values and logs the WARN; `relock` replaces them.
- [ ] **`VenueTests.ingestAndFind`:**
  - generate a start of the placeholder structure in memory and feed it to the ingest path with 2 of its 4 chunks "loaded";
  - `find` (complete only) returns nothing; mark the other 2, and `find` returns it with kind `watchtower`, faction `middle-earth:gondor`, `residents` true;
  - outside the distance band, or with kind `camp`, it does not;
  - `at(pos)` inside the bounds returns it.
- [ ] **`VenueTests.persistence`:** the index survives a `SavedData` save and load. An entry of an unknown structure is dropped with a WARN.
- [ ] **`VenueTests.neverLoadsChunks`:** a lookup over a band of unloaded chunks leaves the level's loaded chunk count unchanged.
- [ ] **`CreditTests.announceOnce`:** a mock player inside an indexed licensed build gets exactly one chat line containing the author, the licence and the source URL; a second check gets none; after `credits mute` a new session gets none. Own-work builds are never announced.
- [ ] **`DevModeTests.blockDumpFormat`:** the dump writer produces JSON with `minecraft:stone` and at least one `middle-earth:` block, each with its properties.
- [ ] **Optional (not required in CI), `PlacementTests.locateInMiddleEarth`:** in the Middle-earth level, a locate for the placeholder structure from a point in Gondor finds one within 64 chunks.
- [ ] **Tool unit tests** (`python3 -I -m unittest discover tools/structure-import/tests`, run in CI):
  - NBT: round trip of every tag type, gzip and uncompressed, modified UTF-8 strings.
  - Formats: the same fixture build read from Sponge v2, Sponge v3, `.litematic` (2 regions, one with negative size; palettes of size 2, 3, 17 and 300 so entries span longs) and `.nbt` gives the same block grid; index order is checked on an asymmetric 3 x 4 x 5 box; `.schematic` and `.mcstructure` are refused.
  - Versions: DataVersion 3955 passes; 3700 asks for the upgrade step; 1400 and 4100 are refused.
  - Licence gate: each accepted SPDX passes; NC, ND, CC 2.0, "none" and unknown are refused; missing author, source, archive or date and a sha mismatch are refused; a `lotr:` block, or a Middle-earth manager in a build that is not own work, is refused.
  - Remap: exact rules; family rules carry facing, half, shape and waterlogged; `random` is deterministic; an unmatched block stops the build and is listed; a property missing on the target falls back with one warning.
  - Strip: each row of the stripping table, and the `changes` lines it writes.
  - Fit: trim; ground detection on a plot with a 2-layer grass base; outside air becomes structure void and an enclosed room stays air; ground replacement.
  - Limits: each limit just under passes and just over is refused.
  - Nests: the manager and nest block entities carry exactly Middle-earth's keys and values; a nest 21 blocks from the manager is refused; `extract-nests` reads back what `build` wrote.
  - Emit: placeholder outputs match the golden files; `verify` fails after a committed file is edited by hand.
  - Docs: the `NOTICE` block is replaced between its markers and nothing else in `NOTICE` changes; running `docs` twice gives no diff.
- [ ] **Playtest:** fly over Gondor and the wilds with the placeholder enabled; builds sit on the ground (no floating, no buried doors), keep clear of Middle-earth's villages, have guards that respawn after 3 days, credits show once, and `/lotr_revival venues list` shows Middle-earth's watchtowers and camps.

## Tickets

Each ticket is 0.5-2 days. Tickets 1-9 use only the **placeholder build**: `placeholder_watchtower`, a 7 x 7 x 11 stone watchtower we generate ourselves with a script (own work, `ASSETS_LICENSE`). It is placed in the gondor region, kinds `watchtower`, with a manager and 2 nests on our pool `lotr_revival:gondor_watchtower`. No third-party build is imported until ticket 12.

1. **Tool: NBT and readers** (1.5 d). `tools/structure-import/sinr/nbt.py`, `sinr/formats/{sponge,litematic,vanilla}.py`, `sinr/grid.py`, `import.py` (`inspect`, `convert`), `tests/test_nbt.py`, `tests/test_formats.py`.
2. **Tool: placeholder and fixtures** (0.5 d). `tools/structure-import/placeholder/make_placeholder.py` writes `builds/placeholder_watchtower/source/placeholder_watchtower.schem` (v3) and the format fixtures under `tests/fixtures/`; `builds/placeholder_watchtower/build.json`; `build.schema.json`.
3. **Add-on dev modes** (1 d). `structures/dev/BlockDump.java`, `structures/dev/StructureUpgrade.java`, the system properties in `addon/build.gradle` (touching `build.gradle`), `structures/test/DevModeTests.java`; `.gitignore` entries for `tools/structure-import/work/` and `blocks.json`.
4. **Tool: remap, strip, fit, limits** (2 d). `sinr/remap.py`, `sinr/strip.py`, `sinr/fit.py`, `sinr/limits.py`, `palettes/base.json`, `palettes/neutral.json`, `palettes/gondor.json` (from the block dump), tests.
5. **Tool: nests, emit, verify** (1.5 d). `sinr/nests.py` (insert and `extract-nests`), `sinr/emit.py` (template, structure, pool, set, biome and venue tags, data map, lang), `regions.json`, `venues_middle_earth.json`, `verify`, golden files; first commit of the placeholder outputs.
6. **Tool: licence gate and docs** (1 d). `sinr/manifest.py`, `sinr/licences.py`, `sinr/docs.py` (`docs/licences/structures.md`, `NOTICE` block), tests; adds the CI steps (unit tests and `verify`) to `.github/workflows/build.yml` (touching the workflow).
7. **Structure type, placement and config** (2 d). `structures/StructuresModule.java` (registration, called from `LotrRevival`), `structures/StructuresConfig.java`, `structures/BuildStructure.java`, `structures/ConfiguredSpreadPlacement.java`, `structures/SiteRules.java`, `structures/WorldLock.java`, `structures/StructureInfo.java` (data map and codec), `structures/test/PlacementTests.java`.
8. **Residents** (1 d). `data/lotr_revival/middle-earth/structure_manager_data/gondor_watchtower.json` and the other default pools for the factions in the region table, nest lang entries, `structures/test/PoolTests.java`, `structures/test/ResidentTests.java`, `structures/test/TemplateTests.java`.
9. **Venue index and API** (2 d). `structures/venue/{VenueKind,Venue,VenueQuery,StructureVenues,VenueIndex,VenueIngest,ChunkProbe}.java`, `structures/VenueCommands.java`, `structures/test/VenueTests.java`; the hand-written `structure_info` entries for Middle-earth's structures.
10. **Credits in game** (1 d). `structures/credits/{Credit,CreditAnnouncer,CreditsCommand}.java`, the `credits_muted` attachment, `structures/test/CreditTests.java`; the `NOTICE` exception sentence and the `neoforge.mods.toml` description line.
11. **Bridges and site rules for water** (1 d). River-crossing check, rotation choice, `_crossings` sets; a second placeholder `placeholder_bridge` (own work); tests.
12. **First licensed builds** (1-2 d per batch, after the maintainer approves the candidates). Manifests, any new palettes (`orc`, `ruin`, `rohan`, ...), nests via the authoring flow, `build`, `docs`, a playtest.
13. **Logging pass** (0.5 d). DBG lines and the 600-tick summary for placement, venues and credits; the split-logs check that `logs/structures.log` appears.

## Open questions

1. **NC builds:** should we accept exactly CC BY-NC-SA 4.0, which matches our `ASSETS_LICENSE`? This spec says no, for future relicensing and platform monetisation. Does the maintainer plan to opt in to CurseForge or Modrinth payouts?
2. **Output licence:** keep the source family (this spec), or relicense CC0 and CC BY outputs under `ASSETS_LICENSE` so most assets share one licence?
3. **Film-like and landmark builds:** are builds that closely follow the films' designs (Meduseld, Minas Tirith), or canon landmarks (Weathertop, Amon Hen), allowed? The builder's CC licence does not clear third-party rights. Middle-earth may also build those places itself. This spec prefers generic builds and needs maintainer sign-off for landmarks.
4. **Courtesy contact:** should we tell or ask authors before importing, even when the licence allows it?
5. **Credits on entry:** is one chat line per build per session too chatty? The alternatives are only the command, or a lectern or sign in the build itself.
6. **Keeping source files** (up to 1 MiB) in the repo makes builds reproducible. It also means redistributing the unmodified originals, which the licences allow with attribution. Keep this, or store only the URL, archive and hash?
7. **Middle-earth coordination:** is the structure manager and nest data format (registry, JSON fields, block entity keys) stable enough to depend on? It is internal, though Middle-earth has contract tests for it. Would Jukoz accept our builds in Middle-earth's village pools later, or does Middle-earth plan its own outposts and bridges that ours would duplicate?
8. **Density defaults** (24 / 10 chunks per region, exclusion 3 chunks) are guesses against the nemesis target of at least 2 venues within 256 blocks. Tune after the first playtest, ideally before many worlds lock them.
9. **Respawn delay:** 3 days (as Middle-earth), or shorter for outposts that events use often?
10. **Rivers:** does Middle-earth's terrain have rivers narrow enough for bridges of 48 blocks or less, often enough for the crossing check to pass?
11. **Umbar, Moria and Goblin Town:** when do they get regions (coast builds, underground placement)?
