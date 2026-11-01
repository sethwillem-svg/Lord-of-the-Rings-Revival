import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import path from 'node:path';

const CLI = fileURLToPath(new URL('../build.mjs', import.meta.url));
const run = (args, opts = {}) => spawnSync(process.execPath, [CLI, ...args], { encoding: 'utf8', ...opts });
const tmp = () => mkdtempSync(path.join(os.tmpdir(), 'modelgen-'));
const files = (dir) => readdirSync(dir, { recursive: true }).filter((f) => /\.(bbmodel|png)$/.test(f)).sort();

test('writes models, lod copies and a preview', () => {
  const out = tmp();
  const r = run(['man', '--seeds', '1-2', '--out', out]);
  assert.equal(r.status, 0, r.stderr);
  for (const f of ['man_base_1.bbmodel', 'man_base_2.bbmodel', 'lod/man_base_1_lod.bbmodel', 'lod/man_base_2_lod.bbmodel', 'preview.png']) {
    assert.ok(existsSync(path.join(out, f)), f);
  }
  const doc = JSON.parse(readFileSync(path.join(out, 'man_base_1.bbmodel'), 'utf8'));
  assert.equal(doc.meta.format_version, '4.10');
});

test('two builds are byte-identical', () => {
  const [a, b] = [tmp(), tmp()];
  run(['man', '--seeds', '1-2', '--out', a]);
  run(['man', '--seeds', '1-2', '--out', b]);
  assert.ok(files(a).length >= 5, 'expected output files');
  assert.deepEqual(files(a), files(b));
  for (const f of files(a)) assert.ok(readFileSync(path.join(a, f)).equals(readFileSync(path.join(b, f))), f);
});

test('runs from another working directory', () => {
  const out = tmp();
  const r = run(['man', '--seeds', '3', '--out', out], { cwd: os.tmpdir() });
  assert.equal(r.status, 0, r.stderr);
  assert.ok(existsSync(path.join(out, 'man_base_3.bbmodel')));
});

test('seed list form works', () => {
  const out = tmp();
  run(['man', '--seeds', '4,9', '--out', out]);
  assert.deepEqual(files(out).filter((f) => !f.includes('/') && f.endsWith('.bbmodel')), ['man_base_4.bbmodel', 'man_base_9.bbmodel']);
});

test('bad arguments explain themselves', () => {
  const r = run(['ent', '--seeds', '1', '--out', tmp()]);
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /race/);
  const r2 = run(['man', '--seeds', 'x-y', '--out', tmp()]);
  assert.notEqual(r2.status, 0);
  assert.match(r2.stderr, /seeds/);
});

test('without --seeds it builds the showcase seeds', () => {
  const out = tmp();
  const r = run(['man', '--out', out]);
  assert.equal(r.status, 0, r.stderr);
  const built = files(out).filter((f) => !f.includes('/') && f.endsWith('.bbmodel')).length;
  assert.equal(built, 8);
});

test('output does not depend on the system locale', () => {
  const [a, b] = [tmp(), tmp()];
  run(['man', '--seeds', '7', '--out', a], { env: { ...process.env, LC_ALL: 'C', LANG: 'C' } });
  run(['man', '--seeds', '7', '--out', b], { env: { ...process.env, LC_ALL: 'cs_CZ.UTF-8', LANG: 'cs_CZ.UTF-8' } });
  assert.ok(readFileSync(path.join(a, 'man_base_7.bbmodel')).equals(readFileSync(path.join(b, 'man_base_7.bbmodel'))));
});

test('--palette writes faction men named after the faction', () => {
  const out = tmp();
  const r = run(['man', '--seeds', '7', '--palette', 'rohan', '--out', out]);
  assert.equal(r.status, 0, r.stderr);
  for (const f of ['man_rohan_7.bbmodel', 'lod/man_rohan_7_lod.bbmodel', 'preview.png']) assert.ok(existsSync(path.join(out, f)), f);
});

test('--palette all writes one folder per faction', () => {
  const out = tmp();
  const r = run(['man', '--seeds', '7', '--palette', 'all', '--out', out]);
  assert.equal(r.status, 0, r.stderr);
  for (const f of ['rohan', 'dale', 'dunedain', 'gondor', 'dunland', 'harad']) {
    assert.ok(existsSync(path.join(out, f, `man_${f}_7.bbmodel`)), f);
    assert.ok(existsSync(path.join(out, f, 'preview.png')), `${f} preview`);
  }
  assert.ok(!existsSync(path.join(out, 'base')), 'base is not a faction');
});

test('an unknown palette explains itself', () => {
  const r = run(['man', '--seeds', '1', '--palette', 'mordor_pink', '--out', tmp()]);
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /unknown palette/);
});

test('gear builds weapons, armour sets, an index and a preview', () => {
  const out = tmp();
  const r = run(['gear', '--out', out]);
  assert.equal(r.status, 0, r.stderr);
  const idx = JSON.parse(readFileSync(path.join(out, 'index.json'), 'utf8'));
  assert.equal(idx.weapons.length, 16);
  assert.equal(idx.armour.length, 6);
  for (const w of idx.weapons) assert.ok(existsSync(path.join(out, w.file)), w.file);
  for (const a of idx.armour) assert.ok(existsSync(path.join(out, a.file)), a.file);
  assert.ok(existsSync(path.join(out, 'preview.png')));
  assert.equal(idx.weapons.find((w) => w.id === 'longsword').file, 'weapons/longsword.bbmodel');
  assert.equal(idx.armour.find((a) => a.id === 'gondor').file, 'armour/gondor.bbmodel');
  // fitted Gondor armour for every committed man: base and faction men, showcase seeds
  const g = idx.armour.find((a) => a.id === 'gondor');
  assert.equal(g.fitted, 'armour/gondor/');
  for (const name of ['man_base_13', 'man_gondor_13', 'man_rohan_37']) assert.ok(existsSync(path.join(out, 'armour/gondor', `${name}.bbmodel`)), name);
  assert.equal(idx.armour.find((a) => a.id === 'rohan').fitted, undefined);
});

test('materials renders a swatch sheet of every recipe', () => {
  const out = path.join(tmp(), 'materials.png');
  const r = run(['materials', '--out', out]);
  assert.equal(r.status, 0, r.stderr);
  assert.ok(existsSync(out));
  assert.match(r.stdout, /steel_plate/);
});

test('a broken material recipe fails with its problems', () => {
  const dir = tmp();
  writeFileSync(path.join(dir, 'broken.json'), JSON.stringify({ kind: 'glass', colour: 'metal' }));
  const r = run(['materials', '--materials', dir, '--out', path.join(tmp(), 'm.png')]);
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /broken: kind must be/);
});

test('--materials renders another recipe set', () => {
  const dir = tmp(), out = path.join(tmp(), 'alt.png');
  writeFileSync(path.join(dir, 'blue_steel.json'), JSON.stringify({ kind: 'plate', colour: '#5a6a8a', bands: 3, polish: 0.4 }));
  const r = run(['materials', '--materials', dir, '--out', out]);
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /blue_steel/);
  assert.ok(existsSync(out));
});
