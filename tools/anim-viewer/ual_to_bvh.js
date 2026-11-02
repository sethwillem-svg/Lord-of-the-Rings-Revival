#!/usr/bin/env node
/* Quaternius Universal Animation Library (UAL1/UAL2, CC0) and KayKit (CC0) .glb -> BVH clips for the mocap rig viewer.
 *
 *   node tools/anim-viewer/ual_to_bvh.js UAL1_Standard.glb tools/anim-viewer/library/ual [name-regex]
 *
 * Reads the "Unreal-Godot" .glb (Unreal mannequin bone names, glTF Y up, metres; use the file without _RM so clips
 * play in place) and writes one <Animation>.bvh per clip, resampled to 30 fps, in the Mixamo naming retarget.js
 * understands (LeftUpLeg = thigh, LeftLeg = shin, LeftHandIndex1 ...). The retargeter only reads joint positions,
 * so each joint gets three position channels holding its offset from its parent in world space (no rotations).
 * Kept joints: spine, neck, head, a synthetic HeadTop_End, clavicles, arms, the index/middle/pinky knuckles (for
 * "wrist follows the mocap"), legs, feet and toes. The T-pose reference clip is skipped.
 * KayKit's rigs (Kay Lousberg's character packs: hips, spine, chest, head, upperarm.l, wrist.l, upperleg.l ...) are
 * read too: no neck, clavicles or fingers, so the head's up axis and the shoulder line do that work.
 */
'use strict';
const fs = require('fs');
const path = require('path');

const [glbPath, outDir, filter] = process.argv.slice(2);
if (!glbPath || !outDir) {
  console.error('Usage: node ual_to_bvh.js <UAL*.glb> <out dir> [name regex]');
  process.exit(1);
}

const buf = fs.readFileSync(glbPath);
if (buf.readUInt32LE(0) !== 0x46546c67) throw new Error(glbPath + ' is not a .glb file');
const jsonLen = buf.readUInt32LE(12);
const gl = JSON.parse(buf.slice(20, 20 + jsonLen).toString('utf8'));
const bin = buf.slice(20 + jsonLen + 8);

function accessor(i) {
  const a = gl.accessors[i], v = gl.bufferViews[a.bufferView];
  if (a.componentType !== 5126) throw new Error('only float accessors are supported');
  const n = { SCALAR: 1, VEC3: 3, VEC4: 4 }[a.type];
  const off = bin.byteOffset + (v.byteOffset || 0) + (a.byteOffset || 0);
  const f = new Float32Array(bin.buffer.slice(off, off + a.count * n * 4));
  const out = [];
  for (let k = 0; k < a.count; k++) out.push(Array.from(f.subarray(k * n, k * n + n)));
  return out;
}

// ---------- names ----------
const SIDE = { l: 'Left', r: 'Right' };
const LIMB = { clavicle: 'Shoulder', upperarm: 'Arm', lowerarm: 'ForeArm', hand: 'Hand', thigh: 'UpLeg', calf: 'Leg', foot: 'Foot', ball: 'ToeBase' };
const FINGER = { index: 'Index', middle: 'Middle', pinky: 'Pinky' };
const KAYKIT = { upperarm: 'Arm', lowerarm: 'ForeArm', wrist: 'Hand', upperleg: 'UpLeg', lowerleg: 'Leg', foot: 'Foot', toes: 'ToeBase' };
function rename(n) {
  const fixed = { pelvis: 'Hips', spine_01: 'Spine', spine_02: 'Spine1', spine_03: 'Spine2', neck_01: 'Neck', Head: 'Head',
                  hips: 'Hips', spine: 'Spine', chest: 'Spine2', head: 'Head' };   // second row: KayKit
  if (fixed[n]) return fixed[n];
  const k = n.match(/^(upperarm|lowerarm|wrist|upperleg|lowerleg|foot|toes)\.([lr])$/);
  if (k) return SIDE[k[2]] + KAYKIT[k[1]];
  let m = n.match(/^(clavicle|upperarm|lowerarm|hand|thigh|calf|foot|ball)_([lr])$/);
  if (m) return SIDE[m[2]] + LIMB[m[1]];
  m = n.match(/^(index|middle|pinky)_0([12])_([lr])$/);
  if (m) return SIDE[m[3]] + 'Hand' + FINGER[m[1]] + m[2];
  return null;
}

// ---------- math ----------
const quatMat = ([x, y, z, w]) => [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
  2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)];
function mul(a, b) {
  const r = new Array(9);
  for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
  return r;
}
const apply = (m, v) => [0, 1, 2].map((i) => m[i * 3] * v[0] + m[i * 3 + 1] * v[1] + m[i * 3 + 2] * v[2]);
function slerp(a, b, t) {
  let d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
  if (d < 0) { b = b.map((x) => -x); d = -d; }
  if (d > 0.9995) { const r = a.map((x, i) => x + t * (b[i] - x)), l = Math.hypot(...r); return r.map((x) => x / l); }
  const th = Math.acos(d), s = Math.sin(th);
  return a.map((x, i) => (Math.sin((1 - t) * th) * x + Math.sin(t * th) * b[i]) / s);
}
function sample(ch, t) {
  const T = ch.times, V = ch.values;
  if (t <= T[0]) return V[0];
  if (t >= T[T.length - 1]) return V[V.length - 1];
  let k = 0;
  while (T[k + 1] < t) k++;
  if (ch.step) return V[k];
  const u = (t - T[k]) / (T[k + 1] - T[k]);
  return ch.path === 'rotation' ? slerp(V[k], V[k + 1], u) : V[k].map((x, i) => x + u * (V[k + 1][i] - x));
}

// ---------- skeleton ----------
const nodes = gl.nodes, parentOf = {};
nodes.forEach((n, i) => (n.children || []).forEach((c) => { parentOf[c] = i; }));
const skin = gl.skins[0].joints;
const kept = skin.filter((i) => rename(nodes[i].name));
const headNode = skin.find((i) => rename(nodes[i].name) === 'Head');
if (headNode === undefined || !kept.length) throw new Error('no Unreal-mannequin or KayKit skeleton found (expected pelvis/hips, Head/head, thigh_l/upperleg.l ...)');
const names = kept.map((i) => rename(nodes[i].name)).concat('HeadTop_End');
const parent = kept.map((i) => {
  let p = parentOf[i];
  while (p !== undefined && !kept.includes(p)) p = parentOf[p];
  return p === undefined ? -1 : kept.indexOf(p);
});
parent.push(kept.indexOf(headNode));
const children = names.map((_, j) => parent.map((p, k) => (p === j ? k : -1)).filter((k) => k >= 0));
const rootJ = parent.indexOf(-1);
const order = [];
(function walk(j) { order.push(j); children[j].forEach(walk); })(rootJ);

function hierarchy() {
  let s = 'HIERARCHY\n';
  (function emit(j, d) {
    const ind = '  '.repeat(d);
    s += `${ind}${d ? 'JOINT' : 'ROOT'} ${names[j]}\n${ind}{\n${ind}  OFFSET 0 0 0\n${ind}  CHANNELS 3 Xposition Yposition Zposition\n`;
    children[j].forEach((k) => emit(k, d + 1));
    s += `${ind}}\n`;
  })(rootJ, 0);
  return s;
}

// ---------- clips ----------
const FPS = 30;
fs.mkdirSync(outDir, { recursive: true });
let written = 0;
for (const anim of gl.animations) {
  if (/tpose/i.test(anim.name)) continue;
  if (filter && !new RegExp(filter, 'i').test(anim.name)) continue;
  const chans = {};
  let end = 0;
  for (const c of anim.channels) {
    const s = anim.samplers[c.sampler], times = accessor(s.input).map((x) => x[0]);
    end = Math.max(end, times[times.length - 1]);
    (chans[c.target.node] = chans[c.target.node] || {})[c.target.path] =
      { times, values: accessor(s.output), path: c.target.path, step: s.interpolation === 'STEP' };
  }
  const F = Math.max(2, Math.round(end * FPS) + 1);
  const rows = [];
  for (let f = 0; f < F; f++) {
    const t = Math.min(f / FPS, end), W = {};
    const world = (i) => {
      if (W[i]) return W[i];
      const n = nodes[i], c = chans[i] || {};
      const T = c.translation ? sample(c.translation, t) : (n.translation || [0, 0, 0]);
      const Q = c.rotation ? sample(c.rotation, t) : (n.rotation || [0, 0, 0, 1]);
      const S = c.scale ? sample(c.scale, t) : (n.scale || [1, 1, 1]);
      const L = mul(quatMat(Q), [S[0], 0, 0, 0, S[1], 0, 0, 0, S[2]]);
      if (parentOf[i] === undefined) return (W[i] = { p: T, m: L });
      const P = world(parentOf[i]), d = apply(P.m, T);
      return (W[i] = { p: [P.p[0] + d[0], P.p[1] + d[1], P.p[2] + d[2]], m: mul(P.m, L) });
    };
    const pos = kept.map((i) => world(i).p);
    const H = world(headNode), up = apply(H.m, [0, 0.2, 0]);   // head top: 20 cm along the head bone's own axis
    pos.push([H.p[0] + up[0], H.p[1] + up[1], H.p[2] + up[2]]);
    rows.push(pos.map((p) => p.map((x) => x * 100)));   // metres -> centimetres
  }
  let txt = hierarchy() + `MOTION\nFrames: ${F}\nFrame Time: ${(1 / FPS).toFixed(6)}\n`;
  for (const r of rows) {
    txt += order.map((j) => (parent[j] < 0 ? r[j] : r[j].map((x, k) => x - r[parent[j]][k]))
      .map((x) => (Math.abs(x) < 0.005 ? '0' : x.toFixed(2))).join(' ')).join(' ') + '\n';
  }
  fs.writeFileSync(path.join(outDir, anim.name + '.bvh'), txt);
  written++;
}
console.log(`Wrote ${written} clips (${names.length} joints, ${FPS} fps) to ${outDir}`);
