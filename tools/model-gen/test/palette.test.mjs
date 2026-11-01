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

// hair and beards may follow a faction's style weights; everything else (body, clothes) must not move, so faction
// men never grow past the base silhouette that armour is fitted to
const FACE_OR_HAIR = /^(hair_|beard_|chin$|tunic_lace$)/;
test('a faction keeps the body and clothes; only hair and beard picks may differ', () => {
  const shape = (m) => JSON.stringify([m.cubes.filter((c) => !FACE_OR_HAIR.test(c.name)).map((c) => [c.name, c.bone, c.from, c.to]),
    m.rig.all().map((b) => [b.name, b.pivot])]);
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

test('outfit options are valid', () => {
  const OK = { pattern: ['plain', 'stripes', 'plaid'], emblem: ['none', 'horse', 'tree', 'star', 'serpent'],
    sleeves: ['short', 'long'], boots: ['low', 'tall'], legs: ['trousers', 'wraps'], face: ['none', 'warpaint'] };
  for (const name of PALETTES) {
    const o = loadPalette(name).outfit || {};
    for (const [k, v] of Object.entries(o)) {
      if (k === 'hair' || k === 'beard') continue;
      assert.ok(OK[k] && OK[k].includes(v), `${name}.outfit.${k} = ${v}`);
    }
  }
});

const parts = (f, s) => buildModel('man', s, { palette: f });
test('long sleeves and tall boots repaint the forearm and shin, they do not add cubes', () => {
  const g = parts('gondor', 7).asm, h = parts('harad', 7).asm;
  assert.equal(g.get('forearm_r').slot, 'tunic');
  assert.equal(h.get('forearm_r').slot, 'skin');
  assert.equal(g.get('shin_l').slot, 'boots');
  assert.equal(h.get('shin_l').slot, 'trousers');
});

test('emblems, leg wraps and war paint are painted on', () => {
  const chest = (f) => parts(f, 7).asm.get('tunic').stamps.north.grid.join('');
  for (const f of ['rohan', 'gondor', 'harad']) assert.ok(chest(f).includes('x'), `${f} emblem`);
  assert.ok(!chest('dale').includes('x'), 'dale has no emblem');
  assert.ok(parts('dunland', 7).asm.get('shin_r').stamps, 'dunland leg wraps');
  assert.ok(parts('harad', 7).asm.get('skull').stamps.north.grid.join('').includes('p'), 'harad war paint');
  assert.ok(!parts('gondor', 7).asm.get('skull').stamps.north.grid.join('').includes('p'));
});

test('faction hair and beard weights are followed', () => {
  for (let s = 1; s <= 60; s++) {
    assert.notEqual(parts('rohan', s).styles.hair, 'bald', `rohan seed ${s}`);
    assert.ok(!['long', 'full'].includes(parts('gondor', s).styles.beard), `gondor seed ${s}`);
  }
});

test('patterns change the tunic paint', () => {
  const tex = (o) => Buffer.from(buildModel('man', 7, { palette: { ...loadPalette('dunland'), grime: 0, outfit: o } }).model.textures[0].data);
  assert.ok(!tex({ pattern: 'plain' }).equals(tex({ pattern: 'plaid' })));
  assert.ok(!tex({ pattern: 'plain' }).equals(tex({ pattern: 'stripes' })));
});
