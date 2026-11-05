'use strict';
/* import/mocap.js: BVH from the clip library (UAL, KayKit, CMU) onto the NPC rig with the browser viewer's retarget settings,
 * as Gecko clips the build and the game read. Judged as written (gecko/write.js) and played (gecko/sample.js), measured with
 * test/lib/motion.js. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const MC = require('../import/mocap.js');
const { RIG } = require('../gecko/rig.js');
const { animationJson } = require('../gecko/write.js');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const M = require('./lib/motion.js');
const LIBRARY = require('../library.js');
const ST = require('./lib/stance.js');

const bvh = (p) => fs.readFileSync(path.join(__dirname, '../library', p + '.bvh'), 'utf8');
const played = (c) => animationJson({ c }).animations.c;
const foot = (w, side) => point(w[side + '_knee'], [side === 'right' ? 1.9 : -1.9, 0, 0]);
const walk = MC.importBvh(bvh('ual/Walk_Loop'), { loop: true });
const walkFrames = M.frames(played(walk));

test('the retarget settings are the browser viewer’s, with the clip library’s (in place, no weapon IK)', () => {
  assert.equal(MC.SETTINGS.inPlace, true);
  assert.equal(MC.SETTINGS.ik, false);
  assert.equal(MC.SETTINGS.jointLimits, true);
  assert.equal(MC.SETTINGS.footLock, true);
  assert.equal(MC.SETTINGS.footPlant, true);
  assert.deepEqual(LIBRARY.defaults, { inPlace: true, ik: false });
});

test('a UAL walk imports as a loop on the rig’s own bones, its 40 frames at 30 fps', () => {
  const bones = new Set(RIG.map((b) => b.name));
  for (const b of Object.keys(walk.bones)) assert.ok(bones.has(b), b);
  for (const b of ['root', 'upper', 'lower', 'head', 'right_arm', 'right_elbow', 'left_arm', 'left_elbow', 'right_leg', 'right_knee', 'left_leg', 'left_knee'])
    assert.ok(walk.bones[b], `${b} not keyed`);
  assert.equal(walk.loop, true);
  assert.ok(Math.abs(walk.length - 40 / 30) < 1e-3, String(walk.length));
});

test('the walk loops: every bone ends within 0.1 px of where it starts', () => {
  const a = played(walk), w0 = worldBones(samplePose(a, 0)), w1 = worldBones(samplePose(a, a.animation_length - 1e-6));
  for (const b of Object.keys(w0)) {
    const d = Math.hypot(...point(w0[b], [0, 10, 0]).map((x, i) => x - point(w1[b], [0, 10, 0])[i]));
    assert.ok(d < 0.1, `${b} jumps ${d.toFixed(2)} px`);
  }
});

test('knees fold back and elbows forward (10° of slack), and both really bend', () => {
  let knee = 0, elbow = 0;
  for (const f of walkFrames) for (const s of ['right', 'left']) {
    const k = M.hinge(f.w, `${s}_knee`, `${s}_leg`).bend, e = M.hinge(f.w, `${s}_elbow`, `${s}_arm`).bend;
    assert.ok(k >= -10, `${s} knee folds forward ${k.toFixed(0)}° at ${f.t.toFixed(2)}`);
    assert.ok(e <= 10, `${s} elbow folds backward ${e.toFixed(0)}° at ${f.t.toFixed(2)}`);
    knee = Math.max(knee, k); elbow = Math.max(elbow, -e);
  }
  assert.ok(knee > 45, `knees bend at most ${knee.toFixed(0)}°`);
  assert.ok(elbow > 15, `elbows bend at most ${elbow.toFixed(0)}°`);
});

test('the feet stay near the floor: the lower foot within -1.5..3.5 px of it, and each foot swings through a stride', () => {
  for (const f of walkFrames) {
    const low = Math.min(foot(f.w, 'right')[1], foot(f.w, 'left')[1]);
    assert.ok(low >= -1.5 && low <= 3.5, `lower foot at ${low.toFixed(1)} px at ${f.t.toFixed(2)}`);
  }
  const z = walkFrames.map((f) => foot(f.w, 'right')[2]);
  assert.ok(Math.max(...z) - Math.min(...z) > 6, 'the right foot hardly moves');
});

test('right is right: the rig’s right hand throws UAL’s right cross', () => {
  const a = played(MC.importBvh(bvh('ual/Punch_Cross'), {}));
  let best = null;
  for (const f of M.frames(a)) { const r = point(f.w.weapon_r, [6, 12, -2]); if (!best || r[2] < best.r[2]) best = { r, l: point(f.w.weapon_l, [-6, 12, -2]) }; }
  assert.ok(best.r[2] < best.l[2] - 6, `right fist at z ${best.r[2].toFixed(1)}, left at ${best.l[2].toFixed(1)}`);
  assert.ok(best.r[0] > -4, `the right fist crossed to x ${best.r[0].toFixed(1)}`);
});

test('in place: a UAL walk has no travel, and a travelling CMU march is cut to one gait cycle in place, its legs still sweeping', () => {
  const xz = (c) => Math.max(0, ...((c.bones.root && c.bones.root.position) || []).map((k) => Math.hypot(k.v[0], k.v[2])));
  assert.equal(walk.travels, false);
  assert.ok(xz(walk) < 1e-6);
  const march = MC.importBvh(bvh('cmu/21_06'), { loop: true, cycles: 1 });
  assert.equal(march.travels, true);   // the take walks across the room: its foot lock is off (import/mocap.js)
  assert.ok(xz(march) < 1e-6, `root travels ${xz(march)} px`);
  assert.ok(march.length > 0.8 && march.length < 2.4, `cycle ${march.length} s`);
  const fs0 = M.frames(played(march)), z = fs0.map((f) => foot(f.w, 'right')[2]);
  assert.ok(Math.max(...z) - Math.min(...z) > 6, 'the legs stopped sweeping');
  // the planted foot slides back steadily under the hips (test/lib/stance.js skate): with the foot lock left on, it would be
  // held where it landed and then jump (16.8 px/s on this take)
  const s = ST.skate(fs0);
  assert.ok(s < 6, `the planted foot skates ${s.toFixed(1)} px/s`);
  const w0 = fs0[0].w, w1 = worldBones(samplePose(played(march), march.length - 1e-6));
  for (const b of Object.keys(w0)) assert.ok(Math.hypot(...point(w0[b], [0, 10, 0]).map((x, i) => x - point(w1[b], [0, 10, 0])[i])) < 0.5, `${b} pops at the seam`);
});

test('KayKit and CMU skeletons import too, and a held frame is one key per channel', () => {
  const k = MC.importBvh(bvh('kaykit/2H_Melee_Idle'), { loop: true });
  assert.ok(k.bones.right_arm && k.bones.left_knee);
  const h = MC.holdFrame(MC.importBvh(bvh('cmu/124_08'), {}), 0.5);
  for (const ch of Object.values(h.bones)) for (const keys of Object.values(ch)) assert.equal(keys.length, 1);
});
