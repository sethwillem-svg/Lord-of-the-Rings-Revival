/**
 * Head: skull with a painted face (eyes, brows, mouth), nose, ears and chin. Skull rows count from the top;
 * hair caps cover rows 0-1, brows sit above the eyes, the mouth is the second row from the bottom.
 */
export const STYLES = ['square', 'long', 'broad'];
export const NOSES = ['small', 'long', 'broad', 'hooked'];
export const BROWS = ['straight', 'heavy', 'arched'];
export const EARS = ['small', 'large'];

/** Geometry hair and beards fit to. y of skull row r spans [st - r - 1, st - r]. */
export function headGeometry(body, shape) {
  const top = body.legs + body.torso;
  const hh = body.head - 1;
  const hw = shape === 'long' ? body.head - 2 : shape === 'broad' ? body.head : body.head - 1;
  const hd = body.head - 1;
  const sb = top + 1, st = sb + hh;
  const eye = Math.floor(hh / 2);
  return { top, hw, hh, hd, sb, st, fz: -hd / 2, eye, mouth: hh - 2, rowY: (r) => [st - r - 1, st - r] };
}

const FACE = { w: ['skin', 0], e: ['eye', 1], b: ['hair', 2], m: ['skin', 3], s: ['skin', 2] };

/** Face grid for the skull's front: one string per row, '.' leaves the skin. */
export function faceGrid(g, brow) {
  const rows = Array.from({ length: g.hh }, () => Array(g.hw).fill('.'));
  const margin = g.hw >= 7 ? 1 : 0;
  const L = margin, R = g.hw - margin - 2;           // left eye columns L..L+1, right eye R..R+1
  rows[g.eye][L] = 'w'; rows[g.eye][L + 1] = 'e';
  rows[g.eye][R] = 'e'; rows[g.eye][R + 1] = 'w';
  const b = rows[g.eye - 1];
  if (brow === 'straight') { b[L] = b[L + 1] = b[R] = b[R + 1] = 'b'; }
  if (brow === 'heavy') { for (const x of [L - 1, L, L + 1, R, R + 1, R + 2]) if (x >= 0 && x < g.hw) b[x] = 'b'; }
  if (brow === 'arched') { b[L] = b[R + 1] = 'b'; rows[g.eye - 1][L + 1] = rows[g.eye - 1][R] = 'b';
    if (g.eye - 2 >= 2) { rows[g.eye - 2][L + 1] = rows[g.eye - 2][R] = 'b'; b[L + 1] = b[R] = '.'; } }
  const mw = g.hw % 2 ? 3 : 2, m0 = (g.hw - mw) / 2;
  for (let x = m0; x < m0 + mw; x++) rows[g.mouth][x] = 'm';
  return { grid: rows.map((r) => r.join('')), legend: FACE };
}

export function addHead(asm, body, rng, choice = {}) {
  const shape = choice.head || rng.pick(STYLES);
  const nose = choice.nose || rng.pick(NOSES);
  const brow = choice.brow || rng.pick(BROWS);
  const ears = choice.ears || rng.pick(EARS);
  const g = headGeometry(body, shape);
  asm.meta.head = { ...g, shape, brow };
  const { hw, hd, sb, st, fz } = g;
  asm.add({ name: 'skull', bone: 'head', from: [-hw / 2, sb, -hd / 2], to: [hw / 2, st, hd / 2],
    slot: 'skin', style: 'skin', stamps: { north: faceGrid(g, brow) } });

  const [ny0] = g.rowY(g.eye + 1), [, ny1] = g.rowY(g.eye);
  const noses = {
    small: { x: 1, y: [ny0, ny0 + 1], z: 1 },
    long: { x: 1, y: [ny0, ny1], z: 1 },
    broad: { x: 2, y: [ny0, ny0 + 1], z: 1 },
    hooked: { x: 1, y: [ny0, ny1], z: 2 },
  }[nose];
  asm.add({ name: 'nose', bone: 'head', from: [-noses.x / 2, noses.y[0], fz - noses.z], to: [noses.x / 2, noses.y[1], fz],
    slot: 'skin', style: 'skin', detail: true });

  const ear = ears === 'large' ? { h: 3, d: 2 } : { h: 2, d: 1 };
  const [ey0] = g.rowY(g.eye + 1);
  for (const [n, s] of [['ear_r', -1], ['ear_l', 1]]) {
    const x0 = s < 0 ? -hw / 2 - 1 : hw / 2;
    asm.add({ name: n, bone: 'head', from: [x0, ey0, -ear.d / 2], to: [x0 + 1, ey0 + ear.h, ear.d / 2],
      slot: 'skin', style: 'skin', detail: true });
  }
  const cw = Math.max(2, hw - 4);
  asm.add({ name: 'chin', bone: 'head', from: [-cw / 2, sb, fz - 1], to: [cw / 2, sb + 1, fz], slot: 'skin', style: 'skin' });
  return { head: shape, nose, brow, ears };
}
