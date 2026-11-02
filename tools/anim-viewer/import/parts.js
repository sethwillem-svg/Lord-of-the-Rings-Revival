/* Shared conversion from Minecraft-style independent body parts (each about its vanilla pivot) to the NPC rig's hierarchy.
 * Values are Blockbench / ModelPart conventions (Clip convention, gecko/write.js). Matrices are row-major 3x3, true space.
 *   partWorld(pivot, rot, pos)                world transform of a part rotated about its pivot and moved by pos
 *   worldToClip(frames, times, length, loop)  { bone: world } per frame -> Clip with local keys on the rig
 *   ease(name, f), sampleTrack(keys, t)       easing (easings.net) and sampling of { t, v, ease } tracks */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const W = isNode ? require('../gecko/write.js') : root.GeckoWrite;
  const { RIG } = isNode ? require('../gecko/rig.js') : root.GeckoRig;
  const { trueRotation, fromTrue, mul3 } = W;

  /** Vanilla part pivots in rig space (Minecraft model point m -> (-m.x, 24 - m.y, m.z)). */
  const VANILLA_PIVOT = {
    head: [0, 24, 0], body: [0, 24, 0], right_arm: [5, 22, 0], left_arm: [-5, 22, 0], right_leg: [1.9, 12, 0], left_leg: [-1.9, 12, 0],
  };
  const PIVOT = Object.fromEntries(RIG.map((b) => [b.name, b.pivot]));
  const PARENT = Object.fromEntries(RIG.map((b) => [b.name, b.parent]));

  const IDENTITY = { R: [1, 0, 0, 0, 1, 0, 0, 0, 1], p: [0, 0, 0] };
  const apply = (R, v) => [0, 1, 2].map((r) => R[r * 3] * v[0] + R[r * 3 + 1] * v[1] + R[r * 3 + 2] * v[2]);
  const transpose = (R) => [R[0], R[3], R[6], R[1], R[4], R[7], R[2], R[5], R[8]];
  const add = (a, b) => a.map((x, i) => x + b[i]);
  const sub = (a, b) => a.map((x, i) => x - b[i]);

  /** x -> R (x - pivot) + pivot + offset, with R from a Blockbench rotation and offset from a Blockbench position. */
  function partWorld(pivot, rot, pos) {
    const R = trueRotation(rot || [0, 0, 0]);
    const off = pos ? [-pos[0], pos[1], pos[2]] : [0, 0, 0];
    return { R, p: add(sub(add(pivot, off), apply(R, pivot)), [0, 0, 0]) };
  }

  /** a then b: (a ∘ b)(x) = a(b(x)). */
  function compose(a, b) {
    return { R: mul3(a.R, b.R), p: add(apply(a.R, b.p), a.p) };
  }

  function inverse(a) {
    const Rt = transpose(a.R);
    return { R: Rt, p: apply(Rt, a.p).map((x) => -x) };
  }

  /** A rotation matrix in Minecraft model space (y down, x mirrored) as true space: conjugation by diag(-1,-1,1). */
  function mcToTrue(m) {
    return [m[0], m[1], -m[2], m[3], m[4], -m[5], -m[6], -m[7], m[8]];
  }

  /** frames: [{ bone: world }]; bones not given follow their parent. Keys are written for bones that ever move. */
  function worldToClip(frames, times, length, loop) {
    const tracks = {};
    for (let f = 0; f < frames.length; f++) {
      const world = {};
      for (const b of RIG) {
        const parent = b.parent ? world[b.parent] : IDENTITY;
        const w = frames[f][b.name] || parent;
        world[b.name] = w;
        const L = compose(inverse(parent), w);
        const pv = PIVOT[b.name];
        const posTrue = add(sub(L.p, pv), apply(L.R, pv));
        const rot = fromTrue(L.R), pos = [-posTrue[0], posTrue[1], posTrue[2]];
        const t = (tracks[b.name] = tracks[b.name] || { rotation: [], position: [] });
        t.rotation.push({ t: times[f], v: rot.map(clean), lerp: 'linear' });
        t.position.push({ t: times[f], v: pos.map(clean), lerp: 'linear' });
      }
    }
    const bones = {};
    for (const [name, t] of Object.entries(tracks)) {
      const b = {};
      if (t.rotation.some((k) => k.v.some((x) => Math.abs(x) > 1e-3))) b.rotation = t.rotation;
      if (t.position.some((k) => k.v.some((x) => Math.abs(x) > 1e-3))) b.position = t.position;
      if (b.rotation || b.position) bones[name] = b;
    }
    return { length, loop, bones };
  }

  const clean = (x) => (Math.abs(x) < 1e-9 ? 0 : +x.toFixed(5));

  /** Sample times every 1/rate s from 0 to length inclusive. */
  function sampleTimes(length, rate = 20) {
    const n = Math.max(1, Math.round(length * rate));
    return Array.from({ length: n + 1 }, (_, i) => (i * length) / n);
  }

  // Easing (easings.net), by name without the "ease" prefix, any case: inoutsine, outcirc, ...
  const c1 = 1.70158, c2 = c1 * 1.525, c3 = c1 + 1, c4 = (2 * Math.PI) / 3, c5 = (2 * Math.PI) / 4.5;
  function bounceOut(x) {
    const n1 = 7.5625, d1 = 2.75;
    if (x < 1 / d1) return n1 * x * x;
    if (x < 2 / d1) return n1 * (x -= 1.5 / d1) * x + 0.75;
    if (x < 2.5 / d1) return n1 * (x -= 2.25 / d1) * x + 0.9375;
    return n1 * (x -= 2.625 / d1) * x + 0.984375;
  }
  const IN = {
    sine: (x) => 1 - Math.cos((x * Math.PI) / 2), quad: (x) => x * x, cubic: (x) => x ** 3, quart: (x) => x ** 4, quint: (x) => x ** 5,
    expo: (x) => (x === 0 ? 0 : 2 ** (10 * x - 10)), circ: (x) => 1 - Math.sqrt(1 - x * x), back: (x) => c3 * x ** 3 - c1 * x * x,
    elastic: (x) => (x === 0 || x === 1 ? x : -(2 ** (10 * x - 10)) * Math.sin((x * 10 - 10.75) * c4)), bounce: (x) => 1 - bounceOut(1 - x),
  };
  function ease(name, f) {
    const n = String(name || 'linear').toLowerCase().replace(/^ease/, '');
    if (n === 'linear' || n === '') return f;
    if (n === 'constant' || n === 'step') return 0;
    const m = /^(inout|in|out)(\w+)$/.exec(n);
    if (!m || !IN[m[2]]) return f;
    const g = IN[m[2]];
    if (m[1] === 'in') return g(f);
    if (m[1] === 'out') return 1 - g(1 - f);
    return f < 0.5 ? g(2 * f) / 2 : 1 - g(2 - 2 * f) / 2;
  }

  /** keys sorted by t, each { t, v: [..], ease } where ease shapes the segment ending at that key. */
  function sampleTrack(keys, t) {
    if (!keys.length) return null;
    if (t <= keys[0].t) return keys[0].v;
    for (let i = 1; i < keys.length; i++) {
      const a = keys[i - 1], b = keys[i];
      if (t <= b.t) {
        const f = b.t > a.t ? ease(b.ease, (t - a.t) / (b.t - a.t)) : 1;
        return a.v.map((x, j) => x + f * (b.v[j] - x));
      }
    }
    return keys[keys.length - 1].v;
  }

  const api = { VANILLA_PIVOT, PIVOT, PARENT, partWorld, compose, inverse, mcToTrue, worldToClip, sampleTimes, ease, sampleTrack, IDENTITY };
  if (isNode) module.exports = api;
  else root.ImportParts = api;
})(typeof window !== 'undefined' ? window : globalThis);
