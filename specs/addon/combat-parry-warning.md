# Attack warning and parry (Middle-earth add-on)

- **Status:** draft (2026-11-05), written by the lead; the maintainer reviews after. Design choices the lead made alone are marked **(choice)**; the open ones are listed at the end.
- **Project:** part of project 3 (nemesis and world events) in `specs/addon/hiring-reputation.md`, pulled forward because it only needs the existing attack timing.
- **Builds on:** `combat.AttackTiming` (NPC melee damage held until the attack clip's hit frame), `combat.AttackClips` (`hits.json`), `combat.WeaponClass`, `combat.Hands`, `combat.NpcAttackPayload`, `client.anim.ClientAttack` / `NpcAnimator` / `NpcAnimatable`; logging per `specs/addon/debug-logging.md` (logger `combat`).
- **Middle-earth integration points (public classes only, nothing copied):** `NpcEntity` (melee through vanilla `MeleeAttackGoal` and `doHurtTarget`; a mounted NPC's `doHurtTarget` also makes its beast mount hit; `getBlocking()` is a stub that always returns false, and NPCs never raise their shields), `CustomShieldItem` with `ShieldTypesME` (LIGHT, MEDIUM, HEAVY; all extend vanilla `ShieldItem`), the NPC rank pools (`NpcRank`: CIVILIAN, MILITIA, SOLDIER, KNIGHT, VETERAN, LEADER), the melee archetype's `critical_chance` (data only; Middle-earth does not use it yet, and neither do we).

## Summary

Middle-earth NPCs now swing on a clip with a hit frame, but the wind-up before it is only 2-4 ticks, too short to see. This spec stretches every NPC melee swing into a readable wind-up, shows a marker over the NPC while it winds up (white for a normal swing, red for a heavy unblockable one), and lets players answer with a timed parry: raise a shield or press parry with a blade just before the hit frame to take no damage, stagger the attacker and earn a stronger counter-hit. Late or early answers fall back to an ordinary shield block or a half-damage glance. Heavy attacks cannot be parried or blocked and must be dodged. NPCs (hired units included) also parry now and then, by rank. The server decides everything; the client only shows it.

## Player-facing behaviour

1. An NPC starts a melee swing at someone. For the wind-up (half a second normally) it visibly draws back, and a marker appears above its head: a white diamond with a ring closing around it. When the ring meets the diamond, that is the moment to parry.
2. Some swings by tougher NPCs are **heavy**: longer wind-up, a red spiked marker, a low warning sound if you are the target. They cannot be parried or blocked; step out of reach (the hit misses a target out of reach, as now) or take the extra damage.
3. **Shield parry:** raise a shield (use key) so the hit lands within the first few ticks of raising it. No damage, no shield wear, a spark and a ringing sound, and the attacker staggers back. Raise it earlier and keep it up: an ordinary vanilla block.
4. **Blade parry:** with a sword, longsword, dagger, axe or spear and no shield, press the use key (or the Parry key) to snap into a short guard. A hit early in the guard is a perfect parry; later in the guard it glances off for half damage. Outside the guard it lands normally.
5. After a perfect parry, your next hit on that attacker within 1.5 seconds deals +50% damage and cannot be parried by it.
6. Mashing does not work: a failed parry attempt locks the perfect window for one second (no stamina bar). A perfect parry clears the lock, so a good rhythm against a group is rewarded.
7. NPCs sometimes parry you: a spark, a ring, your hit does nothing. Veterans and leaders do it more; militia never.
8. Off-screen attackers targeting you show a small arrow at the edge of the screen pointing toward them (can be turned off).

## Rules and numbers

All numbers are config values (`lotr_revival-common.toml` section `[combat]`, marker values in `lotr_revival-client.toml` section `[combat.warning]`) unless marked as data. Ticks are server ticks (20 per second).

### Wind-up (changes to `AttackTiming`)

| Thing | Value | Notes |
| --- | --- | --- |
| Normal wind-up (swing start to hit frame) | 10 ticks | replaces the 3-12 tick fit; the clip's own hit time is 1.3-4 ticks today (`hits.json`) |
| Heavy wind-up | 18 ticks | still inside the melee goal's 20-tick rhythm |
| Heavy damage multiplier | x1.4 | applied at the hit frame |
| Mounted rider wind-up | same | the beast's own hit is part of the rider's `doHurtTarget` and lands or fails with it |
| Reach slack at the hit frame | 0.75 blocks | unchanged; mounted riders get Middle-earth's +0.5 reach as well (today `AttackTiming` misses it) |
| Clip playback | time-warped **(choice)** | the client plays the clip's first 60% up to its hit frame stretched over the wind-up minus the remaining 40%, which plays at normal speed, so the NPC draws back slowly then strikes. Replaces the uniform `speed` stretch. |

### Heavy attacks

| Attacker rank | Heavy chance per swing |
| --- | --- |
| CIVILIAN, MILITIA | 0% |
| SOLDIER | 5% |
| KNIGHT | 10% |
| VETERAN | 15% |
| LEADER | 25% |
| nemesis (later project) | 30% (set by that project through `AttackTiming.heavyChanceOverride`) |

- Only NPCs with a weapon (`WeaponClass` not null, not bow/crossbow/banner/shield) make heavy swings. Longsword and axe classes add +5% **(choice: two-handers read as heavy hitters)**.
- No two heavy swings in a row from one NPC.
- A heavy swing uses the clip flagged `"heavy": true` in `hits.json` for its race and class; if none is flagged, the clip with the latest hit frame (WARN once per race and class, see Logging). Flags are data written by `tools/anim-viewer/build_npc.js`.
- **Unblockable:** ignores perfect parries, blade guards, vanilla shield blocks and NPC parry rolls. Only distance avoids it. Config `heavy.shield_share` (default 0.0) lets a raised shield still absorb that share of it.

### Warning marker

| Thing | Value | Notes |
| --- | --- | --- |
| Shown | from the attack payload's arrival until the hit tick plus 2 ticks, or until an end payload | |
| Normal shape | white diamond, ring closing on it | ring closes at the ideal press time (see Latency) |
| Heavy shape | red spiked diamond (four points), thicker ring, gentle pulse | shape differs from normal, not only colour (colour-blind safe) |
| Position | 0.5 blocks above the NPC's head (above the name tag when one shows) | billboarded, scales with distance so it stays 14-24 px tall |
| Size for the local player as target | 100% | |
| Size for attacks on others | 60%, 50% opacity | config `warning.show_others` (true) |
| Range | 24 blocks | client config; the server already sends only to players tracking the NPC |
| Through walls | no (depth-tested) | config `warning.through_walls` (false) |
| Max drawn at once | 32 | the nearest, plus always every attack aimed at the local player |
| Off-screen arrow | on, for attacks aimed at you from outside the view | config `warning.offscreen_arrow` |
| Heavy cue sound | `lotr_revival:combat.heavy_warn`, played to the target only | |
| Master switch | `warning.enabled` (true), client side | the server sends the same payload either way |

### Parry

| Defender holds | Parry by | Perfect window (ticks after the press) | Then | Notes |
| --- | --- | --- | --- | --- |
| Light shield (`ShieldTypesME.LIGHT_SHIELD`) | starting to raise it | 0-7 | vanilla block while raised | |
| Medium shield, vanilla shield, any other `ShieldItem` | starting to raise it | 0-6 | vanilla block | unknown shield subclasses count as medium |
| Heavy shield (heavy and siege) | starting to raise it | 0-5 | vanilla block | heavy shields block better later (hiring's shield wall) |
| Dagger | blade guard | 0-4 | glance until tick 12 | |
| Sword | blade guard | 0-5 | glance until tick 12 | |
| Longsword | blade guard | 0-6 | glance until tick 12 | |
| Axe, spear | blade guard | 0-4 | glance until tick 12 | |
| Bow, crossbow, banner, tools, empty hand | cannot parry | - | - | |

- **Press tick:** the server tick the shield use started (`LivingEntityUseItemEvent.Start`, off or main hand), or the server tick the parry payload arrived. A shield in either hand takes the shield row; otherwise the main hand's class (through `Hands.weapon`).
- **Facing:** the attacker must be in front of the defender (the same half-plane test vanilla shields use). Behind: no parry, no glance.
- **Perfect parry:** 0 damage; no shield or weapon wear; the mount's hit (if any) is cancelled with the rider's; attacker staggered; knockback 0.4 on the attacker; sound `combat.parry_perfect`, particle `parry_spark` at the contact point; counter bonus armed. One perfect window can parry several hits landing in it (groups).
- **Glance** (blade guard only, after the perfect window, until guard tick 12): damage x0.5, weapon -1 durability, sound `combat.parry_glance`. No stagger.
- **Vanilla shield block** (raised before the window and still up, at least 5 ticks of use as vanilla needs): unchanged vanilla behaviour, including wear and an axe disabling the shield. A perfect parry against an axe does not disable it.
- **Stagger:** 20 ticks (LEADER x0.5, nemesis x0.5). A staggered NPC cannot start a swing (its melee damage event is cancelled without queueing), moves at -50% speed, and plays the `stagger` clip. Its pending swing, if any, is dropped.
- **Counter bonus:** the defender's next melee hit on that attacker within 30 ticks: x1.5 damage, and that hit skips the NPC parry roll. Used up by one hit.
- **Spam lock:** a press that does not give a perfect parry locks perfect windows for 20 ticks from that press. A press inside the lock still blocks (shield) or guards (blade guard glance only). A perfect parry clears the lock.
- **Blade guard cooldown:** a new blade guard cannot start until the previous one ends (12 ticks).
- **Ranged:** arrows and bolts cannot be parried; vanilla shield blocking of projectiles is unchanged. No markers for ranged attacks **(choice: keep it modest; an "aiming at you" cue is an open question)**.
- **PvP:** player attacks have no wind-up, so players cannot parry players. Out of scope.

### Latency (server authoritative)

| Thing | Value | Notes |
| --- | --- | --- |
| Grace ticks `g` for a player defender | `min(ceil(latency_ms / 50), 4)` | latency from the player's connection (keep-alive round trip) |
| Window, server side | press tick in `[hit - W, hit + g]`, W the perfect window | a press seen up to `g` ticks after the hit frame still counts: on the player's screen it came before the hit |
| Deferred resolution | hits on a player who holds a parry-capable item resolve at `hit + g`, not `hit` | the reach check stays at the hit tick; NPC and itemless targets resolve at `hit` |
| Client ring timing | closes at `windup - W/2 - rtt/2` ticks after the payload arrives | the client knows its own round trip from the player list |

- Only the press tick is trusted, and it is stamped by the server; the client never claims a time.

### NPC parry (modest)

| Defender rank | Chance per melee hit taken |
| --- | --- |
| CIVILIAN, MILITIA | 0% |
| SOLDIER | 5% |
| KNIGHT | 10% |
| VETERAN | 15% |
| LEADER | 20% |
| + shield in either hand | +10% |
| + hired unit level (hiring spec) | +2% per level above 1 |
| Maximum | 40% |

- Rolled when a melee hit from a player, or an NPC's hit frame, reaches the NPC. Needs: the attacker in front, the NPC not staggered and not in its own wind-up, the NPC able to parry (table above; a shield or blade), and its parry cooldown (40 ticks) ready.
- Success: 0 damage, sound and sparks, the NPC plays a parry clip. A player attacker's attack strength is reset (their next swing starts from zero charge); an NPC attacker is staggered.
- Heavy attacks and counter-bonus hits are never rolled. Arrows are never rolled.
- NPC-versus-NPC rolls: config `npc_parry.vs_npcs` (true).

## Edge cases

- **Multiplayer:** each player's press, lock and counter are their own. One NPC attacks one target; others see a small marker. Two players parrying hits from the same NPC in the same tick cannot happen (one pending swing per NPC).
- **Lag:** see Latency. Above 200 ms the grace caps at 4 ticks; such players parry by pressing earlier. A press arriving after the deferred resolution does nothing. Server lag (low TPS) stretches everything evenly because all timing is in server ticks.
- **Death, logout, dimension change:** a pending swing on a dead, removed or departed attacker or target is dropped (as now) and an end payload clears the marker. Parry state (press, lock, counter) is transient and is not saved; a dead player respawns with none.
- **Item swap:** the item counts at press time and must still be held at the hit; switching away cancels the guard (a dropped shield stops vanilla blocking anyway).
- **Stagger while mounted:** the rider staggers; the mount keeps moving but its hit is cancelled with the rider's. Riders are never dismounted by a parry.
- **Heavy plus out of reach:** a target that steps out of reach is missed, heavy or not.
- **Creative, spectator:** creative players parry normally; spectators get no markers and cannot be targeted.
- **Hiring and formations (owned by the hiring session, not changed here):** hired units use the NPC parry roll with the level bonus. Shield wall's frontal reduction applies after the roll to hits that were not parried; whether shield wall softens heavy attacks is an open question. Spear line, charge and banner bonuses are separate multipliers and stack with the counter bonus. Formation stances do not change parry windows.
- **Middle-earth shield logic:** `NpcEntity.blockUsingShield` only runs if an NPC is really using a shield, which it never does today; if Middle-earth makes NPCs raise shields later, their vanilla blocks happen as normal and our roll still runs first.
- **Other mods' damage:** only damage from held NPC swings (`AttackTiming`) and direct player melee is touched; everything else passes unchanged.

## Data and APIs

- **Changed payload `NpcAttackPayload` (S2C, to players tracking the NPC):** `entityId`, `slot` (may be empty: no clip, marker only), `windupTicks` (VarInt), `targetId` (VarInt, -1 none), `flags` (byte: bit 0 heavy, bit 1 mounted). Replaces `speed`; the client computes the time warp from `windupTicks` and the clip's hit time. Sent for every held swing, with or without a clip. Payload registrar version goes to `"2"`.
- **New `NpcAttackEndPayload` (S2C):** `entityId`, `reason` (HIT, MISSED, PARRIED, CANCELLED). Clears the marker; PARRIED plays the attacker's `stagger` clip.
- **New `ParryPayload` (C2S, empty):** a blade guard press. The server stamps the tick.
- **New `ParryFxPayload` (S2C, to trackers of the defender):** `defenderId`, `kind` (PERFECT, GLANCE, NPC_PARRY). Plays the defender's parry clip or the player's guard flash. Sounds and particles are played server side (`level.playSound`, `sendParticles`), no packet.
- **Attachments (transient, not serialized, not copied on death):** player `lotr_revival:parry` (last press tick, press kind and item class, lock until, guard until, counter target UUID and until tick); entity `lotr_revival:combat_state` on `NpcEntity` (stagger until, NPC parry cooldown until, last swing heavy). Existing `AttackTiming` weak maps stay as they are; the pending record gains `startTick`, `heavy` and `resolveTick`.
- **Events (no mixins needed):** `LivingIncomingDamageEvent` (existing hold; counter bonus; glance; NPC parry roll; heavy multiplier during replay), `LevelTickEvent.Post` (existing resolution, now with parry checks before `doHurtTarget`), `LivingEntityUseItemEvent.Start` (shield press), `LivingShieldBlockEvent` (bypass vanilla blocking for heavy replays; no wear on a perfect parry against an axe), client `RenderLevelStageEvent` stage AFTER_ENTITIES (markers, one batch), a registered GUI layer (off-screen arrows), `InputEvent.InteractionKeyMappingTriggered` (use key with a blade and no usable off-hand item sends `ParryPayload`), `RegisterKeyMappingsEvent` (Parry key, unbound by default).
- **Rank lookup:** `combat.NpcRanks.of(NpcEntity)`: the NPC type's rank from Middle-earth's faction rank pools, cached per type id. The hiring work needs the same lookup; whichever lands first owns it in `combat` and the other reuses it.
- **New assets (made for this repo, credits in `assets/lotr_revival/CREDITS.md`):** sounds `combat.parry_perfect`, `combat.parry_glance`, `combat.heavy_warn`; particle `parry_spark`; marker textures `textures/combat/warn_normal.png`, `warn_heavy.png`, `warn_ring.png`, `offscreen_arrow.png`; clips per race `stagger`, `parry_<class>` (shield, blade), from the licence-compatible sets in `npc-animation-pulselib.md`; `"heavy": true` flags in `hits.json`.
- **Hooks for the nemesis project:** `AttackTiming.heavyChanceOverride` (per NPC), stagger multiplier per NPC.

## Logging

Logger `combat` through `LotrLog` (`specs/addon/debug-logging.md`). Client lines also use `combat`.

| Level | Event |
| --- | --- |
| INFO | startup: wind-up ticks, heavy chances, parry windows, grace cap, NPC parry chances and `vs_npcs` in use |
| INFO | client startup: marker enabled, range, max drawn |
| WARN | no `"heavy"` clip for a race and class, longest clip used (once per race and class) |
| WARN | a shield item of an unknown type, treated as medium (once per item id) |
| WARN | a `ParryPayload` from a player not holding a parry-capable item, more than 20 in a second (possible bad client) |
| DBG | swing start: `swing: npc=<id> target=<id> clip=<slot|none> windup=10 heavy=false mounted=false tick=<t>` |
| DBG | press: `press: player=<id> kind=shield|blade item=sword tick=<t> locked=false` |
| DBG | resolution: `parry ok: defender=<id> weapon=sword window=4/6 ticks grace=2 attacker=<id> dmg 7.0->0.0`; also `glance`, `vanilla block`, `late` (press after the window), `locked`, `behind`, `heavy bypass` with the same fields |
| DBG | stagger start and end; counter bonus applied (`dmg 4.0->6.0`) |
| DBG | NPC parry roll: `npc parry: npc=<id> rank=KNIGHT chance=0.20 roll=0.13 ok attacker=<id>` |
| DBG (client) | marker cap reached this frame (counted, not per frame; goes in the summary) |
| DBG summary (600 ticks) | swings, heavy swings, misses, perfect parries, glances, vanilla blocks, late presses, locked presses, NPC parries, staggers |

## Out of scope

- Parrying projectiles, ranged warning cues, player-versus-player parry.
- Stamina, directional blocking (left/right/overhead), dodge rolls.
- Ripostes as their own move (the counter bonus stands in for one).
- First-person parry animation beyond vanilla's shield raise and a short guard swing.
- Formation code (hiring session).

## Acceptance tests

GameTests in `combat/test/`, `@GameTest(template = "empty")`, on a real Middle-earth `gondor.soldier` built as in `AttackTimingTests` (no AI, iron sword, fixed `ATTACK_DAMAGE`). Mock players from `makeMockServerPlayerInLevel` are removed in a `finally`. Heavy chance and NPC parry chance are set through test overrides (`AttackTiming.heavyChanceOverride`, `NpcParry.chanceOverride`) and reset in `finally`. Mock players have 0 latency, so `g = 0`.

- [ ] `windupIsReadable`: a swing's due tick is start + 10; with heavy forced, start + 18 and the pending record says heavy.
- [ ] `perfectShieldParry`: player with a medium Middle-earth shield off-hand starts using it at due-3; at due+2 the player's health is unchanged, shield damage unchanged, the NPC is staggered (`ParryState.staggeredUntil(npc) > now`), and a `doHurtTarget` during the stagger queues nothing.
- [ ] `earlyShieldIsVanillaBlock`: shield raised at due-15 and held: no health lost, shield damaged, NPC not staggered.
- [ ] `heavyIgnoresShield`: heavy forced, shield raised at due-3: player loses 1.4x the baseline damage (baseline from `noParryBaseline` in the same run), no stagger.
- [ ] `bladePerfectAndGlance`: sword in hand, `Parry.press(player)` (the payload handler) at due-3: no damage; in a second NPC-player pair, press at due-9 (inside the 12-tick guard, outside 5): half the baseline damage.
- [ ] `lateAndLockedPresses`: press at due+1 (g = 0): full damage. Press at due-20 then due-3: the second press is locked, the first guard has ended: full damage. After a perfect parry the lock is cleared: a press 5 ticks later can parry again.
- [ ] `behindCannotParry`: attacker placed behind the player, shield raised at due-3: full damage (vanilla block also fails from behind).
- [ ] `counterBonus`: after a perfect parry, `npc.hurt(playerAttack(player), 4)` takes 6; a second hit takes 4; a hit after 31 ticks takes 4.
- [ ] `npcParryRoll`: chance forced to 1.0: a player-sourced melee hit from the front deals 0 and resets nothing else; from behind deals full; a second front hit within 40 ticks deals full; a counter-bonus hit is never rolled.
- [ ] `mountedAttackerParried`: soldier riding a vanilla horse; perfect parry: no damage to the player from rider or mount.
- [ ] `graceWindow` (pure function, also a GameTest so CI runs it): `ParryWindow.classify(press, hit, W, guard, g)` for presses before, inside, after the window, with g = 0 and 3; `grace(0 ms) = 0`, `grace(120 ms) = 3`, `grace(500 ms) = 4`.
- [ ] `payloadRoundTrip`: `NpcAttackPayload` and `NpcAttackEndPayload` encode and decode through their codecs with every flag combination; a swing with no clip still produces a payload (captured through a test sink on `AttackTiming`).
- [ ] Existing `AttackTimingTests` still pass (hit lands on the due tick; stepping out of reach misses).
- [ ] Playtest: markers readable at 2, 10 and 24 blocks; heavy distinct without colour; 50 NPCs fighting with no frame drop beyond 5%; parry by ear and eye at 0 ms and with 150 ms simulated latency; off-screen arrow; config switches.

## Tickets

Rough plan; paths under `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/`.

| # | Ticket | Files | Size |
| --- | --- | --- | --- |
| 1 | Config and rank lookup: `[combat]` common and `[combat.warning]` client config; `NpcRanks` from the faction rank pools; startup INFO lines | `combat/CombatConfig.java`, `client/combat/WarningConfig.java`, `combat/NpcRanks.java` | 1 d |
| 2 | Readable wind-up and heavy swings: fixed wind-ups, heavy roll and multiplier, heavy clip choice (`hits.json` flag), mounted reach, pending record with start/resolve ticks, payload v2 and end payload, test sink | `combat/AttackTiming.java`, `combat/AttackClips.java`, `combat/NpcAttackPayload.java`, `combat/NpcAttackEndPayload.java`, `LotrRevival.java` (registrar) | 1.5 d |
| 3 | Client time warp: play the clip's draw-back stretched over the wind-up, then the strike; `stagger` on PARRIED | `client/anim/ClientAttack.java`, `client/anim/NpcAnimatable.java`, `client/anim/NpcAnimator.java` | 1 d |
| 4 | Warning marker renderer: marker store keyed by entity id, batched billboards, nearest-32 cap, target emphasis, ring timing with own round trip; off-screen arrow layer; heavy cue sound | `client/combat/AttackMarkers.java`, `client/combat/MarkerRenderer.java`, `client/combat/OffscreenArrows.java` | 2 d |
| 5 | Player parry core: press tracking (shield use start, `ParryPayload`), windows by item, grace and deferred resolution, perfect / glance / vanilla block / heavy bypass, lock, stagger, counter bonus | `combat/Parry.java`, `combat/ParryWindow.java`, `combat/ParryState.java` (attachments), `combat/ParryPayload.java` | 2 d |
| 6 | Client input: use key with a blade sends `ParryPayload`; Parry key mapping; guard swing | `client/combat/ParryInput.java` | 0.5 d |
| 7 | NPC parry roll (players and NPCs as attackers), cooldown, hired-unit level bonus hook, `ParryFxPayload` | `combat/NpcParry.java`, `combat/ParryFxPayload.java` | 1 d |
| 8 | Assets: sounds, `parry_spark` particle, marker textures, `stagger` and `parry_<class>` clips per race, `"heavy"` flags written by `build_npc.js`; credits | `assets/lotr_revival/...`, `tools/anim-viewer/build_npc.js` | 1.5 d |
| 9 | GameTests from Acceptance tests, plus logging and the 600-tick summary | `combat/test/ParryTests.java`, `combat/test/WindupTests.java`, `combat/CombatStats.java` | 1.5 d |

Order: 1, 2, 5, 9 (server side, testable headless) then 3, 4, 6, 7, 8.

## Open questions

1. **Longer wind-ups change the feel of every fight.** 10 ticks (0.5 s) normal and 18 heavy are readable but make NPCs hit later than now. Shorter for militia, longer for leaders?
2. **Time-warped clips vs. a separate "ready" pose:** stretching the draw-back of existing clips needs no new animation but may look slow-motion. A short held ready pose per class before the clip is the alternative.
3. **Heavy fully unblockable,** or should heavy shields (or shield wall, owned by hiring) still absorb a share (`heavy.shield_share`, now 0)?
4. **Blade parry input:** the use key with a blade (no conflict today: Middle-earth blades have no use action, its spear throw is commented out) plus an unbound Parry key. Should the Parry key be bound by default, and to what (the hiring spec uses `R`)?
5. **Ranged cues:** add an "aiming at you" marker while an NPC archer draws at you, and a perfect shield parry that reflects arrows?
6. **NPC parry chances by rank** (0-20%, +10% shield, cap 40%): enough to notice, not enough to frustrate? Should militia get 0%?
7. **NPC-versus-NPC parries** default on: good for battles between factions, but they make fights longer. Keep on?
8. **Counter bonus +50% for 30 ticks:** use Middle-earth's unused `critical_chance` instead (a guaranteed crit), if Middle-earth starts using it?
9. **Marker for attacks on others** (small and faded by default): useful for protecting hired units, or clutter in big battles?
