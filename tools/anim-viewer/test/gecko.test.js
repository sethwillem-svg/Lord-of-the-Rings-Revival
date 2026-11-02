'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { RIG } = require('../gecko/rig.js');
const { geoJson, animationJson } = require('../gecko/write.js');

test('rig has the 15 bones with AnimationsPlus pivots', () => {
  assert.equal(RIG.length, 15);
  assert.deepEqual(RIG.find((b) => b.name === 'right_elbow'), { name: 'right_elbow', parent: 'right_arm', pivot: [6, 18, 0] });
  assert.equal(RIG.find((b) => b.name === 'left_knee').parent, 'left_leg');
  assert.equal(RIG.find((b) => b.name === 'right_arm').parent, 'body');   // AnimationsPlus twists body and the arms follow
  assert.deepEqual(RIG.find((b) => b.name === 'root').pivot, [0, 16, 0]);
  assert.deepEqual(RIG.find((b) => b.name === 'weapon_l'), { name: 'weapon_l', parent: 'left_elbow', pivot: [-6, 12, -2] });
});

test('every parent is declared before its child', () => {
  const seen = new Set();
  for (const b of RIG) { if (b.parent) assert.ok(seen.has(b.parent), b.name); seen.add(b.name); }
});

test('geo.json lists bones without cubes', () => {
  const g = geoJson(RIG)['minecraft:geometry'][0];
  assert.equal(g.description.identifier, 'geometry.npc_biped');
  assert.equal(g.bones.length, 15);
  assert.ok(g.bones.every((b) => !b.cubes));
  assert.equal(g.bones.find((b) => b.name === 'root').parent, undefined);
});

test('animation json writes keys by time with lerp_mode', () => {
  const a = animationJson({ wave: { length: 1, loop: true, bones: { right_arm: { rotation: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }, { t: 0.5, v: [-90, 0, 0], lerp: 'catmullrom' }] } } } });
  assert.equal(a.format_version, '1.8.0');
  assert.deepEqual(a.animations.wave.bones.right_arm.rotation['0.5'], { vector: [90, 0, 0], lerp_mode: 'catmullrom' });   // Blockbench x -90 is PulseLib x 90
  assert.deepEqual(a.animations.wave.bones.right_arm.rotation['0.0'], { vector: [0, 0, 0] });
  assert.equal(a.animations.wave.animation_length, 1);
  assert.equal(a.animations.wave.loop, true);
});

const { eulerMatrix, toPulse, trueRotation, fromTrue } = require('../gecko/write.js');
const close = (a, b, eps = 1e-6) => a.every((x, i) => Math.abs(x - b[i]) < eps);

test('eulerMatrix composes in the named order', () => {
  const m = eulerMatrix([90, 0, 0], 'XYZ');   // y axis goes to z
  assert.ok(close([m[1], m[4], m[7]], [0, 0, 1]), m.join());
});

test('Blockbench values are applied as Rz·Ry·Rx of (-x, -y, z): x -90 lifts a hanging arm forward (-z)', () => {
  const R = trueRotation([-90, 0, 0]);
  const down = [0, -1, 0], v = [0, 1, 2].map((r) => R[r * 3] * down[0] + R[r * 3 + 1] * down[1] + R[r * 3 + 2] * down[2]);
  assert.ok(close(v, [0, 0, -1]), v.join());
});

test('rotations are written so PulseLib (XYZ, no flips) gives the rotation Blockbench shows', () => {
  for (const v of [[-38, 52, 34], [-90, 77, -90], [0, 90, -35], [10, -170, 45]]) {
    assert.ok(close(eulerMatrix(toPulse(v), 'XYZ'), trueRotation(v)), String(v));
    assert.ok(close(fromTrue(trueRotation(v)).map((x, i) => x), v.map((x) => x), 1e-6) || close(trueRotation(fromTrue(trueRotation(v))), trueRotation(v)), 'round trip ' + v);
  }
  const a = animationJson({ c: { length: 1, loop: false, bones: { right_arm: { rotation: [{ t: 0, v: [-38, 52, 34], lerp: 'linear' }], position: [{ t: 0, v: [1, 2, 3], lerp: 'linear' }] } } } });
  const b = a.animations.c.bones.right_arm;
  assert.ok(close(eulerMatrix(b.rotation['0.0'].vector, 'XYZ'), trueRotation([-38, 52, 34]), 1e-3));
  assert.deepEqual(b.position['0.0'].vector, [-1, 2, 3]);   // Blockbench negates position x too
});

test('hold clips are written as hold_on_last_frame', () => {
  const a = animationJson({ c: { length: 1, loop: 'hold', bones: {} } });
  assert.equal(a.animations.c.loop, 'hold_on_last_frame');
});
