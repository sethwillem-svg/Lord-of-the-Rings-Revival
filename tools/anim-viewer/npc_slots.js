/* The NPC animation slots: animation name in the race files -> source clip (build_npc.js).
 * Entry: { source, clip, mirror?, loop?, hold? (seconds: a held pose of that frame), strides? (keep one of this many strides,
 * in phase with walk), races?: { race: entry } }. Every attack also gets a mirrored left-handed twin, <name>_l.
 * Sources: animationsplus (AP, MIT), mobends (MIT), seriousplayer (MIT), emotecraft (CC0); see geckolib/animations/CREDITS.md.
 * Names attack_<class>_N are the attack sets combat.AttackClips picks from (by weapon class). */
(function (root) {
  'use strict';
  const RACES = ['man', 'elf', 'dwarf', 'hobbit', 'orc'];
  const ap = (clip, extra) => ({ source: 'animationsplus', clip, ...extra });
  const mb = (clip, extra) => ({ source: 'mobends', clip, ...extra });
  const SLOTS = {
    // movement
    idle: ap('idle'), idle_2: ap('Idle_1', { loop: true }), idle_3: ap('Idle_2', { loop: true }), idle_4: ap('Idle_3', { loop: true }),
    walk: ap('walk'), run: ap('sprint', { strides: 3 }), crouch_walk: ap('crouchwalk'), swim: ap('swim'), climb: ap('climb'),
    // riding
    ride_idle: ap('sitHorse'), ride_move: ap('sitmoveHorse'),
    // attacks by weapon class
    attack_sword_1: ap('Sword_Swing_1R'), attack_sword_2: ap('Sword_Swing_2R'), attack_sword_3: ap('Sword_Swing_3R'), attack_sword_4: ap('Sword_Swing_4R'),
    attack_longsword_1: mb('slash_down'), attack_longsword_2: mb('slash_inward'), attack_longsword_3: mb('slash_outward'),
    attack_longsword_4: mb('slash_up'), attack_longsword_5: mb('slash_whirl'),
    attack_spear_1: ap('Spear_Swing_1R'), attack_spear_2: ap('Spear_Swing_2R'), attack_spear_3: ap('Spear_Swing_3R'),
    attack_dagger_1: ap('Assassin_MultiHitR'), attack_dagger_2: ap('Sword_Swing_2R'),
    attack_axe_1: ap('axe_1R'), attack_axe_2: ap('axe_2R'),
    attack_shield_1: ap('Warrior_bashR'),
    attack_unarmed_1: ap('attackR'), attack_unarmed_2: ap('attackL'),
    // ranged
    bow_aim: ap('bowR'), bow_release: ap('Bow_ShootR'),
    crossbow_reload: ap('loadR'), crossbow_aim: ap('crossR'), crossbow_shoot: ap('Cross_ShootR'),
    // formation stances (specs/addon/hiring-reputation.md)
    guard: ap('blockR', { loop: true }), shield_brace: ap('blockL', { loop: true }), spear_brace: ap('Spear_Swing_1R', { hold: 0 }), march: ap('walk'),
    // reactions and social
    hurt: ap('Fall_0'),
    taunt_1: ap('Taunt_0'), taunt_2: ap('Taunt_1'), taunt_3: ap('Taunt_2'), taunt_4: ap('Taunt_3'), taunt_5: ap('Taunt_4'), taunt_6: ap('Taunt_5'),
  };
  /** Race posture, added to every clip: hunch (upper x), arms out (arms z, away from the body), knee bend (knees x, thighs
   * half the other way so the feet stay under), stride (walk/run/march leg swing scale). Degrees. */
  const STYLE = {
    man: {},
    elf: { hunch: -8, armsOut: -5 },
    dwarf: { hunch: 9, armsOut: 11, kneeBend: 15 },
    hobbit: { kneeBend: 9, stride: 0.65 },
    orc: { hunch: 24, armsOut: 15, kneeBend: 18 },
  };
  const api = { RACES, SLOTS, STYLE };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.NpcSlots = api;
})(typeof window !== 'undefined' ? window : globalThis);
