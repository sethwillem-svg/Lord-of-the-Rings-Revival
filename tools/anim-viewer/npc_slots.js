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
    attack_longsword_1: ap('pickaxe_1R'), attack_longsword_2: ap('Spear_Swing_3R'), attack_longsword_3: ap('Spear_Swing_2R'),
    attack_spear_1: ap('Spear_Swing_2R'), attack_spear_2: ap('Spear_Swing_3R'),
    attack_dagger_1: ap('hoe_1R'), attack_dagger_2: ap('Wand_Wave_3R'), attack_dagger_3: ap('Sword_Swing_3R'),
    attack_axe_1: ap('axe_1R'), attack_axe_2: ap('pickaxe_1R'),
    attack_shield_1: ap('Warrior_bashR'),
    attack_unarmed_1: ap('attackR'), attack_unarmed_2: ap('Wand_Wave_3R'),
    // ranged
    bow_aim: ap('bowR'), bow_release: ap('Bow_ShootR'),
    crossbow_reload: ap('loadR'), crossbow_aim: ap('crossR'), crossbow_shoot: ap('Cross_ShootR'),
    // formation stances (specs/addon/hiring-reputation.md)
    // spear brace: the two-handed thrust held at its strike, spear levelled forward
    guard: ap('blockR', { loop: true }), shield_brace: ap('blockL', { loop: true }), spear_brace: ap('Spear_Swing_2R', { hold: 0.19 }), march: ap('walk'),
    // reactions and social
    hurt: ap('Fall_0'),
    taunt_1: ap('Taunt_0'), taunt_2: ap('Taunt_1'), taunt_3: ap('Taunt_2'), taunt_4: ap('Taunt_3'), taunt_5: ap('Taunt_4'), taunt_6: ap('Taunt_5'),
  };
  /** Race posture, added to every clip: hunch (upper x; the head lifts back by most of it, so eyes stay on the horizon), arms
   * forward (arms x: rounded shoulders), arms out (arms z, away from the body), knee bend (knees x, thighs half the other way
   * so the feet stay under), stride (walk/run/march leg swing scale). Degrees. */
  /* fight (attacks only; npc_slots tests in test/fighting.test.js): how each race swings, from animation craft —
   *   windup     the wind-up drawn further back (+) or kept short (-), as a share of its distance from the strike pose
   *   overswing  the follow-through carried past the strike (+) or reined in (-)
   *   twist      the torso's turn exaggerated (+): a body-driven swing
   *   extend     the weapon arm straighter at the strike (+) or kept compact (-)
   *   lean       degrees the upper body leans into the blow at the strike
   *   low        degrees the weapon arm strikes lower
   *   lead       seconds the hips lead the arm (+, skilled) or the arm lags the body (-, a brawler's throw)
   *   freeArm    { out: degrees held away from the body (+) or tucked (-), flail: share of the weapon arm's swing the free
   *              arm throws the other way } — not for two-handed weapons, whose free hand stays on the grip
   *   jitter     degrees every key wobbles by: no two swings alike
   * A swing that would no longer strike in front, would spin, or would lose a two-handed grip gets less of it. */
  const STYLE = {
    man: {},
    elf: { hunch: -8, armsOut: -5, fight: { overswing: -0.15, extend: 0.9, lean: 6, lead: 0.03, freeArm: { out: 35 } } },
    dwarf: { hunch: 9, armsOut: 11, kneeBend: 15, fight: { windup: 0.3, twist: 0.45, lean: 8, extend: -0.2 } },
    hobbit: { kneeBend: 9, stride: 0.65, fight: { windup: -0.35, low: 14, extend: -0.3, freeArm: { out: -6 } } },
    orc: { hunch: 12, armsForward: 8, armsOut: 12, kneeBend: 14,
      fight: { windup: 0.75, overswing: 0.25, lean: 10, lead: -0.05, freeArm: { out: 15, flail: 0.6 }, jitter: 5 } },
  };
  const api = { RACES, SLOTS, STYLE };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.NpcSlots = api;
})(typeof window !== 'undefined' ? window : globalThis);
