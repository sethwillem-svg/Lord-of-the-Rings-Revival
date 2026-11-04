'use strict';
/* Weapon-in-hand checks. Race files: strikes in front, no spins, two-handed grips, the left-handed twins, no clipping.
 * Exported Middle-earth NPCs (addon/run/viewer_export, from ./addon/bench.sh export; skipped when absent): every real item
 * seated as the game and viewer seat it (gecko/held.js seatItem: drawn length to the class length, handle on the fist),
 * and shields facing forward (strapped to the forearm, gecko/shield.js; test/shield.test.js checks every shield clip without an
 * export). */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const { strike, blade, FIST } = require('../gecko/strike.js');
const { partToRig, quadsOf, drawnItem, seatItem, seatPoint, exportClass } = require('../gecko/held.js');
const { classLength } = require('../build_npc.js');
const SH = require('../gecko/shield.js');
const { RACES } = require('../npc_slots.js');

const { shipped: built } = require('./lib/shipped.js');   // the files the game ships
const ATTACK = /^attack_([a-z]+)_\d+$/;
const TWO_HANDED = new Set(['longsword', 'spear', 'axe']);
const EXPORT = path.join(__dirname, '../../../addon/run/viewer_export');
const GRIPS = JSON.parse(fs.readFileSync(path.join(__dirname, '../../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json'), 'utf8'));
const attacks = (race) => Object.keys(built[race].animations.animations).filter((n) => ATTACK.test(n));
const anim = (race, name) => built[race].animations.animations[name];

// ---- strikes ----
test('every strike lands in front of the NPC: tip at least 6 px ahead, within 45° of straight ahead, from knee height to just over the head', () => {
  const bad = [];
  for (const race of RACES) for (const name of attacks(race)) {
    const s = strike(anim(race, name), classLength(name));
    if (!(s.frontAngle <= 45 && s.tip[2] <= -6 && s.tip[1] >= 4 && s.tip[1] <= 50))
      bad.push(`${race} ${name}: ${s.frontAngle.toFixed(0)}° tip [${s.tip.map((x) => x.toFixed(0))}]`);
  }
  assert.deepEqual(bad, []);
});

test('the hit in hits.json is that strike moment', () => {
  for (const race of RACES) for (const name of attacks(race))
    assert.equal(built[race].hits[name].hit, strike(anim(race, name), classLength(name)).hit, `${race} ${name}`);
});

test('no spinning moves: the body turns at most 180° and the weapon sweeps at most 300° round the NPC', () => {
  const bad = [];
  for (const race of RACES) for (const name of attacks(race)) {
    const s = strike(anim(race, name), classLength(name));
    if (s.bodyYawRange > 180 || s.tipSweep > 300) bad.push(`${race} ${name}: body ${s.bodyYawRange.toFixed(0)}°, sweep ${s.tipSweep.toFixed(0)}°`);
  }
  assert.deepEqual(bad, []);
});

test('longer weapons reach further: spear tips land further out than swords, swords than daggers', () => {
  const reach = (cls) => Math.min(...attacks('man').filter((n) => n.split('_')[1] === cls).map((n) => -strike(anim('man', n), classLength(n)).tip[2]));
  assert.ok(reach('spear') > reach('sword') && reach('sword') > reach('dagger'), `${reach('spear')} ${reach('sword')} ${reach('dagger')}`);
});

// ---- grips ----
test('two-handed weapons keep the free hand on the grip at the strike (within 8 px of the shaft)', () => {
  const bad = [];
  for (const race of RACES) for (const name of attacks(race)) {
    if (!TWO_HANDED.has(name.split('_')[1])) continue;
    const s = strike(anim(race, name), classLength(name));
    if (s.leftToBlade > 8) bad.push(`${race} ${name}: ${s.leftToBlade.toFixed(1)} px`);
  }
  assert.deepEqual(bad, []);
});

test('left-handed twins trace the mirror image of the right-handed swing', () => {
  for (const race of ['man', 'orc']) for (const name of attacks(race)) {
    const R = anim(race, name), L = anim(race, name + '_l'), len = classLength(name);
    for (let t = 0; t <= R.animation_length; t += R.animation_length / 12) {
      const r = point(worldBones(samplePose(R, t)).weapon_r, blade('right', len, 1));
      const l = point(worldBones(samplePose(L, t)).weapon_l, blade('left', len, 1));
      assert.ok(Math.hypot(r[0] + l[0], r[1] - l[1], r[2] - l[2]) < 0.5, `${race} ${name} t=${t.toFixed(2)}: [${r.map((x) => x.toFixed(1))}] vs [${l.map((x) => x.toFixed(1))}]`);
    }
  }
});

// ---- clipping ----
/** Is a rig point inside a bone's rest box (rig space) once posed? */
function inside(w, box, p) {
  const v = [0, 1, 2].map((i) => p[i] - w.p[i]);
  const q = [0, 1, 2].map((c) => w.R[c] * v[0] + w.R[3 + c] * v[1] + w.R[6 + c] * v[2]);   // R^T v: back to rest
  return q.every((x, i) => x > box[0][i] + 1 && x < box[1][i] - 1);   // 1 px of skin: the clothing layer
}
const BODY = [[-4, 12, -2], [4, 24, 2]], HEAD = [[-4, 24, -4], [4, 32, 4]];

test('standing, walking and the stances keep the blade out of the body and head, and every clip keeps it above the floor', () => {
  const bad = [];
  for (const race of RACES) for (const [name, a] of Object.entries(built[race].animations.animations)) {
    const calm = /^(idle(_\d)?|walk|run|guard|march|shield_brace|spear_brace)$/.test(name);
    for (let t = 0; t <= a.animation_length + 1e-9; t += Math.max(0.05, a.animation_length / 20)) {
      const w = worldBones(samplePose(a, t));
      for (const f of [0.35, 0.65, 1]) {
        const p = point(w.weapon_r, blade('right', 1, f));
        if (p[1] < -1 && !/^(swim|climb|crouch_walk|taunt_\d)$/.test(name)) bad.push(`${race} ${name} t=${t.toFixed(2)} below the floor`);
        if (calm && (inside(w.body, BODY, p) || inside(w.head, HEAD, p))) bad.push(`${race} ${name} t=${t.toFixed(2)} inside the body`);
      }
    }
  }
  assert.deepEqual([...new Set(bad)], []);
});

// ---- real Middle-earth items (exported) ----
const exported = fs.existsSync(path.join(EXPORT, 'index.json'))
  ? JSON.parse(fs.readFileSync(path.join(EXPORT, 'index.json'), 'utf8')).npcs.map((n) => JSON.parse(fs.readFileSync(path.join(EXPORT, n.file), 'utf8')))
  : null;
const hands = () => exported.flatMap((d) => ['right_arm', 'left_arm'].map((part) => {
  const ms = d.meshes.filter((m) => m.hand && m.part === part);
  if (!ms.length) return null;
  const pts = [], quads = [];
  for (const m of ms) for (let i = 0; i < m.pos.length; i += 3) pts.push(partToRig(part, [m.pos[i], m.pos[i + 1], m.pos[i + 2]]));
  for (const m of ms) for (const q of quadsOf(m.pos, m.idx)) quads.push(q.map((p) => partToRig(part, p)));
  return { label: `${d.label} (${part})`, side: part === 'left_arm' ? 'left' : 'right', weapon: exportClass(ms[0].weapon, d.label), pts, quads };
}).filter(Boolean));
/** The drawn weapon's points (gecko/held.js drawnItem), placed as the viewer places them. */
const SEATED = (h) => {
  const seat = seatItem(h.weapon, GRIPS, h.quads, FIST[h.side]);
  return drawnItem(h.quads).pts.map((p) => seatPoint(seat, p));
};
const dedupe = (pts) => [...new Map(pts.map((p) => [p.map((x) => x.toFixed(4)).join(), p])).values()];
/** The farthest pair's distance, every pair (the measure's own two-pass search only approximates it). */
const diameter = (pts) => { let d = 0; for (const a of pts) for (const b of pts) d = Math.max(d, Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2])); return d; };

/** The hand: the bottom of the arm (part space x -3..1, y 7..12, z -2.5..2.5 for the right arm) in rig space. */
const HAND = { right: [[4, 10, -2.5], [8, 15, 2.5]], left: [[-8, 10, -2.5], [-4, 15, 2.5]] };
const toBox = (b, p) => Math.hypot(...[0, 1, 2].map((i) => Math.max(b[0][i] - p[i], 0, p[i] - b[1][i])));

test('real items: the handle is in the hand (the drawn weapon within 2.5 px of it once seated)', { skip: !exported && 'no export (./addon/bench.sh export)' }, () => {
  const bad = [];
  for (const h of hands()) {
    if (!GRIPS[h.weapon]) continue;
    const d = Math.min(...SEATED(h).map((p) => toBox(HAND[h.side], p)));
    if (d > 2.5) bad.push(`${h.label} ${h.weapon}: ${d.toFixed(1)} px from the hand`);
  }
  assert.deepEqual(bad, []);
});

test('real items: the drawn weapon is its class length once seated (±5%, pommel to tip)', { skip: !exported && 'no export (./addon/bench.sh export)' }, () => {
  const bad = [];
  for (const h of hands()) {
    if (!GRIPS[h.weapon]) continue;
    const len = diameter(dedupe(SEATED(h))) / 16;
    if (Math.abs(len / GRIPS[h.weapon].length - 1) > 0.05) bad.push(`${h.label}: ${len.toFixed(2)} blocks for ${h.weapon} (${GRIPS[h.weapon].length})`);
  }
  assert.deepEqual(bad, []);
});

test('real shields face forward when braced: their face points along the facing within 35°', { skip: !exported && 'no export (./addon/bench.sh export)' }, () => {
  const bad = [];
  for (const h of hands().filter((x) => x.weapon === 'shield')) {
    const a = anim('man', 'shield_brace'), w = worldBones(samplePose(a, 0))[h.side === 'left' ? 'weapon_l' : 'weapon_r'];
    const seat = SH.seat(h.quads, h.side);   // strapped to the forearm as the game and viewer strap it
    const P = h.pts.map((p) => point(w, seatPoint(seat, p)));
    // the face normal: the direction of least spread (smallest extent of the three world axes is enough for a flat plate)
    const ext = [0, 1, 2].map((i) => Math.max(...P.map((p) => p[i])) - Math.min(...P.map((p) => p[i])));
    if (ext[2] > Math.min(ext[0], ext[1]) * Math.tan((35 * Math.PI) / 180) * 2 + 2) bad.push(`${h.label}: extents [${ext.map((x) => x.toFixed(1))}]`);
  }
  assert.deepEqual(bad, []);
});
