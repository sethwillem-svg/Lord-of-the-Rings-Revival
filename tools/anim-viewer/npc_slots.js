/* The NPC animation slots: animation name in the race files -> source clip (build_npc.js).
 * Entry: { source, clip, mirror?, loop?, hold? (seconds: a held pose of that frame), strides? (keep one of this many strides,
 * in phase with walk), races?: { race: entry } }. Every attack also gets a mirrored left-handed twin, <name>_l.
 * Sources: animationsplus (AP, MIT), mobends (MIT), seriousplayer (MIT), emotecraft (CC0); see geckolib/animations/CREDITS.md.
 * Names attack_<class>_N are the attack sets combat.AttackClips picks from (by weapon class). */
(function (root) {
  'use strict';
  const RACES = ['man', 'elf', 'dwarf', 'hobbit', 'orc'];
  const ap = (clip, extra) => ({ source: 'animationsplus', clip, ...extra });
  const SLOTS = {
    // movement
    idle: ap('idle'), idle_2: ap('Idle_1', { loop: true }), idle_3: ap('Idle_2', { loop: true }),   // AP Idle_3 bends the elbows backwards
    walk: ap('walk'), run: ap('sprint', { strides: 3 }), crouch_walk: ap('crouchwalk'), swim: ap('swim'), climb: ap('climb'),
    // riding
    ride_idle: ap('sitHorse'), ride_move: ap('sitmoveHorse'),
    // attacks by weapon class
    // Chosen by measurement (test/weapons.test.js): each strike lands in front of the NPC, nothing spins, and two-handed
    // classes (longsword, spear, axe) keep the left hand on the grip.
    attack_sword_1: ap('Wand_Wave_1R'), attack_sword_2: ap('Sword_Swing_3R'), attack_sword_3: ap('Relik_Strike_2R'), attack_sword_4: ap('Relik_Strike_3R'),
    attack_longsword_1: ap('pickaxe_1R'), attack_longsword_2: ap('axe_1R'), attack_longsword_3: ap('Spear_Swing_2R'),
    attack_spear_1: ap('Spear_Swing_2R'), attack_spear_2: ap('Spear_Swing_3R'),
    attack_dagger_1: ap('hoe_1R'), attack_dagger_2: ap('Wand_Wave_3R'), attack_dagger_3: ap('Sword_Swing_3R'),
    attack_axe_1: ap('axe_1R'), attack_axe_2: ap('pickaxe_1R'),
    attack_shield_1: ap('Warrior_bashR'),
    attack_unarmed_1: ap('attackR'), attack_unarmed_2: ap('Wand_Wave_3R'),
    // ranged
    bow_aim: ap('bowR'), bow_release: ap('Bow_ShootR'),
    crossbow_reload: ap('loadR'), crossbow_aim: ap('crossR'), crossbow_shoot: ap('Cross_ShootR'),
    // formation stances (specs/addon/hiring-reputation.md)
    guard: ap('blockR', { loop: true }), shield_brace: ap('blockL', { loop: true }), spear_brace: ap('Spear_Swing_2R', { hold: 0.19 }),   // the two-handed thrust held at its strike: spear levelled forward march: ap('walk'),
    // reactions and social
    hurt: ap('Fall_0'),
    taunt_1: ap('Taunt_0'), taunt_2: ap('Taunt_1'), taunt_3: ap('Taunt_2'), taunt_4: ap('Taunt_3'), taunt_5: ap('Taunt_4'), taunt_6: ap('Taunt_5'),
  };
  /** Race posture, added to every clip: hunch (upper x; the head lifts back by most of it, so eyes stay on the horizon), arms
   * forward (arms x: rounded shoulders), arms out (arms z, away from the body), knee bend (knees x, thighs half the other way
   * so the feet stay under), stride (walk/run/march leg swing scale). Degrees. sloppy (attacks only): the body leads and the
   * arms lag by lag seconds, swings overshoot by overswing, and every key wobbles by up to jitter degrees — a brawler, not a
   * fencer. */
  const STYLE = {
    man: {},
    elf: { hunch: -8, armsOut: -5 },
    dwarf: { hunch: 9, armsOut: 11, kneeBend: 15 },
    hobbit: { kneeBend: 9, stride: 0.65 },
    orc: { hunch: 12, armsForward: 8, armsOut: 12, kneeBend: 14, sloppy: { lag: 0.05, overswing: 1.1, jitter: 5 } },
  };
  const api = { RACES, SLOTS, STYLE };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.NpcSlots = api;
})(typeof window !== 'undefined' ? window : globalThis);
