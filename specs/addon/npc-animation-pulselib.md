# NPC animation on PulseLib's runtime (Middle-earth add-on)

- **Status:** draft for review (2026-11-02).
- **Decisions (maintainer):**
  - Rebuild the NPC animation side the PulseLib way.
  - Clips may come from any licence-compatible source (CC0, CC-BY, MIT, Apache, LGPL; not GPL or All Rights Reserved), hand-animated or mocap (2026-11-02, replacing "real mocap only").
  - Weapon grips are tuned in the viewer against the real Middle-earth export Claude already made; the maintainer checks in game on their machine (no more game runs in the container, 2026-11-02).
- **PulseLib version:** 1.21.1-1.2.0 (newest for 1.21.1). Its registration API is a `@PulsePlugin(CLIENT)` class implementing `IPulseClientPlugin` (`registerModels(PModelRegistrar)`), not the `PulseLibEvents` of the online docs; the GeckoLib loader is added with `PModelCache.registerModelLoader(PGeckoModelLoader.INSTANCE)`.
- **Replaces:** our own clip player (`client.anim.NpcAnimator`, baked `clips/*.json`) and the shape-guessed grips (`client.render.WeaponGrip`).
- **Keeps:**
  - The PulseLib mesh renderer for Middle-earth's skin, armour and items.
  - The GPU bend deformers.
  - Server hit timing (`combat.AttackTiming`).
  - Race posture and variants.
- **References:** PulseLib docs: animatables and controllers, animation graphs, animation events, mesh deformers, model data, model loaders (Gecko loader).

## Why

The hand-rolled player has hard switches between clips, no blending, a weapon placed by guessing from the item's shape, and attack clips squeezed to fit the attack rhythm. PulseLib already provides each of these:

- **Animation graphs:** blend spaces for idle, walk and run, with one-shot overlays for attacks.
- **Random clip pools:** weighted, with no immediate repeats.
- **Animation events:** a hit frame, sound or particle at a time in a clip.
- **Skeleton evaluation:** of a rig with weapon bones.

Weapons then sit where the animation puts them in every frame.

## Design

### 1. The rig: a Blockbench / GeckoLib model of our own

- **Format:** Gecko (Blockbench's GeckoLib format), read by PulseLib's `PGeckoModelLoader`: `assets/lotr_revival/geckolib/models/entity/npc_biped_<race>.geo.json`, with the clips in `geckolib/animations/entity/npc_biped_<race>.animation.json`.
- **Why Gecko:** both open and edit in Blockbench (with the GeckoLib plugin), so a clip that looks wrong can be fixed by hand, not only by code. They are plain JSON our tools write and the viewer reads. Gecko sound and particle keyframes carry over as PulseLib events.
- **Source:** `tools/anim-viewer/gecko/rig.js` writes the `.geo.json` (it opens in Blockbench for hand edits). The loader is registered with `PModelCache.registerModelLoader(PGeckoModelLoader.INSTANCE)` in client setup.

- **Bones:** a skeleton only, no mesh, at vanilla biped proportions (px, y down), laid out like AnimationsPlus (section 2) so its clips map bone for bone:
  - `root` → `upper` (chest and everything above the waist) and `lower` (hips)
  - `upper` → `head`, `body`, `right_arm` → `right_elbow` → `weapon_r`, `left_arm` → `left_elbow` → `weapon_l`
  - `lower` → `right_leg` → `right_knee`, `left_leg` → `left_knee`
- **Pivots:** AnimationsPlus' own (Blockbench, y up, feet at 0; its x is mirrored from Minecraft's model space): upper and lower at y 12, head and body at 24, arms at (±4.5, 22.5), elbows at (±6, 18), item pivots at (±6, 12, -2), legs at (±1.9, 12), knees at (±1.9, 6). These match the existing elbow and knee bend zones, so clips play as authored. The waist is `upper` turning about the hips over `lower`: the body mesh turns with `upper`, so no waist deformer is needed.
- **Weapon bones:** `weapon_r` and `weapon_l` sit where Minecraft's held-item layer puts the item in the fist; a clip's keyframes on them are small per-clip nudges (turn and lift), the way AnimationsPlus' item pivots and Serious Player Animations' `rightItem` work.
- **One model per race** (`npc_biped_man` … `npc_biped_orc`): the same skeleton with that race's clips, so the race postures and preferences stay baked in.

### 2. Clips: from licence-compatible animation sets, converted to Gecko animation JSON

- **Converters:** `tools/anim-viewer/import/` (`animationsplus.js`, `seriousplayer.js`, `emotecraft.js`, `mobends.js`, and `mocap.js` over the existing retarget), combined by `build_npc.js`, all write Gecko animations on the rig's bones. Each source's licence and credit goes in `assets/lotr_revival/geckolib/animations/CREDITS.md`.
- **Sources, verified:**

  | Source | Licence | Used for |
  | --- | --- | --- |
  | AnimationsPlus (emart126, Blockbench, 142 clips on a biped with waist, elbows, knees, item pivots) | MIT | main set: sword swings 1-4, spear thrusts 1-3, bow draw and shoot, crossbow load and shoot, block, combat-ready, bash, uppercut, idles 1-3, walk, sprint, crouch, climb, swim, horse sitting and moving, taunts |
  | Mo' Bends (forearm/foreleg clip JSON) | MIT | sword combo (slash down, inward, outward, up, whirl), riding, stances |
  | Serious Player Animations | MIT | axe and tool swings, shield, bow idle, horse attack, running, idle |
  | Emotecraft-emotes (KosmX) | CC0 | social clips: bow (greeting), sit, lean, look around, gestures; trademarked dance recreations excluded |
  | Mesh2Motion | CC0 | bow pull, hold and release; taunts (insult, victory, angry) |
  | UAL, CMU (already in the library) | CC0, CMU terms | fill-ins; 100STYLE (CC-BY 4.0) walk styles and ACCAD (CC-BY 3.0) later |

- **Bends:** clips that bend elbows, knees and the waist (AnimationsPlus, Mo' Bends, mocap) drive the bend deformers through those bones; clips without bends keep straight limbs.
- **Classes:** sword, axe (tool swings), spear (thrusts), bow, crossbow and shield from the table; dagger uses the quick sword swings and the assassin multi-hit; longsword uses the Mo' Bends combo and the warrior bash and uppercut. Left-handed play is the L clips (AnimationsPlus) or mirroring.
- **Hit frames:** each attack's hit time goes in `geckolib/animations/entity/npc_biped_<race>.hits.json` (`{"<animation>": { length, hit, fadeIn, fadeOut, clip, style, missesBar?, lead?, apex, tame, ready }}`: times in s; see `specs/addon/npc-attack-tempo.md` Data and APIs), written by the same tool that writes the animation file. The server reads it for `combat.AttackTiming` and picks the clip, so client and server use one number and one clip list.

### 3. Runtime: a PulseLib animatable per NPC

- **Wrapper:** `client.anim.NpcAnimatable` implements `PAnimatable<NpcAnimatable>`, one per `NpcEntity` (weak map, instance manager). Middle-earth's entity class stays untouched.
- **Controllers:**

  | Controller | Drives |
  | --- | --- |
  | `locomotion` (graph) | `BlendSpace1D` on `speed` (idle, walk, run), with stance states (guard, shield brace, spear brace, march) and ranged states (bow aim, crossbow aim and reload). Idles use `thenRandomLoop` over the race's idle variants. The walk is picked per NPC as now. |
  | `action` (graph overlay) | `OneShotOverlay` attacks, triggered by `NpcAttackPayload` with the clip name; `hurt`; bow release. |

- **Speed:** clips keep their authored speed, except attacks the server must fit into the attack rhythm (as now, only when longer).

### 4. Renderer

- **Pose:** `PulseNpcRenderer` gets the NPC's pose from `PBakedModel.evaluate(controllers, …)` and builds bone world matrices.
- **Skin and armour:** draws Middle-earth's meshes (as now) on `head`, `body`, the arms and the legs.
- **Bends:** the elbow and knee deformer angles are the `*_elbow` and `*_knee` bones' rotations; the waist is `upper` turning, so no waist deformer.
- **Held items:** drawn where Minecraft's held-item layer puts them (Middle-earth's models already seat the handle in the fist), then moved by the `weapon_r` / `weapon_l` bone's per-clip nudge. Each class is scaled to its length (dagger 0.6, sword 1.0, longsword 1.4, axe 1.1, spear 2.5, bow 1.1, longbow 1.6, crossbow 0.9 blocks) about the fist point, so the handle stays in the hand. No shape-guessed recentring. Per-class extras (a spear's level carry, the brace stances) live in `assets/lotr_revival/weapon_grips.json`.
- **Bow aim:** the arms follow where the NPC looks (pitch), added on top of the bow clip, as Serious Player Animations does.
- **Other renderers:** vanilla and Middle-earth's renderer get the head, body, arm and leg rotations through the existing model mixin, without bends, as agreed.

### 5. Viewer

`tools/anim-viewer/npc.html` loads the same Gecko animation JSON and plays it on the test figure or an exported Middle-earth NPC. Weapon grips are applied from `weapon_grips.json`, so what the viewer shows is what the game draws.

### 6. Tuning against real Middle-earth items

Claude tunes `weapon_grips.json` and the weapon tracks in the viewer against the real Middle-earth export it already made (95 NPCs, in `addon/run/viewer_export`, not committed), then the maintainer checks in game (`./addon/bench.sh play`) and re-exports on their machine when needed.

## Out of scope

- Text-to-motion generated clips.
- First-person.
- Hired-unit formation behaviour (hiring spec).
- Making vanilla or Middle-earth's renderer bend.

## Acceptance

- [ ] Every class shows its weapon at its length, held at its grip, in idle, walk and every attack, on 10 sampled real Middle-earth NPCs (viewer screenshots from the existing export; the maintainer confirms in game).
- [ ] Idle, walk and run blend without pops. An attack overlays the legs' locomotion.
- [ ] The hit frame from `hits.json` matches the moment the weapon reaches the target in the clip, on both sides.
- [ ] The viewer and game show the same pose for the same clip and time.
- [ ] Frame time with 200 NPCs is no worse than today's pulse renderer by more than 10%.
