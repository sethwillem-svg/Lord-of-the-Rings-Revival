# Blockbench source models

Source projects for the orc, olog and troll faction packs: 104 units in eight faction folders, each with a low-detail copy under `<faction>/lod/`. Textures are embedded in each `.bbmodel`. These files are kept for editing and re-export and are never loaded by the game (`specs/core/rendering-animation.md` section 5d).

They use the `muster-pack` bone layout (`root > body`, legs under `root`, `right_weapon` under `right_hand`), so the mocap rig viewer in `tools/anim-viewer/` loads them directly.

## `man/` (generated)

Base men (no gear) built by `tools/model-gen` (`node tools/model-gen/build.mjs man`), on the spec biped rig
(`specs/core/rendering-animation.md` 5b), with low-detail copies in `man/lod/` and `preview.png`. Faction colour sets (same men, faction palettes) are in
`man/<faction>/` for rohan, dale, dunedain, gondor, dunland and harad (`build.mjs man --palette all`).

## `gear/` (generated)

Weapons (`gear/weapons/`), faction armour sets (`gear/armour/`) and `gear/index.json`, built by
`node tools/model-gen/build.mjs gear`. Weapons: root bone `weapon` pivoted at the grip, +Y to the striking end.
Armour: groups named after the anchor bones. See `specs/core/model-generator.md`, Gear. Do not
edit these by hand: a rebuild overwrites them. Change the race table or parts in `tools/model-gen/` instead.
