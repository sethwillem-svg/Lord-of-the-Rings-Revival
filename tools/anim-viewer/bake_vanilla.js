#!/usr/bin/env node
/* Bake library clips for vanilla 6-part biped models (head, body, two arms, two legs), e.g. the Middle-earth mod's NPCs.
 *
 *   node tools/anim-viewer/bake_vanilla.js [outDir]     bake every race and slot into the add-on (prints a QA report)
 *   npc.html                                            the same bake, live in the browser (window.VanillaBake)
 *
 * Each clip is retargeted onto the orc muster rig (as in the viewer), then reduced to the six vanilla parts:
 *   body  rotates about the waist (vanilla rotates it about the neck, so its pivot is moved to compensate)
 *   head  the rig head's world rotation, pivot on the moved neck
 *   arms  the upper arm's rotation, corrected so the straight vanilla arm points from shoulder to wrist (elbow bend folded in)
 *   legs  the same, hip to ankle
 * Output per clip (vanilla model space: px, y down; rotations as quaternions x,y,z,w for ModelPart rotationZYX):
 *   { dt, length, loop, hit, parts: { head: { q: [x,y,z,w, ...], p: [x,y,z, ...] }, body, right_arm, left_arm, right_leg, left_leg } }
 */
(function (root) {
  'use strict';

  const RACES = ['man', 'elf', 'dwarf', 'hobbit', 'orc'];
  const SLOTS = {
    idle: 'move.idle', walk: 'move.walk', run: 'move.run', hurt: 'react.hurt', death: 'react.death',
    attack_sword_1: 'attack.sword.1', attack_sword_2: 'attack.sword.2', attack_sword_3: 'attack.sword.3', attack_sword_4: 'attack.sword.4',
    attack_unarmed_1: 'attack.unarmed.1', attack_unarmed_2: 'attack.unarmed.2',
    // formation stances (specs/addon/hiring-reputation.md)
    guard: 'hold.sword', shield_brace: 'defend.brace.shield', spear_brace: 'hold.sword', march: 'move.march',
  };
  const LOOPS = new Set(['idle', 'walk', 'run', 'guard', 'shield_brace', 'spear_brace', 'march']);
  /** Slots that hold some parts still after baking: part -> vanilla rotation (radians, x y z). No library clip braces a spear,
   *  so spear_brace is the guard stance with the spear arm pointed forward. */
  const HOLD = { spear_brace: { right_arm: [-0.6, 0, 0] } };
  const PARTS = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'];
  /** Vanilla biped boxes in each part's own space (HumanoidModel.createMesh). */
  const BOXES = {
    head: [[-4, -8, -4], [4, 0, 4]], body: [[-4, 0, -2], [4, 12, 2]],
    right_arm: [[-3, -2, -2], [1, 10, 2]], left_arm: [[-1, -2, -2], [3, 10, 2]],
    right_leg: [[-2, 0, -2], [2, 12, 2]], left_leg: [[-2, 0, -2], [2, 12, 2]],
  };
  const MAX_TWIST = 720;   // deg/s an arm may twist about its own axis
  const RIG_PATH = 'src/main/art/blockbench/mordor_orc/mordor_orc.bbmodel';   // relative to the repo root

  // ---- small 3x3 helpers (row-major, same layout as retarget.js) ----
  const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
  const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
  const scl = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
  const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const norm = (a) => { const l = Math.hypot(...a); return l > 1e-9 ? scl(a, 1 / l) : [0, -1, 0]; };
  const apply = (m, v) => [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]];
  const mul = (a, b) => {
    const r = new Array(9);
    for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
    return r;
  };
  const tr = (m) => [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]];
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
  function quatToMat([x, y, z, w]) {
    return [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
            2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
            2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)];
  }
  const dot4 = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
  const r4 = (v) => Math.round(v * 1e4) / 1e4;

  /** Bones of the muster rig the bake reads. */
  function rigBones(rig) {
    const I = (n) => { if (rig.idx[n] === undefined) throw new Error('rig has no bone ' + n); return rig.idx[n]; };
    const B = { body: I('body'), head: I('head'), ra: I('right_arm'), rh: I('right_hand'), la: I('left_arm'), lh: I('left_hand'),
                rl: I('right_leg'), rs: I('right_shin'), ll: I('left_leg'), ls: I('left_shin') };
    B.scale = 12 / rig.bones[B.rl].origin[1];               // rig hip height -> vanilla hip height (12 px)
    return B;
  }

  /** One retargeted clip -> six vanilla parts per frame. A is retarget.js. */
  function bake(A, rig, clip) {
    const B = rigBones(rig), S = B.scale;
    const shinLen = (i) => rig.bones[i].origin[1];          // ankle at the floor in the rest pose
    const parts = {};
    for (const k of PARTS) parts[k] = [];
    const put = (name, R, P) => parts[name].push({ q: quat(toModelMat(R)), p: toModelPos(P) });
    const arm = { right: {}, left: {} };
    for (const fr of clip.frames) {
      const fk = A.rigFK(rig, fr.locals, fr.rootPos);
      const root = scl(fr.rootPos, S);
      const waist = add([0, 12, 0], root);
      const Rb = fk.rot[B.body];
      const at = (v) => add(waist, apply(Rb, v));          // a point of the vanilla torso, rotated about the waist
      const neck = at([0, 12, 0]);
      put('body', Rb, neck);
      put('head', fk.rot[B.head], neck);
      // arms: aim shoulder -> wrist; the twist about the arm follows the hand (the held item hangs off it in game).
      // The pose is carried over from the previous frame (no swing-twist singularity with the arm overhead) and the
      // twist may change at most MAX_TWIST deg/s, which removes the retarget's one-frame wrist flips.
      for (const [side, iA, iH, x] of [['right', B.ra, B.rh, -5], ['left', B.la, B.lh, 5]]) {
        const H = fk.rot[iH], dir = norm(sub(fk.pos[iH], fk.pos[iA]));
        const target = mul(between(norm(apply(H, [0, -1, 0])), dir), H);
        const st = arm[side];
        let R = target;
        if (st.R) {
          const carried = mul(between(st.dir, dir), st.R);
          const u = norm(apply(carried, [0, 0, -1])), v = norm(apply(target, [0, 0, -1]));
          const phi = Math.atan2(dot(cross(u, v), dir), dot(u, v)), cap = MAX_TWIST * Math.PI / 180 * clip.dt;
          R = mul(axisAngle(dir, Math.max(-cap, Math.min(cap, phi))), carried);
        }
        st.R = R; st.dir = dir;
        put(side + '_arm', R, at([x, 10, 0]));
      }
      for (const [side, iL, iS, x] of [['right', B.rl, B.rs, -1.9], ['left', B.ll, B.ls, 1.9]]) {
        const foot = add(fk.pos[iS], apply(fk.rot[iS], [0, -shinLen(iS), 0]));
        const dir = norm(sub(foot, fk.pos[iL]));
        // no swing inward past vertical: vanilla legs are 4 px boxes touching at rest, so any crossing clips
        dir[0] = side === 'right' ? Math.min(dir[0], 0) : Math.max(dir[0], 0);
        // swing only, no twist: the mocap hip twist makes a straight box leg swivel
        put(side + '_leg', between([0, -1, 0], norm(dir)), add([x, 12, 0], root));
      }
    }
    // floor contact: straight legs cannot bend, so shift each frame until the lowest box corner touches the floor (y = 24)
    const n = parts.head.length;
    for (let f = 0; f < n; f++) {
      let low = -Infinity;
      for (const k of PARTS) {
        const { q, p } = parts[k][f], R = quatToMat(q), [a, b] = BOXES[k];
        for (const cx of [a[0], b[0]]) for (const cy of [a[1], b[1]]) for (const cz of [a[2], b[2]])
          low = Math.max(low, p[1] + apply(R, [cx, cy, cz])[1]);
      }
      for (const k of PARTS) parts[k][f].p[1] += 24 - low;
    }
    const out = {};
    for (const [k, list] of Object.entries(parts)) {
      // keep quaternions on one hemisphere so the runtime can slerp between neighbours without flipping
      for (let f = 1; f < list.length; f++) if (dot4(list[f].q, list[f - 1].q) < 0) list[f].q = list[f].q.map((v) => -v);
      out[k] = { q: list.flatMap((e) => e.q.map(r4)), p: list.flatMap((e) => e.p.map(r4)) };
    }
    return out;
  }

  /** Retarget options the bake uses for a library entry (the viewer's library defaults, in place). */
  function bakeOptions(A, LIB, race, entry) {
    const r = LIB.races[race];
    return Object.assign({}, A.DEFAULTS, LIB.defaults, r.settings || {}, entry.mirror ? { mirror: true } : {}, entry.opts || {},
                         { footLift: true, inPlace: true });
  }

  /** Join a library entry's source clips (same rules as the viewer). readText(relPath) returns the BVH text. */
  function joinBvh(A, LIB, entry, readText) {
    const dir = LIB.sources[entry.from || 'ual'].dir;
    const parts = [].concat(entry.src).map((s) => A.parseBVH(readText(dir + s + '.bvh')));
    const b = parts[0];
    for (const p of parts.slice(1)) b.data = b.data.concat(p.data);
    if (entry.range) b.data = b.data.slice(entry.range[0], entry.range[1] + 1);
    b.frames = b.data.length;
    if (entry.speed) b.dt /= entry.speed;
    return b;
  }

  /** Library entry for a race's slot, with vanilla_overrides.json (written from npc.html) applied on top:
   *  { race: { slot: { src, from, range, speed, mirror, opts } } }. */
  function resolveEntry(LIB, overrides, race, slot) {
    const r = LIB.races[race], key = SLOTS[slot];
    const base = r.clips[key] || (r.unique || {})[key];
    const o = overrides && overrides[race] && overrides[race][slot];
    if (!o) return base || null;
    const e = Object.assign({}, o.src ? {} : base || {}, o);
    if (base && base.opts && !o.src) e.opts = Object.assign({}, base.opts, o.opts || {});
    return e.src ? e : null;
  }

  function clipData(A, rig, clip, slot) {
    const parts = bake(A, rig, clip);
    for (const [name, [x, y, z]] of Object.entries(HOLD[slot] || {})) {
      const q = eulerQuat(x, y, z), P = parts[name];
      for (let i = 0; i < P.q.length; i += 4) P.q.splice(i, 4, ...q);
    }
    return { dt: +clip.dt.toFixed(6), length: +clip.length.toFixed(4), loop: LOOPS.has(slot), hit: +clip.hitTime.toFixed(4), parts };
  }

  /** [x y z w] for a vanilla ModelPart rotation (applied z, then y, then x: the inverse of NpcAnimator.set). */
  function eulerQuat(x, y, z) {
    const cx = Math.cos(x / 2), sx = Math.sin(x / 2), cy = Math.cos(y / 2), sy = Math.sin(y / 2), cz = Math.cos(z / 2), sz = Math.sin(z / 2);
    return [sx * cy * cz - cx * sy * sz, cx * sy * cz + sx * cy * sz, cx * cy * sz - sx * sy * cz, cx * cy * cz + sx * sy * sz].map((v) => +v.toFixed(6));
  }

  /** Pose of frame f: {part: {q, p, R}}. */
  function frame(data, f) {
    const out = {};
    for (const k of PARTS) {
      const P = data.parts[k], q = P.q.slice(f * 4, f * 4 + 4);
      out[k] = { q, p: P.p.slice(f * 3, f * 3 + 3), R: quatToMat(q) };
    }
    return out;
  }

  /**
   * What tends to look wrong on a boxy vanilla biped, per frame (vanilla model space, y down, floor at y = 24).
   * Returns [{ id, label, frames: [f...] }] for every check that fired.
   */
  function qa(data, slot) {
    const n = data.parts.head.q.length / 4;
    const checks = {
      arm_in_body: { label: 'hand inside the torso', frames: [] },
      legs_cross: { label: 'legs cross', frames: [] },
      under_floor: { label: 'foot below the floor', frames: [] },
      floating: { label: 'both feet off the floor', frames: [] },
      leg_twist: { label: 'leg twisted > 45 deg', frames: [] },
      head_turn: { label: 'head turned > 80 deg from the torso', frames: [] },
      pop: { label: 'part jumps > 50 deg in one frame', frames: [] },
      bent_back: { label: 'torso leans > 60 deg', frames: [] },
    };
    let prev = null;
    for (let f = 0; f < n; f++) {
      const P = frame(data, f), body = P.body;
      const local = (pt) => apply(tr(body.R), sub(pt, body.p));       // into the torso box (x -4..4, y 0..12, z -2..2)
      for (const side of ['right_arm', 'left_arm']) {
        const hand = add(P[side].p, apply(P[side].R, [side === 'right_arm' ? -1 : 1, 9, 0]));
        const l = local(hand);
        if (Math.abs(l[0]) < 3 && l[1] > 1 && l[1] < 11 && Math.abs(l[2]) < 1.5) { checks.arm_in_body.frames.push(f); break; }
      }
      const foot = (k) => add(P[k].p, apply(P[k].R, [0, 12, 0]));
      const fr = foot('right_leg'), fl = foot('left_leg');
      if (fr[0] > fl[0] - 0.5) checks.legs_cross.frames.push(f);
      if (Math.max(fr[1], fl[1]) > 24.75) checks.under_floor.frames.push(f);
      if (Math.max(fr[1], fl[1]) < 21) checks.floating.frames.push(f);
      for (const k of ['right_leg', 'left_leg']) {
        // twist about the leg's own long axis (swing-twist split): what makes a box leg look screwed in
        const R = P[k].R, T = mul(tr(between([0, 1, 0], norm(apply(R, [0, 1, 0])))), R);
        if (Math.abs(Math.atan2(T[2], T[0])) * 180 / Math.PI > 45) { checks.leg_twist.frames.push(f); break; }
      }
      const hf = apply(P.head.R, [0, 0, -1]), bf = apply(body.R, [0, 0, -1]);
      if (Math.acos(Math.max(-1, Math.min(1, dot(hf, bf)))) * 180 / Math.PI > 80) checks.head_turn.frames.push(f);
      const up = apply(body.R, [0, -1, 0]);
      if (Math.acos(Math.max(-1, Math.min(1, -up[1]))) * 180 / Math.PI > 60) checks.bent_back.frames.push(f);
      if (prev) for (const k of PARTS) {
        const c = Math.abs(dot4(prev[k].q, P[k].q));
        if (2 * Math.acos(Math.min(1, c)) * 180 / Math.PI > 50) { checks.pop.frames.push(f); break; }
      }
      prev = P;
    }
    if (slot === 'death') { delete checks.floating; delete checks.bent_back; }
    return Object.entries(checks).filter(([, c]) => c.frames.length).map(([id, c]) => ({ id, label: c.label, frames: c.frames, share: c.frames.length / n }));
  }

  const api = { RACES, SLOTS, LOOPS, HOLD, PARTS, BOXES, RIG_PATH, bake, bakeOptions, resolveEntry, joinBvh, clipData, frame, qa, quatToMat };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.VanillaBake = api;

  // ---- CLI ----
  if (typeof require !== 'undefined' && typeof module !== 'undefined' && require.main === module) {
    const fs = require('fs');
    const path = require('path');
    const A = require('./retarget.js');
    const LIB = require('./library.js');
    const MAPS = require('./rigs.js');
    const HERE = __dirname, REPO = path.resolve(HERE, '../..');
    const OUT = path.resolve(process.argv[2] || path.join(REPO, 'addon/src/main/resources/assets/lotr_revival/clips'));
    const rig = A.parseRig(JSON.parse(fs.readFileSync(path.join(REPO, RIG_PATH), 'utf8')));
    const map = MAPS.find((m) => m.id === 'muster-pack');
    const OVR = path.join(HERE, 'vanilla_overrides.json');
    const OVERRIDES = fs.existsSync(OVR) ? JSON.parse(fs.readFileSync(OVR, 'utf8')) : {};
    const index = {};
    let n = 0;
    for (const race of RACES) {
      const r = LIB.races[race];
      fs.mkdirSync(path.join(OUT, race), { recursive: true });
      index[race] = [];
      for (const [slot, key] of Object.entries(SLOTS)) {
        const entry = resolveEntry(LIB, OVERRIDES, race, slot);
        if (!entry) continue;
        let bvh;
        try { bvh = joinBvh(A, LIB, entry, (rel) => fs.readFileSync(path.join(HERE, rel), 'utf8')); }
        catch (e) { console.warn(`skip ${race}/${slot}: ${e.message}`); continue; }
        const data = clipData(A, rig, A.retarget(bvh, rig, map, bakeOptions(A, LIB, race, entry)), slot);
        fs.writeFileSync(path.join(OUT, race, slot + '.json'), JSON.stringify(data));
        index[race].push(slot);
        n++;
        const issues = qa(data, slot).filter((c) => c.share >= 0.05);
        if (issues.length) console.log(`  ${race}/${slot}: ` + issues.map((c) => `${c.label} ${Math.round(c.share * 100)}%`).join(', '));
      }
    }
    fs.writeFileSync(path.join(OUT, 'index.json'), JSON.stringify(index, null, 1));
    console.log(`baked ${n} clips into ${path.relative(REPO, OUT)}`);
  }
})(typeof window !== 'undefined' ? window : globalThis);
