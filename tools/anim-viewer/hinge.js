/* Turns elbows and knees into true hinges, the only bend the game can draw on a limb (client.render.NpcJoints bends about
 * the limb's x axis). AnimationsPlus' elbows also twist and swing freely (its forearms are rigid boxes in Figura), which in
 * game leaves the held weapon (following the whole elbow) at odds with the drawn forearm. Per frame, the two-bone swivel:
 * the shoulder and elbow stay put, the upper limb turns about its own length so the lower limb lies in the hinge plane, the
 * joint bends by a pure hinge angle to point the lower limb where it was, and the weapon bone takes up the remainder so the
 * weapon stays exactly where the animator put it. Rig space (gecko/rig.js), Blockbench values in and out (import/parts.js). */
'use strict';
const { animationJson } = require('./gecko/write.js');
const { samplePose, worldBones, point } = require('./gecko/sample.js');
const { RIG } = require('./gecko/rig.js');
const { worldToClip } = require('./import/parts.js');

const LIMBS = [
  { upper: 'right_arm', joint: 'right_elbow', end: 'weapon_r', shoulder: [4.5, 22.5, 0], knee: [6, 18, 0], foot: [6, 12, 0] },
  { upper: 'left_arm', joint: 'left_elbow', end: 'weapon_l', shoulder: [-4.5, 22.5, 0], knee: [-6, 18, 0], foot: [-6, 12, 0] },
  { upper: 'right_leg', joint: 'right_knee', end: null, shoulder: [1.9, 12, 0], knee: [1.9, 6, 0], foot: [1.9, 0, 0] },
  { upper: 'left_leg', joint: 'left_knee', end: null, shoulder: [-1.9, 12, 0], knee: [-1.9, 6, 0], foot: [-1.9, 0, 0] },
];
const PIVOT = Object.fromEntries(RIG.map((b) => [b.name, b.pivot]));
const sub = (a, b) => a.map((x, i) => x - b[i]), add = (a, b) => a.map((x, i) => x + b[i]);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(...a) || 1; return a.map((x) => x / l); };
const apply = (R, v) => [0, 1, 2].map((r) => R[r * 3] * v[0] + R[r * 3 + 1] * v[1] + R[r * 3 + 2] * v[2]);
const mul3 = (a, b) => { const o = []; for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) o.push(a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]); return o; };
/** Rotation matrix about a unit axis by an angle (radians). */
function axisAngle(u, a) {
  const c = Math.cos(a), s = Math.sin(a), t = 1 - c, [x, y, z] = u;
  return [t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x, t * x * z - s * y, t * y * z + s * x, t * z * z + c];
}
const wrap = (a) => a - 2 * Math.PI * Math.round(a / (2 * Math.PI));

/** One limb at one frame: new world transforms for the upper limb and the joint; phi carries the swivel between frames. */
function solve(w, limb, state) {
  const A = w[limb.upper], J = w[limb.joint];
  const S = point(A, limb.shoulder), E = point(A, limb.knee), H = point(J, limb.foot);
  const u = norm(sub(E, S)), d = norm(sub(H, E));
  const x0 = apply(A.R, [1, 0, 0]);
  const par = dot(x0, u), xp = sub(x0, u.map((v) => v * par)), xq = cross(u, xp);
  // hinge axis after a swivel phi: x(phi) = par u + xp cos phi + xq sin phi; want it square to the lower limb
  const C = par * dot(u, d), Ac = dot(xp, d), Bs = dot(xq, d), Rm = Math.hypot(Ac, Bs);
  const hingeFor = (ph) => {
    const Rt = axisAngle(u, ph), A2 = { R: mul3(Rt, A.R), p: add(apply(Rt, sub(A.p, S)), S) };
    const x = apply(A2.R, [1, 0, 0]), f0 = apply(A2.R, sub(limb.foot, limb.knee));
    const fp = norm(sub(f0, x.map((v) => v * dot(f0, x)))), dp = norm(sub(d, x.map((v) => v * dot(d, x))));
    return { phi: ph, A2, theta: Math.atan2(dot(cross(fp, dp), x), dot(fp, dp)) };
  };
  let pick;
  if (Rm < 1e-9 || Math.abs(C) > Rm || Math.abs(dot(norm(sub(E, S)), d)) > 0.999) pick = hingeFor(state.phi);   // straight: keep the swivel
  else {
    const base = Math.atan2(Bs, Ac), off = Math.acos(-C / Rm);
    const cands = [base + off, base - off].map((c) => hingeFor(state.phi + wrap(c - state.phi)));
    // anatomy first: elbows fold forward (positive turn about x), knees back; then the swivel nearest the last frame
    const ok = (h) => (limb.upper.endsWith('arm') ? h.theta >= -0.05 : h.theta <= 0.05);
    const good = cands.filter(ok);
    pick = (good.length ? good : cands).reduce((b, h) => (Math.abs(h.phi - state.phi) < Math.abs(b.phi - state.phi) ? h : b));
  }
  state.phi = pick.phi;
  const A2 = pick.A2, theta = pick.theta;
  const Rl = axisAngle([1, 0, 0], theta), kp = limb.knee;   // local turn of the joint about its pivot
  const local = { R: Rl, p: sub(kp, apply(Rl, kp)) };
  const J2 = { R: mul3(A2.R, local.R), p: add(apply(A2.R, local.p), A2.p) };
  return { [limb.upper]: A2, [limb.joint]: J2 };
}

/**
 * The clip with every elbow and knee a pure hinge; the weapon bones keep their world. Resampled at 30 fps plus the clip's own
 * key times, or (opts.rate) on an even grid at that rate only: Catmull-Rom curves need evenly spaced keys to stay smooth.
 */
function hingeify(clip, opts = {}) {
  const bonesOf = (l) => [l.upper, l.joint, l.end].filter(Boolean);
  const moving = LIMBS.filter((l) => bonesOf(l).some((b) => clip.bones[b]));
  if (!moving.length) return clip;
  const anim = animationJson({ c: clip }).animations.c, L = clip.length;
  const held = Object.values(clip.bones).every((ch) => Object.values(ch).every((keys) => keys.every((k) => k.t === 0)));
  const set = new Set(held ? [0] : [0, L]);   // a held pose stays one key
  if (!held && opts.rate) { const n = Math.max(1, Math.round(L * opts.rate)); for (let i = 0; i <= n; i++) set.add(+((i * L) / n).toFixed(5)); }
  else if (!held) {
    for (let t = 0; t < L; t += 1 / 30) set.add(+t.toFixed(5));
    for (const l of moving) for (const b of bonesOf(l)) for (const ch of Object.values(clip.bones[b] || {})) for (const k of ch) set.add(k.t);
  }
  const times = [...set].filter((t) => t >= 0 && t <= L + 1e-9).sort((a, b) => a - b);
  const poses = times.map((t) => worldBones(samplePose(anim, Math.min(t, L))));
  const pass = (states) => poses.map((w) => {
    const f = Object.fromEntries(Object.entries(w));
    moving.forEach((l, i) => Object.assign(f, solve(w, l, states[i])));
    return f;
  });
  const states = moving.map(() => ({ phi: 0 }));
  let frames = pass(states);
  // a loop starts where it ends: solve again from the swivel the first pass finished on
  if (clip.loop === true) frames = pass(states.map((st) => ({ phi: st.phi })));
  const out = worldToClip(frames, times, L, clip.loop);
  const c = JSON.parse(JSON.stringify(clip));
  // even-grid resamples carry every bone (one clock for the whole swing); otherwise only the limbs change
  const replace = opts.rate ? Object.keys({ ...clip.bones, ...out.bones }) : moving.flatMap(bonesOf);
  for (const b of replace) {
    if (out.bones[b]) c.bones[b] = out.bones[b];
    else delete c.bones[b];
  }
  if (opts.filter) for (const ch of Object.values(c.bones)) for (const keys of Object.values(ch)) lowPass(keys);
  return c;
}

/**
 * A 5-tap binomial low-pass over evenly spaced keys (at 60 fps, a 33 ms window): rounds the corners a sparse source swing
 * has at its keys. Euler values are unwrapped first; the first and last keys stay exact, so the swing starts and ends where
 * it did.
 */
function lowPass(keys) {
  if (keys.length < 5) return;
  const v = keys.map((k) => k.v.slice());
  for (let i = 1; i < v.length; i++) for (let j = 0; j < 3; j++) v[i][j] = v[i - 1][j] + (((v[i][j] - v[i - 1][j] + 180) % 360) + 360) % 360 - 180;
  const W = [1, 4, 6, 4, 1];
  for (let i = 1; i < v.length - 1; i++) {
    keys[i].v = [0, 1, 2].map((j) => {
      let s = 0, n = 0;
      for (let d = -2; d <= 2; d++) { const k = Math.min(v.length - 1, Math.max(0, i + d)); s += W[d + 2] * v[k][j]; n += W[d + 2]; }
      return s / n;
    });
  }
}

module.exports = { hingeify, solve, lowPass, LIMBS, PIVOT };
