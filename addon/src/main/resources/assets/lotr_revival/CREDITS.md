# Asset credits (lotr_revival)

Made for this repo, no samples, recordings, textures or models from anywhere else:

- `sounds/combat/*.ogg`: synthesized from sines, seeded noise and envelopes by `tools/sfx/make_combat_sfx.sh` (ffmpeg).
- `textures/combat/*.png`, `textures/particle/parry_spark_*.png`, `textures/particle/heavy_warn_*.png`: drawn from
  geometry by `tools/sfx/make_combat_textures.py`.

NPC animation clips: see `geckolib/animations/licenses/` (AnimationsPlus, Mo' Bends, Serious Player Animations: MIT;
Emotecraft: CC0). The combat reaction clips (`stagger`, `parry_blade`, `parry_shield`) are hand-keyed in
`tools/anim-viewer/build_npc.js`, starting from AnimationsPlus guard poses.
