/* GeckoLib / Bedrock JSON writers for the NPC rig.
 *   geoJson(rig)          minecraft:geometry 1.12.0, one geometry (geometry.npc_biped), bones without cubes
 *   animationJson(clips)  Bedrock animation 1.8.0 from { name: Clip }
 * Clip = { length (s), loop, bones: { bone: { rotation?: Key[], position?: Key[] } } }, Key = { t, v: [x,y,z], lerp },
 * degrees and px in Blockbench conventions. lerp is 'linear' (omitted in the file), 'catmullrom' or 'step'.
 * Blockbench composes a bone's rotation as Rz·Ry·Rx; PulseLib's GeckoLib loader reads the file literally and composes
 * Rx·Ry·Rz (quaternion rotationXYZ, no axis flips), so rotation keys are written re-expressed in that order. */
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
  const RAD = Math.PI / 180;

  /** Row-major 3x3 rotation from Euler degrees: 'XYZ' = Rx·Ry·Rz (PulseLib), 'ZYX' = Rz·Ry·Rx (Blockbench). */
  function eulerMatrix(v, order) {
    const R = {
      X: (a) => [1, 0, 0, 0, Math.cos(a), -Math.sin(a), 0, Math.sin(a), Math.cos(a)],
      Y: (a) => [Math.cos(a), 0, Math.sin(a), 0, 1, 0, -Math.sin(a), 0, Math.cos(a)],
      Z: (a) => [Math.cos(a), -Math.sin(a), 0, Math.sin(a), Math.cos(a), 0, 0, 0, 1],
    };
    const angle = { X: v[0] * RAD, Y: v[1] * RAD, Z: v[2] * RAD };
    return order.split('').map((ax) => R[ax](angle[ax])).reduce(mul3);
  }

  function mul3(a, b) {
    const o = new Array(9);
    for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
    return o;
  }

  /** Blockbench (ZYX) Euler degrees as the XYZ Euler degrees of the same rotation. */
  function zyxToXyz(v) {
    const m = eulerMatrix(v, 'ZYX');
    const sb = Math.max(-1, Math.min(1, m[2]));
    const b = Math.asin(sb);
    let a, c;
    if (Math.abs(sb) < 0.999999) { a = Math.atan2(-m[5], m[8]); c = Math.atan2(-m[1], m[0]); }
    else { a = Math.atan2(m[7], m[4]); c = 0; }
    return [a / RAD, b / RAD, c / RAD];
  }

  function channel(keys, rotation) {
    const out = {};
    for (const k of keys) {
      const e = { vector: (rotation ? zyxToXyz(k.v) : k.v).map(round) };
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
        if (ch.rotation && ch.rotation.length) b.rotation = channel(ch.rotation, true);
        if (ch.position && ch.position.length) b.position = channel(ch.position, false);
        if (Object.keys(b).length) bones[bone] = b;
      }
      animations[name] = { loop: c.loop === 'hold' ? 'hold_on_last_frame' : !!c.loop, animation_length: round(c.length), bones };
    }
    return { format_version: '1.8.0', animations };
  }

  const api = { geoJson, animationJson, timeKey, eulerMatrix, zyxToXyz, mul3 };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.GeckoWrite = api;
})(typeof window !== 'undefined' ? window : globalThis);
