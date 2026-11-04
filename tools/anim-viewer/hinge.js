/* Turns elbows and knees into true hinges, the only bend the game can draw on a limb (client.render.NpcJoints bends about
 * the limb's x axis). AnimationsPlus' elbows also twist and swing freely (its forearms are rigid boxes in Figura), which in
 * game leaves the held weapon (following the whole elbow) at odds with the drawn forearm. Per frame, the two-bone swivel:
 * the shoulder and elbow stay put, the upper limb turns about its own length so the lower limb lies in the hinge plane, the
 * joint bends by a pure hinge angle to point the lower limb where it was, and the weapon bone takes up the remainder so the
 * weapon stays exactly where the animator put it. Rig space (gecko/rig.js), Blockbench values in and out (import/parts.js). */
'use strict';
const { animationJson } = require('./gecko/write.js');
const { samplePose, worldBones, point } = require('./gecko/sample.js');
const { RIG } = require('./gecko/rig.js');
const { worldToClip } = require('./import/parts.js');

const LIMBS = [
  { upper: 'right_arm', joint: 'right_elbow', end: 'weapon_r', shoulder: [4.5, 22.5, 0], knee: [6, 18, 0], foot: [6, 12, 0] },
  { upper: 'left_arm', joint: 'left_elbow', end: 'weapon_l', shoulder: [-4.5, 22.5, 0], knee: [-6, 18, 0], foot: [-6, 12, 0] },
  { upper: 'right_leg', joint: 'right_knee', end: null, shoulder: [1.9, 12, 0], knee: [1.9, 6, 0], foot: [1.9, 0, 0] },
  { upper: 'left_leg', joint: 'left_knee', end: null, shoulder: [-1.9, 12, 0], knee: [-1.9, 6, 0], foot: [-1.9, 0, 0] },
];
const PIVOT = Object.fromEntries(RIG.map((b) => [b.name, b.pivot]));
const PARENT = Object.fromEntries(RIG.map((b) => [b.name, b.parent]));
const sub = (a, b) => a.map((x, i) => x - b[i]), add = (a, b) => a.map((x, i) => x + b[i]);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(...a) || 1; return a.map((x) => x / l); };
const apply = (R, v) => [0, 1, 2].map((r) => R[r * 3] * v[0] + R[r * 3 + 1] * v[1] + R[r * 3 + 2] * v[2]);
const mul3 = (a, b) => { const o = []; for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) o.push(a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]); return o; };
/** Rotation matrix about a unit axis by an angle (radians). */
function axisAngle(u, a) {
  const c = Math.cos(a), s = Math.sin(a), t = 1 - c, [x, y, z] = u;
  return [t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x, t * x * z - s * y, t * y * z + s * x, t * z * z + c];
}
const wrap = (a) => a - 2 * Math.PI * Math.round(a / (2 * Math.PI));

/** One limb at one frame: new world transforms for the upper limb and the joint; phi carries the swivel between frames. */
function solve(w, limb, state) {
  const A = w[limb.upper], J = w[limb.joint];
  const S = point(A, limb.shoulder), E = point(A, limb.knee), H = point(J, limb.foot);
  const u = norm(sub(E, S)), d = norm(sub(H, E));
  const x0 = apply(A.R, [1, 0, 0]);
  const par = dot(x0, u), xp = sub(x0, u.map((v) => v * par)), xq = cross(u, xp);
  // hinge axis after a swivel phi: x(phi) = par u + xp cos phi + xq sin phi; want it square to the lower limb
  const C = par * dot(u, d), Ac = dot(xp, d), Bs = dot(xq, d), Rm = Math.hypot(Ac, Bs);
  const hingeFor = (ph) => {
    const Rt = axisAngle(u, ph), A2 = { R: mul3(Rt, A.R), p: add(apply(Rt, sub(A.p, S)), S) };
    const x = apply(A2.R, [1, 0, 0]), f0 = apply(A2.R, sub(limb.foot, limb.knee));
    const fp = norm(sub(f0, x.map((v) => v * dot(f0, x)))), dp = norm(sub(d, x.map((v) => v * dot(d, x))));
    return { phi: ph, A2, theta: Math.atan2(dot(cross(fp, dp), x), dot(fp, dp)) };
  };
  let pick;
  if (Rm < 1e-9 || Math.abs(C) > Rm || Math.abs(dot(norm(sub(E, S)), d)) > 0.999) pick = hingeFor(state.phi);   // straight: keep the swivel
  else {
    const base = Math.atan2(Bs, Ac), off = Math.acos(-C / Rm);
    const cands = [base + off, base - off].map((c) => hingeFor(state.phi + wrap(c - state.phi)));
    // anatomy first: elbows fold forward (positive turn about x), knees back; then the swivel nearest the last frame
    const ok = (h) => (limb.upper.endsWith('arm') ? h.theta >= -0.05 : h.theta <= 0.05);
    const good = cands.filter(ok);
    pick = (good.length ? good : cands).reduce((b, h) => (Math.abs(h.phi - state.phi) < Math.abs(b.phi - state.phi) ? h : b));
  }
  state.phi = pick.phi;
  const A2 = pick.A2, theta = pick.theta;
  const Rl = axisAngle([1, 0, 0], theta), kp = limb.knee;   // local turn of the joint about its pivot
  const local = { R: Rl, p: sub(kp, apply(Rl, kp)) };
  const J2 = { R: mul3(A2.R, local.R), p: add(apply(A2.R, local.p), A2.p) };
  return { [limb.upper]: A2, [limb.joint]: J2 };
}

/**
 * Human ranges of twist, the turn about a limb's own length (degrees; the swing-twist split about the bone's rest y axis):
 * the upper arm against the body (humeral rotation, about 70° internal to 90° external), the end bone (the hand and what it
 * holds) against the lower limb (forearm pronation and supination, about 85-90°), the thigh against the hips. untwist aims
 * TWIST_MARGIN under them, so the smoothing and resampling after it stay inside, and holds hard at TWIST_HARD under.
 */
const TWIST = { arm: 95, end: 95, leg: 45 };
const TWIST_MARGIN = 12, TWIST_HARD = 3;
/** untwist's grids (degrees, each a full turn): swivels about the shoulder-to-hand line and rolls of the end bone about its
 *  own axis; the fastest either may turn (degrees per second: 1200°/s, 10° in 1/120 s, so on top of the fastest turn the
 *  animation has, about 51° in 1/120 s, a limb stays under the 60° that would read as a snap); and its weights: per degree²
 *  past the aim, and again past the hard limit, per degree² of swivel (the animator's elbow) and of roll (the animator's
 *  grip), per (degree per 60th of a second)² of change. */
const SWIVEL_STEP = 2.5, ROLL_STEP = 5, SWIVEL_RATE = 1200, ROLL_RATE = 1200;
const W_PAST = 1, W_HARD = 1e4, W_SWIVEL = 0.002, W_ROLL = 0.004, W_TURN = 0.2;
/** One full turn of angles, -180 up to 180 less a step. */
const fullTurn = (step) => Array.from({ length: Math.round(360 / step) }, (_, i) => -180 + i * step);
const SWIVELS = fullTurn(SWIVEL_STEP), ROLLS = fullTurn(ROLL_STEP);
const COARSE = fullTurn(2 * SWIVEL_STEP), ROLL_NEAR = 45;   // swivels tried for each roll: within 45° of the first pick
const COARSE_SWIVELS = fullTurn(10);   // untwist's first look at an arm's anatomy: every 10th degree
const apart = (a, b) => Math.abs(((((a - b) % 360) + 540) % 360) - 180);   // degrees between two angles
/** Turns v about unit axis n by an angle with cosine c and sine s (Rodrigues). */
const turnVec = (v, n, c, s) => { const k = dot(n, v) * (1 - c), x = cross(n, v); return [0, 1, 2].map((i) => v[i] * c + x[i] * s + n[i] * k); };
const turnBy = (v, n, deg) => turnVec(v, n, Math.cos((deg * Math.PI) / 180), Math.sin((deg * Math.PI) / 180));
const col = (R, j) => [R[j], R[3 + j], R[6 + j]];
/** Twist about y (degrees, ±180) of the turn taking frame (columns x0, z0) to frame (x1, z1), from the entries of
 *  F0ᵀ F1 that carry it: atan2(m02 − m20, m00 + m22), whose length is 2 cos²(swing / 2). Within POLE of a half-turn swing
 *  (the limb pointing back along its rest axis) the twist is not defined: NaN. */
const POLE = 3;
const POLE_LEN = 2 * Math.cos((((180 - POLE) / 2) * Math.PI) / 180) ** 2;
function twistOf(x0, z0, x1, z1) {
  const s = dot(x0, z1) - dot(z0, x1), c = dot(x0, x1) + dot(z0, z1);
  return Math.hypot(s, c) < POLE_LEN ? NaN : (Math.atan2(s, c) * 180) / Math.PI;
}

/**
 * The limb's twists for one frame g at a swivel d (degrees) about its shoulder-to-hand line, the end bone rolled to columns
 * e = [x, z] (default its own): [upper against its parent, end against the joint (if the limb holds one)]. Turning the
 * whole limb about that line keeps the shoulder, the hand, the hinge's bend and the end bone's world where they are; only
 * the elbow's place on its circle, and so the twists, change.
 */
function swivelTwists(g, d, e = g.w) {
  // each dot product twistOf takes is a sum of a constant, a cosine and a sine of the swivel: worked out once per frame
  // (and per end-bone roll), then each swivel costs a handful of products
  if (!g.tu) g.tu = twistTerms(g.p0, g.p2, g.a0, g.a2, g.n, false);
  if (e && g.teFor !== e) { g.te = twistTerms(e[0], e[1], g.j0, g.j2, g.n, true); g.teFor = e; }
  const c = Math.cos((d * Math.PI) / 180), sn = Math.sin((d * Math.PI) / 180);
  const out = [twistAt(g.tu, c, sn)];
  if (e) out.push(twistAt(g.te, c, sn));
  return out;
}
/** dot(u, v turned about n by d) = k0 + k1 cos d + k2 sin d: [k0, k1, k2]. */
const turnedDot = (u, v, n) => { const k = dot(n, v), par = n.map((x) => x * k); return [dot(u, par), dot(u, sub(v, par)), dot(u, cross(n, v))]; };
/** twistOf's two sums as turnedDot terms: from fixed columns (x0, z0) to turned (x1, z1), or (turned: the end bone)
 *  from turned (x1, z1) to fixed (x0, z0). [s terms, c terms]. */
function twistTerms(x0, z0, x1, z1, n, turned) {
  const xz = turnedDot(x0, z1, n), zx = turnedDot(z0, x1, n), xx = turnedDot(x0, x1, n), zz = turnedDot(z0, z1, n);
  // turned: twistOf(t(x1), t(z1), x0, z0): s = x1t·z0 − z1t·x0 = zx − xz
  const sgn = turned ? -1 : 1;
  return [[0, 1, 2].map((i) => sgn * (xz[i] - zx[i])), [0, 1, 2].map((i) => xx[i] + zz[i])];
}
function twistAt([S, C], c, sn) {
  const s = S[0] + S[1] * c + S[2] * sn, k = C[0] + C[1] * c + C[2] * sn;
  return Math.hypot(s, k) < POLE_LEN ? NaN : (Math.atan2(s, k) * 180) / Math.PI;
}

/** The limb's twist limits: [upper, end]. */
const limitsOf = (limb) => [limb.upper.endsWith('arm') ? TWIST.arm : TWIST.leg, TWIST.end];

/**
 * Shoulder range: where an upper arm may point in torso space (the body bone's frame; the arm's long axis, rest -y), from
 * the clinical ranges (flexion and abduction 180°, extension 60°, horizontal adduction 45° past straight ahead): not into
 * the cap of 60° about the direction 30° above straight back (BACK_CAP: straight up and 60° behind hanging lie on its rim),
 * nor into the cap of 45° about straight across the chest. Measured as the figure each equals in its own plane: back, 120°
 * less the angle to BACK_CAP (an arm swung straight back: its extension); across, 90° less the angle to straight across
 * (a level arm: how far past straight ahead it reaches). The hard limit is the range plus SHOULDER_SLACK (the tables
 * differ by about that much, and the rig has no shoulder blade to glide): untwist aims TWIST_MARGIN under it and holds
 * at TWIST_HARD under, as for twist.
 */
const SHOULDER = { back: 60, across: 45 }, SHOULDER_SLACK = 10;
const BACK_CAP = [0, 0.5, Math.sqrt(3) / 2];
/**
 * Body clearance: an arm's forearm and fist, and its upper arm's lower half, kept out of the torso box (8×12×4 px, under
 * the body bone) and the forearm out of the head box (8×8×8): capsules LIMB_R round (the arms are 4 px thick) along the
 * arm box's axis (x ±6, z 0), each stopping LIMB_R short of where its part ends so its round end lands there (the forearm
 * from the elbow pivot to y 14, the fist's end at 12; the upper arm from y 19 to the elbow pivot, its middle at 21). At most
 * DEPTH px may sink in. Weighed with the angles as PX_DEG degrees a pixel (a pixel at the fist, 6 px from the elbow, is
 * some 10° of the forearm's turn).
 */
const BODY_BOXES = { torso: { bone: 'body', min: [-4, 12, -2], max: [4, 24, 2] }, head: { bone: 'head', min: [-4, 24, -4], max: [4, 32, 4] } };
const LIMB_R = 2, DEPTH = 0.5, PX_DEG = 10;
/** Arm capsules in the arm's rest frame, per side (s: +1 right, -1 left): [bone, a, b, boxes]. */
const armCapsules = (limb, s) => [
  [limb.joint, [6 * s, 18, 0], [6 * s, 14, 0], ['torso', 'head']],
  [limb.upper, [6 * s, 19, 0], [6 * s, 18, 0], ['torso']],
];
const isArm = (limb) => limb.upper.endsWith('arm');
/** Signed distance (px) from a point in a box's frame to the box: negative inside. */
function boxDist(q, min, max) {
  let out = 0, inMax = -Infinity;
  for (let i = 0; i < 3; i++) {
    const d = Math.abs(q[i] - (min[i] + max[i]) / 2) - (max[i] - min[i]) / 2;
    if (d > 0) out += d * d;
    if (d > inMax) inMax = d;
  }
  return out > 0 ? Math.sqrt(out) : Math.min(inMax, 0);
}
/** The least signed distance from segment a-b (in the box's frame) to the box: convex along it, so a golden-section
 *  search (to some 0.01 px on a 4 px segment). */
const GOLD = (Math.sqrt(5) - 1) / 2;
function segBoxDist(a, b, box) {
  const q = [0, 0, 0];
  const at = (f) => { for (let i = 0; i < 3; i++) q[i] = a[i] + (b[i] - a[i]) * f; return boxDist(q, box.min, box.max); };
  let lo = 0, hi = 1, x1 = hi - GOLD, x2 = GOLD, f1 = at(x1), f2 = at(x2);
  for (let k = 0; k < 12; k++) {
    if (f1 <= f2) { hi = x2; x2 = x1; f2 = f1; x1 = hi - GOLD * (hi - lo); f1 = at(x1); }
    else { lo = x1; x1 = x2; f1 = f2; x2 = lo + GOLD * (hi - lo); f2 = at(x2); }
  }
  return Math.min(at(0), at(1), f1, f2);
}
/** Bᵀ v: a world vector in a bone's rest frame. */
const into = (W, v) => [W.R[0] * v[0] + W.R[3] * v[1] + W.R[6] * v[2], W.R[1] * v[0] + W.R[4] * v[1] + W.R[7] * v[2], W.R[2] * v[0] + W.R[5] * v[1] + W.R[8] * v[2]];
/** v split for a turn by d about the unit axis n: v(d) = v∥ + v⊥ cos d + (n × v) sin d; returns [v∥, v⊥, n × v]. */
const splitAbout = (v, n) => { const k = dot(n, v), par = n.map((x) => x * k); return [par, sub(v, par), cross(n, v)]; };

/** An arm at one frame, in the world, for anatomyPrep: its side (s), shoulder S, unit shoulder-to-hand line n (the swivel's
 *  axis), the body bone B, its long axis (y) and its capsules' ends with the boxes each is judged against. */
function armFrame(f, limb) {
  const s = limb.shoulder[0] > 0 ? 1 : -1, S = point(f[limb.upper], limb.shoulder);
  return {
    s, S, n: norm(sub(point(f[limb.joint], limb.foot), S)), B: f[PARENT[limb.upper]], ay: col(f[limb.upper].R, 1),
    segs: armCapsules(limb, s).flatMap(([bone, a, b, boxes]) => boxes.map((k) => ({ box: BODY_BOXES[k], W: f[BODY_BOXES[k].bone], a: point(f[bone], a), b: point(f[bone], b) }))),
  };
}
/** Depths more than this far clear (degrees as PX_DEG a pixel) are not worked out exactly (anatomyPast). */
const CLEAR = 10;
/**
 * An arm (armFrame) ready to be judged at any swivel d about its shoulder-to-hand line: its long axis (negated: where the
 * arm points) in the torso's frame, and each capsule's ends in its box's frame, as [c0, c1, c2] with value(d) = c0 +
 * c1 cos d + c2 sin d. A capsule that stays clear of its box whatever the swivel (each end's circle about the line kept off
 * by more than CLEAR) carries that bound (far).
 */
function anatomyPrep(arm) {
  const dir = splitAbout(arm.ay, arm.n).map((v) => into(arm.B, v).map((x) => -x));
  const clear = LIMB_R - DEPTH + CLEAR / PX_DEG;
  const segs = arm.segs.map((g) => {
    const s0 = into(g.W, sub(arm.S, g.W.p));
    const end = (p) => { const [a0, a1, a2] = splitAbout(sub(p, arm.S), arm.n).map((v) => into(g.W, v)); return [add(s0, a0), a1, a2]; };
    const a = end(g.a), b = end(g.b);
    const far = Math.min(boxDist(a[0], g.box.min, g.box.max), boxDist(b[0], g.box.min, g.box.max)) - Math.hypot(...sub(a[0], b[0])) / 2 - Math.max(Math.hypot(...a[1]), Math.hypot(...b[1]));
    return { box: g.box, a, b, far: far > clear ? far : null };
  });
  return { s: arm.s, dir, segs };
}
/**
 * The most an arm (anatomyPrep) can be past each of its anatomy's limits at any swivel, as anatomyPast lists them (a
 * bound: the shoulder's exactly, from the circle the arm's direction runs round; a capsule not known to stay clear (far)
 * as touching its box right through).
 */
function anatomyWorst(p) {
  const [c0, c1, c2] = p.dir;
  // the least angle (degrees) between the arm and t over the circle: dot = c0·t + (c1·t) cos d + (c2·t) sin d
  const least = (t) => {
    const l = Math.hypot(...c0) ** 2 + Math.hypot(...c1) ** 2, top = (dot(c0, t) + Math.hypot(dot(c1, t), dot(c2, t))) / Math.sqrt(l || 1);
    return (Math.acos(Math.max(-1, Math.min(1, top))) * 180) / Math.PI;
  };
  const out = [120 - least(BACK_CAP) - SHOULDER.back - SHOULDER_SLACK, 90 - least([-p.s, 0, 0]) - SHOULDER.across - SHOULDER_SLACK];
  for (const g of p.segs) out.push(g.far === null ? Infinity : (LIMB_R - g.far - DEPTH) * PX_DEG);
  return out;
}
/**
 * How far an arm (anatomyPrep) is past its anatomy at a swivel of d degrees: [back, across] past the shoulder's hard limit
 * (degrees), then each capsule's depth in its box past DEPTH (as PX_DEG degrees a pixel). Negative is within. A capsule
 * well clear (by more than CLEAR) is reported at a bound: at least as deep as it is, and still clear by CLEAR.
 */
function anatomyPast(p, d = 0) {
  const c = Math.cos((d * Math.PI) / 180), sn = Math.sin((d * Math.PI) / 180);
  const at = (k) => [k[0][0] + k[1][0] * c + k[2][0] * sn, k[0][1] + k[1][1] * c + k[2][1] * sn, k[0][2] + k[1][2] * c + k[2][2] * sn];
  const dir = at(p.dir), l = Math.hypot(dir[0], dir[1], dir[2]) || 1;
  const ang = (x) => (Math.acos(Math.max(-1, Math.min(1, x / l))) * 180) / Math.PI;
  const out = [120 - ang(dot(dir, BACK_CAP)) - SHOULDER.back - SHOULDER_SLACK, 90 - ang(-p.s * dir[0]) - SHOULDER.across - SHOULDER_SLACK];
  const clear = LIMB_R - DEPTH + CLEAR / PX_DEG;
  for (const g of p.segs) {
    let dist = g.far;
    if (dist === null) {
      const a = at(g.a), b = at(g.b), mid = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2];
      const bound = boxDist(mid, g.box.min, g.box.max) - Math.hypot(b[0] - a[0], b[1] - a[1], b[2] - a[2]) / 2;
      dist = bound > clear ? bound : segBoxDist(a, b, g.box);
    }
    out.push((LIMB_R - dist - DEPTH) * PX_DEG);
  }
  return out;
}
/** How far under each of anatomyPast's limits untwist aims and holds (degrees; depths as PX_DEG a pixel): the shoulder as
 *  twist (TWIST_MARGIN, TWIST_HARD), depths at 0.5 and 0.2 px under (a limb resting against the body, 0.5 px under, costs
 *  nothing: the arm hangs touching the torso's side). */
const ANAT_MARGIN = [TWIST_MARGIN, TWIST_MARGIN, 5], ANAT_HARD = [TWIST_HARD, TWIST_HARD, 2];
const marginOf = (m, i) => m[Math.min(i, 2)];
/** Past the hard limit, per degree²: a hundredth of twist's (W_HARD), so where a swivel cannot meet both the arm keeps its
 *  twist (a wrung arm reads as broken) and gives way here. */
const W_HARD_ANAT = W_HARD / 100;
/** The cost of being past: per degree² past the aim and again past the hard limit. */
const pastCost = (xs) => xs.reduce((e, x, i) => e + W_PAST * Math.max(0, x + marginOf(ANAT_MARGIN, i)) ** 2 + W_HARD_ANAT * Math.max(0, x + marginOf(ANAT_HARD, i)) ** 2, 0);
/** Within the aim everywhere: nothing to pay. */
const anatomyFits = (xs) => xs.every((x, i) => x <= -marginOf(ANAT_MARGIN, i));

/**
 * The least-cost path through a grid of values over the frames: cost[i][k] for value values[k] at frame i (weighed by the
 * time the frame stands for), plus W_TURN per (change per 60th of a second)², changing by at most `rate` degrees per
 * second (or one step per sample 0.2 ms or more apart, whichever is more); scale[i] (0..1) counts a change into frame i at that share (a swivel of a near-straight limb hardly moves the
 * elbow). A loop ends on the value it starts with; a single frame takes its cheapest value. Where no value can be reached
 * at a finite cost, every frame takes 0 (no turn). Returns the value per frame. Values are one full turn of angles
 * (fullTurn), so a change may go round past ±180°.
 */
function cheapest(cost, values, times, loop, scale = null, rate = SWIVEL_RATE) {
  const K = values.length, step = 360 / K, none = cost.map(() => 0);
  if (cost.length === 1) { const c = cost[0], m = Math.min(...c); return m < Infinity ? [values[c.indexOf(m)]] : none; }
  const run = (pin) => {
    let acc = cost[0].map((c, k) => (pin === undefined || k === pin ? c : Infinity));
    const from = [null];
    for (let i = 1; i < cost.length; i++) {
      const dt = Math.max(1e-6, times[i] - times[i - 1]), sc = scale ? scale[i] : 1;
      // at least a step per sample, so the densest samples (near the pole, a quarter to half a millisecond apart) still let
      // it move; samples closer than 0.2 ms (a key and its twin a sliver apart) hold it
      const reach = dt < 0.0002 ? 0 : Math.min(Math.floor((K - 1) / 2), Math.max(1, Math.floor((rate * dt) / step + 1e-9)));
      const next = new Array(K), back = new Array(K);
      for (let k = 0; k < K; k++) {
        let best = Infinity, bj = k;
        for (let o = -reach; o <= reach; o++) {   // round the circle: values are angles
          const j = (k - o + K) % K, r = (((o * step) / 60) / dt) * sc, v = acc[j] + W_TURN * r * r * dt * 60;
          if (v < best) { best = v; bj = j; }
        }
        next[k] = best + cost[i][k] * dt * 60; back[k] = bj;
      }
      acc = next; from.push(back);
    }
    if (pin !== undefined) acc = acc.map((v, k) => (k === pin ? v : Infinity));
    if (Math.min(...acc) === Infinity) return null;
    let k = acc.indexOf(Math.min(...acc));
    const pick = new Array(cost.length);
    for (let i = cost.length - 1; i >= 0; i--) { pick[i] = k; if (i) k = from[i][k]; }
    return pick;
  };
  let pick = run();
  if (pick && loop) pick = run(pick[pick.length - 1]);   // a loop starts where it ends
  return pick ? pick.map((k) => values[k]) : none;
}

/**
 * Keeps each limb's twists within human range (TWIST). Per frame the limb turns as a whole about its shoulder-to-hand line
 * (the swivel a real arm has: the elbow moves round its circle), which keeps the shoulder, the hand, the elbow's bend and
 * the end bone (the weapon) where they are, and only shares the twist out between the upper limb and the end bone. The
 * swivels are chosen together over the clip (cheapest): no twist past the aim, as little swivel as will do, no needless
 * quick turns. Where no swivel is enough (the arm and wrist together twisted past both ranges) and opts.roll names the end
 * bones' own axes ({ weapon_r: axis, ... }, about their pivots), the end bone also rolls about that axis, its direction and
 * place kept, except at times in opts.keep ({ weapon_r: [from, to] } s: where its roll is set, such as an edge leading at
 * the hit). With opts.anatomy the swivels also keep each arm within the shoulder's range and out of the body (anatomyPast,
 * aimed ANAT_MARGIN and held ANAT_HARD under its limits, giving way to twist where the two cannot both be met).
 * Limbs already within range, less a 5° margin (and arms within their anatomy's aim), are left as they are. frames: world
 * bones per time (worldBones); returns { frames, changed }.
 */
function untwist(frames, times, limbs, loop, opts = {}) {
  let out = frames, changed = false;
  for (const limb of limbs) {
    if (!frames[0][limb.upper] || !frames[0][limb.joint] || !frames[0][PARENT[limb.upper]]) continue;
    const lim = limitsOf(limb), end = limb.end && frames[0][limb.end] ? limb.end : null;
    const anat = opts.anatomy && isArm(limb) && frames[0].body && frames[0].head;
    const geo = frames.map((f) => {
      const A = f[limb.upper], J = f[limb.joint], P = f[PARENT[limb.upper]];
      const S = point(A, limb.shoulder), E = point(A, limb.knee), H = point(J, limb.foot), n = norm(sub(H, S)), u = sub(E, S);
      // how far the elbow swings out when the limb swivels: its distance from the shoulder-to-hand line, as a share of the
      // upper limb's length (0 when straight)
      const off = Math.hypot(...sub(u, n.map((x) => x * dot(u, n)))) / (Math.hypot(...u) || 1);
      const g = { S, n, out: Math.max(0.05, off), p0: col(P.R, 0), p2: col(P.R, 2), a0: col(A.R, 0), a2: col(A.R, 2), j0: col(J.R, 0), j2: col(J.R, 2) };
      if (end) g.w = [col(f[end].R, 0), col(f[end].R, 2)];
      if (anat) g.anat = g.prep = anatomyPrep(armFrame(f, limb));
      return g;
    });
    const pastBy = (tw, m) => tw.reduce((e, x, i) => e + (Number.isNaN(x) ? 0 : Math.max(0, Math.abs(x) - lim[i] + m) ** 2), 0);
    const tame = (tw) => tw.every((x, i) => Number.isNaN(x) || Math.abs(x) <= lim[i] - 5);
    // an arm's anatomy (opts.anatomy): within the shoulder's range and out of the body, inside the aim
    const fit = (g) => !g.anat || anatomyFits(anatomyPast(g.anat));
    const tameAsIs = geo.every((g) => tame(swivelTwists(g, 0)));
    if (tameAsIs && geo.every(fit)) continue;
    // a frame costs nothing for its arm's anatomy where it stays within the aim at every swivel: so where anatomyWorst bounds
    // it there, or where every 10th degree of swivel stays inside the aim by a further 5° (between them the arm's direction
    // moves at most 5°, and a capsule's end, at most some 5 px off the line, under half a pixel). Nor where no swivel brings
    // it within the hard limits without wringing the upper arm past its own (every 10th degree past one or the other by 5°
    // more): the hand itself is out of reach there, and swivelling for a part of the way would only change the clip
    for (const g of geo) {
      if (!g.anat) continue;
      if (anatomyFits(anatomyWorst(g.anat))) { g.anat = null; continue; }
      const coarse = COARSE_SWIVELS.map((d) => anatomyPast(g.anat, d));
      if (coarse.every((xs) => xs.every((x, i) => x <= -marginOf(ANAT_MARGIN, i) - 5))) { g.anat = null; continue; }
      const wrung = (d) => { const tw = swivelTwists(g, d, null)[0]; return Number.isNaN(tw) ? -Infinity : Math.abs(tw) - lim[0] + TWIST_HARD; };
      if (coarse.every((xs, k) => Math.max(wrung(COARSE_SWIVELS[k]), ...xs.map((x, i) => x + marginOf(ANAT_HARD, i))) > 5)) g.outOfReach = true;
    }
    // an arm holding the item (opts.held, default every one) and out of reach anywhere is left to its twist alone:
    // swivelling it for part of the clip would not bring it within, and every swivel strays the weapon's path a little
    // between keys (END_STRAY) and adds keys, enough to move a swing's timing measures. A free hand swivels where it can
    const holds = !opts.held || opts.held.includes(end);
    if (holds && geo.some((g) => g.outOfReach)) for (const g of geo) g.anat = null;
    if (tameAsIs && !geo.some((g) => g.anat)) continue;   // nothing left to swivel for
    const w0 = geo.map((g) => g.w);
    // each frame's anatomy cost at every swivel, worked out once (the roll search below asks again)
    const anatCost = (g, d) => {
      if (!g.anat) return 0;
      if (!g.anatCost) g.anatCost = new Float64Array(SWIVELS.length).fill(NaN);
      const k = Math.round((d + 180) / SWIVEL_STEP) % SWIVELS.length;
      if (Number.isNaN(g.anatCost[k])) g.anatCost[k] = pastCost(anatomyPast(g.anat, SWIVELS[k]));
      return g.anatCost[k];
    };
    const outs = geo.map((g) => g.out);
    /** The swivels (and end-bone rolls) over the clip, weighing the arm's anatomy or not. */
    const solveLimb = (withAnat) => {
      geo.forEach((g, i) => { g.w = w0[i]; });
      const swivelCost = (g, d, e) => {
        const tw = swivelTwists(g, d, e);
        // an undefined twist (at the pole) costs as much as one at the aim's edge (TWIST_MARGIN past it): never free, so
        // no swivel hides a twist there, but no worse than a twist the limb can show
        const pole = tw.filter((x) => Number.isNaN(x)).length * W_PAST * TWIST_MARGIN * TWIST_MARGIN;
        return W_PAST * pastBy(tw, TWIST_MARGIN) + W_HARD * pastBy(tw, TWIST_HARD) + pole + (withAnat ? anatCost(g, d) : 0) + W_SWIVEL * d * d * g.out * g.out;
      };
      let swivel = cheapest(geo.map((g) => SWIVELS.map((d) => swivelCost(g, d))), SWIVELS, times, loop, outs);
      let roll = geo.map(() => 0);
      const axis = end && opts.roll && opts.roll[end];
      if (axis && geo.some((g, i) => !tame(swivelTwists(g, swivel[i])))) {
        // the end bone's axis in the world, and its frame's x and z rolled about it by each roll
        const rolled = (g, i, r) => {
          const R = frames[i][end].R, ax = norm([0, 1, 2].map((j) => R[j * 3] * axis[0] + R[j * 3 + 1] * axis[1] + R[j * 3 + 2] * axis[2]));
          return g.w.map((v) => turnBy(v, ax, r));
        };
        const keep = opts.keep && opts.keep[end], kept = (i) => keep && times[i] >= keep[0] - 1e-9 && times[i] <= keep[1] + 1e-9;
        // each roll judged with the best swivel near the one already chosen (a far one may not be reachable in time)
        const rollCost = geo.map((g, i) => {
          const near = COARSE.filter((d) => apart(d, swivel[i]) <= ROLL_NEAR);
          return ROLLS.map((r) => {
            if (r && kept(i)) return Infinity;
            const e = rolled(g, i, r);
            return Math.min(...near.map((d) => swivelCost(g, d, e))) + W_ROLL * r * r;
          });
        });
        roll = cheapest(rollCost, ROLLS, times, loop, null, ROLL_RATE);
        geo.forEach((g, i) => { if (roll[i]) g.w = rolled(g, i, roll[i]); });
        swivel = cheapest(geo.map((g) => SWIVELS.map((d) => swivelCost(g, d))), SWIVELS, times, loop, outs);
      }
      return { swivel, roll, axis };
    };
    let sol = solveLimb(true);
    if (geo.some((g) => g.anat)) {
      // the arm's anatomy taken only where it is a gain: an arm holding the item brought within at every frame (else
      // swivelling it is no use, as above), a free hand made no worse past its limits anywhere than twist alone leaves it
      // (which leaves a limb within its twist range as it is)
      const plain = tameAsIs ? { swivel: geo.map(() => 0), roll: geo.map(() => 0), axis: null } : solveLimb(false);
      const worst = (sw, i) => Math.max(...anatomyPast(geo[i].prep, sw[i]));
      const gain = holds ? geo.every((g, i) => worst(sol.swivel, i) <= 0)
        : geo.every((g, i) => worst(sol.swivel, i) <= Math.max(0, worst(plain.swivel, i)) + 1e-6);
      if (!gain) sol = plain;
    }
    const { swivel, roll, axis } = sol;
    if (swivel.every((d) => d === 0) && roll.every((r) => r === 0)) continue;
    if (!changed) { out = frames.map((f) => ({ ...f })); changed = true; }
    out.forEach((f, i) => {
      if (roll[i]) f[end] = rollEnd(f[end], axis, PIVOT[end], roll[i]);   // about its pivot: the item's place and direction stay
      if (!swivel[i]) return;
      const R = axisAngle(geo[i].n, (swivel[i] * Math.PI) / 180), S = geo[i].S;
      const turn = (B) => ({ R: mul3(R, B.R), p: add(apply(R, sub(B.p, S)), S) });
      f[limb.upper] = turn(f[limb.upper]);
      f[limb.joint] = turn(f[limb.joint]);
    });
  }
  return { frames: out, changed };
}

/**
 * The clip with every elbow and knee a pure hinge; the weapon bones keep their world. Resampled at 30 fps plus the clip's own
 * key times, or (opts.rate) on an even grid at that rate only: Catmull-Rom curves need evenly spaced keys to stay smooth.
 * Every limb is kept within its twist range (untwist), unless opts.filter smooths the keys afterwards (see there).
 */
function hingeify(clip, opts = {}) {
  const bonesOf = (l) => [l.upper, l.joint, l.end].filter(Boolean);
  const moving = LIMBS.filter((l) => bonesOf(l).some((b) => clip.bones[b]));
  if (!moving.length) return clip;
  const anim = animationJson({ c: clip }).animations.c, L = clip.length;
  const held = Object.values(clip.bones).every((ch) => Object.values(ch).every((keys) => keys.every((k) => k.t === 0)));
  const set = new Set(held ? [0] : [0, L]);   // a held pose stays one key
  if (!held && opts.rate) { const n = Math.max(1, Math.round(L * opts.rate)); for (let i = 0; i <= n; i++) set.add(+((i * L) / n).toFixed(5)); }
  else if (!held) {
    for (let t = 0; t < L; t += 1 / 30) set.add(+t.toFixed(5));
    for (const l of moving) for (const b of bonesOf(l)) for (const ch of Object.values(clip.bones[b] || {})) for (const k of ch) set.add(k.t);
  }
  const times = [...set].filter((t) => t >= 0 && t <= L + 1e-9).sort((a, b) => a - b);
  const poses = times.map((t) => worldBones(samplePose(anim, Math.min(t, L))));
  const pass = (states) => poses.map((w) => {
    const f = Object.fromEntries(Object.entries(w));
    moving.forEach((l, i) => Object.assign(f, solve(w, l, states[i])));
    return f;
  });
  const states = moving.map(() => ({ phi: 0 }));
  let frames = pass(states);
  // a loop starts where it ends: solve again from the swivel the first pass finished on
  if (clip.loop === true) frames = pass(states.map((st) => ({ phi: st.phi })));
  // and every limb within its twist range: the swivel that turns the elbow's fold to the front without wringing the arm.
  // Not under a filter: smoothing each bone's keys on its own would undo the share-out between arm and weapon, so a
  // filtered clip has its twist limited once it is finished (limitTwist)
  if (!opts.filter) frames = untwist(frames, times, moving, clip.loop === true || held).frames;
  const out = worldToClip(frames, times, L, clip.loop);
  const c = JSON.parse(JSON.stringify(clip));
  // even-grid resamples carry every bone (one clock for the whole swing); otherwise only the limbs change
  const replace = opts.rate ? Object.keys({ ...clip.bones, ...out.bones }) : moving.flatMap(bonesOf);
  for (const b of replace) {
    if (out.bones[b]) c.bones[b] = out.bones[b];
    else delete c.bones[b];
  }
  if (opts.filter) for (const ch of Object.values(c.bones)) for (const keys of Object.values(ch)) lowPass(keys);
  return c;
}

/**
 * A 5-tap binomial low-pass over evenly spaced keys (at 60 fps, a 33 ms window): rounds the corners a sparse source swing
 * has at its keys. Euler values are unwrapped first; the first and last keys stay exact, so the swing starts and ends where
 * it did.
 */
function lowPass(keys) {
  if (keys.length < 5) return;
  const v = keys.map((k) => k.v.slice());
  for (let i = 1; i < v.length; i++) for (let j = 0; j < 3; j++) v[i][j] = v[i - 1][j] + (((v[i][j] - v[i - 1][j] + 180) % 360) + 360) % 360 - 180;
  const W = [1, 4, 6, 4, 1];
  for (let i = 1; i < v.length - 1; i++) {
    keys[i].v = [0, 1, 2].map((j) => {
      let s = 0, n = 0;
      for (let d = -2; d <= 2; d++) { const k = Math.min(v.length - 1, Math.max(0, i + d)); s += W[d + 2] * v[k][j]; n += W[d + 2]; }
      return s / n;
    });
  }
}

/** A clip's moving limbs and its poses at every key and at least every 1/120 s between them (limitTwist, peakTwist);
 *  isKey[i] tells the keys from the samples between. */
function sampled(clip) {
  const moving = LIMBS.filter((l) => clip.bones[l.upper] || clip.bones[l.joint] || (l.end && clip.bones[l.end]));
  const anim = animationJson({ c: clip }).animations.c, L = clip.length;
  const set = new Set([0, L]);
  for (const ch of Object.values(clip.bones)) for (const keys of Object.values(ch)) for (const k of keys) set.add(k.t);
  const keys = [...set].filter((t) => t >= 0 && t <= L + 1e-9).sort((a, b) => a - b).filter((t, i, a) => !i || t - a[i - 1] > 1e-6);
  // and between keys, every 1/120 s or less: a fast turn can pass through a twist between two keys that have none
  const times = [], isKey = [];
  for (let i = 0; i < keys.length; i++) {
    if (i) {
      const n = Math.ceil((keys[i] - keys[i - 1]) * 120 - 1e-6);
      for (let j = 1; j < n; j++) { times.push(keys[i - 1] + ((keys[i] - keys[i - 1]) * j) / n); isKey.push(false); }
    }
    times.push(keys[i]); isKey.push(true);
  }
  const held = Object.values(clip.bones).every((ch) => Object.values(ch).every((ks) => ks.every((k) => k.t === 0)));
  const pose = (t) => worldBones(samplePose(anim, Math.min(t, L)));
  let poses = times.map(pose);
  // and every 0.5 ms where an upper limb comes within NEAR_POLE of a half-turn swing: there its twist swings round fast
  // however little the limb moves, and the swivel has to follow it closely
  const near = (w) => moving.some((l) => w[l.upper] && w[PARENT[l.upper]] && swingOf(w[PARENT[l.upper]].R, w[l.upper].R) > 180 - NEAR_POLE);
  const T = [], K = [], P = [];
  for (let i = 0; i < times.length; i++) {
    if (i && (near(poses[i - 1]) || near(poses[i]))) {
      const n = Math.ceil((times[i] - times[i - 1]) / 0.0005 - 1e-6);
      for (let j = 1; j < n; j++) { const t = times[i - 1] + ((times[i] - times[i - 1]) * j) / n; T.push(t); K.push(false); P.push(pose(t)); }
    }
    T.push(times[i]); K.push(isKey[i]); P.push(poses[i]);
  }
  return { moving, times: T, isKey: K, held, poses: P };
}

/** Within this many degrees of a half-turn swing, sampled more densely (sampled). */
const NEAR_POLE = 20;
/** How far a bone (frame B) swings off its parent's (frame A) y axis, degrees. */
const swingOf = (A, B) => (Math.acos(Math.max(-1, Math.min(1, dot(col(A, 1), col(B, 1))))) * 180) / Math.PI;

/** The most any limb of `limbs` twists past its range (TWIST) over these poses, degrees (0 or less when all are within). */
function pastOf(poses, limbs) {
  let past = -Infinity;
  for (const f of poses) for (const l of limbs) {
    if (!f[l.upper] || !f[l.joint] || !f[PARENT[l.upper]]) continue;
    const P = f[PARENT[l.upper]].R, A = f[l.upper].R, lim = limitsOf(l);
    const tu = twistOf(col(P, 0), col(P, 2), col(A, 0), col(A, 2));   // NaN (at the pole) is not judged
    if (!Number.isNaN(tu)) past = Math.max(past, Math.abs(tu) - lim[0]);
    if (l.end && f[l.end]) {
      const J = f[l.joint].R, W = f[l.end].R, te = twistOf(col(J, 0), col(J, 2), col(W, 0), col(W, 2));
      if (!Number.isNaN(te)) past = Math.max(past, Math.abs(te) - lim[1]);
    }
  }
  return past;
}

/** The most an arm is past its anatomy at a frame (anatomyPast; -Infinity for a leg or a rig without body and head). */
const anatomyOver = (f, l) => (isArm(l) && f.body && f.head ? Math.max(...anatomyPast(anatomyPrep(armFrame(f, l)))) : -Infinity);

/** How far written keys may let an end bone (a held weapon) stray between them from where it was solved: its pivot by
 *  0.25 px, its turn by 0.5° (a 1.5-block blade's tip by about 0.2 px more). */
const END_STRAY = { px: 0.25, deg: 0.5 };
/** How far an upper limb's keys may stray between them from its solved turn (degrees): keyed more densely there, Euler
 *  interpolation cannot take a turn the long way round. */
const UPPER_STRAY = 2;
/** The angle between two rotation matrices (degrees). */
function turnBetween(a, b) {
  const tr = [0, 1, 2].reduce((t, j) => t + dot(col(a, j), col(b, j)), 0);   // trace of aᵀb: 1 + 2 cos(angle)
  return Math.acos(Math.max(-1, Math.min(1, (tr - 1) / 2))) * (180 / Math.PI);
}
function strayed(a, b, pivot) {
  const pa = point(a, pivot), pb = point(b, pivot);
  return Math.hypot(...sub(pa, pb)) > END_STRAY.px || turnBetween(a.R, b.R) > END_STRAY.deg;
}

/**
 * A finished clip's limbs brought within their twist range (untwist), judged at every key and every 1/120 s between
 * (sampled). A limb it turns has its upper bone and end bone written at the clip's own key times (the joint keeps its
 * keys: a swivel leaves the bend alone), and at a time between only where the keys alone would pass out of range there or
 * let the upper limb (UPPER_STRAY) or a held item (END_STRAY) stray from the solved pose: Euler keys interpolate a turn
 * their own way, and can take it the long way round. So the clip keeps its timing and its weapon's path; every other bone, and every limb already in range, is left as it was. For a clip changed after
 * hingeify. opts as untwist's, and opts.held: the end bones that hold an item (default every one), the only ones kept
 * from straying; opts.preRoll: { bone: degrees } to roll an end bone about its opts.roll axis first (an edge put to lead),
 * exactly at every sample. With opts.anatomy the arms' swivels also keep them within the shoulder's range and out of the
 * body (untwist), and keys are added between where the written ones would carry an arm past its anatomy that the solved
 * pose is within. A swivel leaves the hand where it is, so a held weapon keeps its path: an arm the swivel cannot bring
 * in (the hand itself behind the back or against the body) is left so.
 */
function limitTwist(clip, opts = {}) {
  const s0 = sampled(clip), { moving, times, isKey, held } = s0;
  if (!moving.length) return clip;
  // an end bone rolled first (opts.preRoll: degrees about its opts.roll axis), in the world at every sample: so between its
  // keys it rolls exactly too, rather than as Euler keys rolled one by one would interpolate
  const pre = opts.preRoll || {};
  const poses = s0.poses.map((w) => {
    const f = { ...w };
    for (const [b, a] of Object.entries(pre)) if (a && f[b]) f[b] = rollEnd(f[b], opts.roll[b], PIVOT[b], a);
    return f;
  });
  const r = untwist(poses, times, moving, clip.loop === true || held, opts);
  const rolledFirst = (l) => l.end && pre[l.end];
  if (!r.changed && !moving.some(rolledFirst)) return clip;
  const turned = moving.filter((l) => rolledFirst(l) || !poses.every((w, i) => r.frames[i][l.upper] === w[l.upper] && (!l.end || r.frames[i][l.end] === w[l.end])));
  const use = isKey.slice();
  let c = clip;
  for (let pass = 0; pass < 6; pass++) {
    const idx = times.map((_, i) => i).filter((i) => use[i]);
    const out = worldToClip(idx.map((i) => r.frames[i]), idx.map((i) => times[i]), clip.length, clip.loop);
    c = JSON.parse(JSON.stringify(clip));
    for (const l of turned) for (const b of [l.upper, l.end].filter(Boolean)) {
      if (out.bones[b]) c.bones[b] = out.bones[b];
      else delete c.bones[b];
    }
    // the times between keys where the written keys stray from the solved pose: out of range though it is not, or the
    // end bone (the weapon) off its place by more than END_STRAY
    const anim = animationJson({ c }).animations.c;
    const more = times.map((t, i) => i).filter((i) => {
      if (use[i]) return false;
      const w = worldBones(samplePose(anim, Math.min(times[i], clip.length)));
      if (pastOf([w], turned) > -1 && pastOf([r.frames[i]], turned) <= -1) return true;
      if (opts.anatomy && turned.some((l) => anatomyOver(w, l) > -1 && anatomyOver(r.frames[i], l) <= -1)) return true;
      if (turned.some((l) => turnBetween(w[l.upper].R, r.frames[i][l.upper].R) > UPPER_STRAY)) return true;
      return turned.some((l) => l.end && w[l.end] && (!opts.held || opts.held.includes(l.end)) && strayed(w[l.end], r.frames[i][l.end], PIVOT[l.end]));
    });
    if (!more.length) break;
    for (const i of more) use[i] = true;
  }
  return startInReach(c, moving);
}

/** The most of a clip's start (s) that startInReach may hold. */
const START_HOLD = 0.05;
/**
 * A clip that starts out of reach: where a limb is still past its twist range in the first START_HOLD s after untwist (no
 * swivel within its rate can reach the start pose: an orc's shield bash begins with the arm overhead, wrung 2° past range),
 * every bone holds the first pose after that stretch from the clip's start. It lies inside the attack's fade-in (0.1 s), so
 * the swing seen is the same; a clip still out of range later is left as it is.
 */
function startInReach(clip, moving) {
  const anim = animationJson({ c: clip }).animations.c, L = clip.length;
  let last = -1;
  for (let t = 0; t <= Math.min(L, START_HOLD) + 1e-9; t += 0.001) if (pastOf([worldBones(samplePose(anim, t))], moving) > 0) last = t;
  if (last < 0 || last + 0.002 >= Math.min(L, START_HOLD)) return clip;
  const t0 = +(last + 0.002).toFixed(4), c = JSON.parse(JSON.stringify(clip));
  for (const ch of Object.values(c.bones)) for (const [kind, keys] of Object.entries(ch)) {
    const v = valueAt(keys, t0);
    ch[kind] = [{ t: 0, v: v.slice(), lerp: 'linear' }, { t: t0, v: v.slice(), lerp: 'linear' }, ...keys.filter((k) => k.t > t0 + 1e-6)];
  }
  return c;
}

/** A channel's value at t, linear between its keys (as written). */
function valueAt(keys, t) {
  if (t <= keys[0].t) return keys[0].v;
  for (let i = 1; i < keys.length; i++) if (t <= keys[i].t) {
    const a = keys[i - 1], b = keys[i], f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
    return a.v.map((x, j) => x + f * (b.v[j] - x));
  }
  return keys[keys.length - 1].v;
}

/** An end bone's world turned by `deg` degrees about its own axis (`axis`, in its frame) through its pivot. */
function rollEnd(W, axis, pivot, deg) {
  const ax = norm(apply(W.R, axis)), R = axisAngle(ax, (deg * Math.PI) / 180), pv = add(apply(W.R, pivot), W.p);
  return { R: mul3(R, W.R), p: add(apply(R, sub(W.p, pv)), pv) };
}

/** How far a clip's limbs twist (degrees, over its keys and every 1/120 s between): past, the most any twists past its range
 *  (TWIST; 0 when all are within it), and end, the most any end bone (a held weapon) twists against its joint; with
 *  `anatomy`, also anatomy, the most any arm is past its anatomy (anatomyPast; 0 when within). */
function peakTwist(clip, anatomy = false) {
  const { moving, poses } = sampled(clip);
  let end = 0;
  for (const f of poses) for (const l of moving) {
    if (l.end && f[l.end] && f[l.joint]) {
      const J = f[l.joint].R, W = f[l.end].R, te = twistOf(col(J, 0), col(J, 2), col(W, 0), col(W, 2));
      if (!Number.isNaN(te)) end = Math.max(end, Math.abs(te));
    }
  }
  let past = 0;
  if (anatomy) for (const f of poses) for (const l of moving) past = Math.max(past, anatomyOver(f, l));
  return { past: Math.max(0, pastOf(poses, moving)), end, anatomy: past };
}

module.exports = { hingeify, solve, lowPass, untwist, limitTwist, peakTwist, TWIST, LIMBS, PIVOT,
  _internal: { cheapest, twistOf, SWIVELS, SWIVEL_RATE } };
