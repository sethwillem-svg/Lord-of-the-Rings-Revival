'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { RIG } = require('../gecko/rig.js');
const { geoJson, animationJson } = require('../gecko/write.js');

test('rig has the 15 bones with AnimationsPlus pivots', () => {
  assert.equal(RIG.length, 15);
  assert.deepEqual(RIG.find((b) => b.name === 'right_elbow'), { name: 'right_elbow', parent: 'right_arm', pivot: [6, 18, 0] });
  assert.equal(RIG.find((b) => b.name === 'left_knee').parent, 'left_leg');
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
