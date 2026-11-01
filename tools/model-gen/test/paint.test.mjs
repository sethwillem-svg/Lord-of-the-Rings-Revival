import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Model, Atlas } from '../vendor/bbmt/dist/index.js';
import { makeRng } from '../lib/rng.mjs';
import { solveBody, BONES } from '../lib/body.mjs';
import { MAN } from '../races/man.mjs';
import { Assembly } from '../lib/assembly.mjs';
import { resolvePalette, packUv, paintModel } from '../lib/paint.mjs';

const BASE = JSON.parse(readFileSync(new URL('../palettes/base.json', import.meta.url)));

function small(seed = 1) {
  const body = solveBody(MAN, makeRng(seed));
  const asm = new Assembly(body);
  asm.add({ name: 'tunic', bone: 'torso', from: [-4, 12, -2], to: [4, 25, 2], slot: 'tunic', style: 'cloth' });
  asm.add({ name: 'skull', bone: 'head', from: [-4, 25, -4], to: [4, 33, 4], slot: 'skin', style: 'skin',
    stamps: { north: { grid: ['.e..e.'], legend: { e: ['eye', 1] } } } });
  asm.add({ name: 'boot_r', bone: 'right_shin', from: [-4, 0, -3], to: [0, 4, 2], slot: 'boots', style: 'leather' });
  const model = new Model({ name: 'm', resolution: [128, 128] });
  for (const [n, parent] of BONES) model.bone(n, body.pivots[n], parent);
  for (const c of asm.cubes) model.cube({ name: c.name, bone: c.bone, from: c.from, to: c.to });
  const atlas = new Atlas(128, 128);
  packUv(model, atlas);
  paintModel(model, atlas, asm.cubes, resolvePalette(BASE, body.colours));
  return { model, atlas };
}
const lum = (c) => 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2];

test('uv windows never overlap and stay inside 128', () => {
  const { model } = small();
  const rects = model.cubes.flatMap((c) => Object.values(c.faces));
  for (const r of rects) for (const v of r) assert.ok(v >= 0 && v <= 128);
  for (let i = 0; i < rects.length; i++) for (let j = i + 1; j < rects.length; j++) {
    const [a, b] = [rects[i], rects[j]];
    const overlap = a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1];
    assert.ok(!overlap, `rects ${a} and ${b} overlap`);
  }
});

test('top of a side face is lighter than its bottom row', () => {
  const { model, atlas } = small();
  const [u1, v1, u2, v2] = model.findCube('tunic').faces.north;
  const row = (y) => { let s = 0; for (let x = u1; x < u2; x++) s += lum(atlas.canvas.get(x, y)); return s / (u2 - u1); };
  assert.ok(row(v1) > row(v2 - 1));
});

test('same input gives byte-identical PNG', () => {
  assert.deepEqual(Buffer.from(small().atlas.canvas.toPng()), Buffer.from(small().atlas.canvas.toPng()));
});

test('stamps draw in their slot colour', () => {
  const { model, atlas } = small();
  const [u1, v1] = model.findCube('skull').faces.north;
  const pal = resolvePalette(BASE, solveBody(MAN, makeRng(1)).colours);
  assert.deepEqual(atlas.canvas.get(u1 + 1, v1).slice(0, 3), pal.eye[1]);
});

test('sizes that are not whole pixels are rejected', () => {
  const asm = new Assembly(solveBody(MAN, makeRng(1)));
  assert.throws(() => asm.add({ name: 'x', bone: 'torso', from: [0, 0, 0], to: [1, 1.5, 1], slot: 'tunic' }), /whole pixels/);
});

test('half-pixel positions are allowed, quarter pixels are not', () => {
  const asm = new Assembly(solveBody(MAN, makeRng(1)));
  asm.add({ name: 'a', bone: 'torso', from: [-4.5, 0, 0], to: [4.5, 1, 1], slot: 'tunic' });
  assert.throws(() => asm.add({ name: 'b', bone: 'torso', from: [0.25, 0, 0], to: [1.25, 1, 1], slot: 'tunic' }), /half/);
});

test('anchors take no cubes and names are unique', () => {
  const asm = new Assembly(solveBody(MAN, makeRng(1)));
  assert.throws(() => asm.add({ name: 'h', bone: 'head_armor', from: [0, 0, 0], to: [1, 1, 1], slot: 'metal' }), /anchor/);
  asm.add({ name: 'n', bone: 'head', from: [0, 0, 0], to: [1, 1, 1], slot: 'skin' });
  assert.throws(() => asm.add({ name: 'n', bone: 'head', from: [0, 0, 0], to: [1, 1, 1], slot: 'skin' }), /already/);
  assert.throws(() => asm.add({ name: 'q', bone: 'tail', from: [0, 0, 0], to: [1, 1, 1], slot: 'skin' }), /bone/);
});

test('an unknown slot is rejected', () => {
  const asm = new Assembly(solveBody(MAN, makeRng(1)));
  assert.throws(() => asm.add({ name: 'z', bone: 'torso', from: [0, 0, 0], to: [1, 1, 1], slot: 'gold' }), /slot/);
});

test('plate style lights the top-left edges and darkens the bottom-right edges of a face', () => {
  const body = solveBody(MAN, makeRng(1));
  const asm = new Assembly(body);
  asm.add({ name: 'plate', bone: 'torso', from: [-4, 12, -2], to: [4, 22, 2], slot: 'metal', style: 'plate' });
  const model = new Model({ name: 'p', resolution: [128, 128] });
  for (const [n, parent] of BONES) model.bone(n, body.pivots[n], parent);
  model.cube({ name: 'plate', bone: 'torso', from: [-4, 12, -2], to: [4, 22, 2] });
  const atlas = new Atlas(128, 128);
  packUv(model, atlas);
  const pal = resolvePalette(BASE, body.colours);
  paintModel(model, atlas, asm.cubes, pal);
  const [u1, v1, u2, v2] = model.findCube('plate').faces.north;
  const at = (x, y) => atlas.canvas.get(x, y).slice(0, 3);
  assert.deepEqual(at(u1, v1 + 3), pal.metal[0], 'left edge lit');
  assert.deepEqual(at(u2 - 1, v1 + 3), pal.metal[3], 'right edge dark');
  assert.deepEqual(at(u1 + 3, v2 - 1), pal.metal[3], 'bottom edge dark');
  assert.deepEqual(at(u1 + 3, v1 + 4), pal.metal[1], 'inside is the base colour');
});

test('an edge colour rims only the bottom row, so short lames stay their own colour', () => {
  const body = solveBody(MAN, makeRng(1));
  const asm = new Assembly(body);
  asm.add({ name: 'lame', bone: 'torso', from: [-4, 12, -2], to: [4, 14, 2], slot: 'tunic', style: 'plate', edge: 'tunic_trim' });
  const model = new Model({ name: 'l', resolution: [128, 128] });
  for (const [n, parent] of BONES) model.bone(n, body.pivots[n], parent);
  model.cube({ name: 'lame', bone: 'torso', from: [-4, 12, -2], to: [4, 14, 2] });
  const atlas = new Atlas(128, 128);
  packUv(model, atlas);
  const pal = resolvePalette(BASE, body.colours);
  paintModel(model, atlas, asm.cubes, pal);
  const [u1, v1] = model.findCube('lame').faces.north;
  assert.deepEqual(atlas.canvas.get(u1 + 3, v1).slice(0, 3), pal.tunic[0], 'top row is the lame colour, lit');
  assert.deepEqual(atlas.canvas.get(u1 + 3, v1 + 1).slice(0, 3), pal.tunic_trim[1], 'bottom row is the rim colour');
});
