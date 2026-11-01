import test from 'node:test';
import assert from 'node:assert/strict';
import { WEAPONS, buildWeapon, weaponIndex } from '../gear/weapons.mjs';
import { serializeLegacy } from '../lib/bbmodel.mjs';

// spec table: faction -> weapons
const FACTION_WEAPONS = {
  rohan: ['rohan_sword', 'spear', 'short_bow', 'rohan_shield'],
  gondor: ['longsword', 'spear', 'longbow', 'gondor_shield'],
  harad: ['scimitar', 'harad_spear', 'harad_bow', 'harad_shield'],
  dunland: ['hand_axe', 'great_axe', 'club', 'javelin', 'hide_shield'],
  dale: ['longsword', 'spear', 'longbow'],
  dunedain: ['longsword', 'longbow'],
};
const LENGTH = { longsword: 20, rohan_sword: 18, scimitar: 16, spear: 40, harad_spear: 40, hand_axe: 11, great_axe: 22, club: 14,
  javelin: 30, longbow: 32, short_bow: 22, harad_bow: 24, rohan_shield: 13, gondor_shield: 21, harad_shield: 12, hide_shield: 12 };
const bounds = (m) => {
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
  for (const c of m.cubes) for (let a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], c.from[a]); hi[a] = Math.max(hi[a], c.to[a]); }
  return { lo, hi };
};

test('every faction has the weapons the spec lists', () => {
  for (const [f, ids] of Object.entries(FACTION_WEAPONS)) for (const id of ids) {
    assert.ok(WEAPONS[id], `${id} missing`);
    assert.ok(WEAPONS[id].factions.includes(f), `${id} not marked for ${f}`);
  }
});

test('each weapon has one root bone "weapon" pivoted at the grip, the origin', () => {
  for (const id of Object.keys(WEAPONS)) {
    const { model } = buildWeapon(id);
    assert.deepEqual(model.rig.names, ['weapon'], id);
    assert.deepEqual(model.rig.get('weapon').pivot, [0, 0, 0], id);
    assert.ok(model.cubes.some((c) => c.from.every((v, a) => v <= 0 && c.to[a] >= 0)), `${id}: no cube holds the grip`);
  }
});

test('lengths follow the spec and the striking end is +Y (shields: height)', () => {
  for (const id of Object.keys(WEAPONS)) {
    const { lo, hi } = bounds(buildWeapon(id).model);
    const kind = WEAPONS[id].kind;
    const len = kind === 'shield' ? Math.max(hi[1] - lo[1], hi[0] - lo[0]) : hi[1] - lo[1];
    assert.ok(Math.abs(len - LENGTH[id]) <= 1, `${id}: length ${len}, want ${LENGTH[id]}`);
    if (kind !== 'shield') assert.ok(hi[1] >= -lo[1], `${id}: more weapon below the grip than above`);
  }
});

test('shields face -Z and bows keep the string on +Z', () => {
  for (const id of Object.keys(WEAPONS)) {
    const w = WEAPONS[id], { lo, hi } = bounds(buildWeapon(id).model);
    if (w.kind === 'shield') assert.ok(-lo[2] > hi[2], `${id}: face should be on -Z`);
    if (w.kind === 'bow') assert.ok(hi[2] > -lo[2], `${id}: string should be on +Z`);
  }
});

test('main-hand weapons go to right_item, shields and bows to left_item', () => {
  for (const [id, w] of Object.entries(WEAPONS)) {
    assert.equal(w.hand, w.kind === 'shield' || w.kind === 'bow' ? 'left_item' : 'right_item', id);
  }
});

test('weapons pass the hidden-cube check and rebuild byte-identically', () => {
  for (const id of Object.keys(WEAPONS)) {
    const a = buildWeapon(id), b = buildWeapon(id);
    assert.ok(a.qa.ok, `${id}: ${a.qa.problems.join('; ')}`);
    assert.equal(serializeLegacy(a.model), serializeLegacy(b.model), id);
  }
});

test('the index lists every weapon with hand, kind, factions, length, grip and tip', () => {
  const idx = weaponIndex();
  assert.equal(idx.length, Object.keys(WEAPONS).length);
  for (const e of idx) {
    for (const k of ['id', 'name', 'hand', 'kind', 'factions', 'length', 'grip', 'tip']) assert.ok(e[k] !== undefined, `${e.id}.${k}`);
    assert.deepEqual(e.grip, [0, 0, 0]);
  }
});
