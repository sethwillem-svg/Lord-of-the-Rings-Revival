/** Men: body ranges in whole pixels (16 px = 1 block) and colour choices. The reference biped. */
export const MAN = {
  id: 'man',
  /** Seeds committed to src/main/art/blockbench/man/: chosen to cover the range of bodies, hair and beards. */
  showcase: [7, 8, 13, 22, 24, 26, 29, 37],
  legs: [11, 13],
  torso: [11, 12],
  head: [8, 9],     // includes the 1 px gap the collar fills under the skull
  shoulders: [7, 8],   // tunic width: at most the hips (2 x legThick) plus 2
  depth: [4, 5],
  arm: [11, 12],
  armThick: [3, 4],
  legThick: [3, 3],   // slim: armour adds the bulk later
  skin: ['#f1d2b6', '#e3b48f', '#c99068', '#a8714c', '#7d5236', '#5a3a26'],
  hair: ['#1c1a18', '#3b2a1e', '#5a3d27', '#7a5232', '#8e4a2a', '#b08a52', '#d2b47a', '#8c8a86'],
  eye: ['#3a5a7a', '#4a6a3a', '#5a3d27', '#2a2622'],
};
