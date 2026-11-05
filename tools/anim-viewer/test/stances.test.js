'use strict';
/* Formation stances (specs/addon/npc-formation-stances.md): every hard rule of the spec's table on every race's stance
 * clips and their shield versions, as the game ships them, measured with test/lib/stance.js (which the build's pick uses
 * too). One test per row of the table; each failure names the race, the clip and the measure. The picks the build makes
 * are recorded in npc_slots.js (STANCE_PICKS), checked against the build here. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { RACES, STYLE, STANCE_POOL, STANCE_PICKS } = require('../npc_slots.js');
const ST = require('./lib/stance.js');
const { shipped } = require('./lib/shipped.js');   // the files the game ships

const anim = (race, name) => shipped[race].animations.animations[name];
/** Every stance clip of every race, judged once: { race, name, fails } (a missing clip fails every row). */
const judged = [];
for (const race of RACES) {
  const ctx = { idle: anim(race, 'idle'), walk: anim(race, 'walk'), hunch: (STYLE[race] && STYLE[race].hunch) || 0 };
  for (const name of ST.CLIPS) {
    const a = anim(race, name);
    judged.push({ race, name, missing: !a, ...(a ? ST.judge(name, a, { ...ctx, key: `${race}/${name}` }) : { fails: [] }) });
  }
}
/** Fails with the first 15 failures of a row, and how many clips failed it of how many judged. */
function row(rule, applies = () => true) {
  const bad = [], seen = judged.filter((j) => applies(j));
  for (const j of seen) {
    if (j.missing) { bad.push(`${j.race} ${j.name}: no such clip`); continue; }
    const f = j.fails.filter((x) => x.rule === rule);
    if (f.length) bad.push(`${j.race} ${j.name}: ${f.map((x) => x.msg).join('; ')}`);
  }
  assert.deepEqual(bad.slice(0, 15), [], `${bad.length}/${seen.length} clips fail`);
}
const slot = (j) => ST.slotOf(j.name);
const still = (j) => ST.SLOT[slot(j)].still;

test('every race file has every stance slot and its shield version', () => {
  assert.deepEqual(judged.filter((j) => j.missing).map((j) => `${j.race} ${j.name}`), []);
});
test('stances loop without a pop: every bone within 0.5 px end to start', () => row('loop'));
test('still stances keep their feet planted: each foot moves ≤ 0.5 px over the loop, the lower foot -1.5..3.5 px of the floor', () => row('feet', still));
test('still stances face the front: the chest turned at most 15° from the facing', () => row('facing', still));
test('still stances are steady but alive: the weapon tip sways 0.2-2 px over the loop', () => row('sway', still));
test('stances keep the arms in the shoulder’s range (10° slack) and out of the body (0.5 px)', () => row('anatomy'));
test('stances keep the blade out of the body and head, and above the floor', () => row('blade'));
test('stances keep the head level: within 30° of the horizon', () => row('head'));
test('guard: tip in front of the chest (z ≤ -4 px), above the hips, within 45° of straight ahead, for every class that plays it; knees bent 10-40°', () => row('guard', (j) => slot(j) === 'guard'));
test('guard_spear: shaft 20-60° above level, tip forward; the lower hand at the waist or above; both hands within 2.5 px of the shaft (no shield)', () => row('guard_spear', (j) => slot(j) === 'guard_spear'));
test('guard_bow: bow upright within 30°, held below the chin; the drawing hand within 6 px of the string', () => row('guard_bow', (j) => slot(j) === 'guard_bow'));
test('with a shield: the plate faces the front within 35°, covers the chest, leaves the face clear, and the weapon arm is outside it', () => row('shield', (j) => ST.shieldOf(j.name)));
test('shield_brace: hips ≥ 1.5 px lower than idle, lead foot ≥ 3 px ahead of the other', () => row('shield_brace', (j) => /^shield_brace/.test(j.name)));
test('shield_brace_spear: shaft within 15° of level, tip ≥ 20 px in front of the shield’s face, over its top edge', () => row('shield_brace_spear', (j) => j.name === 'shield_brace_spear'));
test('spear_brace: shaft 5-25° above level, tip ≥ 28 px in front, both hands on the shaft; hips ≥ 2 px lower than idle, feet ≥ 4 px apart', () => row('spear_brace', (j) => j.name === 'spear_brace'));
test('march: in phase with walk, torso within 8° of the race’s upright, arms swing ≤ 60% of walk’s, weapon tip ≤ 4 px a stride against the chest, no foot skate beyond walk’s', () => row('march', (j) => !still(j)));
test('march_spear: shaft within 20° of vertical, tip up, butt below the hip', () => row('march_spear', (j) => slot(j) === 'march_spear'));
test('march_bow: bow in the hand at the side, limb axis within 30° of vertical', () => row('march_bow', (j) => slot(j) === 'march_bow'));

test('each pick is the build’s, as npc_slots.js STANCE_PICKS records it, and passed every rule in the build’s own judging', () => {
  const { built } = require('./lib/built.js');   // every race as the build makes it today (cached by its inputs)
  const bad = [];
  for (const race of RACES) for (const s of Object.keys(STANCE_POOL)) {
    const p = built[race].clips[s].pick, want = STANCE_PICKS[s] && STANCE_PICKS[s][race];
    if (!p || p.id !== want) bad.push(`${race} ${s}: the build picks ${p && p.id}, npc_slots.js records ${want}`);
    else if (!p.passes) bad.push(`${race} ${s}: no candidate passed, ${p.id} ships`);
  }
  assert.deepEqual(bad, []);
});
