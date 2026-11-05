/* Mocap and hand-animated BVH (library/ual: Quaternius UAL 1 and 2, CC0; library/kaykit: KayKit, CC0; library/cmu: the CMU
 * Graphics Lab Motion Capture Database, CMU's terms) onto the NPC rig (gecko/rig.js), as Gecko clips on the rig's bones.
 *   NPC_MAP                       the rig map: rigs.js 'spec-biped' with its bones named as the NPC rig names them
 *   SETTINGS                      the retarget settings: the browser viewer's (index.html) with the clip library's (library.js)
 *   npcRig()                      the NPC rig as retarget.js reads a rig (a skeleton with limb boxes, in retarget space)
 *   importBvh(text, opts)         one Clip: { length, loop, bones } in the build's Clip convention (gecko/write.js)
 *   loopCycle(clip, cycles)       a looping clip cut to `cycles` gait cycles, starting with the right thigh furthest forward,
 *                                 its seam spread over the cycle so the end meets the start
 *   holdFrame(clip, t)            a held pose of time t
 * opts: { loop: whether the clip loops, cycles: gait cycles to keep (loops that travel), speed, range: [first, last] frame,
 *         mirror, settings: retarget overrides }.
 *
 * retarget.js works in its own rig space, where +x is the character's left (as Blockbench models are drawn); the NPC rig's
 * space has the character's right on +x (gecko/rig.js). So the rig is handed to retarget.js mirrored in x, and every pose
 * it returns is mirrored back (a reflection on both sides of a rotation leaves it a rotation): the motion is the take's
 * own, not its mirror image.
 *
 * In place versus root motion: the game moves the entity, so clips play in place (the clip library's default). UAL and
 * KayKit loops are authored in place; CMU takes travel. A travelling take keeps only its hips' height (inPlace), and its
 * foot lock is turned off: the lock pins a planted mocap foot where it landed, which on a take that travels would freeze
 * the foot under hips that no longer travel (the legs would stop sweeping back). */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const A = isNode ? require('../retarget.js') : root.AnimRetarget;
  const RIG_MAPS = isNode ? require('../rigs.js') : root.RIG_MAPS;
  const LIB = isNode ? require('../library.js') : root.ANIM_LIBRARY;
  const { RIG } = isNode ? require('../gecko/rig.js') : root.GeckoRig;
  const P = isNode ? require('./parts.js') : root.ImportParts;

  /** rigs.js spec-biped, bone for bone on the NPC rig: its body (the pelvis) is the rig's lower, its torso the rig's upper
   *  (which carries body and the arms), the forearms the elbows, the shins the knees. No hand and no weapon bone: the
   *  weapon bones (weapon_r, weapon_l) hold the item where Minecraft seats it in the fist, so they keep their rest grip (a
   *  mocap wrist would turn the held item with it; retarget.js turns the weapon bone 180° to run a blade along the forearm,
   *  which is a Blockbench model's grip, not Minecraft's). */
  const RENAME = { body: 'lower', torso: 'upper', right_forearm: 'right_elbow', left_forearm: 'left_elbow', right_shin: 'right_knee', left_shin: 'left_knee' };
  const SPEC = RIG_MAPS.find((m) => m.id === 'spec-biped');
  const name = (n) => RENAME[n] || n;
  const NPC_MAP = {
    id: 'npc-rig', note: 'spec-biped on the NPC rig (gecko/rig.js)',
    root: name(SPEC.root), pelvis: name(SPEC.pelvis), upper: name(SPEC.upper), head: name(SPEC.head),
    arms: { right: SPEC.arms.right.slice(0, 2).map(name), left: SPEC.arms.left.slice(0, 2).map(name) },
    legs: { right: SPEC.legs.right.map(name), left: SPEC.legs.left.map(name) },
  };

  /** The browser viewer's retarget settings as the page opens (index.html settings(): every control at its default), with
   *  the clip library's (library.js defaults: in place, weapon IK off). */
  const VIEWER = {
    torsoTiltCap: 90, rootDrop: 1, weapon: 'forearm', mirror: false, footLift: true, footPlant: true, inPlace: false,
    ik: true, ikTargetY: 0, ikReach: 0.85, rotOrder: 'auto', jointLimits: true, footLock: true, stance: 1,
    wrist: 'auto', edgeFirst: true, ikTorsoShare: 0.4, weaponPitch: 0, weaponYaw: 0, weaponRoll: 0, smooth: 0,
  };
  const SETTINGS = Object.freeze(Object.assign({}, VIEWER, LIB.defaults));

  /** The limb boxes retarget.js measures (the feet: the lowest point under each knee), rig px, NPC rig space. */
  const BOXES = {
    right_leg: [[-0.1, 6, -2], [3.9, 12, 2]], right_knee: [[-0.1, 0, -2], [3.9, 6, 2]],
    left_leg: [[-3.9, 6, -2], [0.1, 12, 2]], left_knee: [[-3.9, 0, -2], [0.1, 6, 2]],
    upper: [[-4, 12, -2], [4, 24, 2]], head: [[-4, 24, -4], [4, 32, 4]],
    right_arm: [[4, 18, -2], [8, 24, 2]], right_elbow: [[4, 12, -2], [8, 18, 2]],
    left_arm: [[-8, 18, -2], [-4, 24, 2]], left_elbow: [[-8, 12, -2], [-4, 18, 2]],
  };
  const flip = (v) => [-v[0], v[1], v[2]];

  /** The NPC rig as retarget.js parseRig() returns a rig, mirrored into retarget space (+x the character's left). */
  function npcRig() {
    const idx = {};
    RIG.forEach((b, i) => { idx[b.name] = i; });
    const bones = RIG.map((b) => {
      const box = BOXES[b.name];
      const cubes = box ? [{ from: flip(box[1]).map((x, i) => (i === 0 ? x : box[0][i])), to: flip(box[0]).map((x, i) => (i === 0 ? x : box[1][i])) }] : [];
      return { name: b.name, parent: b.parent ? idx[b.parent] : -1, origin: flip(b.pivot), rest: [0, 0, 0], restMat: [1, 0, 0, 0, 1, 0, 0, 0, 1], cubes, children: [] };
    });
    bones.forEach((b, i) => { if (b.parent >= 0) bones[b.parent].children.push(i); });
    return { bones, idx, bb: null };
  }

  /** Does the take travel? Its hips move further over the ground, start to end, than a third of their height. */
  function travels(bvh) {
    const P0 = A.bvhFK(bvh, false), hips = bvh.idx.Hips;
    if (hips === undefined) return false;
    const a = P0[0][hips], b = P0[P0.length - 1][hips];
    let floor = Infinity;
    for (const fr of P0) for (const p of fr) floor = Math.min(floor, p[1]);
    return Math.hypot(b[0] - a[0], b[2] - a[2]) > (a[1] - floor) / 3;
  }

  const S = [-1, 0, 0, 0, 1, 0, 0, 0, 1];   // the mirror in x
  const mirrored = (R) => A.mul(S, A.mul(R, S));

  /** Retargets a parsed BVH (retarget.js parseBVH) onto the NPC rig: a Clip of rotation (and root position) keys. */
  function fromBvh(bvh, opts = {}) {
    const rig = npcRig();
    const moving = travels(bvh);
    const settings = Object.assign({}, SETTINGS, moving ? { footLock: false } : {}, opts.mirror ? { mirror: true } : {}, opts.settings || {});
    const clip = A.retarget(bvh, rig, NPC_MAP, settings);
    const times = clip.frames.map((_, f) => f * clip.dt);
    const frames = clip.frames.map((fr) => {
      const fk = A.rigFK(rig, fr.locals, fr.rootPos), out = {};
      rig.bones.forEach((b, i) => {
        const R = mirrored(fk.rot[i]), pv = flip(b.origin), at = flip(fk.pos[i]);
        out[b.name] = { R, p: at.map((x, k) => x - (R[k * 3] * pv[0] + R[k * 3 + 1] * pv[1] + R[k * 3 + 2] * pv[2])) };
      });
      return out;
    });
    const c = P.worldToClip(frames, times, clip.length, !!opts.loop);
    c.travels = moving;
    return c;
  }

  /** One BVH file's text as a Clip on the NPC rig (opts: see the header). */
  function importBvh(text, opts = {}) {
    const bvh = A.parseBVH(text);
    if (opts.range) bvh.data = bvh.data.slice(opts.range[0], opts.range[1] + 1);
    bvh.frames = bvh.data.length;
    if (opts.speed) bvh.dt /= opts.speed;
    const raw = fromBvh(bvh, opts);
    let c = raw;
    if (opts.loop && opts.cycles) c = loopCycle(raw, opts.cycles);
    else if (opts.loop) c = closeLoop(raw);
    c.travels = raw.travels;
    return c;
  }

  /** A channel's value at t (linear; keys sorted). */
  function valueAt(keys, t) {
    if (t <= keys[0].t) return keys[0].v;
    for (let i = 1; i < keys.length; i++) if (t <= keys[i].t) {
      const a = keys[i - 1], b = keys[i], f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
      return a.v.map((x, j) => x + f * (b.v[j] - x));
    }
    return keys[keys.length - 1].v;
  }

  /** Euler keys unwrapped so neighbours differ by less than half a turn per angle (the residual spread over a loop must
   *  not take the long way round). */
  function unwrapKeys(keys) {
    for (let i = 1; i < keys.length; i++) keys[i].v = keys[i].v.map((x, j) => x + 360 * Math.round((keys[i - 1].v[j] - x) / 360));
  }

  /** The loop closed: each channel's end-to-start difference spread linearly over the clip, so the last key is the first. */
  function closeLoop(clip) {
    const c = { ...clip, loop: true, bones: {} };
    for (const [bone, ch] of Object.entries(clip.bones)) {
      c.bones[bone] = {};
      for (const [kind, keys0] of Object.entries(ch)) {
        const keys = keys0.map((k) => ({ ...k, v: k.v.slice() }));
        if (kind === 'rotation') unwrapKeys(keys);
        const first = keys[0].v, last = keys[keys.length - 1].v, L = clip.length || 1;
        for (const k of keys) k.v = k.v.map((x, j) => x - (last[j] - first[j]) * (k.t / L));
        c.bones[bone][kind] = keys;
      }
    }
    return c;
  }

  /**
   * `cycles` gait cycles of a take that keeps walking: the period is the lag at which the thighs' swing repeats best
   * (0.6-2.4 s a cycle), the cut starts where the right thigh is furthest forward (as the walk clip does: build_npc.js
   * oneStride), within the first period, and the seam is spread over the cut (closeLoop). Resampled at 30 fps.
   */
  function loopCycle(clip, cycles = 1) {
    const legs = ['right_leg', 'left_leg'].map((b) => clip.bones[b] && clip.bones[b].rotation).filter(Boolean);
    if (!legs.length) return closeLoop(clip);
    const L = clip.length, dt = 1 / 120, x = (t) => legs.map((k) => valueAt(k, t)[0]);
    let best = null;
    for (let per = 0.6; per <= Math.min(2.4, L / cycles - 0.05); per += 1 / 120) {
      let err = 0, n = 0;
      for (let t = 0; t + per <= L; t += 1 / 30) { const a = x(t), b = x(t + per); err += a.reduce((s, v, i) => s + (v - b[i]) ** 2, 0); n++; }
      err = n ? err / n : Infinity;
      // prefer the shortest lag that repeats about as well as the best: a two-cycle lag repeats too
      if (!best || err < best.err * 0.8) best = { per, err };
    }
    const period = best ? best.per : L;
    const span = period * cycles;
    let start = 0, lo = Infinity;
    const right = clip.bones.right_leg && clip.bones.right_leg.rotation;
    for (let t = 0; t <= Math.min(period, L - span); t += dt) { const v = right ? valueAt(right, t)[0] : 0; if (v < lo) { lo = v; start = t; } }
    const n = Math.max(2, Math.round(span * 30)), bones = {};
    for (const [bone, ch] of Object.entries(clip.bones)) {
      bones[bone] = {};
      for (const [kind, keys] of Object.entries(ch)) {
        bones[bone][kind] = Array.from({ length: n + 1 }, (_, i) => ({ t: (i * span) / n, v: valueAt(keys, start + (i * span) / n).slice(), lerp: 'linear' }));
      }
    }
    return closeLoop({ length: span, loop: true, bones, period, start });
  }

  /** A held pose: every channel's value at time t, as one key. */
  function holdFrame(clip, t) {
    const bones = {};
    for (const [bone, ch] of Object.entries(clip.bones)) {
      bones[bone] = {};
      for (const [kind, keys] of Object.entries(ch)) bones[bone][kind] = [{ t: 0, v: valueAt(keys, t).slice(), lerp: 'linear' }];
    }
    return { length: 0.05, loop: true, bones };
  }

  const api = { NPC_MAP, SETTINGS, npcRig, importBvh, fromBvh, loopCycle, closeLoop, holdFrame, travels, valueAt };
  if (isNode) module.exports = api;
  else root.ImportMocap = api;
})(typeof window !== 'undefined' ? window : globalThis);
