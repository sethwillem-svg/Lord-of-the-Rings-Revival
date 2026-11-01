/**
 * Body solver: picks a body inside a race table's ranges from a seeded random source and places every
 * biped bone of specs/core/rendering-animation.md section 5b. Feet at y = 0, front is -Z, the
 * character's right is -X. All lengths are whole pixels; pivots may sit on half pixels.
 */

/** [bone, parent], parents first. Anchor bones (*_armor, *_item, cape) never take cubes. */
export const BONES = [
  ['root', null], ['body', 'root'], ['torso', 'body'], ['head', 'torso'], ['head_armor', 'head'],
  ['right_arm', 'torso'], ['right_forearm', 'right_arm'], ['right_hand', 'right_forearm'], ['right_item', 'right_hand'],
  ['right_arm_armor', 'right_arm'],
  ['left_arm', 'torso'], ['left_forearm', 'left_arm'], ['left_hand', 'left_forearm'], ['left_item', 'left_hand'],
  ['left_arm_armor', 'left_arm'],
  ['chest_armor', 'torso'], ['cape', 'torso'],
  ['right_leg', 'body'], ['right_shin', 'right_leg'], ['right_leg_armor', 'right_leg'], ['right_boot_armor', 'right_leg'],
  ['left_leg', 'body'], ['left_shin', 'left_leg'], ['left_leg_armor', 'left_leg'], ['left_boot_armor', 'left_leg'],
];
export const ANCHORS = new Set(BONES.map(([n]) => n).filter((n) => /_armor$|_item$|^cape$/.test(n)));

export function solveBody(race, rng) {
  const build = rng.next();   // 0 slim .. 1 stocky: keeps widths consistent with each other
  const sized = (range) => {
    const [lo, hi] = range;
    return Math.min(hi, Math.max(lo, Math.round(lo + build * (hi - lo) + (rng.next() - 0.5))));
  };
  const legs = rng.range(race.legs), torso = rng.range(race.torso), head = rng.range(race.head);
  const arm = rng.range(race.arm);
  const shoulders = sized(race.shoulders), depth = sized(race.depth);
  const armThick = sized(race.armThick), legThick = sized(race.legThick);
  const colours = { skin: rng.pick(race.skin), hair: rng.pick(race.hair), eye: rng.pick(race.eye) };
  return { race: race.id, build, ...layoutBody({ legs, torso, head, shoulders, depth, arm, armThick, legThick }), colours };
}

/** The race's largest body (every range at its maximum): what armour is sized to. */
export function largestBody(race) {
  const max = (k) => race[k][1];
  return { race: race.id, build: 1, ...layoutBody({ legs: max('legs'), torso: max('torso'), head: max('head'), shoulders: max('shoulders'),
    depth: max('depth'), arm: max('arm'), armThick: max('armThick'), legThick: max('legThick') }), colours: {} };
}

/** Derived lengths and the pivot of every spec bone from the main body sizes. */
export function layoutBody({ legs, torso, head, shoulders, depth, arm, armThick, legThick }) {
  const thigh = Math.ceil(legs / 2), shin = legs - thigh;
  const hand = 2, upperArm = Math.ceil(arm / 2), forearm = arm - upperArm - hand;
  const top = legs + torso;                     // shoulder line
  const ax = shoulders / 2 + armThick / 2, lx = legThick / 2;
  const sy = top - armThick / 2;                // shoulder joint
  const p = {
    root: [0, 0, 0], body: [0, legs, 0], torso: [0, legs, 0], head: [0, top, 0],
    chest_armor: [0, legs, 0], cape: [0, top, depth / 2],
  };
  p.head_armor = p.head;
  for (const [side, s] of [['right', -1], ['left', 1]]) {
    p[`${side}_arm`] = [s * ax, sy, 0];
    p[`${side}_arm_armor`] = p[`${side}_arm`];
    p[`${side}_forearm`] = [s * ax, top - upperArm, 0];
    p[`${side}_hand`] = [s * ax, top - upperArm - forearm, 0];
    p[`${side}_item`] = [s * ax, top - arm, 0];
    p[`${side}_leg`] = [s * lx, legs, 0];
    p[`${side}_leg_armor`] = p[`${side}_leg`];
    p[`${side}_shin`] = [s * lx, shin, 0];
    p[`${side}_boot_armor`] = p[`${side}_shin`];
  }
  return {
    height: legs + torso + head,
    legs, thigh, shin, torso, head, shoulders, depth, arm, upperArm, forearm, hand, armThick, legThick,
    pivots: p,
  };
}
