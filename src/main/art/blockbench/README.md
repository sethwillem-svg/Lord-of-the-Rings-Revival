# Blockbench source models

The orc, olog and troll faction packs: 104 units in eight faction folders, each with a low-detail copy under
`<faction>/lod/`. Textures are embedded in each `.bbmodel`. They use the `muster-pack` bone layout (`root > body`,
legs under `root`, `right_weapon` under `right_hand`), so the mocap rig viewer in `tools/anim-viewer/` loads them
directly. Planned use: PulseLib models for the Middle-earth add-on.
