/** Hair: caps sit half a pixel outside the skull so they are never buried. */
export const STYLES = ['bald', 'short', 'side_part', 'long', 'shoulder', 'tied'];

export function addHair(asm, body, rng, style) {
  style = style || rng.pick(STYLES);
  const g = asm.meta.head;
  if (style === 'bald') return style;
  const { hw, hd, sb, st, top } = g;
  const X = (hw + 1) / 2, Z = (hd + 1) / 2;
  const hair = (name, from, to, extra = {}) => asm.add({ name, bone: 'head', from, to, slot: 'hair', style: 'hair', ...extra });
  hair('hair_cap', [-X, st - 2, -Z], [X, st + 1, Z]);
  if (style === 'side_part') hair('hair_fringe', [-X, st - 3, -Z], [-X + 3, st - 2, -Z + 1]);
  if (style === 'long' || style === 'shoulder') hair('hair_back', [-X, top, Z - 2], [X, st - 1, Z]);
  if (style === 'shoulder') {
    hair('hair_side_r', [-X, sb + 1, -Z + 2], [-X + 1, st - 1, Z - 1]);
    hair('hair_side_l', [X - 1, sb + 1, -Z + 2], [X, st - 1, Z - 1]);
  }
  if (style === 'tied') hair('hair_tail', [-1, st - 6, Z], [1, st - 2, Z + 2], { detail: true });
  return style;
}
