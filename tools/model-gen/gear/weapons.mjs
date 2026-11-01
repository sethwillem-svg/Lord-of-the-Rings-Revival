/**
 * Weapons for men (specs/core/model-generator.md, Gear). Convention: one root bone `weapon` pivoted at the grip
 * (origin, middle of the fist); +Y toward the striking end; -Z is the edge or striking face; flats face +-X.
 * Shields face -Z with the boss on the grip; bows keep limbs on +-Y and the string on +Z. 1 px = about 5.6 cm.
 */
import { GearAssembly, buildGear, gearPalette } from '../lib/gear.mjs';

const W = 'weapon';
const EMBLEM = {   // shield art (our own), drawn on the -Z face in the emblem slot
  horse: ['..xx.', '.xxxx', 'xx.x.', '..xx.', '.x.x.'],
  tree: ['x.x.x', '.xxx.', '..x..', '..x..', '.xxx.'],
};
const centred = (w, h, art, ch = 'x') => {
  const rows = Array.from({ length: h }, () => Array(w).fill('.'));
  const ox = Math.floor((w - art[0].length) / 2), oy = Math.floor((h - art.length) / 2);
  art.forEach((line, y) => [...line].forEach((c, x) => { if (c === 'x') rows[oy + y][ox + x] = ch; }));
  return rows.map((r) => r.join(''));
};
const stamp = (grid, legend) => ({ north: { grid, legend } });

function blade(g, { pommel, grip, guard, blades, tip, guardSlot = 'metal', pommelSlot = 'metal_dark' }) {
  g.box('pommel', W, [-1, 1], pommel, [-1, 1], pommelSlot, { style: 'metal' });
  g.box('grip', W, [-0.5, 0.5], grip, [-0.5, 0.5], 'leather', { style: 'leather' });
  g.box('guard', W, [-0.5, 0.5], guard.y, guard.z, guardSlot, { style: 'metal' });
  blades.forEach(([y, z], i) => g.box(`blade_${i}`, W, [-0.5, 0.5], y, z, 'metal', { style: 'metal' }));
  if (tip) g.box('tip', W, [-0.5, 0.5], tip[0], tip[1], 'metal', { style: 'metal' });
}

function polearm(g, { butt, shaft, socket, head, tip, tipSlot = 'metal', wrap = [-1, 1] }) {
  g.box('butt', W, [-1, 1], butt, [-1, 1], 'metal_dark', { style: 'metal' });
  g.box('shaft', W, [-0.5, 0.5], shaft, [-0.5, 0.5], 'wood', { style: 'cloth' });
  g.box('grip_wrap', W, [-1, 1], wrap, [-1, 1], 'leather', { style: 'leather', detail: true });
  g.box('socket', W, [-1, 1], socket, [-1, 1], 'metal_dark', { style: 'metal' });
  g.box('head', W, [-0.5, 0.5], head, [-1.5, 1.5], 'metal', { style: 'metal' });
  g.box('tip', W, [-0.5, 0.5], tip, [-0.5, 0.5], tipSlot, { style: 'metal' });
}

function bow(g, { limbs, tips, tipSlot = 'wood' }) {
  g.box('riser', W, [-0.5, 0.5], [-2, 2], [-0.5, 0.5], 'wood', { style: 'cloth' });
  g.box('grip_wrap', W, [-1, 1], [-1, 1], [-1, 1], 'leather', { style: 'leather' });
  limbs.forEach(([y0, y1, z0], i) => {
    g.box(`limb_up_${i}`, W, [-0.5, 0.5], [y0, y1], [z0, z0 + 1], i === limbs.length - 1 ? tipSlot : 'wood', { style: 'cloth' });
    g.box(`limb_down_${i}`, W, [-0.5, 0.5], [-y1, -y0], [z0, z0 + 1], i === limbs.length - 1 ? tipSlot : 'wood', { style: 'cloth' });
  });
  g.box('string', W, [-0.5, 0.5], [-tips, tips], [limbs[limbs.length - 1][2] + 1, limbs[limbs.length - 1][2] + 2], 'string');
}

/** Round shield from stacked bands (no overlaps, so no coplanar faces), face on -Z, grip handle behind on the origin. */
function roundShield(g, bands, { slot, style, art, legend, boss }) {
  g.box('handle', W, [-0.5, 0.5], [-1, 1], [-0.5, 0.5], 'leather', { style: 'leather' });
  bands.forEach(([hw, y0, y1], i) => {
    const w = hw * 2, h = y1 - y0;
    const extra = art && i === 0 ? { stamps: stamp(centred(w, h, art), legend) } : {};
    g.box(`face_${i}`, W, [-hw, hw], [y0, y1], [-1.5, -0.5], slot, { style, ...extra });
    if (i > 0) g.box(`face_${i}b`, W, [-hw, hw], [-y1, -y0], [-1.5, -0.5], slot, { style });
  });
  if (boss) g.box('boss', W, [-boss.r, boss.r], [-boss.r, boss.r], [-1.5 - boss.d, -1.5], 'metal', { style: 'metal' });
}

export const WEAPONS = {
  longsword: { name: 'Longsword', kind: 'sword', factions: ['gondor', 'dunedain', 'dale'],
    build: (g) => blade(g, { pommel: [-3, -2], grip: [-2, 2], guard: { y: [2, 3], z: [-2.5, 2.5] }, blades: [[[3, 16], [-1, 1]]], tip: [[16, 17], [-0.5, 0.5]] }) },
  rohan_sword: { name: 'Rohan sword', kind: 'sword', factions: ['rohan'],
    build: (g) => blade(g, { pommel: [-3, -2], grip: [-2, 2], guard: { y: [2, 3], z: [-2, 2] }, blades: [[[3, 14], [-1.5, 1.5]]],
      tip: [[14, 15], [-1, 1]], guardSlot: 'trim', pommelSlot: 'trim' }) },
  scimitar: { name: 'Scimitar', kind: 'sword', factions: ['harad'],
    build: (g) => blade(g, { pommel: [-3, -2], grip: [-2, 1], guard: { y: [1, 2], z: [-1.5, 1.5] },
      blades: [[[2, 6], [-1, 1]], [[6, 9], [-0.5, 1.5]], [[9, 11], [0, 2]]], tip: [[11, 13], [1, 2]], guardSlot: 'trim', pommelSlot: 'trim' }) },
  spear: { name: 'Spear', kind: 'spear', factions: ['rohan', 'gondor', 'dale'],
    build: (g) => polearm(g, { butt: [-13, -12], shaft: [-12, 22], socket: [22, 23], head: [23, 26], tip: [26, 27] }) },
  harad_spear: { name: 'Haradrim spear', kind: 'spear', factions: ['harad'],
    build: (g) => {
      polearm(g, { butt: [-13, -12], shaft: [-12, 22], socket: [22, 23], head: [23, 26], tip: [26, 27], tipSlot: 'paint' });
      g.box('ribbon', W, [0.5, 1.5], [19, 22], [-0.5, 0.5], 'paint', { detail: true });
    } },
  hand_axe: { name: 'Hand axe', kind: 'axe', factions: ['dunland'],
    build: (g) => {
      g.box('haft', W, [-0.5, 0.5], [-2, 7], [-0.5, 0.5], 'wood', { style: 'cloth' });
      g.box('head', W, [-0.5, 0.5], [5, 8], [-3.5, -0.5], 'metal', { style: 'metal' });
      g.box('bit', W, [-0.5, 0.5], [4, 9], [-4.5, -3.5], 'metal', { style: 'metal' });
      g.box('poll', W, [-0.5, 0.5], [6, 7], [0.5, 1.5], 'metal_dark', { style: 'metal' });
    } },
  great_axe: { name: 'Great axe', kind: 'axe', factions: ['dunland'],
    build: (g) => {
      g.box('haft', W, [-0.5, 0.5], [-8, 13], [-0.5, 0.5], 'wood', { style: 'cloth' });
      g.box('grip_wrap', W, [-1, 1], [-1, 1], [-1, 1], 'leather', { style: 'leather', detail: true });
      g.box('cap', W, [-1, 1], [13, 14], [-1, 1], 'metal_dark', { style: 'metal' });
      g.box('head', W, [-0.5, 0.5], [9, 13], [-4.5, -0.5], 'metal', { style: 'metal' });
      g.box('bit', W, [-0.5, 0.5], [8, 14], [-5.5, -4.5], 'metal', { style: 'metal' });
      g.box('spike', W, [-0.5, 0.5], [10, 12], [0.5, 2.5], 'metal_dark', { style: 'metal' });
    } },
  club: { name: 'Club', kind: 'club', factions: ['dunland'],
    build: (g) => {
      g.box('handle', W, [-0.5, 0.5], [-3, 4], [-0.5, 0.5], 'wood', { style: 'cloth' });
      g.box('head', W, [-1.5, 1.5], [4, 11], [-1.5, 1.5], 'wood', { style: 'leather' });
      g.box('stud_a', W, [-0.5, 0.5], [6, 7], [-2.5, -1.5], 'metal_dark', { detail: true });
      g.box('stud_b', W, [1.5, 2.5], [8, 9], [-0.5, 0.5], 'metal_dark', { detail: true });
    } },
  javelin: { name: 'Javelin', kind: 'spear', factions: ['dunland'],
    build: (g) => {
      g.box('shaft', W, [-0.5, 0.5], [-9, 18], [-0.5, 0.5], 'wood', { style: 'cloth' });
      g.box('head', W, [-0.5, 0.5], [18, 20], [-1, 1], 'metal_dark', { style: 'metal' });
      g.box('tip', W, [-0.5, 0.5], [20, 21], [-0.5, 0.5], 'metal_dark', { style: 'metal' });
    } },
  longbow: { name: 'Longbow', kind: 'bow', factions: ['gondor', 'dale', 'dunedain'],
    build: (g) => bow(g, { limbs: [[2, 7, 0], [7, 12, 0.5], [12, 16, 1]], tips: 16 }) },
  short_bow: { name: 'Short bow', kind: 'bow', factions: ['rohan'],
    build: (g) => bow(g, { limbs: [[2, 6, 0], [6, 9, 0.5], [9, 11, 1]], tips: 11 }) },
  harad_bow: { name: 'Haradrim bow', kind: 'bow', factions: ['harad'],
    build: (g) => bow(g, { limbs: [[2, 7, 0], [7, 10, 1], [10, 12, 0.5]], tips: 12, tipSlot: 'trim' }) },
  rohan_shield: { name: 'Rohan round shield', kind: 'shield', factions: ['rohan'],
    build: (g) => roundShield(g, [[6.5, -3.5, 3.5], [5.5, 3.5, 5.5], [3.5, 5.5, 6.5]],
      { slot: 'cloth', style: 'leather', art: EMBLEM.horse, legend: { x: ['emblem', 1] } }) },
  gondor_shield: { name: 'Gondor tall shield', kind: 'shield', factions: ['gondor'],
    build: (g) => {
      g.box('handle', W, [-0.5, 0.5], [-1, 1], [-0.5, 0.5], 'leather', { style: 'leather' });
      g.box('face', W, [-5, 5], [-7.5, 10.5], [-1.5, -0.5], 'cloth', { style: 'leather', stamps: stamp(centred(10, 18, EMBLEM.tree), { x: ['emblem', 1] }) });
      g.box('face_low', W, [-3.5, 3.5], [-9.5, -7.5], [-1.5, -0.5], 'cloth', { style: 'leather' });
      g.box('face_point', W, [-1.5, 1.5], [-10.5, -9.5], [-1.5, -0.5], 'cloth', { style: 'leather' });
    } },
  harad_shield: { name: 'Haradrim spiked shield', kind: 'shield', factions: ['harad'],
    build: (g) => roundShield(g, [[6, -3, 3], [5, 3, 5], [3, 5, 6]], { slot: 'trim', style: 'leather',
      art: ['xx...xx', 'xxx.xxx', '.xx.xx.', '.......', '.xx.xx.', 'xxx.xxx'], legend: { x: ['emblem', 1] }, boss: { r: 0.5, d: 2 } }) },
  hide_shield: { name: 'Hide shield', kind: 'shield', factions: ['dunland'],
    build: (g) => roundShield(g, [[6, -3, 3], [5, 3, 5], [3, 5, 6]], { slot: 'leather', style: 'leather', boss: { r: 1, d: 1 } }) },
};
for (const w of Object.values(WEAPONS)) w.hand = w.kind === 'shield' || w.kind === 'bow' ? 'left_item' : 'right_item';

export function buildWeapon(id) {
  const w = WEAPONS[id];
  if (!w) throw new Error(`unknown weapon "${id}" (known: ${Object.keys(WEAPONS).join(', ')})`);
  const g = new GearAssembly([[W, null, [0, 0, 0]]]);
  w.build(g);
  return buildGear(id, g, gearPalette(w.factions[0]));
}

/** Index entries for the viewer and later item definitions: hand, kind, factions, length, grip and tip. */
export function weaponIndex() {
  return Object.entries(WEAPONS).map(([id, w]) => {
    const { model } = buildWeapon(id);
    const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
    for (const c of model.cubes) for (let a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], c.from[a]); hi[a] = Math.max(hi[a], c.to[a]); }
    const length = w.kind === 'shield' ? Math.max(hi[0] - lo[0], hi[1] - lo[1]) : hi[1] - lo[1];
    return { id, name: w.name, hand: w.hand, kind: w.kind, factions: w.factions, length, grip: [0, 0, 0], tip: [0, hi[1], 0] };
  });
}
