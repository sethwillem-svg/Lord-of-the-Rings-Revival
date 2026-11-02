/* Plays the GeckoLib animation JSON the way PulseLib does: rotation keys are XYZ Euler degrees (Rx·Ry·Rz, slerped as
 * quaternions), position keys are px offsets, both as written (gecko/write.js), about the rig's pivots (gecko/rig.js).
 *   samplePose(animation, t)  { bone: { q: [x,y,z,w], pos: [x,y,z] } }  loops wrap, one-shots and holds clamp
 *   worldBones(pose)          { bone: { R (row-major 3x3), p } }  x -> R x + p in rig space (px, y up, facing -z)
 *   point(world, v)           a rig-space rest point moved by a bone's world transform
 * A segment is stepped when its first key is 'step', Catmull-Rom (positions) when either end is 'catmullrom'. */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const { RIG } = isNode ? require('./rig.js') : root.GeckoRig;
  const RAD = Math.PI / 180;

  const parsed = new WeakMap();
  function channelKeys(ch) {
    if (!ch) return null;
    if (parsed.has(ch)) return parsed.get(ch);
    const keys = Object.entries(ch).map(([t, k]) => ({ t: Number(t), v: (k.vector || k.post || k).map(Number), lerp: k.lerp_mode || 'linear' })).sort((a, b) => a.t - b.t);
    parsed.set(ch, keys);
    return keys;
  }

  function quatXyz(v) {
    const h = v.map((d) => (d * RAD) / 2);
    const qx = [Math.sin(h[0]), 0, 0, Math.cos(h[0])], qy = [0, Math.sin(h[1]), 0, Math.cos(h[1])], qz = [0, 0, Math.sin(h[2]), Math.cos(h[2])];
    return qmul(qmul(qx, qy), qz);
  }

  function qmul(a, b) {
    return [
      a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
      a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
      a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
      a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
    ];
  }

  function slerp(a, b, f) {
    let d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    if (d < 0) { b = b.map((x) => -x); d = -d; }
    if (d > 0.9995) {
      const r = a.map((x, i) => x + f * (b[i] - x));
      const n = Math.hypot(...r);
      return r.map((x) => x / n);
    }
    const th = Math.acos(d), s = Math.sin(th);
    const wa = Math.sin((1 - f) * th) / s, wb = Math.sin(f * th) / s;
    return a.map((x, i) => wa * x + wb * b[i]);
  }

  function catmull(p0, p1, p2, p3, f) {
    return p1.map((_, i) => 0.5 * (2 * p1[i] + (-p0[i] + p2[i]) * f + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * f * f + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * f * f * f));
  }

  function sampleKeys(keys, t, rotation) {
    const val = (k) => (rotation ? quatXyz(k.v) : k.v);
    if (t <= keys[0].t) return val(keys[0]);
    for (let i = 1; i < keys.length; i++) {
      const a = keys[i - 1], b = keys[i];
      if (t > b.t) continue;
      if (a.lerp === 'step') return val(a);
      const f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
      if (rotation) return slerp(val(a), val(b), f);
      if (a.lerp === 'catmullrom' || b.lerp === 'catmullrom') {
        return catmull((keys[i - 2] || a).v, a.v, b.v, (keys[i + 1] || b).v, f);
      }
      return a.v.map((x, j) => x + f * (b.v[j] - x));
    }
    return val(keys[keys.length - 1]);
  }

  function samplePose(animation, t) {
    const len = animation.animation_length || 0;
    const time = animation.loop === true && len > 0 ? ((t % len) + len) % len : Math.min(Math.max(t, 0), len);
    const pose = {};
    for (const [bone, ch] of Object.entries(animation.bones || {})) {
      const r = channelKeys(ch.rotation), p = channelKeys(ch.position);
      pose[bone] = { q: r ? sampleKeys(r, time, true) : [0, 0, 0, 1], pos: p ? sampleKeys(p, time, false) : [0, 0, 0] };
    }
    return pose;
  }

  function quatMatrix(q) {
    const [x, y, z, w] = q;
    return [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
      2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
      2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)];
  }
  const apply = (R, v) => [0, 1, 2].map((r) => R[r * 3] * v[0] + R[r * 3 + 1] * v[1] + R[r * 3 + 2] * v[2]);
  function mul3(a, b) {
    const o = new Array(9);
    for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
    return o;
  }

  function worldBones(pose, rig = RIG) {
    const out = {};
    for (const b of rig) {
      const s = pose[b.name] || { q: [0, 0, 0, 1], pos: [0, 0, 0] };
      const R = quatMatrix(s.q), pv = b.pivot;
      const lp = apply(R, pv).map((x, i) => pv[i] + s.pos[i] - x);   // x -> R (x - pv) + pv + pos
      const parent = b.parent ? out[b.parent] : { R: [1, 0, 0, 0, 1, 0, 0, 0, 1], p: [0, 0, 0] };
      out[b.name] = { R: mul3(parent.R, R), p: apply(parent.R, lp).map((x, i) => x + parent.p[i]) };
    }
    return out;
  }

  const point = (w, v) => apply(w.R, v).map((x, i) => x + w.p[i]);

  const api = { samplePose, worldBones, point, quatXyz, quatMatrix };
  if (isNode) module.exports = api;
  else root.GeckoSample = api;
})(typeof window !== 'undefined' ? window : globalThis);
