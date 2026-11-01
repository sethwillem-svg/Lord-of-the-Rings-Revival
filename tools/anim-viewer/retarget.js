/* Mocap -> cube rig retargeting. Runs in the browser (window.AnimRetarget) and in Node (module.exports).
 *
 * BVH (Kimodo SOMA, Y up, cm) -> any Blockbench rig described by a rig map (rigs.js).
 * Retargeting is by direction, not position: rig proportions differ a lot from human ones.
 * Rig space: +X = character's left (names-consistent with Blockbench rigs), +Y up, -Z forward, 16 px = 1 block.
 * Rotation convention for rest poses and exported viewer Euler: degrees, matrix = Rz * Ry * Rx.
 * Matrices are row-major arrays of 9 numbers. The Python reference is tools/anim/retarget3.py.
 */
(function (root) {
  'use strict';
  const D2R = Math.PI / 180, EPS = 1e-9;

  // ---------- small linear algebra ----------
  const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
  const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
  const scl = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
  const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const len = (a) => Math.sqrt(dot(a, a));
  const norm = (a) => { const l = len(a); return l > EPS ? scl(a, 1 / l) : [0, 0, 0]; };
  const I3 = () => [1, 0, 0, 0, 1, 0, 0, 0, 1];
  function mul(a, b) {
    const r = new Array(9);
    for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++)
      r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
    return r;
  }
  const tr = (m) => [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]];
  const apply = (m, v) => [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]];
  const fromCols = (x, y, z) => [x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2]];
  const col = (m, i) => [m[i], m[3 + i], m[6 + i]];
  function rotAxis(axis, deg) {
    const t = deg * D2R, c = Math.cos(t), s = Math.sin(t);
    if (axis === 'X') return [1, 0, 0, 0, c, -s, 0, s, c];
    if (axis === 'Y') return [c, 0, s, 0, 1, 0, -s, 0, c];
    return [c, -s, 0, s, c, 0, 0, 0, 1];
  }
  const rx = (rad) => rotAxis('X', rad / D2R);
  /** Euler degrees [x,y,z], matrix = Rz * Ry * Rx (Blockbench / viewer convention). */
  const eulerZYXtoMat = (e) => mul(rotAxis('Z', e[2]), mul(rotAxis('Y', e[1]), rotAxis('X', e[0])));
  function matToEulerZYX(m) {        // inverse of eulerZYXtoMat
    const b = Math.asin(Math.max(-1, Math.min(1, -m[6])));
    return [Math.atan2(m[7], m[8]) / D2R, b / D2R, Math.atan2(m[3], m[0]) / D2R];
  }
  function matToEulerXYZ(m) {        // matrix = Rx * Ry * Rz (JOML rotationXYZ, PulseLib keys)
    const b = Math.asin(Math.max(-1, Math.min(1, m[2])));
    return [Math.atan2(-m[5], m[8]) / D2R, b / D2R, Math.atan2(-m[1], m[0]) / D2R];
  }
  /** Pick the Euler triple (or its equivalent) nearest to prev, unwrapped by 360. */
  function nearestEuler(e, prev) {
    if (!prev) return e;
    const alt = [e[0] + 180, 180 - e[1], e[2] + 180];
    const wrap = (c) => c.map((v, i) => v + 360 * Math.round((prev[i] - v) / 360));
    const a = wrap(e), b = wrap(alt);
    const d = (c) => Math.abs(c[0] - prev[0]) + Math.abs(c[1] - prev[1]) + Math.abs(c[2] - prev[2]);
    return d(a) <= d(b) ? a : b;
  }
  /** Rotation with columns x, y, z = x cross y; y exact, x orthogonalized. Identity when upright. */
  function frameFrom(x, y) {
    y = norm(y);
    x = norm(sub(x, scl(y, dot(x, y))));
    return fromCols(x, y, cross(x, y));
  }
  const smoothstep = (a, b, v) => { const t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); };

  // ---------- BVH ----------
  function parseBVH(text) {
    const names = [], parent = [], offset = [], chan = [];
    const lines = text.split(/\r?\n/);
    const stack = [];
    let cur = -1, i = 0;
    for (; i < lines.length; i++) {
      const t = lines[i].trim().split(/\s+/);
      if (!t[0]) continue;
      if (t[0] === 'ROOT' || t[0] === 'JOINT' || t[0] === 'End') {
        names.push(t[0] === 'End' ? names[stack[stack.length - 1]] + '_End' : t[1]);
        parent.push(stack.length ? stack[stack.length - 1] : -1);
        offset.push([0, 0, 0]); chan.push([]);
        cur = names.length - 1;
      } else if (t[0] === '{') stack.push(cur);
      else if (t[0] === '}') stack.pop();
      else if (t[0] === 'OFFSET') offset[cur] = t.slice(1, 4).map(Number);
      else if (t[0] === 'CHANNELS') chan[cur] = t.slice(2, 2 + Number(t[1]));
      else if (t[0] === 'MOTION') { i++; break; }
    }
    const frames = Number(lines[i].trim().split(/\s+/)[1]);
    const dt = Number(lines[i + 1].trim().split(/\s+/).pop());
    const data = [];
    for (let k = i + 2; k < lines.length && data.length < frames; k++) {
      const s = lines[k].trim();
      if (s) data.push(s.split(/\s+/).map(Number));
    }
    const idx = {};
    names.forEach((n, j) => { idx[n] = j; });
    return { names, parent, offset, chan, data, frames: data.length, dt, idx };
  }

  /** World joint positions P[f][j] (cm). Channel rotations compose in listed order (intrinsic). */
  /** reversed: compose each joint's rotation channels in the opposite order to the header (some exporters do). */
  function bvhFK(b, reversed) {
    const P = [];
    for (let f = 0; f < b.frames; f++) {
      const row = b.data[f], Pw = [], Rw = [];
      let c = 0;
      for (let j = 0; j < b.names.length; j++) {
        let pos = b.offset[j].slice(), rot = I3();
        for (const ch of b.chan[j]) {
          const v = row[c++];
          if (ch.endsWith('position')) pos['XYZ'.indexOf(ch[0])] = v;
          else rot = reversed ? mul(rotAxis(ch[0], v), rot) : mul(rot, rotAxis(ch[0], v));
        }
        const p = b.parent[j];
        if (p < 0) { Pw.push(pos); Rw.push(rot); }
        else { Pw.push(add(Pw[p], apply(Rw[p], pos))); Rw.push(mul(Rw[p], rot)); }
      }
      P.push(Pw);
    }
    return P;
  }

  // ---------- rig (.bbmodel) ----------
  function parseRig(bb) {
    const els = {};
    for (const e of bb.elements || []) els[e.uuid] = e;
    const bones = [];
    function walk(n, p) {
      if (typeof n === 'string') {
        if (p >= 0 && els[n] && els[n].type !== 'locator') bones[p].cubes.push(els[n]);
        return;
      }
      const b = { name: n.name, parent: p, origin: (n.origin || [0, 0, 0]).map(Number),
                  rest: (n.rotation || [0, 0, 0]).map(Number), cubes: [], children: [] };
      bones.push(b);
      const me = bones.length - 1;
      if (p >= 0) bones[p].children.push(me);
      for (const c of n.children || []) walk(c, me);
    }
    for (const n of bb.outliner || []) walk(n, -1);
    const idx = {};
    bones.forEach((b, i) => { idx[b.name] = i; b.restMat = eulerZYXtoMat(b.rest); });
    return { bones, idx, bb };
  }
  function subtree(rig, i) {
    const out = [i];
    for (let k = 0; k < out.length; k++) out.push(...rig.bones[out[k]].children);
    return out;
  }
  function cubeCorners(rig, i) {
    const pts = [];
    for (const k of subtree(rig, i)) for (const c of rig.bones[k].cubes) { pts.push(c.from.map(Number)); pts.push(c.to.map(Number)); }
    return pts;
  }

  /** Rig FK: locals {boneIndex: mat}, rootPos [x,y,z] -> {pos[], rot[]} (rig space, px). */
  function rigFK(rig, locals, rootPos) {
    const pos = [], rot = [];
    rig.bones.forEach((b, i) => {
      const L = locals[i] || b.restMat;
      if (b.parent < 0) { pos.push(add(b.origin, rootPos)); rot.push(L); }
      else {
        const pb = rig.bones[b.parent];
        pos.push(add(pos[b.parent], apply(rot[b.parent], sub(b.origin, pb.origin))));
        rot.push(mul(rot[b.parent], L));
      }
    });
    return { pos, rot };
  }

  // ---------- retarget ----------
  const DEFAULTS = {
    torsoTiltCap: 90,    // max upper-body tilt from vertical, degrees (90 = off)
    rootDrop: 1.0,       // share of the hips' vertical travel applied to the root
    weapon: 'forearm',   // 'forearm': blade continues the forearm; 'rest': keep the rig's rest grip
    mirror: false,       // swap left and right (for left-handed takes)
    footLift: true,      // lift the root so no foot goes below the floor
    footPlant: false,    // set the root height every frame so the lowest foot is as high as the mocap's (fixes takes
                         // that start crouched, where frame-0-relative hips leave the feet floating)
    ik: false,           // weapon IK: pull the weapon tip (or right hand) onto a target at the hit
    ikTargetY: 0,        // target height above the floor, px (0 = ground strike)
    ikReach: 0.85,       // target distance from the shoulder as a share of arm + weapon length
    weaponPitch: 0,      // extra weapon rotation in the hand, degrees: tilt the blade forward/back
    weaponYaw: 0,        //   swing it left/right
    weaponRoll: 0,       //   twist it about the grip
    rotOrder: 'auto',    // BVH rotation channel order: 'auto', 'listed' (as the header says) or 'reversed'
    smooth: 0,           // Gaussian smoothing of the retargeted pose over time, sigma in frames (0 = off)
    inPlace: false,      // drop horizontal root travel (attacks played on the spot; the game moves the entity)
    jointLimits: false,  // cube-rig limits: cap elbow/knee bend at maxBend, keep upper arms out of the torso box,
                         // keep the feet at least 3/4 of the hip spacing apart (boxy legs clip when they close in)
    maxBend: 140,        // degrees
    ikTorsoShare: 0,     // share of the weapon IK correction taken by the upper body leaning/twisting (0..0.8)
    footLock: false,     // pin each foot while the mocap foot is planted (removes foot skating)
    stance: 1,           // leg spacing multiplier (1 = as in the mocap)
    wrist: 'rest',       // 'rest' (hands keep their rest angle) or 'auto' (hand follows the mocap wrist when the BVH
                         // has finger joints, within +-70 degrees)
    edgeFirst: false,    // twist the weapon about its blade so the edge, not the flat, leads the swing
  };

  /** Rotation of ang radians about unit axis (Rodrigues). */
  function axisAngle(a, ang) {
    const c = Math.cos(ang), s = Math.sin(ang), t = 1 - c, [x, y, z] = a;
    return [t * x * x + c, t * x * y - s * z, t * x * z + s * y,
            t * x * y + s * z, t * y * y + c, t * y * z - s * x,
            t * x * z - s * y, t * y * z + s * x, t * z * z + c];
  }
  /**
   * Limit a local rotation by swing-twist about its bone's Y axis: twist (turning about the bone) up to maxTwist,
   * swing (bending away from it) up to maxSwing. Used for wrists, where forearm roll lands on the hand.
   */
  function limitSwingTwist(M, maxSwing, maxTwist) {
    let [x, y, z, w] = matToQuat(M);
    const tn = Math.hypot(y, w) || 1;
    let ty = y / tn, tw = w / tn;                                   // twist about Y
    if (tw < 0) { ty = -ty; tw = -tw; }
    const twistAng = 2 * Math.atan2(ty, tw);
    const tq = [0, ty, 0, tw];
    const qmul = (a, b) => [a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1], a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3], a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]];
    const swing = qmul([x, y, z, w], [0, -ty, 0, tw]);              // q = swing * twist
    const swingM = capRotation(quatToMat(swing), maxSwing);
    const t = Math.max(-maxTwist, Math.min(maxTwist, twistAng));
    return mul(swingM, axisAngle([0, 1, 0], t));
  }
  /** Same rotation as M, scaled to at most maxAng radians. */
  function capRotation(M, maxAng) {
    const ang = Math.acos(Math.max(-1, Math.min(1, (M[0] + M[4] + M[8] - 1) / 2)));
    if (ang <= maxAng || ang < 1e-9) return M;
    const ax = norm([M[7] - M[5], M[2] - M[6], M[3] - M[1]]);
    return len(ax) > EPS ? axisAngle(ax, maxAng) : M;
  }

  /**
   * Two-bone IK on a hinge chain (upper bone iU, hinge bone iL whose local rotation is Rx(theta)).
   * effector(fk) gives the end point; G is the goal. The hinge sets the root-to-effector distance (|theta| <= maxBend),
   * then the chain is swung about the upper bone's pivot to aim at G, keeping the current bend plane.
   */
  function solveTwoBone(rig, fr, iU, iL, effector, G, maxBend) {
    const fk = rigFK(rig, fr.locals, fr.rootPos);
    const S = fk.pos[iU], E = fk.pos[iL], Ru = fk.rot[iU], Rl = fk.rot[iL];
    const pu = rig.bones[iU].parent, Rp = pu < 0 ? I3() : fk.rot[pu];
    const ul = apply(tr(Ru), sub(E, S)), vl = apply(tr(Rl), sub(effector(fk), E));
    const Ll = mul(tr(Ru), Rl), th0 = Math.atan2(Ll[7], Ll[8]);
    const d = Math.min(len(sub(G, S)), 0.99 * (len(ul) + len(vl)));
    const lim = maxBend === undefined ? Math.PI : maxBend;
    const cost = (th) => (Math.abs(th) > lim + 1e-9 ? Infinity : 0) + (len(add(ul, apply(rx(th), vl))) - d) ** 2 + 1e-3 * wrapPi(th - th0) ** 2;
    let th = Math.max(-lim, Math.min(lim, th0)), c0 = cost(th);
    for (let k = -360; k < 360; k++) { const t = k * 0.5 * D2R, c = cost(t); if (c < c0) { c0 = c; th = t; } }
    for (let k = -25; k <= 25; k++) { const t = th + k * 0.02 * D2R, c = cost(t); if (c < c0) { c0 = c; th = t; } }
    const aim = arc(norm(apply(Ru, add(ul, apply(rx(th), vl)))), norm(sub(G, S)));
    fr.locals[iU] = mul(tr(Rp), mul(aim, Ru));
    fr.locals[iL] = rx(th);
  }

  function matToQuat(m) {
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
  /** Gaussian-smooth every bone's local rotation (sign-aligned quaternion average) and the root position over time. */
  function smoothFrames(frames, nBones, sigma) {
    const F = frames.length, r = Math.ceil(3 * sigma), wts = [];
    for (let k = -r; k <= r; k++) wts.push(Math.exp(-0.5 * (k / sigma) ** 2));
    for (let i = 0; i < nBones; i++) {
      const q = frames.map((fr) => matToQuat(fr.locals[i]));
      for (let f = 1; f < F; f++) if (q[f].reduce((a, v, k) => a + v * q[f - 1][k], 0) < 0) q[f] = q[f].map((v) => -v);
      const out = q.map((_, f) => {
        const acc = [0, 0, 0, 0];
        for (let k = -r; k <= r; k++) { const g = q[Math.min(F - 1, Math.max(0, f + k))], w = wts[k + r]; for (let j = 0; j < 4; j++) acc[j] += g[j] * w; }
        const n = Math.hypot(...acc);
        return acc.map((v) => v / n);
      });
      frames.forEach((fr, f) => { fr.locals[i] = quatToMat(out[f]); });
    }
    const pos = frames.map((fr) => fr.rootPos.slice());
    frames.forEach((fr, f) => {
      let sw = 0; const acc = [0, 0, 0];
      for (let k = -r; k <= r; k++) { const g = pos[Math.min(F - 1, Math.max(0, f + k))], w = wts[k + r]; sw += w; for (let j = 0; j < 3; j++) acc[j] += g[j] * w; }
      fr.rootPos = acc.map((v) => v / sw);
    });
  }

  /** Shortest-arc rotation taking unit vector a to unit vector b. */
  function arc(a, b) {
    const v = cross(a, b), c = dot(a, b);
    if (c < -0.9999) {
      const p = norm(Math.abs(a[0]) < 0.9 ? cross(a, [1, 0, 0]) : cross(a, [0, 1, 0]));
      return [2 * p[0] * p[0] - 1, 2 * p[0] * p[1], 2 * p[0] * p[2], 2 * p[1] * p[0], 2 * p[1] * p[1] - 1, 2 * p[1] * p[2],
              2 * p[2] * p[0], 2 * p[2] * p[1], 2 * p[2] * p[2] - 1];
    }
    const K = [0, -v[2], v[1], v[2], 0, -v[0], -v[1], v[0], 0], K2 = mul(K, K), k = 1 / (1 + c);
    return I3().map((x, i) => x + K[i] + K2[i] * k);
  }
  const minJerk = (s) => s * s * s * (10 - 15 * s + 6 * s * s);
  const wrapPi = (a) => Math.atan2(Math.sin(a), Math.cos(a));

  /**
   * Weapon IK (goal warp, Witkin and Popovic 1995). The effector (weapon tip, else hand) keeps its own path but is
   * offset by w(t) * (target - effector at hit). w rises with a minimum-jerk curve from 0 at the windup extreme A to 1
   * at the hit H, holds 1 while the mocap stays down (contact hold, to C), then eases back to 0 as the arms rise (R).
   * Goals never go below the floor. Each frame is solved at full weight: the elbow hinge sets the shoulder-to-effector
   * distance, then the whole arm is swung about the shoulder to aim at the goal (keeps the mocap's elbow plane).
   * Only the arm's upper and lower bones change. Returns the markers and target.
   */
  function weaponIK(rig, frames, arm, effector, H, opts, iUpper) {
    const F = frames.length, iU = arm[0], iL = arm[1];
    if (H < 3) return null;   // hit at the very start: no windup to warp from, leave the clip as it is
    const trace = frames.map((fr) => effector(rigFK(rig, fr.locals, fr.rootPos)));
    let A = 0;
    for (let f = 0; f <= H; f++) if (trace[f][1] > trace[A][1]) A = f;
    if (H - A < 3) A = Math.max(0, H - 10);
    const span = Math.max(1, trace[A][1] - trace[H][1]);
    let C = H;
    while (C + 1 < F && trace[C + 1][1] <= trace[H][1] + 0.1 * span) C++;
    let R = C;
    while (R + 1 < F && trace[R + 1][1] < trace[H][1] + 0.6 * span) R++;
    if (C < F - 1) R = Math.min(F - 1, Math.max(R + 1, C + 3));
    // target on the plane y = ikTargetY, in front of the character and in the swing's horizontal direction from the
    // shoulder, at ikReach of the arm-plus-weapon length (reachable, not folded under the shoulder)
    const fkH = rigFK(rig, frames[H].locals, frames[H].rootPos), SH = fkH.pos[iU];
    const reach = len(sub(fkH.pos[iL], SH)) + len(sub(trace[H], fkH.pos[iL]));
    let dirH = [trace[H][0] - SH[0], 0, trace[H][2] - SH[2]];
    // rig space faces -Z; strikes land in the front half
    if (len(dirH) < 0.3 * reach || -norm(dirH)[2] < 0.25) dirH = [0, 0, -1];
    dirH = norm(dirH);
    const r = opts.ikReach * reach;
    const ty = Math.max(opts.ikTargetY, SH[1] - 0.98 * r);   // out of reach: the lowest point the chain can touch
    const h = SH[1] - ty, horiz = Math.sqrt(Math.max(0, r * r - h * h));
    const target = [SH[0] + dirH[0] * horiz, ty, SH[2] + dirH[2] * horiz];
    const off = sub(target, trace[H]);
    for (let f = A; f <= R; f++) {
      const w = f <= H ? minJerk((f - A) / Math.max(1, H - A))
        : f <= C ? 1 : 1 - minJerk((f - C) / Math.max(1, R - C));
      if (w <= 0) continue;
      const fr = frames[f], G = add(trace[f], scl(off, w));
      G[1] = Math.max(G[1], Math.min(0, opts.ikTargetY));
      if (opts.ikTorsoShare > 0 && iUpper !== undefined) {
        // The upper body absorbs part of the change in reach, so the arm keeps more of its mocap extension: turn the
        // torso about its pivot (lean and twist together) by the angle that would bring the shoulder to the arm's
        // current shoulder-to-effector distance from the goal, scaled by the share and capped at 35 degrees.
        const fk = rigFK(rig, fr.locals, fr.rootPos), pv = fk.pos[iUpper], S0 = fk.pos[iU];
        const rc = len(sub(trace[f], S0)), ax = cross(sub(S0, pv), sub(G, pv));
        if (len(ax) > 1e-6 && Math.abs(len(sub(G, S0)) - rc) > 0.05) {
          const n = norm(ax);
          let best = 0, err = Infinity;
          for (let k = -70; k <= 70; k++) {
            const t = k * 0.5 * D2R, Sk = add(pv, apply(axisAngle(n, t), sub(S0, pv)));
            const e = Math.abs(len(sub(G, Sk)) - rc);
            if (e < err) { err = e; best = t; }
          }
          const ang = Math.max(-35 * D2R, Math.min(35 * D2R, opts.ikTorsoShare * best));
          const pu = rig.bones[iUpper].parent, Rp = pu < 0 ? I3() : fk.rot[pu];
          fr.locals[iUpper] = mul(tr(Rp), mul(axisAngle(n, ang), fk.rot[iUpper]));
        }
      }
      solveTwoBone(rig, fr, iU, iL, effector, G, opts.jointLimits ? opts.maxBend * D2R : undefined);
    }
    return { A, H, C, R, target, raised: ty > opts.ikTargetY + 1e-6 ? ty - opts.ikTargetY : 0 };
  }

  /** Hinge limb in world space. du, dl: unit upper/lower directions; def: default hinge axis; state.last carries continuity. */
  function hingeLimb(du, dl, def, state, maxBend) {
    const perp = sub(dl, scl(du, dot(dl, du)));
    const plen = len(perp);
    let g = plen > 1e-6 ? cross(du, norm(perp)) : null;
    let d = norm(sub(def, scl(du, dot(def, du))));
    const last = state.last || def;
    if (!g) g = d;
    if (dot(g, last) < 0) g = scl(g, -1);
    if (dot(d, last) < 0) d = scl(d, -1);
    const w = smoothstep(0.08, 0.30, plen);
    let X = add(scl(g, w), scl(d, 1 - w));
    X = len(X) > EPS ? norm(X) : last;
    state.last = X;
    const Y = scl(du, -1), Z = cross(X, Y);
    const Rup = fromCols(X, Y, Z);
    const l = apply(tr(Rup), dl);
    let theta = Math.atan2(-l[2], -l[1]);
    if (maxBend !== undefined) theta = Math.sign(theta) * Math.min(Math.abs(theta), maxBend);
    return { upper: Rup, lower: mul(Rup, rx(theta)) };
  }

  /** Clamp a frame's tilt (angle of its Y column from vertical) to capDeg, keeping heading. */
  function capTilt(M, capDeg) {
    if (capDeg >= 90) return M;
    const y = col(M, 1), ang = Math.acos(Math.max(-1, Math.min(1, y[1]))) / D2R;
    if (ang <= capDeg) return M;
    const h = norm([y[0], 0, y[2]]), c = Math.cos(capDeg * D2R), s = Math.sin(capDeg * D2R);
    const ny = len(h) > EPS ? [h[0] * s, c, h[2] * s] : [0, 1, 0];
    return frameFrom(col(M, 0), ny);
  }

  /**
   * Retarget one BVH onto a rig.
   * map: rig map from rigs.js. opts: see DEFAULTS. Returns a clip object (see README).
   */
  function retarget(bvh, rig, map, opts) {
    opts = Object.assign({}, DEFAULTS, opts || {});
    // Source skeletons: Kimodo SOMA names, or Mixamo (prefix like "mixamorig:" ignored), where the thigh is
    // LeftUpLeg and LeftLeg is the shin. Joints are looked up by SOMA role names below.
    const byName = {};
    bvh.names.forEach((n, i) => { const k = n.replace(/^[^:]*:/, ''); if (!(k in byName)) byName[k] = i; });
    // CMU (cgspeed BVH conversion) uses the Mixamo limb names with LowerBack/Spine/Spine1, Neck/Neck1 and LHipJoint.
    const MIXAMO_LEGS = { LeftLeg: 'LeftUpLeg', RightLeg: 'RightUpLeg', LeftShin: 'LeftLeg', RightShin: 'RightLeg' };
    const ALIAS = 'LHipJoint' in byName && !('Spine2' in byName) ? Object.assign({}, MIXAMO_LEGS, { HeadEnd: 'Head_End', Spine2: 'Spine1' })
      : 'LeftUpLeg' in byName ? Object.assign({}, MIXAMO_LEGS, { HeadEnd: 'HeadTop_End' in byName ? 'HeadTop_End' : 'Head_End', Neck1: 'Neck' })
      : {};
    const has = (n) => (ALIAS[n] || n) in byName;
    // Rotation order: as the header says, reversed, or auto (pick the reading where the body is plausible:
    // no foot above the hips, lowest foot near the floor). Some BVH exporters write the angles in reverse order.
    const roleIdx = (n) => byName[ALIAS[n] || n];
    const implausible = (Pk) => {
      const feet = ['LeftFoot', 'RightFoot'].map(roleIdx), hips = roleIdx('Hips');
      if (feet.some((x) => x === undefined) || hips === undefined) return 0;
      let floor0 = Infinity;
      for (const fr of Pk) for (const x of feet) floor0 = Math.min(floor0, fr[x][1]);
      const legLen = Math.max(1e-6, Pk[0][hips][1] - floor0);
      let score = 0;
      for (const fr of Pk) {
        const lo = Math.min(fr[feet[0]][1], fr[feet[1]][1]), hi = Math.max(fr[feet[0]][1], fr[feet[1]][1]);
        score += (lo - floor0) / legLen + (hi > fr[hips][1] ? 5 : 0);
      }
      return score / Pk.length;
    };
    let P, rotOrder = opts.rotOrder || 'auto';
    if (rotOrder === 'auto') {
      const a = bvhFK(bvh, false), r = bvhFK(bvh, true);
      const sa = implausible(a), sr = implausible(r);
      if (sr < sa * 0.5) { P = r; rotOrder = 'reversed (auto)'; } else { P = a; rotOrder = 'as written (auto)'; }
    } else P = bvhFK(bvh, rotOrder === 'reversed');
    const F = bvh.frames, J = (n) => {
      if (opts.mirror) n = n.replace(/^Left/, '#').replace(/^Right/, 'Left').replace(/^#/, 'Right');
      const j = byName[ALIAS[n] || n];
      if (j === undefined) throw new Error('BVH has no joint ' + n + (ALIAS[n] ? ` (${ALIAS[n]})` : ''));
      return j;
    };
    const g = (f, n) => P[f][J(n)];
    const B = (n) => { const i = rig.idx[n]; if (i === undefined) throw new Error('rig has no bone ' + n); return i; };

    // heading basis from frame 0
    const l0 = sub(g(0, 'LeftArm'), g(0, 'RightArm')); l0[1] = 0;
    const L0 = norm(l0), F0 = cross(L0, [0, 1, 0]);
    let o = (v) => [dot(v, L0), v[1], -dot(v, F0)];
    if (opts.mirror) { const o0 = o; o = (v) => { const r = o0(v); return [-r[0], r[1], r[2]]; }; }
    let floor = Infinity;
    const feet = ['LeftFoot', 'RightFoot', 'LeftToeBase', 'RightToeBase'].filter(has);   // toes are optional (Mixamo exports may lack them)
    for (let f = 0; f < F; f++) for (const n of feet) floor = Math.min(floor, g(f, n)[1]);
    const hipH = g(0, 'Hips')[1] - floor;
    const legRoot = rig.bones[B(map.legs.right[0])];
    const s = legRoot.origin[1] / hipH;

    const iRoot = B(map.root), iUpper = B(map.upper), iHead = map.head ? B(map.head) : -1;
    const iPelvis = map.pelvis ? B(map.pelvis) : -1;
    const limbs = [];
    for (const side of ['right', 'left']) {
      const S = side === 'right' ? 'Right' : 'Left';
      limbs.push({ kind: 'arm', side, bones: map.arms[side].map(B), j: [S + 'Arm', S + 'ForeArm', S + 'Hand'], state: {} });
      limbs.push({ kind: 'leg', side, bones: map.legs[side].map(B), j: [S + 'Leg', S + 'Shin', S + 'Foot'], state: {} });
    }
    const wName = [].concat(map.weapon || []).find((n) => rig.idx[n] !== undefined);   // first weapon bone the rig has
    const iWeapon = wName ? B(wName) : -1;
    let weaponLocal = null;
    if (iWeapon >= 0) {
      const base = opts.weapon === 'forearm' ? rotAxis('X', 180) : rig.bones[iWeapon].restMat;
      const adj = opts.weaponPitch || opts.weaponYaw || opts.weaponRoll;
      weaponLocal = adj ? mul(eulerZYXtoMat([opts.weaponPitch, opts.weaponYaw, opts.weaponRoll]), base) : base;
    }

    // lowest cube point below each lower-leg pivot; weapon tip (farthest cube corner from the pivot)
    const footDrop = limbs.filter((l) => l.kind === 'leg').map((l) => {
      const i = l.bones[1], ys = cubeCorners(rig, i).map((p) => p[1]);
      return { i, d: (ys.length ? Math.min(...ys) : rig.bones[i].origin[1]) - rig.bones[i].origin[1] };
    });
    let tipLocal = null;
    if (iWeapon >= 0) {
      const o0 = rig.bones[iWeapon].origin, pts = cubeCorners(rig, iWeapon);
      if (pts.length) tipLocal = sub(pts.reduce((a, p) => (len(sub(p, o0)) > len(sub(a, o0)) ? p : a)), o0);
    }

    const hasEyes = has('LeftEye') && has('RightEye'), hasHeadEnd = has('HeadEnd');
    const bendCap = opts.jointLimits ? opts.maxBend * D2R : undefined;
    const hasFingers = ['Left', 'Right'].every((S) => has(S + 'HandMiddle1') && has(S + 'HandIndex1') && has(S + 'HandPinky1'));
    const wristMocap = opts.wrist === 'auto' && hasFingers;
    const knuckle = (fing) => (has('LeftHand' + fing + '2') ? 'Hand' + fing + '2' : 'Hand' + fing + '1');
    const wristRel = { right: [], left: [] };
    const frames = [];
    const hips0 = g(0, 'Hips');
    for (let f = 0; f < F; f++) {
      const W = {};            // desired world rotations by bone index
      let upper = frameFrom(o(sub(g(f, 'LeftArm'), g(f, 'RightArm'))),
        o(sub(scl(add(g(f, 'LeftArm'), g(f, 'RightArm')), 0.5), g(f, 'Hips'))));
      upper = capTilt(upper, opts.torsoTiltCap);
      W[iUpper] = upper;
      if (iPelvis >= 0) W[iPelvis] = frameFrom(o(sub(g(f, 'LeftLeg'), g(f, 'RightLeg'))), o(sub(g(f, 'Spine2'), g(f, 'Hips'))));
      if (iHead >= 0) {   // eyes give the head's side axis; without them (Mixamo) use the shoulder line
        const side = hasEyes ? sub(g(f, 'LeftEye'), g(f, 'RightEye')) : sub(g(f, 'LeftArm'), g(f, 'RightArm'));
        const up = hasHeadEnd ? sub(g(f, 'HeadEnd'), g(f, 'Head')) : sub(g(f, 'Head'), g(f, 'Neck1'));
        W[iHead] = frameFrom(o(side), o(up));
      }
      for (const l of limbs) {
        const a = g(f, l.j[0]), b = g(f, l.j[1]), c = g(f, l.j[2]);
        const def = l.kind === 'arm' ? col(upper, 0) : (iPelvis >= 0 ? col(W[iPelvis], 0) : [1, 0, 0]);
        let du = norm(o(sub(b, a)));
        if (opts.jointLimits && l.kind === 'arm') {   // keep the upper arm out of the torso box: limit how far it
          const dl = apply(tr(upper), du), side = l.side === 'right' ? 1 : -1;   // points across the body (more when reaching forward)
          const allowed = 0.25 + 0.55 * Math.max(0, Math.min(1, -dl[2])), m = dl[0] * side;
          if (m > allowed) { dl[0] -= (m - allowed) * side; du = apply(upper, norm(dl)); }
        }
        const h = hingeLimb(du, norm(o(sub(c, b))), def, l.state, bendCap);
        W[l.bones[0]] = h.upper; W[l.bones[1]] = h.lower;
        if (l.bones[2] !== undefined) {
          W[l.bones[2]] = h.lower;
          if (l.kind === 'arm' && wristMocap) {   // hand frame from the mocap fingers, within 70 degrees of the forearm
            const S = l.side === 'right' ? 'Right' : 'Left';
            const y = norm(o(sub(g(f, S + 'Hand'), g(f, S + knuckle('Middle')))));   // knuckles: steadier than the base joints
            const sideV = o(sub(g(f, S + knuckle('Index')), g(f, S + knuckle('Pinky'))));
            wristRel[l.side].push(mul(tr(h.lower), frameFrom(cross(sideV, y), y)));   // hand relative to forearm
          }
        }
      }
      // world -> local through the rig hierarchy (parents first: outliner order)
      const locals = {}, worldRot = [];
      rig.bones.forEach((bn, i) => {
        const pw = bn.parent < 0 ? I3() : worldRot[bn.parent];
        let L;
        if (i === iRoot) L = I3();
        else if (W[i]) L = mul(tr(pw), W[i]);
        else if (i === iWeapon && weaponLocal) L = weaponLocal;
        else L = bn.restMat;
        locals[i] = L;
        worldRot.push(mul(pw, L));
      });
      const hp = scl(o(sub(g(f, 'Hips'), hips0)), s);
      const rootPos = opts.inPlace ? [0, hp[1] * opts.rootDrop, 0] : [hp[0], hp[1] * opts.rootDrop, hp[2]];
      frames.push({ locals, rootPos });
    }

    // wrists: the first frame's hand orientation is neutral (skeletons disagree on hand axes), later frames follow the
    // mocap's change from it, limited to 60 degrees of bend and 90 of roll about the forearm
    if (wristMocap) {
      for (const l of limbs.filter((x) => x.kind === 'arm' && x.bones[2] !== undefined)) {
        const rel = wristRel[l.side], n0 = tr(rel[0]);
        frames.forEach((fr, f) => { fr.locals[l.bones[2]] = limitSwingTwist(mul(rel[f], n0), 60 * D2R, 90 * D2R); });
      }
    }

    // stance: scale leg spacing, and with joint limits keep each foot at least 3/4 of the hip offset out from the
    // middle (box legs clip as soon as they close in). Each thigh turns about its parent's forward axis.
    const legs = limbs.filter((l) => l.kind === 'leg');
    const footPoint = (fk, l) => { const d = footDrop.find((x) => x.i === l.bones[1]).d; return add(fk.pos[l.bones[1]], apply(fk.rot[l.bones[1]], [0, d, 0])); };
    if (opts.stance !== 1 || opts.jointLimits) {
      for (const fr of frames) {
        const fk = rigFK(rig, fr.locals, fr.rootPos);
        const mid = scl(add(fk.pos[legs[0].bones[0]], fk.pos[legs[1].bones[0]]), 0.5);
        for (const l of legs) {
          const iT = l.bones[0], pt = rig.bones[iT].parent, Rp = pt < 0 ? I3() : fk.rot[pt], sign = l.side === 'right' ? -1 : 1;
          const q = apply(tr(Rp), sub(footPoint(fk, l), mid)), hp = apply(tr(Rp), sub(fk.pos[iT], mid));
          const outward = sign * q[0], hipX = Math.abs(hp[0]);
          let want = opts.stance * outward;
          if (opts.jointLimits) want = Math.max(want, 0.75 * hipX);
          const vy = Math.max(1, hp[1] - q[1]);
          const a = Math.atan2(want - outward, vy) - Math.atan2(0, vy);
          if (Math.abs(a) > 1e-6) fr.locals[iT] = mul(rotAxis('Z', sign * a / D2R), fr.locals[iT]);
        }
      }
    }

    if (opts.smooth > 0) smoothFrames(frames, rig.bones.length, opts.smooth);

    // root: lift so no foot goes below the floor
    const footY = (fk) => Math.min(...footDrop.map((d) => fk.pos[d.i][1] + d.d));
    let lift = 0;
    if (opts.footPlant) {
      frames.forEach((fr, f) => {
        const src = Math.min(...feet.map((n) => g(f, n)[1])) - floor;   // mocap's lowest foot above its floor
        const delta = src * s - footY(rigFK(rig, fr.locals, fr.rootPos));
        fr.rootPos[1] += delta;
        lift = Math.max(lift, Math.abs(delta));
      });
    } else if (opts.footLift) {
      for (const fr of frames) {
        const y = footY(rigFK(rig, fr.locals, fr.rootPos));
        if (y < 0) { fr.rootPos[1] -= y; lift = Math.max(lift, -y); }
      }
    }
    // foot lock: while a mocap foot is planted (near its floor and barely moving), pin the rig foot where it landed
    const locks = [];
    if (opts.footLock) {
      for (const l of legs) {
        const S = l.side === 'right' ? 'Right' : 'Left';
        const ank = (f) => g(Math.max(0, Math.min(F - 1, f)), S + 'Foot');
        const planted = [];
        for (let f = 0; f < F; f++) {
          const v = len(sub([ank(f + 1)[0], 0, ank(f + 1)[2]], [ank(f - 1)[0], 0, ank(f - 1)[2]])) / (2 * bvh.dt);
          planted.push(ank(f)[1] - floor < 0.12 * hipH && v < 0.8 * hipH);
        }
        for (let f = 0; f < F;) {
          if (!planted[f]) { f++; continue; }
          let e = f; while (e + 1 < F && planted[e + 1]) e++;
          if (e - f >= 2) locks.push({ l, a: f, b: e });
          f = e + 1;
        }
      }
      for (const k of locks) {
        const lockAt = footPoint(rigFK(rig, frames[k.a].locals, frames[k.a].rootPos), k.l);
        const eff = (fk) => footPoint(fk, k.l);
        const lo = Math.max(0, k.a - 2), hi = Math.min(F - 1, k.b + 2), orig = [];
        for (let f = lo; f <= hi; f++) orig.push([frames[f].locals[k.l.bones[0]], frames[f].locals[k.l.bones[1]]]);
        const pin = () => {
          for (let f = lo; f <= hi; f++) {
            frames[f].locals[k.l.bones[0]] = orig[f - lo][0]; frames[f].locals[k.l.bones[1]] = orig[f - lo][1];
            const w = f < k.a ? (f - k.a + 3) / 3 : f > k.b ? (k.b + 3 - f) / 3 : 1;
            const cur = eff(rigFK(rig, frames[f].locals, frames[f].rootPos));
            solveTwoBone(rig, frames[f], k.l.bones[0], k.l.bones[1], eff, add(cur, scl(sub(lockAt, cur), w)), bendCap);
          }
        };
        pin();
        // the pin holds the sole's centre; if a tilted box boot dips a corner below the floor, raise the pin and redo
        const d = footDrop.find((x) => x.i === k.l.bones[1]).d;
        let dip = 0;
        for (let f = k.a; f <= k.b; f++) {
          const fk = rigFK(rig, frames[f].locals, frames[f].rootPos);
          dip = Math.min(dip, fk.pos[k.l.bones[1]][1] + d);
        }
        if (dip < 0) { lockAt[1] -= dip; pin(); }
      }
    }

    // hit: frame where the mocap hands are lowest
    let hitF = 0, best = Infinity;
    for (let f = 0; f < F; f++) {
      const hs = g(f, 'LeftHand')[1] + g(f, 'RightHand')[1];
      if (hs < best) { best = hs; hitF = f; }
    }

    const arm = limbs[0].bones, hand = arm[2] !== undefined ? arm[2] : arm[1];
    const effector = (fk) => (tipLocal ? add(fk.pos[iWeapon], apply(fk.rot[iWeapon], tipLocal)) : fk.pos[hand]);
    const ik = opts.ik ? weaponIK(rig, frames, arm, effector, hitF, opts, iUpper) : null;

    // edge first: twist the weapon about its blade so the blade plane contains the tip's motion (edge leads).
    // The blade normal is the thinnest axis of the weapon's cubes; the twist is speed-weighted and smoothed.
    if (opts.edgeFirst && tipLocal) {
      const wo = rig.bones[iWeapon].origin, pts = cubeCorners(rig, iWeapon).map((p) => sub(p, wo));
      const ext = [0, 1, 2].map((k) => Math.max(...pts.map((p) => p[k])) - Math.min(...pts.map((p) => p[k])));
      const nLocal = [0, 0, 0]; nLocal[ext.indexOf(Math.min(...ext))] = 1;
      const axisL = norm(tipLocal);
      const nPerp = norm(sub(nLocal, scl(axisL, dot(nLocal, axisL))));
      const tips = frames.map((fr) => effector(rigFK(rig, fr.locals, fr.rootPos)));
      const vmax = Math.max(1e-6, ...tips.map((t, f) => len(sub(tips[Math.min(F - 1, f + 1)], tips[Math.max(0, f - 1)]))));
      const tw = frames.map((fr, f) => {
        const fk = rigFK(rig, fr.locals, fr.rootPos), Rw = fk.rot[iWeapon];
        const u = norm(apply(Rw, axisL)), n = apply(Rw, nPerp);
        const v = sub(tips[Math.min(F - 1, f + 1)], tips[Math.max(0, f - 1)]);
        const vp = sub(v, scl(u, dot(v, u)));
        if (len(vp) < 1e-6) return 0;
        let want = norm(cross(u, vp));                 // the normal that puts the motion in the blade plane
        if (dot(want, n) < 0) want = scl(want, -1);     // nearest of the two, so it never flips more than 90 degrees
        const ang = Math.atan2(dot(cross(n, want), u), dot(n, want));
        return ang * smoothstep(0.15, 0.5, len(v) / vmax);
      });
      const sm = tw.map((_, f) => { let a = 0, sw = 0; for (let k = -3; k <= 3; k++) { const w = Math.exp(-0.5 * (k / 1.5) ** 2); a += w * tw[Math.max(0, Math.min(F - 1, f + k))]; sw += w; } return a / sw; });
      frames.forEach((fr, f) => { if (Math.abs(sm[f]) > 1e-6) fr.locals[iWeapon] = mul(fr.locals[iWeapon], axisAngle(axisL, sm[f])); });
    }

    // QA traces
    const qa = { footMin: Infinity, handMin: Infinity, tipMin: Infinity, tipMax: -Infinity, lift, maxBend: 0, minFootGap: Infinity,
                 footSlide: 0, wrist: wristMocap ? 'mocap' : 'rest' };
    for (const l of limbs) for (const fr of frames) {
      const L = fr.locals[l.bones[1]]; qa.maxBend = Math.max(qa.maxBend, Math.abs(Math.atan2(L[7], L[8])) / D2R);
    }
    for (const k of locks) {
      const p0 = footPoint(rigFK(rig, frames[k.a].locals, frames[k.a].rootPos), k.l);
      for (let f = k.a; f <= k.b; f++) qa.footSlide = Math.max(qa.footSlide, len(sub(footPoint(rigFK(rig, frames[f].locals, frames[f].rootPos), k.l), p0)));
    }
    for (const fr of frames) {
      const fk = rigFK(rig, fr.locals, fr.rootPos);
      qa.minFootGap = Math.min(qa.minFootGap, Math.abs(footPoint(fk, legs[0])[0] - footPoint(fk, legs[1])[0]));
      qa.footMin = Math.min(qa.footMin, footY(fk));
      qa.handMin = Math.min(qa.handMin, fk.pos[hand][1]);
      if (tipLocal) {
        fr.tip = effector(fk);
        qa.tipMin = Math.min(qa.tipMin, fr.tip[1]); qa.tipMax = Math.max(qa.tipMax, fr.tip[1]);
      }
    }
    if (!tipLocal) { qa.tipMin = null; qa.tipMax = null; }
    if (ik) {
      const fk = rigFK(rig, frames[hitF].locals, frames[hitF].rootPos);
      ik.errorAtHit = len(sub(effector(fk), ik.target));
    }
    return { frames, dt: bvh.dt, length: (F - 1) * bvh.dt, hitFrame: hitF, hitTime: hitF * bvh.dt, scale: s, qa, rotOrder, locks: locks.map((k) => ({ side: k.l.side, a: k.a, b: k.b })),
             rootBone: iRoot, ik };
  }

  // ---------- export ----------
  /** Viewer clip (Orc Muster Viewer anim-lib format): Euler rest-relative degrees, Rz*Ry*Rx. */
  function toViewerClip(clip, rig) {
    const T = clip.frames.map((_, f) => +(f * clip.dt).toFixed(4));
    const bones = {};
    rig.bones.forEach((b, i) => {
      let prev = null; const r = [];
      for (const fr of clip.frames) {
        const e = nearestEuler(matToEulerZYX(fr.locals[i]), prev); prev = e;
        r.push(+(e[0] - b.rest[0]).toFixed(3), +(e[1] - b.rest[1]).toFixed(3), +(e[2] - b.rest[2]).toFixed(3));
      }
      if (r.some((v) => Math.abs(v) > 1e-3)) bones[b.name] = { r };
    });
    const rootName = rig.bones[clip.rootBone].name;
    bones[rootName] = bones[rootName] || { r: new Array(T.length * 3).fill(0) };
    bones[rootName].p = clip.frames.flatMap((fr) => fr.rootPos.map((v) => +v.toFixed(4)));
    const ht = +clip.hitTime.toFixed(4);
    return { length: +clip.length.toFixed(4), loop: false, events: [[ht, 'hit']], hits: [ht], T, bones };
  }

  /**
   * PulseLib Gecko-format animation (see the "PulseLib math" notes): per key, rotation = XYZ Euler of
   * q_anim = q_pose * q_rest^-1, linear keys only, positions in px added to the rest pivot.
   * Not yet verified in game.
   */
  function toPulseLibAnimation(clip, rig, key) {
    const bones = {};
    rig.bones.forEach((b, i) => {
      const rot = {}; let prev = null, moved = false;
      clip.frames.forEach((fr, f) => {
        const qa = mul(fr.locals[i], tr(b.restMat));
        const e = nearestEuler(matToEulerXYZ(qa), prev); prev = e;
        if (e.some((v) => Math.abs(v) > 1e-3)) moved = true;
        rot[(f * clip.dt).toFixed(4)] = { vector: e.map((v) => +v.toFixed(3)), lerp_mode: 'linear' };
      });
      if (moved) bones[b.name] = { rotation: rot };
    });
    const rootName = rig.bones[clip.rootBone].name, pos = {};
    clip.frames.forEach((fr, f) => { pos[(f * clip.dt).toFixed(4)] = { vector: fr.rootPos.map((v) => +v.toFixed(4)), lerp_mode: 'linear' }; });
    bones[rootName] = Object.assign(bones[rootName] || {}, { position: pos });
    return { format_version: '1.8.0', animations: { [key]: { animation_length: +clip.length.toFixed(4), bones } } };
  }

  /** Pick the first rig map whose required bones all exist in the rig. */
  function detectMap(rig, maps) {
    const need = (m) => [m.root, m.upper, m.head, m.pelvis, ...m.arms.right, ...m.arms.left, ...m.legs.right, ...m.legs.left].filter(Boolean);
    return maps.find((m) => need(m).every((n) => rig.idx[n] !== undefined)) || null;
  }

  const api = { parseBVH, bvhFK, parseRig, rigFK, retarget, toViewerClip, toPulseLibAnimation, detectMap, DEFAULTS,
                eulerZYXtoMat, matToEulerZYX, matToEulerXYZ, mul, tr };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.AnimRetarget = api;
})(typeof window !== 'undefined' ? window : globalThis);
