import test from 'node:test';
import assert from 'node:assert/strict';
import { Canvas } from '../vendor/bbmt/dist/index.js';
import { MATERIALS, validateMaterial, paintSurface, tones } from '../lib/surface.mjs';
import { buildArmour } from '../gear/armour.mjs';

const lum = (c) => 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2];
const PAL = { metal: tones('#b3b6b9'), trim: tones('#c4c8cc'), cloth: tones('#1f2226'), accent: tones('#b8963e') };
const paint = (recipe, w = 8, h = 12, face = 'north') => {
  const c = new Canvas(w, h, [0, 0, 0, 0]);
  paintSurface(c, [0, 0, w, h], face, recipe, (slot) => PAL[slot] || tones(slot), 'swatch');
  return (x, y) => c.get(x, y).slice(0, 3);
};

test('every material recipe in materials/ is valid', () => {
  assert.ok(Object.keys(MATERIALS).length >= 5);
  for (const [id, m] of Object.entries(MATERIALS)) assert.deepEqual(validateMaterial(m), [], id);
});

test('plate bands darken down a side face', () => {
  const at = paint({ kind: 'plate', colour: 'metal', bands: 3, bevel: false });
  const row = (y) => [2, 3, 4, 5].reduce((s, x) => s + lum(at(x, y)), 0);
  assert.ok(row(1) > row(6) && row(6) > row(10));
});

test('the polish streak is brighter than its neighbours', () => {
  const at = paint({ kind: 'plate', colour: 'metal', bands: 1, polish: 0.3, bevel: true });
  const px = Math.round(0.3 * 7);
  assert.ok(lum(at(px, 5)) > lum(at(px + 2, 5)));
});

test('rivets are a lit dot with a shadow under it', () => {
  const at = paint({ kind: 'plate', colour: 'metal', bands: 1, rivets: { spacing: 3, rows: 'top' } });
  assert.deepEqual(at(2, 1), PAL.metal[0]);
  assert.ok(lum(at(2, 2)) < lum(at(3, 2)));
});

test('a raised border is painted in its own colour, one pixel in from the edge', () => {
  const at = paint({ kind: 'plate', colour: 'cloth', bands: 1, border: 'trim', bevel: true });
  assert.ok(PAL.trim.some((t) => t.join() === at(1, 5).join()), 'border pixel uses the trim tones');
  assert.ok(PAL.cloth.some((t) => t.join() === at(4, 5).join()), 'inside stays the plate colour');
});

test('a material rejects unknown kinds and bad numbers', () => {
  assert.ok(validateMaterial({ kind: 'glass', colour: 'metal' }).length > 0);
  assert.ok(validateMaterial({ kind: 'plate', colour: 'metal', bands: 9 }).length > 0);
  assert.ok(validateMaterial({ kind: 'plate', colour: 'metal', scratches: 2 }).length > 0);
});

test('painting is deterministic', () => {
  const a = paint(MATERIALS.steel_plate), b = paint(MATERIALS.steel_plate);
  for (let y = 0; y < 12; y++) for (let x = 0; x < 8; x++) assert.deepEqual(a(x, y), b(x, y));
});

test("Gondor's armour pieces use materials", () => {
  const cubes = buildArmour('gondor').model.cubes.map((c) => c.name);
  assert.ok(cubes.length > 20);
  const { materialOf } = buildArmour('gondor');
  for (const n of ['helm', 'breastplate', 'fauld_1', 'belt', 'rerebrace_r', 'greave_l']) assert.ok(materialOf[n], n);
});
