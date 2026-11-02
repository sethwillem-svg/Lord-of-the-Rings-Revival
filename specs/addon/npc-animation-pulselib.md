# NPC animation on PulseLib's runtime (Middle-earth add-on)

- **Status:** draft for review (2026-11-02).
- **Decisions (maintainer):**
  - Rebuild the NPC animation side the PulseLib way.
  - Weapon-class clips come from real motion capture only (UAL and CMU), no stylised or generated clips.
  - Weapon grips are tuned against real Middle-earth exports, which Claude runs in its own container.
- **Replaces:** our own clip player (`client.anim.NpcAnimator`, baked `clips/*.json`) and the shape-guessed grips (`client.render.WeaponGrip`).
- **Keeps:**
  - The PulseLib mesh renderer for Middle-earth's skin, armour and items.
  - The GPU bend deformers.
  - Server hit timing (`combat.AttackTiming`).
  - Race posture and variants.
- **References:** PulseLib docs: animatables and controllers, animation graphs, animation events, mesh deformers, model data.

## Why

The hand-rolled player has hard switches between clips, no blending, a weapon placed by guessing from the item's shape, and attack clips squeezed to fit the attack rhythm. PulseLib already provides each of these:

- **Animation graphs:** blend spaces for idle, walk and run, with one-shot overlays for attacks.
- **Random clip pools:** weighted, with no immediate repeats.
- **Animation events:** a hit frame, sound or particle at a time in a clip.
- **Skeleton evaluation:** of a GLB rig with weapon bones.

Weapons then sit where the animation puts them in every frame.

## Design

### 1. The rig: `assets/lotr_revival/glmodels/entity/npc_biped.glb` (our own asset)

- **Bones:** a skeleton only, no mesh, at vanilla biped proportions (px, y down):
  - `root`, `body` (chest), `waist`, `head`
  - `right_arm` → `right_forearm` → `right_hand` → `weapon_r`
  - `left_arm` → `left_forearm` → `left_hand` → `weapon_l`
  - `right_leg` → `right_shin`, `left_leg` → `left_shin`
- **Pivots:** where Minecraft's parts pivot (shoulders at ±5, 2; hips at ±1.9, 12). Elbow and knee bones sit at the middle of the existing bend zones.
- **Weapon bones:** `weapon_r` and `weapon_l` hold the grip point. A weapon's +y axis runs from the grip toward the tip, and +z is the blade's flat side.
- **One GLB per race** (`npc_biped_man.glb` … `npc_biped_orc.glb`): the same skeleton with that race's clips, so the race postures and preferences stay baked in.

### 2. Clips: baked from real motion capture into the GLB

- **The exporter:** `tools/anim-viewer/bake_glb.js` turns the existing retarget into glTF animations on those bones. It reuses bake_vanilla.js's arm hinge, waist, knee and posture logic, rotations only, plus root height.
- **Bends:** elbow, knee and waist bends become rotations of the forearm, shin and waist bones. The renderer reads them back as deformer angles, so the bends stay smooth.
- **Weapon tracks:** each clip gets a `weapon_r` / `weapon_l` track from the retarget's weapon orientation for its class:
  - Sword: the blade continues the forearm, as the retarget already does.
  - Spear: along the forearm, through the fist.
  - Bow: vertical in the off hand.
- **Clips per class**, real mocap only (UAL and CMU, all in our library). A class without enough takes falls back to the sword set and says so in the viewer.

  | Class | Clips |
  | --- | --- |
  | Sword | UAL sword set (current) |
  | Longsword | CMU 124_07 two-handed heavy swing, CMU 64_01 low sweep, UAL Sword_Heavy_Combo cuts |
  | Axe | CMU 62_10 hammer blow, UAL TreeChopping swing |
  | Spear | CMU 144_11 / 144_17 lunges (footwork) with the spear held level (the weapon track does the thrust; the arms follow the lunge), CMU 124_08 level-shaft guard |
  | Dagger | CMU 143_23 / 144_20 punches as stabs, UAL Punch_Jab |
  | Bow | CMU 79_86 draw and release (already in the library) |
  | Crossbow | CMU 79_96 / 80_03 shooting |

- **Hit frames:** each attack's hit time is written as an `animation_parameter` event, `attack_hit`, in the GLB's sidecar `glmodels/entity/npc_biped_<race>.events.json`. The server reads the same sidecar for `combat.AttackTiming`, so client and server use one number.

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
- **Bends:** the elbow, knee and waist deformer angles are the forearm, shin and waist bones' rotations.
- **Held items:** drawn on `weapon_r` / `weapon_l`. Each class has a grip in `assets/lotr_revival/weapon_grips.json`:
  - how far up the item the hand holds it (share of length);
  - a roll about the grip;
  - the class length (dagger 0.6, sword 1.0, longsword 1.4, axe 1.1, spear 2.5, bow 1.1, longbow 1.6, crossbow 0.9 blocks).

  The item's own long axis is aligned with the bone, so no per-item numbers are needed.
- **Unchanged:** vanilla and Middle-earth's renderer stay stiff, as agreed.

### 5. Viewer

`tools/anim-viewer/npc.html` loads the same GLBs (three.js GLTFLoader) and plays them on the test figure or an exported Middle-earth NPC. Weapon grips are applied from `weapon_grips.json`, so what the viewer shows is what the game draws.

### 6. Tuning against real Middle-earth items

Claude runs `./addon/bench.sh export` in its container (Xvfb, no Spark) to get every NPC type with its gear. It then tunes `weapon_grips.json` and the weapon tracks against real items in the viewer before handing anything over.

## Out of scope

- Generated or stylised clips.
- First-person.
- Hired-unit formation behaviour (hiring spec).
- Making vanilla or Middle-earth's renderer bend.

## Acceptance

- [ ] Every class shows its weapon at its length, held at its grip, in idle, walk and every attack, on 10 sampled real Middle-earth NPCs (screenshots from the container export).
- [ ] Idle, walk and run blend without pops. An attack overlays the legs' locomotion.
- [ ] The hit frame from the sidecar matches the moment the weapon reaches the target in the clip, on both sides.
- [ ] The viewer and game show the same pose for the same clip and time.
- [ ] Frame time with 200 NPCs is no worse than today's pulse renderer by more than 10%.
