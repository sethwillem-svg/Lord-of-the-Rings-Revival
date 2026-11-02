'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { animationJson } = require('../gecko/write.js');
const { samplePose, worldBones, point } = require('../gecko/sample.js');

const anim = (bones, loop = false, length = 1) => animationJson({ a: { length, loop, bones } }).animations.a;
const near = (a, b, eps = 1e-4) => a.every((x, i) => Math.abs(x - b[i]) < eps);

test('a key time returns the key value', () => {
  const a = anim({ head: { position: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }, { t: 0.5, v: [2, 4, 6], lerp: 'linear' }] } });
  assert.ok(near(samplePose(a, 0.5).head.pos, [-2, 4, 6]));   // file positions are true offsets (x flipped from Blockbench)
});

test('linear position halfway is the average', () => {
  const a = anim({ head: { position: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }, { t: 1, v: [0, 4, 0], lerp: 'linear' }] } });
  assert.ok(near(samplePose(a, 0.5).head.pos, [0, 2, 0]));
});

test('loops wrap and one-shots clamp', () => {
  const a = anim({ head: { position: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }, { t: 1, v: [0, 10, 0], lerp: 'linear' }] } }, true);
  assert.ok(near(samplePose(a, 1.1).head.pos, samplePose(a, 0.1).head.pos));
  const b = anim({ head: { position: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }, { t: 1, v: [0, 10, 0], lerp: 'linear' }] } }, false);
  assert.ok(near(samplePose(b, 3).head.pos, [0, 10, 0]));
});

test('a step key holds until the next key (Blockbench)', () => {
  const a = anim({ head: { position: [{ t: 0, v: [0, 0, 0], lerp: 'step' }, { t: 1, v: [0, 10, 0], lerp: 'linear' }] } });
  assert.ok(near(samplePose(a, 0.9).head.pos, [0, 0, 0]));
});

test('a right arm at x -90 puts the fist in front of the body', () => {
  const a = anim({ right_arm: { rotation: [{ t: 0, v: [-90, 0, 0], lerp: 'linear' }] } });
  const w = worldBones(samplePose(a, 0));
  const fist = point(w.right_arm, [4.5, 12.5, 0]);
  assert.ok(fist[2] < -9, fist.join());
  assert.ok(Math.abs(fist[1] - 22.5) < 1e-6);
});
