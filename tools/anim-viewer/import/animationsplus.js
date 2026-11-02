/* AnimationsPlus (emart126, MIT; library/animationsplus) onto the NPC rig.
 *   importAnimationsPlus(bbmodel, warn?)  { name: Clip } for every animation in the Blockbench model
 *   mirror(clip)                          left and right swapped (rotation y, z and position x negated), for left-hand play
 * The rig shares AnimationsPlus' space and pivots (gecko/rig.js), so values pass through unchanged. */
(function (root) {
  'use strict';

  const BONE_MAP = {
    Player: 'root', Upper: 'upper', Lower: 'lower', head: 'head', body: 'body',
    Arm_R: 'right_arm', Elbow_R: 'right_elbow', RightItemPivot: 'weapon_r',
    Arm_L: 'left_arm', Elbow_L: 'left_elbow', LeftItemPivot: 'weapon_l',
    Leg_R: 'right_leg', Knee_R: 'right_knee', Leg_L: 'left_leg', Knee_L: 'left_knee',
  };
  const CHANNELS = new Set(['rotation', 'position']);
  const LOOP = { loop: true, once: false, hold: 'hold' };

  function num(v, warn, where) {
    if (typeof v === 'number') return v;
    const s = String(v == null ? '' : v).trim();
    if (s === '') return 0;
    const n = Number(s);
    if (Number.isFinite(n)) return n;
    warn(`${where}: Molang "${s}" read as 0`);
    return 0;
  }

  function importAnimationsPlus(model, warn = (w) => console.warn(w)) {
    const out = {};
    for (const a of model.animations || []) {
      const bones = {};
      for (const an of Object.values(a.animators || {})) {
        const bone = an.type === 'bone' || an.type === undefined ? BONE_MAP[an.name] : undefined;
        if (!bone) continue;
        for (const k of an.keyframes || []) {
          if (!CHANNELS.has(k.channel)) continue;
          const d = k.data_points[0] || {};
          const where = `${a.name}/${an.name}/${k.channel}@${k.time}`;
          const ch = ((bones[bone] = bones[bone] || {})[k.channel] = bones[bone][k.channel] || []);
          ch.push({ t: k.time, v: [num(d.x, warn, where), num(d.y, warn, where), num(d.z, warn, where)], lerp: k.interpolation || 'linear' });
        }
      }
      for (const b of Object.values(bones)) for (const ch of Object.values(b)) ch.sort((x, y) => x.t - y.t);
      out[a.name] = { length: a.length, loop: a.loop in LOOP ? LOOP[a.loop] : false, bones };
    }
    return out;
  }

  const SWAP = { right_arm: 'left_arm', right_elbow: 'left_elbow', weapon_r: 'weapon_l', right_leg: 'left_leg', right_knee: 'left_knee' };
  for (const [r, l] of Object.entries(SWAP)) SWAP[l] = r;

  function mirror(clip) {
    const bones = {};
    for (const [bone, ch] of Object.entries(clip.bones)) {
      const b = {};
      if (ch.rotation) b.rotation = ch.rotation.map((k) => ({ t: k.t, v: [k.v[0], -k.v[1], -k.v[2]], lerp: k.lerp }));
      if (ch.position) b.position = ch.position.map((k) => ({ t: k.t, v: [-k.v[0], k.v[1], k.v[2]], lerp: k.lerp }));
      bones[SWAP[bone] || bone] = b;
    }
    return { length: clip.length, loop: clip.loop, bones };
  }

  const api = { importAnimationsPlus, mirror, BONE_MAP };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.ImportAnimationsPlus = api;
})(typeof window !== 'undefined' ? window : globalThis);
