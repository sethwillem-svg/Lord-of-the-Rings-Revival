'use strict';
/* Race-against-man comparisons on the same swing (fighting.test.js, timing.test.js). */
const assert = require('node:assert/strict');
const { shipped: built } = require('./shipped.js');
const { KEEP } = require('../../npc_slots.js');

/** One-handed attacks: every style applies in full. */
const ATTACK = /^attack_(sword|dagger|unarmed)_\d+$/;

/** The race's attacks that swing the same clip as a man's, with at least KEEP (60%) of the race's style kept (hits.json). */
function pairs(race, oneHanded = true) {
  const man = built.man.hits, h = built[race].hits;
  return Object.keys(h).filter((n) => (!oneHanded || ATTACK.test(n)) && man[n] && h[n].clip === man[n].clip && h[n].style >= KEEP);
}
/** At least 3 in 4 of the race's paired attacks (those `only` keeps) show the difference; names the ones that do not. */
function mostly(race, label, holds, oneHanded = true, only = () => true) {
  const ps = pairs(race, oneHanded).filter(only), fails = ps.filter((n) => !holds(n));
  assert.ok(ps.length >= 3, `${race}: only ${ps.length} attacks to compare`);
  assert.ok(fails.length <= ps.length / 4, `${race} ${label} on only ${ps.length - fails.length}/${ps.length}; not: ${fails.join(', ')}`);
}

module.exports = { ATTACK, pairs, mostly };
