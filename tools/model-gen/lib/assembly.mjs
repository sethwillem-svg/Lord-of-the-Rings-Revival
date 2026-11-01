/**
 * Assembly: the cubes the parts declare for one model, before UV packing and painting. Every cube names
 * its bone, colour slot and paint style. Sizes are whole pixels (one texel per pixel); positions may sit
 * on half pixels so odd widths stay centred.
 */
import { BONES, ANCHORS } from './body.mjs';

export const SLOTS = ['skin', 'hair', 'eye', 'tunic', 'tunic_alt', 'tunic_trim', 'trousers', 'boots', 'belt', 'metal', 'emblem', 'paint'];
export const STYLES = ['flat', 'cloth', 'leather', 'skin', 'hair', 'metal'];
const BONE_NAMES = new Set(BONES.map(([n]) => n));
const isHalf = (v) => Number.isInteger(v * 2);

export class Assembly {
  constructor(body) {
    this.body = body;
    this.cubes = [];
    this.names = new Set();
    this.meta = {};   // shared facts between parts (for example the head geometry hair and beards fit to)
  }

  get(name) {
    return this.cubes.find((c) => c.name === name);
  }

  remove(name) {
    this.cubes = this.cubes.filter((c) => c.name !== name);
    this.names.delete(name);
  }

  add(spec) {
    const { name, bone, from, to, slot } = spec;
    if (this.names.has(name)) throw new Error(`assembly: cube "${name}" already exists`);
    if (!BONE_NAMES.has(bone)) throw new Error(`assembly: "${name}" names unknown bone "${bone}"`);
    if (ANCHORS.has(bone)) throw new Error(`assembly: "${name}" is on anchor bone "${bone}"; anchors take no cubes`);
    if (!SLOTS.includes(slot)) throw new Error(`assembly: "${name}" uses unknown slot "${slot}"`);
    if (spec.style && !STYLES.includes(spec.style)) throw new Error(`assembly: "${name}" uses unknown style "${spec.style}"`);
    for (let a = 0; a < 3; a++) {
      const size = to[a] - from[a];
      if (!(size > 0) || !Number.isInteger(size)) {
        throw new Error(`assembly: "${name}" size ${size} on axis ${a} is not whole pixels`);
      }
      if (!isHalf(from[a]) || !isHalf(to[a])) {
        throw new Error(`assembly: "${name}" coordinates must sit on whole or half pixels`);
      }
    }
    const cube = { style: 'flat', detail: false, ...spec, from: [...from], to: [...to] };
    this.cubes.push(cube);
    this.names.add(name);
    return cube;
  }
}
