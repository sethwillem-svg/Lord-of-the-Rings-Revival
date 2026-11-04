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
const { RACES } = require('../npc_slots.js');
const { report } = require('./lib/swing.js');
const { shipped } = require('./lib/shipped.js');   // the files the game ships

const clips = [];   // { race, name, fs }
for (const race of RACES) for (const [name, anim] of Object.entries(shipped[race].animations.animations)) {
  clips.push({ race, name, fs: M.frames(anim, M.RATE, `${race}/${name}`) });
}

/** Every clip whose bone twists past `limit` against its parent, at its worst moment. */
function over(pairs, limit) {
  const bad = [];
  for (const c of clips) for (const [bone, parent] of pairs) {
    let worst = null;
    for (const f of c.fs) {
      if (!f.w[bone] || !f.w[parent] || M.swing(f.w, bone, parent) > 175) continue;
      const t = M.twist(f.w, bone, parent);
      if (!worst || Math.abs(t) > Math.abs(worst.t)) worst = { t, at: f.t };
    }
    if (worst && Math.abs(worst.t) > limit) bad.push(`${c.race} ${c.name} t=${worst.at.toFixed(2)} ${bone}: twist ${worst.t.toFixed(0)}°`);
  }
  return bad;
}

test('upper arms turn about their own length within a shoulder’s range: |twist| ≤ 95° against the body', () => {
  report(over([['right_arm', 'body'], ['left_arm', 'body']], 95));
});

test('the held item turns about the forearm within a wrist’s range: |twist| ≤ 95° against the elbow', () => {
  report(over([['weapon_r', 'right_elbow'], ['weapon_l', 'left_elbow']], 95));
});

test('thighs turn about their own length at most 45° against the hips', () => {
  report(over([['right_leg', 'lower'], ['left_leg', 'lower']], 45));
});
