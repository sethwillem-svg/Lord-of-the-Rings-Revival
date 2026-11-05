'use strict';
/* Bows in the archer's hands: in every race's bow clips the bow stands upright across the aim with its grip in the bow
 * hand and its string toward the archer, the drawing hand holds the string's middle as it is drawn to the jaw, and the
 * model the game shows (slack, pulling_0..2: gecko/bow.js stageOf, from the clip's draw progress and the race's draw.json)
 * follows the hand. The bow is placed as the game and the viewer place it (gecko/bow.js seat: client.render.WeaponGrip.bow).
 *
 * Bows: two made here as Minecraft bakes a generated (flat) item model, so no export is needed: a bow and a flatter
 * longbow drawn on their texture's diagonal (two faces over the 16 px square, one-texel edge quads along the wood's two
 * edges and along the string), seen in the arm as a display transform leaves them; and every bow and longbow of the
 * exported Middle-earth NPCs (addon/run/viewer_export, from ./addon/bench.sh export), when there is one.
 *
 * Numbers:
 *  - upright: the limb axis within 25° of the torso's up while aiming. Archers cant the bow 10-20° (a "canted" or
 *    instinctive hold) and still count as shooting upright; 25° leaves room for the torso's own lean in the clip (the
 *    orc's hunch tilts its torso's up 6° from the vertical) while a bow held flat or slanting across the body still fails.
 *  - the plane holds the aim within 15°: the arrow lies in the bow's plane (it rests on the grip and the string), so its
 *    plane must hold the direction of the shot; 15° is about the arrow's own spread off the string at full draw.
 *  - grip in the bow hand: within 2.5 px of the hand (the melee test's measure, test/weapons.test.js).
 *  - the drawing hand within 2 px of the nock: half the hand's 4 px.
 *  - full draw: the nock at least 70% of the arm's length (shoulder pivot to fist, 10.79 px) from the bow hand, and
 *    within 4 px of the jaw (the head's lower edge from the chin to the jaw's corner on the drawing side) or of the chest
 *    (the torso's upper front, from the breastbone to the drawing-side shoulder).
 * Aiming: bow_aim from its draw start, bow_release until its release. Frames: every 1/120 s. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const Hd = require('../gecko/held.js');
const B = require('../gecko/bow.js');
const { RACES } = require('../npc_slots.js');
const { shipped } = require('./lib/shipped.js');

const GRIPS = JSON.parse(fs.readFileSync(path.join(__dirname, '../../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json'), 'utf8'));
const EXPORT = path.join(__dirname, '../../../addon/run/viewer_export');
const UPRIGHT = 25, PLANE = 15, GRIP_NEAR = 2.5, ON_STRING = 2, SPAN = 0.7, JAW_NEAR = 4;
const ARM = Math.hypot(1.5, 10.5, 2);   // the arm's length: shoulder pivot (4.5, 22.5, 0) to fist (6, 12, -2), rig px
const AIM = [0, 0, -1];
const STEP = 1 / 120;
const deg = (r) => (r * 180) / Math.PI;
const sub = (a, b) => a.map((x, i) => x - b[i]);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(...a); return a.map((x) => x / l); };
const rot = (w, d) => [0, 1, 2].map((r) => w.R[r * 3] * d[0] + w.R[r * 3 + 1] * d[1] + w.R[r * 3 + 2] * d[2]);
const angle = (a, b) => deg(Math.acos(Math.max(-1, Math.min(1, dot(norm(a), norm(b))))));

// ---- bows ----
/** A generated bow as Minecraft bakes it, in the arm's part space as drawn (the texture square in the plane y = 8, its u
 *  along -x and v along +z about the fist): the tips at texels (15.5, 0.5) and (0.5, 15.5), the wood a quadratic arc bowed
 *  toward texel (c, c), 1.5 texels thick, the string straight between the tips, 1 texel thick. */
function generatedBow(c) {
  const A = [15.5, 0.5], Z = [0.5, 15.5], C = [c, c];
  const bez = (f) => [0, 1].map((i) => (1 - f) ** 2 * A[i] + 2 * f * (1 - f) * C[i] + f * f * Z[i]);
  const toPart = ([u, v], y) => [8 - u - 1, y, v - 8 - 2];
  const edge = (line) => {   // one-texel edge quads along a polyline (texels), the item 1 px thick
    const qs = [];
    for (let i = 0; i + 1 < line.length; i++) qs.push([toPart(line[i], 7.5), toPart(line[i + 1], 7.5), toPart(line[i + 1], 8.5), toPart(line[i], 8.5)]);
    return qs;
  };
  const offset = (pts, d) => pts.map((p, i) => {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)], t = norm([b[0] - a[0], b[1] - a[1], 0]);
    return [p[0] - t[1] * d, p[1] + t[0] * d];
  });
  const wood = Array.from({ length: 25 }, (_, i) => bez(i / 24)), string = Array.from({ length: 22 }, (_, i) => [0, 1].map((k) => A[k] + ((Z[k] - A[k]) * i) / 21));
  const face = (y) => [toPart([0, 0], y), toPart([16, 0], y), toPart([16, 16], y), toPart([0, 16], y)];
  return [face(7.5), face(8.5).reverse(), ...edge(offset(wood, 0.75)), ...edge(offset(wood, -0.75)), ...edge(offset(string, 0.5)), ...edge(offset(string, -0.5))];
}
const toRigQuads = (quads, side) => quads.map((q) => q.map((p) => Hd.partToRig(side === 'left' ? 'left_arm' : 'right_arm', p)));
const exported = fs.existsSync(path.join(EXPORT, 'index.json'))
  ? JSON.parse(fs.readFileSync(path.join(EXPORT, 'index.json'), 'utf8')).npcs.map((n) => JSON.parse(fs.readFileSync(path.join(EXPORT, n.file), 'utf8')))
  : [];
const BOWS = [
  { label: 'generated bow', cls: 'bow', quads: generatedBow(2) },
  { label: 'generated longbow', cls: 'longbow', quads: generatedBow(4) },
  ...exported.flatMap((d) => {
    const ms = d.meshes.filter((m) => m.hand && m.part === 'right_arm' && (m.weapon === 'bow' || m.weapon === 'longbow'));
    return ms.length ? [{ label: d.label, cls: ms[0].weapon, quads: ms.flatMap((m) => Hd.quadsOf(m.pos, m.idx)) }] : [];
  }),
].map((b) => {
  const rig = toRigQuads(b.quads, 'right'), seat = B.seat(rig, 'right', b.cls, GRIPS);
  return { ...b, rig, seat, placed: B.placedBow(rig, seat && seat.m) };
});

// ---- clips ----
const draw = (race) => shipped[race].draw;
const anim = (race, name) => shipped[race].animations.animations[name];
const frames = (a, from = 0, to = a.animation_length) => {
  const out = [];
  for (let i = 0; i * STEP <= a.animation_length + 1e-9; i++) if (i * STEP >= from - 1e-9 && i * STEP < to - 1e-9) out.push(i * STEP);
  return out;
};
/** The aiming frames of a race: bow_aim from its draw start, bow_release until its release, with their draw progress. */
function aiming(race) {
  const d = draw(race);
  if (!d) return [];
  return [
    ...frames(anim(race, 'bow_aim'), d.drawStart).map((t) => ({ clip: 'bow_aim', t })),
    ...frames(anim(race, 'bow_release'), 0, d.release).map((t) => ({ clip: 'bow_release', t })),
  ].map((f) => ({ ...f, p: B.pullAt(d, f.clip, f.t), W: worldBones(samplePose(anim(race, f.clip), f.t)) }));
}
const AIMING = Object.fromEntries(RACES.map((r) => [r, aiming(r)]));
/** A placed bow posed by world bones W: its up, out, normal, grip and brace (rig space). */
function posedBow(b, W) {
  const w = W.weapon_r, up = rot(w, b.placed.up), out = rot(w, b.placed.out);
  return { up, out, normal: norm(cross(up, out)), grip: point(w, b.placed.grip), brace: b.placed.brace };
}
const hand = (W) => point(W.weapon_l, B.DRAW_HAND);

/** Each failing (race, clip, bow) once, with its worst frame. */
function judge(check) {
  const worst = new Map();
  let n = 0;
  for (const race of RACES) for (const f of AIMING[race]) for (const b of BOWS) {
    const r = check(f, b, race);
    if (r === null) continue;
    n++;
    if (r.bad) {
      const k = `${race} ${f.clip} (${b.label})`, w = worst.get(k);
      if (!w || r.v > w.v) worst.set(k, { v: r.v, msg: `${k} t=${f.t.toFixed(3)}: ${r.msg}` });
    }
  }
  return { n, bad: [...worst.values()].map((x) => x.msg) };
}

test('every race ships its draw timing (draw.json): draw start, full draw and release, in order and inside the clips', () => {
  for (const race of RACES) {
    const d = draw(race);
    assert.ok(d, `${race}: no draw.json`);
    assert.ok(d.drawStart > 0 && d.drawStart < d.fullDraw && d.fullDraw <= anim(race, 'bow_aim').animation_length, `${race}: ${JSON.stringify(d)}`);
    assert.ok(d.release > 0 && d.release < anim(race, 'bow_release').animation_length, `${race}: ${JSON.stringify(d)}`);
  }
});

test('draw.json is the drawing hand as the clips move it (gecko/bow.js drawTiming)', () => {
  for (const race of RACES) {
    const worlds = (clip, t) => worldBones(samplePose(anim(race, clip), t));
    assert.deepEqual(draw(race), B.drawTiming(worlds, anim(race, 'bow_aim').animation_length, anim(race, 'bow_release').animation_length), race);
  }
});

test('the bow stands upright across the aim: limbs within 25° of the torso’s up, its plane holding the aim within 15°', () => {
  const r = judge((f, b) => {
    const pb = posedBow(b, f.W), tilt = angle(pb.up, rot(f.W.body, [0, 1, 0])), off = deg(Math.asin(Math.min(1, Math.abs(dot(pb.normal, AIM)))));
    return { bad: tilt > UPRIGHT || off > PLANE, v: Math.max(tilt - UPRIGHT, off - PLANE), msg: `limbs ${tilt.toFixed(0)}° off the torso's up, plane ${off.toFixed(0)}° off the aim` };
  });
  assert.ok(r.n > 0, 'no aiming frames');
  assert.deepEqual(r.bad, [], `${r.bad.length} failing`);
});

test('the string faces the drawing hand, not the target', () => {
  const r = judge((f, b) => {
    const pb = posedBow(b, f.W), toHand = sub(hand(f.W), pb.grip), a = angle(pb.out.map((x) => -x), toHand);
    return { bad: !(a < 90), v: a, msg: `the string side is ${a.toFixed(0)}° from the drawing hand` };
  });
  assert.ok(r.n > 0, 'no aiming frames');
  assert.deepEqual(r.bad, [], `${r.bad.length} failing`);
});

test('the drawing hand holds the string: within 2 px of its middle, pulled back by the draw shown', () => {
  const r = judge((f, b) => {
    const d = Math.hypot(...sub(hand(f.W), B.nock(posedBow(b, f.W), f.p)));
    return { bad: d > ON_STRING, v: d, msg: `the hand is ${d.toFixed(2)} px from the nock (draw ${f.p.toFixed(2)})` };
  });
  assert.ok(r.n > 0, 'no aiming frames');
  assert.deepEqual(r.bad, [], `${r.bad.length} failing`);
});

/** The distance from p to the segment a-b. */
const toSegment = (p, a, b) => {
  const ab = sub(b, a), f = Math.max(0, Math.min(1, dot(sub(p, a), ab) / dot(ab, ab)));
  return Math.hypot(...sub(p, a.map((x, i) => x + f * ab[i])));
};
test('at full draw the nock is by the jaw or the chest, at least 70% of the arm’s length from the bow hand', () => {
  const r = judge((f, b) => {
    if (f.p < 1) return null;
    const pb = posedBow(b, f.W), n = B.nock(pb, 1), fist = point(f.W.weapon_r, Hd.FIST.right);
    const span = Math.hypot(...sub(n, fist)) / ARM;
    const jaw = toSegment(n, point(f.W.head, [0, 24, -4]), point(f.W.head, [-4, 24, 0]));
    const chest = toSegment(n, point(f.W.body, [0, 22, -2]), point(f.W.body, [-4, 22, -2]));
    const near = Math.min(jaw, chest);
    return { bad: span < SPAN || near > JAW_NEAR, v: Math.max(SPAN - span, near - JAW_NEAR), msg: `nock ${(span * 100).toFixed(0)}% of the arm from the bow hand, ${jaw.toFixed(1)} px from the jaw, ${chest.toFixed(1)} px from the chest` };
  });
  assert.ok(r.n > 0, 'no full-draw frames');
  assert.deepEqual(r.bad, [], `${r.bad.length} failing`);
});

/** The hand: the bottom of the arm (part space x -3..1, y 7..12, z -2.5..2.5 for the right arm) in rig space. */
const HAND = { right: [[4, 10, -2.5], [8, 15, 2.5]], left: [[-8, 10, -2.5], [-4, 15, 2.5]] };
const toBox = (b, p) => Math.hypot(...[0, 1, 2].map((i) => Math.max(b[0][i] - p[i], 0, p[i] - b[1][i])));
const HOLDING = ['bow_aim', 'bow_release', 'idle', 'idle_2', 'idle_3', 'walk', 'run', 'crouch_walk', 'march'];
test('the bow’s grip is in the bow hand (within 2.5 px of it) in every clip an archer holds it in, either hand', () => {
  const bad = [];
  for (const b of BOWS) for (const side of ['right', 'left']) {
    const rig = toRigQuads(b.quads, side), seat = B.seat(rig, side, b.cls, GRIPS), pb = B.placedBow(rig, seat && seat.m);
    for (const race of RACES) for (const name of HOLDING) {
      const a = anim(race, name), bone = side === 'left' ? 'weapon_l' : 'weapon_r', arm = side === 'left' ? 'left_elbow' : 'right_elbow';
      let worst = 0;
      for (const t of frames(a).filter((_, i) => i % 4 === 0)) {
        const W = worldBones(samplePose(a, t)), g = point(W[bone], pb.grip), e = W[arm];
        const local = rot({ R: [e.R[0], e.R[3], e.R[6], e.R[1], e.R[4], e.R[7], e.R[2], e.R[5], e.R[8]] }, sub(g, e.p));   // back into the forearm's rest frame
        worst = Math.max(worst, toBox(HAND[side], local));
      }
      if (worst > GRIP_NEAR) bad.push(`${b.label} (${side}) ${race} ${name}: ${worst.toFixed(1)} px from the hand`);
    }
  }
  assert.deepEqual(bad.slice(0, 12), [], `${bad.length} failing`);
});

test('the bow is its class length once placed (±5%), and is measured from its own faces: the grip on the wood’s middle, the string on the far side', () => {
  for (const b of BOWS) {
    assert.ok(b.placed, `${b.label}: not measured`);
    assert.ok(Math.abs(b.placed.length / (GRIPS[b.cls].length * 16) - 1) <= 0.05, `${b.label}: ${(b.placed.length / 16).toFixed(2)} blocks for ${b.cls}`);
    assert.ok(b.placed.brace > 1 && b.placed.brace < 7, `${b.label}: brace ${b.placed.brace.toFixed(2)} px`);
  }
  // the generated bow, as drawn: tips at texels (15.5, 0.5) and (0.5, 15.5); the wood's middle bowed toward texel (5, 5)
  const m = B.measure(generatedBow(2));
  const g = [8 - 5 - 1, 8, 5 - 8 - 2];   // texel (5, 5) in part space (generatedBow's toPart)
  assert.ok(Math.hypot(...sub(m.grip, g)) < 0.3, `grip [${m.grip.map((x) => x.toFixed(2))}], want [${g}]`);
  assert.ok(Math.abs(m.brace - 3 * Math.SQRT2) < 0.3, `brace ${m.brace.toFixed(2)} texels, want ${(3 * Math.SQRT2).toFixed(2)}`);
});

test('the model shown follows the draw: slack until it starts, then pulling_0..2 rising with the hand’s pull, pulling_2 at full draw, slack from the release', () => {
  const bad = [];
  const b = BOWS[0];
  for (const race of RACES) {
    const d = draw(race);
    if (!d) { bad.push(`${race}: no draw.json`); continue; }
    const aim = anim(race, 'bow_aim');
    let last = 0;
    for (const t of frames(aim).concat([aim.animation_length])) {
      const s = B.stageOf(B.pullAt(d, 'bow_aim', t));
      if (t < d.drawStart - 1e-9 && s !== 0) bad.push(`${race} bow_aim t=${t.toFixed(3)}: stage ${s} before the draw`);
      if (t >= d.fullDraw - 1e-9 && s !== 3) bad.push(`${race} bow_aim t=${t.toFixed(3)}: stage ${s} at full draw`);
      if (s < last) bad.push(`${race} bow_aim t=${t.toFixed(3)}: stage falls ${last} -> ${s}`);
      last = s;
      if (t > d.drawStart + 1e-9 && t < d.fullDraw) {   // the hand's own progress shows the same stage, but at a threshold
        const W = worldBones(samplePose(aim, t)), pb = posedBow(b, W), pull = dot(sub(pb.grip, hand(W)), pb.out);
        const q = (pull - pb.brace) / (B.DRAW - pb.brace), edge = [0, B.PULL_1, B.PULL_2].some((x) => Math.abs(q - x) < 0.08);
        if (!edge && B.stageOf(q) !== s) bad.push(`${race} bow_aim t=${t.toFixed(3)}: stage ${s}, the hand's pull ${q.toFixed(2)} shows ${B.stageOf(q)}`);
      }
    }
    const rel = anim(race, 'bow_release');
    for (const t of frames(rel)) {
      const s = B.stageOf(B.pullAt(d, 'bow_release', t));
      if ((t < d.release - 1e-9 ? 3 : 0) !== s) bad.push(`${race} bow_release t=${t.toFixed(3)}: stage ${s} (release ${d.release})`);
    }
  }
  assert.deepEqual(bad.slice(0, 12), [], `${bad.length} failing`);
});

test('stages: slack at no draw, then Minecraft’s bow predicates (pulling; pull 0.65; pull 0.9)', () => {
  assert.deepEqual([0, 0.01, 0.64, 0.65, 0.89, 0.9, 1].map(B.stageOf), [0, 1, 1, 2, 2, 3, 3]);
  assert.deepEqual(B.STAGES, ['', '_pulling_0', '_pulling_1', '_pulling_2']);
  const d = { drawStart: 0.2, fullDraw: 0.6, release: 0.05 };
  assert.deepEqual([0, 0.2, 0.4, 0.6, 0.7].map((t) => +B.pullAt(d, 'bow_aim', t).toFixed(9)), [0, 0, 0.5, 1, 1]);
  assert.deepEqual([0, 0.049, 0.05, 0.3].map((t) => B.pullAt(d, 'bow_release', t)), [1, 1, 0, 0]);
  assert.equal(B.pullAt(d, 'idle', 0.3), 0);
});
