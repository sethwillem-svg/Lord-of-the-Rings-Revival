#!/usr/bin/env node
/* Builds the NPC rig and each race's clip set for PulseLib's GeckoLib loader.
 *   node tools/anim-viewer/build_npc.js     writes, under addon/src/main/resources/assets/lotr_revival/geckolib/,
 *     models/entity/npc_biped_<race>.geo.json, animations/entity/npc_biped_<race>.animation.json and .hits.json
 * hits.json: { "<attack>": { length, hit, fadeIn, fadeOut, clip, style, missesBar?, lead?, apex, tame, ready } } — times in
 * seconds of clip time: length; hit, where in the fast part of the swing the weapon's tip is most directly in front of the
 * NPC (gecko/strike.js), which the server times damage by (combat.AttackTiming); fadeIn/fadeOut, the blend over the
 * stance; lead (only with a lead-in from standing), its end; apex, the end of the wind-up; tame, the hold there (0 where
 * dropped); ready, the ease back to the ready stance (0 if none). clip is the source clip picked, style the share of the
 * race's fighting style kept, missesBar (only when set) that no clip met the quality bar. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { RIG } = require('./gecko/rig.js');
const { geoJson, animationJson } = require('./gecko/write.js');
const { hitTime: strikeHit, speeds: tipSpeeds, acceptable, strike, hingesOk, cleanSwing, waistOk, clearOfBody, spacing, bodyHitAfter, fadeFor, quality, blade: bladeAt, DIR: STRIKE_DIR } = require('./gecko/strike.js');
const { trueRotation, fromTrue, mul3 } = require('./gecko/write.js');
const { samplePose, worldBones, point } = require('./gecko/sample.js');
const { importAnimationsPlus, mirror } = require('./import/animationsplus.js');
const { importMoBends } = require('./import/mobends.js');
const { importSeriousPlayer } = require('./import/seriousplayer.js');
const { importEmote } = require('./import/emotecraft.js');
const { RACES, SLOTS, STYLE, POOL, KEEP } = require('./npc_slots.js');
const { hingeify, lowPass } = require('./hinge.js');

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
  clip.fightK = k;   // finishAttack draws the wind-up back after posture (drawBack) at this strength
  const cls = slot.split('_')[1], hit = strikeHit(animationJson({ c: clip }).animations.c, classLength(slot));
  const rot = (bone) => clip.bones[bone] && clip.bones[bone].rotation;
  const at = (keys, t) => valueAt(keys, t, clip.length, false);
  // wind-up and follow-through on the arms (a body-driven turn is `twist`): away from (or toward) the strike pose
  const arms = TWO_HANDED.has(cls) ? ['right_arm', 'weapon_r', 'left_arm', 'weapon_l'] : ['right_arm', 'weapon_r'];
  for (const bone of arms) {
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
  if (f.low && rot('right_arm')) scaleKeys(rot('right_arm'), (key) => [key.v[0] + f.low * k, key.v[1], key.v[2]]);   // lower throughout: no extra travel
  if (f.lead) {   // + the hips (upper) go first, - the arm trails the body
    const shift = (bone, d) => rot(bone) && scaleKeys(rot(bone), (key) => { if (key.t > 0) key.t = Math.max(0, Math.min(clip.length, key.t + d)); return key.v; });
    if (f.lead > 0) shift('upper', -f.lead * k);
    else for (const b of TWO_HANDED.has(cls) ? ['right_arm', 'right_elbow', 'weapon_r', 'left_arm', 'left_elbow', 'weapon_l'] : ['right_arm', 'weapon_r'])
      shift(b, -f.lead * k);   // two hands on one grip trail together
  }
  if (f.freeArm && !TWO_HANDED.has(cls)) {
    const arm = rot('right_arm') || [{ t: 0, v: [0, 0, 0] }], v0 = arm[0].v;
    const times = [...new Set([0, clip.length, ...arm.map((x) => x.t)])].sort((x, y) => x - y);
    const left = (clip.bones.left_arm = clip.bones.left_arm || {});
    const base = left.rotation || [{ t: 0, v: [0, 0, 0], lerp: 'linear' }];
    left.rotation = times.map((t) => {
      const r = at(arm, t), v = at(base, t).slice(), fl = (f.freeArm.flail || 0) * k;
      return { t, v: [v[0] - fl * (r[0] - v0[0]), v[1], v[2] - (f.freeArm.out || 0) * k], lerp: 'linear' };   // counter-swing front to back
    });
  }
  // uneven, not jagged: a slow sway per bone and axis (0.5-1.5 cycles over the swing, random phase), zero at the start
  if (f.jitter) for (const bone of ['right_arm', 'weapon_r', 'left_arm', 'upper'])
    if (rot(bone)) scaleKeys(rot(bone), (key) => key.v.map((x, j) => {
      const cyc = 1 + 0.5 * noise(`${slot}/${bone}/${j}/f`), ph = Math.PI * noise(`${slot}/${bone}/${j}/p`), u = key.t / Math.max(clip.length, 1e-6);
      return x + f.jitter * k * (Math.sin(2 * Math.PI * cyc * u + ph) - Math.sin(ph));
    }));
  for (const bone of Object.keys(clip.bones)) for (const ch of Object.values(clip.bones[bone])) ch.sort((x, y) => x.t - y.t);
}

/** The blade is clear of the torso and head at every key time after the fade-in as well: quality samples on a 60 Hz grid,
 *  and keys the tempo warp moved off it can carry the blade through the body between two samples. */
function clearAtKeys(anim, len) {
  const fade = fadeFor(anim, len), L = anim.animation_length, ts = new Set();
  for (const b of Object.values(anim.bones || {})) for (const ch of Object.values(b)) {
    if (ch && typeof ch === 'object' && !Array.isArray(ch)) for (const k of Object.keys(ch)) ts.add(Number(k));
  }
  // clearOfBody samples from its `from` to the clip's end: ending the clip just after t makes it judge the one moment t
  return [...ts].filter((t) => t >= fade && t <= L).every((t) => clearOfBody({ ...anim, animation_length: Math.min(L, t + 1e-6) }, len, t));
}

/** The held item (a one-block blade, as test/weapons.test.js draws every clip's) stays above the floor (y ≥ -1 px) from
 *  start to end, sampled at 60 Hz: a follow-through carried further can drive it into the ground. */
function aboveFloor(anim) {
  for (let t = 0; t <= anim.animation_length + 1e-9; t += 1 / 60) {
    const w = worldBones(samplePose(anim, Math.min(t, anim.animation_length)));
    if ([0.35, 0.65, 1].some((f) => point(w.weapon_r, bladeAt('right', 1, f))[1] < -1)) return false;
  }
  return true;
}

/** A finished attack the game can ship: it meets the quality bar (gecko/strike.js quality) with the blade clear of the
 *  body at its keys too (clearAtKeys) and above the floor (aboveFloor) and, for two-handed weapons, keeps the free hand on
 *  the grip at the strike (within 8 px of the shaft). */
function meetsBar(anim, slot) {
  const len = classLength(slot);
  return quality(anim, len) && clearAtKeys(anim, len) && aboveFloor(anim) && !(TWO_HANDED.has(slot.split('_')[1]) && strike(anim, len).leftToBlade > 8);
}

/**
 * The strongest version of a race's fighting style on an attack that still meets the bar (meetsBar): full strength, else
 * 60%, 30%, none.
 */
function fought(clip, slot, style, idle, walk) {
  for (const k of [1, 0.6, 0.3]) {
    const c = clone(clip);
    fight(c, slot, style.fight, k);
    const anim = animationJson({ c: finishAttack(c, slot, style, idle, 'right', walk) }).animations.c;   // judged as written
    if (meetsBar(anim, slot)) return { clip: c, k };
  }
  return { clip, k: 0 };
}

/** Overlays (attacks, hurt, ranged releases) play over the legs' locomotion: posture must not add bones they leave alone. */
const isOverlay = (slot) => /^attack_/.test(slot) || ['hurt', 'bow_release', 'crossbow_shoot'].includes(slot);

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
  const hunch = (style.hunch || 0) * (/^(attack_|ride_)/.test(slot) ? 0.5 : 1);   // attacks and riders straighten up halfway
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

/** Class lengths (blocks) for hit times: weapon_grips.json, fists and shields as short reaches. The file gives the weapon as
 *  drawn, pommel to tip (client.render.WeaponGrip seats the drawn handle on the fist); here the blade runs that length from
 *  the fist (gecko/strike.js blade). */
const GRIPS = JSON.parse(fs.readFileSync(path.join(__dirname, '../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json'), 'utf8'));
function classLength(attack) {
  const cls = attack.split('_')[1];
  return (GRIPS[cls] && GRIPS[cls].length) || (cls === 'unarmed' ? 0.25 : 1);
}

/** When the swing lands: in its fast part, where the weapon tip is most directly ahead (gecko/strike.js). */
const hitTime = (animation, attack = 'attack_sword_1') => strikeHit(animation, classLength(attack));

/** Smooth curves through an attack's keys (Catmull-Rom; PulseLib and gecko/sample.js both play it): no corners at the keys. */
function smooth(clip) {
  for (const ch of Object.values(clip.bones)) for (const keys of Object.values(ch)) for (const k of keys) k.lerp = 'catmullrom';
}

/** The hips turn with the torso (share of the upper body's twist): a body-driven swing, and no 70° twist over still hips. */
function hipsFollow(clip, share = 0.4) {
  const up = clip.bones.upper && clip.bones.upper.rotation;
  if (!up || up.every((k) => Math.abs(k.v[1]) < 1e-6)) return;
  const lower = (clip.bones.lower = clip.bones.lower || {});
  const own = lower.rotation || [{ t: 0, v: [0, 0, 0], lerp: 'linear' }];
  const times = [...new Set([...up.map((k) => k.t), ...own.map((k) => k.t)])].sort((a, b) => a - b);
  lower.rotation = times.map((t) => {
    const v = valueAt(own, t, clip.length, clip.loop === true).slice();
    v[1] += share * valueAt(up, t, clip.length, clip.loop === true)[1];
    return { t, v, lerp: 'linear' };
  });
}

/**
 * Rolls an edged weapon about its own length so its edge, not its flat, leads at the strike: the flat faces the arm's x
 * in the weapon frame (Minecraft seats a sword's flat sideways), turned by the roll that puts it square to the swing.
 */
function edgeAlign(clip, slot, side) {
  const bone = side === 'left' ? 'weapon_l' : 'weapon_r', ch = clip.bones[bone];
  if (!ch || !ch.rotation) return;
  const len = classLength(slot), anim = animationJson({ c: clip }).animations.c, hit = strikeHit(anim, len, side);
  const dt = 1 / 120, tipAt = (t) => point(worldBones(samplePose(anim, Math.max(0, Math.min(clip.length, t))))[bone], bladeAt(side, len, 1));
  const v = tipAt(hit + dt).map((x, i) => x - tipAt(hit - dt)[i]);
  const R = worldBones(samplePose(anim, hit))[bone].R, axis = STRIKE_DIR.map((x, i) => (i === 0 && side === 'left' ? -x : x));
  let best = 0, bestCost = Infinity;
  for (let a = -90; a <= 90; a += 1) {   // a roll of ±90° covers every flat orientation
    const Rr = eulerAxis(axis, (a * Math.PI) / 180), n = mulVec(R, mulVec(Rr, [1, 0, 0]));
    const cost = Math.abs(n[0] * v[0] + n[1] * v[1] + n[2] * v[2]) + 1e-4 * Math.abs(a);
    if (cost < bestCost) { bestCost = cost; best = a; }
  }
  if (!best) return;
  const Rr = eulerAxis(axis, (best * Math.PI) / 180);
  for (const k of ch.rotation) k.v = fromTrue(mul3(trueRotation(k.v), Rr));   // about the weapon pivot: positions stay
}
const mulVec = (R, v) => [0, 1, 2].map((r) => R[r * 3] * v[0] + R[r * 3 + 1] * v[1] + R[r * 3 + 2] * v[2]);
function eulerAxis(u, a) {
  const c = Math.cos(a), s = Math.sin(a), t = 1 - c, [x, y, z] = u;
  return [t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x, t * x * z - s * y, t * y * z + s * x, t * z * z + c];
}

/**
 * How long an overlay fades in and out (s): long enough that blending from standing (idle, walk) into the first pose, and
 * from the last pose back, moves the weapon no faster than the swing itself; the fade-in ends before the hit.
 */
function fades(anim, attack, base, len, side = 'right') {
  const bone = side === 'left' ? 'weapon_l' : 'weapon_r', tip = bladeAt(side, len, 1);
  const at = (a, t) => point(worldBones(samplePose(a, t))[bone], tip);
  let peak = 0;
  for (let t = 1 / 120; t <= anim.animation_length; t += 1 / 120) peak = Math.max(peak, Math.hypot(...at(anim, t).map((x, i) => x - at(anim, t - 1 / 120)[i])) * 120);
  const dist = (p) => Math.max(...base.map((b) => Math.hypot(...p.map((x, i) => x - at(b, 0)[i]))));
  const hit = strikeHit(anim, len, side), round = (x) => Math.ceil(x * 20) / 20;   // whole ticks
  // the lead-in (leadIn) keeps it gentle, judged before the race's tempo; a tempo that brings the hit forward shortens this
  // fade, so it is lengthened a tick at a time (to 0.15 s, still ending before the hit) while the blend from standing would
  // outrun the swing. The swing's peak here is on the 60 Hz grid: finer sampling only finds it faster.
  let grid = 0;
  for (let t = 1 / 60; t <= anim.animation_length + 1e-9; t += 1 / 60) grid = Math.max(grid, Math.hypot(...at(anim, t).map((x, i) => x - at(anim, t - 1 / 60)[i])) * 60);
  let fadeIn = Math.min(0.1, Math.max(0.05, Math.floor(0.8 * hit * 20) / 20));
  while (dist(at(anim, 0)) / fadeIn > grid && fadeIn + 0.05 <= 0.15 + 1e-9 && fadeIn + 0.05 < hit) fadeIn = Math.round((fadeIn + 0.05) * 20) / 20;
  const fadeOut = Math.min(Math.max(0.15, round(dist(at(anim, anim.animation_length)) / peak)), 0.5);
  return { fadeIn, fadeOut };
}

/**
 * Anticipation: an attack whose first pose is far from standing gets a lead-in, from the standing pose (idle's first frame)
 * into its first pose, at no more than 30% of the swing's top speed (a slow wind-up, as animation craft has it). The overlay then fades in from a pose it is already in,
 * and the strike keeps its own timing after the lead-in.
 */
function leadIn(clip, idle, slot, side = 'right', walk = null) {
  const bone = side === 'left' ? 'weapon_l' : 'weapon_r', len = classLength(slot), tipRest = bladeAt(side, len, 1);
  const anim = animationJson({ c: clip }).animations.c, rest = animationJson({ i: idle }).animations.i;
  const moving = walk ? animationJson({ w: walk }).animations.w : null;
  const tip = (a, t) => point(worldBones(samplePose(a, t))[bone], tipRest);
  let peak = 0;
  for (let t = 1 / 120; t <= clip.length; t += 1 / 120) peak = Math.max(peak, Math.hypot(...tip(anim, t).map((x, i) => x - tip(anim, t - 1 / 120)[i])) * 120);
  const from = (a) => Math.hypot(...tip(anim, 0).map((x, i) => x - tip(a, 0)[i]));
  const dist = Math.max(from(rest), moving ? from(moving) : 0), hit = strikeHit(anim, len, side);
  const fade = Math.min(0.1, Math.max(0.05, Math.floor(0.8 * hit * 20) / 20));   // as fades() will set it
  if (peak <= 0 || dist / fade <= peak) return clip;   // the fade alone is gentle enough
  // from standing at no more than 30% of the swing's speed, and late enough that the full 0.1 s fade fits before the hit
  const T = Math.ceil(Math.max(from(rest) / (0.3 * peak), 0.13 - hit) * 20) / 20;
  const c = clone(clip);
  for (const [bone2, ch] of Object.entries(c.bones)) for (const [kind, keys] of Object.entries(ch)) {
    const fromIdle = idle.bones[bone2] && idle.bones[bone2][kind] ? valueAt(idle.bones[bone2][kind], 0, idle.length, true).slice() : [0, 0, 0];
    for (const k of keys) k.t += T;
    keys.unshift({ t: 0, v: fromIdle, lerp: keys[0].lerp });
  }
  c.length += T;
  c.lead = T;   // recorded in hits.json: where the authored swing starts
  return c;
}

/**
 * Where the weapon tip starts a swing (at `from` s, the lead-in's end): its azimuth about the body axis from straight ahead
 * (degrees), unwrapped by following the tip back from the hit (past ±180: it came round the other side), and its z (+ is
 * behind the body).
 */
function startOf(anim, len, hit, from = 0) {
  const tipRest = bladeAt('right', len, 1), tip = (t) => point(worldBones(samplePose(anim, t)).weapon_r, tipRest);
  const az = (t) => { const p = tip(t); return Math.atan2(p[0], -p[2]); };
  let prev = az(hit), sum = (prev * 180) / Math.PI;
  for (let t = hit - 1 / 60; t > from - 1 / 60 + 1e-9; t -= 1 / 60) {
    const now = az(Math.max(from, t));
    let d = ((now - prev) * 180) / Math.PI;
    d -= 360 * Math.round(d / 360);
    sum += d; prev = now;
  }
  return { az: sum, z: tip(from)[2] };
}

/** Does start p lie further back than start q: on the same side, at least as far round from the front (and no more than
 *  BACK_LIMIT), and not moved toward the front (z)? */
const BACK_LIMIT = 150;
const furtherBack = (p, q) => Math.sign(p.az) === Math.sign(q.az) && Math.abs(p.az) >= Math.abs(q.az) - 1e-9 && Math.abs(p.az) <= BACK_LIMIT && p.z >= q.z - 1e-9;

/**
 * The wind-up drawn further back after posture (STYLE fight.windupAfter, times the style's strength): each arm key before
 * the hit moves away from the hit pose by share w of its distance (as fight's windup, but on the postured clip, whose
 * offsets would otherwise undo much of it). The Euler push does not always go back: on some clips it swings the start
 * round the other side or down in front. So the largest of w, 7/8 w, ... w/8 is taken whose swing starts further back
 * (furtherBack) than the unpushed one, within 150° of the front; none if no push does. This is judged on the postured clip;
 * finishAttack checks it again on the finished one.
 */
function drawBack(clip, slot, w) {
  const cls = slot.split('_')[1], len = classLength(slot);
  const hit = strikeHit(animationJson({ c: clip }).animations.c, len);
  const arms = TWO_HANDED.has(cls) ? ['right_arm', 'weapon_r', 'left_arm', 'weapon_l'] : ['right_arm', 'weapon_r'];
  const pushed = (sh) => {
    const c = clone(clip);
    for (const bone of arms) {
      const keys = c.bones[bone] && c.bones[bone].rotation;
      if (!keys) continue;
      const vHit = valueAt(keys, hit, c.length, false).slice();
      scaleKeys(keys, (key) => (key.t < hit ? key.v.map((x, j) => x + (x - vHit[j]) * sh * Math.min(1, (hit - key.t) / (0.4 * hit + 1e-6))) : key.v));
    }
    return c;
  };
  const start = (c) => startOf(animationJson({ c }).animations.c, len, hit);
  const s0 = start(clip);
  if (!(w > 0) || Math.abs(s0.az) > BACK_LIMIT) return clip;
  for (let i = 8; i >= 1; i--) {
    const c = pushed((w * i) / 8);
    if (furtherBack(start(c), s0)) return c;
  }
  return clip;
}

/**
 * The finishing steps every attack goes through after its race's fighting style: posture, a lead-in from standing, the
 * hips following the torso, smooth curves, true hinges resampled evenly, and the edge leading. The style check (fought)
 * judges this exact output.
 */
function finishAttack(clip, slot, style, idle, side = 'right', walk = null) {
  const c = clone(clip);
  applyStyle(c, slot, style);
  const w = style.fight && style.fight.windupAfter && c.fightK ? style.fight.windupAfter * c.fightK : 0;
  const back = w ? drawBack(c, slot, w) : c;
  if (back === c) return finishPostured(c, slot, style, idle, side, walk);
  // the drawn-back wind-up, kept only if the finished swing still starts further back than it would without it
  const len = classLength(slot), at = (x) => { const a = animationJson({ c: x }).animations.c; return startOf(a, len, strikeHit(a, len, side), x.lead || 0); };
  const pushed = finishPostured(back, slot, style, idle, side, walk), plain = finishPostured(c, slot, style, idle, side, walk);
  return furtherBack(at(pushed), at(plain)) ? pushed : plain;
}

/** finishAttack's steps after posture (and any drawn-back wind-up). */
function finishPostured(c, slot, style, idle, side, walk) {
  hipsFollow(c);
  smooth(c);
  c = hingeify(c, { rate: 60, filter: true });
  const len = classLength(slot), anim = () => animationJson({ c }).animations.c;
  // corners left by posture or style: up to three more smoothing passes
  for (let i = 0; i < 3 && !cleanSwing(anim(), len, fadeFor(anim(), len, side), side); i++) for (const ch of Object.values(c.bones)) for (const keys of Object.values(ch)) lowPass(keys);
  // a lead-in from standing, judged on this swing's own first pose and hit
  if (idle) c = leadIn(c, idle, slot, side, walk);
  // a wind-up hurried against its strike (under 3:1) is drawn out, until it is not
  for (let i = 0; i < 3; i++) {
    const ratio = spacing(anim(), len, fadeFor(anim(), len, side), side);
    if (ratio >= 3) break;
    stretchWindup(c, strikeHit(anim(), len, side) - 0.15, (3 * 1.08) / ratio);
  }
  // a follow-through that would carry the blade into the body ends just before it
  const hit = strikeHit(anim(), len, side), into = bodyHitAfter(anim(), len, hit + 0.05, side);
  if (into !== null) trimTo(c, Math.max(hit + 0.05, into - 1 / 60));
  edgeAlign(c, slot, side);
  return tempo(c, slot, tempoFor(slot, style), idle, side);
}

/**
 * The tempo an attack takes: its race's, except unarmed blows, which every race throws on man's timing with a 0.03 s tame
 * (still easing back into its own ready stance).
 */
const tempoFor = (slot, style) => (/^attack_unarmed_/.test(slot)
  ? { ...STYLE.man.tempo, tame: 0.03, ready: !!(style.tempo && style.tempo.ready) } : style.tempo);

/**
 * The end of a swing's wind-up (s) and whether the tip is slow there: the slowest turn (a local minimum of the weapon tip's
 * speed) between lead + 0.02 s and hit − 0.05 s, if it is under half the strike's peak. A swing with none (a thrust that only
 * speeds up) takes hit − 0.1 s, slow only if the tip happens to be under half the peak there.
 */
function apexScan(anim, len, side, hit, lead = 0) {
  const L = anim.animation_length, from = lead + 0.02, to = hit - 0.05;
  const s = tipSpeeds(anim, len, side, Math.max(0, lead / L), Math.min(1, (hit + 0.05) / L));
  const peak = Math.max(0, ...s.map((x) => x.v));
  let best = null;
  for (let i = 1; i < s.length - 1; i++) {
    if (s[i].t < from - 1e-9 || s[i].t > to + 1e-9 || s[i].v > s[i - 1].v || s[i].v > s[i + 1].v) continue;
    if (!best || s[i].v <= best.v) best = s[i];   // the last of equally slow moments: the end of a still stretch
  }
  if (best && best.v < 0.5 * peak) return { apex: best.t, slow: true };
  const apex = Math.max(0, hit - 0.1), at = s.reduce((a, x) => (Math.abs(x.t - Math.max(lead, apex)) < Math.abs(a.t - Math.max(lead, apex)) ? x : a), s[0]);
  return { apex, slow: !!at && at.v < 0.5 * peak };
}
const apexOf = (anim, len, side, hit, lead = 0) => apexScan(anim, len, side, hit, lead).apex;

/**
 * Where source time x lands under a race's tempo t (pure). Wind-up W = [lead, apex], strike S = [apex, hit + 0.05 s] and
 * recovery R = [hit + 0.05 s, end] are each scaled (t.windup, t.strike, t.recover) and eased (tsume, p ≥ 1: g maps new
 * progress to source progress, 1 − (1 − u)^p slowing into the apex and settling after the hit, u^p bursting to the hit;
 * keys move by its inverse); the tame (t.tame s) sits between W and S, and the lead-in is untouched. Times come back
 * rounded to 1e-12 s, so poses that meet land on the same instant (the files keep four decimals).
 */
function warp(x, { lead, apex, hit, end, t }) {
  const p = t.tsume || {}, b = Math.min(end, hit + 0.05);
  const W = apex - lead, S = b - apex, R = end - b;
  const w = W * (t.windup || 1), st = S * (t.strike || 1), rc = R * (t.recover || 1), tame = t.tame || 0;
  const ease = (u, q, out) => (out ? 1 - Math.pow(1 - u, 1 / (q || 1)) : Math.pow(u, 1 / (q || 1)));   // g⁻¹: source → new
  let y;   // a branch is reached only when its phase has length
  if (x <= lead) y = x;
  else if (x <= apex) y = lead + w * ease((x - lead) / W, p.windup, true);
  else if (x <= b) y = lead + w + tame + st * ease((x - apex) / S, p.strike, false);
  else if (x <= end) y = lead + w + tame + st + rc * ease((x - b) / R, p.recover, true);
  else y = lead + w + tame + st + rc + (x - end) * (t.recover || 1);
  return Math.round(y * 1e12) / 1e12;
}

/**
 * A race's tempo (STYLE.tempo) on a finished attack: the tame-tsume warp around its apex and hit (warp), the apex pose
 * held for the tame (none where the tip is still fast there or the wind-up is under 0.05 s), and an optional ease back into
 * the ready stance. Sets c.apex (s, new time), c.tame and c.ready (s).
 * fixed = { apex, hit } stands in for the measured ones (tests).
 */
function tempo(clip, slot, t, idle, side = 'right', fixed = null) {
  if (!t) return clip;
  const c = clone(clip), len = classLength(slot), lead = c.lead || 0;
  const src = fixed ? null : animationJson({ c: clip }).animations.c;
  const hit = fixed ? fixed.hit : strikeHit(src, len, side);
  const scan = fixed ? { apex: fixed.apex, slow: true } : apexScan(src, len, side, hit, lead);
  const apex = Math.min(hit, Math.max(lead, scan.apex));
  // a tame only where the tip is slow at the apex (a fast blade would freeze mid-swing) and on a wind-up of 0.05 s or more
  // (on a shorter one the hold would swallow the hit)
  const tame = scan.slow && apex - lead >= 0.05 - 1e-9 ? t.tame || 0 : 0;
  const at = { lead, apex, hit, end: clip.length, t: { ...t, tame } }, top = warp(apex, at);
  for (const ch of Object.values(c.bones)) for (const [kind, keys] of Object.entries(ch)) {
    const atApex = valueAt(keys, apex, clip.length, false).slice();
    for (const k of keys) k.t = warp(k.t, at);
    if (tame > 0) {   // the pose at the top of the wind-up, held
      keys.push({ t: top, v: atApex.slice(), lerp: 'linear' }, { t: top + tame, v: atApex.slice(), lerp: 'linear' });
      keys.sort((x, y) => x.t - y.t);
      ch[kind] = keys.filter((k, i, arr) => i === 0 || k.t - arr[i - 1].t > 1e-6 || k.t < top - 1e-6 || k.t > top + tame + 1e-6);
    }
  }
  c.length = warp(clip.length, at);
  c.apex = top;
  c.tame = tame;
  c.ready = 0;
  if (t.ready && idle) {   // ease back into the ready stance, no faster than a third of the swing
    const anim = animationJson({ c }).animations.c, rest = animationJson({ i: idle }).animations.i;
    const bone = side === 'left' ? 'weapon_l' : 'weapon_r', tipRest = bladeAt(side, len, 1);
    const tip = (x, tt) => point(worldBones(samplePose(x, tt))[bone], tipRest);
    let peak = 0;
    for (let x = 1 / 120; x <= c.length; x += 1 / 120) peak = Math.max(peak, Math.hypot(...tip(anim, x).map((v, i) => v - tip(anim, x - 1 / 120)[i])) * 120);
    const dist = Math.hypot(...tip(anim, c.length).map((v, i) => v - tip(rest, 0)[i]));
    const T = Math.max(0.2, Math.ceil((dist / (0.33 * Math.max(peak, 1))) * 20) / 20);
    for (const [bone2, ch] of Object.entries(c.bones)) for (const [kind, keys] of Object.entries(ch)) {
      const to = idle.bones[bone2] && idle.bones[bone2][kind] ? valueAt(idle.bones[bone2][kind], 0, idle.length, true).slice() : [0, 0, 0];
      keys.push({ t: c.length + T, v: to, lerp: 'catmullrom' });
    }
    c.length += T;
    c.ready = T;
  }
  return c;
}

/** Slows everything before `until` by `factor` (keys there spread out; later keys move back by the added time). */
function stretchWindup(clip, until, factor) {
  if (until <= 0) return;
  const added = until * (factor - 1);
  if (clip.lead) clip.lead = clip.lead < until ? clip.lead * factor : clip.lead + added;   // the lead-in's end moves with its keys
  for (const ch of Object.values(clip.bones)) for (const keys of Object.values(ch)) for (const k of keys) k.t = k.t < until ? k.t * factor : k.t + added;
  clip.length += added;
}

/** Ends a clip at time t (keys after it replaced by the pose at t). */
function trimTo(clip, t) {
  if (t >= clip.length) return;
  for (const ch of Object.values(clip.bones)) for (const [kind, keys] of Object.entries(ch)) {
    const v = valueAt(keys, t, clip.length, false).slice();
    const kept = keys.filter((k) => k.t < t - 1e-9);
    kept.push({ t, v, lerp: 'linear' });
    ch[kind] = kept;
  }
  clip.length = t;
}

/** Clips that get a shield version (<name>_shield): movement, the guard, and every attack. */
const SHIELD_SLOTS = /^(idle(_\d)?|walk|run|march|guard|attack_[a-z]+_\d+)$/;
const SHIELD_BONES = ['left_arm', 'left_elbow', 'weapon_l'];

/** The off arm holding a shield up in front: AnimationsPlus' left-hand block, with the race's arm posture. */
function shieldArm(sources, style) {
  let hold = holdAt(clone(sources.animationsplus.blockL), 0);
  applyStyle(hold, 'shield_brace', style);
  hold = hingeify(hold);
  return Object.fromEntries(SHIELD_BONES.map((b) => [b, hold.bones[b] || { rotation: [{ t: 0, v: [0, 0, 0], lerp: 'linear' }] }]));
}

/** The same clip with the off arm replaced by the held shield. */
function withShield(clip, arm) {
  const c = clone(clip);
  for (const b of SHIELD_BONES) c.bones[b] = clone(arm[b]);
  return c;
}

/**
 * An attack that meets the bar (meetsBar) as finished for this race, with the race's style stepped down if need be: its own
 * clip, if that keeps at least KEEP of the style (or meets the bar, for a race with no style). Otherwise the clip of its
 * class's pool (npc_slots.js POOL), its own among them, not already used by another slot, that keeps the most of the
 * style; on a tie its own clip first, then pool order, with clips that are another slot's own last (a race with no style
 * takes the first that meets the bar in that order, so it does not take another slot's own clip while a free one fits:
 * that would push the other slot off its clip in turn). A race that prefers a kind of clip (STYLE prefer) takes the first in that order that
 * meets the bar.
 */
function bestAttack(c, slot, entry, race, sources, style, clips, used, slots = SLOTS) {
  const cls = slot.split('_')[1], len = classLength(slot);
  const tryClip = (raw) => {
    const f = style.fight ? fought(raw, slot, style, clips.idle, clips.walk) : { clip: raw, k: 0 };
    const anim = animationJson({ c: finishAttack(f.clip, slot, style, clips.idle, 'right', clips.walk) }).animations.c;
    return meetsBar(anim, slot) ? f : null;
  };
  let pool = POOL[cls] || [];
  // weapon swings only: a shield bash or a fist is not lifted overhead, so those keep their own clip (and the style it takes)
  const prefer = /^(shield|unarmed)$/.test(cls) ? null : style.prefer;
  if (prefer === 'overhead') {   // the clips whose wind-up lifts the weapon highest first, its own clip among them
    const apex = (name) => {
      const a = animationJson({ c: sources.animationsplus[name] }).animations.c, h = strikeHit(a, len);
      let top = -Infinity;
      for (let t = 0; t <= h; t += 1 / 60) top = Math.max(top, point(worldBones(samplePose(a, t)).weapon_r, bladeAt('right', len, 1))[1]);
      return top;
    };
    pool = [...new Set([entry.clip, ...pool])].filter((n) => sources.animationsplus[n]).sort((x, y) => apex(y) - apex(x));
  }
  const free = (n) => !used.has(cls + '/' + n) && sources.animationsplus[n];
  const take = (n, got) => { used.add(cls + '/' + n); return { ...got, source: n }; };
  const tryName = (n) => tryClip(n === entry.clip ? c : clipFor(slot, { source: 'animationsplus', clip: n }, race, sources));
  if (prefer) {
    for (const n of pool.filter(free)) { const got = tryName(n); if (got) return take(n, got); }
  } else {
    const own = free(entry.clip) ? tryClip(c) : null;
    if (own && (!style.fight || own.k >= KEEP)) return take(entry.clip, own);
    const others = new Set(Object.entries(slots).filter(([s]) => s !== slot && /^attack_/.test(s) && s.split('_')[1] === cls).map(([, e]) => e.clip));
    const names = [...new Set(pool)].filter((n) => n !== entry.clip && free(n));
    let best = own ? { n: entry.clip, got: own } : null;
    for (const n of [...names.filter((x) => !others.has(x)), ...names.filter((x) => others.has(x))]) {
      const got = tryName(n);
      if (got && (!best || got.k > best.got.k)) best = { n, got };
      if (best && !style.fight) break;   // no style to keep: the first that meets the bar
      if (best && best.got.k >= 1) break;   // the full style: no later clip can beat it (a tie keeps the earlier one)
    }
    if (best) return take(best.n, best.got);
  }
  const f = style.fight ? fought(c, slot, style, clips.idle, clips.walk) : { clip: c, k: 0 };
  return { ...f, source: entry.clip, missesBar: true };
}

function buildRace(race, sources, slots = SLOTS) {
  const clips = {}, style = STYLE[race] || {}, shield = shieldArm(sources, style), used = new Set(), leftOf = [], meta = {};
  for (const [slot, entry] of Object.entries(slots)) {
    let c = clipFor(slot, entry, race, sources);
    if (/^attack_/.test(slot)) {   // before mirroring
      const best = bestAttack(c, slot, entry, race, sources, style, clips, used, slots);
      c = best.clip;
      meta[slot] = { clip: best.source, style: best.k, ...(best.missesBar ? { missesBar: true } : {}) };
    }
    if (/^attack_/.test(slot)) {   // the left-handed twin: the same swing mirrored, item on weapon_l
      leftOf.push(slot);   // mirrored from the finished swing below: an exact twin
    }
    if (/^attack_/.test(slot)) c = finishAttack(c, slot, style, clips.idle, 'right', clips.walk);
    else {
      applyStyle(c, slot, style);
      if (slot === 'run') groundFeet(c);   // the sprint's long airborne phase floats both feet
      hipsFollow(c);   // the hips take part of every torso twist
      c = hingeify(c);   // elbows and knees as the hinges the game draws (hinge.js)
    }
    clips[slot] = c;
    if (leftOf.includes(slot)) clips[slot + '_l'] = mirror(c);
    if (SHIELD_SLOTS.test(slot)) clips[slot + '_shield'] = withShield(c, shield);
  }
  const animations = animationJson(clips);
  const hits = {};
  const base = ['idle', 'walk'].map((n) => animations.animations[n]);
  for (const name of Object.keys(clips)) if (/^attack_[a-z]+_\d+$/.test(name)) {   // not the _l and _shield versions: same hit
    const a = animations.animations[name];
    hits[name] = { length: a.animation_length, hit: hitTime(a, name), ...fades(a, name, base, classLength(name)), ...meta[name], ...(clips[name].lead ? { lead: +clips[name].lead.toFixed(3) } : {}),
      apex: +(clips[name].apex || 0).toFixed(3), tame: +(clips[name].tame || 0).toFixed(3), ready: +(clips[name].ready || 0).toFixed(3) };
  }
  return { geo: geoJson(RIG), animations, hits, clips };
}

/** The attacks whose source clip differs from the race's hits.json on disk, as "attack: old -> new" (none if no file).
 *  Clips are picked after the race's tempo is applied, so a STYLE change can swap them (npc_slots.js). */
function clipChanges(race, hits) {
  let old;
  try { old = JSON.parse(fs.readFileSync(path.join(OUT, `animations/entity/npc_biped_${race}.hits.json`), 'utf8')); } catch { return []; }
  const clip = (h, n) => (h[n] && h[n].clip) || 'none';
  return [...new Set([...Object.keys(old), ...Object.keys(hits)])].filter((n) => clip(old, n) !== clip(hits, n)).map((n) => `${n}: ${clip(old, n)} -> ${clip(hits, n)}`);
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
    const r = buildRace(race, sources), changed = clipChanges(race, r.hits);
    console.log(changed.length ? `${race}: attacks that changed clip: ${changed.join(', ')}` : `${race}: no attack changed clip`);
    write(`models/entity/npc_biped_${race}.geo.json`, r.geo);
    write(`animations/entity/npc_biped_${race}.animation.json`, r.animations);
    write(`animations/entity/npc_biped_${race}.hits.json`, r.hits);
    console.log(`${race}: ${Object.keys(r.animations.animations).length} animations, ${Object.keys(r.hits).length} attacks`);
  }
}

module.exports = { loadSources, buildRace, hitTime, applyStyle, classLength, _fight: fight, _finish: finishAttack, _clipFor: clipFor, _tempo: { warp, apexOf, apply: tempo } };
if (require.main === module) main();
