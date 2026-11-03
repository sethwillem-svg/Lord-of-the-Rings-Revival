'use strict';
/* Body checks on every race's clips: heads level, feet on the floor, joints bending the right way, loops without seams. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const { RACES } = require('../npc_slots.js');

const { shipped: built } = require('./lib/shipped.js');   // the files the game ships
const CALM = /^(idle(_\d)?|walk|run|guard|march)$/;
const STANDING = /^(idle(_\d)?|walk|run|guard|march|shield_brace|spear_brace)$/;
const times = (a, n = 24) => Array.from({ length: n + 1 }, (_, i) => (i * a.animation_length) / n);
const each = (filter, fn) => {
  const bad = [];
  for (const race of RACES) for (const [name, a] of Object.entries(built[race].animations.animations)) if (filter.test(name)) fn(race, name, a, bad);
  return [...new Set(bad)];
};
const dir = (w, v) => [0, 1, 2].map((r) => w.R[r * 3] * v[0] + w.R[r * 3 + 1] * v[1] + w.R[r * 3 + 2] * v[2]);

test('heads stay level while standing and moving (looking within 30° of the horizon)', () => {
  assert.deepEqual(each(CALM, (race, name, a, bad) => {
    for (const t of times(a)) {
      const f = dir(worldBones(samplePose(a, t)).head, [0, 0, -1]), pitch = (Math.asin(Math.max(-1, Math.min(1, f[1]))) * 180) / Math.PI;
      if (Math.abs(pitch) > 30) { bad.push(`${race} ${name}: head ${pitch.toFixed(0)}°`); break; }
    }
  }), []);
});

test('feet stay on the floor while standing and moving (the lower foot within -1.5..3.5 px of it)', () => {
  assert.deepEqual(each(STANDING, (race, name, a, bad) => {
    for (const t of times(a)) {
      const w = worldBones(samplePose(a, t));
      const low = Math.min(point(w.right_knee, [1.9, 0, 0])[1], point(w.left_knee, [-1.9, 0, 0])[1]);
      if (low < -1.5 || low > 3.5) { bad.push(`${race} ${name} t=${t.toFixed(2)}: lower foot at ${low.toFixed(1)} px`); break; }
    }
  }), []);
});

/** A joint's hinge angle as the game's bend deformer reads it (client.anim.NpcPose.bend): negative folds forward. */
function bend(w, joint, parent) {
  const P = w[parent].R, J = w[joint].R;
  const L = [0, 1, 2].map((r) => [0, 1, 2].map((c) => P[r] * J[c] + P[3 + r] * J[3 + c] + P[6 + r] * J[6 + c]));   // P^T J
  const d = [-L[0][1], -L[1][1], -L[2][1]];                                                                         // (0,-1,0) through it
  return (Math.atan2(d[2], -d[1]) * 180) / Math.PI;
}

test('elbows only fold forward and knees only fold back (10° of slack)', () => {
  assert.deepEqual(each(/./, (race, name, a, bad) => {
    for (const t of times(a, 16)) {
      const w = worldBones(samplePose(a, t));
      for (const s of ['right', 'left']) {
        const e = bend(w, `${s}_elbow`, `${s}_arm`), k = bend(w, `${s}_knee`, `${s}_leg`);
        if (e > 10) bad.push(`${race} ${name}: ${s} elbow bends backwards ${e.toFixed(0)}°`);
        if (k < -10) bad.push(`${race} ${name}: ${s} knee bends forwards ${k.toFixed(0)}°`);
      }
    }
  }), []);
});

test('looping clips end where they start (every bone within 0.5 px), so no pop each cycle', () => {
  assert.deepEqual(each(/./, (race, name, a, bad) => {
    if (a.loop !== true || a.animation_length < 0.1) return;
    const w0 = worldBones(samplePose(a, 0)), w1 = worldBones(samplePose(a, a.animation_length - 1e-6));
    for (const bone of Object.keys(w0)) {
      const p = [0, 10, 0], d = Math.hypot(...point(w0[bone], p).map((x, i) => x - point(w1[bone], p)[i]));
      if (d > 0.5) { bad.push(`${race} ${name}: ${bone} jumps ${d.toFixed(1)} px`); break; }
    }
  }), []);
});
