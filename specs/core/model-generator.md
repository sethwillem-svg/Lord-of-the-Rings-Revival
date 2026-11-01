# NPC model generator (proof of concept: base men)

- **Pod:** core
- **Lead:** Opus 1
- **Status:** draft
- **Depends on:** `specs/core/rendering-animation.md` section 5b (biped bone names; revision 4 on `core/spec-race-rigs`), `tools/anim-viewer/` (viewer, retargeter, clip library)

## Summary

A script that builds NPC Blockbench models from code instead of hand-written JSON. A model is a race body (proportions picked within ranges) plus named parts (head, nose, hair, beard, clothes) that place cubes on the spec's biped bones and paint their own pixels in named colour slots. One seed gives one reproducible variant. This proof of concept builds **base men only**: unarmoured templates with skin, hair or beard and simple clothes, at the detail level of the existing orc packs. It proves the parts system before elves, dwarves, gear and faction looks are added. Players get NPC men that vary from one to the next instead of clones.

## Player-facing behavior

None in game yet. The output is source models under `src/main/art/blockbench/` (never loaded by the game, like the orc packs). The maintainer sees:

1. `node tools/model-gen/build.mjs man --seeds 1-8` writes 8 base men, each with a low-detail copy, a preview contact sheet and a quality report.
2. The models open in Blockbench and in the mocap rig viewer, where the Man clip library (UAL set and the 10 CMU uniques) plays on them.
3. `--watch` rebuilds on every save into `clips/`, which the viewer watches, so a change to a part shows on the models within seconds.

## Rules and numbers

| Thing | Value | Notes |
| --- | --- | --- |
| Bones | Exactly the biped list in rendering-animation 5b | `root > body > torso > head > head_armor`, arms to `*_hand > *_item`, `*_arm_armor`, `chest_armor`, `cape`, legs with `*_shin`, `*_leg_armor`, `*_boot_armor` |
| Anchor bones | No cubes | `*_armor`, `*_item`, `cape` (cape stays empty in the PoC) |
| Height | 32 px, every man | Standard body since 2026-11-01 (maintainer): legs 12, torso 11, head 9 (8 px skull + 1 px collar gap), shoulders 8, depth 4, arms 12 long and 3 thick, legs 3 thick. Armour fits every man; variety comes from faces, hair, beards, colours and outfits |
| Build | one standard build | The race table keeps [min, max] ranges so other races can vary; men use equal bounds |
| Head parts | 3 head shapes (painted: plain, hollow cheeks, heavy jaw), 4 noses, 3 brows, 2 ears | Faces (eyes, brows, mouth) are pixel stamps written as text grids in our code; one skull size |
| Hair | about 6 styles | Includes bald and long |
| Beard | about 5 styles | Includes none |
| Clothes | tunic (belted), trousers, boots | One shape each in the PoC; variety comes from proportions and colours |
| Cubes per model | 36 to 80 | Orc-pack detail level; lowered from 40 (2026-11-01) when the shoulder pads and fingers were removed at the maintainer's request |
| Texture | 128 x 128, one sheet per model, embedded | About one texel per model pixel; flat 3 to 4 shade ramps, light from above, pattern brushes for cloth, stitching, leather and soles; no noise or dithering, except a palette's grime (mud and smudges, deterministic per pixel) |
| Colour slots | `skin`, `hair`, `eye`, `tunic`, `tunic_trim`, `trousers`, `boots`, `belt`, `metal` | Each slot is a shade ramp. Skin, hair and eye come from the race's ranges by seed; clothes from `palettes/base.json` |
| Low-detail copy | about half the cubes | Drops cubes parts mark as detail (nose, ears, buckle, boot cuffs, beard strands), merges the beard into one block, same texture |
| Committed seeds | 8 | Chosen to cover the range: slim and stocky, short and tall, bald, long-haired, bearded, clean-shaven |
| Determinism | byte-identical rebuilds | Same seed and code give the same files on any machine |

## Edge cases

- **Joint gaps:** two cubes that only touch open a slit when a limb rotates. The toolkit's seal check fails the build below its threshold.
- **Buried cubes:** a cube fully inside another (an eye under a cheek) fails the build.
- **Seed out of range:** any seed is valid; proportions are always clamped to the race table.
- **Hand edits:** a model polished by hand in Blockbench is overwritten by a rebuild. The PoC does not protect hand edits; a later override layer will.
- **Animation fit:** the mocap retargeter must play clips on every model without errors and with feet no more than 1 px under the floor.

## Data and APIs

- **Vendored toolkit:** [Blockbench Models Toolkit](https://github.com/dlkhwshabwlfdl-web/Blockbench-Models-Toolkit-) (MIT), copied to `tools/model-gen/vendor/bbmt/` at commit `749793d8660aa035ab48b22b607fd78d8f0729f1` with its licence and a `VENDORED.md` saying what was reviewed (no install scripts, no network or process calls, one runtime dependency: `pngjs`). Its build, 35 smoke checks and 121 unit tests passed on 2026-11-01. We use its rig, cube, atlas, palette, `.bbmodel` export, seal and buried-face checks and headless renderer. Updating it is a reviewed copy, never a live dependency.
- **Our code** (`tools/model-gen/`):
  - `races/man.js`: proportion ranges and skin, hair and eye colour ranges.
  - `parts/*.js`: one module per part kind; a part takes the body and a seeded random source, adds named cubes to spec bones, marks its detail cubes, and paints its faces in colour slots.
  - `palettes/base.json`: slot name to shade ramp for the clothes.
  - `build.mjs`: CLI (`<race> --seeds A-B [--watch] [--out DIR]`), writes models, low-detail copies, contact sheet and report.
  - `test/`: the acceptance checks below, run with `node --test`.
- **Output:** `src/main/art/blockbench/man/man_base_<seed>.bbmodel`, `src/main/art/blockbench/man/lod/man_base_<seed>_lod.bbmodel`, `tools/model-gen/out/man_contact.png` (git-ignored except the committed preview `src/main/art/blockbench/man/preview.png`).
- **Viewer:** no changes expected. `rigs.js` already maps `spec-biped`, and `library.js` picks the Man race from the file name.
- Build tooling lives in `tools/` only. No change to `build.gradle`, game code or resources.

## Gear: weapons and armour for men (added 2026-11-01)

Source models and a viewer preview only; in-game items come later. Built by `node tools/model-gen/build.mjs gear`.

**Weapon convention** (replaces the orc packs' habit of modelling each weapon in place on one unit, slanted and
offset, which cannot be reused on another body; matches how PulseLib attaches a model: the attachment's pivot
is moved onto the anchor bone, then an offset, rotation and scale from data are applied):

| Rule | Value |
| --- | --- |
| File | one `.bbmodel` per weapon in `src/main/art/blockbench/gear/weapons/`, one root bone `weapon`, nothing else at the root |
| Pivot | `weapon` pivot at `[0, 0, 0]` = the middle of the closed fist on the grip |
| Axes | +Y runs along the blade or shaft toward the striking end; -Z is the cutting edge or striking face (axe bit, blade edge); flats face +-X. Shields: face toward -Z, boss centred on the grip. Bows: limbs along +-Y, string on the +Z (archer) side, arrows fly -Z |
| Scale | model pixels, as the bodies (16 px = 1 block). Real lengths at 1 px = about 5.6 cm (a 1.8 m man is 32 px) |
| Sizes | longsword 20 px, Rohan sword 18, scimitar 16, spear 40 (grip a third up from the butt), hand axe 11, great axe 22, club 14, javelin 30, longbow 32, short bow 22, Haradrim bow 24, round shield 12-13 across, tall shield 21 high |
| Hand | main-hand weapons attach to `right_item`; shields and bows to `left_item` |
| Texture | own 64 x 64 sheet, colour slots from the faction palette (metal, dark metal, wood, leather, cloth, trim, emblem, string) |
| Index | `gear/index.json` lists every item: id, name, hand, kind, factions, length, grip and tip points; the viewer reads it, and in-game display transforms will be derived from it |

**Open question for the maintainer:** rendering-animation 5b puts `*_boot_armor` under `*_leg` (the thigh), so
greaves on it would not bend with the knee. The viewer attaches boot armour to `*_shin` for now; the rig spec
should probably move `*_boot_armor` under `*_shin`. Forearm armour (vambraces) has no anchor in 5b; generated sets
use `*_forearm_armor` groups that the viewer attaches to `*_forearm`. A `*_forearm_armor` anchor under `*_forearm`
would make that official.

**Fitted armour** (Gondor first): a fitted set is built on one man, every piece measured from the cubes it covers
(helm: skull, hair, ears; breastplate: tunic; plates: sleeves, forearms, thighs, skirt halves, shins, boots) and
placed half a pixel outside them in width and depth (heights 1 px, as heights are whole pixels). `build.mjs gear`
writes one fitted file per committed man (`gear/armour/gondor/<man model>.bbmodel`); the viewer uses it when it
exists and falls back to the generic set built on the largest man.

**Armour style** (Gondor first, 2026-11-01): shells that follow the body (helm crown, sides and back around an open
face; breastplate; limb plates) instead of loose boxes; layered lames; `plate` shading lit from the top-left with
dark bottom and right edges and rivets; `rings` for mail; black lames rimmed in silver; a gold accent belt. Detail
comes from the texture, shape from the cubes. Armour is painted from material recipes (`tools/model-gen/materials/*.json`:
tone bands, polish streak, bevel, rim, raised border, rivets, scratches, engraving, lames), previewed with
`build.mjs materials [--watch]`, the armour texturing tool.

**Armour convention:** one `.bbmodel` per faction in `gear/armour/`, groups named after the anchor bones
(`head_armor`, `chest_armor`, `*_arm_armor`, `*_leg_armor`, `*_boot_armor`, `cape`); each group's pivot is that
anchor's pivot on the race's largest body, and the viewer moves each group onto the loaded man's anchor.
Pieces are sized to the largest body of the race, so at rest no main body cube (tunic, sleeves, thighs, skirt,
shins, boots) pokes through the piece that covers it; small detail cubes may be covered.

| Faction | Weapons | Armour |
| --- | --- | --- |
| Rohan | Rohan sword, spear, short bow, round shield (white horse on green) | mail, leather pauldrons and greaves, nasal helm with a horsehair crest |
| Gondor | longsword, spear, longbow, tall shield (White Tree) | mail under a black surcoat, steel pauldrons and greaves, high-crowned helm with cheek guards and white sea-bird wings |
| Harad | scimitar, red-tipped spear, Haradrim bow, round spiked shield (yellow and black) | red and gold scale corselet, open spiked helm |
| Dunland | hand axe, great axe, club, javelin, hide shield | hide jerkin, leather cap |
| Dale | longsword, spear, longbow | studded brigandine, kettle hat |
| Dunedain | longsword, longbow | leather jerkin, grey hooded cloak |

## Out of scope

- Elves, dwarves and the other races (later race tables on the same parts system).
- Gear for other races, in-game items and recipes for the gear.
- **Faction clothing parts for men** (maintainer wish): faction-specific cuts and pieces, so men differ in what they wear, not only in colour. Faction palettes are done: rohan, dale, dunedain, gondor, dunland, harad (2026-11-01; Rohan, Gondor and Harad kempt, Dunland dirty), with outfits that repaint (patterns, emblems, sleeves, tall boots, leg wraps, war paint) and weight the existing hair and beard styles, without growing past the base silhouette.
- GeckoLib / PulseLib export (rendering-animation 5a export step).
- Protecting hand edits from rebuilds.

## Acceptance tests

- [ ] `node tools/model-gen/build.mjs man --seeds 1-8` succeeds and writes 8 models, 8 low-detail copies, a contact sheet and a report.
- [ ] Every model has exactly the 5b bone names; anchor bones have no cubes.
- [ ] Seal and buried-face checks pass on every model and low-detail copy.
- [ ] Every model has 36 to 80 cubes; every low-detail copy has at most 60% of its model's cubes.
- [ ] Two builds give byte-identical files.
- [ ] Each model retargets the CMU walk (`82_09`) and punch (`02_05`) with every retarget feature on, with no error and feet no more than 1 px under the floor.
- [ ] The 8 models load in the viewer with the Man race picked and its library playing (browser check).
- [ ] Playtest (maintainer): the contact sheet reads as 8 different men in the same art style as the orc packs.

## Tickets

Filled in by the lead once the spec is approved.
