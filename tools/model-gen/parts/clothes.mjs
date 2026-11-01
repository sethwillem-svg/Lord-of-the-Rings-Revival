/**
 * Base clothes: belted tunic with collar and skirt, sleeves, slim trousers (armour adds bulk later), boots. Limb joints overlap by one
 * pixel and the lower piece is half a pixel slimmer on each side, so joints seal without coplanar faces.
 */
export const STYLES = ['tunic'];

// The lace is skipped under a full or long beard, which would hide it.
const EMBLEMS = {   // our own pixel art, painted on the chest in the emblem slot
  horse: ['..xx.', '.xxxx', 'xx.x.', '..xx.', '.x.x.'],
  tree: ['x.x.x', '.xxx.', '..x..', '..x..', '.xxx.'],
  serpent: ['..xx.', '.x...', '..x..', '...x.', '.xx..'],
  star: ['.x.', 'xxx', '.x.'],
};

function trimRows(w, h, neck, emblem = 'none') {
  const rows = Array.from({ length: h }, () => Array(w).fill('.'));
  rows[h - 1].fill('t');
  if (neck) { const n = Math.min(3, w - 2), x = (w - n) / 2 | 0; for (let i = x; i < x + n; i++) rows[0][i] = 't'; }
  const art = EMBLEMS[emblem];
  if (art) {   // the star is a brooch high on the chest; the others sit centred below the lace
    const [ox, oy] = emblem === 'star' ? [w - 4, 1] : [Math.floor((w - art[0].length) / 2), 4];
    art.forEach((line, y) => [...line].forEach((c, x) => { if (c === 'x' && rows[oy + y]) rows[oy + y][ox + x] = 'x'; }));
  }
  return { grid: rows.map((r) => r.join('')), legend: { t: ['tunic_trim', 1], x: ['emblem', 1] } };
}

/** Criss-cross leg wraps painted over a shin face (Dunland). */
const wraps = (w, h) => ({ grid: Array.from({ length: h }, (_, y) => Array.from({ length: w }, (_, x) => ((x + y) % 3 === 0 ? 'w' : '.')).join('')),
  legend: { w: ['tunic_trim', 2] } });

export function addClothes(asm, body, rng) {
  const { legs: L, torso: T, shoulders: S, depth: D, armThick: aT, legThick: lT, upperArm, forearm, arm, shin } = body;
  const top = L + T;
  const o = { pattern: 'plain', emblem: 'none', sleeves: 'short', boots: 'low', legs: 'trousers', ...(asm.meta.outfit || {}) };
  const add = (name, bone, from, to, slot, style, extra = {}) => asm.add({ name, bone, from, to, slot, style, ...extra });
  const cloth = { pattern: o.pattern };

  // torso
  add('tunic', 'torso', [-S / 2, L, -D / 2], [S / 2, top, D / 2], 'tunic', 'cloth',
    { ...cloth, stamps: { north: trimRows(S, T, true, o.emblem), south: trimRows(S, T, false) } });
  add('collar', 'torso', [-2, top - 1, -D / 2 + 0.5], [2, top + 2, D / 2 - 0.5], 'tunic_trim', 'cloth');
  if (!asm.get('beard_chin')) add('tunic_lace', 'torso', [-0.5, top - 4, -D / 2 - 1], [0.5, top - 1, -D / 2], 'tunic_trim', 'flat', { detail: true });
  const sw = S > 2 * lT ? S : 2 * lT + 1;
  add('tunic_skirt', 'body', [-sw / 2, L - 3, -(D + 1) / 2], [sw / 2, L + 1, (D + 1) / 2], 'tunic', 'cloth',
    { ...cloth, stamps: { north: trimRows(sw, 4, false), south: trimRows(sw, 4, false) } });
  const bx = sw / 2 + 0.5, bz = D / 2 + 1;
  add('belt', 'body', [-bx, L, -bz], [bx, L + 1, bz], 'belt', 'leather');
  add('buckle', 'body', [-1, L, -bz - 1], [1, L + 1, -bz], 'metal', 'metal', { detail: true });
  add('belt_end', 'body', [-0.5, L - 2, -bz - 1], [0.5, L, -bz], 'belt', 'leather', { detail: true });   // hangs under the buckle
  const pz = (D + 1) / 2;
  add('pouch', 'body', [-sw / 2 + 0.5, L - 3, -pz - 1], [-sw / 2 + 2.5, L - 1, -pz], 'belt', 'leather', { detail: true });
  add('pouch_flap', 'body', [-sw / 2 + 0.5, L - 2, -pz - 2], [-sw / 2 + 2.5, L - 1, -pz - 1], 'belt', 'leather', { detail: true });
  add('purse', 'body', [sw / 2 - 2.5, L - 3, -pz - 1], [sw / 2 - 0.5, L - 1, -pz], 'belt', 'leather', { detail: true });
  add('purse_tie', 'body', [sw / 2 - 2, L - 2, -pz - 2], [sw / 2 - 1, L - 1, -pz - 1], 'tunic_trim', 'flat', { detail: true });

  for (const [s, k] of [[-1, 'r'], [1, 'l']]) {
    const side = s < 0 ? 'right' : 'left';
    const xi = s * S / 2, xo = s * (S / 2 + aT);             // arm inner and outer x
    const ax = [Math.min(xi, xo), Math.max(xi, xo)];
    const elbow = top - upperArm, wrist = elbow - forearm;
    add(`sleeve_${k}`, `${side}_arm`, [ax[0], elbow, -aT / 2], [ax[1], top, aT / 2], 'tunic', 'cloth', cloth);
    add(`sleeve_cuff_${k}`, `${side}_arm`, [ax[0] - 0.5, elbow, -(aT + 1) / 2], [ax[1] + 0.5, elbow + 1, (aT + 1) / 2], 'tunic_trim', 'cloth', { detail: true });
    // long sleeves paint the forearm as sleeve; same cube either way
    add(`forearm_${k}`, `${side}_forearm`, [ax[0] + 0.5, wrist, -(aT - 1) / 2], [ax[1] - 0.5, elbow + 1, (aT - 1) / 2],
      ...(o.sleeves === 'long' ? ['tunic', 'cloth', cloth] : ['skin', 'skin']));
    add(`wrist_wrap_${k}`, `${side}_forearm`, [ax[0], wrist + 1, -aT / 2], [ax[1], wrist + 2, aT / 2], 'tunic_trim', 'cloth', { detail: true });
    add(`hand_${k}`, `${side}_hand`, [ax[0], top - arm, -aT / 2], [ax[1], wrist + 1, aT / 2], 'skin', 'skin');
    const tx = s < 0 ? ax[1] - 1 : ax[0];
    add(`thumb_${k}`, `${side}_hand`, [tx, wrist - 1, -aT / 2 - 1], [tx + 1, wrist + 1, -aT / 2], 'skin', 'skin', { detail: true });

    const lx = [s < 0 ? -lT : 0, s < 0 ? 0 : lT];
    add(`thigh_${k}`, `${side}_leg`, [lx[0], shin, -lT / 2], [lx[1], L, lT / 2], 'trousers', 'cloth');
    // tall boots paint the shin as boot leather; wraps paint criss-cross bands; same cube either way
    const sw2 = lT - 1, sh = shin + 1;
    add(`shin_${k}`, `${side}_shin`, [lx[0] + 0.5, 0, -(lT - 1) / 2], [lx[1] - 0.5, shin + 1, (lT - 1) / 2],
      ...(o.boots === 'tall' ? ['boots', 'leather', {}] : ['trousers', 'cloth',
        o.legs === 'wraps' ? { stamps: Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, wraps(sw2, sh)])) } : {}]));
    const bz0 = -lT / 2 - 1;
    add(`boot_${k}`, `${side}_shin`, [lx[0], 1, bz0], [lx[1], 4, lT / 2], 'boots', 'leather');
    add(`boot_sole_${k}`, `${side}_shin`, [lx[0], 0, bz0 - 1], [lx[1], 1, lT / 2], 'boots', 'flat', { detail: true });
    add(`boot_toe_${k}`, `${side}_shin`, [lx[0] + 0.5, 1, bz0 - 1], [lx[1] - 0.5, 3, bz0], 'boots', 'leather', { detail: true });
  }
  return 'tunic';
}
