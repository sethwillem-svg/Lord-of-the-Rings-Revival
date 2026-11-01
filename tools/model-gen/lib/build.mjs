/**
 * buildModel: race table + seed -> a painted, checked toolkit Model. The low-detail copy drops cubes the
 * parts mark as detail and merges each lodGroup into one cube spanning the group.
 */
import { readFileSync, readdirSync } from 'node:fs';
import { Model, Atlas } from '../vendor/bbmt/dist/index.js';
import { makeRng } from './rng.mjs';
import { solveBody, BONES } from './body.mjs';
import { Assembly } from './assembly.mjs';
import { resolvePalette, packUv, paintModel } from './paint.mjs';
import { runQa } from './qa.mjs';
import { MAN } from '../races/man.mjs';
import { addMan } from '../parts/index.mjs';

const RACES = { man: { table: MAN, parts: addMan } };
const PALETTE_DIR = new URL('../palettes/', import.meta.url);
/** Palette ids: the file names in palettes/ ('base' plus one per faction). */
export const PALETTES = readdirSync(PALETTE_DIR).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort();

/**
 * A palette: clothes slot colours, optional skin/hair/eye choices that replace the race's, grime 0..1, and an
 * outfit (pattern, emblem, sleeves, boots, legs, face, hair and beard weights) that only repaints or picks
 * among existing styles, so faction men never grow past the base silhouette armour is fitted to.
 */
export function loadPalette(id) {
  if (!PALETTES.includes(id)) throw new Error(`unknown palette "${id}" (known: ${PALETTES.join(', ')})`);
  return { id, grime: 0, ...JSON.parse(readFileSync(new URL(`${id}.json`, PALETTE_DIR))) };
}
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

export function buildModel(race, seed, { lod = false, palette = 'base', overrides = {}, body: fixedBody = null } = {}) {
  const r = RACES[race];
  if (!r) throw new Error(`unknown race "${race}" (known: ${Object.keys(RACES).join(', ')})`);
  const pal = typeof palette === 'string' ? loadPalette(palette) : { id: 'custom', grime: 0, ...palette };
  // a palette's colour choices replace the race's; the picks use the same random draws, so geometry is unchanged
  const table = { ...r.table, ...Object.fromEntries(['skin', 'hair', 'eye'].filter((k) => pal[k]).map((k) => [k, pal[k]])) };
  const rng = makeRng(seed);
  // fixedBody (sizes and pivots from layoutBody) replaces the seeded sizes; colours still come from the seed
  const body = fixedBody ? { ...solveBody(table, rng.fork('body')), ...fixedBody, colours: solveBody(table, rng.fork('body')).colours }
    : solveBody(table, rng.fork('body'));
  const asm = new Assembly(body);
  const styles = r.parts(asm, body, rng, overrides, pal.outfit || {});
  const cubes = lod ? lodCubes(asm.cubes.map((c) => ({ ...c, from: [...c.from], to: [...c.to] }))) : asm.cubes;

  const name = `${race}_${pal.id}_${seed}${lod ? '_lod' : ''}`;
  const model = new Model({ name, resolution: [RESOLUTION, RESOLUTION], identifier: `geometry.${name}` });
  for (const [bone, parent] of BONES) model.bone(bone, [...body.pivots[bone]], parent);
  for (const c of cubes) model.cube({ name: c.name, bone: c.bone, from: c.from, to: c.to });
  const atlas = new Atlas(RESOLUTION, RESOLUTION);
  packUv(model, atlas);
  paintModel(model, atlas, cubes, resolvePalette(pal, body.colours), { grime: pal.grime });
  model.addTexture(atlas.toTexture(`${name}.png`));
  return { model, asm, styles, body, qa: runQa(model, { lod }) };
}
