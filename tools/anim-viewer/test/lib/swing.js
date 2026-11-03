'use strict';
/* The weapon tip of a shipped attack and measures over its speed trace (test/lib/motion.js velocities: samples are not
 * evenly spaced, so every measure weighs a sample by the time it covers). Shared by motion.test.js and timing.test.js. */
const assert = require('node:assert/strict');
const M = require('./motion.js');
const { classLength } = require('../../build_npc.js');

/** Fist rest points (rig px) and the blade's rest direction from the fist. */
const FIST = { right: [6, 12, -2], left: [-6, 12, -2] }, DIR = [0, Math.SQRT1_2, -Math.SQRT1_2];
/** The plain attack a `_l` or `_shield` twin is made from (they share its hits.json entry). */
const baseName = (n) => n.replace(/_(l|shield)$/, '');
const sideOf = (n) => (/_l$/.test(n) ? 'left' : 'right');
/** The rest point of the tip of a blade `blocks` long held in the `side` fist. */
const tipRest = (side, blocks) => FIST[side].map((x, i) => x + DIR[i] * blocks * 16 * (side === 'left' && i === 0 ? -1 : 1));
/** The weapon tip of an attack ({ name, fs }), sample by sample: { side, len (blocks), v (velocities) }. */
const tipOf = (c) => {
  const side = sideOf(c.name), len = classLength(baseName(c.name));
  return { side, len, v: M.velocities(M.track(c.fs, side === 'left' ? 'weapon_l' : 'weapon_r', tipRest(side, len))) };
};
/** Fails with the first 15 entries of a list of failures, and how many there were. */
const report = (bad) => assert.deepEqual(bad.slice(0, 15), [], `${bad.length} failures`);

/** The time a velocity sample covers that lies inside [a, b] (s): the sample stands for the segment between its two poses. */
const overlap = (x, a, b) => Math.max(0, Math.min(b, x.t + x.dt / 2) - Math.max(a, x.t - x.dt / 2));
/** The path the tip travels within [a, b] (px). */
const pathIn = (v, a, b) => v.reduce((s, x) => s + x.speed * overlap(x, a, b), 0);
/** The samples within [a, b], each with the time it covers there (`in`). */
const within = (v, a, b) => v.map((x) => ({ ...x, in: overlap(x, a, b) })).filter((x) => x.in > 0);
/** The top speed within [a, b] (px/s). */
const peak = (v, a, b) => Math.max(0, ...within(v, a, b).map((x) => x.speed));
/** The median of values xs, each weighed by ws (default 1): half the weight lies at or below it. */
function median(xs, ws = xs.map(() => 1)) {
  const s = xs.map((x, i) => [x, ws[i]]).sort((a, b) => a[0] - b[0]), half = ws.reduce((a, b) => a + b, 0) / 2;
  let acc = 0;
  for (const [x, w] of s) if ((acc += w) >= half) return x;
  return NaN;
}

module.exports = { FIST, DIR, baseName, sideOf, tipRest, tipOf, report, overlap, pathIn, within, peak, median };
