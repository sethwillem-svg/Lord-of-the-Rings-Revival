'use strict';
/* A simple flat bow on the NPC rig, for the stance checks (test/lib/stance.js) until the bow and crossbow work brings the
 * game's own bow geometry: a stand-in, our own, made only of the facts a stance needs. Rig space (gecko/rig.js): px, y up,
 * facing -z; points at rest in the holding hand's weapon bone frame, so a bone's world transform poses them.
 *  - The grip is in the fist (the weapon bone's pivot, as every held item is seated: gecko/strike.js FIST).
 *  - The bow lies square to the forearm, its limbs along the forearm's front (rest -z, the upper limb that way): with the
 *    forearm held out level in front, as when aiming, the bow stands upright. Its length is the class length
 *    (weapon_grips.json bow, pommel to tip as for every item: limb tip to limb tip here).
 *  - The string runs between the limb tips BRACE px behind the grip, toward the archer (rest +y: up the forearm, toward the
 *    elbow), the brace height of a bow about a tenth of its length. */
const { FIST } = require('../../gecko/strike.js');

const BRACE = 2;   // px: about a tenth of a 1.2-block (19.2 px) bow
const AXIS = [0, 0, -1];   // the upper limb's direction from the grip (rest, weapon frame)

/** The bow's rest points in `side`'s weapon frame for a bow `blocks` long: { grip, top, bottom, string: [top end, bottom
 *  end] } (the limb tips sit BRACE px toward the string from the grip's plane, the string's ends on them). */
function bow(side = 'right', blocks = 1.2) {
  const g = FIST[side], h = (blocks * 16) / 2;
  const at = (s, back) => [g[0] + AXIS[0] * s, g[1] + AXIS[1] * s + back, g[2] + AXIS[2] * s];
  return { grip: g.slice(), top: at(h, BRACE), bottom: at(-h, BRACE), string: [at(h, BRACE), at(-h, BRACE)] };
}

module.exports = { bow, BRACE, AXIS };
