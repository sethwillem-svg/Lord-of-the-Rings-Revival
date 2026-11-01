#!/usr/bin/env node
/* Bake library clips for vanilla 6-part biped models (head, body, two arms, two legs), e.g. the Middle-earth mod's NPCs.
 *
 *   node tools/anim-viewer/bake_vanilla.js [outDir]
 *
 * Each clip is retargeted onto the orc muster rig (as in the viewer), then reduced to the six vanilla parts:
 *   body  rotates about the waist (vanilla rotates it about the neck, so its pivot is moved to compensate)
 *   head  the rig head's world rotation, pivot on the moved neck
 *   arms  the upper arm's rotation, corrected so the straight vanilla arm points from shoulder to wrist (elbow bend folded in)
 *   legs  the same, hip to ankle
 * Output per clip (vanilla model space: px, y down; rotations as quaternions x,y,z,w for ModelPart rotationZYX):
 *   { dt, length, loop, parts: { head: { q: [x,y,z,w, ...], p: [x,y,z, ...] }, body, right_arm, left_arm, right_leg, left_leg } }
 */
'use strict';
const fs = require('fs');
const path = require('path');
const A = require('./retarget.js');
const LIB = require('./library.js');
const MAPS = require('./rigs.js');

const HERE = __dirname;
const REPO = path.resolve(HERE, '../..');
const OUT = path.resolve(process.argv[2] || path.join(REPO, 'addon/src/main/resources/assets/lotr_revival/clips'));
const RIG_FILE = path.join(REPO, 'src/main/art/blockbench/mordor_orc/mordor_orc.bbmodel');

const RACES = ['man', 'elf', 'dwarf', 'hobbit', 'orc'];
const SLOTS = {
  idle: 'move.idle', walk: 'move.walk', run: 'move.run', hurt: 'react.hurt', death: 'react.death',
  attack_sword_1: 'attack.sword.1', attack_sword_2: 'attack.sword.2', attack_sword_3: 'attack.sword.3', attack_sword_4: 'attack.sword.4',
  attack_unarmed_1: 'attack.unarmed.1', attack_unarmed_2: 'attack.unarmed.2',
};
const LOOPS = new Set(['idle', 'walk', 'run']);

// ---- small 3x3 helpers (row-major, same layout as retarget.js) ----
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const scl = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(...a); return l > 1e-9 ? scl(a, 1 / l) : [0, -1, 0]; };
const apply = (m, v) => [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]];
function axisAngle([x, y, z], t) {
  const c = Math.cos(t), s = Math.sin(t), k = 1 - c;
  return [k * x * x + c, k * x * y - s * z, k * x * z + s * y,
          k * x * y + s * z, k * y * y + c, k * y * z - s * x,
          k * x * z - s * y, k * y * z + s * x, k * z * z + c];
}
/** Smallest rotation taking unit vector a onto unit vector b. */
function between(a, b) {
  const c = Math.max(-1, Math.min(1, dot(a, b)));
  let ax = cross(a, b);
  if (Math.hypot(...ax) < 1e-8) {
    if (c > 0) return [1, 0, 0, 0, 1, 0, 0, 0, 1];
    ax = Math.abs(a[0]) < 0.9 ? cross(a, [1, 0, 0]) : cross(a, [0, 1, 0]);
  }
  return axisAngle(norm(ax), Math.acos(c));
}
/** Rig space (Blockbench: y up) -> vanilla model space (y down). x and z keep their sign: the rig's right_arm sits at
 *  -x like vanilla's rightArm, and both face -z. */
const toModelMat = (m) => [m[0], -m[1], m[2], -m[3], m[4], -m[5], m[6], -m[7], m[8]];
const toModelPos = (p) => [p[0], 24 - p[1], p[2]];
function quat(m) {
  const t = m[0] + m[4] + m[8];
  let x, y, z, w;
  if (t > 0) { const s = Math.sqrt(t + 1) * 2; w = s / 4; x = (m[7] - m[5]) / s; y = (m[2] - m[6]) / s; z = (m[3] - m[1]) / s; }
  else if (m[0] > m[4] && m[0] > m[8]) { const s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2; w = (m[7] - m[5]) / s; x = s / 4; y = (m[1] + m[3]) / s; z = (m[2] + m[6]) / s; }
  else if (m[4] > m[8]) { const s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2; w = (m[2] - m[6]) / s; x = (m[1] + m[3]) / s; y = s / 4; z = (m[5] + m[7]) / s; }
  else { const s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2; w = (m[3] - m[1]) / s; x = (m[2] + m[6]) / s; y = (m[5] + m[7]) / s; z = s / 4; }
  return [x, y, z, w];
}

// ---- clip loading (same joining rules as the viewer's libBvh) ----
function loadBvh(entry) {
  const dir = path.join(HERE, LIB.sources[entry.from || 'ual'].dir);
  const parts = [].concat(entry.src).map((s) => A.parseBVH(fs.readFileSync(path.join(dir, s + '.bvh'), 'utf8')));
  const b = parts[0];
  for (const p of parts.slice(1)) b.data = b.data.concat(p.data);
  if (entry.range) b.data = b.data.slice(entry.range[0], entry.range[1] + 1);
  b.frames = b.data.length;
  if (entry.speed) b.dt /= entry.speed;
  return b;
}

const rig = A.parseRig(JSON.parse(fs.readFileSync(RIG_FILE, 'utf8')));
const map = MAPS.find((m) => m.id === 'muster-pack');
const I = (n) => { if (rig.idx[n] === undefined) throw new Error('rig has no bone ' + n); return rig.idx[n]; };
const B = { body: I('body'), head: I('head'), ra: I('right_arm'), rh: I('right_hand'), la: I('left_arm'), lh: I('left_hand'),
            rl: I('right_leg'), rs: I('right_shin'), ll: I('left_leg'), ls: I('left_shin') };
const shinLen = (i) => rig.bones[i].origin[1];          // ankle at the floor in the rest pose
const S = 12 / rig.bones[B.rl].origin[1];               // rig hip height -> vanilla hip height (12 px)

function bake(clip) {
  const parts = { head: [], body: [], right_arm: [], left_arm: [], right_leg: [], left_leg: [] };
  const put = (name, R, P) => parts[name].push({ q: quat(toModelMat(R)), p: toModelPos(P) });
  for (const fr of clip.frames) {
    const fk = A.rigFK(rig, fr.locals, fr.rootPos);
    const root = scl(fr.rootPos, S);
    const waist = add([0, 12, 0], root);
    const Rb = fk.rot[B.body];
    const at = (v) => add(waist, apply(Rb, v));          // a point of the vanilla torso, rotated about the waist
    const neck = at([0, 12, 0]);
    put('body', Rb, neck);
    put('head', fk.rot[B.head], neck);
    const limb = (iU, end, pivot) => {
      const R = fk.rot[iU];
      return between(norm(apply(R, [0, -1, 0])), norm(sub(end, fk.pos[iU])));
    };
    for (const [side, iA, iH, x] of [['right', B.ra, B.rh, -5], ['left', B.la, B.lh, 5]]) {
      const C = limb(iA, fk.pos[iH]);
      put(side + '_arm', A.mul(C, fk.rot[iA]), at([x, 10, 0]));
    }
    for (const [side, iL, iS, x] of [['right', B.rl, B.rs, -1.9], ['left', B.ll, B.ls, 1.9]]) {
      const foot = add(fk.pos[iS], apply(fk.rot[iS], [0, -shinLen(iS), 0]));
      const C = limb(iL, foot);
      put(side + '_leg', A.mul(C, fk.rot[iL]), add([x, 12, 0], root));
    }
  }
  const out = {};
  for (const [k, list] of Object.entries(parts)) {
    // keep quaternions on one hemisphere so the runtime can slerp between neighbours without flipping
    for (let f = 1; f < list.length; f++) if (dot4(list[f].q, list[f - 1].q) < 0) list[f].q = list[f].q.map((v) => -v);
    out[k] = { q: list.flatMap((e) => e.q.map(r4)), p: list.flatMap((e) => e.p.map(r4)) };
  }
  return out;
}
const dot4 = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
const r4 = (v) => Math.round(v * 1e4) / 1e4;

let n = 0;
const index = {};
for (const race of RACES) {
  const r = LIB.races[race];
  fs.mkdirSync(path.join(OUT, race), { recursive: true });
  index[race] = [];
  for (const [slot, key] of Object.entries(SLOTS)) {
    const entry = r.clips[key] || (r.unique || {})[key];
    if (!entry) continue;
    let bvh;
    try { bvh = loadBvh(entry); } catch (e) { console.warn(`skip ${race}/${slot}: ${e.message}`); continue; }
    const opts = Object.assign({}, A.DEFAULTS, LIB.defaults, r.settings || {}, entry.mirror ? { mirror: true } : {}, entry.opts || {},
                               { footLift: true, inPlace: true });
    const clip = A.retarget(bvh, rig, map, opts);
    const data = { dt: +clip.dt.toFixed(6), length: +clip.length.toFixed(4), loop: LOOPS.has(slot), hit: +clip.hitTime.toFixed(4),
                   parts: bake(clip) };
    fs.writeFileSync(path.join(OUT, race, slot + '.json'), JSON.stringify(data));
    index[race].push(slot);
    n++;
  }
}
fs.writeFileSync(path.join(OUT, 'index.json'), JSON.stringify(index, null, 1));
console.log(`baked ${n} clips into ${path.relative(REPO, OUT)}`);
