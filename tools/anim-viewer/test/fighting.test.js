'use strict';
/* Each race fights its own way (npc_slots.js STYLE.fight), and shields change the clips: checked by measurement. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const { strike, blade, FIST } = require('../gecko/strike.js');
const { classLength } = require('../build_npc.js');
const { RACES } = require('../npc_slots.js');

const { shipped: built } = require('./lib/shipped.js');   // the files the game ships
const ATTACK = /^attack_(sword|dagger|unarmed)_\d+$/;   // one-handed: every style applies in full
const anim = (race, name) => built[race].animations.animations[name];
const attacks = (race) => Object.keys(built[race].animations.animations).filter((n) => ATTACK.test(n));
const mean = (xs) => xs.reduce((a, b) => a + b, 0) / xs.length;
const over = (race, f) => mean(attacks(race).map((n) => f(anim(race, n), n)));

/** How far a rig point travels over a clip (px). */
function path(a, bone, p) {
  let d = 0, prev = null;
  for (let t = 0; t <= a.animation_length + 1e-9; t += a.animation_length / 40) {
    const q = point(worldBones(samplePose(a, t))[bone], p);
    if (prev) d += Math.hypot(q[0] - prev[0], q[1] - prev[1], q[2] - prev[2]);
    prev = q;
  }
  return d;
}
/** How far back the weapon is drawn: the tip's furthest distance behind its strike position before the hit. */
function windup(a, n) {
  const s = strike(a, classLength(n));
  let far = 0;
  for (let t = 0; t < s.hit; t += 1 / 60) {
    const q = point(worldBones(samplePose(a, t)).weapon_r, blade('right', classLength(n), 1));
    far = Math.max(far, Math.hypot(...q.map((x, i) => x - s.tip[i])));
  }
  return far;
}

test('orcs swing wild: bigger windups and a free arm that flails more than men', () => {
  assert.ok(over('orc', windup) > over('man', windup) * 1.08, `windup orc ${over('orc', windup).toFixed(1)} man ${over('man', windup).toFixed(1)}`);
  const flail = (r) => over(r, (a) => path(a, 'weapon_l', FIST.left));
  assert.ok(flail('orc') > flail('man') * 1.25, `free hand orc ${flail('orc').toFixed(1)} man ${flail('man').toFixed(1)}`);
});

test('elves strike at full stretch: the tip lands further out than a man\'s, and the free arm is held out wide', () => {
  const reach = (r) => over(r, (a, n) => -strike(a, classLength(n)).tip[2]);
  assert.ok(reach('elf') > reach('man') + 1, `elf ${reach('elf').toFixed(1)} man ${reach('man').toFixed(1)}`);
  const wide = (r) => over(r, (a, n) => -point(worldBones(samplePose(a, strike(a, classLength(n)).hit)).weapon_l, FIST.left)[0]);
  assert.ok(wide('elf') > wide('man') + 2, `free hand out elf ${wide('elf').toFixed(1)} man ${wide('man').toFixed(1)}`);
});

test('dwarves drive with the body: more torso twist than men', () => {
  const twist = (r) => over(r, (a, n) => strike(a, classLength(n)).bodyYawRange);
  assert.ok(twist('dwarf') > twist('man') * 1.15, `dwarf ${twist('dwarf').toFixed(0)}° man ${twist('man').toFixed(0)}°`);
});

test('hobbits strike low and compact: lower strikes and a shorter windup than men', () => {
  const height = (r) => over(r, (a, n) => strike(a, classLength(n)).tip[1]);
  assert.ok(height('hobbit') < height('man') - 2, `strike height hobbit ${height('hobbit').toFixed(1)} man ${height('man').toFixed(1)}`);
  assert.ok(over('hobbit', windup) < over('man', windup) * 0.95, `windup hobbit ${over('hobbit', windup).toFixed(1)} man ${over('man', windup).toFixed(1)}`);
});

// ---- shields ----
const SHIELDED = ['idle', 'walk', 'run', 'guard', 'march', 'attack_sword_1', 'attack_spear_1', 'attack_axe_1', 'attack_dagger_1'];

test('every movement clip and attack has a shield version', () => {
  for (const race of RACES) for (const n of SHIELDED) assert.ok(anim(race, n + '_shield'), `${race} ${n}_shield`);
});

/** A posed rig point in the chest's own frame (the body bone's rest frame), so turning with the torso does not count as moving. */
function inChest(w, p) {
  const v = [0, 1, 2].map((i) => p[i] - w.body.p[i]);
  return [0, 1, 2].map((c) => w.body.R[c] * v[0] + w.body.R[3 + c] * v[1] + w.body.R[6 + c] * v[2]);
}

test('with a shield the off arm holds it up in front of the chest and steady, whatever the other arm does', () => {
  for (const race of RACES) for (const n of SHIELDED) {
    const a = anim(race, n + '_shield'), pts = [];
    for (let t = 0; t <= a.animation_length + 1e-9; t += a.animation_length / 12) {
      const w = worldBones(samplePose(a, t));
      pts.push(inChest(w, point(w.weapon_l, FIST.left)));
    }
    for (const p of pts) assert.ok(p[2] < -3 && p[1] > 12 && p[1] < 26, `${race} ${n}_shield: shield hand not up in front (${p.map((x) => x.toFixed(1))})`);
    const spread = Math.max(...[0, 1, 2].map((i) => Math.max(...pts.map((p) => p[i])) - Math.min(...pts.map((p) => p[i]))));
    assert.ok(spread < 1, `${race} ${n}_shield: shield hand wanders ${spread.toFixed(1)} px`);
  }
});

test('the shield version swings the weapon exactly like the plain one (same hit)', () => {
  for (const race of RACES) for (const n of SHIELDED.filter((x) => x.startsWith('attack_'))) {
    const a = strike(anim(race, n), classLength(n)), b = strike(anim(race, n + '_shield'), classLength(n));
    assert.equal(a.hit, b.hit, `${race} ${n}`);
  }
});
