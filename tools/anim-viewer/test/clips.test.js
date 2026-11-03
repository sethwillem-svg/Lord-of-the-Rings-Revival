'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { clipsFor, attackClass } = require('../gecko/clips.js');

const NAMES = ['idle', 'idle_2', 'walk', 'run', 'ride_idle', 'ride_move', 'guard', 'march', 'shield_brace', 'spear_brace', 'hurt', 'taunt_1',
  'bow_aim', 'bow_release', 'crossbow_reload', 'crossbow_aim', 'crossbow_shoot',
  'attack_sword_1', 'attack_sword_2', 'attack_sword_1_l', 'attack_spear_1', 'attack_spear_1_l', 'attack_unarmed_1', 'attack_shield_1', 'attack_dagger_1'];

test('a sword shows movement and sword attacks, not spear or ranged clips', () => {
  const c = clipsFor(NAMES, 'sword', null);
  for (const n of ['idle', 'walk', 'ride_move', 'guard', 'hurt', 'attack_sword_1', 'attack_sword_2']) assert.ok(c.includes(n), n);
  for (const n of ['attack_spear_1', 'bow_aim', 'crossbow_aim', 'spear_brace', 'shield_brace', 'attack_sword_1_l']) assert.ok(!c.includes(n), n);
});

test('classes without clips swing like the server falls back (longsword -> sword, bow -> unarmed)', () => {
  assert.equal(attackClass(NAMES, 'longsword'), 'sword');
  assert.equal(attackClass(NAMES, 'axe'), 'sword');
  assert.equal(attackClass(NAMES, 'bow'), 'unarmed');
  assert.equal(attackClass(NAMES, null), 'unarmed');
  assert.ok(clipsFor(NAMES, 'longsword', null).includes('attack_sword_1'));
});

test('a spear adds its brace; a bow its draw and release; a crossbow its reload, aim and shot', () => {
  assert.ok(clipsFor(NAMES, 'spear', null).includes('spear_brace'));
  const bow = clipsFor(NAMES, 'longbow', null);
  assert.ok(bow.includes('bow_aim') && bow.includes('bow_release') && bow.includes('attack_unarmed_1'));
  const xb = clipsFor(NAMES, 'crossbow', null);
  for (const n of ['crossbow_reload', 'crossbow_aim', 'crossbow_shoot']) assert.ok(xb.includes(n), n);
});

test('a shield in the off hand adds the brace and the bash', () => {
  const c = clipsFor(NAMES, 'spear', 'shield');
  assert.ok(c.includes('shield_brace') && c.includes('attack_shield_1'));
});

test('a weapon held in the left hand plays the left-handed twins', () => {
  const c = clipsFor(NAMES, 'spear', null, { leftHanded: true });
  assert.ok(c.includes('attack_spear_1_l') && !c.includes('attack_spear_1'));
});

test('"all" lists every clip', () => assert.deepEqual(clipsFor(NAMES, 'all', null), NAMES));

test('with a shield in the off hand the shield versions replace the plain clips', () => {
  const names = [...NAMES, 'idle_shield', 'walk_shield', 'attack_spear_1_shield', 'attack_sword_1_shield'];
  const c = clipsFor(names, 'spear', 'shield');
  assert.ok(c.includes('idle_shield') && c.includes('walk_shield') && c.includes('attack_spear_1_shield'));
  assert.ok(!c.includes('idle') && !c.includes('attack_spear_1') && !c.includes('attack_sword_1_shield'));
  assert.ok(!clipsFor(names, 'sword', null).some((n) => n.endsWith('_shield')));
});
