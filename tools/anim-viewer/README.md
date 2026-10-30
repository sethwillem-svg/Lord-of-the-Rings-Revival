# Mocap Rig Viewer

A browser page that retargets mocap clips (BVH) onto a Blockbench rig live and plays several at once, one model copy per clip, on a shared timeline. Use it to compare takes, tune retarget settings and spot weapons or hands going through the floor before anything is exported.

## Use

1. Open `index.html` in a browser (double-click works; it loads three.js r128 from cdnjs).
2. **Load model**: a `.bbmodel`. The rig map is picked from the bone names (see below).
3. **Add BVH clips**: one or more `.bvh` files, or drag files onto the stage. Five clips show five models.
4. Scrub the timeline, press **Hit** to jump to a clip's contact frame, or tick **Align hits** to line every clip up on its contact frame.
5. Change the retarget settings; every clip is re-mapped immediately.
6. Export a clip as **Viewer JSON** (the Orc Muster Viewer's animation format) or **PulseLib JSON** (Gecko-style animation file).

## Generate straight into the viewer

`kimodo_batch.py` generates clips on the Kimodo Hugging Face Space and saves the BVH files into a folder; the viewer's **Watch folder** picks each one up as it lands, so nothing has to be uploaded by hand.

1. Once: `pip install gradio_client`, and set `HF_TOKEN` to a Hugging Face token (ZeroGPU then uses your account's quota).
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

## Input

- **BVH**: Kimodo SOMA skeleton (the 78-joint BVH from the Kimodo Hugging Face Spaces), Y up, centimetres. Joint names used: `Hips`, `Spine2`, `LeftArm`/`RightArm`, `LeftForeArm`, `LeftHand`, `LeftLeg`, `LeftShin`, `LeftFoot`, `LeftToeBase`, `Head`, `HeadEnd`, `LeftEye` and the right-side equivalents.
- **Model**: any `.bbmodel` whose bones fit a rig map in `rigs.js`. Embedded textures are used; models with external textures render grey.

## Rig maps (`rigs.js`)

A rig map names the bones the mocap drives: root, upper body, optional pelvis and head, two-bone arms and legs, and an optional weapon bone. Two maps ship:

| Map | Bones | For |
| --- | --- | --- |
| `spec-biped` | `root > body > torso`, legs under `body`, `right_item` | The biped rigs in `specs/core/rendering-animation.md` section 5b (man, elf, dwarf, hobbit, orc, olog, half_troll) |
| `muster-pack` | `root > body`, legs under `root`, `right_weapon` | The orc, olog and troll faction-pack models |

Add a skeleton (for example a dwarf or troll rig with other bone names) by adding an entry. No code change is needed.

## How the retarget works

Directions, not positions: rig proportions are far from human (orc arms are about 1.6 times a human's relative to leg length), so copying joint positions would tear limbs.

- The heading is fixed from frame 0, so turning during the clip is kept.
- The upper body follows the shoulder line and spine; the head follows the eyes and head axis.
- Arms and legs are hinge chains: the upper bone points along the mocap segment and its twist is set by the elbow or knee bend plane, so a raised arm does not flip when it points straight up. Near-straight limbs fall back to the body's side axis.
- The root moves with the hips (scaled by leg length) and is lifted so no foot goes below the floor.
- **Weapon**: "along forearm" points the blade along the forearm; "rig rest grip" keeps the model's own grip angle.

Settings: **Torso tilt cap** limits how far the upper body can lean from vertical (deep bows in the mocap fold blocky torsos flat; 35 to 45 degrees suits overhead slams), **Root drop** scales how far the hips sink, **Mirror** swaps left and right.

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
