'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { isDeepStrictEqual } = require('node:util');
const { SLOTS, RACES } = require('../npc_slots.js');
const { buildRace } = require('../build_npc.js');
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

test('spear brace holds the thrust\'s first frame', () => {
  const c = built.man.clips.spear_brace;
  assert.equal(c.loop, true);
  for (const ch of Object.values(c.bones)) for (const keys of Object.values(ch)) assert.equal(keys.length, 1);
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
