#!/usr/bin/env node
/* Builds the NPC rig and each race's clip set for PulseLib's GeckoLib loader.
 *   node tools/anim-viewer/build_npc.js     writes, under addon/src/main/resources/assets/lotr_revival/geckolib/,
 *     models/entity/npc_biped_<race>.geo.json, animations/entity/npc_biped_<race>.animation.json and .hits.json
 * hits.json: { "<attack>": { "length": s, "hit": s } } — the hit is the fastest moment of the weapon tip (a point a block in
 * front of the fist) between 15% and 80% of the clip; the server times damage by it (combat.AttackTiming). */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { RIG } = require('./gecko/rig.js');
const { geoJson, animationJson } = require('./gecko/write.js');
const { samplePose, worldBones, point } = require('./gecko/sample.js');
const { importAnimationsPlus, mirror } = require('./import/animationsplus.js');
const { importMoBends } = require('./import/mobends.js');
const { importSeriousPlayer } = require('./import/seriousplayer.js');
const { importEmote } = require('./import/emotecraft.js');
const { RACES, SLOTS, STYLE } = require('./npc_slots.js');

const LIB = path.join(__dirname, 'library');
const OUT = path.join(__dirname, '../../addon/src/main/resources/assets/lotr_revival/geckolib');
const LOOPED = new Set(['idle', 'walk', 'run', 'crouch_walk', 'swim', 'climb', 'ride_idle', 'ride_move', 'march', 'guard', 'shield_brace', 'spear_brace', 'crossbow_aim']);
const STRIDE = new Set(['walk', 'run', 'march']);
const TIP = [6, 12, -14];   // weapon_r pivot plus 12 px forward

function loadSources() {
  const json = (p) => JSON.parse(fs.readFileSync(path.join(LIB, p), 'utf8'));
  const dir = (d) => fs.readdirSync(path.join(LIB, d)).filter((f) => f.endsWith('.json')).sort();
  const sources = { animationsplus: importAnimationsPlus(json('animationsplus/model.bbmodel'), () => {}), mobends: {}, seriousplayer: {}, emotecraft: {} };
  for (const f of dir('mobends')) Object.assign(sources.mobends, importMoBends(json('mobends/' + f), f.replace(/\.json$/, '')));
  for (const f of dir('seriousplayer')) Object.assign(sources.seriousplayer, importSeriousPlayer(json('seriousplayer/' + f)));
  for (const f of dir('emotecraft')) Object.assign(sources.emotecraft, importEmote(json('emotecraft/' + f), f.replace(/\.json$/, '')));
  return sources;
}

const clone = (c) => JSON.parse(JSON.stringify(c));

/** A held pose: every channel's value at time t as one key. */
function holdAt(clip, t) {
  const bones = {};
  for (const [bone, ch] of Object.entries(clip.bones)) {
    bones[bone] = {};
    for (const [kind, keys] of Object.entries(ch)) {
      const k = keys.filter((x) => x.t <= t + 1e-9).pop() || keys[0];
      bones[bone][kind] = [{ t: 0, v: k.v.slice(), lerp: 'linear' }];
    }
  }
  return { length: 0.05, loop: true, bones };
}

function clipFor(slot, entry, race, sources) {
  const e = (entry.races && entry.races[race]) || entry;
  const src = sources[e.source] && sources[e.source][e.clip];
  if (!src) throw new Error(`${slot}: no clip ${e.source}/${e.clip}`);
  let c = clone(e.mirror ? mirror(src) : src);
  if (e.hold !== undefined) c = holdAt(c, e.hold);
  if (e.loop !== undefined) c.loop = e.loop;
  if (LOOPED.has(slot)) c.loop = true;
  if (!c.length || c.length < 0.05) c.length = 0.05;
  return c;
}

/** Adds a constant rotation to a bone: to every key, or as one key when the clip leaves the bone alone. */
function addRotation(clip, bone, d) {
  if (!d.some((x) => x)) return;
  const ch = (clip.bones[bone] = clip.bones[bone] || {});
  if (!ch.rotation || !ch.rotation.length) ch.rotation = [{ t: 0, v: [0, 0, 0], lerp: 'linear' }];
  for (const k of ch.rotation) k.v = k.v.map((x, i) => x + d[i]);
}

function applyStyle(clip, slot, style) {
  if (style.stride && STRIDE.has(slot)) {
    for (const leg of ['right_leg', 'left_leg']) for (const k of (clip.bones[leg] && clip.bones[leg].rotation) || []) k.v[0] *= style.stride;
  }
  addRotation(clip, 'upper', [style.hunch || 0, 0, 0]);
  addRotation(clip, 'right_arm', [0, 0, style.armsOut || 0]);
  addRotation(clip, 'left_arm', [0, 0, -(style.armsOut || 0)]);
  const knee = Math.max(0, style.kneeBend || 0);
  for (const side of ['right', 'left']) {
    addRotation(clip, side + '_knee', [knee, 0, 0]);
    addRotation(clip, side + '_leg', [-knee / 2, 0, 0]);
  }
}

/** When the swing lands: the end of the weapon tip's fastest stretch (speed back under half its peak), searched between
 * 15% and 80% of the clip. AnimationsPlus clips start wound up, so the fastest stretch often begins at the window's edge. */
function hitTime(animation) {
  const L = animation.animation_length, dt = 1 / 60;
  const tip = (t) => point(worldBones(samplePose(animation, t)).weapon_r, TIP);
  const speed = (t) => { const a = tip(Math.max(0, t - dt)), b = tip(Math.min(L, t + dt)); return Math.hypot(b[0] - a[0], b[1] - a[1], b[2] - a[2]); };
  const ts = [];
  for (let t = 0.15 * L; t <= 0.8 * L + 1e-9; t += dt / 2) ts.push(t);
  const v = ts.map(speed);
  let peak = 0;
  v.forEach((s, i) => { if (s > v[peak] + 1e-9) peak = i; });
  let end = peak;
  while (end + 1 < ts.length && v[end + 1] >= 0.5 * v[peak]) end++;
  return +ts[end].toFixed(3);
}

function buildRace(race, sources, slots = SLOTS) {
  const clips = {};
  for (const [slot, entry] of Object.entries(slots)) {
    const c = clipFor(slot, entry, race, sources);
    applyStyle(c, slot, STYLE[race] || {});
    clips[slot] = c;
  }
  const animations = animationJson(clips);
  const hits = {};
  for (const name of Object.keys(clips)) if (name.startsWith('attack_')) {
    hits[name] = { length: animations.animations[name].animation_length, hit: hitTime(animations.animations[name]) };
  }
  return { geo: geoJson(RIG), animations, hits, clips };
}

function main() {
  const sources = loadSources();
  const write = (rel, data) => {
    const f = path.join(OUT, rel);
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, JSON.stringify(data) + '\n');
  };
  for (const race of RACES) {
    const r = buildRace(race, sources);
    write(`models/entity/npc_biped_${race}.geo.json`, r.geo);
    write(`animations/entity/npc_biped_${race}.animation.json`, r.animations);
    write(`animations/entity/npc_biped_${race}.hits.json`, r.hits);
    console.log(`${race}: ${Object.keys(r.animations.animations).length} animations, ${Object.keys(r.hits).length} attacks`);
  }
}

module.exports = { loadSources, buildRace, hitTime, applyStyle };
if (require.main === module) main();
