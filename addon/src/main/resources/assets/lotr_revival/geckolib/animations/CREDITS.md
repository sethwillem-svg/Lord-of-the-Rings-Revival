# NPC animation credits

The NPC clips (`entity/npc_biped_<race>.animation.json`) are converted from these sets by `tools/anim-viewer/build_npc.js`.
Each MIT source's licence ships next to this file in `licenses/<source>.txt`; every source's licence is with its data in
`tools/anim-viewer/library/<source>/LICENSE.txt`.

| Source | Author | Licence | Used for |
| --- | --- | --- | --- |
| [AnimationsPlus](https://github.com/emart126/AnimationsPlus) | Eric Martinez (emart126) | MIT | idles, walk, run, crouch, swim, climb, riding, all attacks, bow, crossbow, hurt, taunts; stances: `blockL` (shield brace), `Spear_Swing_2R` held (spear guard's legs), `idle` (spear march's arms) |
| [Mo' Bends](https://github.com/mobends/MoBends) | Iwo Plaza | MIT | stances: the combat stance's legs (`stance_const`, `stance_arms`; guard) |
| [Serious Player Animations](https://github.com/McVader34/Serious-Player-Animations) | McVader | MIT | stances: `walking` (the march's arms), `swimming` held (the spear over the shield) |
| [Emotecraft-emotes](https://github.com/KosmX/Emotecraft-emotes) | KosmX and contributors | CC0 1.0 | imported for the viewer and later slots (not in the race files yet) |
| [Universal Animation Library 1 and 2](https://quaternius.com) | Quaternius | CC0 1.0 | stances: `Sword_Idle` (guard), `Idle_Loop` (guard legs; the body of the bow guard, spear guard and spear brace, their arms set by bow_arms.js; every held stance's breathing), `Crouch_Idle_Loop` and `Shield_Dash` held (spear brace legs) |
| [KayKit character animations](https://kaylousberg.com) | Kay Lousberg | CC0 1.0 | stances: `Ranged_Bow_Idle` (bow guard's legs) |
| [CMU Graphics Lab Motion Capture Database](http://mocap.cs.cmu.edu) (BVH conversion by Bruce Hahne) | Carnegie Mellon University | no restrictions; "The data used in this project was obtained from mocap.cs.cmu.edu. The database was created with funding from NSF EIA-0196217." | stances: takes 124_08 (spear guard legs), 144_17 (brace legs), 79_45 (the spear over the shield), 132_37, 142_11 and 21_06 (march legs and the bow's carry) |

The stance picks per race are in `tools/anim-viewer/npc_slots.js` (STANCE_PICKS).
