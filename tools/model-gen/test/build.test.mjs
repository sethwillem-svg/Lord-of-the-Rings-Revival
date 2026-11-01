import test from 'node:test';
import assert from 'node:assert/strict';
import { buildModel } from '../lib/build.mjs';
import { runQa } from '../lib/qa.mjs';
import { serializeLegacy } from '../lib/bbmodel.mjs';

test('qa passes for seeds 1..50, full and low-detail', () => {
  for (let s = 1; s <= 50; s++) {
    for (const lod of [false, true]) {
      const { qa } = buildModel('man', s, { lod });
      assert.ok(qa.ok, `seed ${s}${lod ? ' lod' : ''}: ${qa.problems.join('; ')}`);
    }
  }
});

test('qa passes for the barest and busiest style mixes', () => {
  for (const o of [{ hair: 'bald', beard: 'none' }, { hair: 'shoulder', beard: 'long', ears: 'large', nose: 'hooked' },
    { hair: 'tied', beard: 'moustache' }, { hair: 'side_part', beard: 'stubble' }, { hair: 'long', beard: 'full' }]) {
    for (const s of [1, 2, 3, 4]) {
      const { qa } = buildModel('man', s, { overrides: o });
      assert.ok(qa.ok, `seed ${s} ${JSON.stringify(o)}: ${qa.problems.join('; ')}`);
    }
  }
});

test('low-detail copy has at most 60% of the cubes', () => {
  for (let s = 1; s <= 50; s++) {
    const full = buildModel('man', s).model.cubes.length;
    const lod = buildModel('man', s, { lod: true }).model.cubes.length;
    assert.ok(lod <= 0.6 * full, `seed ${s}: ${lod} of ${full}`);
  }
});

test('the texture sheet fits for seeds 1..200', () => {
  for (let s = 1; s <= 200; s++) buildModel('man', s);
});

test('same seed gives a byte-identical bbmodel', () => {
  assert.equal(serializeLegacy(buildModel('man', 3).model), serializeLegacy(buildModel('man', 3).model));
});

test('model has the 25 spec bones and its name carries the seed', () => {
  const { model } = buildModel('man', 9);
  assert.equal(model.rig.size, 25);
  assert.equal(model.name, 'man_base_9');
  assert.equal(buildModel('man', 9, { lod: true }).model.name, 'man_base_9_lod');
});

test('qa catches a gap', () => {
  const { model } = buildModel('man', 1);
  const f = model.findCube('forearm_r');
  f.from[1] -= 2; f.to[1] -= 2;
  assert.ok(runQa(model).problems.some((p) => /seal/.test(p)));
});

test('qa catches a buried cube', () => {
  const { model } = buildModel('man', 1);
  const t = model.findCube('tunic');
  const c = t.from.map((v, i) => (v + t.to[i]) / 2);
  model.cube({ name: 'hidden_bit', bone: 'torso', from: c.map((v) => v - 0.5), to: c.map((v) => v + 0.5) });
  assert.ok(runQa(model).problems.some((p) => /hidden/.test(p)));
});

test('qa catches a cube on an anchor bone', () => {
  const { model } = buildModel('man', 1);
  model.cube({ name: 'stray', bone: 'head_armor', from: [0, 40, 0], to: [1, 41, 1] });
  assert.ok(runQa(model).problems.some((p) => /anchor/.test(p)));
});

test('an unknown race is refused', () => {
  assert.throws(() => buildModel('ent', 1), /race/);
});
