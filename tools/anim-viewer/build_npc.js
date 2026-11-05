#!/usr/bin/env node
/* Builds the NPC rig and each race's clip set for PulseLib's GeckoLib loader.
 *   node tools/anim-viewer/build_npc.js     writes, under addon/src/main/resources/assets/lotr_revival/geckolib/,
 *     models/entity/npc_biped_<race>.geo.json, animations/entity/npc_biped_<race>.animation.json and .hits.json
 * hits.json: { "<attack>": { "length": s, "hit": s } } — the hit is where, in the fast part of the swing, the weapon's tip is
 * most directly in front of the NPC (gecko/strike.js); the server times damage by it (combat.AttackTiming). */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { RIG } = require('./gecko/rig.js');
const { geoJson, animationJson } = require('./gecko/write.js');
const { hitTime: strikeHit, acceptable, strike, hingesOk } = require('./gecko/strike.js');
const { samplePose, worldBones, point } = require('./gecko/sample.js');
const { importAnimationsPlus, mirror } = require('./import/animationsplus.js');
const { importMoBends } = require('./import/mobends.js');
const { importSeriousPlayer } = require('./import/seriousplayer.js');
const { importEmote } = require('./import/emotecraft.js');
const { keyedClips } = require('./import/keyed.js');
const { RACES, SLOTS, STYLE } = require('./npc_slots.js');

const LIB = path.join(__dirname, 'library');
const OUT = path.join(__dirname, '../../addon/src/main/resources/assets/lotr_revival/geckolib');
const LOOPED = new Set(['idle', 'walk', 'run', 'crouch_walk', 'swim', 'climb', 'ride_idle', 'ride_move', 'march', 'guard', 'shield_brace', 'spear_brace', 'crossbow_aim']);
const STRIDE = new Set(['walk', 'run', 'march']);

function loadSources() {
  const json = (p) => JSON.parse(fs.readFileSync(path.join(LIB, p), 'utf8'));
  const dir = (d) => fs.readdirSync(path.join(LIB, d)).filter((f) => f.endsWith('.json')).sort();
  const sources = { animationsplus: importAnimationsPlus(json('animationsplus/model.bbmodel'), () => {}), mobends: {}, seriousplayer: {}, emotecraft: {} };
  for (const f of dir('mobends')) Object.assign(sources.mobends, importMoBends(json('mobends/' + f), f.replace(/\.json$/, '')));
  for (const f of dir('seriousplayer')) Object.assign(sources.seriousplayer, importSeriousPlayer(json('seriousplayer/' + f)));
  for (const f of dir('emotecraft')) Object.assign(sources.emotecraft, importEmote(json('emotecraft/' + f), f.replace(/\.json$/, '')));
  sources.keyed = keyedClips(sources.animationsplus);   // combat reactions keyed by hand (import/keyed.js)
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
  if (e.strides) c = oneStride(c, e.strides);
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

/** A small deterministic random in [-1, 1] from a string. */
function noise(key) {
  let h = 2166136261;
  for (const c of key) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return ((h >>> 0) / 4294967295) * 2 - 1;
}

const TWO_HANDED = new Set(['longsword', 'spear', 'axe']);
const scaleKeys = (keys, f) => keys.forEach((k, i) => { k.v = f(k, i); });

/** A race's way of swinging (STYLE.fight), at strength k (0..1), on an attack in Blockbench values before mirroring. */
function fight(clip, slot, f, k) {
  const cls = slot.split('_')[1], hit = strikeHit(animationJson({ c: clip }).animations.c, classLength(slot));
  const rot = (bone) => clip.bones[bone] && clip.bones[bone].rotation;
  const at = (keys, t) => valueAt(keys, t, clip.length, false);
  // wind-up and follow-through: away from (or toward) the strike pose, before and after the hit
  for (const bone of ['right_arm', 'weapon_r', 'upper', 'body']) {
    const keys = rot(bone);
    if (!keys) continue;
    const vHit = at(keys, hit).slice();
    scaleKeys(keys, (key) => {
      if (key.t < hit) return key.v.map((x, j) => x + (x - vHit[j]) * (f.windup || 0) * k * Math.min(1, (hit - key.t) / (0.4 * hit + 1e-6)));
      if (key.t > hit) return key.v.map((x, j) => vHit[j] + (x - vHit[j]) * (1 + (f.overswing || 0) * k));
      return key.v;
    });
  }
  for (const bone of ['upper', 'body']) if (rot(bone)) scaleKeys(rot(bone), (key) => [key.v[0], key.v[1] * (1 + (f.twist || 0) * k), key.v[2]]);
  if (rot('right_elbow')) scaleKeys(rot('right_elbow'), (key) => key.v.map((x) => x * (1 - (f.extend || 0) * k)));
  const curve = (t) => (t <= hit ? t / Math.max(hit, 1e-6) : 1 - 0.6 * ((t - hit) / Math.max(clip.length - hit, 1e-6)));
  if (f.lean) {
    clip.bones.upper = clip.bones.upper || {};
    const keys = clip.bones.upper.rotation || [{ t: 0, v: [0, 0, 0], lerp: 'linear' }];
    if (!keys.some((x) => Math.abs(x.t - hit) < 1e-6)) keys.push({ t: hit, v: at(keys, hit).slice(), lerp: 'linear' });
    keys.sort((x, y) => x.t - y.t);
    scaleKeys(keys, (key) => [key.v[0] + f.lean * k * curve(key.t), key.v[1], key.v[2]]);
    clip.bones.upper.rotation = keys;
  }
  if (f.low && rot('right_arm')) scaleKeys(rot('right_arm'), (key) => [key.v[0] + f.low * k * curve(key.t), key.v[1], key.v[2]]);
  if (f.lead) {   // + the hips (upper) go first, - the arm trails the body
    const shift = (bone, d) => rot(bone) && scaleKeys(rot(bone), (key) => { if (key.t > 0) key.t = Math.max(0, Math.min(clip.length, key.t + d)); return key.v; });
    if (f.lead > 0) shift('upper', -f.lead * k);
    else for (const b of ['right_arm', 'weapon_r']) shift(b, -f.lead * k);
  }
  if (f.freeArm && !TWO_HANDED.has(cls)) {
    const arm = rot('right_arm') || [{ t: 0, v: [0, 0, 0] }], v0 = arm[0].v;
    const times = [...new Set([0, clip.length, ...arm.map((x) => x.t)])].sort((x, y) => x - y);
    const left = (clip.bones.left_arm = clip.bones.left_arm || {});
    const base = left.rotation || [{ t: 0, v: [0, 0, 0], lerp: 'linear' }];
    left.rotation = times.map((t) => {
      const r = at(arm, t), v = at(base, t).slice(), fl = (f.freeArm.flail || 0) * k;
      return { t, v: [v[0] - fl * (r[0] - v0[0]), v[1] - fl * (r[1] - v0[1]), v[2] - (f.freeArm.out || 0) * k], lerp: 'linear' };
    });
  }
  if (f.jitter) for (const bone of ['right_arm', 'weapon_r', 'left_arm', 'upper'])
    if (rot(bone)) scaleKeys(rot(bone), (key, i) => (i === 0 ? key.v : key.v.map((x, j) => x + f.jitter * k * noise(`${slot}/${bone}/${i}/${j}`))));
  for (const bone of Object.keys(clip.bones)) for (const ch of Object.values(clip.bones[bone])) ch.sort((x, y) => x.t - y.t);
}

/**
 * The strongest version of a race's fighting style on an attack that still strikes in front without spinning (gecko/strike.js
 * acceptable) and, for two-handed weapons, keeps the free hand on the grip: full strength, else 60%, 30%, none.
 */
function fought(clip, slot, style) {
  const cls = slot.split('_')[1];
  for (const k of [1, 0.6, 0.3]) {
    const c = clone(clip);
    fight(c, slot, style.fight, k);
    const styled = clone(c);
    applyStyle(styled, slot, style);
    const anim = animationJson({ c: styled }).animations.c;
    if (!acceptable(anim, classLength(slot)) || !hingesOk(anim)) continue;
    if (TWO_HANDED.has(cls) && strike(anim, classLength(slot)).leftToBlade > 8) continue;
    if (process.env.FIGHT_DEBUG) console.log(slot, style.fight === undefined ? '' : 'strength', k);
    return c;
  }
  if (process.env.FIGHT_DEBUG) console.log(slot, 'strength', 0);
  return clip;
}

/** Combat reactions (combat-parry-warning.md): one-shots with mirrored left-handed twins, like attacks. */
const REACTIONS = new Set(['stagger', 'parry_blade', 'parry_shield']);

/** Overlays (attacks, hurt, ranged releases, combat reactions) play over the legs' locomotion: posture must not add bones
 * they leave alone. Keep in step with client.anim.NpcGraphs.isOverlay. */
const isOverlay = (slot) => /^attack_/.test(slot) || ['hurt', 'bow_release', 'crossbow_shoot'].includes(slot) || REACTIONS.has(slot);

/** A clip channel's value at t (linear between keys; loops wrap). */
function valueAt(keys, t, length, loop) {
  if (loop && length > 0) t = ((t % length) + length) % length;
  if (t <= keys[0].t) return keys[0].v;
  for (let i = 1; i < keys.length; i++) if (t <= keys[i].t) {
    const a = keys[i - 1], b = keys[i], f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
    return a.v.map((x, j) => x + f * (b.v[j] - x));
  }
  return keys[keys.length - 1].v;
}

/**
 * One stride of a looping clip that holds `count` strides, starting where the right thigh is furthest forward (as walk
 * does), resampled at 30 fps — so walk and run blend in phase.
 */
function oneStride(clip, count) {
  const period = clip.length / count, leg = clip.bones.right_leg && clip.bones.right_leg.rotation;
  let start = 0;
  if (leg) {
    let lo = Infinity;
    for (let t = 0; t < period; t += 1 / 240) { const x = valueAt(leg, t, clip.length, true)[0]; if (x < lo) { lo = x; start = t; } }
  }
  const n = Math.max(2, Math.round(period * 30)), bones = {};
  for (const [bone, ch] of Object.entries(clip.bones)) {
    bones[bone] = {};
    for (const [kind, keys] of Object.entries(ch)) {
      const ks = Array.from({ length: n + 1 }, (_, i) => ({ t: (i * period) / n, v: valueAt(keys, start + (i * period) / n, clip.length, true).slice(), lerp: 'linear' }));
      ks[n].v = ks[0].v.slice();   // one exact cycle: no seam
      bones[bone][kind] = ks;
    }
  }
  return { length: period, loop: true, bones };
}

/** Keeps a locomotion clip's lower foot on the floor: the root is moved down by however high the lower foot is, per key. */
function groundFeet(clip) {
  const legKeys = (clip.bones.right_leg && clip.bones.right_leg.rotation) || [];
  if (!legKeys.length) return;
  const anim = animationJson({ c: clip }).animations.c;
  const root = (clip.bones.root = clip.bones.root || {});
  const pos = root.position || [{ t: 0, v: [0, 0, 0], lerp: 'linear' }];
  root.position = legKeys.map((k) => {
    const w = worldBones(samplePose(anim, Math.min(k.t, clip.length - 1e-6)));
    const low = Math.min(point(w.right_knee, [1.9, 0, 0])[1], point(w.left_knee, [-1.9, 0, 0])[1]);
    const v = valueAt(pos, k.t, clip.length, clip.loop === true).slice();
    v[1] -= low;
    return { t: k.t, v, lerp: 'linear' };
  });
}

function applyStyle(clip, slot, style) {
  const add = (bone, d) => { if (!isOverlay(slot) || clip.bones[bone]) addRotation(clip, bone, d); };
  if (style.stride && STRIDE.has(slot)) {
    for (const leg of ['right_leg', 'left_leg']) for (const k of (clip.bones[leg] && clip.bones[leg].rotation) || []) k.v[0] *= style.stride;
  }
  const hunch = (style.hunch || 0) * (/^attack_/.test(slot) ? 0.5 : 1);   // attacks straighten up halfway: a lunge, not a dig
  add('upper', [hunch, 0, 0]);
  add('head', [-0.8 * hunch, 0, 0]);   // look ahead, not at the floor
  add('right_arm', [-(style.armsForward || 0), 0, style.armsOut || 0]);
  add('left_arm', [-(style.armsForward || 0), 0, -(style.armsOut || 0)]);
  const knee = Math.max(0, style.kneeBend || 0);
  for (const side of ['right', 'left']) {
    add(side + '_knee', [knee, 0, 0]);
    add(side + '_leg', [-knee / 2, 0, 0]);
  }
}

/** Class lengths (blocks) for hit times: weapon_grips.json, fists and shields as short reaches. */
const GRIPS = JSON.parse(fs.readFileSync(path.join(__dirname, '../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json'), 'utf8'));
function classLength(attack) {
  const cls = attack.split('_')[1];
  return (GRIPS[cls] && GRIPS[cls].length) || (cls === 'unarmed' ? 0.25 : 1);
}

/** When the swing lands: in its fast part, where the weapon tip is most directly ahead (gecko/strike.js). */
const hitTime = (animation, attack = 'attack_sword_1') => strikeHit(animation, classLength(attack));

/** Clips that get a shield version (<name>_shield): movement, the guard, every attack, and the stagger. */
const SHIELD_SLOTS = /^(idle(_\d)?|walk|run|march|guard|stagger|attack_[a-z]+_\d+)$/;
const SHIELD_BONES = ['left_arm', 'left_elbow', 'weapon_l'];

/** The off arm holding a shield up in front: AnimationsPlus' left-hand block, with the race's arm posture. */
function shieldArm(sources, style) {
  const hold = holdAt(clone(sources.animationsplus.blockL), 0);
  applyStyle(hold, 'shield_brace', style);
  return Object.fromEntries(SHIELD_BONES.map((b) => [b, hold.bones[b] || { rotation: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }] }]));
}

/** The same clip with the off arm replaced by the held shield. */
function withShield(clip, arm) {
  const c = clone(clip);
  for (const b of SHIELD_BONES) c.bones[b] = clone(arm[b]);
  return c;
}

function buildRace(race, sources, slots = SLOTS) {
  const clips = {}, style = STYLE[race] || {}, shield = shieldArm(sources, style);
  for (const [slot, entry] of Object.entries(slots)) {
    let c = clipFor(slot, entry, race, sources);
    if (style.fight && /^attack_/.test(slot)) c = fought(c, slot, style);   // before mirroring: the twin swings the same
    if (/^attack_/.test(slot) || REACTIONS.has(slot)) {   // the left-handed twin: the same swing mirrored, item on weapon_l
      const l = mirror(c);
      applyStyle(l, slot, style);
      clips[slot + '_l'] = l;
    }
    applyStyle(c, slot, style);
    if (slot === 'run') groundFeet(c);   // the sprint's long airborne phase floats both feet
    clips[slot] = c;
    if (SHIELD_SLOTS.test(slot)) clips[slot + '_shield'] = withShield(c, shield);
  }
  const animations = animationJson(clips);
  const hits = {};
  for (const name of Object.keys(clips)) if (/^attack_[a-z]+_\d+$/.test(name)) {   // not the _l and _shield versions: same hit
    hits[name] = { length: animations.animations[name].animation_length, hit: hitTime(animations.animations[name], name) };
  }
  return { geo: geoJson(RIG), animations, hits, clips };
}

function main() {
  const sources = loadSources();
  const write = (rel, data, text) => {
    const f = path.join(OUT, rel);
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, text !== undefined ? text : JSON.stringify(data) + '\n');
  };
  // MIT asks for the copyright and permission notice to travel with the clips: ship each source's licence in the jar
  for (const src of ['animationsplus', 'mobends', 'seriousplayer', 'emotecraft']) {
    write(`animations/licenses/${src}.txt`, null, fs.readFileSync(path.join(LIB, src, 'LICENSE.txt'), 'utf8'));
  }
  for (const race of RACES) {
    const r = buildRace(race, sources);
    write(`models/entity/npc_biped_${race}.geo.json`, r.geo);
    write(`animations/entity/npc_biped_${race}.animation.json`, r.animations);
    write(`animations/entity/npc_biped_${race}.hits.json`, r.hits);
    console.log(`${race}: ${Object.keys(r.animations.animations).length} animations, ${Object.keys(r.hits).length} attacks`);
  }
}

module.exports = { loadSources, buildRace, hitTime, applyStyle, classLength };
if (require.main === module) main();
