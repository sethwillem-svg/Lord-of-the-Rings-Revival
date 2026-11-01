import test from 'node:test';
import assert from 'node:assert/strict';
import { makeRng } from '../lib/rng.mjs';
import { solveBody } from '../lib/body.mjs';
import { MAN } from '../races/man.mjs';
import { Assembly } from '../lib/assembly.mjs';
import * as head from '../parts/head.mjs';
import * as hair from '../parts/hair.mjs';
import * as beard from '../parts/beard.mjs';
import * as clothes from '../parts/clothes.mjs';
import { addMan } from '../parts/index.mjs';

const fresh = (seed = 1) => {
  const body = solveBody(MAN, makeRng(seed));
  return { body, asm: new Assembly(body), rng: makeRng(seed) };
};
const names = (asm) => asm.cubes.map((c) => c.name);

test('every style of every part builds', () => {
  for (const [mod, add] of [[head, 'addHead'], [hair, 'addHair'], [beard, 'addBeard'], [clothes, 'addClothes']]) {
    for (const style of mod.STYLES) {
      for (const seed of [1, 2, 3, 4, 5]) {
        const { body, asm, rng } = fresh(seed);
        head.addHead(asm, body, rng.fork('head'));
        if (mod !== head) mod[add](asm, body, rng.fork('x'), style);
      }
    }
  }
});

test('cube count is 40..80 for seeds 1..200', () => {
  for (let s = 1; s <= 200; s++) {
    const { body, asm, rng } = fresh(s);
    addMan(asm, body, rng);
    assert.ok(asm.cubes.length >= 40 && asm.cubes.length <= 80, `seed ${s}: ${asm.cubes.length} cubes`);
  }
});

test('cube count stays in range for the barest and the busiest man', () => {
  for (const o of [{ hair: 'bald', beard: 'none' }, { hair: 'shoulder', beard: 'long' }]) {
    const { body, asm, rng } = fresh(1);
    addMan(asm, body, rng, o);
    assert.ok(asm.cubes.length >= 40 && asm.cubes.length <= 80, `${JSON.stringify(o)}: ${asm.cubes.length}`);
  }
});

test('limb cubes sit on their bones', () => {
  const { body, asm, rng } = fresh(1);
  addMan(asm, body, rng);
  const bone = (n) => asm.get(n).bone;
  assert.equal(bone('sleeve_r'), 'right_arm');
  assert.equal(bone('forearm_r'), 'right_forearm');
  assert.equal(bone('hand_l'), 'left_hand');
  assert.equal(bone('shin_l'), 'left_shin');
  assert.equal(bone('thigh_r'), 'right_leg');
  assert.equal(bone('skull'), 'head');
  assert.equal(bone('tunic'), 'torso');
});

test('bald has no hair cubes and none has no beard cubes', () => {
  const { body, asm, rng } = fresh(1);
  addMan(asm, body, rng, { hair: 'bald', beard: 'none' });
  assert.ok(!names(asm).some((n) => n.startsWith('hair_')));
  assert.ok(!names(asm).some((n) => n.startsWith('beard_')));
});

test('overrides win and styles are reported', () => {
  const { body, asm, rng } = fresh(1);
  const styles = addMan(asm, body, rng, { hair: 'long', beard: 'full' });
  assert.equal(styles.hair, 'long');
  assert.equal(styles.beard, 'full');
  for (const k of ['head', 'nose', 'brow', 'ears']) assert.ok(styles[k], k);
});

test('a jaw beard removes the chin it would cover', () => {
  const { body, asm, rng } = fresh(1);
  addMan(asm, body, rng, { beard: 'short' });
  assert.ok(!asm.get('chin'));
  assert.ok(asm.get('beard_jaw'));
});

test('a long beard groups its cubes for the low-detail copy', () => {
  const { body, asm, rng } = fresh(1);
  addMan(asm, body, rng, { beard: 'long' });
  const group = asm.cubes.filter((c) => c.lodGroup === 'beard').map((c) => c.name).sort();
  assert.deepEqual(group, ['beard_chin', 'beard_jaw', 'beard_tip']);
});

test('trousers stay slim: no leg cube is wider than its thigh, legs are 3 px', () => {
  for (let s = 1; s <= 50; s++) {
    const { body, asm, rng } = fresh(s);
    addMan(asm, body, rng);
    assert.equal(body.legThick, 3, `seed ${s}`);
    for (const k of ['r', 'l']) {
      const thigh = asm.get(`thigh_${k}`);
      const side = k === 'r' ? 'right' : 'left';
      for (const c of asm.cubes.filter((x) => x.bone === `${side}_leg` || x.bone === `${side}_shin`)) {
        assert.ok(c.from[0] >= thigh.from[0] && c.to[0] <= thigh.to[0], `seed ${s}: ${c.name} is wider than the thigh`);
      }
    }
  }
});
