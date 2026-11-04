'use strict';
/* Anatomy of the attacks: where the upper arms point against the torso, and the arms kept out of the body. Every attack of
 * every race with its _l and _shield twins, both arms, sampled every millisecond (as test/joints.test.js), measured with
 * test/lib/anatomy.js only. Each failure names the clip, the time, the arm and the value; failures are held to the
 * 'shoulder' and 'body' lists of test/lib/known.js.
 *
 * Shoulder range (the usual clinical tables): flexion 180°, extension 60°, abduction 180°, horizontal adduction 45° past
 * straight ahead, as a region of upper-arm directions in torso space (lib/anatomy.js shoulder: a cap behind the back and
 * one across the chest the arm may not point into). Slack 10°: the tables differ among themselves by about that much (AAOS,
 * AMA and the Norwegian norms put extension at 50-60° and horizontal adduction at 40-50°); the rig's torso is one rigid box,
 * so the shoulder blade's own glide, which adds some 10-15° at the ends of the range in people, is not there to take it
 * up; and the region's back cap lets the level arm only 35° behind the frontal plane where the tables give 45°. So past
 * 10° the arm is out of any normal range, and within it the pose is still one a person makes. Judged over the whole clip.
 *
 * Out of the body: the forearm and fist (a capsule 2 px round: the arms are 4 px thick) may not sink into the torso box
 * (8×12×4 px) or the head box (8×8×8) by more than 0.5 px, nor the upper arm's lower half into the torso, once the attack
 * has faded in (before that the game blends from the pose it came from). The upper half, where the arm attaches at the
 * shoulder, is not judged. The boxes sit on the rig's pivots (gecko/rig.js): the torso from the neck (body pivot, y 24)
 * down to the hips (y 12), the head from the neck up; the arm boxes on the shoulder (x ±6 at the box's axis). The blade has
 * its own test (motion.test.js). */
const test = require('node:test');
const { samplePose, worldBones } = require('../gecko/sample.js');
const { RACES } = require('../npc_slots.js');
const A = require('./lib/anatomy.js');
const { ratchet } = require('./lib/known.js');
const { baseName } = require('./lib/swing.js');
const { shipped } = require('./lib/shipped.js');   // the files the game ships

const STEP = 0.001, SLACK = 10, DEPTH = 0.5, SIDES = ['right', 'left'];
const clips = [];   // { race, name, fadeIn, t: [s], w: [world bones] } sampled every STEP
for (const race of RACES) for (const [name, anim] of Object.entries(shipped[race].animations.animations)) {
  if (!/^attack_/.test(name)) continue;
  const L = anim.animation_length || 0, t = [];
  for (let i = 0; i * STEP <= L + 1e-9; i++) t.push(i * STEP);
  clips.push({ race, name, fadeIn: shipped[race].hits[baseName(name)].fadeIn, t, w: t.map((x) => worldBones(samplePose(anim, Math.min(x, L)))) });
}

test('upper arms stay within a shoulder’s range: extension 60°, horizontal adduction 45° past straight ahead (10° slack)', () => {
  const bad = [];
  for (const c of clips) {
    let worst = null;
    c.w.forEach((w, i) => {
      for (const side of SIDES) {
        const s = A.shoulder(w, side);
        for (const k of ['back', 'across']) {
          const past = s[k] - A.SHOULDER[k];
          if (!worst || past > worst.past) worst = { past, k, v: s[k], side, i };
        }
      }
    });
    if (worst.past > SLACK) {
      const what = worst.k === 'back' ? 'reaches back' : 'reaches across';
      bad.push({ key: `${c.race}/${c.name}`, msg: `${c.race} ${c.name} t=${c.t[worst.i].toFixed(3)} ${worst.side} arm ${what} ${worst.v.toFixed(0)}° (limit ${A.SHOULDER[worst.k]}°)` });
    }
  }
  ratchet('shoulder', bad);
});

test('arms stay out of the body once faded in: forearm and fist out of the torso and head, the upper arm’s lower half out of the torso (≤ 0.5 px)', () => {
  const bad = [];
  for (const c of clips) {
    let worst = null;
    c.w.forEach((w, i) => {
      if (c.t[i] < c.fadeIn - 1e-9) return;
      for (const side of SIDES) {
        const o = A.overlap(w, side);
        if (!worst || o.d > worst.d) worst = { ...o, i };
      }
    });
    if (worst && worst.d > DEPTH) bad.push({ key: `${c.race}/${c.name}`, msg: `${c.race} ${c.name} t=${c.t[worst.i].toFixed(3)}: ${worst.what} ${worst.d.toFixed(2)} px deep` });
  }
  ratchet('body', bad);
});
