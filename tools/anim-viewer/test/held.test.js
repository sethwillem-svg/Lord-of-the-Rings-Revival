'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { partToRig, FIST, itemScale, scaleAbout, bendPoint } = require('../gecko/held.js');
const { RIG } = require('../gecko/rig.js');

const near = (a, b, eps = 1e-6) => a.every((x, i) => Math.abs(x - b[i]) < eps);
const grips = JSON.parse(fs.readFileSync(path.join(__dirname, '../../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json'), 'utf8'));

test('the fist in arm space is the weapon bone pivot in rig space', () => {
  assert.ok(near(partToRig('right_arm', [-1, 10, -2]), FIST.right));
  assert.ok(near(partToRig('left_arm', [1, 10, -2]), FIST.left));
  assert.deepEqual(FIST.right, RIG.find((b) => b.name === 'weapon_r').pivot);
});

test('class lengths come from weapon_grips.json', () => {
  assert.deepEqual(Object.fromEntries(Object.entries(grips).map(([k, v]) => [k, v.length])),
    { dagger: 0.6, sword: 1.0, longsword: 1.8, axe: 1.4, spear: 2.5, bow: 1.1, longbow: 1.6, crossbow: 0.9 });
});

test('an item is scaled to its class length; unknown classes keep their size', () => {
  assert.ok(Math.abs(itemScale('dagger', grips, 24) - 0.4) < 1e-9);
  assert.ok(Math.abs(itemScale('spear', grips, 56) - 40 / 56) < 1e-9);
  assert.equal(itemScale('shield', grips, 17), 1);
  assert.equal(itemScale(null, grips, 17), 1);
});

test('scaling about the fist keeps the handle in the hand', () => {
  assert.ok(near(scaleAbout(FIST.right, 0.4, FIST.right), FIST.right));
  assert.ok(near(scaleAbout(FIST.right, 0.5, [6, 12, -12]), [6, 12, -7]));
});

test('a bend moves the forearm with the elbow and leaves the upper arm', () => {
  const elbow = { pivot: [6, 18, 0], q: [Math.SQRT1_2, 0, 0, Math.SQRT1_2], pos: [0, 0, 0] };   // 90° about x
  assert.ok(near(bendPoint(elbow, [6, 21, 0], 2), [6, 21, 0]));        // above the band
  assert.ok(near(bendPoint(elbow, [6, 12, 0], 2), [6, 18, -6], 1e-9)); // hand: fully turned (Rx 90 takes -y to -z)
  const mid = bendPoint(elbow, [6, 18, 0], 2);                          // on the joint: unchanged point (it is the pivot)
  assert.ok(near(mid, [6, 18, 0]));
});
