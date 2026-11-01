import test from 'node:test';
import assert from 'node:assert/strict';
import { ARMOUR, buildArmour, armourIndex, coversFor, ATTACH } from '../gear/armour.mjs';
import { buildModel } from '../lib/build.mjs';
import { largestBody, ANCHORS } from '../lib/body.mjs';
import { MAN } from '../races/man.mjs';
import { serializeLegacy } from '../lib/bbmodel.mjs';

const FACTIONS = ['rohan', 'gondor', 'harad', 'dunland', 'dale', 'dunedain'];

test('every faction has an armour set with a helm and body armour', () => {
  for (const f of FACTIONS) {
    const { model } = buildArmour(f);
    assert.ok(model.cubes.some((c) => c.bone === 'head_armor'), `${f}: helm`);
    assert.ok(model.cubes.some((c) => c.bone === 'chest_armor'), `${f}: body armour`);
  }
  assert.ok(buildArmour('dunedain').model.cubes.some((c) => c.bone === 'cape'), 'dunedain cloak');
});

test("armour groups are anchor bones pivoted where the largest man's anchors are", () => {
  const ref = largestBody(MAN).pivots;
  for (const f of FACTIONS) {
    const { model } = buildArmour(f);
    for (const b of model.rig.all()) {
      // anchor bones, or extra groups the viewer attaches to another bone (forearm armour -> forearm)
      assert.ok(ANCHORS.has(b.name) || ATTACH[b.name], `${f}: ${b.name} is not an anchor`);
      assert.deepEqual(b.pivot, ref[ATTACH[b.name] || b.name], `${f}: ${b.name} pivot`);
    }
  }
});

test('armour sets pass the hidden-cube check and rebuild byte-identically', () => {
  for (const f of FACTIONS) {
    const a = buildArmour(f);
    assert.ok(a.qa.ok, `${f}: ${a.qa.problems.join('; ')}`);
    assert.equal(serializeLegacy(a.model), serializeLegacy(buildArmour(f).model), f);
  }
});

// At rest, no main body cube pokes through the armour that covers it: for every half-pixel height the body cube
// and the armour share, one armour box must contain the body cube's x and z extents with a margin.
function poking(man, armour, faction, ref = largestBody(MAN).pivots) {
  const out = [], P = man.body.pivots;
  for (const [group, covered] of Object.entries(coversFor(faction))) {
    const target = ATTACH[group] || group;
    const shift = P[target].map((v, a) => v - ref[target][a]);
    const boxes = armour.model.cubes.filter((c) => c.bone === group)
      .map((c) => ({ lo: c.from.map((v, a) => v + shift[a]), hi: c.to.map((v, a) => v + shift[a]) }));
    if (!boxes.length) continue;
    const gy = [Math.min(...boxes.map((b) => b.lo[1])), Math.max(...boxes.map((b) => b.hi[1]))];
    for (const { name, clipX, clipY } of covered) {
      const c = man.model.findCube(name);
      if (!c) continue;
      // clipX is absolute (the body's centre line splits the skirt between the legs); clipY is relative to the anchor.
      // Cut edges are not faces, so they need no margin.
      const x0 = clipX ? Math.max(c.from[0], clipX[0]) : c.from[0], x1 = clipX ? Math.min(c.to[0], clipX[1]) : c.to[0];
      const m0 = clipX && clipX[0] >= c.from[0] ? 0 : 0.25, m1 = clipX && clipX[1] <= c.to[0] ? 0 : 0.25;
      const yc = clipY ? [P[target][1] + clipY[0], P[target][1] + clipY[1]] : [-Infinity, Infinity];
      const ylo = Math.max(c.from[1], gy[0], yc[0]), yhi = Math.min(c.to[1], gy[1], yc[1]);
      for (let y = ylo + 0.25; y < yhi; y += 0.5) {
        const ok = boxes.some((b) => b.lo[1] <= y && b.hi[1] >= y && b.lo[0] <= x0 - m0 && b.hi[0] >= x1 + m1
          && b.lo[2] <= c.from[2] - 0.25 && b.hi[2] >= c.to[2] + 0.25);
        if (!ok) { out.push(`${name} at y ${y} pokes through ${group}`); break; }
      }
    }
  }
  return out;
}

test('no main body cube pokes through the armour at rest, for men of every size', () => {
  const sets = Object.fromEntries(FACTIONS.map((f) => [f, buildArmour(f)]));
  for (let s = 1; s <= 60; s++) {
    for (const f of FACTIONS) {
      const man = buildModel('man', s, { palette: f });
      const bad = poking(man, sets[f], f);
      assert.deepEqual(bad, [], `seed ${s} ${f}`);
    }
  }
});

test('the index lists every set with its pieces', () => {
  const idx = armourIndex();
  assert.deepEqual(idx.map((e) => e.id).sort(), [...FACTIONS].sort());
  for (const e of idx) assert.ok(e.pieces.includes('head_armor') && e.pieces.includes('chest_armor'), e.id);
});

test('Gondor plate: shells with lames, vambraces on the forearms, knee cops, crest and wings', () => {
  const names = buildArmour('gondor').model.cubes.map((c) => c.name);
  for (const n of ['crest', 'wing_r', 'wing_l', 'breastplate', 'fauld_1', 'pauldron_r', 'pauldron_lame_r', 'vambrace_r', 'vambrace_l',
    'tasset_r', 'cuisse_r', 'knee_cop_r', 'greave_r']) assert.ok(names.includes(n), n);
  assert.equal(ATTACH.right_forearm_armor, 'right_forearm');
  const v = buildArmour('gondor').model.cubes.find((c) => c.name === 'vambrace_r');
  assert.equal(v.bone, 'right_forearm_armor');
});

// Fitted armour: built from the cubes of one man, so it sits half a pixel outside them.
test('fitted Gondor armour never pokes through, for 60 men and unusual hair and beards', () => {
  const men = [];
  for (let s = 1; s <= 60; s++) men.push(buildModel('man', s, { palette: 'gondor' }));
  for (const o of [{ hair: 'tied', beard: 'long' }, { hair: 'bald', beard: 'none', ears: 'large' }, { hair: 'shoulder', head: 'broad' },
    { hair: 'side_part', head: 'long', nose: 'hooked' }]) men.push(buildModel('man', 7, { palette: 'gondor', overrides: o }));
  for (const man of men) {
    const armour = buildArmour('gondor', man);
    assert.ok(armour.qa.ok, `${man.model.name}: ${armour.qa.problems.join('; ')}`);
    assert.deepEqual(poking(man, armour, 'gondor', man.body.pivots), [], man.model.name);
  }
});

test('the fitted helm sits half a pixel outside the head, hair and ears (1 px above)', () => {
  for (const [s, o] of [[13, {}], [22, {}], [7, { hair: 'bald' }], [8, { hair: 'shoulder', head: 'broad', ears: 'large' }]]) {
    const man = buildModel('man', s, { palette: 'gondor', overrides: o });
    const head = man.model.cubes.filter((c) => c.bone === 'head' && /^(skull|hair_(cap|back|side_r|side_l|fringe)|ear_[rl])$/.test(c.name));
    const lo = [0, 1, 2].map((a) => Math.min(...head.map((c) => c.from[a]))), hi = [0, 1, 2].map((a) => Math.max(...head.map((c) => c.to[a])));
    const crown = buildArmour('gondor', man).model.cubes.find((c) => c.name === 'helm');
    assert.deepEqual([crown.from[0], crown.to[0]], [lo[0] - 0.5, hi[0] + 0.5], `${s} width`);
    assert.deepEqual([crown.from[2], crown.to[2]], [lo[2] - 0.5, hi[2] + 0.5], `${s} depth`);
    assert.equal(crown.to[1], hi[1] + 1, `${s} top`);   // heights are whole pixels: 1 px above the head
  }
});
