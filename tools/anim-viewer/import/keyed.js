/* Hand-keyed combat reactions (specs/addon/combat-parry-warning.md, ticket 8): the clips the add-on's combat code plays that
 * no library set has. Keyed here, on the NPC rig's bones in Blockbench degrees (x < 0 raises an arm forward, upper x > 0
 * leans the body forward, right arm z > 0 swings it out), starting from AnimationsPlus' held guard poses (blockR, blockL).
 *   parry_blade   0.5 s  snap into the blade guard, a jolt back at the contact, hold, recover
 *   parry_shield  0.5 s  the shield punched up into the blow, a jolt, hold, recover
 *   stagger       1.0 s  thrown back by a parried swing: chest back, arms flung out, regained (legs are left to
 *                        the locomotion under it; the server slows the NPC while staggered)
 * All are overlays (one-shots over the legs); only the bones keyed here are touched. */
(function (root) {
  'use strict';
  const ZERO = [0, 0, 0];
  const add = (a, b, k = 1) => a.map((x, i) => x + k * b[i]);

  /** The held value of each bone of a pose clip (its first key), as { bone: [x, y, z] } rotations. */
  function poseOf(clip) {
    const out = {};
    for (const [bone, ch] of Object.entries(clip.bones)) if (ch.rotation && ch.rotation.length) out[bone] = ch.rotation[0].v.slice();
    return out;
  }

  /**
   * A clip from timed poses: [[t, { bone: [x, y, z] }], ...]. Every bone named in any pose gets a key at every time (0 where a
   * pose leaves it out), catmull-rom between keys, so the motion eases through the poses.
   */
  function keyed(length, poses, positions = []) {
    const bones = {};
    const names = new Set(poses.flatMap(([, p]) => Object.keys(p)));
    for (const bone of names) {
      bones[bone] = { rotation: poses.map(([t, p]) => ({ t, v: (p[bone] || ZERO).slice(), lerp: 'catmullrom' })) };
    }
    for (const [bone, keys] of Object.entries(Object.fromEntries(positions))) {
      bones[bone] = bones[bone] || {};
      bones[bone].position = keys.map(([t, v]) => ({ t, v: v.slice(), lerp: 'catmullrom' }));
    }
    return { length, loop: false, bones };
  }

  /** Scales every bone of a pose: k = 0 neutral, 1 the pose. */
  const part = (pose, k, extra = {}) => {
    const out = {};
    for (const [b, v] of Object.entries(pose)) out[b] = v.map((x) => x * k);
    for (const [b, v] of Object.entries(extra)) out[b] = add(out[b] || ZERO, v);
    return out;
  };

  function keyedClips(ap) {
    const blade = poseOf(ap.blockR), shield = poseOf(ap.blockL);
    // blade: the weapon arm is driven back and up by the blow, the chest turns away from it
    const bladeJolt = { right_arm: [12, 6, 4], right_elbow: [6, 0, 0], upper: [-5, -6, 0], head: [3, 4, 0] };
    const parryBlade = keyed(0.5, [
      [0, part(blade, 0)],
      [0.07, part(blade, 1.05)],
      [0.12, part(blade, 1, bladeJolt)],
      [0.2, part(blade, 1, { right_arm: [3, 1, 1], upper: [-1.5, -1.5, 0] })],
      [0.34, part(blade, 0.95)],
      [0.5, part(blade, 0)],
    ]);
    // shield: the off arm punches the shield up into the blow, the body braces behind it
    const shieldJolt = { left_arm: [10, -4, -3], left_elbow: [5, 0, 0], upper: [-6, 5, 0], head: [3, -3, 0], right_arm: [-6, 0, 6] };
    const parryShield = keyed(0.5, [
      [0, part(shield, 0)],
      [0.06, part(shield, 1.08)],
      [0.11, part(shield, 1, shieldJolt)],
      [0.2, part(shield, 1, { left_arm: [2.5, -1, -1], upper: [-1.5, 1, 0] })],
      [0.34, part(shield, 0.95)],
      [0.5, part(shield, 0)],
    ]);
    // stagger: thrown back, arms out for balance, then the guard regained
    const stagger = keyed(1.0, [
      [0, {}],
      [0.1, { upper: [-16, 8, -4], head: [10, -6, 3], right_arm: [-28, 0, 22], right_elbow: [-20, 0, 0], left_arm: [-22, 0, -26], left_elbow: [-18, 0, 0] }],
      [0.24, { upper: [-12, 5, -3], head: [7, -4, 2], right_arm: [-20, 0, 26], right_elbow: [-26, 0, 0], left_arm: [-16, 0, -30], left_elbow: [-22, 0, 0] }],
      [0.45, { upper: [-6, 2, -1], head: [3, -1, 1], right_arm: [-12, 0, 14], right_elbow: [-20, 0, 0], left_arm: [-10, 0, -16], left_elbow: [-16, 0, 0] }],
      [0.7, { upper: [-2, 0, 0], head: [1, 0, 0], right_arm: [-5, 0, 5], right_elbow: [-10, 0, 0], left_arm: [-4, 0, -6], left_elbow: [-8, 0, 0] }],
      [1.0, {}],
    ]);
    return { parry_blade: parryBlade, parry_shield: parryShield, stagger };
  }

  const api = { keyedClips, keyed };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.KeyedClips = api;
})(typeof window !== 'undefined' ? window : globalThis);
