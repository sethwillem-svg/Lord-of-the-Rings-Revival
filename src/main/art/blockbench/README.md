# Blockbench source models

Source projects for the orc, olog and troll faction packs: 104 units in eight faction folders, each with a low-detail copy under `<faction>/lod/`. Textures are embedded in each `.bbmodel`. These files are kept for editing and re-export and are never loaded by the game (`specs/core/rendering-animation.md` section 5d).

They use the `muster-pack` bone layout (`root > body`, legs under `root`, `right_weapon` under `right_hand`), so the mocap rig viewer in `tools/anim-viewer/` loads them directly.
