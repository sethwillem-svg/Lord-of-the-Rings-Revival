/**
 * Gear core: free-standing models (weapons, armour sets) built from boxes with colour slots, box-UV packed and
 * painted like the bodies. Sizes are whole pixels, positions whole or half pixels.
 */
import { Model, Atlas, findBuriedFaces } from '../vendor/bbmt/dist/index.js';
import { packUv, shades, paintModel } from './paint.mjs';
import { loadPalette } from './build.mjs';

export const GEAR_SLOTS = ['metal', 'metal_dark', 'wood', 'leather', 'cloth', 'trim', 'emblem', 'string', 'hair', 'paint', 'accent'];
const isHalf = (v) => Number.isInteger(v * 2);

/** Gear colours from a faction palette: its leather, cloth, trim and emblem, plus shared steel, wood and string. */
export function gearPalette(faction = 'base') {
  const p = loadPalette(faction);
  const src = {
    // blades are steel in every faction; gold or silver fittings use trim
    metal: '#b3b6b9', metal_dark: '#4a4a48', wood: '#6b4a2b', leather: p.belt, cloth: p.tunic,
    trim: p.tunic_trim, emblem: p.emblem || p.tunic_trim, string: '#d8cfb8', hair: '#ece4d2', paint: p.paint || '#a82a1e',
    accent: p.accent || p.tunic_trim,
  };
  return { ramps: Object.fromEntries(GEAR_SLOTS.map((s) => [s, shades(src[s])])), grime: p.grime || 0 };
}

export class GearAssembly {
  /** bones: [[name, parent, pivot]], parents first. */
  constructor(bones) {
    this.bones = bones;
    this.boneNames = new Set(bones.map(([n]) => n));
    this.cubes = [];
  }

  add(spec) {
    const { name, bone, from, to, slot } = spec;
    if (this.cubes.some((c) => c.name === name)) throw new Error(`gear: cube "${name}" already exists`);
    if (!this.boneNames.has(bone)) throw new Error(`gear: "${name}" names unknown bone "${bone}"`);
    if (!GEAR_SLOTS.includes(slot)) throw new Error(`gear: "${name}" uses unknown slot "${slot}"`);
    for (let a = 0; a < 3; a++) {
      const size = to[a] - from[a];
      if (!(size > 0) || !Number.isInteger(size)) throw new Error(`gear: "${name}" size ${size} on axis ${a} is not whole pixels`);
      if (!isHalf(from[a]) || !isHalf(to[a])) throw new Error(`gear: "${name}" coordinates must sit on whole or half pixels`);
    }
    this.cubes.push({ style: 'flat', ...spec, from: [...from], to: [...to] });
  }

  /** Shorthand: box(name, bone, [x0, x1], [y0, y1], [z0, z1], slot, extra). */
  box(name, bone, [x0, x1], [y0, y1], [z0, z1], slot, extra = {}) {
    this.add({ name, bone, from: [x0, y0, z0], to: [x1, y1, z1], slot, ...extra });
  }
}

export function buildGear(name, asm, palette, resolution = 64) {
  const model = new Model({ name, resolution: [resolution, resolution], identifier: `geometry.${name}` });
  for (const [bone, parent, pivot] of asm.bones) model.bone(bone, [...pivot], parent);
  for (const c of asm.cubes) model.cube({ name: c.name, bone: c.bone, from: c.from, to: c.to });
  const atlas = new Atlas(resolution, resolution);
  packUv(model, atlas);
  paintModel(model, atlas, asm.cubes, palette.ramps, { grime: palette.grime });
  model.addTexture(atlas.toTexture(`${name}.png`));
  const hidden = findBuriedFaces(model).hiddenCubes;
  const problems = hidden.map((h) => `hidden: ${h.cube} is inside ${h.blockedBy}`);
  return { model, qa: { ok: problems.length === 0, problems } };
}
