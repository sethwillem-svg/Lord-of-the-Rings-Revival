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
  /**
   * box relative to the group's anchor pivot (or the bone ATTACH names for it). Armour cubes are inflated by a quarter
   * pixel, as vanilla armour layers are: their faces then never lie on the body's faces (which sit on whole and half
   * pixels), so nothing flickers or blends, while the texture stays one texel per pixel.
   */
  box(group, name, [x0, x1], [y0, y1], [z0, z1], slot, style, extra = {}) {
    const p = this.ref[ATTACH[group] || group];
    this.used.add(group);
    this.cubes.push({ name, bone: group, from: [x0 + p[0], y0 + p[1], z0 + p[2]], to: [x1 + p[0], y1 + p[1], z1 + p[2]], slot, style,
      inflate: 0.25, ...extra });
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
  // Gondor v3, Minecraft style: few big pieces drawn pixel by pixel (the standard man makes every face a fixed size).
  // One helmet box with a see-through face window, cheek guards and a silver brow band, plus crest, nasal guard and
  // white wings; one chest box with the White Tree, gold belt and black faulds rimmed in silver; one box per upper arm
  // (pauldron over black lames), forearm, tasset, cuisse and greave. Sizes keep every armour face off the body's
  // face planes (belt, buckle, pouches and cuffs included), so nothing flickers or blends.
  gondor: { name: 'Gondor citadel',
    build: (k) => {
      const steel = (extra = {}) => ['metal', 'plate', { material: 'gondor_steel', ...extra }];
      const L = { ' ': 'clear', T: ['trim', 1], t: ['trim', 3], R: ['metal', 0], K: ['metal', 4], k: ['metal', 3],
        b: ['cloth', 2], B: ['cloth', 4], c: ['cloth', 1], G: ['accent', 1], g: ['accent', 3], x: ['cloth', 2], w: ['emblem', 1] };
      const grid = (rows) => ({ grid: rows, legend: L });
      const fill = (w, h, rowFn) => Array.from({ length: h }, (_, y) => rowFn(y, w));
      const rep = (ch, w) => ch.repeat(w);
      // helmet: 12 x 13 x 12 around the head and any hair, down to a gorget over the neck; face window over brows,
      // eyes, nose and mouth (rows 5-9 line up with the face's pixels)
      k.box('head_armor', 'helmet', [-6, 6], [-2, 11], [-6.5, 5.5], ...steel({ stamps: {
        north: grid(['............', '............', '............', '.R........R.', 'TTTTTTTTTTTT', '..K      K..',
          '..K      K..', '..KK    KK..', '...K    K...', '...K    K...', '...kkkkkk...', 'TTTTTTTTTTTT', 'tttttttttttt']),
        east: grid(fill(12, 13, (y, w) => (y === 4 || y === 11 ? rep('T', w) : y === 3 ? '.R...R...R..' : y === 7 ? rep('k', w) : rep('.', w)))),
        west: grid(fill(12, 13, (y, w) => (y === 4 || y === 11 ? rep('T', w) : y === 3 ? '..R...R...R.' : y === 7 ? rep('k', w) : rep('.', w)))),
        south: grid(fill(12, 13, (y, w) => (y === 4 || y === 11 ? rep('T', w) : y === 6 || y === 8 || y === 10 ? rep('k', w) : rep('.', w)))),
        up: grid(fill(12, 12, (y, w) => '.....kk.....')),
        down: grid(fill(12, 12, (y, w) => rep(' ', w))),
      } }));
      k.box('head_armor', 'crest', [-0.5, 0.5], [11, 14], [-3.5, 4.5], ...steel());
      k.box('head_armor', 'nasal', [-0.5, 0.5], [3, 6], [-7.5, -6.5], ...steel());
      k.pair('head_armor', 'wing', [-7, -6], [5, 13], [0.5, 2.5], 'hair', 'hair');
      // chest: 8 x 11 x 6 over the tunic and lace, above the belt (no wider than the tunic, so it never cuts the arms)
      const tree = ['x.x.x', '.xxx.', '..x..', '..x..', '.xxx.'];
      const chestRow = (y, w, emblem) => {
        if (y === 7) return rep('G', w);
        if (y === 8 || y === 10) return rep('b', w);
        if (y === 9) return rep('T', w);
        if (y === 6) return rep('k', w);
        if (emblem && y >= 1 && y <= 5) { const o = (w - 5) / 2; return '.'.repeat(Math.floor(o)) + tree[y - 1] + '.'.repeat(Math.ceil(o)); }
        if (y === 0 && w >= 6) return 'R' + '.'.repeat(w - 2) + 'R';
        return rep('.', w);
      };
      k.box('chest_armor', 'chest', [-4, 4], [1, 12], [-3.5, 2.5], ...steel({ stamps: {
        north: grid(fill(8, 11, (y, w) => chestRow(y, w, true))), south: grid(fill(8, 11, (y, w) => chestRow(y, w, false))),
        east: grid(fill(6, 11, (y, w) => chestRow(y, w, false))), west: grid(fill(6, 11, (y, w) => chestRow(y, w, false))),
      } }));
      // upper arms: pauldron over black lames with silver rims
      const armRow = (y, w) => (y === 4 ? rep('T', w) : y >= 5 ? rep(y === 7 ? 'B' : 'b', w) : rep('.', w));   // steel pauldron, one silver rim, black below
      // flush with the inside of the arm (so it stays out of the chest), a pixel wider than the sleeve outside
      const armFaces = { north: grid(fill(4, 8, armRow)), south: grid(fill(4, 8, armRow)), east: grid(fill(6, 8, armRow)), west: grid(fill(6, 8, armRow)) };
      k.both('arm_armor', 'arm', [-2.5, 1.5], [-5, 3], [-3, 3], ...steel({ stamps: armFaces }));
      // vambraces: below the sleeve cuff, over the wrist wrap
      const vamRow = (y, w) => (y === 0 ? rep('T', w) : y === 3 ? rep('k', w) : rep('.', w));
      k.both('forearm_armor', 'vambrace', [-1.5, 1.5], [-4, 0], [-1.5, 1.5], ...steel({ stamps: { north: grid(fill(3, 4, vamRow)), south: grid(fill(3, 4, vamRow)), east: grid(fill(3, 4, vamRow)), west: grid(fill(3, 4, vamRow)) } }));
      // tassets: black lames over the skirt halves, below the belt; cuisses below
      const tasRow = (y, w) => (y === 3 ? rep('T', w) : y === 0 ? rep('c', w) : rep('b', w));   // black lame, lit top, silver bottom rim
      const tas = { north: grid(fill(5, 4, tasRow)), south: grid(fill(5, 4, tasRow)), east: grid(fill(9, 4, tasRow)), west: grid(fill(9, 4, tasRow)) };
      k.box('right_leg_armor', 'tasset_r', [-3.5, 1.5], [-4.5, -0.5], [-5, 4], ...steel({ stamps: tas }));
      k.box('left_leg_armor', 'tasset_l', [-1.5, 3.5], [-4.5, -0.5], [-5, 4], ...steel({ stamps: tas }));
      k.box('right_leg_armor', 'cuisse_r', [-2.5, 1.5], [-7, -4], [-2.5, 2.5], ...steel());
      k.box('left_leg_armor', 'cuisse_l', [-1.5, 2.5], [-7, -4], [-2.5, 2.5], ...steel());
      // greaves on the shins, knee rim on top
      const grRow = (y, w) => (y === 0 ? rep('T', w) : y === 1 ? rep('k', w) : rep('.', w));
      k.both('boot_armor', 'greave', [-2, 2], [-3.5, 1.5], [-3, 2], ...steel({ stamps: { north: grid(fill(4, 5, grRow)), south: grid(fill(4, 5, grRow)), east: grid(fill(5, 5, grRow)), west: grid(fill(5, 5, grRow)) } }));
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
