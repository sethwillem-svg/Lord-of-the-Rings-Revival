# NPC Animation on PulseLib Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Middle-earth NPCs are animated by PulseLib's runtime on a GeckoLib rig of our own, with licence-compatible hand-made clip sets, blended locomotion, attack overlays, per-class weapons placed where Minecraft seats them, and one hit time shared by client and server.

**Architecture:**
- **Offline tools** (Node, `tools/anim-viewer/`): import clip sets (AnimationsPlus, Serious Player Animations, Emotecraft, Mo' Bends, mocap) onto one rig. Write a `.geo.json` rig and one `.animation.json` plus a `.hits.json` per race.
- **In game:** a PulseLib plugin loads them. A per-NPC `PAnimatable` wrapper runs the graphs. A pose provider hands bone transforms to our existing PulseLib mesh renderer (skin, armour, items, bend deformers) and to Minecraft's model parts for the other two renderers.

**Tech Stack:**
- **Tools:** Node 22 (`node --test`), three.js r128 (viewer).
- **Game:** Java 21, NeoForge 21.1.244, PulseLib `1.21.1-1.2.0` (GeckoLib loader, animation graphs), the Middle-earth jar (compileOnly).

**Spec:** `specs/addon/npc-animation-pulselib.md`

## Global Constraints

- **Licences:**
  - Clip sources must be CC0, CC-BY, MIT, Apache or LGPL. Never GPL or All Rights Reserved, and nothing from the original LOTR mod or Middle-earth.
  - Every imported source keeps its licence file next to its data in `tools/anim-viewer/library/<source>/LICENSE.txt`.
  - A line per source goes in `addon/src/main/resources/assets/lotr_revival/geckolib/animations/CREDITS.md`.
- **PulseLib 1.2.0 API:**
  - Registration is a `@PulsePlugin(PulsePlugin.Side.CLIENT)` class implementing `IPulseClientPlugin` (`getPluginId()`, `registerModels(PModelRegistrar)`).
  - The GeckoLib loader is added with `PModelCache.registerModelLoader(PGeckoModelLoader.INSTANCE)`. There is no `PulseLibEvents` in this version.
- **Asset paths:**
  - Models: `assets/lotr_revival/geckolib/models/entity/npc_biped_<race>.geo.json`.
  - Clips: `assets/lotr_revival/geckolib/animations/entity/npc_biped_<race>.animation.json`.
  - Hit times: `.../animations/entity/npc_biped_<race>.hits.json`.
  - Races: `man`, `elf`, `dwarf`, `hobbit`, `orc`.
- **Rig bones** (exact names): `root`, `upper`, `lower`, `head`, `body`, `right_arm`, `right_elbow`, `weapon_r`, `left_arm`, `left_elbow`, `weapon_l`, `right_leg`, `right_knee`, `left_leg`, `left_knee`.
- **Weapon lengths, in blocks:** dagger 0.6, sword 1.0, longsword 1.4, axe 1.1, spear 2.5, bow 1.1, longbow 1.6, crossbow 0.9. Scaled about the fist point; no shape-guessed recentring.
- **Who runs the game:** no game runs in the container. Game behaviour is verified by `./gradlew build -PmeJar=$ME -PnoSpark` here and by the maintainer in game. Visual tuning uses the viewer and the existing export in `addon/run/viewer_export`.
- **Code style:** Java 21, 4-space indent, no wildcard imports. Commits look like `feat(addon): …` and end with the session's attribution lines. Push to `core/anim-viewer`, and cherry-pick onto `main` so both have identical trees.
- **The Middle-earth jar here:** `ME=/tmp/claude-0/-home-user-lord-of-the-rings-revival/c42c9528-ff28-5f34-8634-a3341f53121d/scratchpad/me/middle-earth/build/libs/Middle-earth-1.0.2-1.21.1-beta-backport.1.jar`.

## Review Focus

1. **A race lacks a clip for a slot** (for example no hobbit spear thrust). Expect it to fall back to the man's clip for that slot, never to play nothing or a T-pose. Test in Task 4.
2. **Server and client disagree on clip names**, for example the server picks an attack the client's animation file doesn't have. Expect every name in `hits.json` to exist in the same race's animation file. Test in Task 4. The client falls back to the class's first attack (Task 8).
3. **Off-hand and dual wielding** (thieves with two daggers, a shield in the left hand). Expect left-hand actions to use the L clips or a mirrored clip, with the item on `weapon_l`. Test in Task 2 (L/R swap) and Task 5 (`weapon_l` placement).
4. **Riders and passengers.** Expect seated riding clips, never walking legs. Test in Task 4 (riding slots exist); the slot choice is in Task 6.
5. **Resource reload** (F3+T) while NPCs are on screen. Expect no crash and no stale bones: the baked model is fetched through `PModelData.getModel()` every frame, never cached across reloads (Task 6).

---

### Task 1: Rig and GeckoLib writers

**Files:**
- Create:
  - `tools/anim-viewer/gecko/rig.js`
  - `tools/anim-viewer/gecko/write.js`
- Test: `tools/anim-viewer/test/gecko.test.js`

**Interfaces:**
- Produces:
  - `RIG` (rig.js), an array of `{ name, parent, pivot: [x, y, z] }`. Blockbench space (y up, feet at 0, x mirrored from Minecraft), in this order with these pivots:

    | Bones | Pivot |
    | --- | --- |
    | `root` | (0, 0, 0) |
    | `upper`, `lower` | (0, 12, 0) |
    | `head`, `body` | (0, 24, 0) |
    | `right_arm` / `left_arm` | (4.5 / -4.5, 22.5, 0) |
    | `right_elbow` / `left_elbow` | (±6, 18, 0) |
    | `weapon_r` / `weapon_l` | (±6, 12, -2) |
    | `right_leg` / `left_leg` | (1.9 / -1.9, 12, 0) |
    | `right_knee` / `left_knee` | (±1.9, 6, 0) |

    Parents: `upper`, `lower` → `root`; `head`, `body`, the arms → `upper`; the elbows → their arm; `weapon_*` → their elbow; the legs → `lower`; the knees → their leg. Right-side pivots have +x.
  - `geoJson(rig)` returns a `minecraft:geometry` 1.12.0 object (one geometry `geometry.npc_biped`, bones with `name`, `parent`, `pivot`, no cubes).
  - `animationJson(clips)` takes `{ name: Clip }` and returns `{ format_version: "1.8.0", animations: { name: { loop, animation_length, bones: { bone: { rotation|position: { "<t>": { vector: [x,y,z], lerp_mode? } } } } } } }`.
  - `Clip`, the shared clip shape used by every later task: `{ length: s, loop: bool, bones: { boneName: { rotation?: Key[], position?: Key[] } } }` with `Key = { t, v: [x,y,z], lerp: 'linear'|'catmullrom'|'step' }`, degrees and px, in Blockbench conventions.

- [ ] **Step 1: Write the failing tests**

```js
test('rig has the 15 bones with AnimationsPlus pivots', () => {
  assert.equal(RIG.length, 15);
  assert.deepEqual(RIG.find(b => b.name === 'right_elbow'), { name: 'right_elbow', parent: 'right_arm', pivot: [6, 18, 0] });
  assert.equal(RIG.find(b => b.name === 'left_knee').parent, 'left_leg');
});
test('geo.json lists bones without cubes', () => {
  const g = geoJson(RIG)['minecraft:geometry'][0];
  assert.equal(g.description.identifier, 'geometry.npc_biped');
  assert.ok(g.bones.every(b => !b.cubes));
});
test('animation json writes keys by time with lerp_mode', () => {
  const a = animationJson({ wave: { length: 1, loop: true, bones: { right_arm: { rotation: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }, { t: 0.5, v: [-90, 0, 0], lerp: 'catmullrom' }] } } } });
  assert.deepEqual(a.animations.wave.bones.right_arm.rotation['0.5'], { vector: [-90, 0, 0], lerp_mode: 'catmullrom' });
  assert.equal(a.animations.wave.animation_length, 1);
});
```

- [ ] **Step 2: Run them and see them fail.** Run `node --test tools/anim-viewer/test/`; expected failures because the modules are missing.
- [ ] **Step 3: Implement `rig.js` and `write.js`.** Both are UMD modules, like `bake_vanilla.js`, so the viewer can load them too. `lerp_mode` is omitted for `linear`.
- [ ] **Step 4: Run them.** Expected PASS.
- [ ] **Step 5: Commit**: `feat(anim-viewer): GeckoLib rig and writers for the PulseLib NPC rig`.

### Task 2: AnimationsPlus importer

**Files:**
- Create:
  - `tools/anim-viewer/library/animationsplus/model.bbmodel`, copied from github.com/emart126/AnimationsPlus at a pinned commit (record the hash in LICENSE.txt).
  - `tools/anim-viewer/library/animationsplus/LICENSE.txt`: the MIT text, "Copyright (c) 2024 Eric Martinez", and the source URL.
  - `tools/anim-viewer/import/animationsplus.js`
- Test: `tools/anim-viewer/test/animationsplus.test.js`

**Interfaces:**
- Consumes: `Clip` (Task 1).
- Produces:
  - `importAnimationsPlus(bbmodelJson)` returns `{ [clipName]: Clip }` for all 142 clips, on our bone names.
  - Bone map: `Upper`→`upper`, `Lower`→`lower`, `head`, `body`, `Arm_R`→`right_arm`, `Elbow_R`→`right_elbow`, `RightItemPivot`→`weapon_r`, `Arm_L`→`left_arm`, `Elbow_L`→`left_elbow`, `LeftItemPivot`→`weapon_l`, `Leg_R`→`right_leg`, `Knee_R`→`right_knee`, `Leg_L`→`left_leg`, `Knee_L`→`left_knee`. All other animators are dropped.
  - `mirror(clip)` swaps left and right bones and negates rotation y and z and position x, for left-hand play of clips with no L version.

- [ ] **Step 1: Write the failing tests**
  - `Sword_Swing_1R` has keys on `right_arm` and `right_elbow`, a `length` equal to the bbmodel's, and `loop === false`.
  - `walk` has `loop === true`.
  - The data points are numbers, parsed from the bbmodel's strings; any Molang value becomes 0 with a warning, and the test uses a fixture holding `"math.sin(q.anim_time)"`.
  - `mirror(importAnimationsPlus(m).Sword_Swing_1R)` equals `Sword_Swing_1L` on `left_arm` rotation to within 15° at t = 0.25 s. This is the check that the sign convention is right.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement.**
  - Read the `animations[].animators[*].keyframes`, using each keyframe's `channel`, `time`, `data_points[0]` and `interpolation`.
  - Keep Blockbench conventions. The rig shares Blockbench space, so the values pass through unchanged.
- [ ] **Step 4: Run them.** Expected PASS.
- [ ] **Step 5: Commit**: `feat(anim-viewer): import AnimationsPlus (MIT) onto the NPC rig`.

### Task 3: The other sources

**Files:**
- Create `tools/anim-viewer/import/`:
  - `seriousplayer.js`
  - `emotecraft.js`
  - `mobends.js`
  - `mocap.js`
- Create `tools/anim-viewer/library/`:
  - `seriousplayer/` (the clip JSONs used, plus LICENSE.txt: MIT, McVader34)
  - `emotecraft/` (chosen emotes, plus the CC0 LICENSE.txt; leave out the trademarked dance recreations)
  - `mobends/` (the `player/*.json` clips used, plus LICENSE.txt: MIT, Iwo Plaza)
  - `mesh2motion/` (`bow-pull.glb`, `bow-release.glb`, plus the CC0 licence text)
- Test: `tools/anim-viewer/test/sources.test.js`

**Interfaces:**
- Consumes: `Clip`; `bake_vanilla.js` (`bake`, `deform` joint angles); `ual_to_bvh.js` (GLB to BVH); `retarget.js`.
- Produces:
  - `importSeriousPlayer(json)`: Bedrock 1.8.0 JSON. `rightArm`→`right_arm`, `leftArm`→`left_arm`, `torso`→`upper`, `body`→`root`, `head`, `rightLeg`→`right_leg`, `leftLeg`→`left_leg`, `rightItem`→`weapon_r`.
  - `importEmote(json)`: Emotecraft JSON (tick keys, per-part axis values, bend keys). Each part becomes a bone. `bend`/`bendDirection` become the elbow or knee rotation.
  - `importMoBends(json)`: per-frame quaternions per bone, converted to Euler XYZ keys in degrees at each frame time.
  - `importMocap(bvhText, entry)`: through `retarget` and `bake_vanilla.bake`. Part rotations go on the matching bones; joint angles become the `*_elbow`/`*_knee` x rotation in degrees. `waist_*` becomes `upper` relative to `lower`.
  - Every importer returns `{ [name]: Clip }`.

- [ ] **Step 1: Write the failing tests:** one per importer, against a real file from its library folder.
  - `importSeriousPlayer(sword_attack).sword_attack.length === 0.875`, with keys on `weapon_r`.
  - An imported emote has bones only from the rig.
  - The Mo' Bends `slash_down` has keys on `right_elbow`.
  - The Mesh2Motion `bow-pull` imported through `importMocap` has a non-zero `left_elbow` key.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement the four importers.**
- [ ] **Step 4: Run them.** Expected PASS.
- [ ] **Step 5: Commit**: `feat(anim-viewer): import Serious Player Animations, Emotecraft, Mo' Bends and mocap onto the NPC rig`.

### Task 4: Race clip sets, hit times and the build

**Files:**
- Create:
  - `tools/anim-viewer/npc_slots.js` (the slot table)
  - `tools/anim-viewer/build_npc.js` (CLI)
  - `addon/src/main/resources/assets/lotr_revival/geckolib/animations/CREDITS.md`
  - generated: the 5 `.geo.json`, 5 `.animation.json` and 5 `.hits.json`
- Test: `tools/anim-viewer/test/build.test.js`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces:
  - `SLOTS`, keyed by animation name in the output files. Each value is `{ source: 'animationsplus'|'seriousplayer'|'emotecraft'|'mobends'|'mocap', clip, mirror?, races? }`.

    | Group | Animation name | Clip |
    | --- | --- | --- |
    | Movement | `idle` | AP `idle` |
    | | `idle_2`, `idle_3`, `idle_4` | AP `Idle_1`, `Idle_2`, `Idle_3` |
    | | `walk` | AP `walk` |
    | | `run` | AP `sprint` |
    | | `crouch_walk` | AP `crouchwalk` |
    | | `swim`, `climb` | AP `swim`, `climb` |
    | Riding | `ride_idle` | AP `sitHorse` |
    | | `ride_move` | AP `sitmoveHorse` |
    | Sword | `attack_sword_1` … `attack_sword_4` | AP `Sword_Swing_1R` … `Sword_Swing_4R` |
    | Longsword | `attack_longsword_1` … `_5` | Mo' Bends `slash_down`, `slash_inward`, `slash_outward`, `slash_up`, `slash_whirl` |
    | Spear | `attack_spear_1` … `_3` | AP `Spear_Swing_1R` … `3R` |
    | Dagger | `attack_dagger_1`, `attack_dagger_2` | AP `Assassin_MultiHitR`, `Sword_Swing_2R` |
    | Axe | `attack_axe_1`, `attack_axe_2` | AP `axe_1R`, `axe_2R` |
    | Shield | `attack_shield_1` | AP `Warrior_bashR` |
    | Unarmed | `attack_unarmed_1`, `attack_unarmed_2` | AP `attackR`, `attackL` |
    | Bow | `bow_aim` | AP `bowR` |
    | | `bow_release` | AP `Bow_ShootR` |
    | Crossbow | `crossbow_reload` | AP `loadR` |
    | | `crossbow_aim` | AP `crossR` |
    | | `crossbow_shoot` | AP `Cross_ShootR` |
    | Stances | `guard` | AP `combatReady` |
    | | `shield_brace` | AP `blockL` |
    | | `spear_brace` | AP `Spear_Swing_1R` held at its first frame |
    | | `march` | AP `walk` |
    | Other | `hurt` | AP `Fall_0` (short) |
    | | `taunt_1` … `taunt_6` | AP `Taunt_0` … `Taunt_5` |

    Races may override a slot (`races: { orc: {...} }`).
  - `buildRace(race, sources)` returns `{ geo, animations, hits }`.
    - **Race posture:** added as constant extra rotation on every key, the same numbers as today's `library.js` styles:
      - orc: hunch 24 on `upper` x; arms out 15 on the arms' z, signed per side; knee bend 18 on the knees' x.
      - dwarf: 9 / 11 / 15.
      - elf: -8 / -5 / -6.
      - hobbit: knee bend 9; stride 0.65 scales the legs' x rotation in `walk` and `run`.
    - **Hit times:** `hits[name] = { length, hit }` for every `attack_*`, at the fastest motion of `weapon_r`'s world position between 15% and 80% of the clip (the existing `hitTime` logic, on the rig).
  - CLI: `node tools/anim-viewer/build_npc.js` writes all 15 files and prints one line per race.

- [ ] **Step 1: Write the failing tests**

```js
for (const race of ['man', 'elf', 'dwarf', 'hobbit', 'orc']) test(race + ' has every slot', () => {
  const r = buildRace(race, loadSources());
  for (const slot of Object.keys(SLOTS)) assert.ok(r.animations.animations[slot], slot);   // falls back to the base table, never missing
});
test('every hit entry names an attack in the same file', () => {
  const r = buildRace('orc', loadSources());
  for (const name of Object.keys(r.hits)) { assert.ok(r.animations.animations[name]); assert.ok(r.hits[name].hit > 0 && r.hits[name].hit < r.hits[name].length); }
});
test('orc posture adds hunch to upper', () => {
  const man = buildRace('man', loadSources()), orc = buildRace('orc', loadSources());
  const x = (r) => r.animations.animations.idle.bones.upper?.rotation?.['0']?.vector?.[0] ?? 0;
  assert.ok(Math.abs(x(orc) - x(man) - 24) < 1e-6);
});
test('riding slots exist', () => { assert.ok(SLOTS.ride_idle && SLOTS.ride_move); });
```

- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement.** Write `CREDITS.md`, one line per source (name, author, licence, URL), and run the CLI.
- [ ] **Step 4: Run them, then check the CLI.** Run `node --test tools/anim-viewer/test/ && node tools/anim-viewer/build_npc.js`; expected: tests PASS and 5 lines like `man: 31 animations, 14 attacks`.
- [ ] **Step 5: Commit** the tools and the generated assets: `feat(addon): NPC rig and race clip sets in GeckoLib format with hit times`.

### Task 5: Viewer plays the GeckoLib clips with real weapons

**Files:**
- Create:
  - `tools/anim-viewer/gecko/sample.js`
  - `addon/src/main/resources/assets/lotr_revival/weapon_grips.json`
- Modify: `tools/anim-viewer/npc.html`
- Test: `tools/anim-viewer/test/sample.test.js`

**Interfaces:**
- Consumes: Task 4 outputs; the existing export in `addon/run/viewer_export`.
- Produces:
  - `samplePose(animation, t)` returns `{ bone: { rot: [x,y,z], pos: [x,y,z] } }`.
    - Interpolation: `linear`, `catmullrom` (uniform Catmull-Rom over the neighbouring keys) or `step`.
    - Wrapping: loops wrap and one-shots clamp.
  - `worldBones(pose)` returns `{ bone: Matrix4 }`, in Minecraft model space (px, y down, mirrored x). Rotation order is Z, Y, X, as GeckoLib applies it.
  - `weapon_grips.json`: `{ "<class>": { "length": blocks, "rotate": [x,y,z]?, "offset": [x,y,z]? } }` with the Global Constraints lengths. `spear` gets `rotate` for a level carry, tuned in this task.

- [ ] **Step 1: Write the failing tests**
  - `samplePose` at a key time returns the key value.
  - At a midpoint, linear returns the average.
  - A loop at `length + 0.1` equals the value at 0.1.
  - With `right_arm` rotated `[-90,0,0]`, `worldBones` places the right fist (pivot plus (0,-10,0) in the arm) in front of the body: world z < 0.
- [ ] **Step 2: Run them and see them fail.**
- [ ] **Step 3: Implement `sample.js` and switch `npc.html`.**
  - **Clips:** the clip list comes from the race's animation file.
  - **Mesh parts:** `head`/`hat` follow `head`, `body` follows `body`, the arms follow `*_arm`, the legs follow `*_leg`.
  - **Bends:** the elbow and knee angles are the `*_elbow`/`*_knee` x rotation, fed to the existing `V.deform`.
  - **Held items:** the mesh as exported (where Minecraft seats it) is scaled about the fist point to the class length from `weapon_grips.json`. The `weapon_*` bone's own rotation and offset are applied relative to its pivot, and then the class `rotate`/`offset`.
- [ ] **Step 4: Run them, then check in the viewer.** Run `node --test tools/anim-viewer/test/`; expected PASS. Then render npc_55 (Lórien sword), npc_18 (longsword), npc_10 (Dale spear), npc_21 (axe), npc_2 (two daggers), npc_4 (bow) and npc_39 (crossbow), in `idle`, `walk` and each attack, with the existing Playwright script. Tune `weapon_grips.json` until every weapon sits in the fist at its length.
- [ ] **Step 5: Commit**: `feat(anim-viewer): play the NPC rig's GeckoLib clips with weapons placed by class`.

### Task 6: PulseLib runtime in game

**Files:**
- Create in `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/client/anim/`:
  - `PulseClientPlugin.java`
  - `NpcAnimatable.java`
  - `NpcGraphs.java`
  - `NpcPose.java`
- Modify: `client/LotrRevivalClient.java` (tick)

**Interfaces:**
- Consumes: Task 4 assets.
- Produces:
  - `@PulsePlugin(PulsePlugin.Side.CLIENT) public final class PulseClientPlugin implements IPulseClientPlugin`:
    - `getPluginId()` returns `LotrRevival.id("npc")`;
    - `registerModels(PModelRegistrar r)` calls `r.model(MODELS.get(race))` for each race;
    - a static initializer calls `PModelCache.registerModelLoader(PGeckoModelLoader.INSTANCE)`.
  - `static final Map<String, PModelData> MODELS`, with `PModelData.entity(LotrRevival.id("npc_biped_" + race), PGeckoModelLoader.INSTANCE.id())`.
  - `NpcAnimatable implements PAnimatable<NpcAnimatable>`:
    - `static NpcAnimatable of(NpcEntity)` (weak map);
    - `getAnimationManager(key)` returns a `PLibHelper.createInstanceManager(this)` field;
    - `registerAnimationControllers` adds the `locomotion` and `action` graphs from `NpcGraphs`;
    - `void tick()`, called each client tick for NPCs in view. It sets `speed` (horizontal speed in blocks per tick), `riding`, `stance` (0 none, 1 guard, 2 shield brace, 3 spear brace, 4 march) and `ranged` (0 none, 1 bow, 2 crossbow aim, 3 crossbow reload).
    - `void attack(String animation, float speed)`: triggers the overlay.
  - `NpcGraphs`:
    - `locomotion`: a `BlendSpace1D("move", "speed", [0 → idle, 0.05 → walk, 0.2 → run])`, plus states `ride` (`ride_idle`/`ride_move` by `speed`), `stance_*` and `ranged_*`, with transitions on those parameters (0.15 s crossfade).
    - Idle: `idle` is a random loop over `idle`, `idle_2`, `idle_3`, `idle_4` (weights 6, 2, 2, 1, no immediate repeat).
    - `action`: a `OneShotOverlay` per `attack_*`, plus `hurt` and `bow_release`.
  - `NpcPose`: `static @Nullable NpcPose of(NpcEntity, float partialTick)`.
    - It evaluates `MODELS.get(race).getModel().evaluate(controllers, …)` every call, with no cached baked model, so reloads are safe.
    - Accessors: `Matrix4f part(String bone)` (model space, px), `float bend(String joint)` (radians, from `*_elbow`/`*_knee` x), `Matrix4f weapon(boolean left)`.

- [ ] **Step 1: Build:** `cd addon && ./gradlew build -PmeJar=$ME -PnoSpark`; expected BUILD SUCCESSFUL.
- [ ] **Step 2: Add a check for the plugin and models.** On the first client tick after loading, log `lotr_revival: NPC rig loaded for <races>, <n> animations each`. Hand the maintainer the steps: `./addon/bench.sh play`, then read that line in `addon/run/logs/latest.log`.
- [ ] **Step 3: Commit**: `feat(addon): PulseLib runtime for NPCs (GeckoLib rig, locomotion and action graphs, pose provider)`.

### Task 7: Renderers draw the PulseLib pose

**Files:**
- Modify:
  - `client/render/PulseNpcRenderer.java`
  - `mixin/HumanoidModelMixin.java`
  - `client/render/NpcJoints.java` (angles from `NpcPose.bend`)
- Replace: `client/render/WeaponGrip.java` with class scaling about the fist plus `weapon_grips.json`, keeping `HeldItems`.

**Interfaces:**
- Consumes: `NpcPose` (Task 6); `weapon_grips.json` (Task 5).

- [ ] **Step 1: Implement.**
  - **`PulseNpcRenderer`:** each part's matrix is the entity pose stack times `NpcPose.part(bone)`.
  - **Bend submissions:** from `NpcPose.bend`, for both elbows and both knees. The waist deformer is not submitted: `upper` carries the turn.
  - **Held items:** drawn at `NpcPose.weapon(left)` times the Minecraft hand frame times the class scale about the fist.
  - **Bows:** add pitch aim (the head pitch added to both arms' x while `ranged` is 1 or 2).
  - **`HumanoidModelMixin`:** when an NPC has a pose, copy the head, body, arm and leg rotations into the `ModelPart`s, so the vanilla and Middle-earth renderers animate too (no bends).
- [ ] **Step 2: Build.** Expected BUILD SUCCESSFUL.
- [ ] **Step 3: Hand to the maintainer:** `./addon/bench.sh play`, then `/lotrbench spawn 12 middle-earth:gondor.soldier true`. Check idle, walk and run blending; attacks over the legs; swords, spears and axes at their lengths in the fist; riders seated.
- [ ] **Step 4: Commit**: `feat(addon): NPC renderers draw the PulseLib pose; weapons by class about the fist`.

### Task 8: Server picks clips from hits.json

**Files:**
- Modify:
  - `combat/AttackClips.java` (read `npc_biped_<race>.hits.json`, names `attack_<class>_N`, same fallbacks)
  - `client/anim/ClientAttack.java` (calls `NpcAnimatable.of(npc).attack(name, speed)`; if that race's file lacks `name`, use the class's `_1`)

- [ ] **Step 1: Implement.** Keep `AttackTiming`'s rhythm fitting unchanged. `Clip(slot, length, hit)` now comes from `hits.json`.
- [ ] **Step 2: Build.** Expected BUILD SUCCESSFUL.
- [ ] **Step 3: Commit**: `feat(addon): attack clips and hit times from the rig's hits.json on both sides`.

### Task 9: Remove the old clip player

**Files:**
- Delete:
  - `client/anim/NpcClip.java`
  - the clip playback in `client/anim/NpcAnimator.java` (keep `raceOf`, `restPose`, `stanceOf`, `testStance`; move them to `NpcAnimatable` and delete the class if nothing else remains)
  - `addon/src/main/resources/assets/lotr_revival/clips/`
- Modify:
  - `tools/anim-viewer/bake_vanilla.js` (CLI no longer writes to the add-on; the library and mocap tools stay)
  - `tools/anim-viewer/README.md` (how to rebuild: `node tools/anim-viewer/build_npc.js`)

- [ ] **Step 1: Delete, then build:** `./gradlew build -PmeJar=$ME -PnoSpark`; expected BUILD SUCCESSFUL, with no references to `NpcClip`.
- [ ] **Step 2: Run the tool tests.** Run `node --test tools/anim-viewer/test/`; expected PASS.
- [ ] **Step 3: Tick the spec's acceptance boxes** that the viewer screenshots prove, and leave the in-game ones for the maintainer.
- [ ] **Step 4: Commit**: `refactor(addon): remove the old NPC clip player and baked clips`.
