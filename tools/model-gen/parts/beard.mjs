/** Beards. Jaw beards replace the chin cube (it would be buried) and group for the low-detail copy. */
export const STYLES = ['none', 'stubble', 'moustache', 'short', 'full', 'long'];

export function addBeard(asm, body, rng, style) {
  style = style || rng.pick(STYLES);
  const g = asm.meta.head;
  const { hw, hd, sb, fz } = g;
  if (style === 'none') return style;
  if (style === 'stubble') {
    const face = asm.get('skull').stamps.north;
    face.grid = face.grid.map((row, r) => (r >= g.mouth ? [...row].map((c) => (c === '.' ? 's' : c)).join('') : row));
    return style;
  }
  const beard = (name, from, to, extra = {}) =>
    asm.add({ name, bone: 'head', from, to, slot: 'hair', style: 'hair', ...extra });
  if (style === 'moustache') {
    const [y0, y1] = g.rowY(g.mouth);
    beard('beard_m', [-2, y0, fz - 1], [2, y1, fz], { detail: true });
    return style;
  }
  asm.remove('chin');
  const X = (hw + 1) / 2, back = fz + Math.floor(hd / 2);
  const jawTop = style === 'short' ? sb + 1 : sb + 2;
  const mouth = { grid: [style === 'short' ? '' : '.'.repeat(Math.floor((hw + 1 - 2) / 2)) + 'mm'], legend: { m: ['skin', 3] } };
  beard('beard_jaw', [-X, sb - 1, fz - 1], [X, jawTop, back], { lodGroup: 'beard', stamps: style === 'short' ? undefined : { north: mouth } });
  if (style === 'full' || style === 'long') {
    beard('beard_chin', [-(hw - 2) / 2, sb - 3, fz - 1], [(hw - 2) / 2, sb - 1, fz + 2], { lodGroup: 'beard' });
  }
  if (style === 'long') {
    const w = Math.max(2, hw - 4);
    beard('beard_tip', [-w / 2, sb - 6, fz - 1], [w / 2, sb - 3, fz + 1], { lodGroup: 'beard' });
  }
  return style;
}
