/**
 * Armour texturing: material recipes (materials/*.json) painted onto cube faces. Light comes from the top-left.
 * Tones from one colour: 0 glint, 1 lit, 2 base, 3 shade, 4 dark, 5 deep.
 *
 * Recipe fields (all optional except kind and colour):
 *   kind       plate | mail | leather | cloth
 *   colour     colour slot of the palette (metal, cloth, trim, accent, leather...) or a hex colour
 *   bands      1-4 tone steps from the top of a side face down (polished plate catches light at the top)
 *   polish     0-1: x position of a bright streak down the face (null: none)
 *   bevel      true: lit left and top edges, dark right and bottom edges
 *   rim        colour (slot or hex) of the bottom edge: silver rims on black lames
 *   border     colour of a raised 1 px band one pixel in from the edges
 *   rivets     { spacing: 2-6, rows: "top" | "both" }: lit dots with a shadow under them, in the rim or
 *              border colour when the material has one
 *   scratches  0-0.3: share of inner pixels with a glint or a nick
 *   engrave    scroll | filigree | fluting: pattern cut into the inner face (scroll: fine, one tone deep)
 *   lames      every N rows a dark seam, as if the plate were made of overlapping lames
 *   note       what the material is for (not painted)
 */
import { readdirSync, readFileSync } from 'node:fs';

const KINDS = ['plate', 'mail', 'leather', 'cloth'];
const ENGRAVE = ['scroll', 'filigree', 'fluting'];
const hexRgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const mix = (c, t, f) => c.map((v, i) => Math.round(v + (t[i] - v) * f));

/** Six tones from a colour (hex or [r, g, b]); index 2 is the colour itself. */
export function tones(colour) {
  const c = typeof colour === 'string' ? hexRgb(colour) : colour.slice(0, 3);
  const W = [255, 252, 244], K = [8, 8, 12];
  return [mix(c, W, 0.5), mix(c, W, 0.22), c, mix(c, K, 0.22), mix(c, K, 0.42), mix(c, K, 0.62)];
}

export function validateMaterial(m) {
  const p = [];
  if (!m || typeof m !== 'object') return ['not an object'];
  if (!KINDS.includes(m.kind)) p.push(`kind must be one of ${KINDS.join(', ')}`);
  if (typeof m.colour !== 'string') p.push('colour must be a slot name or a hex colour');
  if (m.bands !== undefined && !(Number.isInteger(m.bands) && m.bands >= 1 && m.bands <= 4)) p.push('bands must be 1-4');
  if (m.polish !== undefined && m.polish !== null && !(m.polish >= 0 && m.polish <= 1)) p.push('polish must be 0-1 or null');
  if (m.scratches !== undefined && !(m.scratches >= 0 && m.scratches <= 0.3)) p.push('scratches must be 0-0.3');
  if (m.engrave !== undefined && m.engrave !== null && !ENGRAVE.includes(m.engrave)) p.push(`engrave must be ${ENGRAVE.join(' or ')}`);
  if (m.rivets !== undefined && m.rivets !== null) {
    if (!(Number.isInteger(m.rivets.spacing) && m.rivets.spacing >= 2 && m.rivets.spacing <= 6)) p.push('rivets.spacing must be 2-6');
    if (!['top', 'both'].includes(m.rivets.rows)) p.push('rivets.rows must be top or both');
  }
  if (m.lames !== undefined && !(Number.isInteger(m.lames) && m.lames >= 0)) p.push('lames must be a whole number');
  const known = ['kind', 'colour', 'bands', 'polish', 'bevel', 'rim', 'border', 'rivets', 'scratches', 'engrave', 'lames', 'note'];
  for (const k of Object.keys(m)) if (!known.includes(k)) p.push(`unknown field ${k}`);
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

const hash01 = (text) => {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
};
const FILIGREE = ['.xx...', 'x..x..', 'x.....', '.xx...', '...x..', 'x..x..'];
const SCROLL = ['x...', '.x..', '..xx', '....'];   // fine diagonal etching, reads at 8-12 px

/**
 * Paint one face. box = [u1, v1, u2, v2] on the canvas; lookup(colour) -> six tones for a slot name or hex colour.
 */
export function paintSurface(canvas, box, face, m, lookup, name = '') {
  const [u1, v1, u2, v2] = box, w = u2 - u1, h = v2 - v1;
  const T = lookup(m.colour), rim = m.rim ? lookup(m.rim) : null, border = m.border ? lookup(m.border) : null;
  const put = (x, y, c) => canvas.set(u1 + x, v1 + y, [c[0], c[1], c[2], 255]);
  const side = !['up', 'down'].includes(face);
  const bands = m.bands || 1, px = m.polish === null || m.polish === undefined ? -1 : Math.round(m.polish * (w - 1));
  const bevel = m.bevel !== false;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const inner = x > 0 && y > 0 && x < w - 1 && y < h - 1;
    let tone, colour = T;
    if (!side) {
      tone = face === 'up' ? (x === px ? 0 : 1) : 4;
      if (bevel && (x === w - 1 || y === h - 1)) tone += 2;
      put(x, y, colour[Math.min(5, tone)]);
      continue;
    }
    // base tone: bands, or the kind's own texture
    tone = bands > 1 ? 1 + Math.min(2, Math.floor((y * bands) / h) * (2 / (bands - 1))) : 2;
    tone = Math.round(tone);
    if (m.kind === 'mail') {
      const ring = (x + (y % 2)) % 2 === 0;
      tone = ring ? (y % 2 === 0 ? 1 : 2) + (y >= (2 * h) / 3 ? 1 : 0) : 5;   // links over deep gaps
    } else if (m.kind === 'leather') {
      if (hash01(`${name}|${face}|${x}|${y}|g`) < 0.1) tone = 3;
      if (h >= 5 && (y === 1 || y === h - 2) && x % 2 === 0 && inner) tone = 4;   // stitching
    } else if (m.kind === 'cloth') {
      if (x % 3 === 2 && y % 4 !== 0) tone = 3;
    } else {
      if (m.lames && y > 0 && y % m.lames === 0) tone = 4;
      if (m.engrave && inner && x > 1 && y > 1 && x < w - 2 && y < h - 2) {
        const cut = m.engrave === 'fluting' ? x % 2 === 0 : m.engrave === 'scroll' ? SCROLL[y % 4][x % 4] === 'x' : FILIGREE[y % 6][x % 6] === 'x';
        if (cut) tone = Math.min(5, tone + (m.engrave === 'scroll' ? 1 : 2));
      }
      if (x === px && inner) tone = Math.max(0, tone - 2);
      if (m.scratches && inner && hash01(`${name}|${face}|${x}|${y}`) < m.scratches) tone = tone >= 2 ? 0 : 3;
    }
    // raised border band one pixel in
    if (border && w >= 5 && h >= 5 && inner && (x === 1 || y === 1 || x === w - 2 || y === h - 2)) {
      colour = border;
      tone = x === 1 || y === 1 ? 1 : 3;
    }
    // rivets: lit dot, shadow under
    if (m.rivets && w >= 5 && h >= 4) {
      const rows = m.rivets.rows === 'both' && h >= 7 ? [1, h - 3] : [1];
      const xs = [];
      for (let rx = 2; rx <= w - 3; rx += m.rivets.spacing) xs.push(rx);
      if (xs.length && xs[xs.length - 1] !== w - 3 && w - 3 > 2) xs.push(w - 3);
      // rivet heads in the rim or border colour when there is one (silver rivets on black lames)
      if (xs.includes(x) && rows.includes(y)) { colour = rim || border || T; tone = rim || border ? 1 : 0; }
      if (xs.includes(x) && rows.includes(y - 1)) { colour = T; tone = 4; }
    }
    // bevel and rim
    if (bevel) {
      if (y === h - 1) { if (rim) { colour = rim; tone = 1; } else { colour = T; tone = 4; } }
      else if (x === w - 1) { colour = T; tone = 4; }
      else if (x === 0 || y === 0) { colour = T; tone = Math.max(0, Math.min(tone, 2) - 1); }
    } else if (y === h - 1 && rim) { colour = rim; tone = 1; }
    put(x, y, colour[Math.max(0, Math.min(5, tone))]);
  }
}
