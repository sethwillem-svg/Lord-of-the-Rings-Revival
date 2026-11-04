'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { partToRig, FIST, itemScale, scaleAbout, bendPoint, quadsOf, drawnItem, seatItem, seatPoint } = require('../gecko/held.js');
const { RIG } = require('../gecko/rig.js');

const near = (a, b, eps = 1e-6) => a.every((x, i) => Math.abs(x - b[i]) < eps);
const grips = JSON.parse(fs.readFileSync(path.join(__dirname, '../../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json'), 'utf8'));

test('the fist in arm space is the weapon bone pivot in rig space', () => {
  assert.ok(near(partToRig('right_arm', [-1, 10, -2]), FIST.right));
  assert.ok(near(partToRig('left_arm', [1, 10, -2]), FIST.left));
  assert.deepEqual(FIST.right, RIG.find((b) => b.name === 'weapon_r').pivot);
});

test('class lengths (blocks, pommel to tip as drawn) come from weapon_grips.json', () => {
  assert.deepEqual(Object.fromEntries(Object.entries(grips).map(([k, v]) => [k, v.length])),
    { dagger: 0.85, sword: 1.5, longsword: 1.9, axe: 1.65, spear: 2.8, bow: 1.2, longbow: 1.8, crossbow: 1.0 });
});

test('an item is scaled to its class length; unknown classes keep their size', () => {
  assert.ok(Math.abs(itemScale('dagger', grips, 24) - 13.6 / 24) < 1e-9);
  assert.ok(Math.abs(itemScale('spear', grips, 56) - 44.8 / 56) < 1e-9);
  assert.equal(itemScale('shield', grips, 17), 1);
  assert.equal(itemScale(null, grips, 17), 1);
});

test('scaling about the fist keeps the handle in the hand', () => {
  assert.ok(near(scaleAbout(FIST.right, 0.4, FIST.right), FIST.right));
  assert.ok(near(scaleAbout(FIST.right, 0.5, [6, 12, -12]), [6, 12, -7]));
});

// ---- drawn weapons: Minecraft's generated item models ----
/* A generated (flat) item model as Minecraft bakes it: two face quads over the whole texture square, front and back, plus
 * thin edge quads (one texel deep) along the outline of the opaque pixels. This one: a 16 px square in the plane x = 6
 * (rig space, the right fist's plane), 1 px thick, with a straight blade 2 px wide drawn on its diagonal, filling 60% of
 * it, the pommel 1.5 px behind and below the fist. */
const SIDE = 16, WIDTH = 2, BLADE = 0.6 * SIDE * Math.SQRT2;
const POMMEL = [6, FIST.right[1] - 1.5, FIST.right[2] + 1.5];
const ALONG = [0, Math.SQRT1_2, -Math.SQRT1_2], ACROSS = [0, Math.SQRT1_2, Math.SQRT1_2];
const at = (p, ...terms) => p.map((x, i) => terms.reduce((s, [k, d]) => s + k * d[i], x));
function generatedItem() {
  const x0 = 5.5, x1 = 6.5, y0 = POMMEL[1] - 3, z0 = POMMEL[2] + 3;
  const face = (x) => [[x, y0, z0], [x, y0 + SIDE, z0], [x, y0 + SIDE, z0 - SIDE], [x, y0, z0 - SIDE]];
  const quads = [face(x0), face(x1).reverse()];
  const outline = [at(POMMEL, [-WIDTH / 2, ACROSS]), at(POMMEL, [BLADE, ALONG], [-WIDTH / 2, ACROSS]),
    at(POMMEL, [BLADE, ALONG], [WIDTH / 2, ACROSS]), at(POMMEL, [WIDTH / 2, ACROSS])];
  for (let e = 0; e < 4; e++) {   // each outline edge as one-texel edge quads
    const a = outline[e], b = outline[(e + 1) % 4], n = Math.ceil(Math.hypot(...b.map((x, i) => x - a[i])));
    for (let k = 0; k < n; k++) {
      const p = a.map((x, i) => x + ((b[i] - x) * k) / n), q = a.map((x, i) => x + ((b[i] - x) * (k + 1)) / n);
      quads.push([[x0, p[1], p[2]], [x0, q[1], q[2]], [x1, q[1], q[2]], [x1, p[1], p[2]]]);
    }
  }
  return quads;
}
const dist = (a, b) => Math.hypot(...a.map((x, i) => x - b[i]));
const exactDiameter = (pts) => Math.max(...pts.flatMap((a) => pts.map((b) => dist(a, b))));

test('a generated item is measured by its drawn weapon, not by its texture square', () => {
  const d = drawnItem(generatedItem());
  const drawn = Math.hypot(BLADE, WIDTH, 1);   // pommel corner to the far tip corner, across the 1 px depth
  assert.ok(Math.abs(d.length - drawn) < 1e-6, `measured ${d.length}, drawn ${drawn}, square ${SIDE} (diagonal ${SIDE * Math.SQRT2})`);
  const dot = (p, u) => p.reduce((s, x, i) => s + (x - POMMEL[i]) * u[i], 0);
  const off = d.pts.filter((p) => Math.abs(dot(p, ACROSS)) > WIDTH / 2 + 1e-9 || dot(p, ALONG) < -1e-9 || dot(p, ALONG) > BLADE + 1e-9);
  assert.deepEqual(off, [], 'only the edge quads (on the blade outline) are kept, not the faces over the square');
});

test('a model without big faces (a real 3D model) keeps all its quads', () => {
  const box = [[0, 0, 0], [2, 3, 6]], c = (i, j, k) => [box[i][0], box[j][1], box[k][2]];
  const quads = [[c(0, 0, 0), c(1, 0, 0), c(1, 1, 0), c(0, 1, 0)], [c(0, 0, 1), c(1, 0, 1), c(1, 1, 1), c(0, 1, 1)],
    [c(0, 0, 0), c(0, 1, 0), c(0, 1, 1), c(0, 0, 1)], [c(1, 0, 0), c(1, 1, 0), c(1, 1, 1), c(1, 0, 1)],
    [c(0, 0, 0), c(1, 0, 0), c(1, 0, 1), c(0, 0, 1)], [c(0, 1, 0), c(1, 1, 0), c(1, 1, 1), c(0, 1, 1)]];
  assert.ok(Math.abs(drawnItem(quads).length - 7) < 1e-9);   // the box's diagonal: 2, 3, 6
});

test('a seated item has its handle on the fist and its drawn length at the class length', () => {
  for (const cls of ['dagger', 'sword', 'longsword', 'axe', 'spear']) {
    const quads = generatedItem(), seat = seatItem(cls, grips, quads, FIST.right);
    const kept = drawnItem(quads).pts.map((p) => seatPoint(seat, p));
    assert.ok(Math.min(...kept.map((p) => dist(p, FIST.right))) < 1e-9, `${cls}: the handle is off the fist`);
    assert.ok(near(seatPoint(seat, seat.grip), FIST.right, 1e-9), `${cls}: grip ${seat.grip} lands at ${seatPoint(seat, seat.grip)}`);
    assert.ok(Math.abs(exactDiameter(kept) / 16 - grips[cls].length) < 1e-6, `${cls}: ${exactDiameter(kept) / 16} blocks`);
  }
  assert.equal(seatItem('shield', grips, generatedItem(), FIST.right), null);   // no class length: drawn as Minecraft seats it
  assert.deepEqual(seatPoint(null, [1, 2, 3]), [1, 2, 3]);
});

test('the grip is the drawn point nearest the fist: on the handle, not on the texture square', () => {
  const seat = seatItem('sword', grips, generatedItem(), FIST.right);
  const along = seat.grip.reduce((s, x, i) => s + (x - POMMEL[i]) * ALONG[i], 0);
  assert.ok(along >= -1e-9 && along <= 3, `grip ${seat.grip}: ${along.toFixed(2)} px up the blade from the pommel`);
  assert.ok(dist(seat.grip, FIST.right) <= Math.hypot(WIDTH / 2, 0.5, 0.5) + 1e-9, `grip ${seat.grip} is ${dist(seat.grip, FIST.right)} px from the fist`);
});

test('quads come back from an exported or three.js mesh (two triangles each), in drawing order', () => {
  // the export: each quad's four vertices in a row, fanned (v, v+1, v+2), (v, v+2, v+3)
  const pos = [0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 5, 0, 0, 5, 2, 0, 5, 2, 2, 5, 0, 2];
  assert.deepEqual(quadsOf(pos, [0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7]),
    [[[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]], [[5, 0, 0], [5, 2, 0], [5, 2, 2], [5, 0, 2]]]);
  // three.js BoxGeometry: (a, b, d), (b, c, d) per cell
  assert.deepEqual(quadsOf([0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0], [0, 1, 3, 1, 2, 3]), [[[0, 0, 0], [0, 1, 0], [1, 1, 0], [1, 0, 0]]]);
});

test('a bend moves the forearm with the elbow and leaves the upper arm', () => {
  const elbow = { pivot: [6, 18, 0], q: [Math.SQRT1_2, 0, 0, Math.SQRT1_2], pos: [0, 0, 0] };   // 90° about x
  assert.ok(near(bendPoint(elbow, [6, 21, 0], 2), [6, 21, 0]));        // above the band
  assert.ok(near(bendPoint(elbow, [6, 12, 0], 2), [6, 18, -6], 1e-9)); // hand: fully turned (Rx 90 takes -y to -z)
  const mid = bendPoint(elbow, [6, 18, 0], 2);                          // on the joint: unchanged point (it is the pivot)
  assert.ok(near(mid, [6, 18, 0]));
});
