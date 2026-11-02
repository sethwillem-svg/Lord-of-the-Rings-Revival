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
  assert.deepEqual(a.animations.wave.bones.right_arm.rotation['0.5'], { vector: [-90, 0, 0], lerp_mode: 'catmullrom' });
  assert.deepEqual(a.animations.wave.bones.right_arm.rotation['0.0'], { vector: [0, 0, 0] });
  assert.equal(a.animations.wave.animation_length, 1);
  assert.equal(a.animations.wave.loop, true);
});

const { eulerMatrix, zyxToXyz } = require('../gecko/write.js');
const close = (a, b, eps = 1e-6) => a.every((x, i) => Math.abs(x - b[i]) < eps);

test('eulerMatrix composes in the named order', () => {
  // 90° about x then nothing: y axis goes to z
  const m = eulerMatrix([90, 0, 0], 'XYZ');
  assert.ok(close([m[1], m[4], m[7]], [0, 0, 1]), m.join());
});

test('rotations are written in PulseLib order (XYZ) giving the same matrix as Blockbench (ZYX)', () => {
  for (const v of [[-38, 52, 34], [-90, 77, -90], [0, 90, -35], [10, -170, 45]]) {
    const xyz = zyxToXyz(v);
    assert.ok(close(eulerMatrix(xyz, 'XYZ'), eulerMatrix(v, 'ZYX')), `${v} -> ${xyz}`);
  }
  const a = animationJson({ c: { length: 1, loop: false, bones: { right_arm: { rotation: [{ t: 0, v: [-38, 52, 34], lerp: 'linear' }] } } } });
  const w = a.animations.c.bones.right_arm.rotation['0.0'].vector;
  assert.ok(close(eulerMatrix(w, 'XYZ'), eulerMatrix([-38, 52, 34], 'ZYX'), 1e-3));
});

test('hold clips are written as hold_on_last_frame', () => {
  const a = animationJson({ c: { length: 1, loop: 'hold', bones: {} } });
  assert.equal(a.animations.c.loop, 'hold_on_last_frame');
});
