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
| Height | 30 to 34 px | 16 px = 1 block; hitbox is 0.6 x 1.8 blocks (`specs/npc/npc-base.md`), 1.8 blocks = 28.8 px, so heads may top the hitbox slightly like the player model |
| Build | slim to stocky | Seed picks shoulder width, hip width, limb thickness and head size within the race table's ranges |
| Head parts | 3 head shapes, 4 noses, 3 brows, 2 ears | Faces (eyes, brows, mouth) are pixel stamps written as text grids in our code |
| Hair | about 6 styles | Includes bald and long |
| Beard | about 5 styles | Includes none |
| Clothes | tunic (belted), trousers, boots | One shape each in the PoC; variety comes from proportions and colours |
| Cubes per model | 40 to 80 | Orc-pack detail level |
| Texture | 128 x 128, one sheet per model, embedded | About one texel per model pixel; flat 3 to 4 shade ramps, light from above, pattern brushes for cloth, stitching, leather and soles; no noise or dithering |
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

## Out of scope

- Elves, dwarves and the other races (later race tables on the same parts system).
- Gear: helmets, armour, weapons, capes.
- **Per-faction looks for men** (maintainer wish, next step after the PoC): faction palettes that recolour the slots, plus faction-specific clothing parts (for example Rohan, Gondor, Dale, Bree, Harad), so men differ in what they wear, not only in colour.
- GeckoLib / PulseLib export (rendering-animation 5a export step).
- Protecting hand edits from rebuilds.

## Acceptance tests

- [ ] `node tools/model-gen/build.mjs man --seeds 1-8` succeeds and writes 8 models, 8 low-detail copies, a contact sheet and a report.
- [ ] Every model has exactly the 5b bone names; anchor bones have no cubes.
- [ ] Seal and buried-face checks pass on every model and low-detail copy.
- [ ] Every model has 40 to 80 cubes; every low-detail copy has at most 60% of its model's cubes.
- [ ] Two builds give byte-identical files.
- [ ] Each model retargets the CMU walk (`82_09`) and punch (`02_05`) with every retarget feature on, with no error and feet no more than 1 px under the floor.
- [ ] The 8 models load in the viewer with the Man race picked and its library playing (browser check).
- [ ] Playtest (maintainer): the contact sheet reads as 8 different men in the same art style as the orc packs.

## Tickets

Filled in by the lead once the spec is approved.
