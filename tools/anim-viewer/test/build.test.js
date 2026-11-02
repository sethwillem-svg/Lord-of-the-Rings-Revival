'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { SLOTS, RACES } = require('../npc_slots.js');
const { buildRace, loadSources } = require('../build_npc.js');

const sources = loadSources();
const built = Object.fromEntries(RACES.map((r) => [r, buildRace(r, sources)]));

for (const race of ['man', 'elf', 'dwarf', 'hobbit', 'orc']) test(race + ' has every slot', () => {
  const r = built[race];
  for (const slot of Object.keys(SLOTS)) assert.ok(r.animations.animations[slot], slot);   // falls back to the base table, never missing
});

test('a race override replaces only its slot; the rest come from the base table', () => {
  const r = buildRace('orc', sources, { ...SLOTS, walk: { ...SLOTS.walk, races: { orc: { source: 'animationsplus', clip: 'crouchwalk' } } } });
  assert.equal(r.clips.walk.length, sources.animationsplus.crouchwalk.length);
  assert.equal(r.clips.run.length, sources.animationsplus.sprint.length);
});

test('every hit entry names an attack in the same file, hit inside the clip', () => {
  for (const r of Object.values(built)) for (const name of Object.keys(r.hits)) {
    assert.ok(r.animations.animations[name], name);
    assert.ok(name.startsWith('attack_'), name);
    assert.ok(r.hits[name].hit > 0 && r.hits[name].hit < r.hits[name].length, name);
  }
  assert.ok(Object.keys(built.man.hits).length >= 14);
});

test('orc posture adds hunch to upper', () => {
  const x = (r) => r.clips.idle.bones.upper.rotation[0].v[0];
  assert.ok(Math.abs(x(built.orc) - x(built.man) - 24) < 1e-6);
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
