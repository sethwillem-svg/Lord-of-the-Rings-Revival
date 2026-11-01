import test from 'node:test';
import assert from 'node:assert/strict';
import { makeRng } from '../lib/rng.mjs';
import { solveBody, BONES } from '../lib/body.mjs';
import { MAN } from '../races/man.mjs';

const SPEC_BONES = [
  'root', 'body', 'torso', 'head', 'head_armor',
  'right_arm', 'right_forearm', 'right_hand', 'right_item', 'right_arm_armor',
  'left_arm', 'left_forearm', 'left_hand', 'left_item', 'left_arm_armor',
  'chest_armor', 'cape',
  'right_leg', 'right_shin', 'right_leg_armor', 'right_boot_armor',
  'left_leg', 'left_shin', 'left_leg_armor', 'left_boot_armor',
];
const inRange = (v, [lo, hi]) => v >= lo && v <= hi;

test('same seed gives the same body', () => {
  assert.deepEqual(solveBody(MAN, makeRng(7)), solveBody(MAN, makeRng(7)));
});

test('heights stay in 30..34 for seeds 1..500 and add up', () => {
  for (let s = 1; s <= 500; s++) {
    const b = solveBody(MAN, makeRng(s));
    assert.ok(inRange(b.height, [30, 34]), `seed ${s}: height ${b.height}`);
    assert.equal(b.height, b.legs + b.torso + b.head);
    assert.equal(b.legs, b.thigh + b.shin);
    assert.equal(b.arm, b.upperArm + b.forearm + b.hand);
  }
});

test('every spec bone has a pivot and BONES lists exactly the spec bones', () => {
  const b = solveBody(MAN, makeRng(1));
  assert.deepEqual(Object.keys(b.pivots).sort(), [...SPEC_BONES].sort());
  assert.deepEqual(BONES.map(([n]) => n).sort(), [...SPEC_BONES].sort());
});

test('parents come before children in BONES', () => {
  const seen = new Set();
  for (const [name, parent] of BONES) {
    if (parent) assert.ok(seen.has(parent), `${name} before its parent ${parent}`);
    seen.add(name);
  }
});

test('right side is negative X', () => {
  const p = solveBody(MAN, makeRng(3)).pivots;
  assert.ok(p.right_arm[0] < 0 && p.left_arm[0] > 0);
  assert.ok(p.right_leg[0] < 0 && p.left_leg[0] > 0);
});

test('odd seeds build within the ranges', () => {
  for (const s of [0, -1, 2 ** 40, 'abc']) {
    const b = solveBody(MAN, makeRng(s));
    assert.ok(inRange(b.height, [30, 34]), `seed ${s}`);
    assert.ok(inRange(b.shoulders, MAN.shoulders));
  }
});

test('forked streams are independent', () => {
  assert.notEqual(makeRng(1).fork('hair').next(), makeRng(1).fork('beard').next());
});

test('colours come from the race lists', () => {
  const b = solveBody(MAN, makeRng(11));
  assert.ok(MAN.skin.includes(b.colours.skin));
  assert.ok(MAN.hair.includes(b.colours.hair));
  assert.ok(MAN.eye.includes(b.colours.eye));
});
