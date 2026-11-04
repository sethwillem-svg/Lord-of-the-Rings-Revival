'use strict';
/* Anatomy yardsticks for the tests: where an upper arm points against the torso (shoulder range) and how deep a limb sinks
 * into the torso or head (body clearance). Kept apart from the build (hinge.js has its own), so the tests judge the build's
 * output with their own measure. Rig space (gecko/rig.js): px, y up, facing -z, the right arm on +x; world bones as
 * gecko/sample.js worldBones gives them ({ R row-major, p }: x -> R x + p). */
const { point } = require('../../gecko/sample.js');

const deg = (r) => (r * 180) / Math.PI;
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
/** Bᵀ v: a world direction in a bone's rest frame. */
const into = (B, v) => [0, 1, 2].map((k) => B.R[k] * v[0] + B.R[3 + k] * v[1] + B.R[6 + k] * v[2]);
const angle = (a, b) => deg(Math.acos(Math.max(-1, Math.min(1, dot(a, b)))));

/**
 * Shoulder range as a region of upper-arm directions in torso space (the body bone's frame): the arm's own long axis
 * (rest -y: the arm box hangs straight down from the shoulder) may point anywhere but into two caps.
 *  - Behind: the cap of 60° around the direction 30° above straight back (in the sagittal plane), which has on its rim
 *    straight up (flexion and abduction end there, at 180°) and 60° behind hanging (extension 60°). Out to the side it
 *    lets the level arm 35° behind the frontal plane (horizontal extension; the usual tables give 45°).
 *  - Across: the cap of 45° around straight across (horizontal, toward the other side), whose rim the level arm meets 45°
 *    past straight ahead (horizontal adduction); hanging, in front of the body or overhead the arm stays well clear of it.
 * Each is measured as the familiar figure it equals in its own plane: `back` = 120° less the angle to the back cap's centre
 * (the extension of an arm swung straight back), `across` = 90° less the angle to straight across (how far past straight
 * ahead a level arm reaches toward the other side). In range: back ≤ 60, across ≤ 45. Straight up and straight down are
 * ordinary directions here (no pole to leave out).
 */
const BACK = [0, Math.sin(Math.PI / 6), Math.cos(Math.PI / 6)];   // up 30°, back (+z)
const SHOULDER = { back: 60, across: 45 };
/** The rest -y axis of an arm bone in its parent's (the body's) frame. */
function armDir(w, arm, parent = 'body') {
  const R = w[arm].R;
  return into(w[parent], [-R[1], -R[4], -R[7]]);
}
/** { back, across } (degrees) for one arm ('right' or 'left') of world bones w. */
function shoulder(w, side) {
  const d = armDir(w, side === 'left' ? 'left_arm' : 'right_arm'), s = side === 'left' ? -1 : 1;
  return { back: 120 - angle(d, BACK), across: 90 - angle(d, [-s, 0, 0]) };
}

/** Rest boxes (rig px, in their bone's frame) of the biped: the torso 8×12×4 under the body bone, the head 8×8×8. */
const BOX = { torso: { bone: 'body', min: [-4, 12, -2], max: [4, 24, 2] }, head: { bone: 'head', min: [-4, 24, -4], max: [4, 32, 4] } };
/** Limb capsules (rest px, radius 2: the arms are 4 px thick), along the arm box's axis (x ±6, z 0; the box hangs from
 *  the shoulder at y 24 to the elbow pivot at 18, the forearm on to the hand's end at 12). Each segment stops 2 px short of
 *  where its part ends, so the round end lands there: the forearm from the elbow pivot to y 14 (its end is the fist); the
 *  upper arm's lower half from y 19 to the elbow pivot (its top is the arm's middle, y 21). The upper half, where the arm
 *  meets the shoulder, is left out. */
const R_LIMB = 2;
const CAPSULE = {
  right: { forearm: { bone: 'right_elbow', a: [6, 18, 0], b: [6, 14, 0] }, upper: { bone: 'right_arm', a: [6, 19, 0], b: [6, 18, 0] } },
  left: { forearm: { bone: 'left_elbow', a: [-6, 18, 0], b: [-6, 14, 0] }, upper: { bone: 'left_arm', a: [-6, 19, 0], b: [-6, 18, 0] } },
};

/** Signed distance (px) from a point in a box's frame to the box: negative inside. */
function boxDist(q, min, max) {
  let out = 0, inMax = -Infinity;
  for (let i = 0; i < 3; i++) {
    const c = (min[i] + max[i]) / 2, h = (max[i] - min[i]) / 2, d = Math.abs(q[i] - c) - h;
    if (d > 0) out += d * d;
    inMax = Math.max(inMax, d);
  }
  return out > 0 ? Math.sqrt(out) : Math.min(inMax, 0);
}
/** The least signed distance from segment a-b (box frame) to the box: convex along the segment, so a ternary search. */
function segDist(a, b, min, max) {
  const at = (f) => boxDist([0, 1, 2].map((i) => a[i] + (b[i] - a[i]) * f), min, max);
  let lo = 0, hi = 1;
  for (let k = 0; k < 40; k++) {
    const m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
    if (at(m1) <= at(m2)) hi = m2; else lo = m1;
  }
  return Math.min(at(0), at(1), at((lo + hi) / 2));
}
/** How deep (px) a limb capsule sinks into a box at world bones w: R_LIMB less the segment's signed distance (≤ 0 clear). */
function depth(w, cap, box) {
  const B = w[box.bone], to = (p) => into(B, [0, 1, 2].map((i) => p[i] - B.p[i]));
  const a = to(point(w[cap.bone], cap.a)), b = to(point(w[cap.bone], cap.b));
  return R_LIMB - segDist(a, b, box.min, box.max);
}
/** The deepest a side's limb goes into the body at w: { d (px), what } over the forearm in the torso and head and the upper
 *  arm's lower half in the torso. */
function overlap(w, side) {
  const c = CAPSULE[side];
  return [['forearm', 'torso'], ['forearm', 'head'], ['upper', 'torso']]
    .map(([limb, box]) => ({ d: depth(w, c[limb], BOX[box]), what: `${side} ${limb === 'upper' ? 'upper arm' : 'forearm'} in the ${box}` }))
    .reduce((x, y) => (y.d > x.d ? y : x));
}

module.exports = { SHOULDER, BACK, armDir, shoulder, BOX, CAPSULE, R_LIMB, boxDist, segDist, depth, overlap };
