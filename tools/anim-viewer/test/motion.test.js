'use strict';
/* Motion checks: every clip sampled at 60 Hz and at every key, and judged over its whole course with limits from outside
 * the build — anatomy and sports biomechanics for joints, animation craft for swings, and the game's own fade times.
 * Measured with test/lib/motion.js (independent of build_npc.js and gecko/strike.js); samples are not evenly spaced, so
 * rates use each sample pair's own dt. Each failure names the clip, the time and the value. */
const test = require('node:test');
const M = require('./lib/motion.js');
const { point } = require('../gecko/sample.js');
const { classLength } = require('../build_npc.js');
const { RACES } = require('../npc_slots.js');
const { FIST, DIR, baseName, sideOf, tipRest, tipOf, report } = require('./lib/swing.js');   // the weapon tip

const { shipped: built } = require('./lib/shipped.js');   // the files the game ships
const all = [];   // { race, name, anim, fs }
for (const race of RACES) for (const [name, anim] of Object.entries(built[race].animations.animations)) all.push({ race, name, anim, fs: M.frames(anim, M.RATE, `${race}/${name}`) });
const attacks = all.filter((c) => /^attack_/.test(c.name));
const JOINTS = [['right_elbow', 'right_arm'], ['left_elbow', 'left_arm'], ['right_knee', 'right_leg'], ['left_knee', 'left_leg']];
const hitsOf = (c) => built[c.race].hits[baseName(c.name)];

// ---------------- bending ----------------
test('elbows and knees are pure hinges, the only bend the game draws on a limb (≤ 2° off the hinge axis)', () => {
  const bad = [];
  for (const c of all) for (const f of c.fs) for (const [j, p] of JOINTS) {
    const h = M.hinge(f.w, j, p);
    if (h.off > 2) { bad.push(`${c.race} ${c.name} t=${f.t.toFixed(2)} ${j}: ${h.off.toFixed(1)}° off the hinge`); break; }
  }
  report(bad);
});

test('elbows and knees stay in their range: fold at most 150°, overextend at most 10° (normal anatomy)', () => {
  const bad = [];
  for (const c of all) for (const f of c.fs) for (const [j, p] of JOINTS) {
    const b = M.hinge(f.w, j, p).bend, fold = j.includes('elbow') ? -b : b;
    if (fold > 150 || fold < -10) { bad.push(`${c.race} ${c.name} t=${f.t.toFixed(2)} ${j}: fold ${fold.toFixed(0)}°`); break; }
  }
  report(bad);
});

test('elbows and knees bend no faster than people can: 1500°/s elbows, 1200°/s knees (sports biomechanics peaks)', () => {
  const bad = [];
  for (const c of all) for (const [j, p] of JOINTS) {
    let prev = null;
    for (const f of c.fs) {
      const b = M.hinge(f.w, j, p).bend, rate = prev && Math.abs(b - prev.b) / (f.t - prev.t);
      if (prev && rate > (j.includes('elbow') ? 1500 : 1200)) { bad.push(`${c.race} ${c.name} t=${f.t.toFixed(2)} ${j}: ${rate.toFixed(0)}°/s`); break; }
      prev = { b, t: f.t };
    }
  }
  report(bad);
});

test('the torso turns over the hips within a spine’s range: twist 60°, forward 45°, back 25°, sideways 30°', () => {
  const bad = [];
  for (const c of all) for (const f of c.fs) {
    const w = M.waist(f.w);
    if (Math.abs(w.twist) > 60 || w.flex > 45 || w.flex < -25 || Math.abs(w.side) > 30) {
      bad.push(`${c.race} ${c.name} t=${f.t.toFixed(2)}: twist ${w.twist.toFixed(0)} flex ${w.flex.toFixed(0)} side ${w.side.toFixed(0)}`); break;
    }
  }
  report(bad);
});

test('every bone moves without jumps: no rest point moves faster than 600 px/s between samples outside attacks', () => {
  const bad = [];
  for (const c of all.filter((x) => !/^attack_/.test(x.name))) for (const bone of Object.keys(c.fs[0].w)) {
    const v = M.velocities(M.track(c.fs, bone, [0, 10, 0]));
    const top = v.reduce((b, x) => (x.speed > b.speed ? x : b));
    if (top.speed > 600) bad.push(`${c.race} ${c.name} ${bone}: ${top.speed.toFixed(0)} px/s at ${top.t.toFixed(2)}`);
  }
  report(bad);
});

// ---------------- the weapon ----------------
test('the weapon tip moves in clean arcs: once faded in and while fast, it never turns tighter than a quarter of the blade', () => {
  const bad = [];
  for (const c of attacks) {
    const { len, v } = tipOf(c), peak = Math.max(...v.map((x) => x.speed)), from = hitsOf(c).fadeIn;
    for (let i = 1; i < v.length; i++) {
      if (v[i].t < from || v[i].speed < 0.5 * peak || v[i - 1].speed < 0.5 * peak) continue;
      const ang = Math.acos(Math.max(-1, Math.min(1, M.dot(v[i].v, v[i - 1].v) / (v[i].speed * v[i - 1].speed))));
      const radius = ((v[i].speed + v[i - 1].speed) / 2) / (ang / (v[i].t - v[i - 1].t));   // speed over turn rate
      if (radius < 0.25 * len * 16) { bad.push(`${c.race} ${c.name} t=${v[i].t.toFixed(2)}: turns on ${radius.toFixed(1)} px`); break; }
    }
  }
  report(bad);
});

test('swings have spacing: the strike is at least 3x faster than the wind-up (animation craft: 3-5:1)', () => {
  const bad = [];
  for (const c of attacks.filter((x) => !/_(l|shield)$/.test(x.name))) {
    const { v } = tipOf(c), hit = hitsOf(c).hit;
    const wind = v.filter((x) => x.t < hit - 0.15 && x.t > hitsOf(c).fadeIn), strike = v.filter((x) => x.t >= hit - 0.15 && x.t <= hit + 0.02);
    const span = wind.reduce((s, x) => s + x.dt, 0);
    if (span < 0.05 - 1e-9) continue;   // no separate wind-up to compare
    const w = wind.reduce((s, x) => s + x.speed * x.dt, 0) / span, s = Math.max(...strike.map((x) => x.speed));   // mean over time
    if (s < 3 * w) bad.push(`${c.race} ${c.name}: strike ${s.toFixed(0)} px/s, wind-up ${w.toFixed(0)} px/s`);
  }
  report(bad);
});

test('the fastest moment of the swing is at the strike, within 0.1 s of the hit', () => {
  const bad = [];
  for (const c of attacks.filter((x) => !/_(l|shield)$/.test(x.name))) {
    const { v } = tipOf(c), hit = hitsOf(c).hit, top = v.filter((x) => x.t > hitsOf(c).fadeIn).reduce((b, x) => (x.speed > b.speed ? x : b));
    if (Math.abs(top.t - hit) > 0.1) bad.push(`${c.race} ${c.name}: fastest at ${top.t.toFixed(2)}, hit at ${hit}`);
  }
  report(bad);
});

test('follow-through slows down: once the weapon has slowed below 40% of its peak after the hit, it does not speed back past 60% within 0.25 s (no bounce)', () => {
  const bad = [];
  for (const c of attacks.filter((x) => !/_(l|shield)$/.test(x.name))) {
    const { v } = tipOf(c), hit = hitsOf(c).hit, peak = Math.max(...v.map((x) => x.speed));
    const slow = v.find((x) => x.t > hit && x.speed < 0.4 * peak);
    const b = slow && v.find((x) => x.t > slow.t && x.t < slow.t + 0.25 && x.speed > 0.6 * peak);
    if (b) bad.push(`${c.race} ${c.name}: slowed to ${slow.speed.toFixed(0)} px/s at ${slow.t.toFixed(2)}, back to ${b.speed.toFixed(0)} at ${b.t.toFixed(2)}`);
  }
  report(bad);
});

test('edged weapons cut with the edge: at the strike the flat faces across the swing (|flat · motion| ≤ 0.5)', () => {
  const bad = [];
  for (const c of attacks.filter((x) => /^attack_(sword|longsword|axe|dagger)_\d+$/.test(x.name))) {
    const { v } = tipOf(c), hit = hitsOf(c).hit;
    const s = v.reduce((b, x) => (Math.abs(x.t - hit) < Math.abs(b.t - hit) ? x : b));
    const f = c.fs.reduce((b, x) => (Math.abs(x.t - hit) < Math.abs(b.t - hit) ? x : b)), R = f.w.weapon_r.R;
    const align = Math.abs(M.dot([R[0], R[3], R[6]], s.v)) / s.speed;
    if (align > 0.5) bad.push(`${c.race} ${c.name}: ${align.toFixed(2)} (a slap)`);
  }
  report(bad);
});

/* A single-edged or curved blade has its edge on one side: Minecraft seats a held item with its blade forward and up from
 * the fist and the edge below it, toward the front, so in the weapon frame the edge faces (0, -√½, -√½), square to the blade
 * (0, √½, -√½) in the flat's plane. Middle-earth files its scimitars as swords (combat.WeaponClass), so every sword clip
 * must strike with that side; axes too. Double-edged classes (longsword, dagger, spear) cut with either side. */
const EDGE = [0, -Math.SQRT1_2, -Math.SQRT1_2];
test('single-edged and curved blades strike with the edge, not the back: at the hit the edge faces the swing (≥ 0.5)', () => {
  const bad = [];
  for (const c of attacks.filter((x) => /^attack_(sword|axe)_\d+$/.test(x.name))) {
    const { v } = tipOf(c), hit = hitsOf(c).hit;
    const s = v.reduce((b, x) => (Math.abs(x.t - hit) < Math.abs(b.t - hit) ? x : b));
    const R = c.fs.reduce((b, x) => (Math.abs(x.t - hit) < Math.abs(b.t - hit) ? x : b)).w.weapon_r.R;
    const edge = [0, 1, 2].map((i) => R[i * 3] * EDGE[0] + R[i * 3 + 1] * EDGE[1] + R[i * 3 + 2] * EDGE[2]);
    const lead = M.dot(edge, s.v) / s.speed;
    if (lead < 0.5) bad.push(`${c.race} ${c.name}: the edge faces the swing by ${lead.toFixed(2)}${lead < 0 ? ' (the back leads)' : ''}`);
  }
  report(bad);
});

/** Is a rig point inside a bone's rest box once posed (no margin)? */
function inside(w, box, p) {
  const v = [0, 1, 2].map((i) => p[i] - w.p[i]);
  const q = [0, 1, 2].map((k) => w.R[k] * v[0] + w.R[3 + k] * v[1] + w.R[6 + k] * v[2]);
  return q.every((x, i) => x > box[0][i] && x < box[1][i]);
}
const TORSO = [[-4, 12, -2], [4, 24, 2]], HEAD = [[-4, 24, -4], [4, 32, 4]];

test('no attack swings the blade through its own torso or head once it has faded in', () => {
  const bad = [];
  for (const c of attacks) {
    const side = sideOf(c.name), len = classLength(baseName(c.name)), bone = side === 'left' ? 'weapon_l' : 'weapon_r';
    for (const f of c.fs.filter((x) => x.t >= hitsOf(c).fadeIn)) {
      const hitAt = [0.3, 0.5, 0.7, 0.9, 1].find((k) => {
        const p = point(f.w[bone], FIST[side].map((x, i) => x + DIR[i] * len * 16 * k * (side === 'left' && i === 0 ? -1 : 1)));
        return inside(f.w.body, TORSO, p) || inside(f.w.head, HEAD, p);
      });
      if (hitAt) { bad.push(`${c.race} ${c.name} t=${f.t.toFixed(2)}: blade ${Math.round(hitAt * 100)}% along is inside the body`); break; }
    }
  }
  report(bad);
});

test('attacks blend in and out without pops: over the fade, the weapon moves no faster than at the swing’s peak', () => {
  const bad = [];
  for (const c of attacks.filter((x) => !/_(l|shield)$/.test(x.name))) {
    const { v, len } = tipOf(c), peak = Math.max(...v.map((x) => x.speed)), h = hitsOf(c);
    const tip = (w) => point(w.weapon_r, tipRest('right', len));
    for (const base of ['idle', 'walk']) {
      const b = M.frames(built[c.race].animations.animations[base], 1)[0].w;
      const vin = M.len(M.sub(tip(c.fs[0].w), tip(b))) / h.fadeIn, vout = M.len(M.sub(tip(c.fs[c.fs.length - 1].w), tip(b))) / h.fadeOut;
      if (vin > peak * 1.0001) bad.push(`${c.race} ${c.name} from ${base}: ${vin.toFixed(0)} px/s over a ${h.fadeIn} s fade (peak ${peak.toFixed(0)})`);
      if (vout > peak * 1.0001) bad.push(`${c.race} ${c.name} back to ${base}: ${vout.toFixed(0)} px/s over a ${h.fadeOut} s fade`);
    }
    if (h.fadeIn >= h.hit) bad.push(`${c.race} ${c.name}: the fade-in (${h.fadeIn} s) is not over by the hit (${h.hit} s)`);
  }
  report(bad);
});
