# Mocap Rig Viewer

A browser page that retargets mocap clips (BVH) onto a Blockbench rig live and plays several at once, one model copy per clip, on a shared timeline. Use it to compare takes, tune retarget settings and spot weapons or hands going through the floor before anything is exported.

## Use

1. Open `index.html` in a browser (double-click works; it loads three.js r128 from cdnjs).
2. **Load model**: a `.bbmodel`. The rig map is picked from the bone names (see below).
3. **Add BVH clips**: one or more `.bvh` files, or drag files onto the stage. Five clips show five models.
4. Scrub the timeline, press **Hit** to jump to a clip's contact frame, or tick **Align hits** to line every clip up on its contact frame.
5. Change the retarget settings; every clip is re-mapped immediately.
6. Export a clip as **Viewer JSON** (the Orc Muster Viewer's animation format) or **PulseLib JSON** (Gecko-style animation file).

## Quick start: `animview` and `animgen`

Needs Git, Python 3 and a browser. From the repo root, run once:

```sh
./tools/anim-viewer/view.sh
```

It asks once whether to add the `animview` and `animgen` commands to your shell (answer Y; `animview setup` adds them later if you said no), then opens the viewer in a new browser tab. After that, `animview` works from anywhere. (Not `view`: on most Linux systems that is vim's read-only mode. Older `view`/`gen` aliases from this tool are renamed on the next run.)

- `animview` makes `<repo>/clips` (git-ignored), starts a local web server for the repo if one is not running, and opens the viewer in your default browser (Firefox works) watching that folder with `mordor_orc` loaded. `animview gundabad_olog_hai` opens with another unit; `animview stop` stops the server.
- `animgen "A person ..." --count 5` generates into `<repo>/clips`, and each take appears in the viewer as it lands.
- The **Model** menu in the sidebar lists every unit in `src/main/art/blockbench/`.

Sidebar extras: **Smoothing** (Gaussian over time, removes jitter), **Weapon** grip plus tilt, swing and twist angles (weapon IK follows the adjusted blade), camera presets, toggles for IK targets and the floor grid, **Clear clips** (hides what is in the watched folder now; new takes still appear) and **Reload folder** (brings them back). Sidebar settings are remembered in the browser between visits. If the strike height is out of reach the target is raised to the lowest reachable point and the clip list says so.

## Clip library (Quaternius UAL, CMU mocap, your local clips)

`library/ual/` holds the 84 clips of Quaternius' Universal Animation Library 1 and 2 (CC0, see `library/ual/LICENSE.txt`) as BVH, and `library.js` says which clip plays each spec clip key for each race. In the sidebar's **Clip library**:

1. **Race** is picked from the model's file name (`*troll*` troll, `*olog*` olog, orc/uruk/goblin orc, else man); change it to preview another race's set on any model.
2. **Load race set** replaces the clips with that race's spec minimum set (from `prompts/<race>.txt`), sets the export rig name and the race's stance and torso tilt cap. Untick **Spec minimum set only** to load everything for the race: the extras (fidgets, more attack variants, work, swim, crossbow) and its unique CMU takes.
3. **Load uniques** replaces the clips with the race's CMU mocap takes (below). No take is shared between races.
4. **Load local** loads the race's clips from `library/local/` (your Unity Asset Store conversions, not in git).
5. **Add** puts one clip from the list next to what is loaded, for example to compare a library clip with generated takes.

The library needs the viewer served over http (`animview` does that). Exports use the spec key, so `orc.attack.sword.1` exports as `animation.orc.attack.sword.1`.

Each library clip shows its source and the settings it fixes. Library clips play in place with weapon IK off, except the smash clips, which keep IK to land the weapon on the ground. Entries can join clips (attack + recovery), cut a frame range out of a combo, slow a clip down for heavy bodies or mirror it. Change them in `library.js`.

| Race | Required clips covered | Not in the packs (use `animgen`) |
| --- | --- | --- |
| man | 20 of 28 | `emote.wave`, `emote.war_cry`, `emote.taunt`, `hold.pike.lowered`, `ranged.bow.*`, `ride.lance.couched` |
| elf, dwarf | 18 of 23 | `emote.wave`, `hold.pike.lowered`, `ranged.bow.*` |
| hobbit | 16 of 18 | `emote.wave`, `emote.shout` |
| orc | 19 of 25 | `emote.taunt`, `emote.war_cry`, `hold.pike.lowered`, `ranged.bow.*` |
| olog | 16 of 18 | `emote.taunt`, `emote.war_cry` |
| half_troll | 17 of 20 | `emote.wave`, `emote.war_cry`, `emote.taunt` |
| troll | 9 of 10 | `emote.war_cry` |

Race choices: elves and hobbits punch with the jab; elves lead with the quicker rising slash; hobbits farm by kneeling to plant; orcs get the lunging hook, the feint-and-chop combo and a slouched fidget; ologs move 10% slower and add the heavy overhead smash (cut from `Sword_Heavy_Combo`); half-trolls and trolls use the zombie idle and lumbering walk, 10% and 20% slower, and the troll swat is the zombie swipe. Stand-ins are marked in their notes: tree chopping for `work.mine` and `work.smith`, the chair sit for `pose.sit`, the driving loop for `ride.idle`, the mirrored spell cast for `emote.order`.

To convert the packs again (for example a newer release), download them from quaternius.com and run, for each pack:

```sh
node tools/anim-viewer/ual_to_bvh.js "Universal Animation Library[Standard]/Unreal-Godot/UAL1_Standard.glb" tools/anim-viewer/library/ual
```

### Unique clips per race (CMU Motion Capture Database)

`library/cmu/` holds 87 takes from the [CMU Graphics Lab Motion Capture Database](http://mocap.cs.cmu.edu) (BVH conversion by Bruce Hahne; no restrictions, see `library/cmu/LICENSE.txt`), 10 per race and 17 for men (extra attacks), each cut to its busiest 2 to 9 seconds at 30 fps. They are real actors, so they have weight and timing the UAL loops lack. Keys are spec-style names outside the minimum sets; heavy races play theirs slower (olog and half-troll 0.9, troll 0.8).

| Race | Unique clips (CMU take) |
| --- | --- |
| orc | heavyset walk (17_08), angry walk (17_01), gorilla prowl (120_02), boxing (13_17), punch combo (144_20), kicks (143_24), devil taunt (55_23), angry quarrel (18_10), sneaky walk (142_22), digging (79_04) |
| troll | bear walk (54_02), dancing bear taunt (55_12), zombie lumber (104_41), bear charge (79_45), tiger swipes (79_63), heavy box lift (79_25), get up face down (140_01), limp (77_19), bent walk (136_01), panther prowl (80_59) |
| olog | strong-man walk (137_42) and idle (137_41), flexing war cry (79_94), weight lifting (79_93), boxing (80_10), march (142_11), front kicks (144_05), zombie walk (120_22), arguing order (80_48), gorilla charge (80_49) |
| half_troll | drunk walk (137_16), clumsy walk (142_02), wild-arms walk (132_54), zombie march (20_08), boxing (14_01), punches (143_23), get up from the side (140_03), duck-footed walk (132_14), dodge (77_09), arm blocks (144_07) |
| man | marching (138_01), soldiers march (21_06), swordplay (02_07, 02_08, 02_09), wave / direct traffic (13_26), confident walk (82_09), ready stance (77_03), waiting (137_32), punch / strike (02_05), two-handed heavy swing (124_07), low sweep (64_01), back off, feint and attack (76_02), back off and punch (76_01), spear lunges (144_11), javelin throw (111_33), overhead strike (22_20) |
| elf | graceful walk (137_24), bow draw and shot (79_86), roundhouse kick (135_07), side kick (135_11), knife-hand block (135_10), lunge punch (135_09), low block (135_05), sneaking (139_29), wave (143_25), jumps, kicks and punches (86_01) |
| dwarf | chopping wood (79_01, 80_71), hammering (62_07, 62_08), digging a hole (80_05), marching (132_37), sweeping (80_19), punch combo (144_13), arm blocks (144_26), cheering (79_69) |
| hobbit | sneaking (139_31), quick little steps (136_31), planting (79_26), raking (79_87), crouched walk (136_09), creeping walk (77_29), scared (79_73), old walk (137_33), ducking (139_09), fishing (79_34) |

To change a pick: add the take to `TAKES` in `cmu_import.py` (window length and start, or `None` for the busiest window), run it, and point the race's `unique` entry in `library.js` at the take id. Take ids and descriptions are in the [mirror's index](https://github.com/Shriinivas/cmubvh).

```sh
python3 tools/anim-viewer/cmu_import.py            # imports any takes in TAKES that are missing
python3 tools/anim-viewer/cmu_import.py 17_08      # re-cut one take after changing its window
```

### Unity Asset Store and other packs you may not redistribute (`library/local/`)

Store licences usually let you use animations in a game but not share the files, and this repo is public, so these clips live in `library/local/`, which git ignores. Convert a pack's FBX files with Blender (or the `bpy` module, `pip install bpy`):

```sh
blender -b -P tools/anim-viewer/fbx_to_bvh.py -- ~/Downloads/SomePack --race orc
```

Each animation take becomes `library/local/orc.<take>.bvh` (Y up, joints renamed to the Mixamo names the retargeter reads; Mixamo, Unity humanoid, 3ds Max Biped and Unreal naming are understood). Rename a file to `orc.<clip key>.bvh` to give it a spec key; files without a race prefix show for every race. BVH files can go through the same script to rename their joints. Check each pack's licence before baking its clips into the mod: whether animations retargeted into a shipped open-source mod count as redistribution is the pack author's call.

## Generate straight into the viewer

`kimodo_batch.py` generates clips on the Kimodo Hugging Face Space and saves the BVH files into a folder; the viewer's **Watch folder** picks each one up as it lands, so nothing has to be uploaded by hand.

1. Nothing to install by hand: the first `animgen` offers to install `gradio_client` and asks once for a Hugging Face access token (type Read), which it saves in Hugging Face's own login store. Setting `HF_TOKEN` also works.
2. Copy a model from `src/main/art/blockbench/<faction>/` into an empty folder, say `clips/`.
3. Open the viewer watching `clips/`. The model loads by itself.
   - **Any browser (Firefox too):** in the repo root run `python3 -m http.server 8000`, then open `http://localhost:8000/tools/anim-viewer/?watch=/clips/`.
   - **Chrome or Edge:** you can also open `index.html` directly, press **Watch folder** and pick `clips/`.
4. Generate:
   ```sh
   python3 tools/anim-viewer/kimodo_batch.py --out clips --count 5 --duration 3 "A person swings both hands overhead and slams them down to the ground"
   ```
   Each take appears on its own model within about two seconds of being saved. `--prompts file.txt` runs one prompt per line; `--seeds 7 42` picks exact seeds.

The script finds the Space's generate endpoint itself (default `hugging-apps/kimodo-motion`, Kimodo-SOMA-RP-v1.2), writes `<prompt>_s<seed>.bvh` plus a `.json` with the prompt and settings, and writes through a `.part` file so the viewer never reads half a clip. A regenerated file replaces its old clip in the viewer.

Clip labels turn red while the weapon tip or the right hand is more than half a pixel below the floor. The clip list shows each clip's lowest hand, weapon tip and foot height in model pixels (16 px = 1 block).

## Clip lists per race

`prompts/<rig>.txt` holds every clip the spec's minimum sets need for that rig (`specs/core/rendering-animation.md` section 5c): 165 clips over man, elf, dwarf, hobbit, orc, olog, half_troll and troll, with a race movement style in each prompt. `prompts/CLIPS.md` is the readable list. Lines are `clip.key | seconds | prompt`, and takes are named after the spec clip (`orc.attack.sword.1_s2.bvh`).

```sh
animgen --prompts tools/anim-viewer/prompts/orc.txt --count 3              # every orc clip, 3 takes each
animgen --prompts tools/anim-viewer/prompts/orc.txt --only attack --dry-run # preview just the attacks
```

Edit the tables in `prompts/build_prompts.py` and run it to regenerate the files. Loop clips (idle, walk, run, march, holds, work) still need trimming to a seamless loop: neither model makes loops.

## Rented GPU (RunPod, Vast.ai)

On a fresh Ubuntu + CUDA + PyTorch machine (RunPod: expose HTTP port 8000):

```sh
git clone -b core/anim-viewer https://github.com/sethwillem-svg/Lord-of-the-Rings-Revival.git
cd Lord-of-the-Rings-Revival && bash tools/anim-viewer/cloud_setup.sh     # or: cloud_setup.sh kimodo | hymotion
```

It installs Kimodo (the `hugging-apps/kimodo-motion` app) and HY-Motion in separate Python environments, downloads the weights, adds `kimodo-up`, `kimodo-down` and `gen-hy`, and starts the viewer on all interfaces, printing the link to open from your own browser. Everything runs on the rented machine, so nothing has to be copied back; export good takes from the viewer. On a 32 GB card run one model at a time (`kimodo-down` before `gen-hy`); a 48 GB card fits both. Needs a Hugging Face token whose account has accepted the Meta Llama 3 licence (Kimodo's text encoder), and about 60 GB of disk. Tested here with stand-ins for the installs and downloads only.

## Local models: Kimodo and HY-Motion

`animgen` (`kimodo_batch.py`) has two backends; both write BVH into `clips/`, so the viewer shows their takes side by side. Each clip's `.json` records its `source` (`kimodo` or `hymotion`), model and prompt. They do not fit on one 32 GB GPU together, so run one at a time.

- **Kimodo, local:** run the Kimodo Space on your own GPU (its Hugging Face page, **Run locally**, gives a Docker command that serves it on `localhost:7860`), then `animgen --space http://localhost:7860 --count 5 "A person ..."`.
- **HY-Motion 1.0, local:** install [HY-Motion-1.0](https://github.com/Tencent-Hunyuan/HY-Motion-1.0) (its README: PyTorch, `git lfs pull`, `pip install -r requirements.txt`, weights into `ckpts/`), then
  ```sh
  animgen --backend hymotion --hy-repo ~/HY-Motion-1.0 --count 4 --duration 3 "A person raises the right arm and chops down"
  ```
  It runs HY-Motion's `local_infer.py` once for the whole batch (`--hy-python` if HY-Motion lives in another Python environment, `--hy-model HY-Motion-1.0-Lite` for the smaller model) and converts each sample as it appears. HY-Motion picks its own seeds; `--count` is samples per prompt. VRAM: about 26 GB (1.0) or 24 GB (Lite).
  `hymotion_to_bvh.py` does the conversion: HY-Motion saves SMPL-H rotations (`.npz`), and the converter writes the 22 body joints as BVH with the Kimodo joint names, using the skeleton template from the HY-Motion install, plus head-end and eye joints. Checked against SMPL forward kinematics on a synthetic clip (joint positions agree to 1e-5 cm); not yet run on real HY-Motion output.
  HY-Motion is under the Tencent HY-Motion 1.0 licence: not for use in the EU, UK or South Korea, and its outputs may not be used to train other models.

## Input

- **BVH**: the clip library's UAL clips (`ual_to_bvh.js` output), CMU clips (`LowerBack`/`Spine`/`Spine1` read as the spine, `Head_End` as the head top), FBX conversions from `fbx_to_bvh.py`, Kimodo SOMA skeleton (the 78-joint BVH from the Kimodo Hugging Face Spaces), HY-Motion output converted by `hymotion_to_bvh.py`, or a Mixamo-style skeleton (`mixamorig:` prefixes are ignored; `LeftUpLeg` is the thigh, and without eye joints the head takes its side axis from the shoulders), for example the BVH export of the [HY-Motion Mixamo Space](https://huggingface.co/spaces/Yosun/hy-motion-mixamo). Y up, centimetres. Joint names used: `Hips`, `Spine2`, `LeftArm`/`RightArm`, `LeftForeArm`, `LeftHand`, `LeftLeg`, `LeftShin`, `LeftFoot`, `LeftToeBase`, `Head`, `HeadEnd`, `LeftEye` and the right-side equivalents.
- **Model**: any `.bbmodel` whose bones fit a rig map in `rigs.js`. Generated base men from `tools/model-gen` (`src/main/art/blockbench/man/`) use the `spec-biped` map and load directly (`animview man_base_7`). Embedded textures are used; models with external textures render grey.

## Rig maps (`rigs.js`)

A rig map names the bones the mocap drives: root, upper body, optional pelvis and head, two-bone arms and legs, and an optional weapon bone. Two maps ship:

| Map | Bones | For |
| --- | --- | --- |
| `spec-biped` | `root > body > torso`, legs under `body`, `right_item` | The biped rigs in `specs/core/rendering-animation.md` section 5b (man, elf, dwarf, hobbit, orc, olog, half_troll) |
| `muster-pack` | `root > body`, legs under `root`, `right_weapon` (orcs) or `weapon` (ologs, trolls) | The orc, olog and troll faction-pack models |

Add a skeleton (for example a dwarf or troll rig with other bone names) by adding an entry. No code change is needed.

## How the retarget works

Directions, not positions: rig proportions are far from human (orc arms are about 1.6 times a human's relative to leg length), so copying joint positions would tear limbs.

- The heading is fixed from frame 0, so turning during the clip is kept.
- The upper body follows the shoulder line and spine; the head follows the eyes and head axis.
- Arms and legs are hinge chains: the upper bone points along the mocap segment and its twist is set by the elbow or knee bend plane, so a raised arm does not flip when it points straight up. Near-straight limbs fall back to the body's side axis.
- The root moves with the hips (scaled by leg length) and is lifted so no foot goes below the floor.
- **Weapon**: "along forearm" points the blade along the forearm; "rig rest grip" keeps the model's own grip angle.

Settings: **Torso tilt cap** limits how far the upper body can lean from vertical (deep bows in the mocap fold blocky torsos flat; 35 to 45 degrees suits overhead slams), **Root drop** scales how far the hips sink, **Mirror** swaps left and right, **Plant feet** sets the root height every frame so the lowest foot matches the mocap's (takes that start crouched no longer float), **BVH rotation order** auto-detects files whose exporter writes the rotation channels in the reverse of the header's order (the HY-Motion Mixamo Space does; the clip list shows which reading was used), **In place** drops forward and sideways travel (attacks usually play on the spot while the game moves the mob).

## Cube-rig refinements

These default on in the viewer (off in `retarget.js` defaults, so the Python parity check covers the base path):

- **Cube joint limits**: elbows and knees bend at most 140 degrees (box limbs clip when folded further); the upper arm cannot point across the body into the torso box, though it can reach further across in front; and each foot stays at least three quarters of the hip spacing out from the middle, because box boots touch as soon as the legs close in.
- **Torso share** (weapon IK): the upper body turns about its pivot, leaning and twisting together, to absorb part of the change in reach that the IK target asks for, so the arm keeps more of the mocap's extension instead of bending to fit. 40 percent by default, capped at 35 degrees.
- **Foot lock**: a foot counts as planted while the mocap ankle is near its floor and barely moving for at least three frames. The rig foot is pinned where it landed and the leg is solved to it, with a short blend in and out. If a tilted box boot dips a corner below the floor, that plant's pin is raised. A body travelling further than the leg reaches still slides, so use In place for attacks.
- **Stance width**: scales the leg spacing (each thigh turns about its parent's forward axis).
- **Wrist follows the mocap**: with finger joints in the BVH (Kimodo), the hand frame comes from the wrist and knuckles. The first frame counts as neutral, because skeletons disagree on hand axes, and later frames follow the change from it, limited by swing and twist to 60 degrees of bend and 90 of roll about the forearm (the cube forearm cannot roll, so forearm roll lands on the wrist). Without finger joints (Mixamo) the hand keeps its rest angle.
- **Edge first**: the weapon twists about its blade so the blade plane contains the tip's motion and the edge leads. The blade normal is the thinnest axis of the weapon's cubes; the twist is speed-weighted and smoothed, and never more than 90 degrees from the grip, so it never flips.

## Weapon IK

Mocap has no weapon, and rig arms are longer than human ones, so copying arm angles alone sends long weapons through the floor. Weapon IK fixes the contact:

1. **Markers** from the weapon tip's path (the right hand if the rig has no weapon bone): the hit H is the frame where the mocap hands are lowest, the windup A is the highest tip point before it, the contact hold lasts to C while the tip stays down, and the release ends at R once the arms have risen.
2. **Target**: a point at the **strike height** (0 = the ground), in front of the character in the swing's direction, at the **reach** share of arm plus weapon length from the shoulder. It is drawn as a red cube.
3. **Goal warp**: every frame the tip's goal is its own path plus w times the offset that would put it on the target at the hit. w rises with a minimum-jerk curve from A to H, holds at 1 until C, and eases back to 0 by R, so the swing keeps its timing and only bends toward the target. Goals never go below the floor.
4. **Solve**: the elbow hinge is set so the shoulder-to-tip distance matches the goal, then the whole arm swings about the shoulder to aim at it. This keeps the mocap's elbow direction. Only the right upper arm and forearm change.

On the Kimodo ground slam with the orc pack model the tip lands within 0.01 px of the target and stays above the floor for the whole clip, with no added jitter (the largest frame-to-frame change in tip velocity is unchanged).

## Known gaps

- Weapon IK moves the arm only. The torso and root do not take a share of the reach yet, and the off hand is not pinned to the weapon (fine for one-handed weapons and shields, not for two-handed grips).
- Weapon IK has no collision with the body: a blade can still cross the torso during the windup.
- Hands keep their rest rotation; fingers are not mapped.
- PulseLib export follows the conversion notes from the earlier pipeline work (rotation = XYZ Euler of `q_pose * q_rest^-1`, linear keys, root position in pixels). It has not been checked in game.

## Python reference

`tools/anim/retarget3.py` (with `tools/anim/bvh.py`) is the reference implementation for the muster-pack rig, and `retarget.js` is a port of it. Check that they still agree after changing either:

```sh
python3 tools/anim/parity.py clip.bvh model.bbmodel
```

It needs node, numpy and scipy, and exits non-zero if any bone differs by more than 0.01 degrees. Weapon IK exists only in the JS version, so the check runs with IK off.
