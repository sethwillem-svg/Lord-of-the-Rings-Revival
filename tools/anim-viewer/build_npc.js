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
const { geoJson, animationJson, eulerMatrix, fromTrue, trueRotation, mul3 } = require('./gecko/write.js');
const { hitTime: strikeHit, speeds: tipSpeeds, acceptable, strike, hingesOk, cleanSwing, waistOk, clearOfBody, spacing, bodyHitAfter, fadeFor, quality, blade: bladeAt, DIR: STRIKE_DIR } = require('./gecko/strike.js');
const { samplePose, worldBones, point } = require('./gecko/sample.js');
const { importAnimationsPlus, mirror } = require('./import/animationsplus.js');
const { importMoBends } = require('./import/mobends.js');
const { importSeriousPlayer } = require('./import/seriousplayer.js');
const { importEmote } = require('./import/emotecraft.js');
const { RACES, SLOTS, STYLE, POOL, KEEP } = require('./npc_slots.js');
const { hingeify, lowPass, limitTwist, peakTwist } = require('./hinge.js');

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
/** The most a free arm's counter-swing (STYLE fight.freeArm.flail) turns it from where it was, degrees: 2.5 times a big swing
 *  would wind it right round the shoulder (an orc's free arm reached 600°), which no twist of the arm can make look whole. */
const FLAIL_MAX = 120;
/** How far inside the shoulder's range (extension 60°, horizontal adduction 45° past straight ahead: test/anatomy.test.js)
 *  a flung free arm is kept, degrees: room for the jitter, the posture and the curves between keys. */
const FLUNG_MARGIN = 10;
/** The most of a free arm's throw back (a counter-swing to a blow forward) taken front to back, degrees: the rest flings it
 *  out to its side (FLUNG_OUT), where the shoulder has the room. */
const FLUNG_BACK = 45;
/** Where a free arm flung out goes, in the torso's frame (x to the right, y up, z back): out to its left and a little up. */
const FLUNG_OUT = (() => { const l = Math.hypot(1, 0.2); return [-1 / l, 0.2 / l, 0]; })();
const angleTo = (a, b) => (Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]))) * 180) / Math.PI;
/**
 * The free (left) arm's key v thrown `swing` degrees front to back (+ forward) and held `out` degrees away from the body.
 * The old counter-swing threw it straight back by the whole swing, round behind the back and over into the head. Now a
 * throw forward keeps as much of itself as stays FLUNG_MARGIN inside the shoulder's range, and a throw back at most
 * FLUNG_BACK (less where that would leave the range); what is left of the throw turns the arm toward FLUNG_OUT (no further),
 * so a big throw flings the arm out and away from the body. The arm hangs from the body bone, so its direction in the
 * torso is its own key's (before posture, which only holds it further forward and out). Returns the key.
 */
function flung(v, swing, out) {
  const dirOf = (R) => [-R[1], -R[4], -R[7]];   // the arm's long axis (rest -y)
  const fits = (R) => { const s = shoulderOf(R); return s.back <= SHOULDER_RANGE.back - FLUNG_MARGIN && s.across <= SHOULDER_RANGE.across - FLUNG_MARGIN; };
  const key = (f) => {   // share f of the throw front to back, the rest toward FLUNG_OUT
    const c = [v[0] - swing * f, v[1], v[2] - out], R = trueRotation(c), d = dirOf(R);
    const a = Math.min(Math.abs(swing) * (1 - f), angleTo(d, FLUNG_OUT));
    const n = [d[1] * FLUNG_OUT[2] - d[2] * FLUNG_OUT[1], d[2] * FLUNG_OUT[0] - d[0] * FLUNG_OUT[2], d[0] * FLUNG_OUT[1] - d[1] * FLUNG_OUT[0]];
    const l = Math.hypot(...n);
    if (a < 1e-6 || l < 1e-9) return { c, R };
    const T = mul3(eulerAxis(n.map((x) => x / l), (a * Math.PI) / 180), R), k = fromTrue(T);
    return { c: nearKey(k, c), R: T };   // the key nearest the unturned one
  };
  const top = swing < 0 ? Math.min(1, FLUNG_BACK / -swing) : 1;
  for (let i = 20; i > 0; i--) { const r = key((i / 20) * top); if (fits(r.R)) return r.c; }
  return key(0).c;
}
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
      // counter-swing front to back, at most FLAIL_MAX either way, flung out where the shoulder would not take it (flung)
      const swing = Math.max(-FLAIL_MAX, Math.min(FLAIL_MAX, fl * (r[0] - v0[0])));
      return { t, v: flung(v, swing, (f.freeArm.out || 0) * k), lerp: 'linear' };
    });
    // a flung arm swings out straight: the elbow opens with the throw, fully at FLAIL_MAX
    const elbow = clip.bones.left_elbow && clip.bones.left_elbow.rotation;
    if (elbow && f.freeArm.flail) {
      const own = elbow.map((x) => ({ ...x, v: x.v.slice() }));
      clip.bones.left_elbow.rotation = [...new Set([...times, ...own.map((x) => x.t)])].sort((x, y) => x - y).map((t) => {
        const throwOf = Math.min(FLAIL_MAX, Math.abs((f.freeArm.flail || 0) * k * (at(arm, t)[0] - v0[0])));
        return { t, v: at(own, t).map((x) => x * (1 - throwOf / FLAIL_MAX)), lerp: 'linear' };
      });
    }
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

/** Edged classes. A double-edged blade (a longsword's, a dagger's, a spearhead) cuts the same with either edge, so a half
 *  turn about it is the same cut. A sword clip may hold a scimitar (Middle-earth files them as swords: combat.WeaponClass),
 *  curved with one edge, so swords count as single-edged, like axes. Shields and fists have none. */
const DOUBLE_EDGED = new Set(['longsword', 'dagger', 'spear']), SINGLE_EDGED = new Set(['sword', 'axe']);
/** A single edge, in the weapon frame: Minecraft seats a held item blade forward and up from the fist with its edge below
 *  it, toward the front, square to the blade (STRIKE_DIR) in the flat's plane. */
const EDGE = [0, -Math.SQRT1_2, -Math.SQRT1_2];
/** Seconds either side of the hit, as played (after the tempo), through which an edge keeps the roll that leads it: the
 *  game tick before and after it. */
const EDGE_HOLD = 0.05;

/**
 * Rolls an edged weapon about its own length so its edge, not its flat, leads at the strike (at `hit`, s on this clip's
 * clock): the flat faces the arm's x in the weapon frame (Minecraft seats a sword's flat sideways), turned by the shortest
 * roll that puts it square to the swing. A double-edged blade may take that roll half a turn the other way instead,
 * whichever leaves less twist; a single-edged one takes whichever of the two leads with its edge (EDGE). The roll moves the
 * wrist's twist, so the limbs are then brought back within their twist range (hinge.js limitTwist), the weapon keeping
 * its roll through `hold` ([from, to] s on this clip's clock: the game tick either side of the hit as played) and, with
 * `anatomy`, within the shoulder's range and out of the body as far as a swivel can bring them. Returns the clip (a new
 * one when anything changed).
 */
function edgeAlign(clip, slot, side, hit, hold = [hit, hit], anatomy = false) {
  const bone = side === 'left' ? 'weapon_l' : 'weapon_r', ch = clip.bones[bone], cls = slot.split('_')[1];
  // the hands may roll what they hold about its length where no swivel keeps the wrist in range; an edge keeps its roll
  // through the strike
  const roll = { weapon_r: STRIKE_DIR, weapon_l: [-STRIKE_DIR[0], STRIKE_DIR[1], STRIKE_DIR[2]] };
  const edged = DOUBLE_EDGED.has(cls) || SINGLE_EDGED.has(cls);
  const limit = (c, a = 0) => limitTwist(c, { roll, held: [bone], keep: edged ? { [bone]: hold } : {}, preRoll: { [bone]: a }, anatomy });
  if (!ch || !ch.rotation || !edged) return limit(clip);
  const len = classLength(slot), anim = animationJson({ c: clip }).animations.c;
  const dt = 1 / 120, tipAt = (t) => point(worldBones(samplePose(anim, Math.max(0, Math.min(clip.length, t))))[bone], bladeAt(side, len, 1));
  const v = tipAt(hit + dt).map((x, i) => x - tipAt(hit - dt)[i]);
  const R = worldBones(samplePose(anim, hit))[bone].R, axis = STRIKE_DIR.map((x, i) => (i === 0 && side === 'left' ? -x : x));
  let best = 0, bestCost = Infinity;
  for (let a = -90; a <= 90; a += 1) {   // a roll of ±90° covers every flat orientation: the shorter way round
    const Rr = eulerAxis(axis, (a * Math.PI) / 180), n = mulVec(R, mulVec(Rr, [1, 0, 0]));
    const cost = Math.abs(n[0] * v[0] + n[1] * v[1] + n[2] * v[2]) + 1e-4 * Math.abs(a);
    if (cost < bestCost) { bestCost = cost; best = a; }
  }
  // rolled in the world at every sample (limitTwist preRoll), about the weapon pivot: its place and its path between keys stay
  const rolled = (a) => limit(clip, a);
  const flip = best > 0 ? best - 180 : best + 180;
  if (SINGLE_EDGED.has(cls)) {   // the roll, or the half turn from it, that leads with the edge
    const edge = mulVec(R, mulVec(eulerAxis(axis, (best * Math.PI) / 180), side === 'left' ? [-EDGE[0], EDGE[1], EDGE[2]] : EDGE));
    return rolled(edge[0] * v[0] + edge[1] * v[1] + edge[2] * v[2] >= 0 ? best : flip);
  }
  const tried = [best, flip].map((a) => ({ a, c: rolled(a) })).map((x) => ({ ...x, tw: peakTwist(x.c, anatomy) }));
  // least past range first, then (with `anatomy`) the arms least past theirs, then the wrist twisted least, then the
  // shorter roll
  const near = (x, y) => Math.abs(x - y) <= 1e-6;
  const better = (x, b) => x.tw.past < b.tw.past - 1e-6 || (near(x.tw.past, b.tw.past)
    && ((anatomy && x.tw.anatomy < b.tw.anatomy - 1e-6) || ((!anatomy || near(x.tw.anatomy, b.tw.anatomy)) && x.tw.end < b.tw.end - 1e-6)));
  return tried.reduce((b, x) => (better(x, b) ? x : b)).c;
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
 * judges this exact output, without `anatomy`: that (the arms swivelled within the shoulder's range and out of the body,
 * hinge.js limitTwist opts.anatomy, and a one-handed attack's upper arms first re-posed within the shoulder's range away
 * from the strike, repose) is added once the clip is chosen, on the clip that ships (buildRace checks it still meets the
 * bar). The swivel leaves the hands and weapon where they are; the re-posing moves them only outside the game tick either
 * side of the hit. Both are slow enough to build that the many clips tried while choosing would take minutes more.
 */
function finishAttack(clip, slot, style, idle, side = 'right', walk = null, anatomy = false) {
  const c = clone(clip);
  applyStyle(c, slot, style);
  const w = style.fight && style.fight.windupAfter && c.fightK ? style.fight.windupAfter * c.fightK : 0;
  const back = w ? drawBack(c, slot, w) : c;
  if (back === c) return finishPostured(c, slot, style, idle, side, walk, anatomy);
  // the drawn-back wind-up, kept only if the finished swing still starts further back than it would without it
  const len = classLength(slot), at = (x) => { const a = animationJson({ c: x }).animations.c; return startOf(a, len, strikeHit(a, len, side), x.lead || 0); };
  const pushed = finishPostured(back, slot, style, idle, side, walk, anatomy), plain = finishPostured(c, slot, style, idle, side, walk, anatomy);
  return furtherBack(at(pushed), at(plain)) ? pushed : plain;
}

/** finishAttack's steps after posture (and any drawn-back wind-up). */
function finishPostured(c, slot, style, idle, side, walk, anatomy = false) {
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
  // the edge leads on the pose the finished attack strikes with: the tempo moves the hit. The tempo is timed on the swing
  // as it stands: the roll and the twist limit after it leave the weapon's path where it is, but Euler keys can stray from
  // it between them by a fraction of a pixel, enough to move a slow apex
  const t = tempoFor(slot, style), at = t ? warpOf(c, slot, t, side) : null, s = strikePose(c, slot, t, at, idle, side);
  const plain = () => tempo(edgeAlign(c, slot, side, s.hit, s.hold, anatomy), slot, t, idle, side, null, at);
  if (!anatomy || TWO_HANDED.has(slot.split('_')[1])) return plain();
  // with the arms' anatomy, a one-handed attack's arms re-posed within the shoulder's range first (repose: toward hanging,
  // or toward hanging or straight ahead), whichever leaves them least past it, where the swing so finished still meets the
  // bar, no arm snaps round (snaps) and it leaves them less past than they were; else as it is. Two-handed grips are left:
  // turning one arm would take its hand off the shaft
  let best = null;
  for (const goals of REPOSE_GOALS) {
    const r = repose(c, s.hold, goals);
    if (r === c) continue;
    const done = tempo(edgeAlign(r, slot, side, s.hit, s.hold, anatomy), slot, t, idle, side, null, at), a = animationJson({ c: done }).animations.c;
    if (!meetsBar(a, slot) || snaps(a)) continue;
    const past = mostPast(a);
    if (!best || past < best.past - 1e-9) best = { done, past };
  }
  if (!best) return plain();
  const own = plain(), ownPast = mostPast(animationJson({ c: own }).animations.c);
  return best.past < ownPast - 1e-9 ? best.done : own;
}

/** Re-posing (repose): an upper arm is re-posed where it reaches more than REPOSE_PAST degrees past the shoulder's range
 *  (test/anatomy.test.js allows 10°), back to REPOSE_PAST; the turn eases out over REPOSE_EASE s either side, and over
 *  REPOSE_HOLD s toward the hit's edge hold, inside which the swing is left exactly as it is. Through the stretch it re-poses
 *  the arm gets a key every REPOSE_STEP s, so the curves between keys stay re-posed too. */
const REPOSE_PAST = 5, REPOSE_EASE = 0.05, REPOSE_HOLD = 0.02, REPOSE_STEP = 1 / 240;
/** Where repose may turn an arm toward, in the torso's frame: hanging; or hanging or straight ahead, whichever is nearer. */
const REPOSE_GOALS = [[[0, -1, 0]], [[0, -1, 0], [0, 0, -1]]];
/** The arms' rotations in the torso's frame (Bᵀ R) at world bones w. */
function armsLocal(w) {
  const B = w.body.R;
  return ['right_arm', 'left_arm'].map((b) => {
    const R = w[b].R, L = [];
    for (let r = 0; r < 3; r++) for (let q = 0; q < 3; q++) L.push(B[r] * R[q] + B[3 + r] * R[3 + q] + B[6 + r] * R[6 + q]);
    return L;
  });
}
/** The most either upper arm of a finished attack is past the shoulder's range and REPOSE_PAST (degrees), every 2 ms. */
function mostPast(anim) {
  let m = -Infinity;
  for (let t = 0; t <= anim.animation_length + 1e-9; t += 0.002) {
    const [r, l] = armsLocal(worldBones(samplePose(anim, Math.min(t, anim.animation_length))));
    m = Math.max(m, armPast(r, 'right'), armPast(l, 'left'));
  }
  return m;
}
/** Does an upper arm of a finished attack turn more than SNAP degrees in 1/120 s against the torso anywhere (every 2 ms)?
 *  test/joints.test.js allows 60°. */
const SNAP = 50;
function snaps(anim) {
  const L = anim.animation_length, at = (t) => armsLocal(worldBones(samplePose(anim, Math.min(t, L))));
  for (let t = 0; t + 1 / 120 <= L + 1e-9; t += 0.002) {
    const a = at(t), b = at(t + 1 / 120);
    for (let i = 0; i < 2; i++) {
      let tr = 0;
      for (let k = 0; k < 9; k++) tr += a[i][k] * b[i][k];
      if (Math.acos(Math.max(-1, Math.min(1, (tr - 1) / 2))) / RAD > SNAP) return true;
    }
  }
  return false;
}
/** How far (degrees) an upper arm on `side` with local rotation L (its parent is the body bone) is past the shoulder's range
 *  and REPOSE_PAST (≤ 0: within). */
function armPast(L, side) {
  const R = side === 'left' ? L : [L[0], -L[1], L[2], L[3], L[4], L[5], L[6], L[7], L[8]], s = shoulderOf(R);   // a right arm as its mirror
  return Math.max(s.back - SHOULDER_RANGE.back, s.across - SHOULDER_RANGE.across) - REPOSE_PAST;
}
/** Of a rotation key v's two Euler triples (each up to whole turns), the one nearest `near`: keys between which a clip's
 *  Euler curves do not swing the long way round. */
function nearKey(v, near) {
  const wrap = (u) => u.map((x, j) => x + 360 * Math.round((near[j] - x) / 360)), far = (u) => Math.max(...u.map((x, j) => Math.abs(x - near[j])));
  const a = wrap(v), b = wrap([v[0] + 180, 180 - v[1], v[2] + 180]);
  return far(b) < far(a) ? b : a;
}
/**
 * The upper arms of a one-handed attack brought back within reach of the shoulder's range (REPOSE_PAST) at the keys where a
 * swing (most often a race's wind-up drawn further back) carries them further, outside the hit's edge hold (`hold`, s on
 * this clip's clock): each such arm turned, with its forearm and hand, toward one of `goals` (REPOSE_GOALS) by the least
 * angle that does it, easing in and out over REPOSE_EASE s and none inside the hold, so the strike is the swing's own. The
 * swivel (edgeAlign's anatomy) then takes the arms on from there. Returns the clip (a new one when anything changed).
 */
function repose(clip, hold, goals = REPOSE_GOALS[0]) {
  const anim = animationJson({ c: clip }).animations.c, c = clone(clip);
  const localAt = (t, bone) => {   // the arm's rotation in the body bone's frame at t, as the clip plays: Bᵀ R
    const w = worldBones(samplePose(anim, Math.min(t, clip.length))), B = w.body.R, R = w[bone].R, L = [];
    for (let r = 0; r < 3; r++) for (let q = 0; q < 3; q++) L.push(B[r] * R[q] + B[3 + r] * R[3 + q] + B[6 + r] * R[6 + q]);
    return L;
  };
  let changed = false;
  for (const side of ['right', 'left']) {
    const bone = side + '_arm';
    if (!(c.bones[bone] && c.bones[bone].rotation)) continue;
    const bad = c.bones[bone].rotation.filter((k) => armPast(localAt(k.t, bone), side) > 0);
    if (!bad.length) continue;
    const from = Math.max(0, bad[0].t - REPOSE_EASE), to = Math.min(clip.length, bad[bad.length - 1].t + REPOSE_EASE);
    const own = c.bones[bone].rotation, grid = [];
    for (let t = from; t <= to + 1e-9; t += REPOSE_STEP) if (!own.some((k) => Math.abs(k.t - t) < REPOSE_STEP / 2)) grid.push({ t, v: null, lerp: 'catmullrom' });
    const keys = [...own, ...grid].sort((x, y) => x.t - y.t);
    keys.forEach((k, i) => { if (!k.v) k.v = nearKey(fromTrue(localAt(k.t, bone)), keys[i - 1].v); });
    // each key's least turn that brings it within, toward the nearer goal (about d × goal, in the body's frame)
    const turn = keys.map((k) => {
      const L = trueRotation(k.v);
      if (armPast(L, side) <= 0) return null;
      const d = [-L[1], -L[4], -L[7]];
      let best = null;
      for (const goal of goals) {
        const n = [d[1] * goal[2] - d[2] * goal[1], d[2] * goal[0] - d[0] * goal[2], d[0] * goal[1] - d[1] * goal[0]], l = Math.hypot(...n);
        if (l < 1e-9) continue;
        const u = n.map((x) => x / l);
        for (let a = 1; a < 180 && (!best || a < best.a); a++) if (armPast(mul3(eulerAxis(u, a * RAD), L), side) <= 0) { best = { n: u, a }; break; }
      }
      return best;
    });
    // eased: each key turned about the axis of the key whose turn reaches it most, never further past than it was
    keys.forEach((k, i) => {
      const toHold = Math.min(1, Math.max(hold[0] - k.t, k.t - hold[1], 0) / REPOSE_HOLD);
      let pick = null;
      turn.forEach((x, j) => {
        const a = x ? x.a * Math.max(0, 1 - Math.abs(keys[j].t - k.t) / REPOSE_EASE) : 0;
        if (a > 0 && (!pick || a > pick.a)) pick = { a, n: x.n };
      });
      if (!pick || toHold <= 0) return;
      const L = trueRotation(k.v), was = Math.max(0, armPast(L, side));
      for (let f = 1; f > 0.05; f -= 0.1) {
        const T = mul3(eulerAxis(pick.n, pick.a * toHold * f * RAD), L);
        if (armPast(T, side) <= was + 1e-9) { k.v = nearKey(fromTrue(T), k.v); changed = true; return; }
      }
    });
    c.bones[bone].rotation = keys;
  }
  return changed ? c : clip;
}

/**
 * When, on this clip's own clock (before its race's tempo t, warp `at`), the finished attack strikes: { hit, the pose it
 * strikes with; hold, [from, to], the span played as the game tick (EDGE_HOLD) either side of that hit }. The tempo warps
 * the swing's timing unevenly, so the hit, found again on the warped swing, can land on another pose than the one found
 * before it; the warp is undone to find which (the poses themselves do not change).
 */
function strikePose(c, slot, t, at, idle, side) {
  const len = classLength(slot);
  if (!t) {
    const hit = strikeHit(animationJson({ c }).animations.c, len, side);
    return { hit, hold: [hit - EDGE_HOLD, hit + EDGE_HOLD] };
  }
  const hit = strikeHit(animationJson({ c: tempo(c, slot, t, idle, side, null, at) }).animations.c, len, side);
  const [from, to] = unwarpSpan(at, hit - EDGE_HOLD, hit + EDGE_HOLD);
  return { hit: unwarpSpan(at, hit, hit)[0], hold: [from, to] };
}

/** The source span [x0, x1] the warp `at` plays from time y0 to y1 (y in played seconds); a time in the tame's hold maps to
 *  the apex, the pose held there. warp only moves forward, so each end is found by halving. */
function unwarpSpan(at, y0, y1) {
  const back = (y) => {
    let lo = 0, hi = at.end;
    for (let i = 0; i < 50; i++) { const mid = (lo + hi) / 2; if (warp(mid, at) < y) lo = mid; else hi = mid; }
    return (lo + hi) / 2;
  };
  return [back(y0), back(y1)];
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
 * fixed = { apex, hit } stands in for the measured ones (tests); timed, a warp (warpOf) already measured on this swing.
 */
function tempo(clip, slot, t, idle, side = 'right', fixed = null, timed = null) {
  if (!t) return clip;
  const c = clone(clip), len = classLength(slot);
  const at = timed || warpOf(clip, slot, t, side, fixed), { apex, t: { tame } } = at, top = warp(apex, at);
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

/** The warp a race's tempo t puts on a clip (warp's second argument): its lead-in's end, apex, hit, end and the tempo with
 *  the tame it gets (fixed = { apex, hit } stands in for the measured ones, as in tempo). */
function warpOf(clip, slot, t, side = 'right', fixed = null) {
  const len = classLength(slot), lead = clip.lead || 0;
  const src = fixed ? null : animationJson({ c: clip }).animations.c;
  const hit = fixed ? fixed.hit : strikeHit(src, len, side);
  const scan = fixed ? { apex: fixed.apex, slow: true } : apexScan(src, len, side, hit, lead);
  const apex = Math.min(hit, Math.max(lead, scan.apex));
  // a tame only where the tip is slow at the apex (a fast blade would freeze mid-swing) and on a wind-up of 0.05 s or more
  // (on a shorter one the hold would swallow the hit)
  const tame = scan.slow && apex - lead >= 0.05 - 1e-9 ? t.tame || 0 : 0;
  return { lead, apex, hit, end: clip.length, t: { ...t, tame } };
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

/**
 * The off arm holding a shield up in front, strapped to the forearm (gecko/shield.js place, client.render.WeaponGrip): the
 * upper arm raised forward, the elbow bent so the forearm lies level across the chest 4 px in front of it, and the forearm
 * turned about its length (weapon_l, about the forearm's own axis, not the bone's pivot 2 px in front of it) so its back,
 * where the shield's face looks, is to the front and the plate sits over the chest's centre. Found by search on the rig
 * (XYZ degrees as PulseLib plays them). No race posture: the guard is set by where the shield must be.
 */
const SHIELD_POSE = { arm: [-60.4, -61.4, -104.1], elbow: 110, twist: 42.5 };
const RAD = Math.PI / 180;
const PIVOTS = Object.fromEntries(RIG.map((b) => [b.name, b.pivot]));
const FOREARM_AXIS = [0, 0, 2];   // the forearm's axis from weapon_l's pivot (rig px)
const bbKey = (xyz) => fromTrue(eulerMatrix(xyz, 'XYZ'));   // a PulseLib rotation as the clip's (Blockbench) key
/** weapon_l turned tw degrees about the forearm's axis: its rotation key, the position (rig px) that keeps the axis put,
 *  and that position as the clip's key. */
function forearmTwist(tw) {
  const p = FOREARM_AXIS.map((x, i) => x - mulVec(eulerMatrix([0, tw, 0], 'XYZ'), FOREARM_AXIS)[i]);
  return { rotation: bbKey([0, tw, 0]), pos: p, position: [-p[0], p[1], p[2]] };
}
function shieldArm() {
  const key = (v) => [{ t: 0, v, lerp: 'linear' }], w = forearmTwist(SHIELD_POSE.twist);
  return {
    left_arm: { rotation: key(bbKey(SHIELD_POSE.arm)) },
    left_elbow: { rotation: key(bbKey([SHIELD_POSE.elbow, 0, 0])) },
    weapon_l: { rotation: key(w.rotation), position: key(w.position) },
  };
}

/** The rotation (row-major 3x3) by a degrees about the unit axis k (Rodrigues). */
function axisAngle(k, a) {
  const c = Math.cos(a * RAD), s = Math.sin(a * RAD), t = 1 - c, [x, y, z] = k;
  return [t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x, t * x * z - s * y, t * y * z + s * x, t * z * z + c];
}
const transpose = (m) => [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]];
const vadd = (a, b) => a.map((x, i) => x + b[i]), vsub = (a, b) => a.map((x, i) => x - b[i]);
/** A rotation's twist about its rest y axis (degrees; the swing-twist split, as test/lib/motion.js twist measures it). */
function twistOf(m) {
  const tr = m[0] + m[4] + m[8];
  let y, w;
  if (tr > 0) { const s = Math.sqrt(tr + 1) * 2; w = s / 4; y = (m[2] - m[6]) / s; }
  else if (m[0] > m[4] && m[0] > m[8]) { const s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2; w = (m[7] - m[5]) / s; y = (m[1] + m[3]) / s; }
  else if (m[4] > m[8]) { const s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2; w = (m[2] - m[6]) / s; y = s / 4; }
  else { const s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2; w = (m[3] - m[1]) / s; y = (m[5] + m[7]) / s; }
  if (w < 0) { w = -w; y = -y; }
  return Math.hypot(y, w) < 1e-9 ? 0 : (2 * Math.atan2(y, w)) / RAD;
}

/** Every key time of a clip (s), with its start and end. */
const keyTimes = (clip) => [...new Set([0, clip.length, ...Object.values(clip.bones).flatMap((ch) => Object.values(ch).flatMap((ks) => ks.map((k) => k.t)))])]
  .filter((t) => t >= 0 && t <= clip.length + 1e-9).sort((a, b) => a - b);

/**
 * The shield arm, worked in the chest's own frame (rig px at rest; the chest's posed turn taken out): the forearm, and the
 * shield strapped to it, held at frame G; the elbow bent by e and the forearm turned by tw about its axis (weapon_l); the
 * upper arm is then the one rotation G Ry(-tw) Rx(-e), and the forearm G Ry(-tw). Returns the upper arm (left_arm's local
 * rotation), the ends of the forearm's capsule and of the upper arm's lower half (as test/lib/anatomy.js and hinge.js take
 * them), and the shift that places the plate's points (strapped) and its middle, all in the chest's frame.
 */
const TORSO = [[-4, 12, -2], [4, 24, 2]], HEAD = [[-4, 24, -4], [4, 32, 4]];   // the chest's box and the head's (rig px)
/** The arm's capsules (rig px, rest, radius LIMB_R: the arms are 4 px thick), each stopping LIMB_R short of where its part
 *  ends: the forearm from the elbow pivot to y 14 (the fist's end at 12), the upper arm's lower half from y 19 to the elbow. */
const LIMB_R = 2, FOREARM_CAP = [[-6, 18, 0], [-6, 14, 0]], UPPER_CAP = [[-6, 19, 0], [-6, 18, 0]];
/** Points over the plate's back face (rig px, rest, weapon_l's frame): the strap (gecko/shield.js place) puts its middle 4 px
 *  out from the arm's pivot, level with the fist; 14 px along the forearm by 22 px up covers both kinds of shield. */
const PLATE_PTS = [];
for (const a of [-7, -3.5, 0, 3.5, 7]) for (const u of [-11, -5.5, 0, 5.5, 11]) PLATE_PTS.push([-8.5, 12 + a, u]);
const PLATE_MID = [-9, 12, 0];
const SHIELD_OUT = [-1, 0, 0];   // the shield's face in weapon_l's frame (rig): the back of the left forearm
const rotY = (deg) => { const c = Math.cos(deg * RAD), s = Math.sin(deg * RAD); return [c, 0, s, 0, 1, 0, -s, 0, c]; };
const rotX = (deg) => { const c = Math.cos(deg * RAD), s = Math.sin(deg * RAD); return [1, 0, 0, 0, c, -s, 0, s, c]; };
const FOREARM_REL = FOREARM_CAP.map((x) => vsub(x, PIVOTS.left_elbow)), UPPER_CAP_REL = UPPER_CAP.map((x) => vsub(x, PIVOTS.left_arm));
const UPPER_REL = vsub(PIVOTS.left_elbow, PIVOTS.left_arm), WRIST_REL = vsub(vadd(PIVOTS.weapon_l, FOREARM_AXIS), PIVOTS.left_elbow);
/** What armFor needs of the forearm's frame G that does not change with the bend or the turn: the plate's points about
 *  weapon_l's pivot, and the forearm's axis, turned by G. */
function strapped(G) {
  const about = (x) => mulVec(G, vsub(x, PIVOTS.weapon_l));
  return { G, plate: PLATE_PTS.map(about), mid: about(PLATE_MID), axis: mulVec(G, FOREARM_AXIS) };
}
function armFor(pre, e, tw) {
  const AE = mul3(pre.G, rotY(-tw)), arm = mul3(AE, rotX(-e));
  const Pe = vadd(mulVec(arm, UPPER_REL), PIVOTS.left_arm);
  const fore = FOREARM_REL.map((x) => vadd(mulVec(AE, x), Pe)), upper = UPPER_CAP_REL.map((x) => vadd(mulVec(arm, x), PIVOTS.left_arm));
  // weapon_l: turned tw about the forearm's axis (forearmTwist), so x -> G (x - pivot) + AE (pivot + axis - elbow) - G axis + Pe
  const off = vsub(vadd(mulVec(AE, WRIST_REL), Pe), pre.axis);
  return { arm, fore, upper, off, mid: vadd(pre.mid, off) };
}
/** Signed distance (px) from a point to a box [min, max]: negative inside. */
function boxDist(q, box) {
  let out = 0, inMax = -Infinity;
  for (let i = 0; i < 3; i++) {
    const d = Math.abs(q[i] - (box[0][i] + box[1][i]) / 2) - (box[1][i] - box[0][i]) / 2;
    if (d > 0) out += d * d;
    if (d > inMax) inMax = d;
  }
  return out > 0 ? Math.sqrt(out) : Math.min(inMax, 0);
}
/** The least signed distance from segment a-b to a box: convex along the segment, so a golden-section search. */
const GOLD = (Math.sqrt(5) - 1) / 2;
function segDist(a, b, box) {
  const at = (f) => boxDist([a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f], box);
  let lo = 0, hi = 1, x1 = hi - GOLD, x2 = GOLD, f1 = at(x1), f2 = at(x2);
  for (let k = 0; k < 14; k++) {
    if (f1 <= f2) { hi = x2; x2 = x1; f2 = f1; x1 = hi - GOLD * (hi - lo); f1 = at(x1); }
    else { lo = x1; x1 = x2; f1 = f2; x2 = lo + GOLD * (hi - lo); f2 = at(x2); }
  }
  return Math.min(at(0), at(1), f1, f2);
}
/** segDist where it matters: a segment clearly clear of the box (by more than LIMB_R) is given a bound instead. */
function segNear(a, b, box) {
  const bound = boxDist([(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2], box) - Math.hypot(b[0] - a[0], b[1] - a[1], b[2] - a[2]) / 2;
  return bound > LIMB_R ? bound : segDist(a, b, box);
}
/** A point of the chest's frame in the head's (head: { R, p }, the head bone against the chest). */
function inHead(head, q) {
  const R = head.R, x = q[0] - head.p[0], y = q[1] - head.p[1], z = q[2] - head.p[2];
  return [R[0] * x + R[3] * y + R[6] * z, R[1] * x + R[4] * y + R[7] * z, R[2] * x + R[5] * y + R[8] * z];
}
/** boxDist of a point given as x, y, z, clear by at least `clear` px: the clear part not worked out (0). */
function boxShort(x, y, z, box, clear) {
  const dx = Math.abs(x - (box[0][0] + box[1][0]) / 2) - (box[1][0] - box[0][0]) / 2;
  const dy = Math.abs(y - (box[0][1] + box[1][1]) / 2) - (box[1][1] - box[0][1]) / 2;
  const dz = Math.abs(z - (box[0][2] + box[1][2]) / 2) - (box[1][2] - box[0][2]) / 2;
  if (dx >= clear || dy >= clear || dz >= clear) return 0;
  const o = Math.max(0, dx) ** 2 + Math.max(0, dy) ** 2 + Math.max(0, dz) ** 2;
  const d = o > 0 ? Math.sqrt(o) : Math.min(Math.max(dx, dy, dz), 0);
  return Math.max(0, clear - d) ** 2;
}
/** The shoulder's range as test/lib/anatomy.js measures it, for the left upper arm (local rotation `arm` in the chest's
 *  frame): how far it reaches back (less than 60°) and across the chest (less than 45°). */
const BACK_CAP = [0, Math.sin(Math.PI / 6), Math.cos(Math.PI / 6)], SHOULDER_RANGE = { back: 60, across: 45 };
function shoulderOf(arm) {
  const d = [-arm[1], -arm[4], -arm[7]], ang = (x) => Math.acos(Math.max(-1, Math.min(1, x))) / RAD;
  return { back: 120 - ang(d[0] * BACK_CAP[0] + d[1] * BACK_CAP[1] + d[2] * BACK_CAP[2]), across: 90 - ang(d[0]) };
}

/**
 * The shield keeps to the front while the torso turns or leans. At every key of the plain clip and on the 60 Hz grid between,
 * the shield's forearm is turned back against the chest's turn away from the front (the chest's forward against -z) only as
 * far as keeps the shield's face within the aim of the front: a chest turned or leant less than that carries the shield
 * with it, and the less the arm turns against the chest, the more room it has in front of it. The aim is FACE_MARGIN under
 * the shield checks' limits (test/shield.test.js): 35°, and 60° in an attack's swing (swingOf), eased between over CAP_EASE s.
 * Where that still leaves no room in front of the chest for the left arm, the shield is also turned in its own plane (roll,
 * the forearm then no longer level). The arm holding it, for each roll, is found by a pattern search over the elbow's bend
 * and the forearm's turn about its length (armFor): the arm's capsules out of the torso and head (BODY_AIM under
 * test/lib/anatomy.js's 0.5 px) and the plate SHIELD_CLEAR px clear of them, the plate ahead of the chest, the shoulder
 * within its range (test/lib/anatomy.js), the upper arm's twist within SHOULDER_MAX and the forearm's within WRIST_MAX
 * (test/joints.test.js), the plate near where it would be had the chest not turned; then the rolls over the whole clip are
 * chosen together (a Viterbi pass), each key's reached from the one before no faster than ROLL_RATE, ELBOW_RATE, TURN_RATE
 * and ARM_RATE, so the shield turns in its plane before the chest has turned too far, not once the arm is caught. Nothing
 * in the build after this step looks at the arm again. Returns the arm's bones, or null for a clip whose chest never turns
 * the shield past the aim.
 */
const FACE_LIMIT = 35, FACE_LIMIT_SWING = 60, FACE_MARGIN = 3, CAP_EASE = 0.1;
const SHOULDER_MAX = 80, WRIST_MAX = 85, SHIELD_CLEAR = 0.5;
/** How deep (px) the arm's capsules may sink into the torso or head at a key: under the anatomy check's 0.5 px, leaving room
 *  for the interpolation between keys (the standing guard itself rests about 0.05 px into the chest). */
const BODY_AIM = 0.15;
/** The fastest the elbow may bend and the forearm turn between keys (degrees per second): under the elbow's 1500°/s
 *  (test/motion.test.js) and well under a twist's 60° in 1/120 s (test/joints.test.js); and the upper arm, so the keys between
 *  which it is interpolated (Euler angles, as PulseLib plays them) are near enough that it does not swing into the body. */
const ELBOW_RATE = 1200, TURN_RATE = 1200, ARM_RATE = 900;
/** The angle between two rotations (degrees). */
const turnAngle = (a, b) => Math.acos(Math.max(-1, Math.min(1, (a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3] + a[4] * b[4] + a[5] * b[5] + a[6] * b[6] + a[7] * b[7] + a[8] * b[8] - 1) / 2))) / RAD;
/** The shield's turns in its own plane tried, and how fast it may turn so (degrees per second: one step a 60th of a second). */
const ELBOW_RANGE = [20, 145], ROLLS = [0, 15, -15, 30, -30, 45, -45, 60, -60, 75, -75, 90, -90], ROLL_RATE = 900;
function faceFront(c, keyed, swing = null) {
  const anim = animationJson({ c }).animations.c;
  const F = mul3(mul3(eulerMatrix(SHIELD_POSE.arm, 'XYZ'), eulerMatrix([SHIELD_POSE.elbow, 0, 0], 'XYZ')), eulerMatrix([0, SHIELD_POSE.twist, 0], 'XYZ'));
  const rest = armFor(strapped(F), SHIELD_POSE.elbow, SHIELD_POSE.twist).mid, centre = [0, 18, 0], home = { e: SHIELD_POSE.elbow, tw: SHIELD_POSE.twist };
  // keyed where the plain clip is keyed, and on the 60 Hz grid between (the tests sample there, and a grid time gives way to
  // a key within 1 ms of it: test/lib/motion.js times), so the twin is sampled where the plain clip is and its weapon arm
  // measures the same (test/timing.test.js twins), and the arm is never left to interpolate far between keys
  const own = keyTimes(keyed);
  const grid = Array.from({ length: Math.floor(c.length * 60 + 1e-9) + 1 }, (_, i) => +(i / 60).toFixed(4))
    .filter((t) => own.every((k) => Math.abs(k - t) > 1.5e-3));   // no key a sliver from another
  const times = [...new Set([...own, ...grid])].sort((a, b) => a - b);
  // the aim at each key, judged from the keys either side (the arm is interpolated between keys, so a key next to one outside
  // the swing keeps to the plain limit)
  const aims = times.map((t, i) => {
    const inside = swing ? Math.min(times[Math.max(0, i - 1)] - swing.from, swing.to - times[Math.min(times.length - 1, i + 1)]) : -1;
    return (inside <= 0 ? FACE_LIMIT : FACE_LIMIT + (FACE_LIMIT_SWING - FACE_LIMIT) * Math.min(1, inside / CAP_EASE)) - FACE_MARGIN;
  });
  const turn = times.map((t, i) => {
    const W = worldBones(samplePose(anim, t)), B = W.body.R, Bt = transpose(B);
    // the guard's face as the chest carries it, turned back toward the front (about the axis that takes it there) by as
    // little as brings it within the aim
    const n = mulVec(mul3(B, F), SHIELD_OUT), facing = Math.acos(Math.max(-1, Math.min(1, -n[2]))) / RAD;
    const ax = [-n[1], n[0], 0], len = Math.hypot(...ax), axis = len > 1e-9 ? ax.map((x) => x / len) : [1, 0, 0];   // n x (0, 0, -1)
    const head = { R: mul3(Bt, W.head.R), p: mulVec(Bt, vsub(W.head.p, W.body.p)) };   // against the chest
    const back = Math.max(0, facing - aims[i]);
    return { t, B, Bt, head, axis, back, G0: mul3(mul3(Bt, axisAngle(axis, back)), mul3(B, F)) };
  });
  if (turn.every((k) => k.back < 1e-6)) return null;
  /** The arm for one key and one roll: the elbow's bend and the forearm's turn by a pattern search from `start`, no faster
   *  than ELBOW_RATE and TURN_RATE from `warm` (the arm at the key before it follows). */
  function solve(k, roll, warm, dt, start = warm) {
    const rate = Number.isFinite(dt) ? (1 / 60) / dt : 1;
    const pre = strapped(mul3(k.G0, rotX(roll)));   // the shield turned in its own plane, the forearm with it
    const want = vadd(centre, mulVec(mul3(mul3(k.Bt, axisAngle(k.axis, k.back)), k.B), vsub(rest, centre)));
    const H = k.head.R, hp = k.head.p;
    const at = (e, tw) => {
      if (e < ELBOW_RANGE[0] || e > ELBOW_RANGE[1] || Math.abs(tw) > WRIST_MAX) return null;
      if (Math.abs(e - warm.e) > ELBOW_RATE * dt + 1e-9 || Math.abs(tw - warm.tw) > TURN_RATE * dt + 1e-9) return null;
      const a = armFor(pre, e, tw);
      // the arm out of the body as test/lib/anatomy.js judges it: the forearm out of the torso and head, the upper arm's lower
      // half out of the torso
      const deep = Math.max(LIMB_R - segNear(a.fore[0], a.fore[1], TORSO), LIMB_R - segNear(inHead(k.head, a.fore[0]), inHead(k.head, a.fore[1]), HEAD),
        LIMB_R - segNear(a.upper[0], a.upper[1], TORSO));
      // what the anatomy checks judge comes first: the arm out of the body, the shoulder within its range (degrees)
      const sh = shoulderOf(a.arm);
      // and the fist (weapon_l's pivot, which the turned forearm carries to a.off) up at the chest and in front of it, out of
      // it: the forearm's capsule stops short of the hand's corners (test/fighting.test.js holds the shield hand there)
      const fist = a.off, fistOut = Math.max(0, fist[2] + 0.5) ** 2 + Math.max(0, 12.5 - fist[1]) ** 2 + Math.max(0, fist[1] - 29.5) ** 2;
      const clash = 1e4 * Math.max(0, deep - BODY_AIM) ** 2 + 1e3 * fistOut + Math.max(0, sh.back - SHOULDER_RANGE.back) ** 2 + Math.max(0, sh.across - SHOULDER_RANGE.across) ** 2;
      let look = 0;   // then how it looks: the plate clear of the torso and head, and ahead of the chest along the NPC's facing
      for (const x of pre.plate) {
        const qx = x[0] + a.off[0], qy = x[1] + a.off[1], qz = x[2] + a.off[2], hx = qx - hp[0], hy = qy - hp[1], hz = qz - hp[2];
        look += boxShort(qx, qy, qz, TORSO, SHIELD_CLEAR)
          + boxShort(H[0] * hx + H[3] * hy + H[6] * hz, H[1] * hx + H[4] * hy + H[7] * hz, H[2] * hx + H[5] * hy + H[8] * hz, HEAD, SHIELD_CLEAR);
      }
      look += Math.max(0, k.B[6] * a.mid[0] + k.B[7] * (a.mid[1] - 18) + k.B[8] * a.mid[2] + 4) ** 2;
      let s = 100 * clash + 20 * look + vsub(a.mid, want).reduce((x, v) => x + v * v, 0);
      s += 1e4 * Math.max(0, Math.abs(twistOf(a.arm)) - SHOULDER_MAX) ** 2;
      s += 0.05 * rate * ((e - warm.e) ** 2 + (tw - warm.tw) ** 2);
      return { s, clash, deep, e, tw, a };
    };
    const descend = (from, step0) => {
      let b = at(from.e, from.tw);
      if (!b) return null;
      for (let step = step0; step >= 0.25; step /= 2) {
        for (let moved = true; moved;) {
          moved = false;
          for (const [de, dw] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]) {
            const r = at(b.e + de * step, b.tw + dw * step);
            if (r && r.s < b.s - 1e-9) { b = r; moved = true; }
          }
        }
      }
      return b;
    };
    if (Number.isFinite(dt)) return descend(start, 4);
    // a key free of the one before (the first): from the start and from a coarse grid over the elbow and the forearm's turn
    let best = descend(start, 16);
    for (const e of [40, 70, 100, 130]) for (const tw of [-60, -20, 20, 60]) {
      const r = descend({ e, tw }, 8);
      if (r && (!best || r.s < best.s - 1e-9)) best = r;
    }
    return best;
  }
  // The rolls over the clip (Viterbi): at each key a node per roll, reached from a node of the key before within the rates,
  // the path of least cost (each node's own, a roll's price, and changes by their rate) taken.
  let prev = null;
  const table = turn.map((k, i) => {
    let row = rowAt(k, i ? Math.max(1e-4, k.t - turn[i - 1].t) : Infinity);
    if (!row.length) row = rowAt(k, Infinity);   // nothing within the rates: let this key jump (never seen in the clips)
    prev = row;
    return row;
  });
  function rowAt(k, dt) {
    const rate = Number.isFinite(dt) ? (1 / 60) / dt : 1;
    const near = (from, to, max) => from + Math.max(-max * dt, Math.min(max * dt, to - from));
    const row = [];
    for (const roll of ROLLS) {
      // from each node of the key before that this roll can follow (its own roll or the next ones, within ROLL_RATE): the arm
      // searched from that node's (and, where it touches the body, from as near the guard's own pose as the rates let it get),
      // the cheapest way here kept
      const from = prev ? prev.filter((p) => Math.abs(p.roll - roll) <= ROLL_RATE * dt + 1e-6) : [null];
      let best = null;
      for (const p of from) {
        const anchor = p || home;
        let node = solve(k, roll, anchor, dt);
        if (p && (!node || node.clash > 1e-6)) {
          const g = solve(k, roll, anchor, dt, { e: near(anchor.e, home.e, ELBOW_RATE), tw: near(anchor.tw, home.tw, TURN_RATE) });
          if (g && (!node || g.s < node.s - 1e-9)) node = g;
        }
        if (!node) continue;
        let total = node.s + 0.3 * Math.abs(roll);
        if (p) {
          // the upper arm too (past ARM_RATE only at a great cost, where nothing else will do)
          const fast = Math.max(0, turnAngle(p.a.arm, node.a.arm) - ARM_RATE * dt);
          total += p.total + 0.05 * rate * (p.roll - roll) ** 2 + 1e4 * fast * fast;   // e and tw: in node.s, against p
        }
        if (!best || total < best.total) best = { ...node, roll, total, from: p };
      }
      if (best) row.push(best);
    }
    return row;
  }
  const path = [];
  for (let node = table[table.length - 1].reduce((x, y) => (y.total < x.total ? y : x)); node; node = node.from) path.unshift(node);
  const keys = path.map((n, i) => ({ t: turn[i].t, arm: fromTrue(n.a.arm), elbow: bbKey([n.e, 0, 0]), wrist: forearmTwist(n.tw) }));
  const ch = (f) => keys.map((k) => ({ t: k.t, v: f(k), lerp: 'linear' }));
  return {
    left_arm: { rotation: ch((k) => k.arm) },
    left_elbow: { rotation: ch((k) => k.elbow) },
    weapon_l: { rotation: ch((k) => k.wrist.rotation), position: ch((k) => k.wrist.position) },
  };
}

/**
 * An attack's swing, clip seconds: SWING_NEAR s either side of its hit as the game plays it. combat.AttackTiming plays an
 * attack faster when it is longer than 18 ticks or hits later than 12 (speed = max(1, length/18, hit/12) in ticks; also
 * test/lib/game.js), so the window is SWING_NEAR × speed of clip time.
 */
const SWING_NEAR = 0.15;
function swingOf(c, slot) {
  const hit = hitTime(animationJson({ c }).animations.c, slot), speed = Math.max(1, (c.length * 20) / 18, (hit * 20) / 12);
  return { from: hit - SWING_NEAR * speed, to: hit + SWING_NEAR * speed };
}

/** The same clip with the off arm replaced by the held shield, kept to the front (swing: an attack's, or null). */
function withShield(clip, arm, swing = null) {
  const c = clone(clip);
  for (const b of SHIELD_BONES) c.bones[b] = clone(arm[b]);
  const turned = faceFront(c, clip, swing);
  if (turned) Object.assign(c.bones, turned);
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

/* ==== Formation stances (specs/addon/npc-formation-stances.md) ====
 * Each stance slot (npc_slots.js SLOTS entries with `stance`) is picked per race from its STANCE_POOL by the stance rules
 * and score of test/lib/stance.js, the same measures test/stances.test.js judges the race files with. A candidate is one
 * pool entry, or a pair: one entry's upper body over another's legs (split as posture splits them, STANCE.LOWER_BONES).
 * Each is finished as every clip is (race posture, the hips following the torso, true hinges), its grip set (an entry's
 * `grip`: how the hand turns what it holds), and, where its upper body is a held frame, given the breathing of a source
 * idle (STANCE_BREATH). Shield versions take the shield arm from the shield code path (withShield). */
const MOCAP = require('./import/mocap.js');
const STANCE = require('./test/lib/stance.js');
const { STANCE_POOL, STANCE_BREATH, MARCH_STRIDE } = require('./npc_slots.js');
const BVH_SOURCES = new Set(['ual', 'kaykit', 'cmu']);

/** A pool entry's name in the build's printout and in npc_slots.js STANCE_PICKS. */
const stanceId = (e) => `${e.source}:${e.clip}${e.hold !== undefined ? '@' + e.hold : ''}${e.mirror ? ' mirrored' : ''}${e.grip ? ' grip ' + e.grip : ''}`;
const pairId = (up, low) => (up === low ? stanceId(up) : `${stanceId(up)} over ${stanceId(low)}`);

/** The turn that levels a library's heads (degrees up; import/mocap.js headPitch): UAL's head-top joint sits ahead of the
 *  head, so its neutral idle (Idle_Loop) looks 15° down, and every UAL head is lifted by that. KayKit's idles read within 4°
 *  of level, and CMU's actors each carry their own head: those are left as they are. */
const HEAD_NEUTRAL = { ual: 'Idle_Loop' }, headLevels = {};
function headLevel(source) {
  if (!HEAD_NEUTRAL[source]) return 0;
  if (headLevels[source] === undefined) headLevels[source] = -MOCAP.headPitch(MOCAP.importBvh(fs.readFileSync(path.join(LIB, source, HEAD_NEUTRAL[source] + '.bvh'), 'utf8'), {}));
  return headLevels[source];
}

/** A pool entry's clip as its source has it (no posture), cached: the library BVH retargeted (import/mocap.js), or a source
 *  clip; held at a frame (hold, s), mirrored, cut to whole gait cycles (cycles), its seam spread (close). Always loops. */
const stanceRawCache = new Map();
function stanceRaw(e, sources) {
  const key = JSON.stringify([e.source, e.clip, e.hold, e.mirror, e.cycles, e.close, e.range]);
  if (stanceRawCache.has(key)) return clone(stanceRawCache.get(key));
  let c;
  if (BVH_SOURCES.has(e.source)) {
    const text = fs.readFileSync(path.join(LIB, e.source, e.clip + '.bvh'), 'utf8');
    c = MOCAP.importBvh(text, { loop: e.hold === undefined, cycles: e.cycles, range: e.range, headLift: headLevel(e.source) });
    if (e.mirror) c = { ...mirror(c), length: c.length };
  } else {
    const one = (n) => { const x = sources[e.source] && sources[e.source][n]; if (!x) throw new Error(`STANCE_POOL: no clip ${e.source}/${n}`); return x; };
    // layers (Mo' Bends builds a pose of several): merged bone by bone, a later layer's bone over an earlier one's
    const src = Array.isArray(e.clip) ? e.clip.map(one).reduce((a, b) => ({ length: Math.max(a.length, b.length), loop: true, bones: { ...a.bones, ...b.bones } })) : one(e.clip);
    c = clone(e.mirror ? mirror(src) : src);
    if (e.cycles) c = MOCAP.loopCycle(c, e.cycles);
    else if (e.close) c = MOCAP.closeLoop(c);
  }
  if (e.hold !== undefined) c = MOCAP.holdFrame(c, e.hold);
  c = { length: Math.max(c.length || 0, 0.05), loop: true, bones: c.bones };
  stanceRawCache.set(key, c);
  return clone(c);
}

/** Does a clip hold every channel of these bones still (one value throughout)? */
const stillIn = (c, bones) => Object.entries(c.bones).every(([b, ch]) => !bones(b) || Object.values(ch).every((keys) => keys.every((k) => k.v.every((x, j) => Math.abs(x - keys[0].v[j]) < 1e-6))));
const isLower = (b) => STANCE.LOWER_BONES.has(b), isUpper = (b) => !STANCE.LOWER_BONES.has(b);

/** A channel's keys over a loop of length P played k times within L (a still channel stays as it is). */
function fitKeys(keys, P, L) {
  if (keys.length < 2 || P <= 0.05 + 1e-9) return keys.map((k) => ({ ...k, v: k.v.slice() }));
  const k = Math.max(1, Math.round(L / P)), s = L / (k * P), out = [];
  for (let r = 0; r < k; r++) for (const key of keys) {
    const t = (r * P + key.t) * s;
    if (out.length && t <= out[out.length - 1].t + 1e-9) continue;   // a cycle's end is the next one's start
    out.push({ t, v: key.v.slice(), lerp: key.lerp });
  }
  return out;
}

/** One entry's upper body over another's legs. The clip runs as long as the part that moves (the legs first); the other
 *  part's loop is fitted to it, played a whole number of times. */
function stanceCombine(up, low) {
  if (up === low) return clone(up);
  const upStill = stillIn(up, isUpper), lowStill = stillIn(low, isLower);
  const L = !lowStill ? low.length : !upStill ? up.length : 0.05;
  const c = { length: L, loop: true, bones: {} };
  for (const [part, from, test] of [['upper', up, isUpper], ['lower', low, isLower]]) {
    for (const [b, ch] of Object.entries(from.bones)) if (test(b)) {
      c.bones[b] = {};
      for (const [kind, keys] of Object.entries(ch)) c.bones[b][kind] = fitKeys(keys, from.length, L);
    }
  }
  return c;
}

/**
 * The breathing a held upper body is given (spec "Combining"): the source idle's (STANCE_BREATH, UAL Idle_Loop) turns of the
 * torso and head about their mean, scaled so the neck sways at most STANCE_BREATH.sway px. Cached.
 */
let breathCache = null;
function stanceBreath() {
  if (breathCache) return breathCache;
  const e = STANCE_BREATH, src = MOCAP.importBvh(fs.readFileSync(path.join(LIB, e.source, e.clip + '.bvh'), 'utf8'), { loop: true });
  const bones = {};
  for (const b of e.bones) {
    const keys = (src.bones[b] && src.bones[b].rotation) || [];
    if (!keys.length) continue;
    const mean = [0, 1, 2].map((j) => keys.reduce((s, k) => s + k.v[j], 0) / keys.length);
    bones[b] = keys.map((k) => ({ t: k.t, d: k.v.map((x, j) => x - mean[j]) }));
  }
  // the neck's sway with the offsets alone (a still body otherwise), and the scale that keeps it within the limit
  const probe = { length: src.length, loop: true, bones: Object.fromEntries(Object.entries(bones).map(([b, ks]) => [b, { rotation: ks.map((k) => ({ t: k.t, v: k.d, lerp: 'linear' })) }])) };
  const a = animationJson({ p: probe }).animations.p, neck = [];
  for (let t = 0; t < src.length; t += 1 / 60) neck.push(point(worldBones(samplePose(a, t)).body, [0, 24, 0]));
  let sway = 0;
  for (const p of neck) for (const q of neck) sway = Math.max(sway, Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]));
  breathCache = { length: src.length, bones, scale: sway > e.sway ? e.sway / sway : 1 };
  return breathCache;
}
/** A clip whose upper body is held still, breathing: the breath's offsets added to its torso and head, over the clip's loop
 *  (the legs' cycle, a whole number of breaths) or, all of it held, one breath. */
function stanceBreathe(c, slot) {
  if (!stillIn(c, isUpper)) return c;
  const b = stanceBreath(), L = stillIn(c, isLower) ? b.length : c.length;
  const at = (scale) => {
    const out = clone(c);
    out.length = L;
    for (const [bone, ks] of Object.entries(b.bones)) {
      const ch = (out.bones[bone] = out.bones[bone] || {}), base = ch.rotation && ch.rotation.length ? ch.rotation[0].v.slice() : [0, 0, 0];
      ch.rotation = fitKeys(ks.map((k) => ({ t: k.t, v: k.d, lerp: 'linear' })), b.length, L).map((k) => ({ t: k.t, v: base.map((x, j) => x + scale * k.v[j]), lerp: 'linear' }));
    }
    return out;
  };
  // and no more than leaves the weapon's tip swaying BREATH_TIP px: a spear's tip, 45 px out, would sway four times the neck
  const out = at(b.scale), sway = stanceTipSway(out, slot);
  return sway > BREATH_TIP ? at((b.scale * BREATH_TIP) / sway) : out;
}
/** The most the breathing may sway the slot's weapon tip (px): the middle of the spec's 0.2-2 px for a still stance. */
const BREATH_TIP = 1.1;
/** How far the slot's weapon tip (test/lib/stance.js: its first class, or the bow's upper limb) wanders over a clip (px). */
function stanceTipSway(c, slot) {
  const rule = STANCE.SLOT[slot], a = animationJson({ c }).animations.c, pts = [];
  for (let t = 0; t < c.length; t += 1 / 30) {
    const w = worldBones(samplePose(a, t));
    pts.push(rule.bow ? STANCE.bowAt(w).top : STANCE.weapon(w, rule.classes[0]).tip);
  }
  let d = 0;
  for (const p of pts) for (const q of pts) d = Math.max(d, Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]));
  return d;
}

/**
 * How the hand turns what it holds (a pool entry's grip): retarget.js's weapon angle, solved at each key for a direction
 * instead of set by hand, and within a wrist's range (the item turned at most GRIP_MAX from where the fist seats it).
 *   shaft  a two-handed hold: the item runs from the right fist through the left one (a spear held in both hands)
 *   up     the item points straight up (a spear carried upright)
 *   level  the item points straight ahead, level (a spear levelled over a shield)
 *   bow    the flat bow's limbs (test/lib/flatbow.js) stand upright, its string toward the archer
 */
const GRIP_MAX = 80;
const RIGHT_FIST = [6, 12, -2];
function stanceGrip(c, mode) {
  if (!mode) return c;
  const out = clone(c), wr = out.bones.weapon_r = out.bones.weapon_r || {};
  const moving = !stillIn(c, () => true), times = moving ? Array.from({ length: Math.max(1, Math.round(c.length * 30)) + 1 }, (_, i) => (i * c.length) / Math.max(1, Math.round(c.length * 30))) : [0];
  const anim = animationJson({ c }).animations.c;
  const rest = mode === 'bow' ? [0, 0, -1] : STRIKE_DIR;   // the item's axis in the weapon bone's frame (flatbow.js AXIS)
  const keys = times.map((t) => {
    const w = worldBones(samplePose(anim, Math.min(t, c.length - 1e-6))), R = w.weapon_r.R;
    const d0 = mulVec(R, rest);
    let d1;
    if (mode === 'shaft') { const a = point(w.weapon_r, RIGHT_FIST), b = point(w.weapon_l, [-6, 12, -2]); d1 = vsub(b, a); }
    else if (mode === 'level') d1 = [0, 0, -1];
    else d1 = [0, 1, 0];
    const n = Math.hypot(...d1);
    d1 = n > 1e-6 ? d1.map((x) => x / n) : d0;
    // the shortest turn taking d0 to d1, at most as far as keeps the item within GRIP_MAX of its seat in the fist
    const ax = [d0[1] * d1[2] - d0[2] * d1[1], d0[2] * d1[0] - d0[0] * d1[2], d0[0] * d1[1] - d0[1] * d1[0]], s = Math.hypot(...ax);
    const full = Math.atan2(s, d0[0] * d1[0] + d0[1] * d1[1] + d0[2] * d1[2]) / RAD, axis = s > 1e-9 ? ax.map((x) => x / s) : [1, 0, 0];
    const E = w.right_elbow.R, local = (a) => mul3(transpose(E), mul3(axisAngle(axis, a), R));
    let a = full;
    for (let lo = 0, hi = full, i = 0; i < 30 && turnAngle(local(a), [1, 0, 0, 0, 1, 0, 0, 0, 1]) > GRIP_MAX; i++) { a = (lo + hi) / 2; if (turnAngle(local(a), [1, 0, 0, 0, 1, 0, 0, 0, 1]) > GRIP_MAX) hi = a; else lo = a; }
    return { t, v: fromTrue(local(a)), lerp: 'linear' };
  });
  wr.rotation = keys;
  return out;
}

/** A candidate posed for a race (not yet breathing): posture (march slots with the race's march stride), its grip, the
 *  hips following the torso, true hinges with the arms swivelled within the shoulder's range and out of the body as far as
 *  a swivel brings them (hinge.js limitTwist with anatomy, as every attack ships), a still stance's feet on the floor. */
function stancePose(raw, slot, race, grip) {
  const c = clone(raw), style = STYLE[race] || {}, march = /^march/.test(slot);
  applyStyle(c, march ? 'march' : slot, march ? { ...style, stride: MARCH_STRIDE[race] || style.stride } : style);
  let g = stanceGrip(c, grip);
  hipsFollow(g);
  g = limitTwist(hingeify(g), { anatomy: true });
  // a still stance stands on the floor as the tests measure the foot (the sole's middle): the retarget plants the lowest
  // corner of the boot, which on a shin bent deep (a crouch) leaves the sole's middle 3 px up
  if (STANCE.SLOT[slot].still) groundFeet(g);
  return g;
}

/** The shield version of a posed stance: the shield arm as every shield clip holds it (withShield). It goes on before the
 *  breathing, so a held pose keeps one shield arm (the arm's search, faceFront, solves each key on its own, and over a
 *  breath's 75 keys would not come back to where it started), which then breathes with the body. */
const stanceShield = (c) => withShield(c, shieldArm(), null);

/** A candidate built (an upper body and legs, the same entry or two), breathing: { clip, shield () (its shield version,
 *  built when first asked, for slots that have one) }. The brace slots carry their own shield in the clip itself. */
function stanceBuilt(up, low, slot, race, sources) {
  const pose = stancePose(stanceCombine(stanceRaw(up, sources), stanceRaw(low, sources)), slot, race, up.grip);
  if (STANCE.SLOT[slot].shield) return { clip: stanceBreathe(stanceShield(pose), slot), shield: () => null };
  let s;
  const shield = STANCE.SHIELD_VERSIONS.includes(slot) ? () => s || (s = stanceBreathe(stanceShield(pose), slot)) : () => null;
  return { clip: stanceBreathe(pose, slot), shield };
}

/** The stance rules for one slot and race (test/lib/stance.js judge, against the race's idle and walk) on a built candidate
 *  and on its shield version where the slot has one: { fails, measures, score }. The shield version is judged only where
 *  the plain clip's upper body passes (`always`: every time): the shield arm's search is the slow part, and an upper body
 *  that fails is never used. */
function stanceJudge(slot, race, clips) {
  const style = STYLE[race] || {};
  const ctx = { idle: animationJson({ i: clips.idle }).animations.i, walk: animationJson({ w: clips.walk }).animations.w, hunch: style.hunch || 0 };
  return (b, always = true) => {
    const plain = STANCE.judge(slot, animationJson({ c: b.clip }).animations.c, ctx);
    if (!always && plain.fails.some((f) => f.part === 'upper')) return plain;
    const s = b.shield();
    if (!s) return plain;
    const js = STANCE.judge(slot + '_shield', animationJson({ s }).animations.s, ctx);
    return { fails: [...plain.fails, ...js.fails], measures: plain.measures, score: plain.score };
  };
}

/**
 * The pick for one stance slot and race: every pool entry built and judged whole, every pairing (an entry's upper body,
 * another's legs) estimated from its parts' results, then the candidates that pass in estimate built in full, best first,
 * until the best two that pass in full are found (with their shield versions, where the slot has one). Ties go to the
 * earlier pool entry. Returns { clip, shield, pick: { id, score, runnerUp, passes } }.
 */
function stancePick(slot, race, sources, clips) {
  const pool = STANCE_POOL[slot], judgeAll = stanceJudge(slot, race, clips);
  const singles = pool.map((e, i) => {
    const b = stanceBuilt(e, e, slot, race, sources), j = judgeAll(b, false);
    return { e, i, b, j, up: j.fails.filter((f) => f.part === 'upper'), low: j.fails.filter((f) => f.part === 'lower') };
  });
  const LOWER_MEASURES = new Set(['rightFoot', 'leftFoot', 'knees', 'hipsDown', 'lead', 'braceDown', 'apart', 'irregular', 'skate', 'walkSkate']);
  const options = [];
  for (const a of singles) for (const b of singles) {
    if (a.e.part === 'lower' || b.e.part === 'upper' || (a === b && a.e.part)) continue;
    if (a.up.length || b.low.length) continue;
    const m = { ...Object.fromEntries(Object.entries(a.j.measures).filter(([k]) => !LOWER_MEASURES.has(k))), ...Object.fromEntries(Object.entries(b.j.measures).filter(([k]) => LOWER_MEASURES.has(k))) };
    options.push({ a, b, est: STANCE.score(slot, m, false) });
  }
  options.sort((x, y) => x.est - y.est || x.a.i - y.a.i || x.b.i - y.b.i);
  const passed = [];
  for (const o of options) {
    if (passed.length >= 2 || (passed.length && o.est > passed[0].score + 0.25)) break;
    const b = o.a === o.b ? o.a.b : stanceBuilt(o.a.e, o.b.e, slot, race, sources);
    const j = judgeAll(b);
    if (!j.fails.length) passed.push({ id: pairId(o.a.e, o.b.e), b, score: j.score, order: [o.a.i, o.b.i] });
  }
  passed.sort((x, y) => x.score - y.score || x.order[0] - y.order[0] || x.order[1] - y.order[1]);
  const best = passed[0];
  if (!best) {   // none passes: the whole entry with the fewest failures ships, and the tests say why
    const s = singles.reduce((x, y) => (y.j.fails.length < x.j.fails.length ? y : x));
    console.log(`${race}: ${slot}: no candidate passes every stance rule; ${stanceId(s.e)} ships (${s.j.fails.slice(0, 3).map((f) => f.msg).join('; ')})`);
    return { clip: s.b.clip, shield: s.b.shield(), pick: { id: stanceId(s.e), score: +s.j.score.toFixed(4), runnerUp: null, passes: false } };
  }
  const second = passed[1];
  return { clip: best.b.clip, shield: best.b.shield(), pick: { id: best.id, score: +best.score.toFixed(4), runnerUp: second ? { id: second.id, score: +second.score.toFixed(4) } : null, passes: true } };
}

/** Builds a race's stance slot (and its shield version) into clips, and prints the pick. */
function buildStance(slot, race, sources, clips) {
  const p = stancePick(slot, race, sources, clips);
  clips[slot] = { ...p.clip, pick: p.pick };
  if (p.shield) clips[slot + '_shield'] = p.shield;
  const r = p.pick.runnerUp;
  console.log(`${race}: ${slot}: ${p.pick.id} (${p.pick.score.toFixed(3)})${r ? `, runner-up ${r.id} (${r.score.toFixed(3)})` : ', no runner-up'}`);
}


function buildRace(race, sources, slots = SLOTS) {
  const clips = {}, style = STYLE[race] || {}, shield = shieldArm(), used = new Set(), leftOf = [], meta = {};
  for (const [slot, entry] of Object.entries(slots)) {
    if (entry.stance) { buildStance(slot, race, sources, clips); continue; }   // formation stances: picked by measurement
    let c = clipFor(slot, entry, race, sources);
    if (/^attack_/.test(slot)) {   // before mirroring
      const best = bestAttack(c, slot, entry, race, sources, style, clips, used, slots);
      c = best.clip;
      meta[slot] = { clip: best.source, style: best.k, ...(best.missesBar ? { missesBar: true } : {}) };
    }
    if (/^attack_/.test(slot)) {   // the left-handed twin: the same swing mirrored, item on weapon_l
      leftOf.push(slot);   // mirrored from the finished swing below: an exact twin
    }
    if (/^attack_/.test(slot)) {
      // chosen without the arms' anatomy (finishAttack), which should leave it at the bar: where it would not, the attack
      // ships without it (test/anatomy.test.js lists it)
      const kept = finishAttack(c, slot, style, clips.idle, 'right', clips.walk, true);
      if (meta[slot].missesBar || meetsBar(animationJson({ c: kept }).animations.c, slot)) c = kept;
      else {
        console.log(`${race}: ${slot} misses the bar with the arms' anatomy: shipped without it`);
        c = finishAttack(c, slot, style, clips.idle, 'right', clips.walk);
      }
    } else {
      applyStyle(c, slot, style);
      if (slot === 'run') groundFeet(c);   // the sprint's long airborne phase floats both feet
      hipsFollow(c);   // the hips take part of every torso twist
      c = hingeify(c);   // elbows and knees as the hinges the game draws (hinge.js)
      if (slot === 'shield_brace') c = withShield(c, shield);   // the brace holds the strapped shield like every shield clip
    }
    clips[slot] = c;
    if (leftOf.includes(slot)) clips[slot + '_l'] = mirror(c);
    if (SHIELD_SLOTS.test(slot)) clips[slot + '_shield'] = withShield(c, shield, /^attack_/.test(slot) ? swingOf(c, slot) : null);
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

module.exports = { loadSources, buildRace, hitTime, applyStyle, classLength, _fight: fight, _finish: (c, slot, style, idle, side, walk) => finishAttack(c, slot, style, idle, side, walk, true), _clipFor: clipFor, _tempo: { warp, apexOf, apply: tempo, unwarpSpan }, _shield: { SHIELD_POSE, armFor, twistOf, faceFront },
  _stance: { stanceId, stancePick, single: (e, slot, race, sources, clips) => stanceJudge(slot, race, clips)(stanceBuilt(e, e, slot, race, sources)),
    pair: (up, low, slot, race, sources, clips) => stanceJudge(slot, race, clips)(stanceBuilt(up, low, slot, race, sources)), stanceBuilt } };
if (require.main === module) main();
