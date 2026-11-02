/* Mo' Bends clips (Iwo Plaza, MIT; library/mobends): per-frame local quaternions per bone in Minecraft model space.
 *   importMoBends(json, name)  { name: Clip }, frames at 60 fps (31 frames = 0.5 s); a one-frame clip is a held pose
 * Mo' Bends' body bends at the hips and carries the head and arms, like the rig's upper; forearms and forelegs are the
 * elbows and knees; held items rest at 90° about x (its "orientation"), so only the turn from that rest is kept. */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const P = isNode ? require('./parts.js') : root.ImportParts;
  const W = isNode ? require('../gecko/write.js') : root.GeckoWrite;

  const FPS = 60;
  const MAP = {
    body: 'upper', head: 'head', rightArm: 'right_arm', rightForeArm: 'right_elbow', leftArm: 'left_arm', leftForeArm: 'left_elbow',
    rightLeg: 'right_leg', rightForeLeg: 'right_knee', leftLeg: 'left_leg', leftForeLeg: 'left_knee', rightHeldItem: 'weapon_r', leftHeldItem: 'weapon_l',
  };
  const ITEM_REST = Math.SQRT1_2;   // quaternion (sin 45°, 0, 0, cos 45°): 90° about x

  function quatMatrix(q) {
    const [x, y, z, w] = q;
    return [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
      2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
      2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)];
  }

  function importMoBends(json, name) {
    const bones = {};
    let frames = 1;
    for (const [src, b] of Object.entries(json.bones || {})) if (MAP[src]) frames = Math.max(frames, b.keyframes.length);
    const length = frames > 1 ? (frames - 1) / FPS : 0.05;
    for (const [src, b] of Object.entries(json.bones || {})) {
      const bone = MAP[src];
      if (!bone) continue;
      const item = /HeldItem$/.test(src);
      const rot = b.keyframes.map((k, i) => {
        let m = quatMatrix(k.rotation);
        if (item) m = W.mul3(quatMatrix([-ITEM_REST, 0, 0, ITEM_REST]), m);
        return { t: frames > 1 ? i / FPS : 0, v: W.fromTrue(P.mcToTrue(m)).map((x) => (Math.abs(x) < 1e-9 ? 0 : +x.toFixed(5))), lerp: 'linear' };
      });
      if (rot.some((k) => k.v.some((x) => Math.abs(x) > 1e-3))) bones[bone] = { rotation: rot };
    }
    return { [name]: { length, loop: frames <= 1, bones } };
  }

  const api = { importMoBends };
  if (isNode) module.exports = api;
  else root.ImportMoBends = api;
})(typeof window !== 'undefined' ? window : globalThis);
