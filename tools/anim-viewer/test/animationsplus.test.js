'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { importAnimationsPlus, mirror } = require('../import/animationsplus.js');
const { RIG } = require('../gecko/rig.js');

const model = JSON.parse(fs.readFileSync(path.join(__dirname, '../library/animationsplus/model.bbmodel'), 'utf8'));
const clips = importAnimationsPlus(model);
const BONES = new Set(RIG.map((b) => b.name));

/** Linear value of a channel at time t (enough for the mirror check). */
function at(keys, t) {
  const k = [...keys].sort((a, b) => a.t - b.t);
  if (t <= k[0].t) return k[0].v;
  for (let i = 1; i < k.length; i++) if (t <= k[i].t) {
    const f = (t - k[i - 1].t) / (k[i].t - k[i - 1].t);
    return k[i].v.map((v, j) => k[i - 1].v[j] + f * (v - k[i - 1].v[j]));
  }
  return k[k.length - 1].v;
}

test('imports all 142 clips', () => assert.equal(Object.keys(clips).length, 142));

test('Sword_Swing_1R keys the right arm and elbow, plays once at its authored length', () => {
  const c = clips.Sword_Swing_1R;
  const src = model.animations.find((a) => a.name === 'Sword_Swing_1R');
  assert.ok(c.bones.right_arm.rotation.length > 0);
  assert.ok(c.bones.right_elbow.rotation.length > 0);
  assert.ok(c.bones.weapon_r.rotation.length > 0);
  assert.equal(c.length, src.length);
  assert.equal(c.loop, false);
});

test('walk loops; hold clips hold', () => {
  assert.equal(clips.walk.loop, true);
  const held = model.animations.find((a) => a.loop === 'hold');
  assert.equal(clips[held.name].loop, 'hold');
});

test('only rig bones are kept', () => {
  for (const c of Object.values(clips)) for (const b of Object.keys(c.bones)) assert.ok(BONES.has(b), b);
});

test('data points become numbers; Molang and blanks become 0; other bones and effects are dropped', () => {
  const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/molang.bbmodel.json'), 'utf8'));
  const warnings = [];
  const c = importAnimationsPlus(fixture, (w) => warnings.push(w)).wobble;
  assert.deepEqual(c.bones.right_arm.rotation[0], { t: 0, v: [0, 0, 5], lerp: 'linear' });
  assert.deepEqual(c.bones.right_arm.position[0], { t: 0.5, v: [1, 2, 3], lerp: 'step' });
  assert.deepEqual(Object.keys(c.bones), ['right_arm']);
  assert.equal(warnings.length, 1);
});

test('mirroring the right swing gives the authored left swing (sign convention)', () => {
  const m = mirror(clips.Sword_Swing_1R), L = clips.Sword_Swing_1L;
  for (const bone of ['left_arm', 'left_elbow', 'right_arm', 'upper', 'body', 'weapon_l']) {
    const a = at(m.bones[bone].rotation, 0.25), b = at(L.bones[bone].rotation, 0.25);
    for (let i = 0; i < 3; i++) assert.ok(Math.abs(a[i] - b[i]) < 15, `${bone}[${i}] ${a[i]} vs ${b[i]}`);
  }
});
