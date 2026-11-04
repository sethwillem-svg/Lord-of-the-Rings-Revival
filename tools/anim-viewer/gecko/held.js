/* Where things go on the NPC rig in the viewer, as the game draws them (client.render.PulseNpcRenderer).
 *   partToRig(part, l)                  a point of a vanilla part's mesh (part space: px, y down, about its pivot) in rig space
 *   FIST.right / .left                  the fist (the weapon bones' pivots): an item's handle is seated on it
 *   quadsOf(pos, idx)                   an exported or three.js mesh's quads (two triangles each) as [[x, y, z] x4, ...]
 *   drawnItem(quads)                    { pts, length }: the drawn weapon's points and its length (px, farthest pair)
 *   seatItem(cls, grips, quads, fist)   { scale, grip, fist }: scale the drawn length to the class length (weapon_grips.json,
 *                                       blocks, pommel to tip as drawn) about the grip (the drawn point nearest the fist),
 *                                       then move the grip onto the fist; null for classes without a length
 *   seatPoint(seat, p)                  p as seated (p itself for a null seat; a shield's seat, gecko/shield.js seat, is a
 *                                       strap matrix { m })
 *   itemScale(cls, grips, length)       class length (blocks) over the item's drawn length (px)
 *   scaleAbout(c, s, p)                 p scaled by s about c
 *   bendPoint(joint, r, band)           a limb point turned by its elbow or knee: fully below the joint, blended across ±band px
 * Rig space: px, y up, facing -z (gecko/rig.js). */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const S = isNode ? require('./sample.js') : root.GeckoSample;

  /** Vanilla part pivots in rig space: Minecraft model point m -> (-m.x, 24 - m.y, m.z). */
  const PART_PIVOT = { head: [0, 24, 0], hat: [0, 24, 0], body: [0, 24, 0], right_arm: [5, 22, 0], left_arm: [-5, 22, 0], right_leg: [1.9, 12, 0], left_leg: [-1.9, 12, 0] };
  const FIST = { right: [6, 12, -2], left: [-6, 12, -2] };

  function partToRig(part, l) {
    const p = PART_PIVOT[part];
    return [p[0] - l[0], p[1] - l[1], p[2] + l[2]];
  }

  function itemScale(cls, grips, length) {
    const g = cls && grips[cls];
    return g && g.length && length > 1e-6 ? (g.length * 16) / length : 1;
  }

  const scaleAbout = (c, s, p) => p.map((x, i) => c[i] + s * (x - c[i]));

  /* Middle-earth's weapons are flat generated item models: two faces over the whole texture square (front and back) and
   * thin edge quads, one texel deep, along the outline of the opaque pixels. Measuring every quad measures the texture
   * square, not the weapon, and weapons fill 55-100% of their pictures. So the big faces are dropped: any quad over a
   * quarter of the largest quad's area and over about 4 texels² (4 px²: a texel is about 1 px once held). If that leaves
   * nothing (a real 3D model), every quad counts. The same measure is in client.render.WeaponGrip. */
  const FACE_SHARE = 0.25, FACE_MIN = 4;
  const sub = (a, b) => a.map((x, i) => x - b[i]);
  const dist = (a, b) => Math.hypot(...sub(a, b));
  function area(q) {
    let s = 0;
    for (let k = 1; k + 1 < q.length; k++) {
      const u = sub(q[k], q[0]), v = sub(q[k + 1], q[0]);
      s += Math.hypot(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]) / 2;
    }
    return s;
  }

  function quadsOf(pos, idx) {
    const at = (v) => [pos[3 * v], pos[3 * v + 1], pos[3 * v + 2]], out = [];
    for (let t = 0; t + 5 < idx.length; t += 6) {
      const a = [idx[t], idx[t + 1], idx[t + 2]], b = [idx[t + 3], idx[t + 4], idx[t + 5]];
      const extra = b.find((v) => !a.includes(v)), ring = a.slice();
      if (extra !== undefined) {   // the second triangle's own vertex goes between the two it shares with the first
        const k = [0, 1, 2].find((i) => b.includes(a[i]) && b.includes(a[(i + 1) % 3]));
        ring.splice(k + 1, 0, extra);
      }
      out.push(ring.map(at));
    }
    return out;
  }

  function drawnItem(quads) {
    const areas = quads.map(area), max = Math.max(0, ...areas);
    let kept = quads.filter((q, i) => !(areas[i] > FACE_SHARE * max && areas[i] > FACE_MIN));
    if (!kept.length) kept = quads;
    const pts = kept.flat();
    if (!pts.length) return { pts, length: 0 };
    // the farthest pair, two passes: the point farthest from any point, then the point farthest from that one
    const far = (from) => pts.reduce((best, p) => (dist(p, from) > dist(best, from) ? p : best), pts[0]);
    const a = far(pts[0]), b = far(a);
    return { pts, length: dist(a, b) };
  }

  function seatItem(cls, grips, quads, fist) {
    const g = cls && grips[cls];
    if (!g || !g.length) return null;
    const d = drawnItem(quads);
    if (d.length < 1e-3) return null;
    const grip = d.pts.reduce((best, p) => (dist(p, fist) < dist(best, fist) ? p : best), d.pts[0]);
    return { scale: itemScale(cls, grips, d.length), grip, fist };
  }

  /** A seat is a weapon's (scale about the grip, grip onto the fist) or a shield's strap, a 4x4 m (gecko/shield.js seat). */
  function seatPoint(seat, p) {
    if (!seat) return p;
    if (seat.m) return [0, 1, 2].map((r) => seat.m[r * 4] * p[0] + seat.m[r * 4 + 1] * p[1] + seat.m[r * 4 + 2] * p[2] + seat.m[r * 4 + 3]);
    return scaleAbout(seat.grip, seat.scale, p).map((x, i) => x + seat.fist[i] - seat.grip[i]);
  }

  function bendPoint(joint, r, band) {
    const w = Math.max(0, Math.min(1, (joint.pivot[1] + band - r[1]) / (2 * band)));
    if (w <= 0) return r.slice();
    let q = joint.q;
    if (w < 1) {
      const d = Math.min(1, Math.abs(q[3])), th = Math.acos(d), s = Math.sin(th);
      const sign = q[3] < 0 ? -1 : 1;
      q = s < 1e-6 ? [0, 0, 0, 1] : [...q.slice(0, 3).map((x) => (sign * x * Math.sin(w * th)) / s), Math.cos(w * th)];
    }
    const R = S.quatMatrix(q), v = r.map((x, i) => x - joint.pivot[i]);
    return [0, 1, 2].map((i) => R[i * 3] * v[0] + R[i * 3 + 1] * v[1] + R[i * 3 + 2] * v[2] + joint.pivot[i] + w * joint.pos[i]);
  }

  /** An exported item's weapon class; exports made before scimitars became swords (combat.WeaponClass) still say longsword. */
  const exportClass = (weapon, label) => (weapon === 'longsword' && /scimitar/i.test(label || '') ? 'sword' : weapon);

  const api = { PART_PIVOT, FIST, partToRig, itemScale, scaleAbout, quadsOf, drawnItem, seatItem, seatPoint, bendPoint, exportClass };
  if (isNode) module.exports = api;
  else root.GeckoHeld = api;
})(typeof window !== 'undefined' ? window : globalThis);
