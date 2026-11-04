'use strict';
/* The tests' own measuring library (test/lib/motion.js): where clips are sampled, and the pose cache. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const M = require('./lib/motion.js');
const { shipped } = require('./lib/shipped.js');

test('times include every key time of every bone, plus the 60 Hz grid', () => {
  const anim = { animation_length: 0.5, bones: { head: { rotation: { '0.0': [0, 0, 0], '0.137': [10, 0, 0], '0.5': [0, 0, 0] } } } };
  const ts = M.times(anim);
  assert.ok(ts.includes(0.137)); assert.ok(ts.includes(0.25)); assert.equal(ts[ts.length - 1], 0.5);
  assert.deepEqual(ts, [...new Set(ts)].sort((a, b) => a - b));
});

test('cached frames equal uncached frames', () => {
  const a = shipped.man.animations.animations.attack_sword_1;
  const fresh = M.frames(a), cached = M.frames(a, 60, 'man/attack_sword_1'), again = M.frames(a, 60, 'man/attack_sword_1');
  assert.deepEqual(JSON.parse(JSON.stringify(cached)), JSON.parse(JSON.stringify(fresh)));
  assert.deepEqual(JSON.parse(JSON.stringify(again)), JSON.parse(JSON.stringify(fresh)));
  assert.ok(fs.existsSync(path.join(__dirname, '.cache')));
});

test('velocities carry the time step of each sample pair', () => {
  const tr = [{ t: 0, p: [0, 0, 0] }, { t: 0.5, p: [1, 0, 0] }, { t: 0.625, p: [2, 0, 0] }];
  const v = M.velocities(tr);
  assert.deepEqual(v.map((x) => x.dt), [0.5, 0.125]);
  assert.deepEqual(v.map((x) => x.speed), [2, 8]);
});

test('twist is the turn about the bone’s own y, whatever it swings by; swing is the rest', () => {
  const rot = (axis, a) => {   // a row-major rotation matrix about a rig axis (degrees)
    const c = Math.cos((a * Math.PI) / 180), s = Math.sin((a * Math.PI) / 180);
    return { x: [1, 0, 0, 0, c, -s, 0, s, c], y: [c, 0, s, 0, 1, 0, -s, 0, c], z: [c, -s, 0, s, c, 0, 0, 0, 1] }[axis];
  };
  const mul = (a, b) => [0, 1, 2].flatMap((r) => [0, 1, 2].map((c) => a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]));
  const I = [1, 0, 0, 0, 1, 0, 0, 0, 1];
  const near = (a, b) => assert.ok(Math.abs(a - b) < 1e-6, `${a} vs ${b}`);
  near(M.twist({ p: { R: I }, b: { R: rot('y', 70) } }, 'b', 'p'), 70);
  near(M.twist({ p: { R: I }, b: { R: rot('y', -120) } }, 'b', 'p'), -120);
  near(M.twist({ p: { R: I }, b: { R: mul(rot('x', 80), rot('y', 40)) } }, 'b', 'p'), 40);   // swing after twist
  near(M.twist({ p: { R: I }, b: { R: mul(rot('y', 40), rot('z', 60)) } }, 'b', 'p'), 40);   // twist after swing
  near(M.twist({ p: { R: rot('z', 30) }, b: { R: mul(rot('z', 30), rot('y', -50)) } }, 'b', 'p'), -50);   // against the parent
  near(M.swing({ p: { R: I }, b: { R: mul(rot('x', 80), rot('y', 40)) } }, 'b', 'p'), 80);
  near(M.swing({ p: { R: I }, b: { R: rot('y', 120) } }, 'b', 'p'), 0);
});
