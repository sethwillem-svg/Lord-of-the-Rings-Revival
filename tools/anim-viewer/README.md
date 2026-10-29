# Mocap Rig Viewer

A browser page that retargets mocap clips (BVH) onto a Blockbench rig live and plays several at once, one model copy per clip, on a shared timeline. Use it to compare takes, tune retarget settings and spot weapons or hands going through the floor before anything is exported.

## Use

1. Open `index.html` in a browser (double-click works; it loads three.js r128 from cdnjs).
2. **Load model**: a `.bbmodel`. The rig map is picked from the bone names (see below).
3. **Add BVH clips**: one or more `.bvh` files, or drag files onto the stage. Five clips show five models.
4. Scrub the timeline, press **Hit** to jump to a clip's contact frame, or tick **Align hits** to line every clip up on its contact frame.
5. Change the retarget settings; every clip is re-mapped immediately.
6. Export a clip as **Viewer JSON** (the Orc Muster Viewer's animation format) or **PulseLib JSON** (Gecko-style animation file).

Clip labels turn red while the weapon tip or the right hand is more than half a pixel below the floor. The clip list shows each clip's lowest hand, weapon tip and foot height in model pixels (16 px = 1 block).

## Input

- **BVH**: Kimodo SOMA skeleton (the 78-joint BVH from the Kimodo Hugging Face Spaces), Y up, centimetres. Joint names used: `Hips`, `Spine2`, `LeftArm`/`RightArm`, `LeftForeArm`, `LeftHand`, `LeftLeg`, `LeftShin`, `LeftFoot`, `LeftToeBase`, `Head`, `HeadEnd`, `LeftEye` and the right-side equivalents.
- **Model**: any `.bbmodel` whose bones fit a rig map in `rigs.js`. Embedded textures are used; models with external textures render grey.

## Rig maps (`rigs.js`)

A rig map names the bones the mocap drives: root, upper body, optional pelvis and head, two-bone arms and legs, and an optional weapon bone. Two maps ship:

| Map | Bones | For |
| --- | --- | --- |
| `spec-humanoid` | `root > body > torso`, legs under `body`, `right_item` | The rig names in `specs/core/rendering-animation.md` section 5b |
| `muster-pack` | `root > body`, legs under `root`, `right_weapon` | The orc, olog and troll faction-pack models |

Add a skeleton (for example a dwarf or troll rig with other bone names) by adding an entry. No code change is needed.

## How the retarget works

Directions, not positions: rig proportions are far from human (orc arms are about 1.6 times a human's relative to leg length), so copying joint positions would tear limbs.

- The heading is fixed from frame 0, so turning during the clip is kept.
- The upper body follows the shoulder line and spine; the head follows the eyes and head axis.
- Arms and legs are hinge chains: the upper bone points along the mocap segment and its twist is set by the elbow or knee bend plane, so a raised arm does not flip when it points straight up. Near-straight limbs fall back to the body's side axis.
- The root moves with the hips (scaled by leg length) and is lifted so no foot goes below the floor.
- **Weapon**: "along forearm" points the blade along the forearm; "rig rest grip" keeps the model's own grip angle.

Settings: **Torso tilt cap** limits how far the upper body can lean from vertical (deep bows in the mocap fold blocky torsos flat), **Root drop** scales how far the hips sink, **Mirror** swaps left and right.

## Known gaps

- **No weapon IK yet.** Nothing pins the weapon tip or hands to a target, so long weapons on long-armed rigs go through the floor at contact. The QA numbers make this visible.
- Hands keep their rest rotation; fingers are not mapped.
- PulseLib export follows the conversion notes from the earlier pipeline work (rotation = XYZ Euler of `q_pose * q_rest^-1`, linear keys, root position in pixels). It has not been checked in game.

## Python reference

`tools/anim/retarget3.py` (with `tools/anim/bvh.py`) is the reference implementation for the muster-pack rig, and `retarget.js` is a port of it. Check that they still agree after changing either:

```sh
python3 tools/anim/parity.py clip.bvh model.bbmodel
```

It needs node, numpy and scipy, and exits non-zero if any bone differs by more than 0.01 degrees.
