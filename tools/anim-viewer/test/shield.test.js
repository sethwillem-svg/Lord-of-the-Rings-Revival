'use strict';
/* Shields guard the front: in every clip played with a shield (the _shield versions and shield_brace), the shield's plate
 * faces where the NPC faces and covers its chest. The plate is built and placed as the game draws it (gecko/shield.js:
 * Middle-earth's two kinds of shield model, Minecraft's held-item seat and display transform, and item()'s placement),
 * so no export is needed. Frames: the 60 Hz grid, once the clip has faded in (attacks: hits.json fadeIn, as played). */
const test = require('node:test');
const assert = require('node:assert/strict');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const SH = require('../gecko/shield.js');
const { RACES } = require('../npc_slots.js');
const { shipped: built } = require('./lib/shipped.js');
const { playSpeed } = require('./lib/game.js');

const SIDE = 'left';   // the clips hold the shield in the off (left) hand
const FACING = 35, SWING = 60, NEAR_HIT = 0.15;   // degrees; seconds either side of the hit, as played
const MOVING = /^(idle(_\d)?|walk|run|march|guard)_shield$|^shield_brace$/;
const ATTACKING = /^attack_[a-z]+_\d+_shield$/;
const deg = (r) => (r * 180) / Math.PI;
const rot = (w, d) => [0, 1, 2].map((r) => w.R[r * 3] * d[0] + w.R[r * 3 + 1] * d[1] + w.R[r * 3 + 2] * d[2]);

/** Each model's plate in the arm, after item()'s placement: its outward face normal and its faces (part space). */
const PLATES = Object.entries(SH.MODELS).map(([kind, model]) => {
  const drawn = SH.armQuads(model, SIDE === 'left'), m = SH.place(drawn, SIDE === 'left'), plate = SH.plateOf(drawn);
  return { kind, out: m ? SH.mat.applyDir(m, plate.out) : plate.out, faces: SH.placed(plate.faces, m) };
});

const clipsOf = (race) => Object.keys(built[race].animations.animations).filter((n) => MOVING.test(n) || ATTACKING.test(n));
/** The 60 Hz frames once faded in, with the window round the hit (clip seconds) for attacks. */
function frames(race, name) {
  const a = built[race].animations.animations[name], h = built[race].hits[name.replace(/_shield$/, '')];
  const speed = h ? playSpeed(h) : 1, from = h ? h.fadeIn * speed : 0, out = [];
  for (let i = 0; i / 60 <= a.animation_length + 1e-9; i++) {
    const t = i / 60;
    if (t + 1e-9 < from || (a.loop === true && t >= a.animation_length - 1e-9 && t > 0)) continue;
    out.push({ t, nearHit: h ? Math.abs(t - h.hit) <= NEAR_HIT * speed : false });
  }
  return out;
}

/** The angle (degrees) between the plate's face and the NPC's facing (-z), and the posed plate faces (rig space). */
function measure(W, plate) {
  const w = W[SIDE === 'left' ? 'weapon_l' : 'weapon_r'], n = rot(w, SH.dirToRig(plate.out));
  return { angle: deg(Math.acos(Math.max(-1, Math.min(1, -n[2])))), faces: plate.faces.map((q) => q.map((p) => point(w, SH.toRig(SIDE, p)))) };
}

/** Does the segment a-b cross the (planar, convex) quad q? */
function crosses(a, b, q) {
  const sub = (u, v) => u.map((x, i) => x - v[i]), dot = (u, v) => u[0] * v[0] + u[1] * v[1] + u[2] * v[2];
  const cross = (u, v) => [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
  const n = cross(sub(q[1], q[0]), sub(q[2], q[0])), d = sub(b, a), den = dot(n, d);
  if (Math.abs(den) < 1e-12) return false;
  const s = dot(n, sub(q[0], a)) / den;
  if (s < 0 || s > 1) return false;
  const p = a.map((x, i) => x + s * d[i]);
  let sign = 0;
  for (let i = 0; i < q.length; i++) {
    const c = dot(n, cross(sub(q[(i + 1) % q.length], q[i]), sub(p, q[i])));
    if (Math.abs(c) < 1e-9) continue;
    if (sign && Math.sign(c) !== sign) return false;
    sign = Math.sign(c);
  }
  return true;
}

/** Every frame's failures of a check, counted per race and clip; the message lists the counts. */
function judge(check) {
  const bad = [], counts = {};
  let total = 0, failed = 0;
  for (const race of RACES) for (const name of clipsOf(race)) for (const plate of PLATES) {
    const a = built[race].animations.animations[name];
    for (const f of frames(race, name)) {
      const W = worldBones(samplePose(a, f.t)), why = check(W, plate, name, f);
      if (why === null) continue;   // not judged here
      total++;
      if (!why) continue;
      failed++;
      const key = `${race} ${name} (${plate.kind})`;
      counts[key] = counts[key] || { n: 0, first: why };
      counts[key].n++;
    }
  }
  for (const [k, v] of Object.entries(counts)) bad.push(`${k}: ${v.n} frames, e.g. ${v.first}`);
  return { bad, total, failed };
}

test('with a shield the plate faces the front: within 35° of the facing, and within 60° in the swing (±0.15 s of the hit as played)', () => {
  const r = judge((W, plate, name, f) => {
    const { angle } = measure(W, plate), limit = ATTACKING.test(name) && f.nearHit ? SWING : FACING;
    return angle > limit ? `t=${f.t.toFixed(3)} ${angle.toFixed(0)}° (limit ${limit}°)` : '';
  });
  assert.deepEqual(r.bad, [], `${r.failed}/${r.total} frames face away`);
});

test('with a shield, standing, moving and braced, the plate covers the chest: the line from 16 px in front of the chest centre to it passes through the plate', () => {
  const r = judge((W, plate, name, f) => {
    if (!MOVING.test(name)) return null;
    const c = point(W.body, [0, 18, 0]), from = [c[0], c[1], c[2] - 16];
    return measure(W, plate).faces.some((q) => crosses(from, c, q)) ? '' : `t=${f.t.toFixed(3)} chest centre [${c.map((x) => x.toFixed(1))}] open`;
  });
  assert.deepEqual(r.bad, [], `${r.failed}/${r.total} frames leave the chest open`);
});

test('the plate measure finds both kinds of Middle-earth shield: the face away from the handle, one px thick', () => {
  for (const [kind, model] of Object.entries(SH.MODELS)) {
    const p = SH.plateOf(SH.armQuads(model, true));
    assert.ok(Math.abs(p.front - p.back - 1) < 1e-9, `${kind}: plate ${p.front - p.back} px thick`);
    // the handle lies behind the plate: every point is at or behind the plate's front
    const behind = SH.armQuads(model, true).flat().every((q) => q[0] * p.out[0] + q[1] * p.out[1] + q[2] * p.out[2] <= p.front + 1e-9);
    assert.ok(behind, `${kind}: points in front of the plate`);
  }
});
