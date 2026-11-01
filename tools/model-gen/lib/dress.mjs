/**
 * dress(man, gear): a new toolkit Model with the gear's cubes merged onto the man's bones, for previews.
 * Armour groups move from the largest man's anchor pivots to this man's (boot armour onto the shin, see ATTACH);
 * weapons move from the grip origin to the man's item bone. The viewer does the same with gear.js.
 */
import { Model, Atlas, Canvas } from '../vendor/bbmt/dist/index.js';
import { largestBody } from './body.mjs';
import { MAN } from '../races/man.mjs';

export function dress(man, items) {
  const { model: base, body } = man;
  const out = new Model({ name: base.name, resolution: [256, 256] });
  for (const b of base.rig.all()) out.bone(b.name, [...b.pivot], b.parent || null);
  // one combined texture: the man's sheet top-left, each gear sheet below/right of it
  const sheet = new Canvas(256, 256, [0, 0, 0, 0]);
  const place = (tex, ox, oy) => { sheet.blit(Canvas.fromPng(tex.data), ox, oy); };
  place(base.textures[0], 0, 0);
  const copy = (c, bone, shift, ox, oy) => out.cube({ name: c.name, bone, from: c.from.map((v, a) => v + shift[a]), to: c.to.map((v, a) => v + shift[a]),
    faces: Object.fromEntries(Object.entries(c.faces).map(([f, r]) => [f, [r[0] + ox, r[1] + oy, r[2] + ox, r[3] + oy]])) });
  for (const c of base.cubes) copy(c, c.bone, [0, 0, 0], 0, 0);
  const ref = largestBody(MAN).pivots, P = body.pivots;
  const slots = [[128, 0], [0, 128], [128, 128], [192, 0], [192, 64]];
  items.forEach(({ model, attach = {}, hand }, i) => {
    const [ox, oy] = slots[i];
    place(model.textures[0], ox, oy);
    for (const c of model.cubes) {
      let bone, shift;
      if (hand) { bone = hand; shift = P[hand]; }
      else { bone = attach[c.bone] || c.bone; shift = P[bone].map((v, a) => v - ref[bone][a]); }
      copy({ ...c, name: `${model.name}:${c.name}` }, bone, shift, ox, oy);
    }
  });
  out.addTexture({ ...new Atlas(1, 1).toTexture('dressed.png'), width: 256, height: 256, data: sheet.toPng() });
  return out;
}
