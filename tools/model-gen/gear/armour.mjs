/**
 * Armour sets for men (specs/core/model-generator.md, Gear): one model per faction, groups named after the anchor
 * bones and pivoted where the largest man's anchors are. Every box below is relative to its anchor's pivot; the
 * viewer moves each group onto the loaded man's anchor (boot armour onto the shin, so greaves bend with the knee).
 */
import { GearAssembly, buildGear, gearPalette } from '../lib/gear.mjs';
import { largestBody } from '../lib/body.mjs';
import { MAN } from '../races/man.mjs';

/** Where a group attaches on the man when it is not the same-named bone. */
export const ATTACH = { right_boot_armor: 'right_shin', left_boot_armor: 'left_shin' };

/** Main body cubes each group must enclose at rest (tests). clipX absolute, clipY relative to the anchor. */
export const COVERS = {
  head_armor: [{ name: 'hair_cap', clipY: [6, 99] }, { name: 'hair_back', clipY: [6, 99] }],
  chest_armor: [{ name: 'tunic', clipY: [1, 99] }],
  right_arm_armor: [{ name: 'sleeve_r' }], left_arm_armor: [{ name: 'sleeve_l' }],
  right_leg_armor: [{ name: 'tunic_skirt', clipX: [-99, 0] }, { name: 'thigh_r', clipX: [-99, 0], clipY: [-99, -3] }],
  left_leg_armor: [{ name: 'tunic_skirt', clipX: [0, 99] }, { name: 'thigh_l', clipX: [0, 99], clipY: [-99, -3] }],
  right_boot_armor: [{ name: 'shin_r' }, { name: 'boot_r' }], left_boot_armor: [{ name: 'shin_l' }, { name: 'boot_l' }],
};

const TREE = ['x.x.x', '.xxx.', '..x..', '..x..', '.xxx.'];
const STAR = ['.x.', 'xxx', '.x.'];
function art(w, h, pic, at) {
  const rows = Array.from({ length: h }, () => Array(w).fill('.'));
  const [ox, oy] = at || [Math.floor((w - pic[0].length) / 2), Math.floor((h - pic.length) / 2)];
  pic.forEach((line, y) => [...line].forEach((c, x) => { if (c === 'x' && rows[oy + y]) rows[oy + y][ox + x] = 'x'; }));
  return rows.map((r) => r.join(''));
}
const studs = (w, h) => Array.from({ length: h }, (_, y) => Array.from({ length: w }, (_, x) => (x % 3 === 1 && y % 3 === 1 ? 'x' : '.')).join(''));

class Kit {
  constructor() {
    this.ref = largestBody(MAN).pivots;
    this.used = new Set();
    this.cubes = [];
  }
  /** box relative to the group's anchor pivot */
  box(group, name, [x0, x1], [y0, y1], [z0, z1], slot, style, extra = {}) {
    const p = this.ref[group];
    this.used.add(group);
    this.cubes.push({ name, bone: group, from: [x0 + p[0], y0 + p[1], z0 + p[2]], to: [x1 + p[0], y1 + p[1], z1 + p[2]], slot, style, ...extra });
  }
  both(side, name, rx, y, z, slot, style, extra) {   // a right/left pair; rx is the right side's x range
    this.box(`right_${side}`, `${name}_r`, rx, y, z, slot, style, extra);
    this.box(`left_${side}`, `${name}_l`, [-rx[1], -rx[0]], y, z, slot, style, extra);
  }
  pair(group, name, rx, y, z, slot, style, extra) {   // mirrored pair inside one group; rx is the right side's x range
    this.box(group, `${name}_r`, rx, y, z, slot, style, extra);
    this.box(group, `${name}_l`, [-rx[1], -rx[0]], y, z, slot, style, extra);
  }
  bowl(y, slot, style) { this.box('head_armor', 'helm', [-5.5, 5.5], y, [-5, 5], slot, style); }
  body(slot, style, extra) { this.box('chest_armor', 'body', [-4.5, 4.5], [1, 12], [-4, 3], slot, style, extra); }
  belt(slot = 'leather', style = 'leather') { this.box('chest_armor', 'belt', [-5, 5], [1, 2], [-4.5, 3.5], slot, style); }
  arms(slot, style) { this.both('arm_armor', 'sleeve', [-2.5, 2.5], [-5, 3], [-2.5, 2.5], slot, style); }
  pauldrons(slot, style) { this.both('arm_armor', 'pauldron', [-3, 3], [0.5, 3.5], [-3, 3], slot, style); }
  tassets(slot, style) {
    this.box('right_leg_armor', 'tasset_r', [-3.5, 1.5], [-3.5, 1.5], [-3.5, 3.5], slot, style);
    this.box('left_leg_armor', 'tasset_l', [-1.5, 3.5], [-3.5, 1.5], [-3.5, 3.5], slot, style);
  }
  thighGuards(slot, style) { this.both('leg_armor', 'thigh_guard', [-2, 2], [-7.5, -2.5], [-2, 2], slot, style); }
  greaves(slot, style) { this.both('boot_armor', 'greave', [-2, 2], [-3, 1], [-3, 2], slot, style); }
}

export const ARMOUR = {
  rohan: { name: 'Rohan mail', build: (k) => {
    k.bowl([6, 11], 'metal', 'metal');
    k.box('head_armor', 'nasal', [-0.5, 0.5], [3, 6], [-6, -5], 'metal', 'metal');
    k.box('head_armor', 'crest', [-0.5, 0.5], [11, 12], [-3, 5], 'hair', 'hair');
    k.box('head_armor', 'horsetail', [-0.5, 0.5], [5, 11], [5, 6], 'hair', 'hair', { detail: true });
    k.body('metal', 'mail'); k.belt();
    k.arms('metal', 'mail'); k.pauldrons('leather', 'leather');
    k.tassets('metal', 'mail'); k.thighGuards('leather', 'leather'); k.greaves('leather', 'leather');
  } },
  gondor: { name: 'Gondor citadel', build: (k) => {
    k.bowl([6, 12], 'metal', 'metal');
    k.box('head_armor', 'ridge', [-0.5, 0.5], [12, 13], [-4, 4], 'trim', 'metal');
    k.pair('head_armor', 'cheek', [-6.5, -5.5], [2, 7], [-4, 1], 'metal', 'metal');
    k.pair('head_armor', 'wing', [-7.5, -6.5], [7, 11], [0, 3], 'hair', 'hair');
    k.body('metal', 'mail');
    k.box('chest_armor', 'surcoat', [-5, 5], [2, 11], [-4.5, 3.5], 'cloth', 'cloth',
      { stamps: { north: { grid: art(10, 9, TREE), legend: { x: ['emblem', 1] } } } });
    k.arms('metal', 'mail'); k.pauldrons('metal', 'metal');
    k.tassets('metal', 'mail'); k.thighGuards('metal', 'metal'); k.greaves('metal', 'metal');
  } },
  harad: { name: 'Haradrim scale', build: (k) => {
    k.bowl([6, 11], 'trim', 'metal');
    k.box('head_armor', 'spike', [-0.5, 0.5], [11, 13], [-0.5, 0.5], 'metal', 'metal');
    k.box('head_armor', 'band', [-6, 6], [6, 7], [-5.5, 5.5], 'paint', 'cloth');
    k.body('trim', 'scale'); k.belt('paint', 'cloth');
    k.tassets('trim', 'scale');
  } },
  dunland: { name: 'Dunland hide', build: (k) => {
    k.box('head_armor', 'helm', [-5.5, 5.5], [7, 11], [-5, 5], 'leather', 'leather');
    k.box('head_armor', 'fur_rim', [-6, 6], [6, 7], [-5.5, 5.5], 'leather', 'hair');
    k.body('leather', 'leather');
    k.box('chest_armor', 'fur_collar', [-5, 5], [11, 13], [-4.5, 3.5], 'leather', 'hair', { detail: true });
    k.tassets('leather', 'leather');
  } },
  dale: { name: 'Dale brigandine', build: (k) => {
    k.box('head_armor', 'helm', [-5.5, 5.5], [7, 11], [-5, 5], 'metal', 'metal');
    k.box('head_armor', 'brim', [-7, 7], [6, 7], [-6.5, 6.5], 'metal_dark', 'metal');
    k.body('cloth', 'cloth', { stamps: { north: { grid: studs(9, 11), legend: { x: ['metal', 0] } }, south: { grid: studs(9, 11), legend: { x: ['metal', 0] } } } });
    k.belt();
    k.arms('leather', 'leather');
    k.tassets('cloth', 'cloth'); k.thighGuards('leather', 'leather'); k.greaves('leather', 'leather');
  } },
  dunedain: { name: 'Ranger leathers', build: (k) => {
    k.box('head_armor', 'hood', [-6.5, 6.5], [6, 11], [-5, 6], 'cloth', 'cloth');
    k.pair('head_armor', 'hood_side', [-6.5, -5.5], [1, 6], [-4, 6], 'cloth', 'cloth');
    k.box('head_armor', 'hood_back', [-5.5, 5.5], [1, 6], [5, 6], 'cloth', 'cloth');
    k.body('leather', 'leather', { stamps: { north: { grid: art(9, 11, STAR, [5, 1]), legend: { x: ['emblem', 1] } } } });
    k.belt();
    k.box('cape', 'cloak', [-5, 5], [-17, 0], [1.5, 2.5], 'cloth', 'cloth');
  } },
};

export function buildArmour(faction) {
  const set = ARMOUR[faction];
  if (!set) throw new Error(`unknown armour set "${faction}" (known: ${Object.keys(ARMOUR).join(', ')})`);
  const k = new Kit();
  set.build(k);
  const groups = [...k.used].sort();
  const g = new GearAssembly(groups.map((n) => [n, null, k.ref[n]]));
  for (const c of k.cubes) g.add(c);
  return buildGear(`armour_${faction}`, g, gearPalette(faction), 128);
}

export function armourIndex() {
  return Object.entries(ARMOUR).map(([id, a]) => {
    const { model } = buildArmour(id);
    return { id, name: a.name, faction: id, pieces: model.rig.names, attach: ATTACH };
  });
}
