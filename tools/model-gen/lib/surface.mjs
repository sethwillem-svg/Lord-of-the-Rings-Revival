/**
 * Armour texturing (the materials tool): recipes in materials/*.json painted onto cube faces, following the Minecraft
 * texturing guides (Blockbench Minecraft style guide, Faithful guidelines, metal-shading tutorials):
 *   - hue-shifted ramps: warm near-white highlights, cool blue shadows, wide contrast for metal
 *   - material from clusters, not bands or noise: metal reflects a light sky zone above a dark horizon band, a polish
 *     streak with one specular glint, and a thin counter-reflection just above the dark bottom edge
 *   - faces lit by direction: top brightest, front and right (light from the top-left) brighter than back and bottom
 *   - no pillow or pancake shading, no random noise
 *
 * Tones from one colour: 0 specular, 1 highlight, 2 base, 3 shade, 4 shadow, 5 deep.
 *
 * Recipe fields (kind and colour required):
 *   kind     steel | metal | mail | leather | cloth (steel: irregular 1-3 px clusters of close greys, the vanilla
 *            armour look; metal: polished reflections)
 *   colour   palette slot (metal, cloth, trim, accent, leather...) or a hex colour
 *   polish   0-1: x position of the polish streak and its glint (metal), or null for none
 *   rim      colour of the bottom edge (silver rims on black lames)
 *   border   colour of a raised band one pixel in from the edges
 *   rivets   { spacing: 2-6, rows: "top" | "both" }: a lit head with a shadow under it, in the rim or border colour
 *            when the material has one
 *   engrave  scroll | filigree | fluting: a pattern cut one tone deep into the inner face
 *   lames    every N rows a soft seam, as if the plate were overlapping lames (4 or more avoids stripes)
 *   note     what the material is for (not painted)
 */
import { readdirSync, readFileSync } from 'node:fs';

const KINDS = ['steel', 'metal', 'mail', 'leather', 'cloth'];
const ENGRAVE = ['scroll', 'filigree', 'fluting'];
const FIELDS = ['kind', 'colour', 'polish', 'rim', 'border', 'rivets', 'engrave', 'lames', 'note'];
const hexRgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const hash01 = (text) => {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
};

function rgbHsv([r, g, b]) {
  r /= 255; g /= 255; b /= 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let h = 0;
  if (d) h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [(h * 60 + 360) % 360, mx ? d / mx : 0, mx];
}
function hsvRgb([h, s, v]) {
  const c = v * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = v - c;
  const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
  return [r, g, b].map((u) => Math.round((u + m) * 255));
}
// move a hue towards a target by a share of the way, at most `cap` degrees (yellow darkens through orange, not purple)
const towards = (h, target, f, cap = 25) => { const d = ((target - h + 540) % 360) - 180; const step = Math.max(-cap, Math.min(cap, d * f)); return (h + step + 360) % 360; };

/**
 * Six hue-shifted tones from a colour (hex or [r, g, b]); tone 2 is the colour itself. Highlights lean warm
 * (towards 50 degrees) and lose saturation; shadows lean cool (towards 225-245 degrees) and gain it. Dark colours
 * still get visible highlights, so black enamel reads as metal, not as a hole.
 */
export function tones(colour) {
  const rgb = typeof colour === 'string' ? hexRgb(colour) : colour.slice(0, 3);
  const [h, s, v] = rgbHsv(rgb);
  const spec = [
    [Math.min(0.98, Math.max(v * 1.45, v + 0.38)), 50, 0.6, 0.35, 0.04],
    [Math.min(0.94, Math.max(v * 1.22, v + 0.2)), 50, 0.35, 0.7, 0.04],
    null,
    [Math.min(v * 0.78, v - 0.06), 225, 0.25, 1.1, 0.08],
    [Math.min(v * 0.56, v - 0.1), 228, 0.45, 1.2, 0.12],
    [Math.min(v * 0.38, v - 0.13), 245, 0.6, 1.25, 0.16],
  ];
  // greys have no hue of their own: they take the tint fully (warm light, cool shade); colours shift at most 20-25 degrees
  return spec.map((t, i) => (t ? hsvRgb([s < 0.05 ? t[1] : towards(h, t[1], t[2], i < 2 ? 20 : 25), Math.min(1, Math.max(t[4], s * t[3])), Math.max(0.03, t[0])]) : rgb));
}

export function validateMaterial(m) {
  const p = [];
  if (!m || typeof m !== 'object') return ['not an object'];
  if (!KINDS.includes(m.kind)) p.push(`kind must be one of ${KINDS.join(', ')}`);
  if (typeof m.colour !== 'string') p.push('colour must be a slot name or a hex colour');
  if (m.polish !== undefined && m.polish !== null && !(m.polish >= 0 && m.polish <= 1)) p.push('polish must be 0-1 or null');
  if (m.engrave !== undefined && m.engrave !== null && !ENGRAVE.includes(m.engrave)) p.push(`engrave must be ${ENGRAVE.join(', ')}`);
  if (m.rivets !== undefined && m.rivets !== null) {
    if (!(Number.isInteger(m.rivets.spacing) && m.rivets.spacing >= 2 && m.rivets.spacing <= 6)) p.push('rivets.spacing must be 2-6');
    if (!['top', 'both'].includes(m.rivets.rows)) p.push('rivets.rows must be top or both');
  }
  if (m.lames !== undefined && !(Number.isInteger(m.lames) && m.lames >= 0)) p.push('lames must be a whole number');
  for (const k of Object.keys(m)) if (!FIELDS.includes(k)) p.push(`unknown field ${k}`);
  return p;
}

/** Every recipe in a folder (path or file URL), by file name. */
export function loadMaterials(dir) {
  const base = typeof dir === 'string' ? dir.replace(/\/?$/, '/') : dir;
  const file = (f) => (typeof base === 'string' ? base + f : new URL(f, base));
  return Object.fromEntries(readdirSync(base).filter((f) => f.endsWith('.json')).sort()
    .map((f) => [f.slice(0, -5), JSON.parse(readFileSync(file(f), 'utf8'))]));
}

/** The recipes in tools/model-gen/materials/. */
export const MATERIALS = loadMaterials(new URL('../materials/', import.meta.url));

// light from the top-left front: the entity's right side (-X, the west face) is lit, its back and left are not
const FACING = { up: -1, north: 0, west: 0, east: 1, south: 1, down: 2 };
const FILIGREE = ['.xx...', 'x..x..', 'x.....', '.xx...', '...x..', 'x..x..'];
const SCROLL = ['x...', '.x..', '..xx', '....'];

/**
 * Steel as in vanilla armour: blocks of 1-3 px of three close greys (no lone pixels), a lit top row and a darker
 * bottom row on side faces. Deterministic per cube and face.
 */
function steelClusters(face, w, h, name) {
  const t = new Int8Array(w * h).fill(-1), side = !['up', 'down'].includes(face);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (t[y * w + x] >= 0) continue;
    const r = hash01(`${name}|${face}|${x}|${y}`), r2 = hash01(`${name}|${face}|${y}|${x}|t`);
    let bw = 1 + Math.floor(r * 3), bh = 1 + (Math.floor(r * 7) % 2);
    if (bw * bh < 2) bw = 2;
    bw = Math.min(bw, w - x); bh = Math.min(bh, h - y);
    while (bw > 1 && t[y * w + x + bw - 1] >= 0) bw--;
    const tone = r2 < 0.32 ? 1 : r2 < 0.46 ? 3 : 2;   // close greys: mostly base and light, a little shade
    if (bw * bh < 2) {   // a single leftover pixel joins its left or upper neighbour's cluster
      t[y * w + x] = x > 0 ? t[y * w + x - 1] : y > 0 ? t[(y - 1) * w + x] : tone;
      continue;
    }
    for (let yy = y; yy < y + bh; yy++) for (let xx = x; xx < x + bw; xx++) if (t[yy * w + xx] < 0) t[yy * w + xx] = tone;
  }
  if (side) for (let x = 0; x < w; x++) { t[x] = Math.min(t[x], 1); t[(h - 1) * w + x] = Math.max(t[(h - 1) * w + x], 3); }
  return t;
}

/** Base tone of a metal face: reflection zones (sky, horizon, ground) and the polish streak with its glint. */
function metalTone(face, x, y, w, h, px) {
  const streak = px >= 0 && (x === px || (w >= 6 && x === px + 1));
  if (face === 'up') return x === w - 1 || y === h - 1 ? 2 : streak ? 0 : 1;
  if (face === 'down') return x === w - 1 || y === h - 1 ? 5 : 4;
  if (h <= 3) return y === 0 ? 1 : y === h - 1 ? 3 : 2;   // thin lames: lit top, dark bottom
  if (h < 6) {   // small plates: a simple gradient (reflection zones on many small faces read as stripes)
    const t = y === 0 ? 1 : y === h - 1 ? 4 : 2;
    return Math.min(5, (streak && y > 0 && y < h - 1 ? 1 : t) + (x === w - 1 && y < h - 1 ? 1 : 0));
  }
  const hy = Math.round(0.6 * (h - 1)), sky = Math.round(0.35 * (h - 1));
  if (px >= 0 && x === px && y === Math.round(0.2 * (h - 1))) return 0;   // the one specular glint
  let t;
  if (y === h - 1) t = 4;
  else if (y === hy) t = streak ? 2 : 4;                  // dark horizon band, broken by the streak
  else if (y < hy) t = y <= sky ? 1 : 2;                  // sky reflection
  else t = h >= 6 && y === h - 2 ? 2 : 3;                 // ground reflection; counter-reflection above the edge
  if (streak && y !== hy && y !== h - 1) t = Math.max(1, t - 1);
  if (x === w - 1 && y !== hy) t = Math.min(5, t + 1);    // shadow side edge
  return t;
}

/**
 * Paint one face. box = [u1, v1, u2, v2] on the canvas; lookup(colour) -> six tones for a slot name or hex colour.
 */
export function paintSurface(canvas, box, face, m, lookup, name = '') {
  const [u1, v1, u2, v2] = box, w = u2 - u1, h = v2 - v1;
  const T = lookup(m.colour), rim = m.rim ? lookup(m.rim) : null, border = m.border ? lookup(m.border) : null;
  const put = (x, y, c) => canvas.set(u1 + x, v1 + y, [c[0], c[1], c[2], 255]);
  const side = !['up', 'down'].includes(face), off = FACING[face] || 0;
  const px = m.polish === null || m.polish === undefined ? -1 : Math.round(m.polish * (w - 1));
  const rivetX = [];
  if (m.rivets && w >= 5) {
    for (let rx = 2; rx <= w - 3; rx += m.rivets.spacing) rivetX.push(rx);
    if (rivetX.length && rivetX[rivetX.length - 1] !== w - 3 && w - 3 > 2) rivetX.push(w - 3);
  }
  const rivetRows = m.rivets ? (m.rivets.rows === 'both' && h >= 7 ? [1, h - 3] : [1]) : [];
  const steel = m.kind === 'steel' ? steelClusters(face, w, h, name) : null;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const inner = x > 0 && y > 0 && x < w - 1 && y < h - 1;
    let colour = T, tone;
    if (m.kind === 'steel') {
      tone = steel[y * w + x];
    } else if (m.kind === 'mail') {
      // rows of 2 px links over dark gaps, each row offset (not a checkerboard), darker towards the bottom
      const row = y % 2, link = (x + (row ? 2 : 0)) % 3 !== 2;
      tone = !side ? (face === 'up' ? 1 : 4) : link ? 1 + row + (y >= (2 * h) / 3 ? 1 : 0) : 4 + (y >= (2 * h) / 3 ? 1 : 0);
    } else if (m.kind === 'leather' || m.kind === 'cloth') {
      tone = !side ? (face === 'up' ? 1 : 4) : y === h - 1 ? 3 : 2;
      if (side && m.kind === 'cloth' && x % 3 === 2 && y % 4 !== 0 && y < h - 1) tone = 3;                    // folds
      if (side && m.kind === 'leather' && h >= 5 && (y === 1 || y === h - 2) && x % 2 === 0 && inner) tone = 4; // stitching
    } else {
      tone = metalTone(face, x, y, w, h, px);
      if (side && m.lames && y > 0 && y < h - 1 && y % m.lames === 0) tone = Math.max(tone, 3);   // soft seam between lames
      if (side && m.engrave && inner && x > 1 && y > 1 && x < w - 2 && y < h - 2 && tone > 0) {
        const cut = m.engrave === 'fluting' ? x % 2 === 0 : m.engrave === 'scroll' ? SCROLL[y % 4][x % 4] === 'x' : FILIGREE[y % 6][x % 6] === 'x';
        if (cut) tone = Math.min(5, tone + 1);
      }
    }
    if (side && border && w >= 5 && h >= 5 && inner && (x === 1 || y === 1 || x === w - 2 || y === h - 2)) {
      colour = border; tone = x === 1 || y === 1 ? 1 : 3;
    }
    if (side && rivetX.includes(x) && rivetRows.includes(y)) { colour = rim || border || T; tone = rim || border ? 1 : 0; }
    else if (side && rivetX.includes(x) && rivetRows.includes(y - 1)) { colour = T; tone = 4; }
    if (side && y === h - 1 && rim) { colour = rim; tone = 1; }
    const glint = tone === 0 && colour === T;
    put(x, y, colour[Math.max(glint && off <= 0 ? 0 : 1, Math.min(5, tone + off))]);
  }
}
