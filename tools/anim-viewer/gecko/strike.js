/* Where a held weapon is during a clip, for hit times and weapon checks. The weapon is a straight blade seated the way
 * Minecraft holds a handheld item: handle in the fist (the weapon bone's pivot), pointing up and forward at 45° in the arm
 * frame, as long as its class (weapon_grips.json: the drawn weapon, pommel to tip, taken here from the fist to the tip).
 * Rig space: px, y up, facing -z (gecko/rig.js).
 *   blade(side, blocks, f)        a rest point a fraction f along the blade (0 fist, 1 tip)
 *   frontAngle(p)                 degrees between straight ahead and p's direction from the body axis (180 when behind)
 *   hitTime(anim, blocks)         when the strike lands: in the fast part of the swing, where the tip is most directly ahead
 *   strike(anim, blocks, side)    { hit, tip, frontAngle, bodyYawRange, tipSweep, leftToBlade } */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const S = isNode ? require('./sample.js') : root.GeckoSample;
  const FIST = { right: [6, 12, -2], left: [-6, 12, -2] };
  const DIR = [0, Math.SQRT1_2, -Math.SQRT1_2];
  const BONE = { right: 'weapon_r', left: 'weapon_l' };

  const blade = (side, blocks, f) => FIST[side].map((x, i) => x + DIR[i] * blocks * 16 * f);
  const frontAngle = (p) => (p[2] >= 0 ? 180 : (Math.atan2(Math.abs(p[0]), -p[2]) * 180) / Math.PI);
  const pose = (anim, t) => S.worldBones(S.samplePose(anim, t));

  function speeds(anim, blocks, side, from, to) {
    const L = anim.animation_length, dt = 1 / 60, out = [];
    const tip = (t) => S.point(pose(anim, t)[BONE[side]], blade(side, blocks, 1));
    for (let t = from * L; t <= to * L + 1e-9; t += dt / 2) {
      const a = tip(Math.max(0, t - dt)), b = tip(Math.min(L, t + dt));
      out.push({ t, tip: tip(t), v: Math.hypot(b[0] - a[0], b[1] - a[1], b[2] - a[2]) });
    }
    return out;
  }

  function hitTime(anim, blocks = 1, side = 'right') {
    const s = speeds(anim, blocks, side, 0.15, 0.85);
    const peak = Math.max(...s.map((x) => x.v));
    const fast = s.filter((x) => x.v >= 0.5 * peak);
    let best = fast[fast.length - 1];
    for (const x of fast) if (frontAngle(x.tip) < frontAngle(best.tip) - 1e-9) best = x;
    return +best.t.toFixed(3);
  }

  /** The body's facing (degrees about y) from the body bone. */
  function bodyYaw(w) {
    const R = w.body.R;
    return (Math.atan2(-R[2], -R[8]) * 180) / Math.PI;
  }

  function strike(anim, blocks = 1, side = 'right') {
    const L = anim.animation_length, hit = hitTime(anim, blocks, side);
    const w = pose(anim, hit), other = side === 'right' ? 'left' : 'right';
    const tip = S.point(w[BONE[side]], blade(side, blocks, 1)), fist = S.point(w[BONE[side]], FIST[side]);
    const hand = S.point(w[BONE[other]], FIST[other]);
    const ab = tip.map((x, i) => x - fist[i]), ap = hand.map((x, i) => x - fist[i]);
    const u = Math.max(0, Math.min(1, ab.reduce((s, x, i) => s + x * ap[i], 0) / ab.reduce((s, x) => s + x * x, 0)));
    const leftToBlade = Math.hypot(...ap.map((x, i) => x - u * ab[i]));
    // turning: the body's yaw range and how far round the tip travels about the body axis
    let yaw0 = bodyYaw(pose(anim, 0)), yawSum = 0, yawLo = 0, yawHi = 0;
    let az0 = null, azSum = 0, azLo = 0, azHi = 0;
    for (let t = 0; t <= L + 1e-9; t += 1 / 60) {
      const p = pose(anim, t), y = bodyYaw(p), tp = S.point(p[BONE[side]], blade(side, blocks, 1));
      let d = y - yaw0; d -= 360 * Math.round(d / 360); yawSum += d; yaw0 = y;
      yawLo = Math.min(yawLo, yawSum); yawHi = Math.max(yawHi, yawSum);
      const az = (Math.atan2(tp[0], -tp[2]) * 180) / Math.PI;
      if (az0 !== null) { let e = az - az0; e -= 360 * Math.round(e / 360); azSum += e; azLo = Math.min(azLo, azSum); azHi = Math.max(azHi, azSum); }
      az0 = az;
    }
    return { hit, tip, frontAngle: frontAngle(tip), bodyYawRange: yawHi - yawLo, tipSweep: azHi - azLo, leftToBlade };
  }

  /** A strike the game can use: lands in front (tip 6 px ahead, within 45°, knee height to just over the head) and does not spin. */
  function acceptable(anim, blocks) {
    const s = strike(anim, blocks);
    return s.frontAngle <= 45 && s.tip[2] <= -6 && s.tip[1] >= 4 && s.tip[1] <= 50 && s.bodyYawRange <= 180 && s.tipSweep <= 300;
  }

  /** A joint's hinge angle (degrees; the game's bend deformer reads it the same way): negative folds forward. */
  function hinge(w, joint, parent) {
    const P = w[parent].R, J = w[joint].R;
    const col1 = [0, 1, 2].map((r) => P[r] * J[1] + P[3 + r] * J[4] + P[6 + r] * J[7]);   // (P^T J) y column
    return (Math.atan2(-col1[2], col1[1]) * 180) / Math.PI;
  }

  /** Elbows never fold backwards and knees never forwards by more than 10°, and they bend no faster than 1400°/s / 1100°/s. */
  function hingesOk(anim) {
    let prev = null;
    for (let t = 0; t <= anim.animation_length + 1e-9; t += 1 / 120) {
      const w = pose(anim, t), now = {};
      for (const s of ['right', 'left']) {
        now[s + 'e'] = hinge(w, `${s}_elbow`, `${s}_arm`); now[s + 'k'] = hinge(w, `${s}_knee`, `${s}_leg`);
        if (now[s + 'e'] > 10 || now[s + 'k'] < -10) return false;
        if (prev && (Math.abs(now[s + 'e'] - prev[s + 'e']) * 120 > 1400 || Math.abs(now[s + 'k'] - prev[s + 'k']) * 120 > 1100)) return false;
      }
      prev = now;
    }
    return true;
  }

  /**
   * The weapon moves cleanly: after the first `from` seconds (the overlay's fade-in) the tip never turns on a radius tighter
   * than a quarter of the blade while fast, and after the hit it does not speed up again (a bounce).
   */
  function cleanSwing(anim, blocks, from = 0.1, side = 'right') {
    const L = anim.animation_length, dt = 1 / 120, hit = hitTime(anim, blocks, side);
    const tip = (t) => S.point(pose(anim, Math.max(0, Math.min(L, t)))[BONE[side]], blade(side, blocks, 1));
    const v = [];
    for (let t = dt; t <= L + 1e-9; t += dt) { const a = tip(t - dt), b = tip(t); v.push({ t, d: b.map((x, i) => (x - a[i]) / dt) }); }
    v.forEach((x) => { x.s = Math.hypot(...x.d); });
    const peak = Math.max(...v.map((x) => x.s));
    for (let i = 1; i < v.length; i++) {
      if (v[i].t < from || v[i].s < 0.5 * peak || v[i - 1].s < 0.5 * peak) continue;
      const c = (v[i].d[0] * v[i - 1].d[0] + v[i].d[1] * v[i - 1].d[1] + v[i].d[2] * v[i - 1].d[2]) / (v[i].s * v[i - 1].s);
      const ang = Math.acos(Math.max(-1, Math.min(1, c)));
      if (ang > 1e-6 && ((v[i].s + v[i - 1].s) / 2) / (ang / dt) < 0.25 * blocks * 16) return false;
    }
    const fastest = v.filter((x) => x.t >= from).reduce((b, x) => (x.s > b.s ? x : b));
    if (Math.abs(fastest.t - hit) > 0.1) return false;   // the fastest moment is the strike
    const slow = v.find((x) => x.t > hit && x.s < 0.4 * peak);   // a bounce: speeding up again once slowed
    return !(slow && v.some((x) => x.t > slow.t && x.t < slow.t + 0.25 && x.s > 0.6 * peak));
  }

  /** The torso over the hips stays in a spine's range (twist 60°, forward 45°, back 25°, sideways 30°) throughout. */
  function waistOk(anim) {
    for (let t = 0; t <= anim.animation_length + 1e-9; t += anim.animation_length / 24) {
      const w = pose(anim, t), L = w.lower.R, U = w.upper.R, R = [];
      for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) R.push(L[r] * U[c] + L[3 + r] * U[3 + c] + L[6 + r] * U[6 + c]);
      const f = [-R[2], -R[5], -R[8]], u = [R[1], R[4], R[7]];
      const twist = (Math.atan2(-f[0], -f[2]) * 180) / Math.PI, flex = (Math.asin(Math.max(-1, Math.min(1, -f[1]))) * 180) / Math.PI;
      const side = (Math.atan2(u[0], u[1]) * 180) / Math.PI;
      if (Math.abs(twist) > 60 || flex > 45 || flex < -25 || Math.abs(side) > 30) return false;
    }
    return true;
  }

  /** The blade stays out of the torso and head from `from` seconds on (the overlay's fade-in hides the frames before). */
  function clearOfBody(anim, blocks, from = 0.1, side = 'right') {
    const boxes = [['body', [[-4, 12, -2], [4, 24, 2]]], ['head', [[-4, 24, -4], [4, 32, 4]]]];
    for (let t = from; t <= anim.animation_length + 1e-9; t += 1 / 60) {
      const w = pose(anim, t);
      for (const k of [0.3, 0.5, 0.7, 0.9, 1]) {
        const p = S.point(w[BONE[side]], blade(side, blocks, k));
        for (const [bone, box] of boxes) {
          const b = w[bone], v = [0, 1, 2].map((i) => p[i] - b.p[i]);
          const q = [0, 1, 2].map((c) => b.R[c] * v[0] + b.R[3 + c] * v[1] + b.R[6 + c] * v[2]);
          if (q.every((x, i) => x > box[0][i] && x < box[1][i])) return false;
        }
      }
    }
    return true;
  }

  /** Strike speed over wind-up speed: the tip's top speed near the hit against its mean speed earlier (after `from`). */
  function spacing(anim, blocks, from = 0.1, side = 'right') {
    const hit = hitTime(anim, blocks, side), L = anim.animation_length, dt = 1 / 120;
    const tip = (t) => S.point(pose(anim, Math.max(0, Math.min(L, t)))[BONE[side]], blade(side, blocks, 1));
    const wind = [], strikeV = [];
    for (let t = dt; t <= L + 1e-9; t += dt) {
      const v = Math.hypot(...tip(t).map((x, i) => x - tip(t - dt)[i])) / dt, mid = t - dt / 2;
      if (mid > from && mid < hit - 0.15) wind.push(v); else if (mid >= hit - 0.15 && mid <= hit + 0.02) strikeV.push(v);
    }
    if (wind.length < 6 || !strikeV.length) return Infinity;
    return Math.max(...strikeV) / (wind.reduce((a, b) => a + b, 0) / wind.length);
  }

  /** The first moment after `after` at which the blade is inside the torso or head, or null. */
  function bodyHitAfter(anim, blocks, after, side = 'right') {
    const L = anim.animation_length, n = Math.max(1, Math.ceil((L - after) * 120));
    for (let i = 0; i <= n; i++) {
      const t = Math.min(L, after + i / 120);
      const sub = { animation_length: anim.animation_length, loop: anim.loop, bones: anim.bones };
      if (!clearOfBody({ ...sub, animation_length: Math.min(L, t + 1e-6) }, blocks, t, side)) return t;
    }
    return null;
  }

  /** The fade-in an attack gets (s): 0.1, shorter only for an early hit (the fade must be over by 80% of it). */
  const fadeFor = (anim, blocks, side = 'right') => Math.min(0.1, Math.max(0.05, Math.floor(0.8 * hitTime(anim, blocks, side) * 20) / 20));

  /** Everything a finished attack must be (the build's own bar; the tests measure independently). */
  function quality(anim, blocks, side = 'right') {
    const fade = fadeFor(anim, blocks, side);
    return acceptable(anim, blocks) && hingesOk(anim) && waistOk(anim) && cleanSwing(anim, blocks, fade, side)
      && clearOfBody(anim, blocks, fade, side) && spacing(anim, blocks, fade, side) >= 3;
  }

  const api = { speeds, fadeFor, quality, spacing, bodyHitAfter, clearOfBody, FIST, DIR, blade, frontAngle, hitTime, strike, acceptable, hinge, hingesOk, cleanSwing, waistOk };
  if (isNode) module.exports = api;
  else root.GeckoStrike = api;
})(typeof window !== 'undefined' ? window : globalThis);
