/* Shields on the NPC rig, built and placed the way the game draws them (client.render.PulseNpcRenderer item(), with
 * client.render.WeaponGrip for the placement), so the viewer and the tests see the same plate.
 *   MODELS                    the two kinds of Middle-earth shield, as measured (see below): { quads, display, custom }
 *   displayMatrix(d, left)    an item model's third-person display transform (Minecraft's ItemTransform), px
 *   armQuads(model, left)     the shield's faces where Minecraft's held-item layer draws them in the arm (part space, px, y down)
 *   plateOf(quads)            the plate: { out (face normal, away from the handle), up, centre, back, front, faces }
 *   place(quads, left)        where item() moves the drawn shield (part space, px; a 4x4, row-major), null to leave it as drawn
 *   placed(quads, m)          quads moved by such a matrix (m null: unchanged)
 *   toRig(side, p) / dirToRig(v)   a part-space point or direction of an arm in rig space (gecko/held.js partToRig)
 * Part space is the arm's: px, y down, -z forward at rest, origin at the shoulder pivot; the right arm's box is x -3..1, the
 * left's -1..3, both y -2..10 and z -2..2, the fist at (∓1, 10, -2).
 *
 * The shields (numbers taken from Middle-earth 1.0.2's item models, read only to measure them; nothing of theirs is copied):
 *  - element: 103 of Middle-earth's 106 shield items are block-element models. They share one layout: a plate one px thick
 *    in the model's z (z 8..9), 10-22 px wide and 12-35 px tall, facing north (-z), with the handle behind it on the south
 *    side (a 2 x 6 x 6 px block at x 7..9, y 8..14, z 9..15 on the common templates). 93 of them display in third person
 *    with rotation [0, -90, 0] (91 with translation [4, -5, 4.75]) for both hands (Minecraft mirrors the left hand); the
 *    other 10 turn the plate other ways ([180, -90, 90] and the like). The plate here: x 1..15, y 4..18 (the 14 x 14 px
 *    round and erebor templates), with that handle and the common display.
 *  - entity: kite_shield, heater_shield and round_shield are builtin/entity items drawn by their own renderer, with
 *    third-person rotation [0, 90, 0] and translation [10, 6, -4] (right) and [10, 6, 12] (left), like vanilla's shield.
 *    Their geometry is an entity model, so it is taken as vanilla's shield model: a 12 x 22 x 1 px plate at (-6, -11, -2)
 *    and a 2 x 6 x 6 px handle at (-1, -3, -1), drawn flipped (scale 1, -1, -1) as Minecraft's item renderer draws it.
 * Both displays turn the plate edge-on to the front of the arm: its face looks out sideways from the arm. */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const H = isNode ? require('./held.js') : root.GeckoHeld;

  // ---- 4x4 matrices, row-major ----
  const I4 = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
  function mul(a, b) {
    const o = new Array(16).fill(0);
    for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) for (let k = 0; k < 4; k++) o[r * 4 + c] += a[r * 4 + k] * b[k * 4 + c];
    return o;
  }
  const chain = (...ms) => ms.reduce(mul, I4());
  const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
  const Sc = (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1];
  function R(axis, deg) {
    const a = (deg * Math.PI) / 180, c = Math.cos(a), s = Math.sin(a);
    if (axis === 'x') return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1];
    if (axis === 'y') return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1];
    return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
  }
  const apply = (m, p) => [0, 1, 2].map((r) => m[r * 4] * p[0] + m[r * 4 + 1] * p[1] + m[r * 4 + 2] * p[2] + m[r * 4 + 3]);
  const applyDir = (m, v) => [0, 1, 2].map((r) => m[r * 4] * v[0] + m[r * 4 + 1] * v[1] + m[r * 4 + 2] * v[2]);

  // ---- vectors ----
  const sub = (a, b) => a.map((x, i) => x - b[i]);
  const add = (a, b) => a.map((x, i) => x + b[i]);
  const scale = (a, k) => a.map((x) => x * k);
  const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const norm = (a) => { const l = Math.hypot(...a); return l > 1e-12 ? scale(a, 1 / l) : [0, 0, 0]; };

  /** A box's six faces (px), each wound counter-clockwise seen from outside. */
  function box(a, b) {
    const c = (i, j, k) => [i ? b[0] : a[0], j ? b[1] : a[1], k ? b[2] : a[2]];
    return [
      [c(0, 0, 0), c(0, 1, 0), c(1, 1, 0), c(1, 0, 0)], [c(0, 0, 1), c(1, 0, 1), c(1, 1, 1), c(0, 1, 1)],   // -z, +z
      [c(0, 0, 0), c(0, 0, 1), c(0, 1, 1), c(0, 1, 0)], [c(1, 0, 0), c(1, 1, 0), c(1, 1, 1), c(1, 0, 1)],   // -x, +x
      [c(0, 0, 0), c(1, 0, 0), c(1, 0, 1), c(0, 0, 1)], [c(0, 1, 0), c(0, 1, 1), c(1, 1, 1), c(1, 1, 0)],   // -y, +y
    ];
  }

  const MODELS = {
    element: {
      quads: [...box([1, 4, 8], [15, 18, 9]), ...box([7, 8, 9], [9, 14, 15])],
      display: { right: { rotation: [0, -90, 0], translation: [4, -5, 4.75] }, left: { rotation: [0, -90, 0], translation: [4, -5, 4.75] } },
      custom: false,
    },
    entity: {
      quads: [...box([-6, -11, -2], [6, 11, -1]), ...box([-1, -3, -1], [1, 3, 5])],
      display: { right: { rotation: [0, 90, 0], translation: [10, 6, -4] }, left: { rotation: [0, 90, 0], translation: [10, 6, 12] } },
      custom: true,
    },
  };

  /** Minecraft's ItemTransform.apply: translate (x mirrored in the left hand), rotate XYZ (y and z mirrored), scale. px. */
  function displayMatrix(d, left) {
    const t = d.translation || [0, 0, 0], r = d.rotation || [0, 0, 0], s = d.scale || [1, 1, 1], m = left ? -1 : 1;
    return chain(T(m * t[0], t[1], t[2]), R('x', r[0]), R('y', m * r[1]), R('z', m * r[2]), Sc(...s));
  }

  /** The held-item layer's seat in the arm (PulseNpcRenderer item(): Rx -90, Ry 180, translate ±1, 2, -10 px). */
  const handSeat = (left) => chain(R('x', -90), R('y', 180), T(left ? -1 : 1, 2, -10));

  /** Model space to arm part space, as item() then Minecraft's item renderer draw it: the seat, the display transform, the
   *  block's centring (-8 px), and for a custom renderer the vanilla shield's flip. */
  function modelToArm(model, left) {
    return chain(handSeat(left), displayMatrix(model.display[left ? 'left' : 'right'], left), T(-8, -8, -8), model.custom ? Sc(1, -1, -1) : I4());
  }

  function armQuads(model, left) {
    const m = modelToArm(model, left);
    return model.quads.map((q) => q.map((p) => apply(m, p)));
  }

  const placed = (quads, m) => (m ? quads.map((q) => q.map((p) => apply(m, p))) : quads);

  /** A face's unit normal and area (Newell's method). */
  function faceOf(q) {
    let n = [0, 0, 0];
    for (let i = 0; i < q.length; i++) {
      const a = q[i], b = q[(i + 1) % q.length];
      n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]);
    }
    const l = Math.hypot(...n);
    return { n: l > 1e-12 ? scale(n, 1 / l) : [0, 0, 0], area: l / 2 };
  }

  /** How far from the plate's end a face still counts as the plate (px): the plate's two faces, one px apart. */
  const PLATE_DEPTH = 1.5;
  /** Extents (px) this close count as the same when picking the plate's axis. */
  const THIN_TIE = 0.25;

  /**
   * The plate of a shield's faces. Its axis: of the directions its faces look along, the one the whole item is thinnest
   * along (the plate and the handle behind it, a few px deep, against 10 px and more across), a tie going to the one more
   * face area looks along. Face area alone cannot tell: shields built of one-px strips (Middle-earth's Goblin Town,
   * Gundabad heavy and Moria shields) have as much side area across the plate as face area out of it. Its outside: the end
   * of that axis where the faces square to it gather most area within PLATE_DEPTH (the plate), away from the handle behind
   * it; a flat item with
   * nothing behind its plate keeps the axis' own sign. Its up: in the plate, the item's up as displayed (arm space -z, or -y
   * when the plate is square to z), unless the plate is a quarter longer the other way, then that long axis (pointing up
   * or forward). centre: the middle of the plate's faces; back and front: the plate's two faces along out.
   */
  function plateOf(quads) {
    const faces = quads.map((q) => ({ q, ...faceOf(q) })).filter((f) => f.area > 1e-9);
    if (!faces.length) return null;
    // the axis: of the directions the faces look along, the one the shield is thinnest along (a plate and the handle behind
    // it: a few px, against 10 px and more across); a tie (within THIN_TIE) goes to the direction more face area looks along
    const dirs = [];
    for (const f of faces) {
      const d = dirs.find((x) => Math.abs(dot(x.n, f.n)) > 0.999);
      if (d) d.area += f.area;
      else dirs.push({ n: f.n, area: f.area });
    }
    const pts0 = faces.flatMap((f) => f.q);
    for (const d of dirs) { const s = pts0.map((p) => dot(p, d.n)); d.extent = Math.max(...s) - Math.min(...s); }
    const v = dirs.reduce((b, d) => (d.extent < b.extent - THIN_TIE || (Math.abs(d.extent - b.extent) <= THIN_TIE && d.area > b.area) ? d : b)).n;
    const along = (f) => f.q.reduce((s, p) => s + dot(p, v), 0) / f.q.length;
    const all = pts0.map((p) => dot(p, v));
    const lo = Math.min(...all), hi = Math.max(...all);
    const square = faces.filter((f) => Math.abs(dot(f.n, v)) > 0.9);
    const areaNear = (end) => square.filter((f) => Math.abs(along(f) - end) <= PLATE_DEPTH).reduce((s, f) => s + f.area, 0);
    const aLo = areaNear(lo), aHi = areaNear(hi), atLo = aLo > aHi * 1.001;
    const out = atLo ? scale(v, -1) : v, end = atLo ? lo : hi;
    const plate = square.filter((f) => Math.abs(along(f) - end) <= PLATE_DEPTH);
    const pts = plate.flatMap((f) => f.q);
    const inPlane = (d) => norm(sub(d, scale(out, dot(d, out))));
    let e1 = inPlane([0, 0, -1]);
    if (Math.hypot(...sub([0, 0, -1], scale(out, dot([0, 0, -1], out)))) < 0.5) e1 = inPlane([0, -1, 0]);
    const e2 = cross(out, e1);
    const range = (d) => { const s = pts.map((p) => dot(p, d)); return [Math.min(...s), Math.max(...s)]; };
    const r1 = range(e1), r2 = range(e2), ro = range(out);
    let up = e1;
    if (r2[1] - r2[0] > 1.25 * (r1[1] - r1[0])) up = dot(e2, [0, -1, -1]) < 0 ? scale(e2, -1) : e2;
    const mid = (r) => (r[0] + r[1]) / 2;
    const centre = add(add(scale(e1, mid(r1)), scale(e2, mid(r2))), scale(out, mid(ro)));
    return { out, up, centre, back: ro[0], front: ro[1], faces: plate.map((f) => f.q) };
  }

  /** The forearm in part space (px): its back (the outside of the arm's box, x 3 on the left arm, -3 on the right), the
   *  plate's middle this far out from it, and the fist's height down the arm. */
  const ARM_BACK = 3, GAP = 1, FIST_Y = 10;

  /**
   * Where item() moves a shield (client.render.WeaponGrip.strap): strapped to the forearm, whatever the item's display
   * transform did with it. Its face looks along the back of the forearm (the outside of the arm, +x on the left arm, -x
   * on the right), its up along the front of the arm (-z: up when the forearm is held across the chest with its back to
   * the front), the plate's middle GAP px out from the forearm's back, level with the fist and centred on the arm. A 4x4
   * (part space, px, row-major), null for an item with nothing drawn.
   */
  function place(quads, left) {
    const p = plateOf(quads);
    if (!p) return null;
    const out = [left ? 1 : -1, 0, 0], up = [0, 0, -1], at = [(left ? 1 : -1) * (ARM_BACK + GAP), FIST_Y, 0];
    const from = [p.out, p.up, cross(p.out, p.up)], to = [out, up, cross(out, up)];
    const m = I4();
    for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) m[r * 4 + c] = from.reduce((s, f, k) => s + to[k][r] * f[c], 0);
    const moved = applyDir(m, p.centre);
    for (let r = 0; r < 3; r++) m[r * 4 + 3] = at[r] - moved[r];
    return m;
  }

  /** Part space to rig space for an arm (held.js partToRig), and a direction (x and y flip). */
  const toRig = (side, p) => H.partToRig(side === 'left' ? 'left_arm' : 'right_arm', p);
  const dirToRig = (v) => [-v[0], -v[1], v[2]];
  const fromRig = (side, r) => { const o = toRig(side, [0, 0, 0]); return [o[0] - r[0], o[1] - r[1], r[2] - o[2]]; };

  /** The viewer's seat for a shield held on `side` (quads in rig space, as the viewer keeps them): place() worked in the
   *  arm's part space and carried into rig space, as { m } for held.js seatPoint; null with nothing drawn. */
  function seat(quads, side) {
    const m = place(quads.map((q) => q.map((p) => fromRig(side, p))), side === 'left');
    if (!m) return null;
    const o = toRig(side, [0, 0, 0]), F = [-1, 0, 0, o[0], 0, -1, 0, o[1], 0, 0, 1, o[2], 0, 0, 0, 1];   // part -> rig
    const Finv = [-1, 0, 0, o[0], 0, -1, 0, o[1], 0, 0, 1, -o[2], 0, 0, 0, 1];   // rig -> part
    return { m: chain(F, m, Finv) };
  }

  const api = { MODELS, displayMatrix, modelToArm, armQuads, plateOf, place, placed, seat, toRig, fromRig, dirToRig, box, faceOf, ARM_BACK, GAP, FIST_Y,
    mat: { I4, mul, chain, T, Sc, R, apply, applyDir } };
  if (isNode) module.exports = api;
  else root.GeckoShield = api;
})(typeof window !== 'undefined' ? window : globalThis);
