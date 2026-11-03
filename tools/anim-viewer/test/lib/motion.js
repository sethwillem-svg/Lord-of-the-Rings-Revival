'use strict';
/* Motion measurements for the tests: clips sampled densely and points followed through time. Kept apart from the build
 * (build_npc.js never uses it), so the tests judge the build's output with their own yardstick. Rig space: px, y up,
 * facing -z; times in seconds. Clips are sampled on a 60 Hz grid and exactly at every key, so samples are not evenly
 * spaced: rates use each sample pair's own dt. Posed frames of shipped clips are cached in test/.cache (gitignored). */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { samplePose, worldBones, point } = require('../../gecko/sample.js');

const RATE = 60;
const CACHE = path.join(__dirname, '../.cache');
/** The player's own source salts the cache, so a change to how clips are played or posed is never served stale. */
const PLAYER = ['sample.js', 'rig.js'].map((f) => fs.readFileSync(path.join(__dirname, '../../gecko', f), 'utf8')).join('\n');
const sub = (a, b) => a.map((x, i) => x - b[i]);
const len = (v) => Math.hypot(...v);
const dot = (a, b) => a.reduce((s, x, i) => s + x * b[i], 0);
const deg = (r) => (r * 180) / Math.PI;

/** Every key time of every bone's rotation, position and scale (GeckoLib JSON: an object keyed by time strings; a bare
 *  array is a constant and has none). */
function keyTimes(anim) {
  const out = [];
  for (const b of Object.values(anim.bones || {})) for (const ch of ['rotation', 'position', 'scale']) {
    const c = b[ch];
    if (c && typeof c === 'object' && !Array.isArray(c)) for (const k of Object.keys(c)) out.push(Number(k));
  }
  return out;
}

/** Sorted unique times from 0 to the clip's end: the rate grid, the end and every key time. A grid time within 1 ms of a key
 *  gives way to it (keys are written rounded to 0.1 ms), so no sample pair is a sliver apart. */
function times(anim, rate = RATE) {
  const end = anim.animation_length || 0, EPS = 1e-3;
  const exact = [...new Set([...keyTimes(anim), end].filter((t) => t >= 0 && t <= end))].sort((a, b) => a - b);
  const grid = [];
  for (let i = 0; i / rate <= end; i++) grid.push(i / rate);
  let j = 0;
  const free = grid.filter((t) => {
    while (j < exact.length && exact[j] < t - EPS) j++;
    return !(j < exact.length && Math.abs(exact[j] - t) <= EPS);
  });
  return [...exact, ...free].sort((a, b) => a - b);
}

/** The posed skeleton at every time: [{ t, w }]. Given a key (such as 'man/attack_sword_1'), read from and written to the
 *  cache, named by a hash of the key, the clip, the rate and the player's source. */
function frames(anim, rate = RATE, key = null) {
  const file = key === null ? null
    : path.join(CACHE, `${crypto.createHash('sha1').update(key + JSON.stringify(anim) + rate + PLAYER).digest('hex')}.json`);
  if (file && fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
  const out = times(anim, rate).map((t) => ({ t, w: worldBones(samplePose(anim, t)) }));
  if (file) {
    fs.mkdirSync(CACHE, { recursive: true });
    const tmp = `${file}.${process.pid}.tmp`;   // test files run in parallel: write aside, then rename into place
    fs.writeFileSync(tmp, JSON.stringify(out));
    fs.renameSync(tmp, file);
  }
  return out;
}

/** A rest point carried by a bone, through the frames: [{ t, p }]. */
const track = (fs, bone, rest) => fs.map((f) => ({ t: f.t, p: point(f.w[bone], rest) }));

/** Velocities (px/s) between consecutive samples of a track: [{ t, dt, v, speed }], t the pair's midpoint, dt its span. */
function velocities(tr) {
  const out = [];
  for (let i = 1; i < tr.length; i++) {
    const dt = tr[i].t - tr[i - 1].t, v = sub(tr[i].p, tr[i - 1].p).map((x) => x / dt);
    out.push({ t: (tr[i].t + tr[i - 1].t) / 2, dt, v, speed: len(v) });
  }
  return out;
}

/** A bone's rotation relative to its parent as a quaternion [x, y, z, w] (from the world matrices). */
function localQuat(w, bone, parent) {
  const P = w[parent].R, J = w[bone].R, L = [];
  for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) L.push(P[r] * J[c] + P[3 + r] * J[3 + c] + P[6 + r] * J[6 + c]);
  return matQuat(L);
}

function matQuat(m) {
  const tr = m[0] + m[4] + m[8];
  let x, y, z, w;
  if (tr > 0) { const s = Math.sqrt(tr + 1) * 2; w = s / 4; x = (m[7] - m[5]) / s; y = (m[2] - m[6]) / s; z = (m[3] - m[1]) / s; }
  else if (m[0] > m[4] && m[0] > m[8]) { const s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2; w = (m[7] - m[5]) / s; x = s / 4; y = (m[1] + m[3]) / s; z = (m[2] + m[6]) / s; }
  else if (m[4] > m[8]) { const s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2; w = (m[2] - m[6]) / s; x = (m[1] + m[3]) / s; y = s / 4; z = (m[5] + m[7]) / s; }
  else { const s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2; w = (m[3] - m[1]) / s; x = (m[2] + m[6]) / s; y = (m[5] + m[7]) / s; z = s / 4; }
  return [x, y, z, w];
}

/**
 * A hinge joint's turn split into its bend about the hinge axis (rig x; degrees, negative folds forward) and the angle it
 * also turns off that axis (degrees) — a swing-twist decomposition. Elbows and knees are hinges: in game only the bend is
 * drawn on the limb (client.anim.NpcPose.bend), while held items follow the whole turn.
 */
function hinge(w, joint, parent) {
  let q = localQuat(w, joint, parent);
  if (q[3] < 0) q = q.map((x) => -x);   // the same turn; keeps the bend within ±180°
  const n = Math.hypot(q[0], q[3]) || 1, tw = [q[0] / n, 0, 0, q[3] / n];
  const bend = deg(2 * Math.atan2(tw[0], tw[3]));
  // swing = q * conj(twist): its angle is how far the joint turns off the hinge
  const sw = [
    q[3] * -tw[0] + q[0] * tw[3] + q[1] * -tw[2] - q[2] * -tw[1],
    q[3] * -tw[1] - q[0] * -tw[2] + q[1] * tw[3] + q[2] * -tw[0],
    q[3] * -tw[2] + q[0] * -tw[1] - q[1] * -tw[0] + q[2] * tw[3],
    q[3] * tw[3] - q[0] * -tw[0] - q[1] * -tw[1] - q[2] * -tw[2],
  ];
  const off = deg(2 * Math.acos(Math.min(1, Math.abs(sw[3]))));
  // Blockbench x -> true Rx(-x): a forward fold is a positive true angle about x; report it negative like the deformer does
  return { bend: -bend, off };
}

/** The upper body's turn over the hips: twist (about y), flex (forward +), side bend; degrees. */
function waist(w) {
  const L = w.lower.R, U = w.upper.R, R = [];
  for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) R.push(L[r] * U[c] + L[3 + r] * U[3 + c] + L[6 + r] * U[6 + c]);
  const f = [-R[2], -R[5], -R[8]], u = [R[1], R[4], R[7]];
  return { twist: deg(Math.atan2(-f[0], -f[2])), flex: deg(Math.asin(Math.max(-1, Math.min(1, -f[1])))), side: deg(Math.atan2(u[0], u[1])) };
}

module.exports = { RATE, times, frames, track, velocities, localQuat, hinge, waist, sub, len, dot, deg };
