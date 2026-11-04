'use strict';
/* How the game plays an attack clip, for tempo checks judged in game time. combat.AttackTiming
 * (addon/src/main/java/io/github/sethwillemsvg/lotrrevival/combat/AttackTiming.java, WINDOW_TICKS and MAX_WINDUP_TICKS)
 * plays a clip faster when it is longer than the melee goal's window or hits later than the longest wind-up allowed:
 * speed = max(1, length ticks / 18, hit ticks / 12). Keep these in step with that file. */
const WINDOW_TICKS = 18, MAX_WINDUP_TICKS = 12, TICKS_PER_SECOND = 20;

/** The speed the game plays an attack at, from its hits.json entry ({ length, hit } in seconds). */
const playSpeed = (h) => Math.max(1, (h.length * TICKS_PER_SECOND) / WINDOW_TICKS, (h.hit * TICKS_PER_SECOND) / MAX_WINDUP_TICKS);

module.exports = { WINDOW_TICKS, MAX_WINDUP_TICKS, TICKS_PER_SECOND, playSpeed };
