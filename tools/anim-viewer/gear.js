/* Gear for the rig viewer: merge generated weapons and armour (tools/model-gen, src/main/art/blockbench/gear/) onto a
 * loaded Blockbench model, so retargeting, weapon IK and the renderer see one model.
 *
 *   attachGear(baseBb, items) -> new bb (the base is not changed)
 *     items: { bb, hand: 'right_item' }            a weapon: its grip (origin) goes to that bone's pivot
 *            { bb, attach: { group: bone, ... } }   armour: each group moves from its own pivot to the same-named
 *                                                  bone's pivot (or the bone `attach` names, e.g. boot armour -> shin)
 */
(function (root) {
  'use strict';

  function findGroup(nodes, name) {
    for (const n of nodes || []) {
      if (typeof n === 'string') continue;
      if (n.name === name) return n;
      const r = findGroup(n.children, name);
      if (r) return r;
    }
    return null;
  }

  /** Every element uuid under each top-level-or-nested group of a gear model, with that group's pivot. */
  function groupsOf(bb) {
    const out = [];
    (function walk(nodes) {
      for (const n of nodes || []) {
        if (typeof n === 'string') continue;
        out.push({ name: n.name, origin: (n.origin || [0, 0, 0]).map(Number), uuids: n.children.filter((c) => typeof c === 'string') });
        walk(n.children);
      }
    })(bb.outliner);
    return out;
  }

  function attachGear(baseBb, items) {
    const bb = JSON.parse(JSON.stringify(baseBb));
    const baseRes = bb.resolution || { width: 16, height: 16 };
    for (const t of bb.textures) { t.uv_width = t.uv_width || baseRes.width; t.uv_height = t.uv_height || baseRes.height; }
    items.forEach((item, n) => {
      const gear = item.bb, res = gear.resolution || { width: 16, height: 16 };
      const offset = bb.textures.length;
      for (const t of gear.textures || []) bb.textures.push(Object.assign({}, t, { uv_width: t.uv_width || res.width, uv_height: t.uv_height || res.height }));
      const els = {};
      for (const e of gear.elements || []) els[e.uuid] = e;
      for (const g of groupsOf(gear)) {
        if (!g.uuids.length) continue;
        const target = item.hand || (item.attach && item.attach[g.name]) || g.name;
        const bone = findGroup(bb.outliner, target);
        if (!bone) throw new Error(`gear ${gear.name}: the model has no bone ${target}`);
        const to = (bone.origin || [0, 0, 0]).map(Number);
        const shift = item.hand ? to : to.map((v, a) => v - g.origin[a]);
        for (const u of g.uuids) {
          const e = els[u];
          if (!e) continue;
          const c = JSON.parse(JSON.stringify(e));
          c.uuid = `${u}~gear${n}`;   // unique against the base model and the other gear
          c.name = `${gear.name}:${e.name}`;
          c.from = e.from.map((v, a) => v + shift[a]);
          c.to = e.to.map((v, a) => v + shift[a]);
          if (e.origin) c.origin = e.origin.map((v, a) => v + shift[a]);
          for (const f of Object.values(c.faces || {})) if (typeof f.texture === 'number') f.texture += offset;
          bb.elements.push(c);
          bone.children.push(c.uuid);
        }
      }
    });
    return bb;
  }

  const api = { attachGear };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.AnimGear = api;
})(typeof window !== 'undefined' ? window : globalThis);
