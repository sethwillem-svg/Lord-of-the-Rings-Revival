/* Rig maps: which rig bones the mocap drives. One entry per skeleton; the viewer picks the first map whose
 * bones all exist in the loaded .bbmodel, or the one you choose. Add a skeleton by adding an entry here.
 *
 *   root   bone that takes the root translation (never rotated)
 *   upper  upper body (shoulders, chest); driven by the shoulder line and spine
 *   pelvis optional; driven by the hips. Legs hang from it when present
 *   head   optional
 *   arms   [upper arm, forearm, hand?] per side; legs [thigh, shin] per side
 *   weapon optional held-weapon bone under the right hand
 */
(function (root) {
  'use strict';
  const RIG_MAPS = [
    {
      id: 'spec-humanoid',
      note: 'Bone names from specs/core/rendering-animation.md section 5b (humanoid, orc, half_troll).',
      root: 'root', pelvis: 'body', upper: 'torso', head: 'head',
      arms: { right: ['right_arm', 'right_forearm', 'right_hand'], left: ['left_arm', 'left_forearm', 'left_hand'] },
      legs: { right: ['right_leg', 'right_shin'], left: ['left_leg', 'left_shin'] },
      weapon: 'right_item',
    },
    {
      id: 'muster-pack',
      note: 'Orc, olog and troll faction-pack models (body holds the upper torso; legs hang from root).',
      root: 'root', upper: 'body', head: 'head',
      arms: { right: ['right_arm', 'right_forearm', 'right_hand'], left: ['left_arm', 'left_forearm', 'left_hand'] },
      legs: { right: ['right_leg', 'right_shin'], left: ['left_leg', 'left_shin'] },
      weapon: 'right_weapon',
    },
  ];
  if (typeof module !== 'undefined' && module.exports) module.exports = RIG_MAPS;
  else root.RIG_MAPS = RIG_MAPS;
})(typeof window !== 'undefined' ? window : globalThis);
