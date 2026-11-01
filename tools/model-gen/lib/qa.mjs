/**
 * Checks that fail a build: spec bones exactly, no cubes on anchors, every limb joint sealed (shares at
 * least 1 px of material, so it does not open into a slit when it rotates), no cube hidden inside others,
 * and the cube budget for full models.
 */
import { auditSeal, findBuriedFaces } from '../vendor/bbmt/dist/index.js';
import { BONES, ANCHORS } from './body.mjs';

export const CHAINS = [
  ['skull', 'collar', 'tunic'], ['tunic', 'tunic_skirt'],
  ['sleeve_r', 'forearm_r', 'hand_r'], ['sleeve_l', 'forearm_l', 'hand_l'],
  ['thigh_r', 'shin_r'], ['thigh_l', 'shin_l'],
];

export function runQa(model, { lod = false } = {}) {
  const problems = [];
  const have = model.rig.names;
  const want = BONES.map(([n]) => n);
  const missing = want.filter((n) => !have.includes(n)), extra = have.filter((n) => !want.includes(n));
  if (missing.length) problems.push(`bones missing: ${missing.join(', ')}`);
  if (extra.length) problems.push(`bones not in the spec: ${extra.join(', ')}`);
  for (const c of model.cubes) if (ANCHORS.has(c.bone)) problems.push(`cube ${c.name} is on anchor bone ${c.bone}`);
  const seal = auditSeal(model, CHAINS, { minDepth: 1 - 1e-6 });
  for (const p of seal.open) problems.push(`seal: ${p.a} and ${p.b} share only ${p.depth.toFixed(2)} px`);
  const occlusion = findBuriedFaces(model);
  for (const h of occlusion.hiddenCubes) problems.push(`hidden: ${h.cube} is inside ${h.blockedBy}`);
  if (!lod && (model.cubes.length < 40 || model.cubes.length > 80)) problems.push(`cube count ${model.cubes.length} is outside 40..80`);
  return { ok: problems.length === 0, problems, seal, occlusion, cubes: model.cubes.length };
}
