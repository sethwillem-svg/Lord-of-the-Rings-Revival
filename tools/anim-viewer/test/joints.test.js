'use strict';
/* Twist: the turn a limb makes about its own length, which a hinge cannot show. Held to human ranges on every clip of every
 * race (attacks with their _l and _shield versions, loops, held poses, the rest), measured with test/lib/motion.js only
 * (twist: the swing-twist split about the bone's rest y axis). Ranges: the upper arm against the body (humeral rotation) about 70°
 * internal and 90° external; the hand, and the weapon it holds, against the forearm (pronation and supination) about
 * 85-90°; the thigh against the hips well under 45°. Past them the elbow's fold points the wrong way on the body (the arm
 * reads as broken) or the item rolls over in the hand. Within 5° of a half-turn swing (an arm straight up) the twist about
 * the rest axis is not defined, so those samples are not judged. Each failure names the clip, the time and the value. */
const test = require('node:test');
const M = require('./lib/motion.js');
const { samplePose, worldBones } = require('../gecko/sample.js');
const { RACES } = require('../npc_slots.js');
const { report } = require('./lib/swing.js');
const { shipped } = require('./lib/shipped.js');   // the files the game ships

/** Sampling step (s): every millisecond, so a turn between two keys, however close, is seen (the 60 Hz frame cache would
 *  step over one); and the window a turn's speed is judged over, about one 120 Hz frame (8 ms). */
const STEP = 0.001, WINDOW = 8;
const PAIRS = [['right_arm', 'body'], ['left_arm', 'body'], ['weapon_r', 'right_elbow'], ['weapon_l', 'left_elbow'], ['right_leg', 'lower'], ['left_leg', 'lower']];
const clips = [];   // { race, name, t: [s], by: { bone: { twist, swing, q } } } sampled every STEP
for (const race of RACES) for (const [name, anim] of Object.entries(shipped[race].animations.animations)) {
  const L = anim.animation_length || 0, t = [];
  for (let i = 0; i * STEP <= L + 1e-9; i++) t.push(i * STEP);
  const ws = t.map((x) => worldBones(samplePose(anim, Math.min(x, L))));
  const by = {};
  for (const [bone, parent] of PAIRS) {
    if (!ws[0][bone] || !ws[0][parent]) continue;
    by[bone] = { twist: ws.map((w) => M.twist(w, bone, parent)), swing: ws.map((w) => M.swing(w, bone, parent)), q: ws.map((w) => M.localQuat(w, bone, parent)) };
  }
  clips.push({ race, name, t, by });
}
const POLE = 175;   // within 5° of a half-turn swing the twist is not defined: not judged

/** Every clip whose bone twists past `limit` against its parent, at its worst moment. */
function over(bones, limit) {
  const bad = [];
  for (const c of clips) for (const bone of bones) {
    const s = c.by[bone];
    if (!s) continue;
    let worst = null;
    s.twist.forEach((x, i) => { if (s.swing[i] <= POLE && (!worst || Math.abs(x) > Math.abs(worst.x))) worst = { x, i }; });
    if (worst && Math.abs(worst.x) > limit) bad.push(`${c.race} ${c.name} t=${c.t[worst.i].toFixed(3)} ${bone}: twist ${worst.x.toFixed(0)}°`);
  }
  return bad;
}

/** The angle of the turn between two unit quaternions (degrees). */
const turnAngle = (a, b) => M.deg(2 * Math.acos(Math.min(1, Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]))));

/**
 * Every clip whose bone turns faster than `limit` degrees per WINDOW ms, at its fastest: by `what`, either its whole turn
 * against its parent (any direction) or the path its twist takes (about its own length; not judged where the swing comes
 * within 20° of a half turn, where twist swings round however little the limb moves).
 */
function faster(bones, limit, what) {
  const bad = [];
  for (const c of clips) for (const bone of bones) {
    const s = c.by[bone];
    if (!s) continue;
    let worst = { d: 0 };
    for (let i = 0; i + WINDOW < c.t.length; i++) {
      let d = 0;
      if (what === 'turn') d = turnAngle(s.q[i], s.q[i + WINDOW]);
      else {
        if (s.swing.slice(i, i + WINDOW + 1).some((x) => x > 160)) continue;
        for (let j = i + 1; j <= i + WINDOW; j++) { const e = s.twist[j] - s.twist[j - 1]; d += Math.abs(e - 360 * Math.round(e / 360)); }
      }
      if (d > worst.d) worst = { d, i };
    }
    if (worst.d > limit) bad.push(`${c.race} ${c.name} t=${c.t[worst.i].toFixed(3)} ${bone}: ${what} ${worst.d.toFixed(0)}° in ${WINDOW} ms`);
  }
  return bad;
}

test('upper arms turn about their own length within a shoulder’s range: |twist| ≤ 95° against the body', () => {
  report(over(['right_arm', 'left_arm'], 95));
});

test('the held item turns about the forearm within a wrist’s range: |twist| ≤ 95° against the elbow', () => {
  report(over(['weapon_r', 'weapon_l'], 95));
});

test('thighs turn about their own length at most 45° against the hips', () => {
  report(over(['right_leg', 'left_leg'], 45));
});

/* How fast: at most 60° in 1/120 s (7200°/s), about the fastest humeral rotation measured in people (a baseball pitch,
 * some 7000°/s) and above every turn the build made before it limited twist (worst 50.8° turn and 55.7° of twist, the
 * orc's wild dagger swings). A limb that turns faster has snapped round between two poses. */
const FASTEST = 60;
test('arms and held items never snap round: each turns at most 60° in 1/120 s against its parent', () => {
  report(faster(['right_arm', 'left_arm', 'weapon_r', 'weapon_l'], FASTEST, 'turn'));
});

test('nor twist round: the twist about their own length moves at most 60° in 1/120 s', () => {
  report(faster(['right_arm', 'left_arm', 'weapon_r', 'weapon_l'], FASTEST, 'twist'));
});
