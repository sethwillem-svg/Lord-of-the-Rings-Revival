/**
 * UV packing and painting. Every cube gets its own box-UV net on the atlas (one texel per pixel), then
 * each face is painted from its colour slot's 4-step ramp [lit, base, shade, dark]: up faces lit, down faces
 * shaded, side faces base with a lit top row and a shaded bottom row, plus the style's pattern and any
 * stamps (eyes, brows, trims) written as text grids.
 */
import { boxUv } from '../vendor/bbmt/dist/index.js';
import { SLOTS } from './assembly.mjs';

const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const mix = (c, t, f) => c.map((v, i) => Math.round(v + (t[i] - v) * f));

/** Four shades from a base colour; base is kept exactly. */
export function shades(base) {
  const c = hex(base);
  return [mix(c, [255, 248, 230], 0.2), c, mix(c, [10, 8, 12], 0.25), mix(c, [10, 8, 12], 0.45)];
}

export function resolvePalette(baseJson, colours) {
  const out = {};
  const src = { ...baseJson, ...colours };
  for (const slot of SLOTS) {
    if (!src[slot]) throw new Error(`palette: no colour for slot "${slot}"`);
    out[slot] = shades(src[slot]);
  }
  return out;
}

export function packUv(model, atlas) {
  const sizeOf = (c) => c.to.map((v, i) => v - c.from[i]);
  const order = [...model.cubes].sort((a, b) => {
    const [ax, ay, az] = sizeOf(a), [bx, by, bz] = sizeOf(b);
    return (by + bz) - (ay + az) || (a.name < b.name ? -1 : a.name > b.name ? 1 : 0);   // not localeCompare: locale-independent
  });
  for (const cube of order) {
    const [sx, sy, sz] = sizeOf(cube);
    try {
      // boxUv: sides span 2(sx+sz); up and down sit side by side from sz to sz+3sx, wider when sx > sz
      const [x, y] = atlas.alloc(cube.name, Math.max(2 * (sx + sz), sz + 3 * sx), sy + sz);
      cube.faces = boxUv(cube, [x, y]);
    } catch (e) {
      throw new Error(`${model.name}: texture sheet full at cube "${cube.name}" (${e.message})`);
    }
  }
}

const SIDES = ['north', 'south', 'east', 'west'];

function paintFace(canvas, rect, face, ramp, style) {
  const [u1, v1, u2, v2] = [Math.min(rect[0], rect[2]), Math.min(rect[1], rect[3]), Math.max(rect[0], rect[2]), Math.max(rect[1], rect[3])];
  const w = u2 - u1, h = v2 - v1;
  const put = (x, y, c) => canvas.set(u1 + x, v1 + y, [c[0], c[1], c[2], 255]);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let k = face === 'up' ? 0 : face === 'down' ? 2 : 1;
    if (SIDES.includes(face)) {
      if (y === 0 && h >= 2) k = 0;
      else if (y === h - 1 && h >= 3) k = 2;
      else if (style === 'cloth' && w >= 3 && x % 3 === 2 && y % 4 !== 0) k = 2;   // fabric folds
      else if (style === 'leather' && y === 1 && h >= 4 && x % 2 === 0) k = 3;
      else if (style === 'hair' && x % 2 === 1) k = 2;
    } else if (face === 'up' && style === 'hair' && x % 2 === 1) k = 1;
    put(x, y, ramp[k]);
  }
  return [u1, v1, u2, v2];
}

function stamp(canvas, box, s, palette) {
  const [u1, v1, u2, v2] = box;
  s.grid.forEach((row, y) => [...row].forEach((ch, x) => {
    const use = s.legend[ch];
    if (!use || u1 + x >= u2 || v1 + y >= v2) return;
    const c = palette[use[0]][use[1]];
    canvas.set(u1 + x, v1 + y, [c[0], c[1], c[2], 255]);
  }));
}

export function paintModel(model, atlas, partCubes, palette) {
  const byName = new Map(partCubes.map((c) => [c.name, c]));
  for (const cube of model.cubes) {
    const part = byName.get(cube.name);
    if (!part) throw new Error(`paint: no part data for cube "${cube.name}"`);
    for (const [face, rect] of Object.entries(cube.faces)) {
      const box = paintFace(atlas.canvas, rect, face, palette[part.slot], part.style);
      if (part.stamps && part.stamps[face]) stamp(atlas.canvas, box, part.stamps[face], palette);
    }
  }
}
