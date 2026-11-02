/* GeckoLib / Bedrock JSON writers for the NPC rig.
 *   geoJson(rig)          minecraft:geometry 1.12.0, one geometry (geometry.npc_biped), bones without cubes
 *   animationJson(clips)  Bedrock animation 1.8.0 from { name: Clip }
 * Clip = { length (s), loop, bones: { bone: { rotation?: Key[], position?: Key[] } } }, Key = { t, v: [x,y,z], lerp },
 * degrees and px in Blockbench conventions. lerp is 'linear' (omitted in the file), 'catmullrom' or 'step'. */
(function (root) {
  'use strict';

  function geoJson(rig) {
    return {
      format_version: '1.12.0',
      'minecraft:geometry': [{
        description: { identifier: 'geometry.npc_biped', texture_width: 64, texture_height: 64, visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1, 0] },
        bones: rig.map((b) => (b.parent ? { name: b.name, parent: b.parent, pivot: b.pivot.slice() } : { name: b.name, pivot: b.pivot.slice() })),
      }],
    };
  }

  /** A key time as Bedrock writes it: "0.0", "0.5", "1.0417". */
  function timeKey(t) {
    const s = String(+t.toFixed(4));
    return s.includes('.') ? s : s + '.0';
  }

  const round = (x) => +x.toFixed(4);

  function channel(keys) {
    const out = {};
    for (const k of keys) {
      const e = { vector: k.v.map(round) };
      if (k.lerp && k.lerp !== 'linear') e.lerp_mode = k.lerp;
      out[timeKey(k.t)] = e;
    }
    return out;
  }

  function animationJson(clips) {
    const animations = {};
    for (const [name, c] of Object.entries(clips)) {
      const bones = {};
      for (const [bone, ch] of Object.entries(c.bones)) {
        const b = {};
        if (ch.rotation && ch.rotation.length) b.rotation = channel(ch.rotation);
        if (ch.position && ch.position.length) b.position = channel(ch.position);
        if (Object.keys(b).length) bones[bone] = b;
      }
      animations[name] = { loop: !!c.loop, animation_length: round(c.length), bones };
    }
    return { format_version: '1.8.0', animations };
  }

  const api = { geoJson, animationJson, timeKey };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.GeckoWrite = api;
})(typeof window !== 'undefined' ? window : globalThis);
