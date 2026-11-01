import test from 'node:test';
import assert from 'node:assert/strict';
import { ARMOUR, buildArmour, armourIndex, coversFor, ATTACH } from '../gear/armour.mjs';
import { buildModel } from '../lib/build.mjs';
import { largestBody, ANCHORS } from '../lib/body.mjs';
import { MAN } from '../races/man.mjs';
import { serializeLegacy } from '../lib/bbmodel.mjs';
import { Canvas } from '../vendor/bbmt/dist/index.js';

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
    const boxes = armour.model.cubes.filter((c) => c.bone === group)   // as rendered: inflate included
      .map((c) => ({ lo: c.from.map((v, a) => v + shift[a] - (c.inflate || 0)), hi: c.to.map((v, a) => v + shift[a] + (c.inflate || 0)) }));
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

test('Gondor v3 pieces: helmet, crest, nasal, wings, chest, arms, vambraces, tassets, cuisses, greaves', () => {
  const names = buildArmour('gondor').model.cubes.map((c) => c.name);
  for (const n of ['helmet', 'crest', 'nasal', 'wing_r', 'wing_l', 'chest', 'arm_r', 'arm_l', 'vambrace_r', 'vambrace_l',
    'tasset_r', 'tasset_l', 'cuisse_r', 'cuisse_l', 'greave_r', 'greave_l']) assert.ok(names.includes(n), n);
  assert.equal(buildArmour('gondor').model.cubes.find((c) => c.name === 'vambrace_r').bone, 'right_forearm_armor');
});

// Fitted armour: built from the cubes of one man, so it sits half a pixel outside them.
test('Gondor armour never pokes through, for 60 men and unusual hair and beards', () => {
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

// Coplanar overlapping faces flicker and blend in the renderer ("armour sinking into the skin").
function coplanar(man, armour) {
  const out = [], body = man.model.cubes;
  for (const a0 of armour.model.cubes) for (const b of body) {
    const i = a0.inflate || 0, a = { name: a0.name, from: a0.from.map((v) => v - i), to: a0.to.map((v) => v + i) };   // as rendered
    for (let ax = 0; ax < 3; ax++) for (const side of ['from', 'to']) {
      if (a[side][ax] !== b[side][ax]) continue;
      if (ax === 0 && a[side][ax] === 0) continue;   // the centre line between the legs, where both legs meet
      const o = [0, 1, 2].filter((k) => k !== ax);
      const overlap = o.every((k) => Math.min(a.to[k], b.to[k]) - Math.max(a.from[k], b.from[k]) > 0);
      if (overlap) out.push(`${a.name} and ${b.name} share a face (axis ${ax} ${side})`);
    }
  }
  return out;
}

test('Gondor armour never shares a face plane with the body', () => {
  for (const [s, o] of [[13, {}], [7, {}], [22, { hair: 'shoulder', beard: 'full' }], [24, { hair: 'bald', ears: 'large' }]]) {
    const man = buildModel('man', s, { palette: 'gondor', overrides: o });
    assert.deepEqual(coplanar(man, buildArmour('gondor', man)), [], `${s} ${JSON.stringify(o)}`);
  }
});

test('Gondor v3 is a few big pieces (Minecraft style): at most 18 cubes', () => {
  assert.ok(buildArmour('gondor').model.cubes.length <= 18, `${buildArmour('gondor').model.cubes.length} cubes`);
});

test('the helmet is one box with a transparent face window over the eyes', () => {
  const man = buildModel('man', 13, { palette: 'gondor' });
  const { model } = buildArmour('gondor', man);
  const helm = model.cubes.find((c) => c.name === 'helmet');
  const skull = man.model.findCube('skull');
  for (let a = 0; a < 3; a++) assert.ok(helm.from[a] < skull.from[a] && helm.to[a] > skull.to[a], `helmet encloses the head on axis ${a}`);
  const tex = Canvas.fromPng(model.textures[0].data);
  const [u1, v1, u2, v2] = helm.faces.north;
  const g = man.asm.meta.head, eyeY = g.rowY(g.eye)[0];
  const v = v1 + (helm.to[1] - eyeY - 1);            // texture row of the eye line
  const clear = [];
  for (let u = u1; u < u2; u++) if (tex.get(u, v)[3] === 0) clear.push(u);
  assert.ok(clear.length >= 4, `eye row has ${clear.length} clear pixels`);
  assert.equal(tex.get(u1, v)[3], 255, 'the cheek guard at the edge is solid');
});
