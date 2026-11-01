import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { buildModel } from '../lib/build.mjs';
import { toLegacyBbmodel } from '../lib/bbmodel.mjs';
import { retarget as A, RIG_MAPS, REPO } from './helpers.mjs';

import { MAN } from '../races/man.mjs';
const CLIPS = ['82_09', '02_05'].map((id) => [id, A.parseBVH(readFileSync(path.join(REPO, 'tools/anim-viewer/library/cmu', `${id}.bvh`), 'utf8'))]);
const OPTS = { footPlant: true, inPlace: true, jointLimits: true, footLock: true, wrist: 'auto' };

test('cmu walk and punch fit every committed seed', () => {
  for (const seed of MAN.showcase) {
    const { model } = buildModel('man', seed);
    const rig = A.parseRig(toLegacyBbmodel(model));
    const map = A.detectMap(rig, RIG_MAPS);
    assert.equal(map && map.id, 'spec-biped', `seed ${seed}: rig map`);
    for (const [id, bvh] of CLIPS) {
      const r = A.retarget(bvh, rig, map, OPTS);
      assert.ok(r.qa.footMin >= -1, `seed ${seed} clip ${id}: feet ${r.qa.footMin.toFixed(2)} px under the floor`);
    }
  }
});
