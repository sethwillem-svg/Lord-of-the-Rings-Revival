/* The NPC rig: a skeleton only, in Blockbench space (y up, feet at 0, x mirrored from Minecraft's model space), with
 * AnimationsPlus' pivots and hierarchy (arms under body, which sword swings twist) so its clips map bone for bone. Parents come before children. */
(function (root) {
  'use strict';
  const RIG = [
    { name: 'root', parent: null, pivot: [0, 16, 0] },
    { name: 'upper', parent: 'root', pivot: [0, 12, 0] },
    { name: 'lower', parent: 'root', pivot: [0, 12, 0] },
    { name: 'head', parent: 'upper', pivot: [0, 24, 0] },
    { name: 'body', parent: 'upper', pivot: [0, 24, 0] },
    { name: 'right_arm', parent: 'body', pivot: [4.5, 22.5, 0] },
    { name: 'right_elbow', parent: 'right_arm', pivot: [6, 18, 0] },
    { name: 'weapon_r', parent: 'right_elbow', pivot: [6, 12, -2] },
    { name: 'left_arm', parent: 'body', pivot: [-4.5, 22.5, 0] },
    { name: 'left_elbow', parent: 'left_arm', pivot: [-6, 18, 0] },
    { name: 'weapon_l', parent: 'left_elbow', pivot: [-6, 12, -2] },
    { name: 'right_leg', parent: 'lower', pivot: [1.9, 12, 0] },
    { name: 'right_knee', parent: 'right_leg', pivot: [1.9, 6, 0] },
    { name: 'left_leg', parent: 'lower', pivot: [-1.9, 12, 0] },
    { name: 'left_knee', parent: 'left_leg', pivot: [-1.9, 6, 0] },
  ].map((b) => (b.parent ? b : { name: b.name, pivot: b.pivot }));
  const api = { RIG, BONES: RIG.map((b) => b.name) };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.GeckoRig = api;
})(typeof window !== 'undefined' ? window : globalThis);
