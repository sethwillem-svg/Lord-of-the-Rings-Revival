/* Where things go on the NPC rig in the viewer, as the game draws them (client.render.PulseNpcRenderer).
 *   partToRig(part, l)                  a point of a vanilla part's mesh (part space: px, y down, about its pivot) in rig space
 *   FIST.right / .left                  the fist (the weapon bones' pivots): items are scaled about it
 *   itemScale(cls, grips, extent)       class length (weapon_grips.json, blocks) over the item's own longest extent (px)
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

  function itemScale(cls, grips, extent) {
    const g = cls && grips[cls];
    return g && g.length && extent > 1e-6 ? (g.length * 16) / extent : 1;
  }

  const scaleAbout = (c, s, p) => p.map((x, i) => c[i] + s * (x - c[i]));

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

  const api = { PART_PIVOT, FIST, partToRig, itemScale, scaleAbout, bendPoint, exportClass };
  if (isNode) module.exports = api;
  else root.GeckoHeld = api;
})(typeof window !== 'undefined' ? window : globalThis);
