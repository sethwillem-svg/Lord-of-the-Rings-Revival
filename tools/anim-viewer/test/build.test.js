'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { isDeepStrictEqual } = require('node:util');
const { SLOTS, RACES, STYLE } = require('../npc_slots.js');
const { animationJson } = require('../gecko/write.js');
const { buildRace, classLength, hitTime, _tempo, _fight, _finish, _clipFor } = require('../build_npc.js');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const { blade } = require('../gecko/strike.js');
const M = require('./lib/motion.js');
const { built, sources } = require('./lib/built.js');   // every race as the build makes it today (cached by its inputs)

for (const race of ['man', 'elf', 'dwarf', 'hobbit', 'orc']) test(race + ' has every slot', () => {
  const r = built[race];
  for (const slot of Object.keys(SLOTS)) assert.ok(r.animations.animations[slot], slot);   // falls back to the base table, never missing
});

test('a race override replaces only its slot; the rest come from the base table', () => {
  const r = buildRace('orc', sources(), { ...SLOTS, walk: { ...SLOTS.walk, races: { orc: { source: 'animationsplus', clip: 'crouchwalk' } } } });
  assert.equal(r.clips.walk.length, sources().animationsplus.crouchwalk.length);
  assert.ok(Math.abs(r.clips.run.length - sources().animationsplus.sprint.length / 3) < 1e-6);   // run keeps one stride
});

test('every hit entry names an attack in the same file, hit inside the clip', () => {
  for (const r of Object.values(built)) for (const name of Object.keys(r.hits)) {
    assert.ok(r.animations.animations[name], name);
    assert.ok(name.startsWith('attack_'), name);
    assert.ok(r.hits[name].hit > 0 && r.hits[name].hit < r.hits[name].length, name);
  }
  assert.ok(Object.keys(built.man.hits).length >= 14);
});

test('orc posture adds its hunch (12°) to upper', () => {
  const x = (r) => r.clips.idle.bones.upper.rotation[0].v[0];
  assert.ok(Math.abs(x(built.orc) - x(built.man) - 12) < 1e-6);
});

test('hobbits take shorter strides', () => {
  const swing = (r) => r.clips.walk.bones.right_leg.rotation.map((k) => k.v[0]);
  const amp = (r) => Math.max(...swing(r)) - Math.min(...swing(r));   // range: posture shifts the thigh, stride scales the swing
  assert.ok(Math.abs(amp(built.hobbit) / amp(built.man) - 0.65) < 0.05);
});

test('riding slots exist and loop', () => {
  assert.ok(SLOTS.ride_idle && SLOTS.ride_move);
  assert.equal(built.man.animations.animations.ride_move.loop, true);
});

test('spear brace holds a frame that breathes (specs/addon/npc-formation-stances.md): only the torso and head move', () => {
  const c = built.man.clips.spear_brace;
  assert.equal(c.loop, true);
  for (const [bone, ch] of Object.entries(c.bones)) for (const keys of Object.values(ch)) {
    if (bone === 'upper' || bone === 'head') assert.ok(keys.length > 1, `${bone} does not breathe`);
    else assert.ok(keys.every((k) => k.v.every((x, j) => Math.abs(x - keys[0].v[j]) < 1e-6)), `${bone} moves`);
  }
});

test('hits land at the end of the fastest stretch, not at the edge of the search window', () => {
  for (const [name, h] of Object.entries(built.man.hits)) assert.ok(h.hit > 0.15 * h.length + 0.01, `${name} ${h.hit}/${h.length}`);
});

const LEGS = ['right_leg', 'left_leg', 'right_knee', 'left_knee'];
test('posture never adds leg keys to overlays, so legs keep walking under an attack', () => {
  for (const race of RACES) for (const [name, c] of Object.entries(built[race].clips)) {
    if (!/^attack_|^bow_release$|^crossbow_shoot$/.test(name)) continue;
    const base = built.man.clips[name];
    for (const leg of LEGS) assert.equal(!!c.bones[leg], !!base.bones[leg], `${race} ${name} ${leg}`);
  }
});

test('run is one stride starting on the same leg as walk, so the blend stays in phase', () => {
  const legAt0 = (c) => c.bones.right_leg.rotation[0].v[0];
  const minLeg = (c) => Math.min(...c.bones.right_leg.rotation.map((k) => k.v[0]));
  const run = built.man.clips.run, walk = built.man.clips.walk;
  assert.ok(Math.abs(run.length - sources().animationsplus.sprint.length / 3) < 1e-6, String(run.length));
  assert.ok(Math.abs(legAt0(walk) - minLeg(walk)) < 1e-6);
  assert.ok(Math.abs(legAt0(run) - minLeg(run)) < 1, `${legAt0(run)} vs ${minLeg(run)}`);
});

test('every attack has a left-handed twin that swings on weapon_l, with no hit entry of its own', () => {
  for (const name of Object.keys(built.man.hits)) {
    const l = built.man.clips[name + '_l'];
    assert.ok(l, name + '_l');
    assert.ok(!built.man.hits[name + '_l']);
    if (built.man.clips[name].bones.weapon_r) assert.ok(l.bones.weapon_l, name);   // weapon_r may carry the empty hand's forearm twist (hinge.js)
  }
});

test('every state the in-game graph names is in each race file (client.anim.NpcGraphs)', () => {
  const states = ['idle', 'walk', 'run', 'ride_idle', 'ride_move', 'guard', 'shield_brace', 'spear_brace', 'march', 'bow_aim', 'crossbow_aim', 'crossbow_reload', 'hurt', 'bow_release', 'crossbow_shoot'];
  for (const race of RACES) for (const s of states) assert.ok(built[race].animations.animations[s], `${race} ${s}`);
});

test('the shipped race files are what the build makes today (run node tools/anim-viewer/build_npc.js after changing it)', () => {
  const { shipped } = require('./lib/shipped.js');
  const json = (x) => JSON.parse(JSON.stringify(x));   // as written to disk (-0 and 0 are the same there)
  const names = (a, b) => [...new Set([...Object.keys(a), ...Object.keys(b)])];
  // compared clip by clip and named, not diffed whole: a diff of two whole race files runs out of memory
  const stale = [];
  for (const race of RACES) {
    const a = json(built[race].animations), s = shipped[race].animations, h = json(built[race].hits), sh = shipped[race].hits;
    if (!isDeepStrictEqual({ ...a, animations: null }, { ...s, animations: null })) stale.push(`${race}.animation.json header`);
    for (const n of names(a.animations, s.animations)) if (!isDeepStrictEqual(a.animations[n], s.animations[n])) stale.push(`${race}.animation.json ${n}`);
    for (const n of names(h, sh)) if (!isDeepStrictEqual(h[n], sh[n])) stale.push(`${race}.hits.json ${n}`);
  }
  assert.equal(stale.length, 0, `${stale.length} stale: ${stale.slice(0, 20).join(', ')}`);
});

test('the build cache is keyed on every file the build reads (test/lib/built.js inputs)', () => {
  const { inputs } = require('./lib/built.js');
  const tools = path.join(__dirname, '..') + path.sep, tests = __dirname + path.sep;
  const ours = () => Object.keys(require.cache).filter((m) => m.startsWith(tools) && !m.startsWith(tests));
  const saved = ours().map((m) => [m, require.cache[m]]);
  const read = new Set(), real = fs.readFileSync;
  fs.readFileSync = function (f, ...rest) {   // records every file read, the build's modules included
    if (typeof f === 'string' || f instanceof URL || Buffer.isBuffer(f)) read.add(path.resolve(f instanceof URL ? f.pathname : String(f)));
    return real.call(this, f, ...rest);
  };
  try {
    for (const m of ours()) delete require.cache[m];   // loaded afresh, so the reads it makes as it loads count too
    const b = require('../build_npc.js');
    b.buildRace('man', b.loadSources());
  } finally {
    fs.readFileSync = real;
    for (const m of ours()) delete require.cache[m];
    for (const [m, mod] of saved) require.cache[m] = mod;
  }
  const covered = new Set(inputs());
  assert.deepEqual([...read].filter((f) => !covered.has(f)).map((f) => path.relative(tools, f)), []);
});

// ---- tempo: the tame-tsume time warp (specs/addon/npc-attack-tempo.md, "Time warp") ----
const P = { lead: 0.1, apex: 0.3, hit: 0.4, end: 0.7 };
test('warp with p = 1 and tame 0 equals the old linear scales', () => {
  const t = { windup: 2, tame: 0, strike: 0.5, recover: 1, tsume: { windup: 1, strike: 1, recover: 1 } };
  assert.equal(_tempo.warp(0.1, { ...P, t }), 0.1);          // lead-in untouched
  assert.equal(_tempo.warp(0.2, { ...P, t }), 0.3);          // half the wind-up, scaled 2x
  assert.equal(_tempo.warp(0.3, { ...P, t }), 0.5);          // apex
  assert.equal(_tempo.warp(0.45, { ...P, t }), 0.575);       // strike (apex→hit+0.05) scaled 0.5
  assert.equal(_tempo.warp(0.7, { ...P, t }), 0.825);        // end
});
test('tsume windup p = 2 spends the second half of the wind-up on the last 29% of the source', () => {
  const t = { windup: 1, tame: 0, strike: 1, recover: 1, tsume: { windup: 2, strike: 1, recover: 1 } };
  // g(u) = 1-(1-u)^2 ; g(0.5) = 0.75 : source 0.75 of the way is reached at half the new time → warp(0.25) = 0.2
  assert.ok(Math.abs(_tempo.warp(0.1 + 0.2 * 0.75, { ...P, t }) - 0.2) < 1e-9);
});
test('tame inserts the apex pose twice, tame seconds apart, and lengthens the clip by tame', () => {
  const clip = { length: 0.7, lead: 0.1, bones: { right_arm: { rotation: Array.from({ length: 43 }, (_, i) => ({ t: i / 60, v: [i, 0, 0], lerp: 'linear' })) } } };
  const t = { windup: 1, tame: 0.15, strike: 1, recover: 1, tsume: { windup: 1, strike: 1, recover: 1 } };
  const c = _tempo.apply(clip, 'attack_sword_1', t, null, 'right', { apex: 0.3, hit: 0.4 });
  const keys = c.bones.right_arm.rotation, i = keys.findIndex((k) => Math.abs(k.t - 0.3) < 1e-9);
  assert.deepEqual(keys[i].v, keys[i + 1].v); assert.ok(Math.abs(keys[i + 1].t - 0.45) < 1e-9);
  assert.ok(Math.abs(c.length - 0.85) < 1e-9); assert.equal(c.tame, 0.15); assert.ok(Math.abs(c.apex - 0.3) < 1e-9);
});
/** A right-arm swing at 60 Hz whose arm angle (degrees about x) follows f(t). */
const swing = (f, length = 0.5) => animationJson({ c: { length, bones: { right_arm: { rotation: Array.from({ length: Math.round(length * 60) + 1 }, (_, i) => ({ t: i / 60, v: [f(i / 60), 0, 0], lerp: 'linear' })) } } } }).animations.c;
test('a monotonic thrust takes its apex at hit − 0.1 s', () => {
  // a clip whose tip speed only rises until the hit: apexOf must not find a minimum and must return hit - 0.1
  const a = swing((x) => -90 * (x / 0.4) ** 2), apex = _tempo.apexOf(a, classLength('attack_spear_1'), 'right', 0.4);
  assert.ok(Math.abs(apex - 0.3) < 1e-9, String(apex));
});
test('a swing that turns takes its apex at the turn', () => {
  const a = swing((x) => 400 * (x - 0.25) ** 2), apex = _tempo.apexOf(a, classLength('attack_sword_1'), 'right', 0.4);
  assert.ok(Math.abs(apex - 0.25) <= 1 / 60, String(apex));
});
test('every hit entry records apex before the hit, tame ≥ 0 and ready ≥ 0', () => {
  for (const r of RACES) for (const [n, h] of Object.entries(built[r].hits)) {
    assert.ok(h.apex > (h.lead || 0) - 1e-9 && h.apex < h.hit, `${r} ${n}`); assert.ok(h.tame >= 0 && h.ready >= 0, `${r} ${n}`);
  }
});
test('every hit lands after the tame: hit ≥ apex + tame', () => {
  const bad = [];
  for (const r of RACES) for (const [n, h] of Object.entries(built[r].hits)) if (h.hit < h.apex + h.tame - 1e-3) bad.push(`${r} ${n}: apex ${h.apex} + tame ${h.tame} > hit ${h.hit}`);
  assert.deepEqual(bad, []);
});
test('unarmed attacks take the man table with tame 0.03 (0 on a short wind-up or a fast apex), and the race\'s own ready', () => {
  assert.ok([0, 0.03].includes(built.orc.hits.attack_unarmed_1.tame), String(built.orc.hits.attack_unarmed_1.tame));
  for (const r of RACES) for (const [n, h] of Object.entries(built[r].hits)) if (/^attack_unarmed_/.test(n)) {
    assert.ok([0, 0.03].includes(h.tame), `${r} ${n} tame ${h.tame}`);
    // man's windup scale is 1, so apex − lead is the source wind-up
    if (h.apex - (h.lead || 0) < 0.05 - 1e-3) assert.equal(h.tame, 0, `${r} ${n}`);
    assert.equal(h.ready > 0, !!STYLE[r].tempo.ready, `${r} ${n} ready ${h.ready}`);
  }
});

/** Where the weapon tip starts a finished attack (at its lead): azimuth about the body axis from straight ahead (degrees),
 *  unwrapped by following the tip back from the hit, and how far the tip is from the axis, horizontally (px). */
function tipStart(anim, name, lead, hit) {
  const tip = blade('right', classLength(name), 1), at = (t) => point(worldBones(samplePose(anim, t)).weapon_r, tip);
  const az = (t) => { const p = at(t); return Math.atan2(p[0], -p[2]); };
  let prev = az(hit), sum = M.deg(prev);
  for (const t of [...M.times(anim).filter((x) => x > lead + 1e-9 && x < hit - 1e-9).reverse(), lead]) {
    const now = az(t);
    let d = M.deg(now - prev);
    d -= 360 * Math.round(d / 360);
    sum += d; prev = now;
  }
  const p = at(lead);
  return { az: sum, r: Math.hypot(p[0], p[2]) };
}

test('the orc wind-up drawn back after posture (fight.windupAfter) never starts the swing further forward than without it', () => {
  const style = STYLE.orc, plain = { ...style, fight: { ...style.fight, windupAfter: 0 } }, o = built.orc, bad = [];
  assert.ok(style.fight.windupAfter > 0, 'orcs draw their wind-up back');
  for (const [name, h] of Object.entries(o.hits)) {
    const raw = _clipFor(name, { source: 'animationsplus', clip: h.clip }, 'orc', sources());
    if (h.style) _fight(raw, name, style.fight, h.style);
    const finish = (st) => {
      const c = _finish(raw, name, st, o.clips.idle, 'right', o.clips.walk), a = animationJson({ c }).animations.c;
      return { a, ...tipStart(a, name, c.lead || 0, hitTime(a, name)) };
    };
    const a = finish(style), b = finish(plain);
    // the test judges the real clip: rebuilt, it is the one the build made (as JSON, where -0 reads back as 0)
    assert.deepEqual(JSON.parse(JSON.stringify(a.a)), o.animations.animations[name], `${name}: rebuilt as built`);
    if (a.r < 4 || b.r < 4) continue;   // within 4 px of the body axis the azimuth says nothing
    // 0.5° of slack: the build follows the tip at 60 Hz, this test at 60 Hz and every key
    if (Math.sign(a.az) !== Math.sign(b.az) || Math.abs(a.az) < Math.abs(b.az) - 0.5) bad.push(`${name} (${h.clip}): starts at ${a.az.toFixed(0)}° drawn back, ${b.az.toFixed(0)}° without`);
  }
  assert.deepEqual(bad, []);
});

test('limitTwist brings a wrung arm within its twist range by swivelling it, the weapon and the bend left where they were', () => {
  const { limitTwist } = require('../hinge.js');
  const key = (t, v) => ({ t, v, lerp: 'linear' });
  // a bent arm wrung 40° then 150° about its own length, the weapon held still against the forearm
  const clip = { length: 0.5, loop: false, bones: {
    right_arm: { rotation: [key(0, [-20, 40, 0]), key(0.5, [-20, 150, 0])] },
    right_elbow: { rotation: [key(0, [-60, 0, 0]), key(0.5, [-60, 0, 0])] },
    weapon_r: { rotation: [key(0, [0, 0, 0]), key(0.5, [0, 0, 0])] },
  } };
  const pose = (c, t) => worldBones(samplePose(animationJson({ c }).animations.c, t));
  const before = pose(clip, 0.5);
  assert.ok(Math.abs(M.twist(before, 'right_arm', 'body')) > 95, 'the arm starts out of range');
  const out = limitTwist(clip);
  for (let t = 0; t <= 0.5 + 1e-9; t += 1 / 60) {
    const a = pose(clip, t), b = pose(out, t);
    assert.ok(Math.abs(M.twist(b, 'right_arm', 'body')) <= 95, `t=${t.toFixed(2)}: upper arm ${M.twist(b, 'right_arm', 'body').toFixed(0)}°`);
    assert.ok(Math.abs(M.twist(b, 'weapon_r', 'right_elbow')) <= 95, `t=${t.toFixed(2)}: wrist ${M.twist(b, 'weapon_r', 'right_elbow').toFixed(0)}°`);
    assert.ok(Math.abs(M.hinge(b, 'right_elbow', 'right_arm').bend - M.hinge(a, 'right_elbow', 'right_arm').bend) < 1e-3, `t=${t.toFixed(2)}: the bend stays`);
    const tip = (w) => point(w.weapon_r, [6, 12 + 17, -2 - 17]);
    assert.ok(M.len(M.sub(tip(a), tip(b))) < 0.3, `t=${t.toFixed(2)}: the weapon tip moved ${M.len(M.sub(tip(a), tip(b))).toFixed(2)} px`);
  }
});

test('the edge holds its roll over the game tick either side of the hit as played: the window is undone through the warp', () => {
  // a tame hold and a strike burst: played ±0.05 s maps to a source span the warp lands exactly on (or the apex, in the hold)
  const t = { windup: 0.9, tame: 0.02, strike: 0.78, recover: 1.2, tsume: { windup: 1.25, strike: 2.4, recover: 2.4 } };
  const at = { ...P, t }, hit = _tempo.warp(0.4, at);
  const [from, to] = _tempo.unwarpSpan(at, hit - 0.05, hit + 0.05);
  const top = _tempo.warp(P.apex, at);
  assert.ok(Math.abs(_tempo.warp(to, at) - (hit + 0.05)) < 1e-6, `to ${to} plays at ${_tempo.warp(to, at)}`);
  if (hit - 0.05 > top + 0.02) assert.ok(Math.abs(_tempo.warp(from, at) - (hit - 0.05)) < 1e-6);
  else assert.ok(Math.abs(from - P.apex) < 1e-6, 'inside the hold: the apex pose');
  assert.ok(from < 0.4 && to > 0.4, `the span ${from}..${to} holds the hit`);
});

test('hinge.js internals: no cost reachable picks no swivel, a swivel turns no faster than its rate, twist is undefined at the pole', () => {
  const { _internal: H } = require('../hinge.js');
  const vals = H.SWIVELS, times = [0, 1 / 120, 2 / 120];
  assert.deepEqual(H.cheapest(times.map(() => vals.map(() => Infinity)), vals, times, false), [0, 0, 0]);
  // a cost that wants -90° then +90° a frame later: the swivel moves at most its rate
  const want = (d) => vals.map((v) => Math.abs(v - d));
  const got = H.cheapest([want(-90), want(90), want(90)], vals, times, false);
  for (let i = 1; i < got.length; i++) assert.ok(Math.abs(got[i] - got[i - 1]) <= H.SWIVEL_RATE / 120 + 1e-9, `${got}`);
  // frames for a turn about z then y: x and z columns of the identity and of the turned frame
  const turned = (sw, tw) => {
    const r = (a) => (a * Math.PI) / 180, c = Math.cos, s = Math.sin;
    const Rz = [c(r(sw)), -s(r(sw)), 0, s(r(sw)), c(r(sw)), 0, 0, 0, 1], Ry = [c(r(tw)), 0, s(r(tw)), 0, 1, 0, -s(r(tw)), 0, c(r(tw))];
    const m = [0, 1, 2].flatMap((i) => [0, 1, 2].map((j) => Rz[i * 3] * Ry[j] + Rz[i * 3 + 1] * Ry[3 + j] + Rz[i * 3 + 2] * Ry[6 + j]));
    return [[m[0], m[3], m[6]], [m[2], m[5], m[8]]];
  };
  const [x1, z1] = turned(170, 40), [x2, z2] = turned(178, 40);
  assert.ok(Math.abs(H.twistOf([1, 0, 0], [0, 0, 1], x1, z1) - 40) < 1e-6);
  assert.ok(Number.isNaN(H.twistOf([1, 0, 0], [0, 0, 1], x2, z2)), 'within 5° of a half-turn swing: undefined, not 0');
});

test('limitTwist keeps a held item’s roll where it is told to (opts.keep), and rolls it elsewhere only as far as the wrist needs', () => {
  const { limitTwist } = require('../hinge.js');
  const key = (t, v) => ({ t, v, lerp: 'linear' });
  // a straight arm wrung 90° and the item a further 150° round the forearm: more than arm and wrist can take between them
  const clip = { length: 0.5, loop: false, bones: {
    right_arm: { rotation: [key(0, [0, 90, 0]), key(0.5, [0, 90, 0])] },
    right_elbow: { rotation: [key(0, [-1, 0, 0]), key(0.5, [-1, 0, 0])] },
    weapon_r: { rotation: [key(0, [0, 150, 0]), key(0.5, [0, 150, 0])] },
  } };
  const out = limitTwist(clip, { roll: { weapon_r: [0, Math.SQRT1_2, -Math.SQRT1_2] }, held: ['weapon_r'], keep: { weapon_r: [0.2, 0.3] } });
  const pose = (c, t) => worldBones(samplePose(animationJson({ c }).animations.c, t));
  for (let t = 0; t <= 0.5 + 1e-9; t += 0.005) {
    const a = pose(clip, t).weapon_r, b = pose(out, t).weapon_r;
    const off = Math.max(...a.R.map((x, i) => Math.abs(x - b.R[i])));
    if (t >= 0.2 && t <= 0.3) assert.ok(off < 1e-3, `t=${t.toFixed(3)}: the kept item turned (${off.toFixed(4)})`);
    else if (t < 0.1 || t > 0.4) assert.ok(Math.abs(M.twist(pose(out, t), 'weapon_r', 'right_elbow')) <= 95, `t=${t.toFixed(3)}: wrist out of range`);
  }
});
