/**
 * Men: body sizes in whole pixels (16 px = 1 block) and colour choices. Every man has the same standard body (32 px,
 * the vanilla player's height) so armour fits all of them; variety comes from faces, hair, beards, colours and outfits.
 * Sizes stay [min, max] ranges so another race can vary.
 */
export const MAN = {
  id: 'man',
  /** Seeds committed to src/main/art/blockbench/man/: chosen to cover the range of bodies, hair and beards. */
  showcase: [7, 8, 13, 22, 24, 26, 29, 37],
  legs: [12, 12],
  torso: [11, 11],
  head: [9, 9],     // includes the 1 px gap the collar fills under the skull
  shoulders: [8, 8],
  depth: [4, 4],
  arm: [12, 12],
  armThick: [3, 3],
  legThick: [3, 3],
  skin: ['#f1d2b6', '#e3b48f', '#c99068', '#a8714c', '#7d5236', '#5a3a26'],
  hair: ['#1c1a18', '#3b2a1e', '#5a3d27', '#7a5232', '#8e4a2a', '#b08a52', '#d2b47a', '#8c8a86'],
  eye: ['#3a5a7a', '#4a6a3a', '#5a3d27', '#2a2622'],
};
