/* Bows on the NPC rig, measured and held the way the game holds them (client.render.WeaponGrip.bow and
 * client.render.BowPull), so the viewer, the build and the tests see the same bow and the same string.
 *   measure(quads)               a bow's frame from its drawn faces (any space, px): { up, out, normal, grip, brace, length,
 *                                tips }, null for nothing drawn (see below)
 *   place(quads, left, cls, grips)  where item() moves a bow (part space, px; a 4x4, row-major), null to leave it as drawn
 *   seat(quads, side, cls, grips)   the same for quads in rig space, as the viewer keeps them: { m } for held.js seatPoint
 *   placedBow(quads, m)          the measured frame carried through a placement: { up, out, grip, brace, length }
 *   nock(bow, p)                 the string's middle pulled back by draw progress p (0 at brace, 1 at full draw)
 *   stageOf(p) / STAGES          the item model shown at draw progress p: 0 slack, 1-3 pulling_0..pulling_2
 *   pullAt(draw, clip, t)        draw progress in a bow clip (bow_aim, bow_release) at clip time t, from the race's draw
 *                                timing ({ drawStart, fullDraw, release }, the build's draw.json)
 * Part space is the arm's: px, y down, -z forward at rest, origin at the shoulder pivot; the fist at (∓1, 10, -2).
 *
 * The bows (Middle-earth 1.0.2's bow and longbow item models, read only to measure them; nothing of theirs is copied): flat
 * generated item models, the bow drawn on the texture's diagonal, its wood bowed to one side of the string, which runs
 * straight from tip to tip; the pulling_0..2 models draw the string pulled back into a V with an arrow across the grip. Their
 * third-person display turns them for Minecraft's own aiming arms, not for the clips' arms, so it is ignored: the bow is
 * measured from its own faces and held upright across the aim.
 *
 * measure: the drawn points (held.js drawnItem: a generated model's two faces over the texture square dropped); the item's
 * plane: the largest face's normal; the tips: the farthest pair; the limb axis `up` between them; across it in the plane, the
 * wood's middle stands off the straight string to one side: `out` points from the string to the wood (the way the arrow
 * flies), `grip` is the wood's middle (half way through the wood, across the middle tenth of the bow's length), `brace` how
 * far the string's middle stands behind the grip. `up` takes the tip nearer the item's displayed up (-z in part space), so
 * the frame does not depend on the order of the faces.
 *
 * place: the grip on the bow hand, its middle just above the fist (GRIP: the arrow passes over the hand, the wood runs down
 * through it), the bow's plane on the inner side of the forearm; `up` along the front of the forearm (part -z: up when
 * the arm is held out toward the target), `out` down the arm toward the fist (the way the arm points, so the string lies
 * toward the shoulder and the archer); its length the class length (held.js itemScale, as weapons are sized). */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const H = isNode ? require('./held.js') : root.GeckoHeld;

  const sub = (a, b) => a.map((x, i) => x - b[i]);
  const add = (a, b) => a.map((x, i) => x + b[i]);
  const scale = (a, k) => a.map((x) => x * k);
  const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const norm = (a) => { const l = Math.hypot(...a); return l > 1e-12 ? scale(a, 1 / l) : [0, 0, 0]; };
  const dist = (a, b) => Math.hypot(...sub(a, b));
  const apply = (m, p) => [0, 1, 2].map((r) => m[r * 4] * p[0] + m[r * 4 + 1] * p[1] + m[r * 4 + 2] * p[2] + m[r * 4 + 3]);
  const applyDir = (m, v) => [0, 1, 2].map((r) => m[r * 4] * v[0] + m[r * 4 + 1] * v[1] + m[r * 4 + 2] * v[2]);

  /** A face's unit normal (Newell's method) and area. */
  function faceOf(q) {
    let n = [0, 0, 0];
    for (let i = 0; i < q.length; i++) {
      const a = q[i], b = q[(i + 1) % q.length];
      n = add(n, [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]);
    }
    const l = Math.hypot(...n);
    return { n: l > 1e-12 ? scale(n, 1 / l) : [0, 0, 0], area: l / 2 };
  }

  /** The share of the bow's length either side of its middle where the grip is looked for. */
  const MIDDLE = 0.05;
  /** The item's displayed up in the arm's part space (-z: the front of the arm). */
  const DISPLAY_UP = [0, 0, -1];

  function measure(quads) {
    if (!quads.length) return null;
    const faces = quads.map(faceOf);
    const big = faces.reduce((b, f) => (f.area > b.area ? f : b));
    if (big.area < 1e-9) return null;
    const drawn = H.drawnItem(quads).pts;
    if (drawn.length < 2) return null;
    // the points flattened onto the item's middle plane: the edge quads' two ends, a texel apart, count as one
    const n0 = big.n, c0 = drawn.reduce((s, p) => add(s, scale(p, 1 / drawn.length)), [0, 0, 0]);
    const pts = drawn.map((p) => sub(p, scale(n0, dot(sub(p, c0), n0))));
    // the tips: the farthest pair (every pair: a bow is a few hundred points)
    let a = pts[0], b = pts[1], best = -1;
    for (let i = 0; i < pts.length; i++) for (let j = i + 1; j < pts.length; j++) {
      const d = dist(pts[i], pts[j]);
      if (d > best) { best = d; a = pts[i]; b = pts[j]; }
    }
    if (best < 1e-3) return null;
    let up = norm(sub(b, a));
    if (dot(up, DISPLAY_UP) < 0) { up = scale(up, -1); [a, b] = [b, a]; }
    const normal = norm(sub(big.n, scale(up, dot(big.n, up))));
    const across = norm(cross(normal, up));
    const mid = scale(add(a, b), 0.5);
    // the middle of the bow: the points within MIDDLE of its length either side of the tips' midpoint
    const middle = pts.filter((p) => Math.abs(dot(sub(p, mid), up)) <= MIDDLE * best);
    const off = middle.map((p) => dot(sub(p, mid), across));
    const far = off.reduce((m, s) => (Math.abs(s) > Math.abs(m) ? s : m), 0);
    const sign = far < 0 ? -1 : 1, out = scale(across, sign);
    // the wood at the middle: the points standing off the string by over half the farthest one
    // the wood and the string at the middle: the offsets either side of the widest gap between them (the string is a thin
    // line, the wood a band of its two edges, the gap the brace between them); a middle with no gap is all wood, its
    // string the tips' line
    const s = off.map((x) => x * sign).sort((x, y) => x - y);
    let cut = 0, gap = 1e-6;
    for (let i = 1; i < s.length; i++) if (s[i] - s[i - 1] > gap) { gap = s[i] - s[i - 1]; cut = i; }
    const wood = s.slice(cut), string = s.slice(0, cut);
    const depth = (wood[0] + wood[wood.length - 1]) / 2;
    const behind = string.length ? (string[0] + string[string.length - 1]) / 2 : 0;
    const grip = add(mid, scale(out, depth));
    return { up, out, normal: norm(cross(up, out)), grip, brace: depth - behind, length: best, tips: [a, b] };
  }

  /** The grip's place in the bow hand (part space, px): just above the fist's top (z -2.5: the front of the forearm, up
   *  when the arm is held out), half way along the hand (y 8.5), on the forearm's inner side (x +1.25 on the right arm, its
   *  inner face at x 1; -1.25 on the left, whose inner face is at -1). */
  const GRIP = { right: [1.25, 8.5, -2.5], left: [-1.25, 8.5, -2.5] };
  /** The bow's frame in the hand (part space): limbs along the front of the forearm, out down the arm toward the fist. */
  const UP = [0, 0, -1], OUT = [0, 1, 0];

  function place(quads, left, cls, grips) {
    const b = measure(quads);
    if (!b) return null;
    const s = H.itemScale(cls, grips, b.length);
    const from = [b.up, b.out, cross(b.up, b.out)], to = [UP, OUT, cross(UP, OUT)];
    const m = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) m[r * 4 + c] = s * from.reduce((acc, f, k) => acc + to[k][r] * f[c], 0);
    const moved = applyDir(m, b.grip), at = GRIP[left ? 'left' : 'right'];
    for (let r = 0; r < 3; r++) m[r * 4 + 3] = at[r] - moved[r];
    return m;
  }

  /** A bow's measured frame as placed by m (a 4x4 with a uniform scale; null: as drawn). */
  function placedBow(quads, m) {
    const b = measure(quads);
    if (!b || !m) return b;
    const k = Math.hypot(m[0], m[4], m[8]);
    return { up: norm(applyDir(m, b.up)), out: norm(applyDir(m, b.out)), grip: apply(m, b.grip), brace: b.brace * k, length: b.length * k };
  }

  /** Part space <-> rig space for an arm (held.js partToRig), points and 4x4s. */
  const toRig = (side, p) => H.partToRig(side === 'left' ? 'left_arm' : 'right_arm', p);
  const fromRig = (side, r) => { const o = toRig(side, [0, 0, 0]); return [o[0] - r[0], o[1] - r[1], r[2] - o[2]]; };
  function mul(a, b) {
    const o = new Array(16).fill(0);
    for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) for (let k = 0; k < 4; k++) o[r * 4 + c] += a[r * 4 + k] * b[k * 4 + c];
    return o;
  }

  /** The viewer's seat for a bow held on `side` (quads in rig space): place() in the arm's part space carried into rig
   *  space, as { m } for held.js seatPoint; null with nothing drawn. */
  function seat(quads, side, cls, grips) {
    // RED: today's game seats a bow as any weapon (held.js seatItem): its class length, the drawn point nearest the fist on it
    const s0 = H.seatItem(cls, grips, quads, H.FIST[side]);
    if (s0) { const k = s0.scale; return { m: [k, 0, 0, s0.fist[0] - k * s0.grip[0], 0, k, 0, s0.fist[1] - k * s0.grip[1], 0, 0, k, s0.fist[2] - k * s0.grip[2], 0, 0, 0, 1] }; }
    const m = place(quads.map((q) => q.map((p) => fromRig(side, p))), side === 'left', cls, grips);
    if (!m) return null;
    const o = toRig(side, [0, 0, 0]);
    const F = [-1, 0, 0, o[0], 0, -1, 0, o[1], 0, 0, 1, o[2], 0, 0, 0, 1], Finv = [-1, 0, 0, o[0], 0, -1, 0, o[1], 0, 0, 1, -o[2], 0, 0, 0, 1];
    return { m: mul(mul(F, m), Finv) };
  }

  /** The draw: how far the nock (the string's middle) is pulled back from the grip at full draw, px. A sensible draw for
   *  the rig: an archer's draw length is about the arm span over 2.5 (the archery shops' rule); the rig's span, fingertip to
   *  fingertip with the arms out, is 30 px (shoulder pivots 9 px apart, each arm 10.5 px from its pivot to the hand's
   *  end), so 12 px, less the 1.75 in (about 0.7 px here) the rule counts in front of the grip: 11.3 px, rounded to 11. */
  const DRAW = 11;

  /** The string's middle at draw progress p for a placed bow ({ out, grip, brace } in one space): from the brace (p 0) to
   *  DRAW behind the grip (p 1), straight back along -out. */
  const nock = (bow, p) => sub(bow.grip, scale(bow.out, bow.brace + Math.max(0, Math.min(1, p)) * (DRAW - bow.brace)));

  /** The item model at draw progress p: 0 slack (not drawing), then Minecraft's bow predicates (pulling, pull 0.65, pull
   *  0.9): 1 pulling_0, 2 pulling_1, 3 pulling_2. */
  const STAGES = ['', '_pulling_0', '_pulling_1', '_pulling_2'];
  const PULL_1 = 0.65, PULL_2 = 0.9;
  const stageOf = (p) => (!(p > 0) ? 0 : p < PULL_1 ? 1 : p < PULL_2 ? 2 : 3);

  /** Draw progress in a bow clip at clip time t: bow_aim draws from drawStart to fullDraw (straight, as the clip's hand
   *  moves) and holds; bow_release holds the full draw until its release, then the string is slack. 0 otherwise. */
  function pullAt(draw, clip, t) {
    if (!draw) return 0;
    if (clip === 'bow_aim') return t <= draw.drawStart ? 0 : Math.min(1, (t - draw.drawStart) / Math.max(1e-6, draw.fullDraw - draw.drawStart));
    if (clip === 'bow_release') return t < draw.release ? 1 : 0;
    return 0;
  }

  /** The bow as placed in weapon_r's rest frame (rig px; the frame `seat` works in): the grip and the arrow's way. */
  const GRIP_RIG = toRig('right', GRIP.right), OUT_RIG = [-OUT[0], -OUT[1], OUT[2]];
  /** The drawing hand: the left fist (weapon_l's pivot, rig px). */
  const DRAW_HAND = [-6, 12, -2];

  /**
   * The draw timing of a race's bow clips, measured from the drawing hand (the build writes it to draw.json; the game
   * reads it, client.anim.BowDraw). worlds(clip, t) gives a clip's world bones (gecko/sample.js worldBones). Along the
   * arrow (the bow's out, through the grip) the hand's pull is how far it stands behind the grip, its miss how far off
   * the arrow's line it is:
   *   drawStart  bow_aim: the hand on the string (miss under ON px) from here to the full draw, its pull rising
   *   fullDraw   bow_aim: the pull first within ON of its greatest
   *   release    bow_release: the hand first more than ON from where the full draw held it (the string's middle)
   * Times in seconds of clip time, sampled every 1/240 s, rounded to the ms.
   */
  const ON = 0.05, TICK = 1 / 240;
  function drawTiming(worlds, aimLength, releaseLength) {
    const at = (clip, t) => {
      const W = worlds(clip, t), w = W.weapon_r, R = w.R;
      const grip = [0, 1, 2].map((r) => R[r * 3] * GRIP_RIG[0] + R[r * 3 + 1] * GRIP_RIG[1] + R[r * 3 + 2] * GRIP_RIG[2] + w.p[r]);
      const out = [0, 1, 2].map((r) => R[r * 3] * OUT_RIG[0] + R[r * 3 + 1] * OUT_RIG[1] + R[r * 3 + 2] * OUT_RIG[2]);
      const l = W.weapon_l, hand = [0, 1, 2].map((r) => l.R[r * 3] * DRAW_HAND[0] + l.R[r * 3 + 1] * DRAW_HAND[1] + l.R[r * 3 + 2] * DRAW_HAND[2] + l.p[r]);
      const v = sub(grip, hand), pull = dot(v, out);
      return { pull, miss: Math.hypot(...sub(v, scale(out, pull))), hand, grip, out };
    };
    const ts = (len) => Array.from({ length: Math.floor(len / TICK + 1e-6) + 1 }, (_, i) => i * TICK);
    const aim = ts(aimLength).map((t) => ({ t, ...at('bow_aim', t) }));
    const top = Math.max(...aim.map((s) => s.pull));
    const full = aim.find((s) => s.pull >= top - ON);
    let start = aim.indexOf(full);
    while (start > 0 && aim[start - 1].miss < ON && aim[start - 1].pull <= aim[start].pull + 1e-9) start--;
    const rel = ts(releaseLength).map((t) => ({ t, ...at('bow_release', t) }));
    const held = rel[0], gone = rel.find((s) => Math.hypot(...sub(s.hand, sub(s.grip, scale(s.out, held.pull)))) > ON);
    const ms = (t) => +t.toFixed(3);
    return { drawStart: ms(aim[start].t), fullDraw: ms(full.t), release: ms(gone ? gone.t : releaseLength) };
  }

  const api = { measure, place, placedBow, seat, nock, stageOf, pullAt, drawTiming, STAGES, PULL_1, PULL_2, DRAW, GRIP, UP, OUT, MIDDLE, DRAW_HAND, toRig, fromRig };
  if (isNode) module.exports = api;
  else root.GeckoBow = api;
})(typeof window !== 'undefined' ? window : globalThis);
