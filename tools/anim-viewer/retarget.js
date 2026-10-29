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
  function bvhFK(b) {
    const P = [];
    for (let f = 0; f < b.frames; f++) {
      const row = b.data[f], Pw = [], Rw = [];
      let c = 0;
      for (let j = 0; j < b.names.length; j++) {
        let pos = b.offset[j].slice(), rot = I3();
        for (const ch of b.chan[j]) {
          const v = row[c++];
          if (ch.endsWith('position')) pos['XYZ'.indexOf(ch[0])] = v;
          else rot = mul(rot, rotAxis(ch[0], v));
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
  };

  /** Hinge limb in world space. du, dl: unit upper/lower directions; def: default hinge axis; state.last carries continuity. */
  function hingeLimb(du, dl, def, state) {
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
    const theta = Math.atan2(-l[2], -l[1]);
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
    const P = bvhFK(bvh), F = bvh.frames, J = (n) => {
      if (opts.mirror) n = n.replace(/^Left/, '#').replace(/^Right/, 'Left').replace(/^#/, 'Right');
      const j = bvh.idx[n];
      if (j === undefined) throw new Error('BVH has no joint ' + n);
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
    for (let f = 0; f < F; f++) for (const n of ['LeftFoot', 'RightFoot', 'LeftToeBase', 'RightToeBase']) floor = Math.min(floor, g(f, n)[1]);
    const hipH = g(0, 'Hips')[1] - floor;
    const legRoot = rig.bones[B(map.legs.right[0])];
    const s = legRoot.origin[1] / hipH;

    const iRoot = B(map.root), iUpper = B(map.upper), iHead = map.head ? B(map.head) : -1;
    const iPelvis = map.pelvis ? B(map.pelvis) : -1;
    const limbs = [];
    for (const side of ['right', 'left']) {
      const S = side === 'right' ? 'Right' : 'Left';
      limbs.push({ kind: 'arm', bones: map.arms[side].map(B), j: [S + 'Arm', S + 'ForeArm', S + 'Hand'], state: {} });
      limbs.push({ kind: 'leg', bones: map.legs[side].map(B), j: [S + 'Leg', S + 'Shin', S + 'Foot'], state: {} });
    }
    const iWeapon = map.weapon && rig.idx[map.weapon] !== undefined ? B(map.weapon) : -1;
    const weaponLocal = iWeapon >= 0 && opts.weapon === 'forearm' ? rotAxis('X', 180) : null;

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

    const frames = [];
    const hips0 = g(0, 'Hips');
    for (let f = 0; f < F; f++) {
      const W = {};            // desired world rotations by bone index
      let upper = frameFrom(o(sub(g(f, 'LeftArm'), g(f, 'RightArm'))),
        o(sub(scl(add(g(f, 'LeftArm'), g(f, 'RightArm')), 0.5), g(f, 'Hips'))));
      upper = capTilt(upper, opts.torsoTiltCap);
      W[iUpper] = upper;
      if (iPelvis >= 0) W[iPelvis] = frameFrom(o(sub(g(f, 'LeftLeg'), g(f, 'RightLeg'))), o(sub(g(f, 'Spine2'), g(f, 'Hips'))));
      if (iHead >= 0) W[iHead] = frameFrom(o(sub(g(f, 'LeftEye'), g(f, 'RightEye'))), o(sub(g(f, 'HeadEnd'), g(f, 'Head'))));
      for (const l of limbs) {
        const a = g(f, l.j[0]), b = g(f, l.j[1]), c = g(f, l.j[2]);
        const def = l.kind === 'arm' ? col(upper, 0) : (iPelvis >= 0 ? col(W[iPelvis], 0) : [1, 0, 0]);
        const h = hingeLimb(norm(o(sub(b, a))), norm(o(sub(c, b))), def, l.state);
        W[l.bones[0]] = h.upper; W[l.bones[1]] = h.lower;
        if (l.bones[2] !== undefined) W[l.bones[2]] = h.lower;
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
      const rootPos = [hp[0], hp[1] * opts.rootDrop, hp[2]];
      frames.push({ locals, rootPos });
    }

    // root: lift so no foot goes below the floor; then QA traces
    const qa = { footMin: Infinity, handMin: Infinity, tipMin: Infinity, tipMax: -Infinity, lift: 0 };
    const hand = limbs[0].bones[2] !== undefined ? limbs[0].bones[2] : limbs[0].bones[1];
    let hitF = 0, best = Infinity;
    for (let f = 0; f < F; f++) {
      const fr = frames[f];
      let fk = rigFK(rig, fr.locals, fr.rootPos);
      const footY = () => Math.min(...footDrop.map((d) => fk.pos[d.i][1] + d.d));
      if (opts.footLift) {
        const y = footY();
        if (y < 0) { fr.rootPos[1] -= y; qa.lift = Math.max(qa.lift, -y); fk = rigFK(rig, fr.locals, fr.rootPos); }
      }
      qa.footMin = Math.min(qa.footMin, footY());
      qa.handMin = Math.min(qa.handMin, fk.pos[hand][1]);
      if (tipLocal) {
        const t = add(fk.pos[iWeapon], apply(fk.rot[iWeapon], tipLocal));
        fr.tip = t;
        qa.tipMin = Math.min(qa.tipMin, t[1]); qa.tipMax = Math.max(qa.tipMax, t[1]);
      }
      const hs = g(f, 'LeftHand')[1] + g(f, 'RightHand')[1];
      if (hs < best) { best = hs; hitF = f; }
    }
    if (!tipLocal) { qa.tipMin = null; qa.tipMax = null; }
    return { frames, dt: bvh.dt, length: (F - 1) * bvh.dt, hitFrame: hitF, hitTime: hitF * bvh.dt, scale: s, qa,
             rootBone: iRoot };
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
