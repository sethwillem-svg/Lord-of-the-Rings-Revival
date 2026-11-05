'use strict';
// The combat reactions the add-on plays (client.anim.ClientAttack: stagger on a parried swing, parry_shield / parry_blade on
// an NPC parry), hand-keyed in import/keyed.js.
const test = require('node:test');
const assert = require('node:assert/strict');
const { RACES } = require('../npc_slots.js');
const { buildRace, loadSources } = require('../build_npc.js');

const sources = loadSources();
const built = Object.fromEntries(RACES.map((r) => [r, buildRace(r, sources)]));
const NAMES = ['stagger', 'stagger_l', 'stagger_shield', 'parry_blade', 'parry_blade_l', 'parry_shield', 'parry_shield_l'];

for (const race of RACES) test(race + ' has the combat reactions, as one-shots on the upper body', () => {
  const anims = built[race].animations.animations;
  for (const name of NAMES) {
    const a = anims[name];
    assert.ok(a, name);
    assert.equal(a.loop, false, name);
    for (const leg of ['right_leg', 'left_leg', 'right_knee', 'left_knee', 'lower', 'root'])
      assert.ok(!a.bones[leg], `${name} keys ${leg}: the legs belong to the locomotion under the overlay`);
  }
  assert.ok(Math.abs(anims.stagger.animation_length - 1.0) < 1e-6, 'stagger lasts the 20-tick stagger');
  assert.ok(anims.parry_blade.animation_length <= 0.6 && anims.parry_shield.animation_length <= 0.6);
});

test('parry clips reach their guard and come back to rest', () => {
  const c = built.man.clips;
  const at = (clip, bone, t) => clip.bones[bone].rotation.find((k) => Math.abs(k.t - t) < 1e-9).v;
  const g = sources.animationsplus;
  const ends = (clip, bone) => {
    const keys = clip.bones[bone].rotation;
    assert.deepEqual(keys[0].v.map(Math.round), [0, 0, 0], bone + ' starts at rest');
    assert.deepEqual(keys[keys.length - 1].v.map(Math.round), [0, 0, 0], bone + ' ends at rest');
  };
  ends(c.parry_blade, 'right_arm');
  ends(c.parry_shield, 'left_arm');
  // held guard (0.34 s) is close to AnimationsPlus' block pose
  const near = (a, b, tol) => a.every((x, i) => Math.abs(x - b[i]) <= tol);
  assert.ok(near(at(c.parry_blade, 'right_arm', 0.34), g.blockR.bones.right_arm.rotation[0].v, 5));
  assert.ok(near(at(c.parry_shield, 'left_arm', 0.34), g.blockL.bones.left_arm.rotation[0].v, 5));
});

test('the left-handed parry mirrors the right', () => {
  const c = built.man.clips;
  const r = c.parry_blade.bones.right_arm.rotation, l = c.parry_blade_l.bones.left_arm.rotation;
  assert.equal(r.length, l.length);
  r.forEach((k, i) => assert.deepEqual(l[i].v, [k.v[0], -k.v[1], -k.v[2]]));
});
