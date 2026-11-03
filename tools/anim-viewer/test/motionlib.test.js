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
