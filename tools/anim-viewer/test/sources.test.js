'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { importSeriousPlayer } = require('../import/seriousplayer.js');
const { importEmote } = require('../import/emotecraft.js');
const { importMoBends } = require('../import/mobends.js');
const { RIG } = require('../gecko/rig.js');

const lib = (p) => JSON.parse(fs.readFileSync(path.join(__dirname, '../library', p), 'utf8'));
const BONES = new Set(RIG.map((b) => b.name));
const rigOnly = (clips) => { for (const c of Object.values(clips)) for (const b of Object.keys(c.bones)) assert.ok(BONES.has(b), b); };
const first = (keys) => keys[0].v;
const near = (a, b, eps) => a.every((x, i) => Math.abs(x - b[i]) < eps);
const spa = (bones) => importSeriousPlayer({ format_version: '1.8.0', animations: { a: { animation_length: 0.5, bones } } }).a;

test('Serious Player sword_attack keeps its length and keys the weapon', () => {
  const c = importSeriousPlayer(lib('seriousplayer/sword_attack.json')).sword_attack;
  assert.equal(c.length, 0.875);
  assert.equal(c.loop, false);
  assert.ok(c.bones.weapon_r.rotation.length > 0);
  assert.ok(c.bones.right_arm.rotation.length > 0);
});

test('Serious Player walking loops and uses only rig bones', () => {
  const clips = importSeriousPlayer(lib('seriousplayer/walking.json'));
  rigOnly(clips);
  assert.equal(Object.values(clips)[0].loop, true);
});

test('a raised Minecraft arm stays raised on the rig', () => {
  const c = spa({ rightArm: { rotation: { '0.0': { vector: [-90, 0, 0] } } } });
  assert.ok(near(first(c.bones.right_arm.rotation), [-90, 0, 0], 1e-3), String(first(c.bones.right_arm.rotation)));
});

test('arms that turn with the torso are still relative to the rig body', () => {
  const c = spa({ torso: { rotation: { '0.0': { vector: [0, 30, 0] } } }, rightArm: { rotation: { '0.0': { vector: [0, 30, 0] } } } });
  assert.ok(near(first(c.bones.body.rotation), [0, 30, 0], 1e-3));
  const r = c.bones.right_arm && c.bones.right_arm.rotation;
  assert.ok(!r || near(first(r), [0, 0, 0], 1e-3));
});

test('Emotecraft sit bends knees and an elbow and uses only rig bones', () => {
  const clips = importEmote(lib('emotecraft/bend_sit.json'));
  rigOnly(clips);
  const c = Object.values(clips)[0];
  assert.equal(c.loop, true);
  assert.equal(c.length, 3);
  assert.ok(c.bones.right_knee.rotation.some((k) => Math.abs(k.v[0]) > 45));
  assert.ok(c.bones.right_elbow.rotation.some((k) => Math.abs(k.v[0]) > 30));
});

test("Mo' Bends slash_down keys the forearm and uses only rig bones", () => {
  const clips = importMoBends(lib('mobends/slash_down.json'), 'slash_down');
  rigOnly(clips);
  const c = clips.slash_down;
  assert.ok(c.bones.right_elbow.rotation.length > 0);
  assert.ok(c.bones.upper.rotation.length > 0);
  assert.equal(c.loop, false);
  assert.ok(Math.abs(c.length - 0.5) < 1e-9);   // 31 frames at 60 fps
});
