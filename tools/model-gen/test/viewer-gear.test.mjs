import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { buildModel } from '../lib/build.mjs';
import { buildWeapon } from '../gear/weapons.mjs';
import { buildArmour, ATTACH } from '../gear/armour.mjs';
import { toLegacyBbmodel } from '../lib/bbmodel.mjs';
import { retarget as A, RIG_MAPS, REPO } from './helpers.mjs';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const G = require(path.join(REPO, 'tools/anim-viewer/gear.js'));
const man = () => toLegacyBbmodel(buildModel('man', 13).model);
const cubesOn = (bb, name) => {
  const rig = A.parseRig(bb);
  return rig.bones[rig.idx[name]].cubes;
};

test('a weapon lands on right_item with its grip at the item bone', () => {
  const base = man(), item = A.parseRig(base).bones.find((b) => b.name === 'right_item');
  const out = G.attachGear(base, [{ bb: toLegacyBbmodel(buildWeapon('longsword').model), hand: 'right_item' }]);
  const cubes = cubesOn(out, 'right_item');
  assert.equal(cubes.length, buildWeapon('longsword').model.cubes.length);
  const grip = out.elements.find((e) => e.name === 'longsword:grip');
  assert.deepEqual(grip.from.map((v, a) => (v + grip.to[a]) / 2), item.origin);
  assert.equal(out.textures.length, 2);
  assert.equal(out.textures[1].uv_width, 64);
});

test('the base model is not changed', () => {
  const base = man(), before = JSON.stringify(base);
  G.attachGear(base, [{ bb: toLegacyBbmodel(buildWeapon('spear').model), hand: 'right_item' }]);
  assert.equal(JSON.stringify(base), before);
});

test('armour groups land on the anchors, boot armour on the shin', () => {
  const out = G.attachGear(man(), [{ bb: toLegacyBbmodel(buildArmour('gondor').model), attach: ATTACH }]);
  assert.ok(cubesOn(out, 'head_armor').length > 0);
  assert.ok(cubesOn(out, 'chest_armor').length > 0);
  assert.ok(cubesOn(out, 'right_shin').some((c) => /greave/.test(c.name)));
  assert.equal(cubesOn(out, 'right_boot_armor').length, 0);
});

test('weapon IK finds the attached weapon tip', () => {
  const out = G.attachGear(man(), [{ bb: toLegacyBbmodel(buildWeapon('longsword').model), hand: 'right_item' }]);
  const rig = A.parseRig(out), map = A.detectMap(rig, RIG_MAPS);
  const bvh = A.parseBVH(readFileSync(path.join(REPO, 'tools/anim-viewer/library/cmu/02_07.bvh'), 'utf8'));
  const r = A.retarget(bvh, rig, map, { ik: true, footPlant: true, inPlace: true });
  assert.ok(Number.isFinite(r.qa.tipMax), 'tip found');
  const bare = A.retarget(bvh, A.parseRig(man()), map, { ik: true, footPlant: true, inPlace: true });
  assert.ok(!Number.isFinite(bare.qa.tipMax) || bare.qa.tipMax !== r.qa.tipMax);
});
