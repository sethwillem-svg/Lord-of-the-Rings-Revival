# NPC attack tempo (tame, tsume, hitstop)

- **Pod:** addon (animation tools + client/combat)
- **Lead:** Opus
- **Status:** draft
- **Depends on:** `specs/addon/npc-animation-pulselib.md` (the attack pipeline in `tools/anim-viewer/build_npc.js`, the hit timing in `addon/.../combat/AttackTiming.java`)

## Summary

Attacks are built and timed as a Japanese key animator would time them: a **tame** (溜め, a held charge at the top of the
wind-up), **tsume** (ツメ, time bunched at the extremes so the pose lingers at the apex and at rest while the strike itself
is a burst), **meri-hari** (メリハリ, the slow/fast contrast that reads as weight) and **hitstop** (a short freeze when a hit
lands, Sakurai's "stop for big moments"). Each race gets its own numbers, so the same source swing reads as a flowing elven
cut, a plain human blow, a wild orcish hack or a heavy dwarven chop. Tests measure these on the shipped files, in
`tools/anim-viewer/test/lib/motion.js` terms, not on the build's own helpers.

Research notes behind this: `docs/reference/anime-timing.md` (written with this spec: tame/tsume definitions, Yoshida
Toru's "ドン vs ドーン", koma-uchi, Sakurai hitstop; all in our own words).

## Player-facing behavior

1. An NPC starts a swing. The weapon rises into a readable wind-up pose and **sits there for a beat** (the tame): long for a
   dwarf, short for a man, barely at all for an orc, none for an elf.
2. The strike leaves that pose as a burst and is fastest exactly at the hit.
3. If the hit lands, the attacker (and the victim's hurt) **freeze for a few game ticks**, longer for heavier weapons and
   races, then the swing continues.
4. The follow-through leaves the hit fast and **settles slowly** into the end pose (ato-zume); dwarves settle longest (the
   "doooon"), orcs overshoot and recover off balance, elves flow straight back to the ready stance.

In the viewer (`tools/anim-viewer/npc.html`) a speed trace under each figure shows the tip speed over time with the tame,
hit and settle marked, so the tempo can be checked by eye without the game.

## Rules and numbers

### Time warp (replaces the linear phase scales in `tempo()`)

`STYLE.<race>.tempo` becomes:

| Field | Meaning | man | elf | dwarf | hobbit | orc |
| --- | --- | --- | --- | --- | --- | --- |
| `windup` | phase scale, lead-in end → apex | 1 | 0.82 | 1.35 | 0.75 | 0.9 |
| `tame` | hold at the apex, seconds (was `hold`) | 0.05 | 0 | 0.15 | 0.03 | 0.02 |
| `strike` | phase scale, apex → hit + 0.05 s | 1 | 0.85 | 1.3 | 0.85 | 0.78 |
| `recover` | phase scale, hit + 0.05 s → end | 1 | 1 | 1.3 | 0.9 | 0.9 |
| `tsume.windup` | p ≥ 1: slows into the apex, `g(u) = 1 − (1 − u)^p` | 1.6 | 1.3 | 2.2 | 1.4 | 1.1 |
| `tsume.strike` | p ≥ 1: accelerates out of the apex to the hit, `g(u) = u^p` | 1.6 | 1.3 | 3.0 | 1.5 | 2.4 |
| `tsume.recover` | p ≥ 1: leaves the hit fast, settles slowly, `g(u) = 1 − (1 − u)^p` | 1.6 | 1.2 | 2.6 | 1.5 | 2.0 |
| `ready` | ease back to the idle pose at the end | false | true | false | false | false |

Tuned in Task 3 against fighting.test.js; relative race behaviour is the binding part.
Orcs also draw the wind-up back after posture (`fight.windupAfter`): bounded at 150° from the front, never moving the swing's start forward, judged on the finished clip.

- `g` maps **new** phase progress `u ∈ [0,1]` to **source** progress; the warp applied to key times is its inverse
  (`u^(1/p)` and `1 − (1 − v)^(1/p)`, closed form). `p = 1` is today's linear scale.
- The **apex** is the end of the wind-up: the last moment before the hit at which the tip speed is a local minimum (today
  `hit − 0.1 s`). `tempo()` finds it from the clip instead of assuming 0.1 s.
- The warp runs on the 60 fps resampled clip (after `hingeify`), so there are keys for it to move; it never adds keys
  except the two that bracket the tame.
- Build order in `finishAttack` is unchanged; `tempo` stays last. `stretchWindup` (the 3:1 spacing fix) runs before it as
  now; with the new `tsume.windup` most clips no longer need it.

### Hitstop (game)

| Weapon class | Stop (ticks) |
| --- | --- |
| dagger, unarmed | 1 |
| sword, spear | 2 |
| axe, longsword | 3 |
| dwarf, any class | +1 |
| cap | 4 |

- Server: when `AttackTiming.onLevelTick` lands a held hit (`doHurtTarget` returned true), it sends
  `NpcHitPayload(npcId, targetId, stopTicks)` to players tracking the NPC. Server timing (`due`, the 20-tick rhythm) is
  untouched: the freeze is cosmetic and client-only.
- Client: `NpcAnimatable.hitstop(stopTicks)` sets the running attack overlay's speed to 0 and, after `stopTicks` client
  ticks, restores the speed it had (`pendingSpeeds` machinery; guarded like `trigger`). If the target is an animated NPC,
  its `hurt` overlay is frozen for the same ticks. A player victim gets nothing new (vanilla hurt shake is enough).
- A frozen overlay that ends (fade-out) or is restarted drops the freeze; nothing waits on it.

### Tests (new file `tools/anim-viewer/test/timing.test.js`, shipped files, 60 Hz exact-at-keys sampling)

All speeds are the weapon tip's, px/s, from `motion.js` `frames` + `velocities`; "swing" means lead-in end → clip end,
excluding the ready ease.

| # | Principle | Check | Limits |
| --- | --- | --- | --- |
| T1 | kime (readable apex) | longest interval before the hit with tip speed ≤ 10 % of peak | ≥ race `tame`; elves ≥ 0.03 s (the turnaround still reads) |
| T2 | kime (three poses) | tip at apex, hit and end are pairwise ≥ half a blade apart | all attacks |
| T3 | meri-hari | peak speed ÷ median speed over the swing | man ≥ 4, hobbit ≥ 4, elf ≥ 3, orc ≥ 5, dwarf ≥ 6 |
| T4 | tsume, wind-up | share of the wind-up's tip path in its last 30 % of time (slow into the apex) | dwarf ≤ 15 %, man/hobbit ≤ 22 %, elf ≤ 26 %, orc ≤ 30 % |
| T5 | tsume, strike | the strike phase holds ≥ 45 % of the swing's tip path while taking ≤ 30 % of its time | all races; dwarf and orc ≥ 55 % |
| T6 | tsume, recovery | share of the recovery's tip path in its first 30 % of time (fast leave, slow settle) | dwarf ≥ 60 %, orc ≥ 55 %, others ≥ 45 % |
| T7 | weight by contrast (Yoshida) | recovery time ÷ wind-up time | dwarf ≥ 1.1 (the long "doooon"), elf ≤ 0.9 (snaps back), others 0.8..1.2 |
| T8 | race tempo order | wind-up time per clip | dwarf > man > elf, hobbit < man (3 in 4 on shared clips, as `mostly()` does) |
| T9 | off balance (orc) | at the end of recovery the `upper`/`body` bone is ≥ 8° from the idle pose, and the free hand ≥ 6 px further from rest than a man's on the same clip | orcs, 3 in 4 |
| T10 | hitstop numbers | `Hitstop.ticks(race, cls)` table above | GameTest `HitstopTests` in `combat/test` |
| T11 | hitstop fires | a landed held hit sends one `NpcHitPayload` with `ticks == Hitstop.ticks(...)`; a missed one (target stepped out) sends none | GameTest, extends `AttackTimingTests` with a captured `PacketDistributor` sink or a static listener hook on `AttackTiming` |

- Existing motion tests keep their limits (3:1 spacing, fastest at hit ± 0.1 s, no bounce, no pops over fades). The new
  tempo must pass them; nothing is loosened.
- **Sampling:** `motion.js` gains `atKeys(anim)`, which evaluates each bone exactly at the union of its key times plus
  the 60 Hz grid, instead of a blind 120 Hz grid; velocities between consecutive samples. Pose tracks per shipped file are
  cached in `tools/anim-viewer/test/.cache/<sha1 of file>.json` (git-ignored) so a run without a rebuild takes seconds.
  Target: the whole suite under 2 minutes on the maintainer's machine.

## Edge cases

- Multiplayer: hitstop goes only to players tracking the NPC; late joiners miss it (fine). Server timing unaffected.
- Death: an NPC that dies during a freeze plays the death animation normally (the overlay fade drops the freeze).
- Shield and `_l` twins: built from the finished (already tempo'd) swing, so they inherit the timing; T1–T9 run on them too
  (they must give the same numbers as the plain clip within 1 %).
- Speed > 1 from `AttackTiming` (long clips compressed into the 18-tick window) scales tame too; a dwarf longsword at speed
  1.4 holds ~0.1 s. Acceptable; if a tame drops under 1 tick it simply reads as a turnaround.
- Ranged and unarmed: unarmed attacks get the man table with `tame 0.03`; ranged releases are untouched.
- Koma-uchi (holding on twos/threes) is **not** shipped; the viewer gets a "Step: 1/2/3" toggle that quantises playback
  time so the look can be judged. If it reads well for orcs it becomes a later spec.

## Data and APIs

- `tools/anim-viewer/npc_slots.js` `STYLE.<race>.tempo` as above (`hold` renamed `tame`; `tsume` object).
- `tools/anim-viewer/build_npc.js` `tempo(clip, slot, t, idle, side)` implements the warp; exports `_tempo` for unit
  tests; `hits.json` entries gain `apex` (s, after lead-in) and `tame` (s) so the game and the viewer know the pose times.
- `addon/.../combat/Hitstop.java`: `static int ticks(String race, String cls)`; pure, table above.
- `addon/.../combat/NpcHitPayload(int entityId, int targetId, int ticks)`: `CustomPacketPayload`, registered beside
  `NpcAttackPayload`, handled on the client by `ClientAttack.hit(...)` → `NpcAnimatable.hitstop(ticks)`.
- `addon/.../client/anim/NpcAnimatable`: `hitstop(int ticks)`; `tick()` counts the freeze down and restores the speed.
- Viewer: `npc.html` draws the tip-speed trace per figure from `hits.json` (`hit`, `apex`, `tame`) and the sampled clip.

## Out of scope

- Smears/weapon trails (obake) and impact particles: a render spec of its own.
- Koma-uchi in shipped files (viewer toggle only, see above).
- Hitstop for player attacks or for Middle-earth's own renderer.
- Changing the server's attack rhythm (`WINDOW_TICKS`, `MAX_WINDUP_TICKS`).

## Prerequisite: green suite

The branch currently fails these; they are fixed first (or by the new tempo, verified by the tests) before T1–T11 land:

- `motion.test.js`: clean arcs (elf `attack_sword_3`, hobbit `attack_longsword_3` turn on 4–6.5 px), fastest at hit
  (dwarf `attack_sword_4`), pops over fade (hobbit `attack_sword_3`, orc `attack_sword_2`).
- `weapons.test.js`: two-handed free hand on the grip (dwarf `attack_longsword_1` 26 px, `attack_axe_1` 24.7 px).
- `fighting.test.js`: dwarf style survival 10/16, orc bigger wind-up 4/7, orc wider arc 5/14, hobbit quicker wind-up 2/7.

## Acceptance tests

- [ ] `node --test tools/anim-viewer/test/*.test.js` green, including `timing.test.js` T1–T9, in under 2 minutes without a rebuild.
- [ ] GameTests `HitstopTests` (T10) and `AttackTimingTests` (T11) green: `cd addon && ./gradlew runGameTestServer -PmeJar=...`.
- [ ] Viewer: `./tools/anim-viewer/view.sh npc`, "Show all" with a longsword: dwarf figures visibly pause at the top and settle slowly; orc figures lurch; elf figures flow back to ready; speed traces match.
- [ ] Playtest (maintainer): dwarf axe hit on a player freezes for 4 ticks and reads heavy; orc dagger hits feel quick; no NPC ever sticks frozen (watch a fight for a minute, including a kill mid-swing).
