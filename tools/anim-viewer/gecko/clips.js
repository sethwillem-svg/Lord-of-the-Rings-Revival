/* Which of a race's clips an NPC can play with what it holds (the viewer's clip list), mirroring the game:
 * attacks fall back between classes as combat.AttackClips does; ranged, brace and shield clips come with their items.
 *   attackClass(names, cls)                      the class whose attack_<class>_N clips are played (after fallbacks)
 *   clipsFor(names, main, off, { leftHanded })   the clips, in file order; main 'all' lists everything */
(function (root) {
  'use strict';
  const FALLBACK = { dagger: 'sword', longsword: 'sword', axe: 'sword', spear: 'sword', bow: 'unarmed', longbow: 'unarmed',
    crossbow: 'unarmed', shield: 'unarmed', banner: 'sword' };
  const MOVEMENT = /^(idle(_\d+)?|walk|run|crouch_walk|swim|climb|ride_idle|ride_move|guard|march|hurt|taunt_\d+)$/;

  function attackClass(names, cls) {
    const has = (c) => names.includes(`attack_${c}_1`);
    for (let c = cls || 'unarmed'; c; c = FALLBACK[c]) {
      if (has(c)) return c;
      if (c === 'sword' || c === 'unarmed') break;
    }
    return has('sword') ? 'sword' : 'unarmed';
  }

  function clipsFor(names, main, off, opts = {}) {
    if (main === 'all') return names.slice();
    const attack = new RegExp(`^attack_${attackClass(names, main)}_\\d+${opts.leftHanded ? '_l' : ''}$`);
    const extra = new Set();
    if (main === 'bow' || main === 'longbow') extra.add('bow_aim').add('bow_release');
    if (main === 'crossbow') extra.add('crossbow_reload').add('crossbow_aim').add('crossbow_shoot');
    if (main === 'spear') extra.add('spear_brace');
    if (off === 'shield') extra.add('shield_brace').add('attack_shield_1');
    return names.filter((n) => MOVEMENT.test(n) || attack.test(n) || extra.has(n));
  }

  const api = { attackClass, clipsFor, FALLBACK };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.GeckoClips = api;
})(typeof window !== 'undefined' ? window : globalThis);
