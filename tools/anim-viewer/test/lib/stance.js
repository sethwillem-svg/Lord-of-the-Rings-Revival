'use strict';
/* Formation stance measures (specs/addon/npc-formation-stances.md): every hard rule of the spec's table and the key-pose
 * score, measured on a stance clip as the race file ships it (GeckoLib JSON). test/stances.test.js judges the shipped
 * clips with these, and build_npc.js picks each stance with them (the spec has the build reuse the tests' measures). They
 * reuse the tests' and the game's own yardsticks: test/lib/motion.js (frames, hinges), test/lib/anatomy.js (shoulder range,
 * arms out of the body), gecko/strike.js (the blade as the game seats it), gecko/shield.js (the shield's plate as the game
 * straps it) and gecko/bow.js (the bow as the game places it: client.render.WeaponGrip.bow). The loop, feet and head checks are posture.test.js's, and the blade
 * check weapons.test.js's calm-clip one, measured the same way here (those files are tests, not modules).
 * Rig space: px, y up, facing -z (gecko/rig.js). */
const fs = require('node:fs');
const path = require('node:path');
const M = require('./motion.js');
const A = require('./anatomy.js');
const SH = require('../../gecko/shield.js');
const { blade, FIST } = require('../../gecko/strike.js');
const { point, samplePose, worldBones } = require('../../gecko/sample.js');
const BOW = require('../../gecko/bow.js');
const HELD = require('../../gecko/held.js');

const GRIPS = JSON.parse(fs.readFileSync(path.join(__dirname, '../../../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json'), 'utf8'));
/** A class's length in blocks (weapon_grips.json; build_npc.js classLength reads the same file). */
const lengthOf = (cls) => (GRIPS[cls] && GRIPS[cls].length) || 1;

/** The stance slots (spec "Slots"): who plays each, the classes its weapon rules are judged with (the first is the one the
 *  score and the sway use), whether it is held still, and its group. Shield versions are judged as their slot. */
const SLOT = {
  guard: { still: true, classes: ['sword', 'dagger', 'axe', 'longsword'] },
  guard_spear: { still: true, classes: ['spear'], spear: true },
  guard_bow: { still: true, classes: ['bow'], bow: true },
  shield_brace: { still: true, classes: ['sword'], shield: true },
  shield_brace_spear: { still: true, classes: ['spear'], spear: true, shield: true },
  spear_brace: { still: true, classes: ['spear'], spear: true },
  march: { still: false, classes: ['sword', 'dagger', 'axe', 'longsword'] },
  march_spear: { still: false, classes: ['spear'], spear: true },
  march_bow: { still: false, classes: ['bow'], bow: true },
};
/** Every stance clip a race file carries: the slots and their shield versions (spec "Slots"). */
const SHIELD_VERSIONS = ['guard', 'guard_spear', 'march', 'march_spear'];
const CLIPS = [...Object.keys(SLOT), ...SHIELD_VERSIONS.map((s) => s + '_shield')];
const slotOf = (name) => name.replace(/_shield$/, '');
const shieldOf = (name) => /_shield$/.test(name) || SLOT[slotOf(name)].shield === true;

/**
 * Key-pose ranges for the score: the spec's ranges, with an end of our own where the spec gives one side only (spec
 * "Score"). A "within N" limit is the signed range -N..N, so its middle is the limit's own aim (straight ahead, upright,
 * on the shaft). Each measure's distance from its range's middle, over half the range, is averaged with the sway's.
 */
const KEY = {
  tipZ: [-20, -4],          // guard: tip in front of the chest, out to a sword's reach from a bent arm
  tipUp: [0, 12],           // guard: tip above the hips, up to the chin (the hips 12 px below it)
  tipAim: [-45, 45],        // guard: degrees from straight ahead
  knees: [10, 40],          // guard: degrees of bend
  rise: [20, 60],           // guard_spear: shaft above level
  spearAim: [-45, 45],      // guard_spear: the tip's degrees from straight ahead
  onShaft: [-2.5, 2.5],     // the other hand's distance from the shaft
  upright: [-30, 30],       // guard_bow: the bow's tilt from upright
  belowChin: [0, 12],       // guard_bow: the grip under the chin, down to the hips
  toString: [-6, 6],        // guard_bow: the drawing hand's distance from the string
  facing: [-35, 35],        // shield: the plate's face from the facing
  hipsDown: [1.5, 4.5],     // shield_brace: below idle, to the depth of a half crouch
  lead: [3, 9],             // shield_brace: lead foot ahead, to a long pace
  level: [-15, 15],         // shield_brace_spear: shaft from level
  overShield: [20, 40],     // shield_brace_spear: tip ahead of the shield's face
  lunge: [5, 25],           // spear_brace: shaft above level
  reach: [28, 56],          // spear_brace: tip ahead, to the spear's length in front of the fist
  braceDown: [2, 6],        // spear_brace: hips below idle
  apart: [4, 10],           // spear_brace: feet apart front to back
};
const SWAY = [0.2, 2];

// ---- geometry ----
const sub = (a, b) => a.map((x, i) => x - b[i]);
const add = (a, b) => a.map((x, i) => x + b[i]);
const scale = (a, k) => a.map((x) => x * k);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const len = (a) => Math.hypot(...a);
const norm = (a) => scale(a, 1 / (len(a) || 1));
const deg = (r) => (r * 180) / Math.PI;
/** Distance from p to segment a-b. */
function toSegment(p, a, b) {
  const ab = sub(b, a), u = Math.max(0, Math.min(1, dot(sub(p, a), ab) / (dot(ab, ab) || 1)));
  return len(sub(p, add(a, scale(ab, u))));
}
/** The largest distance between two points of a path. */
function spread(pts) {
  let d = 0;
  for (let i = 0; i < pts.length; i++) for (let j = i + 1; j < pts.length; j++) d = Math.max(d, len(sub(pts[i], pts[j])));
  return d;
}
/** A world direction in a bone's rest frame. */
const into = (W, v) => [0, 1, 2].map((k) => W.R[k] * v[0] + W.R[3 + k] * v[1] + W.R[6 + k] * v[2]);
const local = (W, p) => into(W, sub(p, W.p));

// ---- the body ----
const footOf = (w, side) => point(w[side + '_knee'], [side === 'right' ? 1.9 : -1.9, 0, 0]);
const hipsOf = (w) => point(w.lower, [0, 12, 0]);
const chinOf = (w) => point(w.head, [0, 24, -4]);
/** The weapon held in the right hand: fist, tip, unit direction (fist to tip), and the butt 2 px behind the fist (the
 *  drawn handle's end: an item is seated with its drawn handle on the fist, gecko/held.js seatItem). */
function weapon(w, cls) {
  const fist = point(w.weapon_r, FIST.right), tip = point(w.weapon_r, blade('right', lengthOf(cls), 1)), dir = norm(sub(tip, fist));
  return { fist, tip, dir, butt: sub(fist, scale(dir, 2)) };
}
const leftFist = (w) => point(w.weapon_l, FIST.left);
/** Degrees of the direction d above level. */
const rise = (d) => deg(Math.asin(Math.max(-1, Math.min(1, d[1]))));
/** Signed degrees of a point's direction from straight ahead about the body axis (+ to the right). */
const aim = (p) => deg(Math.atan2(p[0], -p[2]));
/** The bow in the right hand at rest, in weapon_r's frame (rig px): a generated bow (gecko/bow.js flatBow, as Minecraft bakes
 *  Middle-earth's) seated as the game seats it (gecko/bow.js seat, client.render.WeaponGrip.bow), its class length.
 *  { grip, up (the limb axis, the upper tip's way), out (the arrow's way), tips: [lower, upper], brace }. */
const BOW_REST = (() => {
  const rig = BOW.flatBow(2).map((q) => q.map((p) => HELD.partToRig('right_arm', p)));
  const p = BOW.placedBow(rig, BOW.seat(rig, 'right', 'bow', GRIPS).m);
  const [a, b] = p.tips, upper = dot(sub(b, a), p.up) > 0 ? [a, b] : [b, a];
  return { grip: p.grip, up: p.up, out: p.out, tips: upper, brace: p.brace };
})();
/** The bow in the right hand, posed: its grip, upper and lower tips and string (tip to tip). */
function bowAt(w) {
  const at = (p) => point(w.weapon_r, p);
  return { grip: at(BOW_REST.grip), top: at(BOW_REST.tips[1]), bottom: at(BOW_REST.tips[0]), string: BOW_REST.tips.map(at) };
}
/** The waist over the hips' joint (px): the belt line, a quarter of the way up the torso's 12 px (the bottom 3 px of a
 *  Minecraft skin's torso are the belt and the trousers' top). A spear's lower hand 1.6 px over the joint (the UAL lantern
 *  guard) reads in a render as a shaft rising from the crotch. */
const WAIST = 3;
/** Both soles on the floor in a still stance: each within this of it (px, measured as the lower foot is). */
const SOLE = [-1.5, 2];
/** How high a still stance's knees stand at least (px; the knee joint, 6 px up an upright shin): with the sole on the
 *  floor, a shin more than about 48° off upright. UAL's Shield_Dash legs (the merged spear brace) lay man's back shin 26°
 *  under level, its knee 2.6 px over the floor, a foot dragged behind a dash: their soles and thighs pass. */
const KNEE_MIN = 4;
/** The most a still stance's thigh may swing from straight down (degrees). */
const THIGH_MAX = 70;
/** A thigh's swing from straight down (degrees): the leg bone's rest down (-y) in the world, against the world's down. */
const thighDown = (w, side) => deg(Math.acos(Math.max(-1, Math.min(1, w[side + '_leg'].R[4]))));
/** The most a still stance's chest may turn from the NPC's facing (degrees). */
const YAW_MAX = 15;
/** The chest's turn from the facing about the vertical (degrees, + to the right): its forward (rest -z) on the ground. */
const torsoYaw = (w) => { const f = [-w.body.R[2], -w.body.R[5], -w.body.R[8]]; return deg(Math.atan2(f[0], -f[2])); };
/** Degrees the bow's limb axis leans from vertical. */
const bowTilt = (b) => deg(Math.acos(Math.max(-1, Math.min(1, norm(sub(b.top, b.bottom))[1]))));

// ---- shields, as test/shield.test.js measures them (gecko/shield.js: both kinds of Middle-earth shield) ----
const PLATES = Object.entries(SH.MODELS).map(([kind, model]) => {
  const drawn = SH.armQuads(model, true), m = SH.place(drawn, true), plate = SH.plateOf(drawn);
  return { kind, out: m ? SH.mat.applyDir(m, plate.out) : plate.out, faces: SH.placed(plate.faces, m) };
});
function plateAt(w, plate) {
  const wl = w.weapon_l, n = [0, 1, 2].map((r) => wl.R[r * 3] * SH.dirToRig(plate.out)[0] + wl.R[r * 3 + 1] * SH.dirToRig(plate.out)[1] + wl.R[r * 3 + 2] * SH.dirToRig(plate.out)[2]);
  const faces = plate.faces.map((q) => q.map((p) => point(wl, SH.toRig('left', p))));
  return { angle: deg(Math.acos(Math.max(-1, Math.min(1, -n[2])))), n, faces };
}
/** Does the segment a-b cross the (planar, convex) quad q? (test/shield.test.js crosses) */
function crosses(a, b, q) {
  const cross = (u, v) => [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
  const n = cross(sub(q[1], q[0]), sub(q[2], q[0])), d = sub(b, a), den = dot(n, d);
  if (Math.abs(den) < 1e-12) return false;
  const s = dot(n, sub(q[0], a)) / den;
  if (s < 0 || s > 1) return false;
  const p = add(a, scale(d, s));
  let sign = 0;
  for (let i = 0; i < q.length; i++) {
    const c = dot(n, cross(sub(q[(i + 1) % q.length], q[i]), sub(p, q[i])));
    if (Math.abs(c) < 1e-9) continue;
    if (sign && Math.sign(c) !== sign) return false;
    sign = Math.sign(c);
  }
  return true;
}
const blocks = (faces, a, b) => faces.some((q) => crosses(a, b, q));
/** Points over the face (the head box's front, 8 x 8 px), a 5 x 5 grid 1 px in from its edges (rest, head frame). */
const FACE = [];
for (const x of [-3, -1.5, 0, 1.5, 3]) for (const y of [25, 26.5, 28, 29.5, 31]) FACE.push([x, y, -4]);
/** The share of the face that must be seen past an element shield: two rows of five, the eyes' row up to the brow. Not
 *  half: the shield code straps the plate's top at the eyes of an elf (whose posture leans the chest back), and the elves'
 *  own walk_shield shows 12 of the 25 points (man's 15, dwarves' 19). */
const FACE_SEEN = 0.4;

// ---- the frames ----
/** The frames a measure walks: the 60 Hz grid and every key (test/lib/motion.js), the loop's end left out (it is its start). */
function framesOf(anim, key = null) {
  const fs0 = M.frames(anim, M.RATE, key);
  const L = anim.animation_length;
  return fs0.filter((f, i) => i === 0 || f.t < L - 1e-6);
}
/** posture.test.js's loop measure: the bone whose point (0, 10, 0) jumps furthest end to start, and how far (px). */
function loopJump(anim) {
  const w0 = worldBones(samplePose(anim, 0)), w1 = worldBones(samplePose(anim, anim.animation_length - 1e-6));
  let worst = { d: 0, bone: 'root' };
  for (const bone of Object.keys(w0)) {
    const d = len(sub(point(w0[bone], [0, 10, 0]), point(w1[bone], [0, 10, 0])));
    if (d > worst.d) worst = { d, bone };
  }
  return worst;
}
const headPitch = (w) => { const f = [0, 1, 2].map((r) => w.head.R[r * 3 + 2] * -1); return deg(Math.asin(Math.max(-1, Math.min(1, f[1])))); };
/** weapons.test.js's calm-clip check: a one-block blade inside the torso or head box (1 px of skin), or below the floor. */
function bladeIn(w) {
  const box = (W, b, p) => { const q = local(W, p); return q.every((x, i) => x > b[0][i] + 1 && x < b[1][i] - 1); };
  for (const f of [0.35, 0.65, 1]) {
    const p = point(w.weapon_r, blade('right', 1, f));
    if (p[1] < -1) return `blade below the floor`;
    if (box(w.body, [[-4, 12, -2], [4, 24, 2]], p) || box(w.head, [[-4, 24, -4], [4, 32, 4]], p)) return `blade ${Math.round(f * 100)}% along inside the body`;
  }
  return null;
}

// ---- gait ----
/** The feet's front-back order through the clip: zR - zL per frame (negative: the right foot ahead). */
const lead = (fs0) => fs0.map((f) => footOf(f.w, 'right')[2] - footOf(f.w, 'left')[2]);
/** Gait cycles per loop: sign changes of the feet's order, halved. */
function cyclesOf(fs0) {
  const d = lead(fs0);
  let n = 0;
  for (let i = 0; i < d.length; i++) { const a = d[i], b = d[(i + 1) % d.length]; if (a !== 0 && Math.sign(a) !== Math.sign(b)) n++; }
  return n / 2;
}
/** The upper arm's swing front to back in the chest's frame (degrees, max over both arms). */
function armSwing(fs0, sides = ['right', 'left']) {
  let worst = 0;
  for (const s of sides) {
    const a = fs0.map((f) => { const d = A.armDir(f.w, s + '_arm'); return deg(Math.atan2(-d[2], -d[1])); });
    worst = Math.max(worst, Math.max(...a) - Math.min(...a));
  }
  return worst;
}
/** Foot skate: while a foot is the planted one (within 0.75 px of the lower foot and of the floor's lowest), how far its
 *  ground velocity strays from the steady slide of an in-place gait (the median planted velocity), px/s, averaged. */
function skate(fs0) {
  const L = fs0[fs0.length - 1].t + (fs0[1] ? fs0[1].t - fs0[0].t : 0);
  const v = [];
  for (const s of ['right', 'left']) {
    for (let i = 0; i < fs0.length; i++) {
      const j = (i + 1) % fs0.length, a = fs0[i], b = fs0[j];
      const dt = j > i ? b.t - a.t : L - a.t + b.t;
      if (dt <= 1e-6) continue;
      const p = footOf(a.w, s), q = footOf(b.w, s), o = footOf(a.w, s === 'right' ? 'left' : 'right');
      if (p[1] > o[1] + 0.75) continue;   // the other foot is the planted one
      v.push([(q[0] - p[0]) / dt, (q[2] - p[2]) / dt]);
    }
  }
  if (!v.length) return 0;
  const med = (k) => { const s = v.map((x) => x[k]).sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; };
  const m = [med(0), med(1)];
  return v.reduce((s, x) => s + Math.hypot(x[0] - m[0], x[1] - m[1]), 0) / v.length;
}
/** Steps: the times and front-back lengths at which each foot comes to the front (the feet's order flips). Irregularity:
 *  the largest share by which a step's length or time strays from the mean. */
function steps(fs0) {
  const d = lead(fs0), L = fs0[fs0.length - 1].t + (fs0[1] ? fs0[1].t - fs0[0].t : 0), out = [];
  for (let i = 0; i < d.length; i++) {
    const j = (i + 1) % d.length;
    if (Math.sign(d[i]) !== Math.sign(d[j]) && d[i] !== 0) {
      // the step's length: the feet's widest split in the half cycle before this flip
      out.push({ t: fs0[j].t, i: j });
    }
  }
  if (out.length < 2) return { n: out.length, irregular: 0 };
  const times = out.map((x, k) => { const nx = out[(k + 1) % out.length]; return ((nx.t - x.t) + L) % L || L; });
  const lens = out.map((x, k) => {
    const nx = out[(k + 1) % out.length];
    let m = 0;
    for (let i = x.i; i !== nx.i; i = (i + 1) % d.length) m = Math.max(m, Math.abs(d[i]));
    return m;
  });
  const dev = (xs) => { const mean = xs.reduce((a, b) => a + b, 0) / xs.length; return Math.max(...xs.map((x) => Math.abs(x - mean) / (mean || 1))); };
  return { n: out.length, irregular: Math.max(dev(times), dev(lens)) };
}
/** The torso's lean from the race's own upright (its posture's hunch forward), degrees. */
function torsoLean(w, hunch = 0) {
  const y = [w.upper.R[1], w.upper.R[4], w.upper.R[7]], h = (hunch * Math.PI) / 180;
  return deg(Math.acos(Math.max(-1, Math.min(1, dot(y, [0, Math.cos(h), -Math.sin(h)])))));
}

/** The bones each half of the body takes when two candidates are combined (build_npc.js): the legs and the hips' place
 *  (root) below, the rest above, as posture splits them. */
const LOWER_BONES = new Set(['root', 'lower', 'right_leg', 'right_knee', 'left_leg', 'left_knee']);

/** The rows of the spec's hard-rule table: each failure names one, and the half of the body it is about. */
const RULES = ['loop', 'feet', 'planted', 'facing', 'sway', 'anatomy', 'blade', 'head', 'guard', 'guard_spear', 'guard_bow', 'shield', 'shield_brace',
  'shield_brace_spear', 'spear_brace', 'march', 'march_spear', 'march_bow'];

/**
 * Every hard rule for one stance clip (name: the slot, or its _shield version), and the score of its key pose:
 * { fails: [{ rule, part ('upper', 'lower', or 'both': a pairing's, judged once built in full), msg }], measures: { name: value },
 * score } (lower is better).
 * ctx: { idle, walk } (the race's idle and walk, GeckoLib JSON), hunch (the race's posture, degrees), key (a
 * test/lib/motion.js cache key, or null).
 */
function judge(name, anim, ctx = {}) {
  const slot = slotOf(name), rule = SLOT[slot], shield = shieldOf(name), fails = [], m = {};
  const fail = (r, part, msg) => { if (!fails.some((x) => x.rule === r && x.msg === msg)) fails.push({ rule: r, part, msg }); };
  const fs0 = framesOf(anim, ctx.key || null);
  const ws = fs0.map((f) => f.w);
  const mean = (f) => ws.reduce((s, w) => s + f(w), 0) / ws.length;
  /** The first frame at which show(w) is not ok, as a failure of rule r. */
  const every = (r, part, what, ok, show) => {
    for (const f of fs0) {
      const v = show(f.w);
      if (!ok(v)) { fail(r, part, `${what} ${v.toFixed(1)} at t=${f.t.toFixed(2)}`); return; }
    }
  };
  // loops without a pop (posture.test.js): every bone's point (0, 10, 0) within 0.5 px end to start
  if (anim.loop !== true) fail('loop', 'upper', 'does not loop');
  const jump = loopJump(anim);
  if (jump.d > 0.5) fail('loop', LOWER_BONES.has(jump.bone) ? 'lower' : 'upper', `${jump.bone} jumps ${jump.d.toFixed(2)} px end to start`);
  // head level (posture.test.js), anatomy (test/lib/anatomy.js, as test/anatomy.test.js judges attacks: 10° of slack on the
  // shoulder's range, 0.5 px into the body), the blade out of the body and above the floor (weapons.test.js)
  every('head', 'upper', 'head from level °', (v) => Math.abs(v) <= 30, headPitch);
  for (const f of fs0) for (const side of ['right', 'left']) {
    const s = A.shoulder(f.w, side);
    for (const k of ['back', 'across']) if (s[k] > A.SHOULDER[k] + 10) fail('anatomy', 'upper', `${side} arm reaches ${k} ${s[k].toFixed(0)}° (limit ${A.SHOULDER[k]}° + 10°)`);
    const o = A.overlap(f.w, side);
    if (o.d > 0.5) fail('anatomy', 'upper', `${o.what} ${o.d.toFixed(1)} px deep`);
  }
  for (const f of fs0) { const b = bladeIn(f.w); if (b) { fail('blade', 'upper', `${b} at t=${f.t.toFixed(2)}`); break; } }
  // the torso over the hips within a spine's range (motion.test.js: twist 60°, forward 45°, back 25°, sideways 30°): it
  // takes both halves, so a pairing is judged on it only when built in full ('both')
  for (const f of fs0) {
    const v = M.waist(f.w);
    if (Math.abs(v.twist) > 60 || v.flex > 45 || v.flex < -25 || Math.abs(v.side) > 30) {
      fail('anatomy', 'both', `torso over the hips: twist ${v.twist.toFixed(0)} flex ${v.flex.toFixed(0)} side ${v.side.toFixed(0)}`);
      break;
    }
  }
  // the weapon of the slot's first class
  const cls = rule.classes[0];
  const tipOf = (w) => (rule.bow ? bowAt(w).top : weapon(w, cls).tip);
  if (rule.still) {
    // feet planted
    for (const side of ['right', 'left']) {
      const d = spread(ws.map((w) => footOf(w, side)));
      m[side + 'Foot'] = d;
      if (d > 0.5) fail('feet', 'lower', `${side} foot moves ${d.toFixed(2)} px`);
    }
    every('feet', 'lower', 'lower foot at y', (v) => v >= -1.5 && v <= 3.5, (w) => Math.min(footOf(w, 'right')[1], footOf(w, 'left')[1]));
    // both feet on the floor (a braced stance, not a dash with the back foot kicked up): each sole within SOLE of it, each
    // thigh at most THIGH_MAX from straight down (a deep lunge's back thigh stands 30-50° from it), and each knee KNEE_MIN
    // over the floor (the shin within about 48° of upright: a leg standing on its foot, not lying back along the ground)
    for (const side of ['right', 'left']) {
      every('planted', 'lower', `${side} sole at y`, (v) => v >= SOLE[0] && v <= SOLE[1], (w) => footOf(w, side)[1]);
      every('planted', 'lower', `${side} thigh from straight down °`, (v) => v <= THIGH_MAX, (w) => thighDown(w, side));
      every('planted', 'lower', `${side} knee over the floor by`, (v) => v >= KNEE_MIN, (w) => point(w[side + '_knee'], [side === 'right' ? 1.9 : -1.9, 6, 0])[1]);
    }
    // facing the front: the chest turned at most YAW_MAX from the NPC's facing (a still stance faces the enemy)
    m.yaw = Math.max(...ws.map(torsoYaw).map(Math.abs));
    if (m.yaw > YAW_MAX) fail('facing', 'upper', `chest turned ${m.yaw.toFixed(0)}° from the facing (≤ ${YAW_MAX}°)`);
    // steady but alive
    m.sway = spread(ws.map(tipOf));
    if (m.sway < SWAY[0] || m.sway > SWAY[1]) fail('sway', 'upper', `weapon tip sways ${m.sway.toFixed(2)} px (${SWAY[0]}-${SWAY[1]})`);
  }
  const idleHips = ctx.idle ? (() => { const fi = framesOf(ctx.idle); return fi.reduce((s, f) => s + hipsOf(f.w)[1], 0) / fi.length; })() : null;
  if (slot === 'guard') {
    for (const c of rule.classes) {
      every('guard', 'upper', `${c} tip z`, (v) => v <= -4, (w) => weapon(w, c).tip[2]);
      every('guard', 'upper', `${c} tip above the hips by`, (v) => v >= 0, (w) => weapon(w, c).tip[1] - hipsOf(w)[1]);
      every('guard', 'upper', `${c} tip from straight ahead °`, (v) => Math.abs(v) <= 45, (w) => aim(weapon(w, c).tip));
    }
    m.tipZ = mean((w) => weapon(w, cls).tip[2]);
    m.tipUp = mean((w) => weapon(w, cls).tip[1] - hipsOf(w)[1]);
    m.tipAim = mean((w) => aim(weapon(w, cls).tip));
    m.knees = mean((w) => (M.hinge(w, 'right_knee', 'right_leg').bend + M.hinge(w, 'left_knee', 'left_leg').bend) / 2);
    for (const side of ['right', 'left']) every('guard', 'lower', `${side} knee bend °`, (v) => v >= 10 && v <= 40, (w) => M.hinge(w, side + '_knee', side + '_leg').bend);
  }
  if (slot === 'guard_spear') {
    every('guard_spear', 'upper', 'shaft above level °', (v) => v >= 20 && v <= 60, (w) => rise(weapon(w, cls).dir));
    every('guard_spear', 'upper', 'spear tip forward of the fist by', (v) => v > 0, (w) => weapon(w, cls).fist[2] - weapon(w, cls).tip[2]);
    m.rise = mean((w) => rise(weapon(w, cls).dir));
    // the lower hand on the shaft at the waist or above (WAIST over the hips' joint: the belt, not the crotch), and the
    // spear pointing ahead (its tip within 45° of straight ahead, as the guard's blade), not across the body
    every('guard_spear', 'upper', 'lower hand above the waist by', (v) => v >= 0, (w) => Math.min(weapon(w, cls).fist[1], shield ? Infinity : leftFist(w)[1]) - hipsOf(w)[1] - WAIST);
    every('guard_spear', 'upper', 'spear tip from straight ahead °', (v) => Math.abs(v) <= 45, (w) => aim(weapon(w, cls).tip));
    m.spearAim = mean((w) => aim(weapon(w, cls).tip));
    if (!shield) {
      every('guard_spear', 'upper', 'left hand from the shaft', (v) => v <= 2.5, (w) => toSegment(leftFist(w), weapon(w, cls).butt, weapon(w, cls).tip));
      m.onShaft = mean((w) => toSegment(leftFist(w), weapon(w, cls).butt, weapon(w, cls).tip));
    }
  }
  if (slot === 'guard_bow') {
    every('guard_bow', 'upper', 'bow from upright °', (v) => v <= 30, (w) => bowTilt(bowAt(w)));
    every('guard_bow', 'upper', 'bow grip under the chin by', (v) => v > 0, (w) => chinOf(w)[1] - bowAt(w).grip[1]);
    every('guard_bow', 'upper', 'drawing hand from the string', (v) => v <= 6, (w) => toSegment(leftFist(w), ...bowAt(w).string));
    m.upright = mean((w) => bowTilt(bowAt(w)));
    m.belowChin = mean((w) => chinOf(w)[1] - bowAt(w).grip[1]);
    m.toString = mean((w) => toSegment(leftFist(w), ...bowAt(w).string));
  }
  if (slot === 'shield_brace' || slot === 'shield_brace_spear') {
    if (idleHips !== null) {
      m.hipsDown = idleHips - mean((w) => hipsOf(w)[1]);
      if (m.hipsDown < 1.5) fail('shield_brace', 'lower', `hips ${m.hipsDown.toFixed(2)} px below idle (≥ 1.5)`);
    }
    m.lead = Math.abs(mean((w) => footOf(w, 'right')[2] - footOf(w, 'left')[2]));
    if (m.lead < 3) fail('shield_brace', 'lower', `lead foot ${m.lead.toFixed(2)} px ahead (≥ 3)`);
  }
  if (slot === 'shield_brace_spear') {
    every('shield_brace_spear', 'upper', 'shaft from level °', (v) => Math.abs(v) <= 15, (w) => rise(weapon(w, cls).dir));
    m.level = mean((w) => rise(weapon(w, cls).dir));
  }
  if (slot === 'spear_brace') {
    every('spear_brace', 'upper', 'shaft above level °', (v) => v >= 5 && v <= 25, (w) => rise(weapon(w, cls).dir));
    every('spear_brace', 'upper', 'spear tip in front by', (v) => v >= 28, (w) => -weapon(w, cls).tip[2]);
    every('spear_brace', 'upper', 'left hand from the shaft', (v) => v <= 2.5, (w) => toSegment(leftFist(w), weapon(w, cls).butt, weapon(w, cls).tip));
    m.lunge = mean((w) => rise(weapon(w, cls).dir));
    m.reach = mean((w) => -weapon(w, cls).tip[2]);
    m.onShaft = mean((w) => toSegment(leftFist(w), weapon(w, cls).butt, weapon(w, cls).tip));
    if (idleHips !== null) {
      m.braceDown = idleHips - mean((w) => hipsOf(w)[1]);
      if (m.braceDown < 2) fail('spear_brace', 'lower', `hips ${m.braceDown.toFixed(2)} px below idle (≥ 2)`);
    }
    m.apart = Math.abs(mean((w) => footOf(w, 'right')[2] - footOf(w, 'left')[2]));
    if (m.apart < 4) fail('spear_brace', 'lower', `feet ${m.apart.toFixed(2)} px apart front to back (≥ 4)`);
  }
  // shields (test/shield.test.js's checks, and the spec's): the plate faces the front, covers the chest, leaves the face
  // clear, and the weapon arm does not pass through it
  if (shield) {
    let facing = 0;
    for (const plate of PLATES) for (const f of fs0) {
      const p = plateAt(f.w, plate);
      facing = Math.max(facing, p.angle);
      if (p.angle > 35) fail('shield', 'upper', `${plate.kind} shield faces ${p.angle.toFixed(0)}° from the front`);
      const c = point(f.w.body, [0, 18, 0]);
      if (!blocks(p.faces, [c[0], c[1], c[2] - 16], c)) fail('shield', 'upper', `${plate.kind} shield leaves the chest open`);
      // the face clear: at least FACE_SEEN of it seen from straight ahead past the plate (FACE: points over the head's front),
      // judged on the element shields (103 of Middle-earth's 106): the three entity-model shields are 22 px tall, and strapped
      // to cover the chest (gecko/shield.js place) their top stands 3.5 px above the eyes in every shield clip
      if (plate.kind === 'element') {
        const seen = FACE.filter((q) => { const e = point(f.w.head, q); return !blocks(p.faces, [e[0], e[1], e[2] - 16], e); }).length;
        if (seen < FACE_SEEN * FACE.length) fail('shield', 'upper', `${plate.kind} shield hides the face (${seen} of ${FACE.length} points seen)`);
      }
      if (blocks(p.faces, point(f.w.right_elbow, [6, 18, 0]), point(f.w.weapon_r, FIST.right))) fail('shield', 'upper', `${plate.kind} shield: the weapon arm passes through the plate`);
      // the spear over the shield's top edge: judged on the element shields too, as the face is (the entity-model shields'
      // top stands above the eyes; only a fist held over the head clears it)
      if (slot === 'shield_brace_spear' && plate.kind === 'element') {
        const wp = weapon(f.w, cls), front = Math.min(...p.faces.flat().map((q) => q[2])), top = Math.max(...p.faces.flat().map((q) => q[1]));
        if (front - wp.tip[2] < 20) fail('shield_brace_spear', 'upper', `spear tip ${(front - wp.tip[2]).toFixed(1)} px in front of the ${plate.kind} shield (≥ 20)`);
        if (blocks(p.faces, wp.butt, wp.tip)) fail('shield_brace_spear', 'upper', `spear shaft through the ${plate.kind} shield`);
        // where the shaft passes the plate's front, it is above the plate's top edge
        const s = Math.abs(wp.dir[2]) > 1e-6 ? (front - wp.fist[2]) / wp.dir[2] : 0, y = wp.fist[1] + wp.dir[1] * s;
        if (s > 0 && y < top) fail('shield_brace_spear', 'upper', `spear shaft passes under the ${plate.kind} shield's top`);
        m.overShield = Math.min(m.overShield === undefined ? Infinity : m.overShield, front - wp.tip[2]);
      }
    }
    m.facing = facing;
  }
  // march
  if (!rule.still) {
    const walk = ctx.walk ? framesOf(ctx.walk) : null;
    if (walk) {
      const first = (x) => Math.sign(lead(x)[0]);
      if (first(fs0) !== first(walk)) fail('march', 'lower', 'leads with the other foot than walk');
      const cw = cyclesOf(walk), cm = cyclesOf(fs0);
      if (cw !== cm) fail('march', 'lower', `${cm} gait cycles a loop, walk ${cw}`);
      m.swing = armSwing(fs0, shield ? ['right'] : ['right', 'left']);
      m.walkSwing = armSwing(walk);
      if (m.swing > 0.6 * m.walkSwing + 1e-9) fail('march', 'upper', `arms swing ${m.swing.toFixed(0)}° (≤ 60% of walk's ${m.walkSwing.toFixed(0)}°)`);
      m.skate = skate(fs0);
      m.walkSkate = skate(walk);
      if (m.skate > m.walkSkate + 1e-6) fail('march', 'lower', `feet skate ${m.skate.toFixed(1)} px/s (walk ${m.walkSkate.toFixed(1)})`);
    }
    m.lean = Math.max(...ws.map((w) => torsoLean(w, ctx.hunch || 0)));
    if (m.lean > 8) fail('march', 'upper', `torso ${m.lean.toFixed(1)}° from upright`);
    // the weapon steady against the chest: its tip's path in the chest's frame, per gait cycle
    const n = Math.max(1, cyclesOf(fs0)), L = anim.animation_length;
    m.carry = 0;
    for (let k = 0; k < n; k++) {
      const part = fs0.filter((f) => f.t >= (k * L) / n - 1e-9 && f.t <= ((k + 1) * L) / n + 1e-9);
      m.carry = Math.max(m.carry, spread(part.map((f) => local(f.w.body, tipOf(f.w)))));
    }
    if (m.carry > 4) fail('march', 'upper', `weapon tip moves ${m.carry.toFixed(1)} px a stride against the chest (≤ 4)`);
    m.irregular = steps(fs0).irregular;
    if (slot === 'march_spear') {
      every('march_spear', 'upper', 'spear from vertical °', (v) => v <= 20, (w) => deg(Math.acos(Math.max(-1, Math.min(1, weapon(w, cls).dir[1])))));
      every('march_spear', 'upper', 'spear butt below the hip by', (v) => v > 0, (w) => hipsOf(w)[1] - weapon(w, cls).butt[1]);
    }
    if (slot === 'march_bow') {
      every('march_bow', 'upper', 'bow limb axis from vertical °', (v) => v <= 30, (w) => bowTilt(bowAt(w)));
      // the bow hand: the fist (the game seats the bow's grip on the forearm's inner side, 2.25 px in from it)
      every('march_bow', 'upper', 'bow hand out at the side by', (v) => v >= 4, (w) => local(w.body, point(w.weapon_r, FIST.right))[0]);
      every('march_bow', 'upper', 'bow hand below the armpit by', (v) => v > 0, (w) => 20 - local(w.body, point(w.weapon_r, FIST.right))[1]);
    }
  }
  return { fails, measures: m, score: score(slot, m, shield) };
}

/** The key-pose score (spec "Score"; lower is better). Still stances: the mean, over the slot's key measures and the sway,
 *  of each one's distance from the middle of its range over half the range (KEY). March: weapon movement, then torso lean,
 *  then stride irregularity, each over its limit, weighed 1, 1/2 and 1/4 (a strict order on measures that never tie would
 *  let a hundredth of a px of weapon movement outweigh any lean). */
function score(slot, m, shield) {
  if (!SLOT[slot].still) return (m.carry || 0) / 4 + (0.5 * (m.lean || 0)) / 8 + (0.25 * (m.irregular || 0)) / 0.1;
  const d = (k, v = m[k]) => { const [lo, hi] = KEY[k]; return v === undefined ? null : Math.abs(v - (lo + hi) / 2) / ((hi - lo) / 2); };
  const keys = {
    guard: ['tipZ', 'tipUp', 'tipAim', 'knees'],
    guard_spear: shield ? ['rise', 'spearAim'] : ['rise', 'spearAim', 'onShaft'],
    guard_bow: ['upright', 'belowChin', 'toString'],
    shield_brace: ['facing', 'hipsDown', 'lead'],
    shield_brace_spear: ['facing', 'hipsDown', 'lead', 'level', 'overShield'],
    spear_brace: ['lunge', 'reach', 'onShaft', 'braceDown', 'apart'],
  }[slot];
  const ds = keys.map((k) => d(k)).filter((x) => x !== null);
  if (m.sway !== undefined) ds.push(Math.max(0, m.sway - SWAY[0]) / (SWAY[1] - SWAY[0]));
  return ds.reduce((a, b) => a + b, 0) / (ds.length || 1);
}

module.exports = { BOW_REST, SLOT, CLIPS, RULES, LOWER_BONES, SHIELD_VERSIONS, KEY, SWAY, judge, score, slotOf, shieldOf, lengthOf, weapon, bowAt, footOf, hipsOf, cyclesOf, armSwing, skate, steps, torsoLean, PLATES, plateAt };
