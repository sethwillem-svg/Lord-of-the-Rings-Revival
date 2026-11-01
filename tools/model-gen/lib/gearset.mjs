/** Write every weapon and armour set, gear/index.json and a preview of each faction armed and armoured. */
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { WEAPONS, buildWeapon, weaponIndex } from '../gear/weapons.mjs';
import { ARMOUR, ATTACH, buildArmour, armourIndex } from '../gear/armour.mjs';
import { buildModel } from './build.mjs';
import { dress } from './dress.mjs';
import { serializeLegacy } from './bbmodel.mjs';
import { contactSheet, Canvas } from '../vendor/bbmt/dist/index.js';

export const PREVIEW_KIT = {   // main hand, off hand per faction for the preview line-up
  rohan: ['rohan_sword', 'rohan_shield'], gondor: ['longsword', 'gondor_shield'], harad: ['scimitar', 'harad_shield'],
  dunland: ['great_axe', 'hide_shield'], dale: ['spear', 'longbow'], dunedain: ['longsword', 'longbow'],
};

export function writeGear(out) {
  const problems = [];
  mkdirSync(path.join(out, 'weapons'), { recursive: true });
  mkdirSync(path.join(out, 'armour'), { recursive: true });
  for (const id of Object.keys(WEAPONS)) {
    const { model, qa } = buildWeapon(id);
    problems.push(...qa.problems.map((p) => `${id}: ${p}`));
    writeFileSync(path.join(out, 'weapons', `${id}.bbmodel`), serializeLegacy(model));
  }
  for (const id of Object.keys(ARMOUR)) {
    const { model, qa } = buildArmour(id);
    problems.push(...qa.problems.map((p) => `armour ${id}: ${p}`));
    writeFileSync(path.join(out, 'armour', `${id}.bbmodel`), serializeLegacy(model));
  }
  const index = {
    convention: 'specs/core/model-generator.md, Gear: weapon pivot at the grip (origin), +Y to the striking end, -Z edge; armour groups are anchor bones pivoted at the largest man\'s anchors',
    weapons: weaponIndex().map((w) => ({ ...w, file: `weapons/${w.id}.bbmodel` })),
    armour: armourIndex().map((a) => ({ ...a, file: `armour/${a.id}.bbmodel` })),
  };
  writeFileSync(path.join(out, 'index.json'), JSON.stringify(index, null, 2) + '\n');
  const tiles = Object.entries(PREVIEW_KIT).map(([f, [main, off]]) => contactSheet(dress(buildModel('man', 13, { palette: f }), [
    { model: buildArmour(f).model, attach: ATTACH },
    { model: buildWeapon(main).model, hand: WEAPONS[main].hand },
    { model: buildWeapon(off).model, hand: WEAPONS[off].hand },
  ]), { views: ['back', 'iso-back'], width: 150, height: 260 }));
  const sheet = new Canvas(tiles.reduce((s, t) => s + t.width, 0), tiles[0].height, [40, 36, 32, 255]);
  let x = 0;
  for (const t of tiles) { sheet.blit(t, x, 0); x += t.width; }
  writeFileSync(path.join(out, 'preview.png'), sheet.toPng());
  return { index, problems };
}
