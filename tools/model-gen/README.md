# NPC model generator

Builds NPC Blockbench models from code: a race table picks a body by seed, parts add named cubes to the
spec biped bones (`specs/core/rendering-animation.md` 5b) and paint them in colour slots, and checks fail
the build on open joints, hidden cubes or wrong bones. Spec: `specs/core/model-generator.md`.
Proof of concept: **base men** (no gear).

## Setup (once)

```sh
cd tools/model-gen && npm install && cd ../..
```

Node 20 or newer. The only package is `pngjs`; the model toolkit is vendored in `vendor/bbmt/` (see its
`VENDORED.md`).

## Commands

```sh
node tools/model-gen/build.mjs man                        # the 8 showcase men into src/main/art/blockbench/man/ (+ lod/, preview.png)
node tools/model-gen/build.mjs man --seeds 1-40 --out /tmp/men   # browse more
node tools/model-gen/build.mjs man --palette rohan          # one faction into src/main/art/blockbench/man/rohan/
node tools/model-gen/build.mjs man --palette all            # every faction, one folder each
node tools/model-gen/build.mjs man --seeds 4,9 --out /tmp/men
node tools/model-gen/build.mjs man --seeds 1-8 --watch    # rebuilds into clips/ on every save
cd tools/model-gen && npm test                            # all checks
```

With `--watch`, open the viewer on a generated man (`animview man_base_7` opens a committed man and watches `clips/`): every save
under `lib/`, `parts/`, `races/` or `palettes/` rebuilds the models in `clips/`, and the viewer reloads the open model when its rebuilt file (same name) appears there.

The report (`tools/model-gen/out/man_<palette>_report.txt`) lists each seed's height, build, part styles and cube counts.
`showcase` in `races/man.mjs` lists the seeds committed to the repo, chosen from the report to cover the range.

## How it fits together

| File | Does |
| --- | --- |
| `races/man.mjs` | Body ranges (whole pixels) and skin, hair and eye colours |
| `lib/body.mjs` | Seed -> body sizes and the pivot of every spec bone (front -Z, character's right -X) |
| `parts/*.mjs` | Head (shape, face stamp, nose, ears, chin), hair, beard, clothes; each adds cubes with a slot and style |
| `lib/paint.mjs` | Box-UV packing (one texel per pixel) and painting from 4-shade slot ramps, patterns and stamps |
| `lib/build.mjs` | One model: body, parts, low-detail copy, UVs, paint, checks |
| `lib/qa.mjs` | Spec bones, empty anchors, sealed joints (1 px shared), no hidden cubes, 36-80 cubes |
| `lib/bbmodel.mjs` | Writes the Blockbench 4.10 layout the viewer and orc packs use |
| `palettes/*.json` | `base` plus one per faction (rohan, dale, dunedain, gondor, dunland, harad): clothes slot colours, optional skin/hair/eye choices that replace the race's, and `grime` (0 clean, 1 filthy: mud on boots and hems, smudges) |

**Adding a part:** export `STYLES` and `addX(asm, body, rng, style)` from a file in `parts/`, add cubes with
`asm.add({ name, bone, from, to, slot, style, detail, lodGroup, stamps })`, call it from `parts/index.mjs`
with its own `rng.fork('x')` (so other parts keep their picks). Sizes must be whole pixels; positions may be
half pixels. Mark small pieces `detail: true` so the low-detail copy drops them. Run `npm test`: the seal and
hidden-cube checks tell you which cube to move.

**Factions:** a palette recolours and sets an `outfit`, which only repaints or picks among existing styles, so
faction men never grow past the base silhouette armour is fitted to. The same seed keeps its body and clothes
cubes in every faction; hair and beards follow the faction's style weights.

| Outfit key | Values | Effect |
| --- | --- | --- |
| `pattern` | plain, stripes, plaid | tunic, skirt and sleeves painted with `tunic_alt` bands or checks |
| `emblem` | none, horse, tree, star, serpent | chest emblem in the `emblem` colour (star: a brooch high on the chest) |
| `sleeves` | short, long | forearms painted as skin or as sleeve |
| `boots` | low, tall | shins painted as trousers or boot leather |
| `legs` | trousers, wraps | criss-cross leg wraps on the shins |
| `face` | none, warpaint | cheek stripes and a brow mark in the `paint` colour |
| `hair`, `beard` | { style: weight } | how common each existing style is (0 = never) |

Rohan: white horse on green, long sleeves, riding boots, long and braided hair. Dale: striped teal. Dunedain:
silver star brooch, long hair, stubble. Gondor: White Tree on black, kempt short hair. Dunland: plaid, bare
forearms, leg wraps, wild hair and long beards. Harad: black serpent on crimson, war paint, braided hair. Rohan, Gondor and Harad are kempt (grime 0); Dale
is lightly lived-in, the Dunedain travel-worn and Dunland dirty. Add a faction by adding a palette file.

**Gear:** `node tools/model-gen/build.mjs gear` writes 16 weapons (`gear/weapons.mjs`), six faction armour sets
(`gear/armour.mjs`), `gear/index.json` and `gear/preview.png` into `src/main/art/blockbench/gear/`. Weapons follow
the spec's convention: root bone `weapon` pivoted at the grip, +Y to the striking end, -Z the edge, real-world
scale. Armour groups are the anchor bones. Gondor's set is fitted: built on each man from the cubes it covers, with
one file per committed man; the other sets are sized to the largest man; a test checks that no main body cube of 60
men pokes through at rest. The viewer's Gear panel shows them on any man.

## Armour texturing tool (materials)

Armour pieces are painted from material recipes in `materials/*.json` by `lib/surface.mjs`, which follows the
Minecraft texturing guides (Blockbench Minecraft style guide, Faithful 32x guidelines, metal-shading tutorials):

- **Hue-shifted ramps**: six tones from one colour, highlights warm and desaturated, shadows cool; greys take the
  tint fully, colours shift at most 20-25 degrees (gold darkens through amber, not purple). Wide contrast for metal.
- **Material from clusters, not bands or noise**: metal faces 6 px or taller reflect a light sky zone, a dark
  horizon band, a polish streak with one specular glint and a counter-reflection above the dark bottom edge;
  smaller faces get a simple lit-top, dark-bottom gradient; mail is offset rows of 2 px links (not a checkerboard).
- **Faces lit by direction** (entity rule): top brightest, front and the right side (light from the top-left)
  brighter than the back and left side, bottom darkest.
- Banding (`bands`), all-round bevels (pillow/pancake shading) and random scratches (noise) were removed.

Fields: `kind` (metal, mail, leather, cloth), `colour` (palette slot such as metal, cloth, trim, accent, leather,
or a hex colour), `polish` (0-1: x of the streak and glint, or null), `rim` (bottom edge colour), `border` (raised
band one pixel in), `rivets` (`{ "spacing": 2-6, "rows": "top" | "both" }`, silver on rimmed or bordered pieces),
`engrave` (scroll, filigree, fluting; one tone deep), `lames` (a soft seam every N rows; 4+ avoids stripes), `note`.

```sh
node tools/model-gen/build.mjs materials            # swatch sheet -> tools/model-gen/out/materials.png
node tools/model-gen/build.mjs materials --watch    # re-render on every save of a recipe
node tools/model-gen/build.mjs materials --materials ~/my-recipes --out /tmp/try.png   # try another set
node tools/model-gen/build.mjs gear                 # repaint the armour with the recipes
```

The sheet shows every recipe on a large plate and a thin lame (front and turned), four per row in file-name order,
then the Gondor armour on a man. A recipe with a problem fails with the field named. An armour piece picks its
recipe with `material` in `gear/armour.mjs`.

**Later:** elves and dwarves are new race tables; faction clothing parts (different cuts per faction) come
with the gear parts.
