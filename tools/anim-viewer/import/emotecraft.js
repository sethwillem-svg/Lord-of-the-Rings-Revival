/* Emotecraft emotes (KosmX and contributors, CC0; library/emotecraft): playerAnimator's emote JSON.
 *   importEmote(json)  { name: Clip } sampled at 20 Hz
 * Moves set part values at ticks: pitch/yaw/roll (ModelPart angles, degrees unless "degrees": false), x/y/z (absolute
 * ModelPart positions), bend/axis (the elbow or knee bend and the direction it bends in). Version < 3 files call the whole
 * player "torso" (as playerAnimator reads them). The whole player's own offsets and the torso bend are not carried over. */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const P = isNode ? require('./parts.js') : root.ImportParts;
  const W = isNode ? require('../gecko/write.js') : root.GeckoWrite;

  const PARTS = { head: 'head', torso: 'body', rightArm: 'right_arm', leftArm: 'left_arm', rightLeg: 'right_leg', leftLeg: 'left_leg' };
  const JOINT = { right_arm: 'right_elbow', left_arm: 'left_elbow', right_leg: 'right_knee', left_leg: 'left_knee' };
  /** Vanilla ModelPart positions (Minecraft model space), the defaults of x/y/z. */
  const DEFAULT_POS = { head: [0, 0, 0], torso: [0, 0, 0], rightArm: [-5, 2, 0], leftArm: [5, 2, 0], rightLeg: [-1.9, 12, 0], leftLeg: [1.9, 12, 0], body: [0, 0, 0] };
  const PROPS = ['x', 'y', 'z', 'pitch', 'yaw', 'roll', 'bend', 'axis'];
  const RAD = 180 / Math.PI;

  function importEmote(json, name) {
    const e = json.emote || json;
    const version = json.version || 1;
    const degrees = e.degrees === undefined ? true : e.degrees === true || e.degrees === 'true';
    const easeBefore = e.easeBeforeKeyframe === true;
    const length = Number(e.endTick) / 20;
    const loop = e.isLoop === true || e.isLoop === 'true';
    const tracks = {};   // part -> prop -> [{ t, v: [value], easeOwn }]
    for (const m of e.moves || []) {
      for (const [k, node] of Object.entries(m)) {
        if (typeof node !== 'object' || node === null) continue;
        const part = version < 3 && k === 'torso' ? 'body' : k;
        for (const prop of PROPS) {
          if (node[prop] === undefined) continue;
          let v = Number(node[prop]);
          if (!degrees && !['x', 'y', 'z'].includes(prop)) v *= RAD;
          ((tracks[part] = tracks[part] || {})[prop] = tracks[part][prop] || []).push({ t: m.tick / 20, v: [v], own: m.easing || 'linear' });
        }
      }
    }
    for (const part of Object.values(tracks)) for (const keys of Object.values(part)) {
      keys.sort((a, b) => a.t - b.t);
      // a key's easing shapes the segment after it unless easeBeforeKeyframe; sampleTrack reads the ending key's ease
      keys.forEach((k, i) => { k.ease = easeBefore ? k.own : i > 0 ? keys[i - 1].own : 'linear'; });
    }
    const value = (part, prop, t, def) => {
      const keys = tracks[part] && tracks[part][prop];
      if (!keys || !keys.length) return def;
      const all = keys[0].t > 0 ? [{ t: 0, v: [def], ease: 'linear' }, ...keys] : keys;   // from the rest pose to the first key
      return P.sampleTrack(all, t)[0];
    };
    const times = P.sampleTimes(length);
    const frames = times.map((t) => {
      const f = {};
      const whole = P.partWorld(P.PIVOT.root, [value('body', 'pitch', t, 0), value('body', 'yaw', t, 0), value('body', 'roll', t, 0)], null);
      f.root = whole;
      for (const [src, bone] of Object.entries(PARTS)) {
        const d = DEFAULT_POS[src];
        const mc = [value(src, 'x', t, d[0]), value(src, 'y', t, d[1]), value(src, 'z', t, d[2])];
        const pos = [mc[0] - d[0], -(mc[1] - d[1]), mc[2] - d[2]];   // Blockbench offset of the ModelPart move
        const rot = [value(src, 'pitch', t, 0), value(src, 'yaw', t, 0), value(src, 'roll', t, 0)];
        f[bone] = P.compose(whole, P.partWorld(P.VANILLA_PIVOT[bone], rot, pos));
        const joint = JOINT[bone];
        const bend = value(src, 'bend', t, 0);
        if (joint && Math.abs(bend) > 1e-6) {
          const axis = value(src, 'axis', t, 0);
          // the lower half turns about an axis in the part's horizontal plane, `axis` degrees round from x (Minecraft space)
          const B = [W.eulerMatrix([0, axis, 0], 'ZYX'), W.eulerMatrix([bend, 0, 0], 'ZYX'), W.eulerMatrix([0, -axis, 0], 'ZYX')].reduce(W.mul3);
          const local = P.partWorld(P.PIVOT[joint], W.fromTrue(P.mcToTrue(B)), null);
          f[joint] = P.compose(f[bone], local);
        }
      }
      return f;
    });
    return { [name || json.name || 'emote']: P.worldToClip(frames, times, length, loop) };
  }

  const api = { importEmote };
  if (isNode) module.exports = api;
  else root.ImportEmote = api;
})(typeof window !== 'undefined' ? window : globalThis);
