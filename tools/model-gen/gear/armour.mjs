/**
 * Armour sets for men (specs/core/model-generator.md, Gear): one model per faction, groups named after the anchor
 * bones and pivoted where the largest man's anchors are. Every box below is relative to its anchor's pivot; the
 * viewer moves each group onto the loaded man's anchor (boot armour onto the shin, so greaves bend with the knee).
 */
import { GearAssembly, buildGear, gearPalette } from '../lib/gear.mjs';
import { MAN as MAN_TABLE } from '../races/man.mjs';
import { largestBody } from '../lib/body.mjs';
import { buildModel } from '../lib/build.mjs';

/** The largest man with the widest head and hair: what the generic (unfitted) sets are built on. */
let LARGEST = null;
export function largestMan() {
  if (!LARGEST) LARGEST = buildModel('man', 'largest', { body: largestBody({ ...MAN_TABLE }),
    overrides: { head: 'broad', hair: 'shoulder', ears: 'large', beard: 'none' } });
  return LARGEST;
}

/** Body cubes each fitted piece is measured from (the head: skull, hair and ears, not the face or beard). */
const FIT = {
  head_armor: ['skull', 'hair_cap', 'hair_back', 'hair_side_r', 'hair_side_l', 'hair_fringe', 'ear_r', 'ear_l'],
  chest_armor: ['tunic'], right_arm_armor: ['sleeve_r'], right_forearm_armor: ['forearm_r'],
  right_leg_armor: ['thigh_r'], right_boot_armor: ['shin_r', 'boot_r'],
};

/** Where a group attaches on the man when it is not the same-named bone. */
export const ATTACH = { right_boot_armor: 'right_shin', left_boot_armor: 'left_shin',
  // not anchors in rendering-animation 5b: forearm armour rides the forearm so it bends at the elbow
  right_forearm_armor: 'right_forearm', left_forearm_armor: 'left_forearm' };

/** Main body cubes each group must enclose at rest (tests). clipX absolute, clipY relative to the anchor. */
export const COVERS = {
  head_armor: [{ name: 'hair_cap', clipY: [6, 99] }, { name: 'hair_back', clipY: [6, 99] }],
  chest_armor: [{ name: 'tunic', clipY: [1, 99] }],
  right_arm_armor: [{ name: 'sleeve_r' }], left_arm_armor: [{ name: 'sleeve_l' }],
  right_leg_armor: [{ name: 'tunic_skirt', clipX: [-99, 0] }, { name: 'thigh_r', clipX: [-99, 0], clipY: [-99, -3] }],
  left_leg_armor: [{ name: 'tunic_skirt', clipX: [0, 99] }, { name: 'thigh_l', clipX: [0, 99], clipY: [-99, -3] }],
  right_boot_armor: [{ name: 'shin_r' }, { name: 'boot_r' }], left_boot_armor: [{ name: 'shin_l' }, { name: 'boot_l' }],
  right_forearm_armor: [{ name: 'forearm_r' }], left_forearm_armor: [{ name: 'forearm_l' }],
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
  /** man: a buildModel result; pieces are placed on its anchors and may be measured from its cubes */
  constructor(man) {
    this.man = man;
    this.ref = man.body.pivots;
    this.used = new Set();
    this.cubes = [];
  }
  /**
   * Bounding box of the man's cubes listed in FIT for a group, relative to the group's pivot, optionally cut to an
   * absolute x range (the skirt is split between the legs). Missing cubes are skipped (a bald man has no hair cap).
   */
  fit(group, names = FIT[group], clipX = null) {
    const p = this.ref[ATTACH[group] || group];
    const cubes = names.map((n) => this.man.model.findCube(n)).filter(Boolean);
    const lo = [0, 1, 2].map((a) => Math.min(...cubes.map((c) => (a === 0 && clipX ? Math.max(c.from[0], clipX[0]) : c.from[a]))));
    const hi = [0, 1, 2].map((a) => Math.max(...cubes.map((c) => (a === 0 && clipX ? Math.min(c.to[0], clipX[1]) : c.to[a]))));
    return { x: [lo[0] - p[0], hi[0] - p[0]], y: [lo[1] - p[1], hi[1] - p[1]], z: [lo[2] - p[2], hi[2] - p[2]] };
  }
  /** a body-relative height: an absolute y of the man minus the group's pivot */
  rel(group, y) { return y - this.ref[ATTACH[group] || group][1]; }
  /** box relative to the group's anchor pivot (or the bone ATTACH names for it) */
  box(group, name, [x0, x1], [y0, y1], [z0, z1], slot, style, extra = {}) {
    const p = this.ref[ATTACH[group] || group];
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
  // Gondor: steel plate over black, fitted to each man: every piece is measured from the cubes it covers (FIT) and sits
  // half a pixel outside them. Helm crown, cheek guards and back around an open face, brow band, crest, white wings;
  // riveted breastplate with the White Tree, black faulds and tassets rimmed in silver, gold belt; pauldron with a
  // black lame, mail upper arm, vambraces on the forearms; cuisses, knee cops and greaves.
  gondor: { name: 'Gondor citadel', fitted: true,
    // the helm is a shell (crown, sides, back, open face): one box spans the hair only above the brow
    covers: { head_armor: [{ name: 'hair_cap', clipY: [8, 99] }, { name: 'hair_back', clipY: [8, 99] }] },
    build: (k) => {
      const steel = ['metal', 'plate'], black = ['cloth', 'plate', { edge: 'trim' }];
      const m = 0.5;
      // helm
      const h = k.fit('head_armor'), g = k.man.asm.meta.head;
      const X = [h.x[0] - m, h.x[1] + m], Z = [h.z[0] - m, h.z[1] + m], top = h.y[1] + 1;   // heights are whole pixels
      const brow = k.rel('head_armor', g.rowY(g.eye - 1)[1]), mouth = k.rel('head_armor', g.rowY(g.mouth)[0]);
      const neck = k.rel('head_armor', g.sb);
      k.box('head_armor', 'helm', X, [brow, top], Z, ...steel);
      k.pair('head_armor', 'helm_side', [X[0], X[0] + 1], [mouth, brow], Z, ...steel);
      k.box('head_armor', 'helm_back', [X[0] + 1, X[1] - 1], [neck, brow], [Z[1] - 1, Z[1]], ...steel);
      k.box('head_armor', 'brow_band', [X[0] + 1, X[1] - 1], [brow, brow + 1], [Z[0] - 1, Z[0]], 'cloth', 'plate', { edge: 'trim' });
      k.box('head_armor', 'crest', [-0.5, 0.5], [top, top + 3], [Z[0] + 2, Z[1] - 1], ...steel);
      const wz = Math.floor((Z[0] + Z[1]) / 2);
      k.pair('head_armor', 'wing', [X[0] - 1, X[0]], [brow, top + 1], [wz, wz + 2], 'hair', 'hair');
      k.pair('head_armor', 'wing_tip', [X[0] - 1, X[0]], [top + 1, top + 4], [wz + 1, wz + 2], 'hair', 'hair', { detail: true });
      // body: breastplate above two faulds; the front clears the tunic lace
      const c = k.fit('chest_armor'), cx = [c.x[0] - m, c.x[1] + m], cz = [c.z[0] - 1.5, c.z[1] + m];
      k.box('chest_armor', 'breastplate', cx, [5, c.y[1] + 1], cz, 'metal', 'plate',
        { stamps: { north: { grid: art(cx[1] - cx[0], 7, TREE, [Math.floor((cx[1] - cx[0] - 5) / 2), 1]), legend: { x: ['cloth', 1] } } } });
      k.box('chest_armor', 'fauld_1', cx, [3, 5], cz, ...black);
      k.box('chest_armor', 'fauld_2', cx, [1, 3], cz, ...black);
      k.box('chest_armor', 'belt', [cx[0] - 0.5, cx[1] + 0.5], [4.5, 5.5], [cz[0] - 0.5, cz[1] + 0.5], 'accent', 'plate');
      // arms (right side measured; both() mirrors)
      const a = k.fit('right_arm_armor');
      k.both('arm_armor', 'pauldron', [a.x[0] - 1, a.x[1] + 1], [a.y[1] - 1, a.y[1] + 1], [a.z[0] - 1, a.z[1] + 1], ...steel);
      k.both('arm_armor', 'pauldron_lame', [a.x[0] - 1.5, a.x[1] + 0.5], [a.y[1] - 3, a.y[1] - 1], [a.z[0] - 1.5, a.z[1] + 1.5], ...black);
      k.both('arm_armor', 'rerebrace', [a.x[0] - m, a.x[1] + m], [a.y[0] - 1, a.y[1] - 3], [a.z[0] - m, a.z[1] + m], 'metal', 'rings');
      const f = k.fit('right_forearm_armor');
      k.both('forearm_armor', 'vambrace', [f.x[0] - m, f.x[1] + m], [f.y[0] + 1, f.y[1]], [f.z[0] - m, f.z[1] + m], ...steel);
      // legs: tasset lames over the skirt half (cut at the centre seam), cuisses below
      const sk = k.fit('right_leg_armor', ['tunic_skirt'], [-99, 0]), l = k.fit('right_leg_armor');
      const tz = [sk.z[0] - m, sk.z[1] + m], mid = sk.y[1] + m - 3;
      for (const [side, s] of [['right', 1], ['left', -1]]) {
        const outer = sk.x[1] - Math.ceil(sk.x[1] - sk.x[0] + m);   // at least m outside, size still whole
        const tx = s > 0 ? [outer, sk.x[1]] : [-sk.x[1], -outer];
        const k2 = side === 'right' ? 'r' : 'l';
        k.box(`${side}_leg_armor`, `tasset_${k2}`, tx, [mid, sk.y[1] + m], tz, ...black);
        k.box(`${side}_leg_armor`, `tasset_low_${k2}`, tx, [sk.y[0] - m, mid], tz, ...black);
      }
      k.both('leg_armor', 'cuisse', [l.x[0] - m, l.x[1] + m], [l.y[0] - m, sk.y[0] - m], [l.z[0] - m, l.z[1] + m], ...steel);
      // shins: greave from the boot top to below the knee, knee cop over the joint
      const b = k.fit('right_boot_armor'), bootTop = k.rel('right_boot_armor', k.man.model.findCube('boot_r').to[1]);
      k.both('boot_armor', 'greave', [b.x[0] - m, b.x[1] + m], [bootTop - 1, -1], [b.z[0] - m, b.z[1] + m], ...steel);
      k.both('boot_armor', 'knee_cop', [b.x[0] - 1, b.x[1] + 1], [-1, 2], [b.z[0] - 1, b.z[1] + 1], ...steel);
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

/** What each group of a set must enclose: the shared COVERS, with the set's own overrides. */
export function coversFor(faction) {
  return { ...COVERS, ...((ARMOUR[faction] || {}).covers || {}) };
}

/**
 * Build a faction's armour on a man (a buildModel result). Fitted sets (Gondor) measure that man; the others use
 * fixed boxes. Without a man, the set is built on the largest man: the generic set the viewer falls back to.
 */
export function buildArmour(faction, man = largestMan()) {
  const set = ARMOUR[faction];
  if (!set) throw new Error(`unknown armour set "${faction}" (known: ${Object.keys(ARMOUR).join(', ')})`);
  const k = new Kit(man);
  set.build(k);
  const groups = [...k.used].sort();
  const g = new GearAssembly(groups.map((n) => [n, null, k.ref[ATTACH[n] || n]]));
  for (const c of k.cubes) g.add(c);
  return buildGear(`armour_${faction}`, g, gearPalette(faction), 128);
}

export function armourIndex() {
  return Object.entries(ARMOUR).map(([id, a]) => {
    const { model } = buildArmour(id);
    return { id, name: a.name, faction: id, pieces: model.rig.names, attach: ATTACH };
  });
}
