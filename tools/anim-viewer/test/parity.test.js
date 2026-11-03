'use strict';
/* The viewer poses clips the way the game does: the game's own poses (PulseLib evaluating the race files, written by
 * ./addon/bench.sh export as addon/run/viewer_export/pose_samples.json; client.bench.PoseSamples) against gecko/sample.js.
 * Skipped until an export has written the file. A mismatch here means the viewer shows something the game does not:
 * mirrored or backwards limbs point at the axis conventions in gecko/write.js and client.anim.NpcPose. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const { RIG } = require('../gecko/rig.js');

const ASSETS = path.join(__dirname, '../../../addon/src/main/resources/assets/lotr_revival/geckolib/animations/entity');
const FILE = path.join(__dirname, '../../../addon/run/viewer_export/pose_samples.json');
const samples = fs.existsSync(FILE) ? JSON.parse(fs.readFileSync(FILE, 'utf8')) : null;
const PIVOT = Object.fromEntries(RIG.map((b) => [b.name, b.pivot]));
const TOLERANCE = 0.35;   // px

/** A column-major 4x4 (blocks) applied to a px point. */
const javaPoint = (m, p) => {
  const b = p.map((x) => x / 16);
  return [0, 1, 2].map((r) => (m[r] * b[0] + m[4 + r] * b[1] + m[8 + r] * b[2] + m[12 + r]) * 16);
};

test('the game probe really moves through each clip (its poses differ between phases)', { skip: !samples && 'no pose_samples.json (./addon/bench.sh export)' }, () => {
  const walk = samples.man && samples.man.walk;
  assert.ok(walk && walk.length > 1, 'no walk samples');
  const differs = walk.some((s) => JSON.stringify(s.bones) !== JSON.stringify(walk[0].bones));
  assert.ok(differs, 'every walk sample is the same pose: the game probe stayed at the start of the clip (an old export? re-run ./addon/bench.sh export); '
    + `runtime phases ${walk.map((s) => s.runtimePhase).join(', ')}`);
});

test('the viewer poses every probed clip like the game (bone points within 0.35 px)', { skip: !samples && 'no pose_samples.json (./addon/bench.sh export)' }, () => {
  const bad = [];
  for (const [race, clips] of Object.entries(samples)) {
    const file = JSON.parse(fs.readFileSync(path.join(ASSETS, `npc_biped_${race}.animation.json`), 'utf8')).animations;
    for (const [clip, list] of Object.entries(clips)) for (const s of list) {
      const anim = file[clip], t = s.phase * anim.animation_length, w = worldBones(samplePose(anim, t));
      for (const [bone, m] of Object.entries(s.bones)) {
        if (!w[bone]) continue;
        for (const d of [[0, 0, 0], [4, 0, 0], [0, -6, 0], [0, 0, -4]]) {
          const p = PIVOT[bone].map((x, i) => x + d[i]), a = point(w[bone], p), b = javaPoint(m, p);
          const err = Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
          if (err > TOLERANCE) { bad.push(`${race} ${clip} @${s.phase} (game phase ${s.runtimePhase}) ${bone}: viewer [${a.map((x) => x.toFixed(1))}] game [${b.map((x) => x.toFixed(1))}]`); break; }
        }
      }
    }
  }
  assert.deepEqual(bad.slice(0, 20), [], `${bad.length} mismatches`);
});
