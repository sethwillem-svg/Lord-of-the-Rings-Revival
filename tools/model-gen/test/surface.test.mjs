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

// Metal follows the Minecraft texturing guides: hue-shifted ramp, reflection clusters (sky zone, dark horizon band,
// highlight streak with a specular glint, counter-reflection by the shadow edge), front/top brighter than back/bottom.
test('ramps are hue-shifted: cool shadows, warm highlights', () => {
  const t = tones('#9aa3ad');
  const warmth = (c) => c[0] - c[2];
  assert.ok(warmth(t[0]) > warmth(t[4]), 'highlight warmer than shadow');
  assert.ok(lum(t[0]) - lum(t[5]) > 150, 'contrasty: specular to deep spans a wide range');
});

test('metal has a light sky zone above a dark horizon band', () => {
  const at = paint({ kind: 'metal', colour: 'metal', polish: null }, 8, 12);
  const row = (y) => [2, 3, 4, 5].reduce((s, x) => s + lum(at(x, y)), 0);
  const horizon = Math.round(0.6 * 11);
  assert.ok(row(2) > row(horizon) + 40, 'sky brighter than horizon');
  assert.ok(row(horizon + 2) > row(horizon), 'ground reflection lighter than the horizon band');
});

test('the polish streak is brighter than its neighbours, with one specular glint', () => {
  const at = paint({ kind: 'metal', colour: 'metal', polish: 0.25 }, 8, 12);
  const sx = Math.round(0.25 * 7);
  assert.ok(lum(at(sx, 8)) > lum(at(sx + 3, 8)));
  const glints = [];
  for (let y = 0; y < 12; y++) for (let x = 0; x < 8; x++) if (at(x, y).join() === PAL.metal[0].join()) glints.push([x, y]);
  assert.equal(glints.length, 1, 'exactly one specular pixel');
});

test('faces are lit by direction: top brightest, front brighter than back, bottom darkest', () => {
  const mean = (face) => { const at = paint({ kind: 'metal', colour: 'metal', polish: null }, 8, 12, face);
    let s = 0; for (let y = 0; y < 12; y++) for (let x = 0; x < 8; x++) s += lum(at(x, y)); return s; };
  assert.ok(mean('up') > mean('north'));
  assert.ok(mean('north') > mean('south'));
  assert.ok(mean('south') > mean('down'));
});

test('no lone noise pixels: metal without rivets or engraving is made of clusters', () => {
  const at = paint({ kind: 'metal', colour: 'metal', polish: null }, 8, 12);
  for (let y = 1; y < 11; y++) for (let x = 1; x < 7; x++) {
    const c = at(x, y).join();
    if (c === PAL.metal[0].join()) continue;   // the specular glint is the one deliberate single pixel
    const same = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => at(x + dx, y + dy).join() === c);
    assert.ok(same, `lone pixel at ${x},${y}`);
  }
});

test('rivets are a lit dot with a shadow under it', () => {
  const at = paint({ kind: 'metal', colour: 'metal', polish: null, rivets: { spacing: 3, rows: 'top' } });
  assert.ok(lum(at(2, 1)) > lum(at(3, 1)), 'rivet head lighter than the plate');
  assert.ok(lum(at(2, 2)) < lum(at(3, 2)), 'shadow under the rivet');
});

test('a raised border is painted in its own colour, one pixel in from the edge', () => {
  const at = paint({ kind: 'metal', colour: 'cloth', polish: null, border: 'trim' });
  assert.ok(PAL.trim.some((t) => t.join() === at(1, 5).join()), 'border pixel uses the trim tones');
  assert.ok(PAL.cloth.some((t) => t.join() === at(4, 5).join()), 'inside stays the plate colour');
});

test('a material rejects unknown kinds and bad numbers', () => {
  assert.ok(validateMaterial({ kind: 'glass', colour: 'metal' }).length > 0);
  assert.ok(validateMaterial({ kind: 'metal', colour: 'metal', polish: 3 }).length > 0);
  assert.ok(validateMaterial({ kind: 'metal', colour: 'metal', bands: 3 }).length > 0, 'bands are gone: they caused banding');
  assert.ok(validateMaterial({ kind: 'metal', colour: 'metal', scratches: 0.1 }).length > 0, 'scratches are gone: they were noise');
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
