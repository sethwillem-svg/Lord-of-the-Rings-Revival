'use strict';
/* Parked checks as ratchets (specs/addon/npc-attack-tempo.md, "Parked / known gaps"). Each list names the attacks known to
 * fail one check today (by race; T2 also names the pose pair). The check fails on any other attack that fails it, and on a
 * listed attack that now passes ("now passes — remove it from the list"), so a list only ever shrinks. Each list carries
 * a one-line reason with its counts, checked against the list. RATCHET_DUMP=1 prints each check's failures as keys, to
 * rebuild a list after a deliberate change. */
const assert = require('node:assert/strict');

const KNOWN = {
  T1: {
    why: 'T1: 54/80 fail; man 10, elf 16, dwarf 8, hobbit 12, orc 8 — no slow apex to hold (elves have no tame; the build drops it on a fast apex or a wind-up under 0.05 s): needs authored anticipation/settle key poses',
    fails: {
      man: ['attack_sword_2', 'attack_sword_3', 'attack_longsword_1', 'attack_longsword_3', 'attack_spear_1',
        'attack_dagger_1', 'attack_dagger_2', 'attack_axe_1', 'attack_unarmed_1', 'attack_unarmed_2'],
      elf: ['attack_sword_1', 'attack_sword_2', 'attack_sword_3', 'attack_sword_4', 'attack_longsword_1',
        'attack_longsword_2', 'attack_longsword_3', 'attack_spear_1', 'attack_spear_2', 'attack_dagger_1',
        'attack_dagger_2', 'attack_axe_1', 'attack_axe_2', 'attack_shield_1', 'attack_unarmed_1', 'attack_unarmed_2'],
      dwarf: ['attack_sword_1', 'attack_sword_4', 'attack_longsword_1', 'attack_dagger_1', 'attack_dagger_2',
        'attack_axe_1', 'attack_unarmed_1', 'attack_unarmed_2'],
      hobbit: ['attack_sword_2', 'attack_sword_3', 'attack_sword_4', 'attack_longsword_1', 'attack_longsword_3',
        'attack_spear_1', 'attack_dagger_1', 'attack_dagger_2', 'attack_axe_1', 'attack_axe_2', 'attack_unarmed_1',
        'attack_unarmed_2'],
      orc: ['attack_sword_2', 'attack_sword_3', 'attack_longsword_1', 'attack_spear_1', 'attack_spear_2',
        'attack_axe_1', 'attack_axe_2', 'attack_unarmed_1'],
    },
  },
  T2: {
    why: 'T2: 44 fail (pose pairs); man 11, elf 9, dwarf 5, hobbit 12, orc 7 — nearly all hit-end, the follow-through ends close to the hit: needs authored anticipation/settle key poses',
    fails: {
      man: ['attack_sword_4 hit-end', 'attack_longsword_1 hit-end', 'attack_longsword_2 hit-end',
        'attack_longsword_3 hit-end', 'attack_spear_1 hit-end', 'attack_spear_2 hit-end', 'attack_dagger_1 hit-end',
        'attack_axe_1 hit-end', 'attack_axe_2 hit-end', 'attack_unarmed_1 hit-end', 'attack_unarmed_2 hit-end'],
      elf: ['attack_sword_3 apex-hit', 'attack_sword_4 hit-end', 'attack_longsword_1 hit-end',
        'attack_longsword_2 hit-end', 'attack_longsword_3 hit-end', 'attack_spear_2 hit-end',
        'attack_dagger_1 hit-end', 'attack_axe_1 hit-end', 'attack_axe_2 hit-end'],
      dwarf: ['attack_longsword_2 hit-end', 'attack_longsword_3 hit-end', 'attack_spear_1 hit-end',
        'attack_spear_2 hit-end', 'attack_axe_2 hit-end'],
      hobbit: ['attack_sword_3 hit-end', 'attack_sword_4 hit-end', 'attack_longsword_1 hit-end',
        'attack_longsword_2 hit-end', 'attack_longsword_3 hit-end', 'attack_spear_1 hit-end',
        'attack_spear_2 hit-end', 'attack_dagger_1 hit-end', 'attack_axe_1 hit-end', 'attack_axe_2 hit-end',
        'attack_unarmed_1 hit-end', 'attack_unarmed_2 hit-end'],
      orc: ['attack_sword_4 hit-end', 'attack_longsword_1 hit-end', 'attack_longsword_2 hit-end',
        'attack_longsword_3 hit-end', 'attack_spear_1 hit-end', 'attack_axe_1 hit-end', 'attack_axe_2 hit-end'],
    },
  },
  T3: {
    why: 'T3: 5/80 fail; man 2, elf 1, hobbit 2 — Sword_Swing_3R has no slow stretch',
    fails: {
      man: ['attack_sword_2', 'attack_dagger_2'],
      elf: ['attack_sword_2'],
      hobbit: ['attack_sword_2', 'attack_dagger_2'],
    },
  },
  T5: {
    why: 'T5: 52/80 fail; man 10, elf 8, dwarf 7, hobbit 12, orc 15 — the strike takes too much of the swing or too little of its path: needs authored anticipation/settle key poses',
    fails: {
      man: ['attack_sword_1', 'attack_sword_2', 'attack_longsword_1', 'attack_longsword_3', 'attack_spear_1',
        'attack_dagger_1', 'attack_dagger_2', 'attack_axe_1', 'attack_unarmed_1', 'attack_unarmed_2'],
      elf: ['attack_sword_1', 'attack_sword_2', 'attack_longsword_3', 'attack_spear_1', 'attack_dagger_1',
        'attack_dagger_2', 'attack_unarmed_1', 'attack_unarmed_2'],
      dwarf: ['attack_sword_4', 'attack_longsword_1', 'attack_dagger_1', 'attack_dagger_2', 'attack_axe_1',
        'attack_unarmed_1', 'attack_unarmed_2'],
      hobbit: ['attack_sword_1', 'attack_sword_2', 'attack_sword_3', 'attack_longsword_1', 'attack_longsword_3',
        'attack_spear_1', 'attack_dagger_1', 'attack_dagger_2', 'attack_axe_1', 'attack_axe_2', 'attack_unarmed_1',
        'attack_unarmed_2'],
      orc: ['attack_sword_1', 'attack_sword_2', 'attack_sword_3', 'attack_longsword_1', 'attack_longsword_2',
        'attack_longsword_3', 'attack_spear_1', 'attack_spear_2', 'attack_dagger_1', 'attack_dagger_2',
        'attack_axe_1', 'attack_axe_2', 'attack_shield_1', 'attack_unarmed_1', 'attack_unarmed_2'],
    },
  },
  T6: {
    why: 'T6: 5/80 fail; man 1, elf 1, dwarf 1, hobbit 1, orc 1 — Warrior_bashR (attack_shield_1): its recovery pauses, then pulls back',
    fails: {
      man: ['attack_shield_1'],
      elf: ['attack_shield_1'],
      dwarf: ['attack_shield_1'],
      hobbit: ['attack_shield_1'],
      orc: ['attack_shield_1'],
    },
  },
  T7: {
    why: 'T7: 2/12 fail; man 1, orc 1 — wind-up too short against the recovery (Spear_Swing_2R, Relik_Strike_3R)',
    fails: {
      man: ['attack_longsword_3'],
      orc: ['attack_sword_4'],
    },
  },
  T9: {
    why: 'T9: 2/4 fail; orc 2 — on Wand_Wave_1R and Sword_Swing_3R the end pose is not off balance enough; parked for the maintainer',
    fails: {
      orc: ['attack_sword_1', 'attack_sword_2'],
    },
  },
  orcArc: {
    why: 'orcArc: 3/7 fail; orc 3 — the strike arc is no wider than a man’s on these swings; parked for the maintainer',
    fails: {
      orc: ['attack_sword_1', 'attack_sword_4', 'attack_shield_1'],
    },
  },
};

/** The listed failures of a check as keys: race/attack (T2: race/attack pair). */
const listed = (id) => Object.entries(KNOWN[id].fails).flatMap(([race, xs]) => xs.map((x) => `${race}/${x}`));

/**
 * Holds a parked check to its list. bad: the failures found now, [{ key, msg }] with key as listed() gives it. Fails on a
 * failure not on the list and on a listed one that no longer fails; the reason's counts must match the list.
 */
function ratchet(id, bad) {
  const k = KNOWN[id], known = listed(id), now = new Set(bad.map((b) => b.key));
  if (process.env.RATCHET_DUMP) console.error(`RATCHET ${id} ${JSON.stringify(bad.map((b) => b.key))}`);
  const total = /^\S+ (\d+)(?:\/\d+)? fail/.exec(k.why);
  assert.ok(total && Number(total[1]) === known.length, `${id}: the reason says ${total && total[1]} fail, the list has ${known.length}`);
  for (const [, race, n] of k.why.matchAll(/\b(man|elf|dwarf|hobbit|orc) (\d+)\b/g)) {
    assert.equal((k.fails[race] || []).length, Number(n), `${id}: the reason counts ${n} for ${race}, the list has ${(k.fails[race] || []).length}`);
  }
  const fresh = bad.filter((b) => !known.includes(b.key)).map((b) => b.msg);
  const fixed = known.filter((x) => !now.has(x)).map((x) => `${x}: now passes — remove it from the ${id} list (test/lib/known.js)`);
  assert.deepEqual([...fresh.slice(0, 15), ...fixed], [], `${id}: ${fresh.length} failures not on the list, ${fixed.length} listed that now pass`);
}

module.exports = { KNOWN, listed, ratchet };
