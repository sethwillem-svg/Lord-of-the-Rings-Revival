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
    idle_2: 'move.idle.1', walk_2: 'move.walk.2',   // variants: one per NPC
    attack_sword_1: 'attack.sword.1', attack_sword_2: 'attack.sword.2', attack_sword_3: 'attack.sword.3', attack_sword_4: 'attack.sword.4',
    attack_unarmed_1: 'attack.unarmed.1', attack_unarmed_2: 'attack.unarmed.2',
    // formation stances (specs/addon/hiring-reputation.md)
    guard: 'hold.sword', shield_brace: 'defend.brace.shield', spear_brace: 'hold.sword', march: 'move.march',
    // Attack sets per weapon class (attack_<class>_N, combat.AttackClips) and ranged clips (bow_*, crossbow_*) are not baked
    // for now: the KayKit retargets did not hold up in game. Classes without clips swing a sword; bows keep Minecraft's pose.
  };
  const LOOPS = new Set(['idle', 'walk', 'run', 'idle_2', 'walk_2', 'guard', 'shield_brace', 'spear_brace', 'march', 'spear_guard', 'bow_aim', 'crossbow_aim']);
  /** Slots that hold some parts and joints still after baking: part -> vanilla rotation (radians, x y z), joint -> angle.
   *  spear_brace: the guard stance with the spear arm's forearm raised forward (no library clip braces a spear);
   *  shield_brace: the shield forearm upright in front of the chest, turned a little inward. */
  const HOLD = {
    spear_brace: { parts: { right_arm: [-0.5, 0, 0] }, joints: { right_elbow: -1.42 } },
    shield_brace: { parts: { left_arm: [-1.0, 0.4, 0] }, joints: { left_elbow: -2.14 } },
  };
  /**
   * How a stance re-seats the held item (client.render.NpcJoints holds the same numbers). Middle-earth holds a spear at an
   * angle to the forearm and a shield face-out, and each model differs, so the item's own shape decides (itemHold): the
   * spear's long axis is turned onto `to` with its shaft through `at` (the fist: couched along the forearm), the shield's
   * face (outer side) onto `to` with its centre at `at` (in front of the forearm). Arm space, px; `to` is where the pose
   * (HOLD) turns forward.
   */
  const ITEM_HOLD = {
    spear_brace: { part: 'right_arm', kind: 'axis', to: [0, 1, 0], at: [-4, 10, 0] },   // along the outside of the forearm
    shield_brace: { part: 'left_arm', kind: 'face', to: [0, 0, 1], at: [1, 7.5, 3.5] },
  };

  /** Centre and principal axes (longest first) of a point cloud [x, y, z, ...]: Jacobi on the covariance. */
  function shape(pos) {
    const n = pos.length / 3, c = [0, 0, 0];
    for (let i = 0; i < pos.length; i += 3) { c[0] += pos[i] / n; c[1] += pos[i + 1] / n; c[2] += pos[i + 2] / n; }
    const A = [[0, 0, 0], [0, 0, 0], [0, 0, 0]];
    for (let i = 0; i < pos.length; i += 3) {
      const d = [pos[i] - c[0], pos[i + 1] - c[1], pos[i + 2] - c[2]];
      for (let r = 0; r < 3; r++) for (let k = 0; k < 3; k++) A[r][k] += d[r] * d[k];
    }
    const V = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
    for (let sweep = 0; sweep < 30; sweep++) {
      for (const [p, q] of [[0, 1], [0, 2], [1, 2]]) {
        if (Math.abs(A[p][q]) < 1e-12) continue;
        const th = 0.5 * Math.atan2(2 * A[p][q], A[q][q] - A[p][p]), cs = Math.cos(th), sn = Math.sin(th);
        for (let k = 0; k < 3; k++) { const a = A[k][p], b = A[k][q]; A[k][p] = cs * a - sn * b; A[k][q] = sn * a + cs * b; }
        for (let k = 0; k < 3; k++) { const a = A[p][k], b = A[q][k]; A[p][k] = cs * a - sn * b; A[q][k] = sn * a + cs * b; }
        for (let k = 0; k < 3; k++) { const a = V[k][p], b = V[k][q]; V[k][p] = cs * a - sn * b; V[k][q] = sn * a + cs * b; }
      }
    }
    const axes = [0, 1, 2].map((k) => ({ v: norm([V[0][k], V[1][k], V[2][k]]), e: A[k][k] })).sort((a, b) => b.e - a.e);
    return { c, axes: axes.map((a) => a.v) };
  }

  /**
   * Held weapons by class (client.render.WeaponGrip holds the same numbers): every weapon of a class is scaled to its length
   * (blocks) along its long axis, with its grip in the fist. Spears are gripped a third of the way up and carried leaning
   * forward; other classes keep Middle-earth's angle.
   */
  const WEAPON_LENGTH = { dagger: 0.6, sword: 1.0, longsword: 1.4, axe: 1.1, spear: 2.5, bow: 1.1, longbow: 1.6, crossbow: 0.9 };
  const FIST = { right_arm: [-1, 10, -2.5], left_arm: [1, 10, -2.5] };   // the front of the fist: grips sit there, not inside the arm
  const SPEAR_GRIP = 0.35, SPEAR_DIR = [0, -1.2 / Math.hypot(1.2, 1), -1 / Math.hypot(1.2, 1)];

  /** A weapon's grip: scale to its class length, grip in the fist (row-major 4x4, arm space px), or null for other items. */
  function weaponGrip(cls, part, pos) {
    const len = WEAPON_LENGTH[cls];
    if (!len || pos.length < 9) return null;
    const { c, axes } = shape(pos);
    let a = axes[0];
    if (dot(a, [0, -1, -1]) < 0) a = scl(a, -1);              // tip end: held items point forward and up out of the fist
    let lo = Infinity, hi = -Infinity;
    for (let i = 0; i < pos.length; i += 3) { const t = dot(sub([pos[i], pos[i + 1], pos[i + 2]], c), a); lo = Math.min(lo, t); hi = Math.max(hi, t); }
    if (hi - lo < 1e-3) return null;
    const fist = FIST[part];
    const tg = cls === 'spear' ? lo + SPEAR_GRIP * (hi - lo) : Math.max(lo, Math.min(hi, dot(sub(fist, c), a)));
    const grip = add(c, scl(a, tg)), s = len * 16 / (hi - lo);
    const R = cls === 'spear' ? between(a, SPEAR_DIR) : [1, 0, 0, 0, 1, 0, 0, 0, 1];
    const M = R.map((v) => v * s), t = sub(fist, apply(M, grip));   // p' = fist + s R (p - grip)
    return [M[0], M[1], M[2], t[0], M[3], M[4], M[5], t[1], M[6], M[7], M[8], t[2], 0, 0, 0, 1];
  }

  /** Points through a row-major 4x4. */
  function transformPoints(m, pos) {
    const out = new Array(pos.length);
    for (let i = 0; i < pos.length; i += 3) {
      const x = pos[i], y = pos[i + 1], z = pos[i + 2];
      out[i] = m[0] * x + m[1] * y + m[2] * z + m[3];
      out[i + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
      out[i + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
    }
    return out;
  }

  /** The stance's re-seating of a held item (points pos, arm space px) as a row-major 4x4 matrix, or null. */
  function itemHold(slot, part, pos) {
    const h = ITEM_HOLD[slot];
    if (!h || h.part !== part || pos.length < 9) return null;
    const { c, axes } = shape(pos);
    let from, pivot;
    if (h.kind === 'axis') {
      let a = axes[0];
      pivot = add(c, scl(a, dot(sub(h.at, c), a)));        // the shaft's point nearest the fist
      if (dot(a, [0, -1, -1]) < 0) a = scl(a, -1);          // the tip: held items point forward and up out of the fist
      from = a;
    } else {
      let nrm = axes[2];
      const armX = part === 'right_arm' ? -1 : 1;
      if (dot(nrm, sub(c, [armX, c[1], 0])) < 0) nrm = scl(nrm, -1);   // the outer face: away from the arm
      from = nrm; pivot = c;
    }
    const R = between(norm(from), h.to), t = sub(h.at, apply(R, pivot));
    return [R[0], R[1], R[2], t[0], R[3], R[4], R[5], t[1], R[6], R[7], R[8], t[2], 0, 0, 0, 1];
  }
  const PARTS = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'];
  /** Vanilla biped boxes in each part's own space (HumanoidModel.createMesh). */
  const BOXES = {
    head: [[-4, -8, -4], [4, 0, 4]], body: [[-4, 0, -2], [4, 12, 2]],
    right_arm: [[-3, -2, -2], [1, 10, 2]], left_arm: [[-1, -2, -2], [3, 10, 2]],
    right_leg: [[-2, 0, -2], [2, 12, 2]], left_leg: [[-2, 0, -2], [2, 12, 2]],
  };
  const MAX_TWIST = 720;   // deg/s an arm may twist about its own axis
  /**
   * Flexible joints, drawn in game with PulseLib's bend and twist deformers (client.render.NpcJoints holds the same numbers).
   * Part space, px, y down. Every op works on the zone [origin, origin + extent] along +y: a bend curves the zone about its
   * axis and turns everything below it rigidly by the angle; a twist turns the zone progressively about +y. Applied in order.
   */
  const JOINTS = {
    body: [{ name: 'waist_yaw', op: 'twist', origin: 6, extent: 3, axis: [0, 1, 0] },
           { name: 'waist_pitch', op: 'bend', origin: 6, extent: 3, axis: [1, 0, 0] },
           { name: 'waist_roll', op: 'bend', origin: 6, extent: 3, axis: [0, 0, 1] }],
    right_arm: [{ name: 'right_elbow', op: 'bend', origin: 3, extent: 2, axis: [1, 0, 0] }],
    left_arm: [{ name: 'left_elbow', op: 'bend', origin: 3, extent: 2, axis: [1, 0, 0] }],
    right_leg: [{ name: 'right_knee', op: 'bend', origin: 5, extent: 2, axis: [1, 0, 0] }],
    left_leg: [{ name: 'left_knee', op: 'bend', origin: 5, extent: 2, axis: [1, 0, 0] }],
  };
  const JOINT_NAMES = Object.values(JOINTS).flat().map((j) => j.name);
  const WAIST = 7.5;       // waist joint below the neck (px): the middle of the body's bend zone
  const WAIST_TWIST = 0.6; // share of the mocap's hip-to-chest twist kept (all of it reads as too much on a box torso)
  const JOINT_SHARE = 0.75; // share of the mocap's elbow, knee and waist bend kept: box limbs read as rubbery with all of it
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
  const norm4 = (a) => { const l = Math.hypot(...a) || 1; return a.map((v) => v / l); };
  const clamp1 = (v) => Math.max(-1, Math.min(1, v));

  /** A point of a part (part space, px) moved by that part's joints (angles: joint name -> radians). PulseLib's bend and
   *  twist operations, with no negative extent. */
  function deform(part, angles, p) {
    let v = p;
    for (const j of JOINTS[part] || []) {
      const ang = (angles && angles[j.name]) || 0;
      if (Math.abs(ang) < 1e-5) continue;
      const L = [0, 1, 0], O = [0, j.origin, 0], d = sub(v, O), along = dot(d, L), t = Math.max(0, Math.min(j.extent, along));
      if (j.op === 'twist') { v = add(O, apply(axisAngle(L, ang * t / j.extent), d)); continue; }
      const ax = j.axis, rad = cross(ax, L), r = dot(d, rad), a = dot(d, ax), k = ang / j.extent, R = 1 / k;
      const q = axisAngle(ax, k * t);
      v = add(add(add(sub(scl(rad, R), apply(q, scl(rad, R - r))), scl(apply(q, L), along - t)), scl(ax, a)), O);
    }
    return v;
  }
  const r4 = (v) => Math.round(v * 1e4) / 1e4;

  /** Bones of the muster rig the bake reads. */
  function rigBones(rig) {
    const I = (n) => { if (rig.idx[n] === undefined) throw new Error('rig has no bone ' + n); return rig.idx[n]; };
    const B = { root: I('root'), body: I('body'), head: I('head'),
                ra: I('right_arm'), rf: I('right_forearm'), rh: I('right_hand'), la: I('left_arm'), lf: I('left_forearm'), lh: I('left_hand'),
                rl: I('right_leg'), rs: I('right_shin'), ll: I('left_leg'), ls: I('left_shin') };
    B.scale = 12 / rig.bones[B.rl].origin[1];               // rig hip height -> vanilla hip height (12 px)
    return B;
  }

  /** One retargeted clip -> six vanilla parts and the joint angles per frame: { parts, joints }. A is retarget.js. */
  /**
   * Race posture on top of every clip (LIB.races[race].settings.style), no speed changes: hunch (deg, chest leans forward),
   * armsOut (deg, arms held away from the body; negative: closer), kneeBend (deg added to both knees, thighs lean forward by
   * half to keep the feet under the hips), stride (share of the mocap's leg swing: shorter steps).
   */
  function bake(A, rig, clip, style, lockHeading) {
    const st = Object.assign({ hunch: 0, armsOut: 0, kneeBend: 0, stride: 1 }, style || {}), D = Math.PI / 180;
    // loops (walks) keep one heading: mocap takes walked in curves would turn the NPC. The pelvis heading, smoothed over
    // a second, is taken out of every frame, so the hips' natural sway stays and only the drift goes.
    const drift = clip.frames.map((fr) => {
      const px = apply(fr.pelvis || [1, 0, 0, 0, 1, 0, 0, 0, 1], [1, 0, 0]);
      return Math.atan2(-px[2], px[0]);
    });
    for (let f = 1; f < drift.length; f++) {   // unwrap
      while (drift[f] - drift[f - 1] > Math.PI) drift[f] -= 2 * Math.PI;
      while (drift[f] - drift[f - 1] < -Math.PI) drift[f] += 2 * Math.PI;
    }
    const W = Math.round(0.5 / clip.dt);
    const heading = drift.map((_, f) => {
      let s = 0, n = 0;
      for (let k = Math.max(0, f - W); k <= Math.min(drift.length - 1, f + W); k++) { s += drift[k]; n++; }
      return s / n;
    });
    const B = rigBones(rig), S = B.scale;
    const shinLen = (i) => rig.bones[i].origin[1];          // ankle at the floor in the rest pose
    const parts = {}, joints = {};
    for (const k of PARTS) parts[k] = [];
    for (const k of JOINT_NAMES) joints[k] = [];
    const put = (name, R, P) => parts[name].push({ q: quat(toModelMat(R)), p: toModelPos(P) });   // rig rotation
    const putM = (name, M, P) => parts[name].push({ q: quat(M), p: toModelPos(P) });              // model rotation
    const dM = (v) => [v[0], -v[1], v[2]];                    // rig direction -> model direction
    const perp = (v, n) => norm(sub(v, scl(n, dot(v, n))));
    const col0 = (m) => [m[0], m[3], m[6]];
    const col2 = (m) => [m[2], m[5], m[8]];
    const frameOf = (X, Y) => { const Z = cross(X, Y); return [X[0], Y[0], Z[0], X[1], Y[1], Z[1], X[2], Y[2], Z[2]]; };
    const arm = { right: {}, left: {} };
    clip.frames.forEach((fr, fi) => {
      const fk = A.rigFK(rig, fr.locals, fr.rootPos);
      if (lockHeading) {
        const Yc = axisAngle([0, 1, 0], -(heading[fi] - heading[0])), c = [fr.rootPos[0], 0, fr.rootPos[2]];
        fk.pos = fk.pos.map((p) => add(c, apply(Yc, sub(p, c))));
        fk.rot = fk.rot.map((R) => mul(Yc, R));
        fr = Object.assign({}, fr, { pelvis: fr.pelvis && mul(Yc, fr.pelvis) });
      }
      const root = scl(fr.rootPos, S);
      const J = {};
      // torso: the chest (rig body) carries the neck, head and arms; the hips follow the pelvis (rig root) through the waist
      // bend, so the waist joint sits WAIST px below the neck and the legs hang from the hips
      const Rb = mul(fk.rot[B.body], axisAngle([1, 0, 0], -st.hunch * D));   // race hunch: lean forward in the chest's frame
      // the hips' turn in the chest's frame, as the waist ops apply it: roll . pitch . yaw (twist scaled by WAIST_TWIST);
      // the pelvis is rebuilt from it so the legs hang from the hips as drawn
      const rel0 = mul(tr(toModelMat(Rb)), toModelMat(fr.pelvis || fk.rot[B.root]));
      J.waist_pitch = Math.asin(clamp1(rel0[7]));
      J.waist_yaw = Math.atan2(-rel0[6], rel0[8]) * WAIST_TWIST;
      J.waist_roll = Math.atan2(-rel0[1], rel0[4]);
      const rel = mul(axisAngle([0, 0, 1], J.waist_roll), mul(axisAngle([1, 0, 0], J.waist_pitch), axisAngle([0, 1, 0], J.waist_yaw)));
      const Rr = toModelMat(mul(toModelMat(Rb), rel));   // model -> rig: the same flip
      const hips = add([0, 12, 0], root);
      const neck = add(add(hips, apply(Rr, [0, 12 - WAIST, 0])), apply(Rb, [0, WAIST, 0]));
      const at = (v) => add(neck, apply(Rb, sub(v, [0, 12, 0])));   // a point of the vanilla torso (neck at y = 12)
      put('body', Rb, neck);
      put('head', fk.rot[B.head], neck);
      // arms: the upper arm aims shoulder -> elbow and the forearm bends at the elbow. Its twist puts the elbow's bend on the
      // part's x axis; with the arm nearly straight that is undefined, so the twist follows the hand (the held item hangs off
      // it) and blends over to the bend between 8 and 30 deg. The twist may change at most MAX_TWIST deg/s, which removes the
      // retarget's one-frame wrist flips. All in model space.
      for (const [side, iA, iF, iH, x] of [['right', B.ra, B.rf, B.rh, -5], ['left', B.la, B.lf, B.lh, 5]]) {
        const up = dM(norm(sub(fk.pos[iF], fk.pos[iA]))), fore = dM(norm(sub(fk.pos[iH], fk.pos[iF])));
        let X = perp(dM(apply(fk.rot[iH], [1, 0, 0])), up);
        const h = cross(fore, up), bent = Math.acos(clamp1(dot(up, fore))) * 180 / Math.PI;
        if (Math.hypot(...h) > 1e-6) {
          const hx = perp(h, up), w = Math.max(0, Math.min(1, (bent - 8) / 22)), ws = w * w * (3 - 2 * w);
          X = norm(apply(axisAngle(up, ws * Math.atan2(dot(cross(X, hx), up), dot(X, hx))), X));
        }
        const st = arm[side];
        if (st.X) {
          const carried = perp(apply(between(st.up, up), st.X), up), cap = MAX_TWIST * Math.PI / 180 * clip.dt;
          const phi = Math.atan2(dot(cross(carried, X), up), dot(carried, X));
          X = norm(apply(axisAngle(up, Math.max(-cap, Math.min(cap, phi))), carried));
        }
        st.X = X; st.up = up;
        const Z = cross(X, up);
        J[side + '_elbow'] = Math.atan2(dot(fore, Z), dot(fore, up));
        let Ma = frameOf(X, up);
        if (st.armsOut) Ma = mul(axisAngle(norm(col2(toModelMat(Rb))), (side === 'right' ? 1 : -1) * st.armsOut * D), Ma);
        putM(side + '_arm', Ma, at([x, 10, 0]));
      }
      // legs hang from the hips (turned with the pelvis): the thigh aims hip -> knee with no twist of its own (its x axis is
      // the pelvis's) and the shin bends at the knee
      const P = toModelMat(Rr), Pt = tr(P);
      for (const [side, iL, iS, x] of [['right', B.rl, B.rs, -1.9], ['left', B.ll, B.ls, 1.9]]) {
        const foot = add(fk.pos[iS], apply(fk.rot[iS], [0, -shinLen(iS), 0]));
        // no swing inward past vertical (in the pelvis frame): vanilla legs are 4 px boxes touching at rest, so any crossing clips
        const local = apply(Pt, dM(norm(sub(fk.pos[iS], fk.pos[iL]))));
        if (st.stride !== 1) { local[0] *= st.stride; local[2] *= st.stride; }   // race stride: less swing
        local[0] = side === 'right' ? Math.min(local[0], 0) : Math.max(local[0], 0);
        const Y = norm(apply(P, local)), shin = dM(norm(sub(foot, fk.pos[iS])));
        const X = perp(col0(P), Y), Z = cross(X, Y);
        J[side + '_knee'] = Math.max(-0.1, Math.atan2(dot(shin, Z), dot(shin, Y)) + st.kneeBend * D);
        let Ml = frameOf(X, Y);
        if (st.kneeBend) Ml = mul(axisAngle(X, -st.kneeBend * D / 2), Ml);   // thigh forward by half the extra bend
        putM(side + '_leg', Ml, add(hips, apply(Rr, [x, 0, 0])));
      }
      for (const k of JOINT_NAMES) joints[k].push(J[k] * JOINT_SHARE);
    });
    // floor contact: shift each frame until the lowest (bent) box corner touches the floor (y = 24)
    const n = parts.head.length;
    for (let f = 0; f < n; f++) {
      let low = -Infinity;
      const J = Object.fromEntries(JOINT_NAMES.map((k) => [k, joints[k][f]]));
      for (const k of PARTS) {
        const { q, p } = parts[k][f], R = quatToMat(q), [a, b] = BOXES[k];
        for (const cx of [a[0], b[0]]) for (const cy of [a[1], b[1]]) for (const cz of [a[2], b[2]])
          low = Math.max(low, p[1] + apply(R, deform(k, J, [cx, cy, cz]))[1]);
      }
      for (const k of PARTS) parts[k][f].p[1] += 24 - low;
    }
    const out = {};
    for (const [k, list] of Object.entries(parts)) {
      // keep quaternions on one hemisphere so the runtime can slerp between neighbours without flipping
      for (let f = 1; f < list.length; f++) if (dot4(list[f].q, list[f - 1].q) < 0) list[f].q = list[f].q.map((v) => -v);
      out[k] = { q: list.flatMap((e) => e.q.map(r4)), p: list.flatMap((e) => e.p.map(r4)) };
    }
    return { parts: out, joints: Object.fromEntries(JOINT_NAMES.map((k) => [k, joints[k].map(r4)])) };
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

  function clipData(A, rig, clip, slot, style) {
    const { parts, joints } = bake(A, rig, clip, style, LOOPS.has(slot)), hold = HOLD[slot] || {};
    for (const [name, [x, y, z]] of Object.entries(hold.parts || {})) {
      const q = eulerQuat(x, y, z), P = parts[name];
      for (let i = 0; i < P.q.length; i += 4) P.q.splice(i, 4, ...q);
    }
    for (const [name, a] of Object.entries(hold.joints || {})) joints[name] = joints[name].map(() => a);
    const data = { dt: +clip.dt.toFixed(6), length: +clip.length.toFixed(4), loop: LOOPS.has(slot), hit: +clip.hitTime.toFixed(4), parts, joints };
    if (slot.startsWith('attack_')) data.hit = hitTime(data, slot.includes('unarmed'));
    if (data.loop) loopSeam(data);
    if (/^(walk|run|march)/.test(slot)) data.cycles = gaitCycles(data);
    return data;
  }

  /** Loops that were not made to loop (mocap takes cut from longer recordings): ease the last 8 frames into the first, so
   *  the wrap does not jump. Seamless loops are unchanged. */
  function loopSeam(data) {
    const n = data.parts.head.q.length / 4, N = Math.min(8, Math.floor(n / 4));
    if (N < 2) return;
    for (const P of Object.values(data.parts)) {
      for (let k = 1; k <= N; k++) {
        const f = n - 1 - N + k, w = k / N;
        let a = P.q.slice(f * 4, f * 4 + 4), b = P.q.slice(0, 4);
        if (dot4(a, b) < 0) b = b.map((v) => -v);
        const q = norm4(a.map((v, i) => v + (b[i] - v) * w));
        for (let i = 0; i < 4; i++) P.q[f * 4 + i] = r4(q[i]);
        for (let i = 0; i < 3; i++) P.p[f * 3 + i] = r4(P.p[f * 3 + i] + (P.p[i] - P.p[f * 3 + i]) * w);
      }
    }
    for (const v of Object.values(data.joints || {}))
      for (let k = 1; k <= N; k++) { const f = n - 1 - N + k, w = k / N; v[f] = r4(v[f] + (v[0] - v[f]) * w); }
  }

  /** How many full gait cycles (two steps) a walk/run clip holds: rising crossings of the right thigh's swing (the game
   *  maps one cycle of leg movement onto 1/cycles of the clip, so feet keep pace with the ground). */
  function gaitCycles(data) {
    const n = data.parts.head.q.length / 4, sw = [];
    for (let f = 0; f < n; f++) { const R = frame(data, f).right_leg.R; sw.push(Math.atan2(-R[7], R[4])); }   // thigh pitch
    const mean = sw.reduce((a, b) => a + b, 0) / n, band = 0.25 * (Math.max(...sw) - Math.min(...sw));
    let up = 0, low = sw[0] < mean - band;   // a cycle: from below mean - band to above mean + band (ignores jitter)
    for (let f = 1; f < n; f++) {
      if (sw[f] < mean - band) low = true;
      else if (low && sw[f] > mean + band) { up++; low = false; }
    }
    return Math.max(1, up);
  }

  /**
   * When an attack connects (s): the frame where the weapon tip (or the fist) moves fastest, either hand, between 15% and
   * 80% of the clip. The server applies the damage at this moment (combat.AttackTiming).
   */
  function hitTime(data, unarmed) {
    const n = data.parts.head.q.length / 4, tip = unarmed ? 0 : -10;
    const at = (f, part, x) => {
      const P = frame(data, f);
      return add(P[part].p, apply(P[part].R, deform(part, P.joints, [x, 10, tip])));
    };
    let best = 0, bestF = Math.round(n * 0.4);
    for (let f = Math.max(1, Math.floor(n * 0.15)); f < Math.ceil(n * 0.8); f++)
      for (const [part, x] of [['right_arm', -1], ['left_arm', 1]]) {
        const v = Math.hypot(...sub(at(f, part, x), at(f - 1, part, x)));
        if (v > best) { best = v; bestF = f; }
      }
    return +(bestF * data.dt).toFixed(4);
  }

  /** [x y z w] for a vanilla ModelPart rotation (applied z, then y, then x: the inverse of NpcAnimator.set). */
  function eulerQuat(x, y, z) {
    const cx = Math.cos(x / 2), sx = Math.sin(x / 2), cy = Math.cos(y / 2), sy = Math.sin(y / 2), cz = Math.cos(z / 2), sz = Math.sin(z / 2);
    return [sx * cy * cz - cx * sy * sz, cx * sy * cz + sx * cy * sz, cx * cy * sz - sx * sy * cz, cx * cy * cz + sx * sy * sz].map((v) => +v.toFixed(6));
  }

  /** Pose of frame f: {part: {q, p, R}, joints: {name: angle}}. */
  function frame(data, f) {
    const out = {};
    for (const k of PARTS) {
      const P = data.parts[k], q = P.q.slice(f * 4, f * 4 + 4);
      out[k] = { q, p: P.p.slice(f * 3, f * 3 + 3), R: quatToMat(q) };
    }
    out.joints = Object.fromEntries(JOINT_NAMES.map((k) => [k, data.joints && data.joints[k] ? data.joints[k][f] : 0]));
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
        const hand = add(P[side].p, apply(P[side].R, deform(side, P.joints, [side === 'right_arm' ? -1 : 1, 9, 0])));
        const l = local(hand);
        if (Math.abs(l[0]) < 3 && l[1] > 1 && l[1] < 11 && Math.abs(l[2]) < 1.5) { checks.arm_in_body.frames.push(f); break; }
      }
      // the hips' frame: the torso turned by the waist joints (roll . pitch . yaw)
      const hipsR = mul(body.R, mul(axisAngle([0, 0, 1], P.joints.waist_roll), mul(axisAngle([1, 0, 0], P.joints.waist_pitch), axisAngle([0, 1, 0], P.joints.waist_yaw))));
      const foot = (k) => add(P[k].p, apply(P[k].R, deform(k, P.joints, [0, 12, 0])));
      const fr = apply(tr(hipsR), foot('right_leg')), fl = apply(tr(hipsR), foot('left_leg'));
      if (fr[0] > fl[0] - 0.5) checks.legs_cross.frames.push(f);
      const yr = foot('right_leg')[1], yl = foot('left_leg')[1];
      if (Math.max(yr, yl) > 24.75) checks.under_floor.frames.push(f);
      if (Math.max(yr, yl) < 21) checks.floating.frames.push(f);
      for (const k of ['right_leg', 'left_leg']) {
        // twist about the leg's own long axis (swing-twist split): what makes a box leg look screwed in
        const R = mul(tr(hipsR), P[k].R), T = mul(tr(between([0, 1, 0], norm(apply(R, [0, 1, 0])))), R);
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

  const api = { RACES, SLOTS, LOOPS, HOLD, ITEM_HOLD, itemHold, WEAPON_LENGTH, weaponGrip, transformPoints, JOINTS, JOINT_NAMES, deform, PARTS, BOXES, RIG_PATH, bake, bakeOptions, resolveEntry, joinBvh, clipData, frame, qa, quatToMat };
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
        const data = clipData(A, rig, A.retarget(bvh, rig, map, bakeOptions(A, LIB, race, entry)), slot, (r.settings || {}).style);
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
