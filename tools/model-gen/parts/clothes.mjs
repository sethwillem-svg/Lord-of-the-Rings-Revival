/**
 * Base clothes: belted tunic with collar and skirt, sleeves, trousers, boots. Limb joints overlap by one
 * pixel and the lower piece is half a pixel slimmer on each side, so joints seal without coplanar faces.
 */
export const STYLES = ['tunic'];

const trimRows = (w, h, neck) => {
  const rows = Array.from({ length: h }, () => '.'.repeat(w));
  rows[h - 1] = 't'.repeat(w);
  if (neck) { const n = Math.min(3, w - 2), x = (w - n) / 2 | 0; rows[0] = '.'.repeat(x) + 't'.repeat(n) + '.'.repeat(w - x - n); }
  return { grid: rows, legend: { t: ['tunic_trim', 1] } };
};

export function addClothes(asm, body, rng) {
  const { legs: L, torso: T, shoulders: S, depth: D, armThick: aT, legThick: lT, upperArm, forearm, arm, shin } = body;
  const top = L + T;
  const add = (name, bone, from, to, slot, style, extra = {}) => asm.add({ name, bone, from, to, slot, style, ...extra });

  // torso
  add('tunic', 'torso', [-S / 2, L, -D / 2], [S / 2, top, D / 2], 'tunic', 'cloth',
    { stamps: { north: trimRows(S, T, true), south: trimRows(S, T, false) } });
  add('collar', 'torso', [-2, top - 1, -D / 2 + 0.5], [2, top + 2, D / 2 - 0.5], 'tunic_trim', 'cloth');
  add('tunic_lace', 'torso', [-0.5, top - 4, -D / 2 - 1], [0.5, top - 1, -D / 2], 'tunic_trim', 'flat', { detail: true });
  const sw = S > 2 * lT ? S : 2 * lT + 1;
  add('tunic_skirt', 'body', [-sw / 2, L - 3, -(D + 1) / 2], [sw / 2, L + 1, (D + 1) / 2], 'tunic', 'cloth',
    { stamps: { north: trimRows(sw, 4, false), south: trimRows(sw, 4, false) } });
  const bx = sw / 2 + 0.5, bz = D / 2 + 1;
  add('belt', 'body', [-bx, L, -bz], [bx, L + 1, bz], 'belt', 'leather');
  add('buckle', 'body', [-1, L, -bz - 1], [1, L + 1, -bz], 'metal', 'metal', { detail: true });
  add('belt_end', 'body', [1, L - 2, -bz - 1], [2, L, -bz], 'belt', 'leather', { detail: true });
  const pz = (D + 1) / 2;
  add('pouch', 'body', [-sw / 2 + 0.5, L - 3, -pz - 1], [-sw / 2 + 2.5, L - 1, -pz], 'belt', 'leather', { detail: true });
  add('pouch_flap', 'body', [-sw / 2 + 0.5, L - 2, -pz - 2], [-sw / 2 + 2.5, L - 1, -pz - 1], 'belt', 'leather', { detail: true });

  for (const [s, k] of [[-1, 'r'], [1, 'l']]) {
    const side = s < 0 ? 'right' : 'left';
    const xi = s * S / 2, xo = s * (S / 2 + aT);             // arm inner and outer x
    const ax = [Math.min(xi, xo), Math.max(xi, xo)];
    const elbow = top - upperArm, wrist = elbow - forearm;
    add(`sleeve_${k}`, `${side}_arm`, [ax[0], elbow, -aT / 2], [ax[1], top, aT / 2], 'tunic', 'cloth');
    add(`shoulder_${k}`, `${side}_arm`, [ax[0] - 0.5, top - 2, -(aT + 1) / 2], [ax[1] + 0.5, top + 1, (aT + 1) / 2], 'tunic', 'cloth', { detail: true });
    add(`sleeve_cuff_${k}`, `${side}_arm`, [ax[0] - 0.5, elbow, -(aT + 1) / 2], [ax[1] + 0.5, elbow + 1, (aT + 1) / 2], 'tunic_trim', 'cloth', { detail: true });
    add(`forearm_${k}`, `${side}_forearm`, [ax[0] + 0.5, wrist, -(aT - 1) / 2], [ax[1] - 0.5, elbow + 1, (aT - 1) / 2], 'skin', 'skin');
    add(`hand_${k}`, `${side}_hand`, [ax[0], top - arm, -aT / 2], [ax[1], wrist + 1, aT / 2], 'skin', 'skin');
    const tx = s < 0 ? ax[1] - 1 : ax[0];
    add(`thumb_${k}`, `${side}_hand`, [tx, wrist - 1, -aT / 2 - 1], [tx + 1, wrist + 1, -aT / 2], 'skin', 'skin', { detail: true });

    const lx = [s < 0 ? -lT : 0, s < 0 ? 0 : lT];
    add(`thigh_${k}`, `${side}_leg`, [lx[0], shin, -lT / 2], [lx[1], L, lT / 2], 'trousers', 'cloth');
    add(`knee_${k}`, `${side}_leg`, [lx[0] + 0.5, shin, -lT / 2 - 1], [lx[1] - 0.5, shin + 2, -lT / 2], 'trousers', 'leather', { detail: true });
    add(`shin_${k}`, `${side}_shin`, [lx[0] + 0.5, 0, -(lT - 1) / 2], [lx[1] - 0.5, shin + 1, (lT - 1) / 2], 'trousers', 'cloth');
    const bz0 = -lT / 2 - 1;
    add(`boot_${k}`, `${side}_shin`, [lx[0], 1, bz0], [lx[1], 4, lT / 2], 'boots', 'leather');
    add(`boot_sole_${k}`, `${side}_shin`, [lx[0] - 0.5, 0, bz0 - 0.5], [lx[1] + 0.5, 1, lT / 2 + 0.5], 'boots', 'flat', { detail: true });
    add(`boot_toe_${k}`, `${side}_shin`, [lx[0] + 0.5, 1, bz0 - 1], [lx[1] - 0.5, 3, bz0], 'boots', 'leather', { detail: true });
    add(`boot_cuff_${k}`, `${side}_shin`, [lx[0] - 0.5, 4, -lT / 2 - 0.5], [lx[1] + 0.5, 5, lT / 2 + 0.5], 'boots', 'leather', { detail: true });
  }
  return 'tunic';
}
