'use strict';
/* Attack tempo, judged as a Japanese key animator times a blow (specs/addon/npc-attack-tempo.md, T1–T9; research notes in
 * docs/reference/anime-timing.md): kime (the wind-up apex, the hit and the end each a pose that reads), tame (a held beat
 * at the apex), tsume (time bunched at the extremes: slow into the apex, a burst to the hit, a fast leave and a slow
 * settle), meri-hari (slow/fast contrast) and Yoshida's weight (a heavy blow settles long). Measured on the shipped files
 * with test/lib/motion.js (60 Hz and every key; samples are not evenly spaced, so every measure weighs a sample by the time
 * it covers), never with the build's own helpers. Times come from hits.json: lead (end of the lead-in), apex, tame, hit,
 * length and ready; the swing runs from lead to the end, length − ready (the ease back to the ready stance is not swing).
 * Unarmed blows are thrown on man's tempo by every race (spec, edge cases), so they are held to man's limits.
 * T8 (race tempo order on the wind-up time) is not repeated here: fighting.test.js checks it on hit − lead
 * ("dwarves ... take longer than a man's", "elves swing swiftly", "hobbits ... a quicker wind-up").
 * T4 and T7 only judge attacks with a wind-up (apex − lead) of 0.1 s or more: a share of a near-zero phase is noise.
 * T1, T2, T3, T5, T6, T7 and T9 are parked as ratchets (test/lib/known.js): the attacks known to fail each are listed, any
 * other failure fails the check, and so does a listed attack that now passes. */
const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('./lib/motion.js');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const { RACES, STYLE } = require('../npc_slots.js');
const { shipped } = require('./lib/shipped.js');
const { FIST, baseName, tipRest, tipOf, report, pathIn, within, peak, median } = require('./lib/swing.js');
const { pairs } = require('./lib/pairs.js');
const { ratchet } = require('./lib/known.js');

const clips = [];   // every attack and its twins: { race, name, anim, fs, h, tip }
for (const race of RACES) for (const [name, anim] of Object.entries(shipped[race].animations.animations)) {
  if (!/^attack_/.test(name)) continue;
  const c = { race, name, anim, fs: M.frames(anim, M.RATE, `${race}/${name}`), h: shipped[race].hits[baseName(name)] };
  c.tip = tipOf(c);
  clips.push(c);
}
const twin = (c) => /_(l|shield)$/.test(c.name);
const plain = clips.filter((c) => !twin(c));
/** Whose tempo limits an attack answers to: its race's, or man's for an unarmed blow. */
const tempoRace = (c) => (/^attack_unarmed_/.test(c.name) ? 'man' : c.race);
const f2 = (x) => x.toFixed(2);
/** A failure of one attack, keyed for the ratchet lists (test/lib/known.js). */
const fail = (c, what) => ({ key: `${c.race}/${c.name}`, msg: `${c.race} ${c.name}: ${what}` });

/** The swing's phase times (s): lead-in end, apex, end of the tame, hit, end of the strike, end of the recovery. */
function phases(c) {
  const h = c.h, lead = h.lead || 0, end = h.length - (h.ready || 0);
  return { lead, apex: h.apex, tameEnd: h.apex + (h.tame || 0), hit: h.hit, strikeEnd: Math.min(end, h.hit + 0.05), end };
}
/** The tip's velocities (px/s), sample by sample. */
const tipV = (c) => c.tip.v;
/** The wind-up is long enough to share out (T4, T7). */
const windsUp = (c) => { const p = phases(c); return p.apex - p.lead >= 0.1 - 1e-9; };

// ---- the measures (each returns the value the test judges) ----
/** T1: the longest stretch in [lead, hit] with the tip at most 10% of the swing's peak speed (s). */
function dwell(c) {
  const p = phases(c), v = tipV(c), top = peak(v, p.lead, p.end);
  let run = 0, best = 0;
  for (const x of within(v, p.lead, p.hit)) { run = x.speed <= 0.1 * top ? run + x.in : 0; best = Math.max(best, run); }
  return best;
}
/** T2: the tip's position at a time (rig px). */
const tipAt = (c, t) => point(worldBones(samplePose(c.anim, t))[c.tip.side === 'left' ? 'weapon_l' : 'weapon_r'], tipRest(c.tip.side, c.tip.len));
/** T3: peak speed over the dt-weighted median speed of the swing. */
function meriHari(c) {
  const p = phases(c), w = within(tipV(c), p.lead, p.end);
  return peak(tipV(c), p.lead, p.end) / median(w.map((x) => x.speed), w.map((x) => x.in));
}
/** T4: share of the wind-up's tip path in its last 30% of time. */
function windupTsume(c) {
  const p = phases(c), v = tipV(c);
  return pathIn(v, p.apex - 0.3 * (p.apex - p.lead), p.apex) / pathIn(v, p.lead, p.apex);
}
/** T5: the strike's share of the swing's tip path, and of its time. */
function strikeTsume(c) {
  const p = phases(c), v = tipV(c);
  return { path: pathIn(v, p.tameEnd, p.strikeEnd) / pathIn(v, p.lead, p.end), time: (p.strikeEnd - p.tameEnd) / (p.end - p.lead) };
}
/** T6: share of the recovery's tip path in its first 30% of time. */
function recoverTsume(c) {
  const p = phases(c), v = tipV(c);
  return pathIn(v, p.strikeEnd, p.strikeEnd + 0.3 * (p.end - p.strikeEnd)) / pathIn(v, p.strikeEnd, p.end);
}
/** T7: recovery time over wind-up time. */
const weight = (c) => { const p = phases(c); return (p.end - p.strikeEnd) / (p.apex - p.lead); };

const T3 = { man: 4, hobbit: 4, elf: 3, orc: 5, dwarf: 6 };
const T4 = { dwarf: 0.15, man: 0.22, hobbit: 0.22, elf: 0.26, orc: 0.3 };
const T5 = { dwarf: 0.55, orc: 0.55, man: 0.45, hobbit: 0.45, elf: 0.45 };
const T6 = { dwarf: 0.6, orc: 0.55, man: 0.45, hobbit: 0.45, elf: 0.45 };
const T7 = { dwarf: [1.1, Infinity], elf: [0, 0.9], man: [0.8, 1.2], hobbit: [0.8, 1.2], orc: [0.8, 1.2] };

/** T1's hold: the race's own tame (STYLE tempo; spec T1, "≥ race tame"), not the clip's hits.json tame, which the build
 *  drops on a fast apex or a short wind-up; unarmed blows take 0.03 s (spec, edge cases) and elves at least 0.03 s (the
 *  turnaround still reads). */
const tameOf = (c) => Math.max(/^attack_unarmed_/.test(c.name) ? 0.03 : STYLE[c.race].tempo.tame, c.race === 'elf' ? 0.03 : 0);

test('T1 kime: the wind-up apex reads as a held beat, the tip under 10% of its peak for at least the race\u2019s tame (elves 0.03 s)', () => {
  const bad = [];
  for (const c of plain) {
    const need = tameOf(c), d = dwell(c);
    if (d < need - 1e-6) bad.push(fail(c, `held ${d.toFixed(3)} s, tame ${need} s`));
  }
  ratchet('T1', bad);
});

test('T2 kime: the tip at the apex, the hit and the end are three poses, each at least half a blade from the others', () => {
  const bad = [];
  for (const c of plain) {
    const p = phases(c), pts = [['apex', p.apex], ['hit', p.hit], ['end', p.end]].map(([k, t]) => [k, tipAt(c, t)]), min = 8 * c.tip.len;
    for (let i = 0; i < 3; i++) for (let j = i + 1; j < 3; j++) {
      const d = M.len(M.sub(pts[i][1], pts[j][1])), pair = `${pts[i][0]}-${pts[j][0]}`;
      if (d < min) bad.push({ key: `${c.race}/${c.name} ${pair}`, msg: `${c.race} ${c.name}: ${pair} ${d.toFixed(1)} px (half a blade ${min} px)` });
    }
  }
  ratchet('T2', bad);
});

test('T3 meri-hari: the peak speed is many times the swing’s median (man, hobbit 4; elf 3; orc 5; dwarf 6)', () => {
  const bad = [];
  for (const c of plain) {
    const r = meriHari(c), lim = T3[tempoRace(c)];
    if (!(r >= lim)) bad.push(fail(c, `${f2(r)} (≥ ${lim})`));
  }
  ratchet('T3', bad);
});

test('T4 tsume: the wind-up slows into the apex, its last 30% of time carrying little of its path (dwarf 15%, man/hobbit 22%, elf 26%, orc 30%)', () => {
  const bad = [];
  for (const c of plain.filter(windsUp)) {
    const r = windupTsume(c), lim = T4[tempoRace(c)];
    if (!(r <= lim)) bad.push(`${c.race} ${c.name}: ${f2(r)} (≤ ${lim})`);
  }
  report(bad);
});

test('T5 tsume: the strike is a burst, at least 45% of the swing’s path (dwarf, orc 55%) in at most 30% of its time', () => {
  const bad = [];
  for (const c of plain) {
    const r = strikeTsume(c), lim = T5[tempoRace(c)];
    if (!(r.path >= lim) || !(r.time <= 0.3)) bad.push(fail(c, `path ${f2(r.path)} (≥ ${lim}), time ${f2(r.time)} (≤ 0.3)`));
  }
  ratchet('T5', bad);
});

test('T6 tsume: the recovery leaves the hit fast and settles slowly, its first 30% of time carrying most of its path (dwarf 60%, orc 55%, others 45%)', () => {
  const bad = [];
  for (const c of plain) {
    const r = recoverTsume(c), lim = T6[tempoRace(c)];
    if (!(r >= lim)) bad.push(fail(c, `${f2(r)} (≥ ${lim})`));
  }
  ratchet('T6', bad);
});

test('T7 weight: recovery time over wind-up time — dwarves settle long (≥ 1.1), elves snap back (≤ 0.9), others 0.8..1.2', () => {
  const bad = [];
  for (const c of plain.filter(windsUp)) {
    const r = weight(c), [lo, hi] = T7[tempoRace(c)];
    if (!(r >= lo && r <= hi)) bad.push(fail(c, `${f2(r)} (${lo}..${hi})`));
  }
  ratchet('T7', bad);
});

test('T9 orcs end off balance: on the same swing as a man, the torso ends ≥ 8° from idle and the free hand ≥ 6 px further from rest', () => {
  const pose = (race, name, t) => worldBones(samplePose(shipped[race].animations.animations[name], t));
  const idle = (race) => pose(race, 'idle', 0);
  const end = (race, n) => { const h = shipped[race].hits[n]; return pose(race, n, h.length - (h.ready || 0)); };
  const hand = (w) => point(w.weapon_l, FIST.left);
  const handOff = (race, n) => M.len(M.sub(hand(end(race, n)), hand(idle(race))));
  // an unarmed blow may strike with the left hand, so its "free" hand is not free: weapon attacks only
  const armed = (n) => !/^attack_unarmed_/.test(n);
  const ps = pairs('orc').filter(armed), bad = [];
  assert.ok(ps.length >= 3, `orc: only ${ps.length} attacks to compare`);
  for (const n of ps) {
    const a = M.waist(end('orc', n)), b = M.waist(idle('orc'));
    const torso = Math.max(Math.abs(a.twist - b.twist), Math.abs(a.flex - b.flex)), hand = handOff('orc', n) - handOff('man', n);
    if (!(torso >= 8 && hand >= 6)) bad.push({ key: `orc/${n}`, msg: `orc ${n}: torso ${f2(torso)}° (≥ 8), free hand +${f2(hand)} px (≥ 6)` });
  }
  ratchet('T9', bad);
});

test('twins: the _l and _shield versions give the plain attack’s meri-hari (T3) and weight (T7) within 1%', () => {
  const bad = [];
  const byName = new Map(plain.map((c) => [`${c.race}/${c.name}`, c]));
  for (const c of clips.filter(twin)) {
    const o = byName.get(`${c.race}/${baseName(c.name)}`);
    for (const [k, f] of [['T3', meriHari], ['T7', weight]]) {
      if (k === 'T7' && !windsUp(o)) continue;
      const a = f(c), b = f(o);
      if (!(Math.abs(a - b) <= 0.01 * Math.abs(b))) bad.push(`${c.race} ${c.name} ${k}: ${f2(a)}, plain ${f2(b)}`);
    }
  }
  report(bad);
});
