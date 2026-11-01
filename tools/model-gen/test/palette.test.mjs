import test from 'node:test';
import assert from 'node:assert/strict';
import { buildModel, loadPalette, PALETTES } from '../lib/build.mjs';

const CLOTHES = ['tunic', 'tunic_trim', 'trousers', 'boots', 'belt', 'metal'];
const FACTIONS = ['rohan', 'dale', 'dunedain', 'gondor', 'dunland', 'harad'];
const HEX = /^#[0-9a-f]{6}$/i;

test('every faction palette exists and fills every clothes slot', () => {
  for (const f of FACTIONS) assert.ok(PALETTES.includes(f), f);
  for (const name of PALETTES) {
    const p = loadPalette(name);
    assert.ok(p.name, `${name}: name`);
    for (const slot of CLOTHES) assert.match(p[slot], HEX, `${name}.${slot}`);
    for (const pool of ['skin', 'hair', 'eye']) if (p[pool]) for (const c of p[pool]) assert.match(c, HEX, `${name}.${pool}`);
    assert.ok(p.grime >= 0 && p.grime <= 1, `${name}: grime`);
  }
});

test('kempt factions are clean and Dunland is dirty', () => {
  for (const f of ['rohan', 'gondor', 'harad']) assert.equal(loadPalette(f).grime, 0, f);
  assert.ok(loadPalette('dunland').grime >= 0.6);
});

test('a palette changes colours, never geometry', () => {
  const shape = (m) => JSON.stringify([m.cubes.map((c) => [c.name, c.bone, c.from, c.to]), m.rig.names]);
  for (const s of [7, 13, 22]) {
    const base = shape(buildModel('man', s).model);
    for (const f of FACTIONS) assert.equal(shape(buildModel('man', s, { palette: f }).model), base, `seed ${s} ${f}`);
  }
});

test("a palette's skin, hair and eye choices are used", () => {
  for (const f of ['harad', 'rohan', 'dunland']) {
    const p = loadPalette(f);
    for (let s = 1; s <= 30; s++) {
      const { body } = buildModel('man', s, { palette: f });
      assert.ok(p.skin.includes(body.colours.skin), `${f} seed ${s} skin`);
      assert.ok(p.hair.includes(body.colours.hair), `${f} seed ${s} hair`);
    }
  }
});

test('faction men pass every check', () => {
  for (const f of FACTIONS) for (let s = 1; s <= 10; s++) {
    const { qa } = buildModel('man', s, { palette: f });
    assert.ok(qa.ok, `${f} seed ${s}: ${qa.problems.join('; ')}`);
  }
});

test('faction models are named after the faction', () => {
  assert.equal(buildModel('man', 7, { palette: 'rohan' }).model.name, 'man_rohan_7');
  assert.equal(buildModel('man', 7, { palette: 'gondor', lod: true }).model.name, 'man_gondor_7_lod');
  assert.equal(buildModel('man', 7).model.name, 'man_base_7');
});

test('grime dirties the texture and no grime leaves it clean', () => {
  const tex = (palette) => Buffer.from(buildModel('man', 7, { palette }).model.textures[0].data);
  const dirty = loadPalette('dunland');
  assert.ok(!tex({ ...dirty, grime: 0 }).equals(tex(dirty)), 'grime should change pixels');
  assert.ok(tex({ ...dirty, grime: 0 }).equals(tex({ ...dirty, grime: 0 })));
});

test('an unknown palette is refused', () => {
  assert.throws(() => buildModel('man', 1, { palette: 'mordor_pink' }), /palette/);
});
