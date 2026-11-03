/* Where a held weapon is during a clip, for hit times and weapon checks. The weapon is a straight blade seated the way
 * Minecraft holds a handheld item: handle in the fist (the weapon bone's pivot), pointing up and forward at 45° in the arm
 * frame, as long as its class (weapon_grips.json). Rig space: px, y up, facing -z (gecko/rig.js).
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

  const api = { FIST, DIR, blade, frontAngle, hitTime, strike };
  if (isNode) module.exports = api;
  else root.GeckoStrike = api;
})(typeof window !== 'undefined' ? window : globalThis);
