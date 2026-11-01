import test from 'node:test';
import assert from 'node:assert/strict';
import { Model } from '../vendor/bbmt/dist/index.js';
import { toLegacyBbmodel } from '../lib/bbmodel.mjs';
import { retarget } from './helpers.mjs';

test("toolkit model round-trips through the viewer's rig reader", () => {
  const model = new Model({ name: 't', resolution: [16, 16] });
  model.bone('root', [0, 0, 0]);
  model.bone('body', [0, 12, 0], 'root');
  model.cube({ name: 'torso', from: [-4, 0, -2], to: [4, 12, 2], bone: 'body' });
  const rig = retarget.parseRig(toLegacyBbmodel(model));
  assert.equal(rig.bones.length, 2);
  assert.deepEqual(rig.bones.map((b) => b.name), ['root', 'body']);
});

test('legacy layout has no groups list and keeps bone pivots in the outliner', () => {
  const model = new Model({ name: 't', resolution: [16, 16] });
  model.bone('root', [0, 0, 0]);
  model.bone('body', [0, 12, 0], 'root');
  const doc = toLegacyBbmodel(model);
  assert.equal(doc.groups, undefined);
  assert.equal(doc.meta.format_version, '4.10');
  assert.deepEqual(doc.outliner[0].children[0].origin, [0, 12, 0]);
});
