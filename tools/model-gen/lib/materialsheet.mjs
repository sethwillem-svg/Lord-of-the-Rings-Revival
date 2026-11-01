/**
 * The armour texturing tool's sheet: every material recipe on a large plate and a thin lame (front and turned), four
 * per row in file-name order, and, for the built-in recipes, the Gondor armour on a man in the last row. Edit materials/*.json and rebuild (or --watch).
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { contactSheet, Canvas } from '../vendor/bbmt/dist/index.js';
import { GearAssembly, buildGear, gearPalette } from './gear.mjs';
import { MATERIALS, validateMaterial } from './surface.mjs';
import { buildModel } from './build.mjs';
import { buildArmour, ATTACH } from '../gear/armour.mjs';
import { buildWeapon } from '../gear/weapons.mjs';
import { dress } from './dress.mjs';

export function checkMaterials(materials) {
  return Object.entries(materials).flatMap(([id, m]) => validateMaterial(m).map((p) => `${id}: ${p}`));
}

export function writeMaterialSheet(out, materials = MATERIALS, faction = 'gondor') {
  const problems = checkMaterials(materials);
  if (problems.length) return { problems, ids: [] };
  const ids = Object.keys(materials);
  const tiles = ids.map((id) => {
    const g = new GearAssembly([['swatch', null, [0, 0, 0]]]);
    const slot = 'metal';
    g.add({ name: `${id}_plate`, bone: 'swatch', from: [-4, 4, -2], to: [4, 16, 2], slot, material: id });
    g.add({ name: `${id}_lame`, bone: 'swatch', from: [-4, 0, -2], to: [4, 3, 2], slot, material: id });
    const { model } = buildGear(`swatch_${id}`, g, gearPalette(faction), 64, { materials });
    return contactSheet(model, { views: ['back', 'iso-back'], width: 200, height: 240 });
  });
  const rows = [];
  for (let i = 0; i < tiles.length; i += 4) rows.push(tiles.slice(i, i + 4));
  if (materials === MATERIALS) {   // the built-in recipes: also show them on the armour they paint
    const man = buildModel('man', 13, { palette: faction });
    rows.push([contactSheet(dress(man, [{ model: buildArmour(faction, man).model, attach: ATTACH, fitted: true },
      { model: buildWeapon('longsword').model, hand: 'right_item' }]), { views: ['back', 'iso-back', 'iso', 'left'], width: 300, height: 440 })]);
  }
  const W = Math.max(...rows.map((r) => r.reduce((s, t) => s + t.width, 0)));
  const H = rows.reduce((s, r) => s + Math.max(...r.map((t) => t.height)), 0);
  const sheet = new Canvas(W, H, [32, 30, 28, 255]);
  let y = 0;
  for (const r of rows) {
    let x = 0;
    for (const t of r) { sheet.blit(t, x, y); x += t.width; }
    y += Math.max(...r.map((t) => t.height));
  }
  mkdirSync(path.dirname(out), { recursive: true });
  writeFileSync(out, sheet.toPng());
  return { problems: [], ids };
}
