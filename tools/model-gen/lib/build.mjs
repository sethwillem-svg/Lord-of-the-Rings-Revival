/**
 * buildModel: race table + seed -> a painted, checked toolkit Model. The low-detail copy drops cubes the
 * parts mark as detail and merges each lodGroup into one cube spanning the group.
 */
import { readFileSync } from 'node:fs';
import { Model, Atlas } from '../vendor/bbmt/dist/index.js';
import { makeRng } from './rng.mjs';
import { solveBody, BONES } from './body.mjs';
import { Assembly } from './assembly.mjs';
import { resolvePalette, packUv, paintModel } from './paint.mjs';
import { runQa } from './qa.mjs';
import { MAN } from '../races/man.mjs';
import { addMan } from '../parts/index.mjs';

const RACES = { man: { table: MAN, parts: addMan } };
const BASE_PALETTE = JSON.parse(readFileSync(new URL('../palettes/base.json', import.meta.url)));
export const RESOLUTION = 128;

function lodCubes(cubes) {
  const out = [], groups = new Map();
  for (const c of cubes) {
    if (c.detail) continue;
    if (!c.lodGroup) { out.push(c); continue; }
    if (!groups.has(c.lodGroup)) { groups.set(c.lodGroup, { ...c, name: c.lodGroup, stamps: undefined }); out.push(groups.get(c.lodGroup)); continue; }
    const g = groups.get(c.lodGroup);
    for (let a = 0; a < 3; a++) { g.from[a] = Math.min(g.from[a], c.from[a]); g.to[a] = Math.max(g.to[a], c.to[a]); }
  }
  return out;
}

export function showcaseSeeds(race) {
  if (!RACES[race]) throw new Error(`unknown race "${race}" (known: ${Object.keys(RACES).join(', ')})`);
  return [...RACES[race].table.showcase];
}

export function buildModel(race, seed, { lod = false, palette = BASE_PALETTE, overrides = {} } = {}) {
  const r = RACES[race];
  if (!r) throw new Error(`unknown race "${race}" (known: ${Object.keys(RACES).join(', ')})`);
  const rng = makeRng(seed);
  const body = solveBody(r.table, rng.fork('body'));
  const asm = new Assembly(body);
  const styles = r.parts(asm, body, rng, overrides);
  const cubes = lod ? lodCubes(asm.cubes.map((c) => ({ ...c, from: [...c.from], to: [...c.to] }))) : asm.cubes;

  const name = `${race}_base_${seed}${lod ? '_lod' : ''}`;
  const model = new Model({ name, resolution: [RESOLUTION, RESOLUTION], identifier: `geometry.${name}` });
  for (const [bone, parent] of BONES) model.bone(bone, [...body.pivots[bone]], parent);
  for (const c of cubes) model.cube({ name: c.name, bone: c.bone, from: c.from, to: c.to });
  const atlas = new Atlas(RESOLUTION, RESOLUTION);
  packUv(model, atlas);
  paintModel(model, atlas, cubes, resolvePalette(palette, body.colours));
  model.addTexture(atlas.toTexture(`${name}.png`));
  return { model, styles, body, qa: runQa(model, { lod }) };
}
