import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, readdirSync, existsSync } from 'node:fs';
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
