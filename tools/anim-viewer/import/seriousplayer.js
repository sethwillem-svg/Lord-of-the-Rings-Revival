/* Serious Player Animations (McVader, MIT; library/seriousplayer): playerAnimator's GeckoLib JSON, independent vanilla parts.
 *   importSeriousPlayer(json)  { name: Clip } sampled at 20 Hz (easing kept)
 * Bones: head, torso (the vanilla body part), rightArm, leftArm, rightLeg, leftLeg, body (the whole player -> root),
 * rightItem / leftItem (the held item, a nudge on weapon_r / weapon_l). File values are Blockbench values about the vanilla
 * pivots, so each part's world transform is rebuilt and re-expressed on the rig's hierarchy (import/parts.js). */
(function (root) {
  'use strict';
  const isNode = typeof module !== 'undefined' && module.exports;
  const P = isNode ? require('./parts.js') : root.ImportParts;

  const PARTS = { head: 'head', torso: 'body', rightArm: 'right_arm', leftArm: 'left_arm', rightLeg: 'right_leg', leftLeg: 'left_leg' };
  const ITEMS = { rightItem: ['weapon_r', 'right_elbow'], leftItem: ['weapon_l', 'left_elbow'] };

  function track(channel) {
    if (!channel) return [];
    if (Array.isArray(channel)) return [{ t: 0, v: channel.map(Number), ease: 'linear' }];
    if (channel.vector) return [{ t: 0, v: channel.vector.map(Number), ease: 'linear' }];
    return Object.entries(channel).map(([time, k]) => {
      const v = Array.isArray(k) ? k : k.vector || (k.post && (k.post.vector || k.post)) || (k.pre && (k.pre.vector || k.pre));
      const lerp = !Array.isArray(k) && k.lerp_mode === 'step' ? 'constant' : null;
      return { t: Number(time), v: v.map(Number), ease: lerp || (!Array.isArray(k) && k.easing) || 'linear' };
    }).sort((a, b) => a.t - b.t);
  }

  function importSeriousPlayer(json) {
    const out = {};
    for (const [name, a] of Object.entries(json.animations || {})) {
      const length = Number(a.animation_length || 0.05);
      const loop = a.loop === true ? true : a.loop === 'hold_on_last_frame' ? 'hold' : false;
      const tracks = {};
      for (const [bone, ch] of Object.entries(a.bones || {})) tracks[bone] = { rot: track(ch.rotation), pos: track(ch.position) };
      const at = (bone, kind, t) => (tracks[bone] && P.sampleTrack(tracks[bone][kind], t)) || null;
      const times = P.sampleTimes(length);
      const frames = times.map((t) => {
        const f = {};
        const whole = P.partWorld(P.PIVOT.root, at('body', 'rot', t), null);
        f.root = whole;
        for (const [src, bone] of Object.entries(PARTS)) {
          f[bone] = P.compose(whole, P.partWorld(P.VANILLA_PIVOT[bone], at(src, 'rot', t), at(src, 'pos', t)));
        }
        for (const [src, [bone, holder]] of Object.entries(ITEMS)) {
          if (!tracks[src]) continue;
          const arm = f[holder.replace('elbow', 'arm')];
          f[bone] = P.compose(arm, P.partWorld(P.PIVOT[bone], at(src, 'rot', t), at(src, 'pos', t)));
        }
        return f;
      });
      out[name] = P.worldToClip(frames, times, length, loop);
    }
    return out;
  }

  const api = { importSeriousPlayer };
  if (isNode) module.exports = api;
  else root.ImportSeriousPlayer = api;
})(typeof window !== 'undefined' ? window : globalThis);
