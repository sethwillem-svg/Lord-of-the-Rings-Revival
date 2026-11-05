/* The NPC animation slots: animation name in the race files -> source clip (build_npc.js).
 * Entry: { source, clip, mirror?, loop?, hold? (seconds: a held pose of that frame), strides? (keep one of this many strides,
 * in phase with walk), races?: { race: entry } }. Every attack also gets a mirrored left-handed twin, <name>_l.
 * Sources: animationsplus (AP, MIT), mobends (MIT), seriousplayer (MIT), emotecraft (CC0); see geckolib/animations/CREDITS.md.
 * Names attack_<class>_N are the attack sets combat.AttackClips picks from (by weapon class). */
(function (root) {
  'use strict';
  const RACES = ['man', 'elf', 'dwarf', 'hobbit', 'orc'];
  const ap = (clip, extra) => ({ source: 'animationsplus', clip, ...extra });
  const stance = { stance: true };
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
    attack_dagger_1: ap('Wand_Wave_3R'), attack_dagger_2: ap('Sword_Swing_3R'),   // AP hoe_1R whips back up after its hit
    attack_axe_1: ap('axe_1R'), attack_axe_2: ap('pickaxe_1R'),
    attack_shield_1: ap('Warrior_bashR'),
    attack_unarmed_1: ap('attackR'), attack_unarmed_2: ap('Wand_Wave_3R'),
    // ranged
    bow_aim: ap('bowR'), bow_release: ap('Bow_ShootR'),
    crossbow_reload: ap('loadR'), crossbow_aim: ap('crossR'), crossbow_shoot: ap('Cross_ShootR'),
    // formation stances (specs/addon/hiring-reputation.md, specs/addon/npc-formation-stances.md): the build's pick from
    // STANCE_POOL (below), recorded in STANCE_PICKS
    guard: stance, guard_spear: stance, guard_bow: stance, shield_brace: stance, shield_brace_spear: stance, spear_brace: stance,
    march: stance, march_spear: stance, march_bow: stance,
    // reactions and social
    hurt: ap('Fall_0'),
    taunt_1: ap('Taunt_0'), taunt_2: ap('Taunt_2'), taunt_3: ap('Taunt_3'), taunt_4: ap('Taunt_4'), taunt_5: ap('Taunt_5'),   // AP Taunt_1 flips its knees through 360°
  };
  /** Race posture, added to every clip: hunch (upper x; the head lifts back by most of it, so eyes stay on the horizon), arms
   * forward (arms x: rounded shoulders), arms out (arms z, away from the body), knee bend (knees x, thighs half the other way
   * so the feet stay under), stride (walk/run/march leg swing scale). Degrees. */
  /* fight (attacks only; npc_slots tests in test/fighting.test.js): how each race swings, from animation craft —
   *   windup     the wind-up drawn further back (+) or kept short (-), as a share of its distance from the strike pose
   *   windupAfter  the same push again after posture, only as far as keeps the swing's starting tip within 150° of the front
   *   overswing  the follow-through carried past the strike (+) or reined in (-)
   *   twist      the torso's turn exaggerated (+): a body-driven swing
   *   extend     the weapon arm straighter at the strike (+) or kept compact (-)
   *   lean       degrees the upper body leans into the blow at the strike
   *   low        degrees the weapon arm strikes lower
   *   lead       seconds the hips lead the arm (+, skilled) or the arm lags the body (-, a brawler's throw)
   *   freeArm    { out: degrees held away from the body (+) or tucked (-), flail: share of the weapon arm's swing the free
   *              arm throws the other way, at most 120° (build_npc.js FLAIL_MAX), front to back only as far as the shoulder
   *              takes it and the rest flung out to the side, the elbow opening (build_npc.js flung) } — not for
   *              two-handed weapons, whose free hand stays on the grip
   *   jitter     degrees every key wobbles by: no two swings alike
   * A swing that would no longer strike in front, would spin, or would lose a two-handed grip gets less of it. */
  /* tempo (attacks only; specs/addon/npc-attack-tempo.md, checked in test/fighting.test.js and test/timing.test.js): time
   * scales for the wind-up (from the lead-in's end to the apex, the slowest turn of the tip before the hit), the strike (apex
   * to 0.05 s after the hit) and the recovery; tame: seconds held at the apex before the strike, dropped where the tip is
   * still fast at the apex or the wind-up is under 0.05 s; tsume (p ≥ 1, 1 is linear): the wind-up slowing into
   * the apex, the strike bursting to the hit, the recovery leaving fast and settling slowly; ready: the attack ends by
   * easing back into the ready stance (the idle pose). prefer: 'overhead' picks, for each weapon attack (not shield bashes
   * or unarmed blows), the class's clip whose wind-up lifts the weapon highest. */
  /* Coupling: man.tempo is also every race's unarmed tempo (build_npc.js tempoFor), and each attack's clip is picked from
   * its class's POOL by judging the finished swing, tempo included (build_npc.js bestAttack). So any STYLE change, even to
   * one race's tempo, can swap clips: after it, check the `clip` fields in hits.json (the build prints the changes against
   * the files on disk) and the race-vs-man pairs the tests compare (test/lib/pairs.js). */
  const STYLE = {
    man: { tempo: { windup: 1, tame: 0.05, strike: 1, recover: 1.3, tsume: { windup: 1.6, strike: 1.6, recover: 2.0 }, ready: false } },
    elf: { hunch: -8, armsOut: -5, fight: { overswing: -0.15, extend: 0.9, lean: 6, lead: 0.03, freeArm: { out: 45 } },
      tempo: { windup: 0.82, tame: 0, strike: 0.85, recover: 1.3, tsume: { windup: 1.3, strike: 1.3, recover: 1.6 }, ready: true } },
    dwarf: { hunch: 9, armsOut: 11, kneeBend: 15, fight: { windup: 0.3, twist: 0.45, lean: 8, extend: -0.2 },
      tempo: { windup: 2.0, tame: 0.15, strike: 1.3, recover: 1.6, tsume: { windup: 2.6, strike: 3.0, recover: 4.0 }, ready: false },
      prefer: 'overhead' },
    hobbit: { kneeBend: 9, stride: 0.65, fight: { windup: -0.6, low: 14, extend: -0.3, freeArm: { out: -6 } },
      tempo: { windup: 0.75, tame: 0.03, strike: 0.85, recover: 1.2, tsume: { windup: 1.4, strike: 1.5, recover: 1.9 }, ready: false } },
    orc: { hunch: 12, armsForward: 8, armsOut: 12, kneeBend: 14,
      fight: { windup: 0.75, windupAfter: 0.8, overswing: 0.7, lean: 10, lead: -0.05, freeArm: { out: 15, flail: 2.5 }, jitter: 5 },
      tempo: { windup: 0.9, tame: 0.02, strike: 0.78, recover: 1.2, tsume: { windup: 1.25, strike: 2.4, recover: 2.4 }, ready: false } },
  };
  /** Substitutes per class, tried in order when a race's version of a slot's clip misses the quality bar (build_npc.js). */
  const POOL = {
    sword: ['Wand_Wave_1R', 'Sword_Swing_3R', 'Relik_Strike_2R', 'Relik_Strike_3R', 'Relik_Strike_1R', 'Sword_Swing_2R', 'Sword_Swing_1R', 'Wand_Wave_2R'],
    longsword: ['pickaxe_1R', 'Spear_Swing_3R', 'Spear_Swing_2R', 'axe_1R', 'Relik_Strike_3R', 'shovel_1R'],
    spear: ['Spear_Swing_2R', 'Spear_Swing_3R', 'Spear_Swing_1R', 'shovel_1R'],
    dagger: ['Wand_Wave_3R', 'Sword_Swing_3R', 'Sword_Swing_2R', 'Wand_Wave_1R', 'hoe_1R'],
    axe: ['axe_1R', 'pickaxe_1R', 'Spear_Swing_3R', 'Relik_Strike_3R'],
    shield: ['Warrior_bashR', 'Warrior_uppercutR'],
    unarmed: ['attackR', 'Wand_Wave_3R', 'attackL', 'Warrior_uppercutR'],
  };
  /** The share of a race's fighting style an attack must keep to count as fought in that style: build_npc.js bestAttack
   *  prefers a clip keeping it, and the tests compare races only on attacks that keep it (test/lib/pairs.js). */
  const KEEP = 0.6;
  /* ---- Formation stances (specs/addon/npc-formation-stances.md) ----
   * Each stance slot plays the build's pick from its STANCE_POOL (build_npc.js stancePick, by test/lib/stance.js): a pool
   * entry, or one entry's upper body over another's legs, finished for the race (posture, grip, hinges) and breathing where
   * it holds a frame. Entry: { source, clip (a list: Mo' Bends layers, merged bone by bone), note, hold? (s: a held frame),
   * mirror?, cycles? (whole gait cycles of a take that keeps walking), close? (the take as a loop, its seam spread),
   * part? ('upper' or 'lower': lends only that half), grip? (how the hand turns what it holds: 'shaft' both hands on one
   * shaft, 'up' carried upright, 'level' levelled forward, 'bow' the bow's limbs upright; build_npc.js stanceGrip) }.
   * Sources: AnimationsPlus (AP), Serious Player (SP), Mo' Bends (MB), Emotecraft (all in loadSources), and the BVH
   * libraries UAL (Quaternius, CC0), KayKit (CC0) and CMU (CMU's terms), retargeted by import/mocap.js. */
  const ent = (source) => (clip, note, extra) => ({ source, clip, note, ...extra });
  const AP = ent('animationsplus'), SP = ent('seriousplayer'), MB = ent('mobends'), UAL = ent('ual'), KAY = ent('kaykit'), CMU = ent('cmu');
  // legs lent to the still stances: the source's own stance under a held upper body
  const LEGS = {
    swordIdle: UAL('Sword_Idle', 'UAL sword guard’s legs: knees deep, weight even', { part: 'lower' }),
    idle: UAL('Idle_Loop', 'UAL idle’s legs: knees soft, weight even', { part: 'lower' }),
    lunge: CMU('144_17', 'CMU lunging footwork, held in the lunge: back foot braced, front knee bent', { part: 'lower', hold: 1 }),
    spearLunge: CMU('144_11', 'CMU spear-thrust lunges, held at full stretch', { part: 'lower', hold: 0.9 }),
    crouch: UAL('Crouch_Idle_Loop', 'UAL crouch, held: low, one foot ahead (its hips tip forward: an upright chest over them bends back)', { part: 'lower', hold: 1 }),
    block: UAL('Sword_Block', 'UAL sword block’s legs, held braced: knees bent, a short step forward', { part: 'lower', hold: 0.3 }),
    dash: UAL('Shield_Dash', 'UAL shield charge’s legs, held as it starts: deep, a long step forward', { part: 'lower', hold: 0.3 }),
  };
  const WALKS = [
    AP('walk', 'AP walk: the walk itself (arms swing full)'),
    SP('walking', 'SP walking, one gait cycle', { cycles: 1 }),
    UAL('Walk_Formal_Loop', 'UAL formal walk: upright, arms close to the body'),
    CMU('142_11', 'CMU marching, one gait cycle', { cycles: 1 }),
    CMU('138_01', 'CMU marching, one gait cycle', { cycles: 1 }),
    CMU('21_06', 'CMU soldiers march, one gait cycle', { cycles: 1 }),
    CMU('132_37', 'CMU marching (the dwarves’ walk variant), one gait cycle', { cycles: 1 }),
  ];
  // the shield holds: the left arm is the shield code's (build_npc.js withShield) in every one, so these lend the body
  const SHIELDS = [
    AP('blockL', 'AP shield block'),
    SP('shield', 'SP shield raised'),
    SP('shield_sneak', 'SP shield raised, crouched'),
    UAL('Idle_Shield_Loop', 'UAL shield idle'),
  ];
  const STANCE_POOL = {
    guard: [
      AP('combatReady', 'AP combat-ready (keys no bone in the file: the rest pose)'),
      AP('blockR', 'AP sword block: blade up across the face'),
      UAL('Sword_Idle', 'UAL sword guard: knees bent, blade forward'),
      UAL('Sword_Idle', 'UAL sword guard, held at its start: the blade still in front, the head highest', { hold: 0, part: 'upper' }),
      UAL('Sword_Idle', 'UAL sword guard, held mid-breath: the blade still in front', { hold: 0.4, part: 'upper' }),
      CMU('76_04', 'CMU defensive guard, held mid-take', { hold: 2.5 }),
      MB(['stance_const', 'stance_arms'], "Mo' Bends combat stance (its legs, elbows and item, with its arms)"),
      MB(['fist_guard_abs', 'fist_guard_pre'], "Mo' Bends fist guard"),
      LEGS.idle,
    ],
    guard_spear: [
      KAY('2H_Melee_Idle', 'KayKit two-handed guard, the shaft through both fists', { grip: 'shaft' }),
      CMU('124_08', 'CMU both hands on a level shaft, held forward (mirrored: right hand behind), the shaft through both fists', { hold: 0.33, mirror: true, grip: 'shaft' }),
      AP('Spear_Swing_2R', 'AP two-handed thrust held as it starts', { hold: 0 }),
      AP('Spear_Swing_3R', 'AP rising thrust held at its strike: shaft up and forward in both hands', { hold: 0.3 }),
      UAL('Idle_Lantern_Loop', 'UAL lantern idle mirrored: right hand at the hip, left raised in front, the shaft through both fists', { mirror: true, grip: 'shaft' }),
      UAL('Idle_Lantern_Loop', 'UAL lantern idle mirrored, held with the left hand highest, the shaft through both fists', { mirror: true, hold: 1.9, grip: 'shaft' }),
      // a front-facing pose with both hands put on the shaft (build_npc.js stanceOnShaft): the libraries' own two-handed
      // spear holds all stand side-on (their chests 26-68° off the facing)
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right at the hip, left 9 px up the shaft rising 40°, across the body', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -4], rise: 40, d: 9, toward: 40 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right at the hip, left 13 px up the shaft rising 30° straight ahead', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -4], rise: 30, d: 13 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right a little forward, left 9 px up the shaft rising 30°', { hold: 0, part: 'upper', onShaft: { right: [5, 17, -6], rise: 30, d: 9, toward: 20 }, grip: 'shaft' }),
      LEGS.idle, LEGS.swordIdle,
    ],
    guard_bow: [
      KAY('Ranged_Bow_Idle', 'KayKit bow idle, mirrored to the right hand, the bow upright', { mirror: true, grip: 'bow' }),
      KAY('Ranged_Bow_Aiming_Idle', 'KayKit bow held at full draw, mirrored, the bow upright', { mirror: true, grip: 'bow' }),
      SP('bow_idle', 'SP bow held ready'),
      SP('bow_idle', 'SP bow held ready, the bow upright', { grip: 'bow' }),
      AP('bowR', 'AP bow draw held as it starts, the bow upright', { hold: 0, grip: 'bow' }),
      KAY('Ranged_Bow_Draw', 'KayKit draw held as the arrow is nocked, low, mirrored, the bow upright', { mirror: true, hold: 0.4, grip: 'bow' }),
      KAY('Ranged_Bow_Draw', 'KayKit draw held with the arrow nocked, low, mirrored, the bow upright', { mirror: true, hold: 0.45, grip: 'bow' }),
      KAY('Ranged_Bow_Draw', 'KayKit draw held with the arrow nocked, the string hand back, mirrored, the bow upright', { mirror: true, hold: 0.48, grip: 'bow' }),
      KAY('Ranged_Bow_Draw', 'KayKit draw held as the bow starts up, mirrored, the bow upright', { mirror: true, hold: 0.5, grip: 'bow' }),
      // the bow clips' own arms (bow_arms.js), set for a ready stance: the bow upright in front, an arrow nocked (lower grips
      // bring the drawing arm's upper arm into the chest)
      UAL('Idle_Loop', 'UAL idle held, arms by bow_arms.js: the bow in front of the chest, an arrow nocked', { hold: 0, part: 'upper', bowArms: [1, 20, -10] }),
      UAL('Idle_Loop', 'UAL idle held, arms by bow_arms.js: the bow in front of the chest a little lower, an arrow nocked', { hold: 0, part: 'upper', bowArms: [1, 19, -10] }),
      UAL('Idle_Loop', 'UAL idle held, arms by bow_arms.js: the bow in front of the chest, further out, an arrow nocked', { hold: 0, part: 'upper', bowArms: [1, 20, -12] }),
      UAL('Idle_Loop', 'UAL idle held, arms by bow_arms.js: the bow in front of the chest’s middle, an arrow nocked', { hold: 0, part: 'upper', bowArms: [0, 20, -11] }),
      LEGS.idle,
    ],
    shield_brace: [...SHIELDS, LEGS.crouch, LEGS.lunge, LEGS.block, LEGS.dash],
    shield_brace_spear: [
      ...SHIELDS,
      SP('trident', 'SP trident raised to throw, levelled', { grip: 'level', part: 'upper' }),
      SP('swimming', 'SP crawl stroke held with the right hand by the head, levelled', { hold: 0, grip: 'level', part: 'upper' }),
      CMU('79_93', 'CMU weight lifting, held with the right hand at the shoulder, levelled overhand', { hold: 2.68, grip: 'level', part: 'upper' }),
      CMU('13_26', 'CMU directing traffic, held with the right hand raised by the head, levelled overhand', { hold: 4.03, grip: 'level', part: 'upper' }),
      CMU('17_01', 'CMU angry walk, held with the right fist raised, levelled overhand', { hold: 3.57, grip: 'level', part: 'upper' }),
      CMU('79_45', 'CMU bear, held with the right paw raised, levelled overhand', { hold: 1.39, grip: 'level', part: 'upper' }),
      CMU('79_45', 'CMU bear, held as the right paw tops its rise, levelled overhand', { hold: 1.45, grip: 'level', part: 'upper' }),
      AP('Spear_Swing_1R', 'AP thrust held at its strike, levelled', { hold: 0.2, grip: 'level', part: 'upper' }),
      LEGS.crouch, LEGS.lunge, LEGS.block, LEGS.dash,
    ],
    spear_brace: [
      AP('Spear_Swing_1R', 'AP thrust held at its strike', { hold: 0.2 }),
      AP('Spear_Swing_2R', 'AP two-handed thrust held at its strike (the old brace)', { hold: 0.19 }),
      AP('Spear_Swing_3R', 'AP rising thrust held at its strike', { hold: 0.43 }),
      KAY('2H_Melee_Idle', 'KayKit two-handed guard, the shaft through both fists', { grip: 'shaft' }),
      CMU('124_08', 'CMU both hands on a level shaft, held forward (mirrored: right hand behind), the shaft through both fists', { hold: 0.33, mirror: true, grip: 'shaft' }),
      AP('blockR', 'AP sword block mirrored: right fist at the hip, left forward, the shaft through both fists', { mirror: true, grip: 'shaft' }),
      CMU('135_09', 'CMU lunge punch (oi zuki), held at full stretch: right fist at the hip, left forward, the shaft through both fists', { hold: 0.73, grip: 'shaft', part: 'upper' }),
      CMU('135_10', 'CMU knife-hand block (shuto uke), held: right hand at the hip, left forward, the shaft through both fists', { hold: 1.72, grip: 'shaft', part: 'upper' }),
      CMU('142_22', 'CMU sneaky walk, held with the hands forward, the shaft through both fists', { hold: 0.81, grip: 'shaft', part: 'upper' }),
      CMU('76_01', 'CMU back off and punch, held on guard: right fist back, left forward, the shaft through both fists', { hold: 7.07, grip: 'shaft', part: 'upper' }),
      CMU('124_08', 'CMU both hands on a level shaft, held as it comes forward (mirrored), the shaft through both fists', { hold: 0.27, mirror: true, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right at the hip, left 13 px along it levelled 8° up, across the body', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -4], rise: 8, d: 13, toward: 40 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right at the hip, left 9 px along it levelled 14° up, across the body', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -4], rise: 14, d: 9, toward: 40 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right at the hip, left 13 px along it levelled 8° up', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -4], rise: 8, d: 13, toward: 20 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right at the hip, left 13 px along it level, across the body (for a posture that leans back)', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -4], rise: 2, d: 13, toward: 30 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right at the hip, left 13 px along it tipped 4° down, a little across (for a posture that leans back)', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -4], rise: -4, d: 13, toward: 15 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right by the hip, left 13 px along it levelled 8° up, across the body', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -2], rise: 8, d: 13, toward: 30 }, grip: 'shaft' }),
      UAL('Idle_Loop', 'UAL idle held, hands on the shaft: right by the hip, left 17 px along it levelled 2° up, a little across', { hold: 0, part: 'upper', onShaft: { right: [6, 16, -2], rise: 2, d: 17, toward: 20 }, grip: 'shaft' }),
      LEGS.crouch, LEGS.lunge, LEGS.spearLunge, LEGS.block, LEGS.dash,
    ],
    march: [
      ...WALKS,
      UAL('Sword_Idle', 'UAL sword guard, held: the blade carried steady', { hold: 0.4, part: 'upper' }),
      AP('idle', 'AP idle: arms easy at the sides', { part: 'upper' }),
    ],
    march_spear: [
      ...WALKS,
      AP('idle', 'AP idle, the spear carried upright', { part: 'upper', grip: 'up' }),
      AP('Idle_2', 'AP idle 2 held, the spear carried upright', { part: 'upper', hold: 1, grip: 'up' }),
    ],
    march_bow: [
      ...WALKS,
      CMU('132_37', 'CMU marching, held with the right forearm forward, the bow upright', { part: 'upper', hold: 2.51, grip: 'bow' }),
      CMU('21_06', 'CMU soldiers march, held with the right forearm forward, the bow upright', { part: 'upper', hold: 2.0, grip: 'bow' }),
      AP('sitHorse', 'AP rider’s hands, the bow upright', { part: 'upper', hold: 0, grip: 'bow' }),
    ],
  };
  /** The build's pick per stance slot and race (build_npc.js stancePick prints each; test/stances.test.js checks this record
   *  against the build). Score: the key pose's distance (test/lib/stance.js score, lower is closer); runner-up in brackets.
   *  guard:
   *    man 0.700 (runner-up ual:Sword_Idle@0.4 over mobends:fist_guard_abs,fist_guard_pre, 0.700)
   *    elf 0.715 (runner-up ual:Sword_Idle@0.4 over mobends:fist_guard_abs,fist_guard_pre, 0.715)
   *    dwarf 0.718 (runner-up ual:Sword_Idle@0.4 over animationsplus:combatReady, 0.725)
   *    hobbit 0.680 (runner-up ual:Sword_Idle@0 over ual:Idle_Loop, 0.716)
   *    orc 0.734 (runner-up ual:Sword_Idle@0 over ual:Idle_Loop, 0.766)
   *  guard_spear:
   *    man 0.303 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over kaykit:2H_Melee_Idle grip shaft, 0.338)
   *    elf 0.346 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over kaykit:2H_Melee_Idle grip shaft, 0.379)
   *    dwarf 0.278 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over kaykit:2H_Melee_Idle grip shaft, 0.328)
   *    hobbit 0.303 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over kaykit:2H_Melee_Idle grip shaft, 0.346)
   *    orc 0.276 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 30° over kaykit:2H_Melee_Idle grip shaft, 0.325)
   *  guard_bow:
   *    man 0.180 (runner-up ual:Idle_Loop@0 bow arms at 1,19,-10 over kaykit:Ranged_Bow_Aiming_Idle mirrored grip bow, 0.209)
   *    elf 0.228 (runner-up ual:Idle_Loop@0 bow arms at 1,20,-10 over kaykit:Ranged_Bow_Aiming_Idle mirrored grip bow, 0.249)
   *    dwarf 0.226 (runner-up ual:Idle_Loop@0 bow arms at 1,19,-10 over kaykit:Ranged_Bow_Aiming_Idle mirrored grip bow, 0.265)
   *    hobbit 0.180 (runner-up ual:Idle_Loop@0 bow arms at 1,19,-10 over kaykit:Ranged_Bow_Aiming_Idle mirrored grip bow, 0.214)
   *    orc 0.260 (runner-up ual:Idle_Loop@0 bow arms at 1,20,-12 over kaykit:Ranged_Bow_Aiming_Idle mirrored grip bow, 0.299)
   *  shield_brace:
   *    man 0.869 (runner-up ual:Idle_Shield_Loop over cmu:144_17@1, 0.900)
   *    elf 0.888 (runner-up ual:Idle_Shield_Loop over cmu:144_17@1, 0.938)
   *    dwarf 0.738 (runner-up ual:Idle_Shield_Loop over cmu:144_17@1, 0.801)
   *    hobbit 0.778 (runner-up ual:Idle_Shield_Loop over cmu:144_17@1, 0.808)
   *    orc 0.765 (runner-up ual:Idle_Shield_Loop over cmu:144_17@1, 0.836)
   *  shield_brace_spear:
   *    man 0.661 (runner-up cmu:79_45@1.45 grip level over cmu:144_17@1, 0.661)
   *    elf 0.694 (runner-up seriousplayer:swimming@0 grip level over ual:Shield_Dash@0.3, 0.780)
   *    dwarf 0.602 (runner-up cmu:79_45@1.39 grip level over cmu:144_17@1, 0.715)
   *    hobbit 0.600 (runner-up cmu:79_45@1.45 grip level over cmu:144_17@1, 0.600)
   *    orc 0.771 (runner-up cmu:79_45@1.45 grip level over ual:Crouch_Idle_Loop@1, 0.941)
   *  spear_brace:
   *    man 0.553 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 2° 20° left over ual:Shield_Dash@0.3, 0.563)
   *    elf 0.597 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 2° 20° left over ual:Shield_Dash@0.3, 0.599)
   *    dwarf 0.561 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 8° 30° left over cmu:144_17@1, 0.564)
   *    hobbit 0.557 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 8° 30° left over cmu:144_17@1, 0.615)
   *    orc 0.487 (runner-up ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 2° 20° left over ual:Crouch_Idle_Loop@1, 0.489)
   *  march:
   *    man 0.025 (runner-up seriousplayer:walking over cmu:142_11, 0.098)
   *    elf 0.025 (runner-up seriousplayer:walking over cmu:142_11, 0.098)
   *    dwarf 0.032 (runner-up seriousplayer:walking over cmu:142_11, 0.059)
   *    hobbit 0.052 (runner-up seriousplayer:walking over cmu:132_37, 0.066)
   *    orc 0.038 (runner-up seriousplayer:walking over cmu:142_11, 0.085)
   *  march_spear:
   *    man 0.460 (runner-up animationsplus:idle grip up over cmu:142_11, 0.540)
   *    elf 0.460 (runner-up animationsplus:idle grip up over cmu:142_11, 0.540)
   *    dwarf 0.467 (runner-up animationsplus:idle grip up over cmu:142_11, 0.501)
   *    hobbit 0.494 (runner-up animationsplus:idle grip up over cmu:132_37, 0.500)
   *    orc 0.468 (runner-up animationsplus:idle grip up over cmu:142_11, 0.527)
   *  march_bow:
   *    man 0.363 (runner-up cmu:21_06@2 grip bow over cmu:142_11, 0.427)
   *    elf 0.402 (runner-up cmu:132_37@2.51 grip bow over cmu:132_37, 0.427)
   *    dwarf 0.359 (runner-up cmu:21_06@2 grip bow over cmu:142_11, 0.387)
   *    hobbit 0.394 (runner-up cmu:21_06@2 grip bow over cmu:132_37, 0.465)
   *    orc 0.343 (runner-up cmu:21_06@2 grip bow over cmu:142_11, 0.388)
   */
  const STANCE_PICKS = {
    guard: {
      man: 'ual:Sword_Idle@0.4 over mobends:stance_const,stance_arms',
      elf: 'ual:Sword_Idle@0.4 over mobends:stance_const,stance_arms',
      dwarf: 'ual:Sword_Idle@0.4 over ual:Idle_Loop',
      hobbit: 'ual:Sword_Idle@0.4 over ual:Idle_Loop',
      orc: 'ual:Sword_Idle@0.4 over ual:Idle_Loop'
    },
    guard_spear: {
      man: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over animationsplus:Spear_Swing_2R@0',
      elf: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over animationsplus:Spear_Swing_2R@0',
      dwarf: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over cmu:124_08@0.33 mirrored grip shaft',
      hobbit: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 40° 40° left over animationsplus:Spear_Swing_2R@0',
      orc: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising 30° over cmu:124_08@0.33 mirrored grip shaft'
    },
    guard_bow: {
      man: 'ual:Idle_Loop@0 bow arms at 1,19,-10 over kaykit:Ranged_Bow_Idle mirrored grip bow',
      elf: 'ual:Idle_Loop@0 bow arms at 1,20,-10 over kaykit:Ranged_Bow_Idle mirrored grip bow',
      dwarf: 'ual:Idle_Loop@0 bow arms at 1,19,-10 over kaykit:Ranged_Bow_Idle mirrored grip bow',
      hobbit: 'ual:Idle_Loop@0 bow arms at 1,19,-10 over kaykit:Ranged_Bow_Idle mirrored grip bow',
      orc: 'ual:Idle_Loop@0 bow arms at 1,20,-12 over kaykit:Ranged_Bow_Idle mirrored grip bow'
    },
    shield_brace: {
      man: 'animationsplus:blockL over cmu:144_17@1',
      elf: 'animationsplus:blockL over cmu:144_17@1',
      dwarf: 'animationsplus:blockL over cmu:144_17@1',
      hobbit: 'animationsplus:blockL over cmu:144_17@1',
      orc: 'animationsplus:blockL over cmu:144_17@1'
    },
    shield_brace_spear: {
      man: 'cmu:79_45@1.39 grip level over cmu:144_17@1',
      elf: 'seriousplayer:swimming@0 grip level over cmu:144_17@1',
      dwarf: 'cmu:79_45@1.45 grip level over cmu:144_17@1',
      hobbit: 'cmu:79_45@1.39 grip level over cmu:144_17@1',
      orc: 'cmu:79_45@1.45 grip level over cmu:144_17@1'
    },
    spear_brace: {
      man: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 8° 30° left over ual:Shield_Dash@0.3',
      elf: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 8° 30° left over ual:Shield_Dash@0.3',
      dwarf: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 2° 20° left over cmu:144_17@1',
      hobbit: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-2 rising 8° 30° left over ual:Shield_Dash@0.3',
      orc: 'ual:Idle_Loop@0 grip shaft hands on a shaft from 6,16,-4 rising -4° 15° left over ual:Crouch_Idle_Loop@1'
    },
    march: {
      man: 'seriousplayer:walking over cmu:132_37',
      elf: 'seriousplayer:walking over cmu:132_37',
      dwarf: 'seriousplayer:walking over cmu:132_37',
      hobbit: 'seriousplayer:walking over cmu:142_11',
      orc: 'seriousplayer:walking over cmu:132_37'
    },
    march_spear: {
      man: 'animationsplus:idle grip up over cmu:132_37',
      elf: 'animationsplus:idle grip up over cmu:132_37',
      dwarf: 'animationsplus:idle grip up over cmu:132_37',
      hobbit: 'animationsplus:idle grip up over cmu:142_11',
      orc: 'animationsplus:idle grip up over cmu:132_37'
    },
    march_bow: {
      man: 'cmu:21_06@2 grip bow over cmu:132_37',
      elf: 'cmu:132_37@2.51 grip bow over cmu:142_11',
      dwarf: 'cmu:21_06@2 grip bow over cmu:132_37',
      hobbit: 'cmu:21_06@2 grip bow over cmu:142_11',
      orc: 'cmu:21_06@2 grip bow over cmu:132_37'
    }
  };
  /** The breathing a held upper body is given (the torso's and head's turns about their mean, the neck's sway at most
   *  0.5 px). */
  const STANCE_BREATH = { source: 'ual', clip: 'Idle_Loop', bones: ['upper', 'head'], sway: 0.5 };
  /** The leg swing of a race's march, where it differs from its walk's (STYLE stride): dwarves march with a shorter stride. */
  const MARCH_STRIDE = { dwarf: 0.8 };
  const api = { RACES, SLOTS, STYLE, POOL, KEEP, STANCE_POOL, STANCE_PICKS, STANCE_BREATH, MARCH_STRIDE };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.NpcSlots = api;
})(typeof window !== 'undefined' ? window : globalThis);
