/**
 * .bbmodel output in the Blockbench 4.10 layout (bone data inline in the outliner), the layout the orc
 * packs use and the mocap viewer reads. The toolkit writes the Blockbench 5 layout, where outliner nodes
 * carry only uuid and children and the bone data lives in a separate `groups` list.
 */
import { toBbmodel } from '../vendor/bbmt/dist/index.js';

export function toLegacyBbmodel(model, options = {}) {
  const doc = toBbmodel(model, options);
  const groups = new Map((doc.groups || []).map((g) => [g.uuid, g]));
  const inline = (node) => {
    if (typeof node === 'string') return node;
    const g = groups.get(node.uuid) || {};
    const { children: _ignored, ...fields } = g;
    return { ...fields, ...node, children: node.children.map(inline) };
  };
  doc.outliner = doc.outliner.map(inline);
  delete doc.groups;
  doc.meta = { ...doc.meta, format_version: '4.10' };
  return doc;
}

/** Deterministic file text: same model, same bytes. */
export const serializeLegacy = (model, options) => JSON.stringify(toLegacyBbmodel(model, options));
