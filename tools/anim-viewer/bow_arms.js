/* The archer's arms in the bow clips (bow_aim, bow_release), set by where the bow and its string must be (gecko/bow.js).
 * AnimationsPlus' bowR and Bow_ShootR keep their torso, head and legs (the turn side-on to the target); their arms are
 * tuned for Minecraft's own aiming arms and do not hold a bow upright across the aim, so they are replaced:
 *  - the bow arm held out toward the target (rig -z), the bow (placed as gecko/bow.js place holds it in the hand) upright
 *    across the aim: its limbs along the torso's up, square to the aim, its plane holding the aim, the grip at one offset
 *    from the bow shoulder (gripFor: the drawing hand at full draw by the jaw, both arms reaching, the forearms clear of the
 *    head and torso and the upper arms' twist in range over the whole draw);
 *  - the drawing hand (the left fist, weapon_l's pivot) on the string: at its middle at brace (BRACE behind the grip) when the
 *    draw starts, pulled straight back along the arrow to DRAW behind the grip at full draw, by the jaw;
 *  - in bow_aim the arms first come up from AnimationsPlus' own (its start, as played from the stance) to the nocked pose
 *    by NOCK, hold it to DRAW_START, draw at an even pace to FULL_DRAW and hold; in bow_release the full draw is held to
 *    RELEASE, then the arms go over to AnimationsPlus' own (its drawing hand flung back past the head).
 * Each arm is a two-bone chain with a hinge elbow (as hinge.js leaves them): the elbow's bend sets the shoulder-to-fist
 * reach, the upper arm points the fist at its target, turned about the shoulder-to-fist line (the swivel) to keep the
 * held item's turn against the forearm least (bow arm) or the hand and forearm out of the head, the twist in range and the
 * elbow out and up (drawing arm). weapon_r takes the bow's orientation; weapon_l stays square on the forearm. The build
 * then measures the draw's timing from the drawing hand (gecko/bow.js drawTiming: draw.json, what the game reads). */
'use strict';
const { animationJson, fromTrue, trueRotation } = require('./gecko/write.js');
const { samplePose, worldBones, point } = require('./gecko/sample.js');
const { RIG } = require('./gecko/rig.js');
const B = require('./gecko/bow.js');

const PIVOT = Object.fromEntries(RIG.map((b) => [b.name, b.pivot]));
const sub = (a, b) => a.map((x, i) => x - b[i]), add = (a, b) => a.map((x, i) => x + b[i]);
const scale = (a, k) => a.map((x) => x * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(...a); return l > 1e-12 ? scale(a, 1 / l) : [0, 0, 0]; };
const mulVec = (R, v) => [0, 1, 2].map((r) => R[r * 3] * v[0] + R[r * 3 + 1] * v[1] + R[r * 3 + 2] * v[2]);
const mul3 = (a, b) => { const o = []; for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) o.push(a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]); return o; };
const T = (m) => [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]];
const col = (R, j) => [R[j], R[3 + j], R[6 + j]];
const RAD = Math.PI / 180;
function axisAngle(u, a) {
  const c = Math.cos(a), s = Math.sin(a), t = 1 - c, [x, y, z] = u;
  return [t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x, t * x * z - s * y, t * y * z + s * x, t * z * z + c];
}
const rotX = (a) => { const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, c, -s, 0, s, c]; };
/** The rotation taking unit a onto unit b, the shortest way. */
function align(a, b) {
  const v = cross(a, b), c = dot(a, b), s = Math.hypot(...v);
  if (s < 1e-9) return c > 0 ? [1, 0, 0, 0, 1, 0, 0, 0, 1] : axisAngle(norm(Math.abs(a[0]) < 0.9 ? cross(a, [1, 0, 0]) : cross(a, [0, 1, 0])), Math.PI);
  return axisAngle(scale(v, 1 / s), Math.atan2(s, c));
}
/** The rotation taking frame (a1, a2) onto frame (b1, b2) (each a pair of unit, square vectors). */
function frameTo(a1, a2, b1, b2) {
  const a3 = cross(a1, a2), b3 = cross(b1, b2), R = new Array(9).fill(0);
  for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) R[r * 3 + c] = b1[r] * a1[c] + b2[r] * a2[c] + b3[r] * a3[c];
  return R;
}
const angleOf = (R) => Math.acos(Math.max(-1, Math.min(1, (R[0] + R[4] + R[8] - 1) / 2)));

// ---- quaternions, to blend local rotations ----
function quatOf(m) {
  const tr = m[0] + m[4] + m[8];
  let q;
  if (tr > 0) { const s = Math.sqrt(tr + 1) * 2; q = [(m[7] - m[5]) / s, (m[2] - m[6]) / s, (m[3] - m[1]) / s, s / 4]; }
  else if (m[0] > m[4] && m[0] > m[8]) { const s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2; q = [s / 4, (m[1] + m[3]) / s, (m[2] + m[6]) / s, (m[7] - m[5]) / s]; }
  else if (m[4] > m[8]) { const s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2; q = [(m[1] + m[3]) / s, s / 4, (m[5] + m[7]) / s, (m[2] - m[6]) / s]; }
  else { const s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2; q = [(m[2] + m[6]) / s, (m[5] + m[7]) / s, s / 4, (m[3] - m[1]) / s]; }
  return q;
}
function matOf(q) {
  const [x, y, z, w] = q;
  return [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)];
}
function slerp(a, b, f) {
  let d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
  if (d < 0) { b = b.map((x) => -x); d = -d; }
  if (d > 0.9995) { const q = a.map((x, i) => x + f * (b[i] - x)), l = Math.hypot(...q); return q.map((x) => x / l); }
  const th = Math.acos(d), s = Math.sin(th);
  return a.map((x, i) => (x * Math.sin((1 - f) * th) + b[i] * Math.sin(f * th)) / s);
}
/** A rotation split into its swing (no turn about the bone's rest y axis) and its twist about y (radians). */
function swingTwist(m) {
  let q = quatOf(m);
  if (q[3] < 0) q = q.map((x) => -x);
  const n = Math.hypot(q[1], q[3]), tw = n < 1e-9 ? 0 : 2 * Math.atan2(q[1] / n, q[3] / n);
  const t = [0, Math.sin(-tw / 2), 0, Math.cos(-tw / 2)];   // the twist undone: swing = q t⁻¹
  const sw = [q[3] * t[0] + q[0] * t[3] + q[1] * t[2] - q[2] * t[1], q[3] * t[1] - q[0] * t[2] + q[1] * t[3] + q[2] * t[0],
    q[3] * t[2] + q[0] * t[1] - q[1] * t[0] + q[2] * t[3], q[3] * t[3] - q[0] * t[0] - q[1] * t[1] - q[2] * t[2]];
  return { sw, tw };
}
/** Blends two local rotations by f: their swings by slerp, their twists about the bone's own length straight between the
 *  two (a plain slerp can swing through more twist than either end has: past the shoulder's or wrist's range). */
function blendRot(A, Bm, f) {
  const a = swingTwist(A), b = swingTwist(Bm);
  const d = b.tw - a.tw;   // through no twist, not the short way round: both ends are within the joint's range, the way between them too
  return mul3(matOf(slerp(a.sw, b.sw, f)), matOf([0, Math.sin((a.tw + f * d) / 2), 0, Math.cos((a.tw + f * d) / 2)]));
}
const smooth = (f) => { const x = Math.max(0, Math.min(1, f)); return x * x * (3 - 2 * x); };

// ---- the clips' times (s) ----
/** bow_aim, on AnimationsPlus' 0.72 s bowR (its torso turned about 30° side-on by NOCK and 60° by FULL_DRAW): the drawing
 *  hand on the string (NOCK), the draw starting (DRAW_START), the full draw; bow_release: the string leaving the fingers. */
const NOCK = 0.25, DRAW_START = 0.3, FULL_DRAW = 0.6, RELEASE = 0.05;
/** After the release bow_release goes over to AnimationsPlus' own arms (its drawing hand flung back past the head, the bow
 *  arm dropping) in RELEASE_BLEND s, fast at first (the string leaves the fingers at once), each joint's swing and twist
 *  blended apart (blendRot), so no arm turns about its length past its range on the way. */
const RELEASE_BLEND = 0.2;
/** The clips' keys: every 1/60 s (PulseLib lerps the Euler angles between them: the hand stays within 0.1 px of its
 *  path, about 0.4 px at 1/30 s). */
const STEP = 1 / 60;
/** The string's middle at brace, behind the grip (px): the middle of the bows' braces as held (3.3-6.6 px for the
 *  exported Middle-earth bows and longbows at their class lengths, gecko/bow.js measure), so the hand meets any of them
 *  within 1.7 px as the draw starts, and nearer as it goes on (at full draw every nock is DRAW behind the grip). */
const BRACE = 5;

// ---- the arm chains (rig px, rest) ----
const ARMS = {
  right: { arm: 'right_arm', elbow: 'right_elbow', hand: 'weapon_r', side: 'right' },
  left: { arm: 'left_arm', elbow: 'left_elbow', hand: 'weapon_l', side: 'left' },
};
for (const a of Object.values(ARMS)) {
  a.S = PIVOT[a.arm];
  a.E = PIVOT[a.elbow];
  a.F = PIVOT[a.hand];   // the fist point
  a.upper = sub(a.E, a.S);
  a.fore = sub(a.F, a.E);
}
/** Elbow bends allowed (radians): a hinge folding forward, from almost straight to 145°. */
const BEND_MIN = 4 * RAD, BEND_MAX = 145 * RAD;
const reachAt = (a, th) => Math.hypot(...add(a.upper, mulVec(rotX(th), a.fore)));
/** The bend that puts the fist d from the shoulder (reach falls as the bend grows), clamped to the range. */
function bendFor(a, d) {
  let lo = BEND_MIN, hi = BEND_MAX;
  if (d >= reachAt(a, lo)) return lo;
  if (d <= reachAt(a, hi)) return hi;
  for (let k = 0; k < 50; k++) { const m = (lo + hi) / 2; if (reachAt(a, m) > d) lo = m; else hi = m; }
  return (lo + hi) / 2;
}

/**
 * One arm reaching its fist point to target X (world) from shoulder S (world): { arm (world R), bend, fist (world) } for
 * each swivel, the caller picking one. The upper arm's world rotation is Rsw · Align(v(bend) → X - S) about the shoulder.
 */
function reach(a, S, X, swivel) {
  const D = sub(X, S), d = Math.hypot(...D), th = bendFor(a, d);
  const v = add(a.upper, mulVec(rotX(th), a.fore)), u = norm(D);
  const R = mul3(axisAngle(u, swivel), align(norm(v), u));
  return { R, bend: th, fist: add(S, mulVec(R, v)), elbowAt: add(S, mulVec(R, a.upper)) };
}

/** World bones of the posed chain: { arm, elbow } as { R, p } (x -> R x + p), from shoulder S. */
function chain(a, S, R, th) {
  const arm = { R, p: sub(S, mulVec(R, a.S)) };
  const RE = mul3(R, rotX(th));
  const elbow = { R: RE, p: sub(add(S, mulVec(R, a.upper)), mulVec(RE, a.E)) };
  return { arm, elbow };
}
const apply = (w, x) => add(mulVec(w.R, x), w.p);

// ---- the body, for the drawing arm's clearance ----
const HEAD = [[-4, 24, -4], [4, 32, 4]], TORSO = [[-4, 12, -2], [4, 24, 2]];
function boxDist(q, box) {
  let out = 0, inMax = -Infinity;
  for (let i = 0; i < 3; i++) {
    const d = Math.abs(q[i] - (box[0][i] + box[1][i]) / 2) - (box[1][i] - box[0][i]) / 2;
    if (d > 0) out += d * d;
    if (d > inMax) inMax = d;
  }
  return out > 0 ? Math.sqrt(out) : Math.min(inMax, 0);
}
/** How far a world point stands out of a bone's rest box (px, negative inside). */
const outOf = (w, box, p) => boxDist(mulVec(T(w.R), sub(p, w.p)), box);
/** Points down the forearm to the hand's middle (rest, rig px, about the arm's axis x ±6, z 0). */
const FOREARM_PTS = { right: [[6, 16, 0], [6, 14, 0], [6, 12.5, 0]], left: [[-6, 16, 0], [-6, 14, 0], [-6, 12.5, 0]] };
const ARM_R = 2;

/** The bow's frame in the world: its up (along the torso's up, square to the aim) and out (the aim). */
const AIM = [0, 0, -1];
function bowFrame(W) {
  const torsoUp = col(W.body.R, 1);
  const up = norm(sub(torsoUp, scale(AIM, dot(torsoUp, AIM))));
  return { up, out: AIM };
}
/** weapon_r's world rotation holding the bow so (its rest frame: the bow's up along rig -z, out along rig -y, as
 *  gecko/bow.js place seats it in the arm's part space). */
const REST_UP = [0, 0, -1], REST_OUT = [0, -1, 0];
const GRIP_REST = B.toRig('right', B.GRIP.right);

/** Where the grip must be, and the bow's hand, for frame W: the bow arm's fist point given the grip G (world). */
function fistFor(Rw, G) { return sub(G, mulVec(Rw, sub(GRIP_REST, ARMS.right.F))); }

/** The bow arm for frame W holding the grip at G: the swivel that leaves the least turn of weapon_r against the forearm. */
function bowArm(W, G, prev, step = 2) {
  const a = ARMS.right, S = point(W.right_arm, a.S), f = bowFrame(W), Rw = frameTo(REST_UP, REST_OUT, f.up, f.out);
  const X = fistFor(Rw, G);
  let best = null;
  for (let sw = -180; sw < 180; sw += step) {
    const r = reach(a, S, X, sw * RAD), c = chain(a, S, r.R, r.bend), wrist = angleOf(mul3(T(c.elbow.R), Rw));
    const cost = wrist + (prev ? 0.3 * angleOf(mul3(T(prev), r.R)) : 0);
    if (!best || cost < best.cost) best = { cost, r, c, wrist };
  }
  return { S, Rw, X, ...best, miss: Math.hypot(...sub(best.r.fist, X)) };
}

/** A local rotation's twist about its rest y axis (degrees; the swing-twist split, as test/lib/motion.js twist takes it). */
function twistAbout(m) {
  const q = quatOf(m), w = q[3] < 0 ? -q[3] : q[3], y = q[3] < 0 ? -q[1] : q[1], n = Math.hypot(y, w);
  return n < 1e-9 ? 0 : (2 * Math.atan2(y / n, w / n)) / RAD;
}
/** The upper arm's twist against the body kept within TWIST_EASY where it can be, and within TWIST_HARD (degrees) unless
 *  no swivel is: under the shoulder's 95° (test/joints.test.js), with room for the lerp between keys. */
const TWIST_EASY = 60, TWIST_HARD = 85;

/** The most the drawing arm's upper arm turns from one key to the next (radians): 20° in 1/60 s, about 200 px/s at the
 *  elbow, well under a jump (test/motion.test.js: 600 px/s). */
const ARM_STEP = 20 * RAD;

/** The drawing arm for frame W with its fist point at N: the swivel keeping the hand and forearm out of the head and
 *  torso, then the upper arm's twist within TWIST_EASY, the elbow out from the body and level with the hand, and nearest
 *  the last frame's (never more than ARM_STEP from it). */
function drawArm(W, N, prev, step = 3) {
  const a = ARMS.left, S = point(W.left_arm, a.S);
  let best = null;
  for (let sw = -180; sw < 180; sw += step) {
    const r = reach(a, S, N, sw * RAD), c = chain(a, S, r.R, r.bend);
    let clash = 0;
    for (const p of FOREARM_PTS.left) {
      const q = apply(c.elbow, p);
      clash += Math.max(0, ARM_R + 0.5 - outOf(W.head, HEAD, q)) + Math.max(0, ARM_R + 0.5 - outOf(W.body, TORSO, q));
    }
    // the elbow out from the body: its distance from the torso's middle line, up to 8 px
    const mid = point(W.body, [0, 18, 0]), up = col(W.body.R, 1), e = sub(r.elbowAt, mid), out = Math.hypot(...sub(e, scale(up, dot(e, up))));
    const turn = Math.abs(twistAbout(mul3(T(W.body.R), r.R)));
    const level = Math.abs(r.elbowAt[1] - N[1]);   // the elbow up level with the hand: in line behind the arrow
    const moved = prev ? angleOf(mul3(T(prev), r.R)) : 0;
    const cost = 20 * clash - 0.2 * Math.min(out, 8) + 0.3 * level + Math.max(0, turn - TWIST_EASY) + 100 * Math.max(0, turn - TWIST_HARD) + 10 * moved
      + 1000 * Math.max(0, moved - ARM_STEP);   // never faster than ARM_STEP a key: the arm swings round, it does not jump
    if (!best || cost < best.cost) best = { cost, r, c, clash, turn };
  }
  return { S, ...best, miss: Math.hypot(...sub(best.r.fist, N)) };
}

/** Local keys (Blockbench) from world rotations: arm against the body, elbow against the arm, hand against the elbow. */
const localKeys = (W, armR, elbowR, handR) => ({ arm: fromTrue(mul3(T(W.body.R), armR)), elbow: fromTrue(mul3(T(armR), elbowR)), hand: fromTrue(mul3(T(elbowR), handR)) });

/** A clip's local rotation (true matrix) of a bone at t: its rotation keys, linearly, as the clip holds them. */
function localAt(clip, bone, t) {
  const keys = clip.bones[bone] && clip.bones[bone].rotation;
  if (!keys || !keys.length) return [1, 0, 0, 0, 1, 0, 0, 0, 1];
  let v = keys[keys.length - 1].v;
  if (t <= keys[0].t) v = keys[0].v;
  else for (let i = 1; i < keys.length; i++) if (t <= keys[i].t) {
    const k0 = keys[i - 1], k1 = keys[i], f = k1.t > k0.t ? (t - k0.t) / (k1.t - k0.t) : 1;
    v = k0.v.map((x, j) => x + f * (k1.v[j] - x));
    break;
  }
  return trueRotation(v);
}

const BONES = ['right_arm', 'right_elbow', 'weapon_r', 'left_arm', 'left_elbow', 'weapon_l'];

/** Where the grip is at frame W: offset g (world px) from the bow shoulder, so the bow arm keeps its shape as the torso
 *  turns under it and the bow stays square to the aim. */
const gripAt = (W, g) => add(point(W.right_arm, ARMS.right.S), g);

/** The grip's offset from the bow shoulder (world px), the same from the draw's start until the release, chosen once per race: the drawing hand DRAW
 *  behind it at full draw by the jaw (JAW, the head's lower front corner on the drawing side), out of the head and torso;
 *  both hands reaching the bow and the string over the whole draw and hold (frames: { W, p } world bones and draw
 *  progress); the bow arm near straight and the bow square on its forearm. A grid search over the space in front of the
 *  archer, every px with the swivels every 10°, then every quarter px about the best with the swivels as posed. */
const JAW = [-2, 24, -4];
function gripFor(frames) {
  const last = frames[frames.length - 1], jaw = point(last.W.head, JAW);
  const score = (G, fine) => {
    let cost = 0;
    for (const f of frames) {
      const at = gripAt(f.W, G), bow = bowArm(f.W, at, null, fine ? 2 : 10);
      if (bow.miss > 0.05) return null;
      const draw = drawArm(f.W, sub(at, scale(AIM, BRACE + f.p * (B.DRAW - BRACE))), null, fine ? 3 : 10);
      if (draw.miss > 0.05 || draw.clash > (fine ? 0.05 : 0.5) || draw.turn > TWIST_HARD + (fine ? 0 : 10)) return null;   // the coarse look lets the swivels every 10° off a little
      if (f === last) cost = Math.hypot(...sub(draw.r.fist, jaw)) + 0.05 * (bow.r.bend / RAD) + 0.1 * (bow.wrist / RAD);
    }
    return cost;
  };
  let best = null;
  const look = (G, fine) => { const c = score(G, fine); if (c !== null && (!best || c < best.cost)) best = { cost: c, G }; };
  const S = point(last.W.right_arm, ARMS.right.S);   // offsets from the bow shoulder, searched about it
  for (let x = -11; x <= 8; x++) for (let y = 20; y <= 28; y++) for (let z = -20; z <= -6; z++) look(sub([x, y, z], S), false);
  if (!best) return null;
  const c0 = best.G;
  best = null;
  for (let dx = -1; dx <= 1; dx += 0.25) for (let dy = -1; dy <= 1; dy += 0.25) for (let dz = -1; dz <= 1; dz += 0.25) look([c0[0] + dx, c0[1] + dy, c0[2] + dz], true);
  return best ? best.G : c0;
}

/** Writes the six arm bones' keys over [from, to] (s) into clip c from pose(t) = { arm, elbow, hand } local keys per side,
 *  replacing their keys there and dropping their position keys (the chains are pure rotations about the pivots). */
function writeArms(c, times, poses) {
  for (const b of BONES) {
    c.bones[b] = c.bones[b] || {};
    if (!/_arm$/.test(b)) delete c.bones[b].position;   // the shoulders keep theirs: the chains were solved from them
  }
  const set = (bone, which, side) => { c.bones[bone].rotation = times.map((t, i) => ({ t, v: poses[i][side][which], lerp: 'linear' })); };
  set('right_arm', 'arm', 'right'); set('right_elbow', 'elbow', 'right'); set('weapon_r', 'hand', 'right');
  set('left_arm', 'arm', 'left'); set('left_elbow', 'elbow', 'left'); set('weapon_l', 'hand', 'left');
}

/** The key times over a clip: every STEP, with the named times and the end. */
function gridTimes(length, extra) {
  const ts = new Set(extra.map((x) => +x.toFixed(4)));
  for (let t = 0; t < length - 1e-6; t += STEP) ts.add(+t.toFixed(4));
  ts.add(+length.toFixed(4));
  return [...ts].sort((a, b) => a - b);
}

/** Both arms posed at frame W for grip offset g and draw progress p: local keys and the world rotations (for continuity). */
function posed(W, g, p, prev) {
  const G = gripAt(W, g), bow = bowArm(W, G, prev && prev.rightR);
  const N = sub(G, scale(AIM, BRACE + p * (B.DRAW - BRACE)));
  const draw = drawArm(W, N, prev && prev.leftR);
  const hand = draw.c.elbow.R;   // weapon_l square on the forearm
  return {
    right: localKeys(W, bow.r.R, bow.c.elbow.R, bow.Rw), left: localKeys(W, draw.r.R, draw.c.elbow.R, hand),
    rightR: bow.r.R, leftR: draw.r.R, miss: Math.max(bow.miss, draw.miss), clash: draw.clash,
  };
}

/** A pose's local keys blended (slerp) from the clip's own at t toward `to` by f. */
function blendPose(c, t, to, f) {
  const k = (bone, v) => fromTrue(blendRot(localAt(c, bone, t), trueRotation(v), f));
  return {
    right: { arm: k('right_arm', to.right.arm), elbow: k('right_elbow', to.right.elbow), hand: k('weapon_r', to.right.hand) },
    left: { arm: k('left_arm', to.left.arm), elbow: k('left_elbow', to.left.elbow), hand: k('weapon_l', to.left.hand) },
  };
}

/** The clip's world bones at t (for its torso and head). */
const worldAt = (anim, t) => worldBones(samplePose(anim, Math.min(t, anim.animation_length)));

const easeOut = (f) => { const x = Math.max(0, Math.min(1, f)); return 1 - (1 - x) ** 3; };

/**
 * The bow clips' arms (see the file comment). aim, release: the finished bow_aim and bow_release clips (hinge.js already
 * applied); returns { aim, release, grip, miss, clash } with the arms replaced (new clips; the inputs are left alone): the
 * grip at full draw (world px), and the most any solved pose missed its targets by and sank the drawing arm into the body.
 */
function bowArms(aim, release) {
  const A = JSON.parse(JSON.stringify(aim)), R = JSON.parse(JSON.stringify(release));
  const animA = animationJson({ c: aim }).animations.c, animR = animationJson({ c: release }).animations.c;
  const pAt = (t) => Math.max(0, Math.min(1, (t - DRAW_START) / (FULL_DRAW - DRAW_START)));
  const probe = [DRAW_START, (DRAW_START + FULL_DRAW) / 2, FULL_DRAW].map((t) => ({ W: worldAt(animA, t), p: pAt(t) }));
  probe.push({ W: worldAt(animR, RELEASE), p: 1 }, { W: worldAt(animA, aim.length), p: 1 });
  const g = gripFor(probe);
  if (!g) throw new Error('bow_aim: no grip lets both hands reach the bow');
  // bow_aim: the arms come up into the nocked pose by NOCK, hold it to DRAW_START, draw to FULL_DRAW and hold
  const tA = gridTimes(aim.length, [NOCK, DRAW_START, FULL_DRAW]);
  const solved = new Map();
  let prev = null;
  for (const t of tA.filter((x) => x >= NOCK - 1e-9)) solved.set(t, (prev = posed(worldAt(animA, t), g, pAt(t), prev)));
  const nocked = solved.get(+NOCK.toFixed(4));
  writeArms(A, tA, tA.map((t) => solved.get(t) || blendPose(aim, t, nocked, smooth(t / NOCK))));
  // bow_release: the full draw held to RELEASE, then over to the clip's own arms
  const tR = gridTimes(release.length, [RELEASE, RELEASE + RELEASE_BLEND]);
  let held = null, prevR = prev;   // bow_release starts where bow_aim ends: its arms go on from there
  const posesR = tR.map((t) => {
    if (t <= RELEASE + 1e-9) return (held = prevR = posed(worldAt(animR, t), g, 1, prevR));
    const f = easeOut((t - RELEASE) / RELEASE_BLEND), own = {
      right: { arm: fromTrue(localAt(release, 'right_arm', t)), elbow: fromTrue(localAt(release, 'right_elbow', t)), hand: fromTrue(localAt(release, 'weapon_r', t)) },
      left: { arm: fromTrue(localAt(release, 'left_arm', t)), elbow: fromTrue(localAt(release, 'left_elbow', t)), hand: fromTrue(localAt(release, 'weapon_l', t)) },
    };
    return f >= 1 ? own : blendPoseFrom(held, own, f);
  });
  writeArms(R, tR, posesR);
  const all = [...solved.values(), ...posesR.filter((x) => x.miss !== undefined)];
  return { aim: A, release: R, grip: gripAt(worldAt(animA, aim.length), g), miss: Math.max(...all.map((x) => x.miss)), clash: Math.max(...all.map((x) => x.clash)) };
}

/** Local keys blended from pose a to pose b by f. */
function blendPoseFrom(a, b, f) {
  const k = (x, y) => fromTrue(blendRot(trueRotation(x), trueRotation(y), f));
  const side = (s) => ({ arm: k(a[s].arm, b[s].arm), elbow: k(a[s].elbow, b[s].elbow), hand: k(a[s].hand, b[s].hand) });
  return { right: side('right'), left: side('left') };
}

module.exports = { bowArms, NOCK, DRAW_START, FULL_DRAW, RELEASE, BRACE, _internals: { gripFor, posed, bowArm, drawArm, worldAt } };
