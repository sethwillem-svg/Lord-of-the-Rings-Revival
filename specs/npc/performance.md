# NPC performance and AI budget

- **Pod:** npc (with two small `pod:core` requests and one `pod:world` request)
- **Lead:** Opus 3
- **Status:** draft, revision 3 (maintainer's answers on PR #186: three discipline levels per faction replace per-faction formation picks; formation animation hooks; the content wave waits for the load test; Bree, Dorwinion and Dol Guldur LOOSE, Dale shield wall when defending; reference machine Ryzen 5 5600 / i5-12400 class; rigid numbers are defaults to tune in playtest)
- **Depends on:** `specs/npc/npc-base.md` (goals, targeting, routine, spawning, despawn), `specs/npc/hiring-trading.md` (hired units, squadrons, follow, guard, teleport), `specs/core/core-apis.md` (registers, attachments, payload rules, `/lotr` root), `specs/world/waypoints-travel.md` (road network), draft `specs/npc/nemesis.md` (PR #182: world events, performance governor, chunk tickets, off-screen resolution), draft `specs/world/territory.md` (PR #183: territory updates, wild marches on the events governor), draft `specs/world/structures.md` (PR #184: garrison caps, structure index, `StructureVenues`), draft `specs/core/rendering-animation.md` (PR #185: clip naming `animation.<rig>.<group>.<name>`, `ANIM_ACTION`/`ANIM_PULSE` bytes).
- **Sources:** our own measurements and estimates of this repo's NPC code (section 0); public documentation of server-side techniques we reuse as ideas only (Sources, at the end). Nothing here comes from the original LOTR Mod.

## Summary

The maintainer wants Middle-earth busy: villages full of people, patrols on the roads, garrisons in the forts and real battles of 100 to 200 fighters, on a single server that still runs at 20 TPS. Today every NPC runs its whole brain every tick wherever it is, scans the world for targets, raycasts every candidate and computes every path synchronously on the server thread. That does not scale past a few hundred NPCs.

This spec is the budget and the framework that every later NPC system builds on:

1. **AI level of detail.** NPCs think at full rate only near players. Farther out they think less often; with nobody near they nearly sleep; unloaded groups live on as data.
2. **Squads with discipline.** One leader plans a path; the others hold a formation slot and only steer locally. Each faction fights RIGID (holds formation), ORGANIZED (forms up, charges, breaks) or LOOSE (swarms, collapses without its captain).
3. **Paths off the server thread.** A fixed worker pool computes paths from immutable terrain snapshots; results are applied on the next tick.
4. **One spatial index** per level, bucketed by faction, replaces per-NPC world scans for targets, threats and witnesses.
5. **One worker executor** with a main-thread handoff for nemesis politics, off-screen battles, territory and event scheduling.
6. **Caps and graceful degradation** tied to the same load signal as the world-events governor.
7. **Network discipline.**
8. **A CI load test** that fails the build when we go over budget, plus a spark how-to for playtests.
9. **An incremental migration** of today's goals onto the framework, landing **before** the G3 NPC content wave.

Players never see this spec directly. They see a world that is full and smooth, and armies that move like armies.

## 0. Where the current NPC code spends time

Read on `main` at 2f0b088 plus PR #169 (`NpcMeleeAttackGoal`). Numbers are **estimates** on the reference machine (a mid-range desktop: Ryzen 5 5600 / i5-12400 class, 16 GB) from known costs of the vanilla calls involved; ticket P1 replaces them with measurements from the load test harness running against today's code.

| # | Where | What it does | Estimated cost | Why it matters |
| --- | --- | --- | --- | --- |
| H1 | `ai/combat/NpcTargetGoal` (`findBest`) | Every 20 ticks per guard/captain: `getEntitiesOfClass(LivingEntity)` over a 33 x 17 x 33 box, ranks every candidate (attachment reads, faction relation lookups, hired-unit checks), then a **line-of-sight raycast for every valid candidate**, not just the best. Runs from both `canUse` and `canContinueToUse`. | Idle: ~20 µs per scan. In a 150-fighter melee each NPC sees ~75 enemies: 75 raycasts at 3-10 µs = **0.3-0.8 ms per scan**. | The check time is set from the tick the goal last ran and starts at `-20`, so NPCs spawned together (groups, anchors, event waves) **scan on the same tick forever** (vanilla only splits goal evaluation into two phases by entity id). 150 fighters means ~75 scans in one tick: **a 20-60 ms spike every second**. The single largest problem. |
| H2 | Vanilla `MeleeAttackGoal` (and PR #169's subclass) | Re-paths to a moving target every 4-11 ticks, synchronously. Path node limit = follow range x 16 = 256 nodes (follow range is 16). | 50-300 µs per path. 150 fighters: ~20 paths per tick = **1-6 ms per tick**, more around walls and water. | Most of these paths are 2-6 blocks long across open ground, where a straight steer would do. |
| H3 | `routine/RoutineGoal` | At dusk every home-bound NPC switches mode on the same tick (day time is global) and walks home with a synchronous path, re-issued every 20 ticks with the **same phase for everyone**. | 100-300 µs per path; two villages of 40: **8-24 ms spike at dusk, repeating every 20 ticks** until they arrive. | Every village will do this at once. |
| H4 | `RoutineGoal.tickTravel` | A traveller whose `moveTo` fails stays "done" and calls `moveTo` again **every tick** until its 1,200-tick timeout. | Up to one 256-node A* per tick per stuck traveller (~0.3 ms). | A handful of stuck travellers on cliffs costs a millisecond each tick. |
| H5 | `ai/combat/FleeThreatsGoal` | Every goal evaluation (every 2 ticks) per civilian: entity box scan (radius 8) with a predicate that runs faction relations and alignment for each neighbour; then a random flee position and a synchronous path. | 10-30 µs per evaluation in a village; **~0.3-0.5 ms per tick per village of 40**. | Villagers mostly see other villagers; the work finds nothing. |
| H6 | `hire/ai/UnitGoals.FollowGoal`, `UnitAi.canPathTo` | Every following unit re-paths to its owner every 10 ticks; every 20 ticks a far unit builds a **full path just to test reachability** for teleport. | 20 followers: 2 paths per tick (~0.2-0.6 ms) plus failing 256-node reachability paths when the owner rides off. | 20 units that each path to the same player. |
| H7 | Vanilla stroll, look and creeper-avoid goals | Stroll: random position search plus a synchronous path, ~1 in 120 evaluations. Look-at-player and creeper-avoid: cheap class-filtered scans. | 1,500 NPCs: ~6 strolls per tick, **~0.6 ms per tick**. | Nobody is watching most of them stroll. |
| H8 | Base entity tick | Movement, collision, pushing other entities, effects. Every NPC, every tick, at any distance. | ~15-30 µs per NPC; pushing in a dense battle adds ~3-8 µs. | 1,500 NPCs = **22-45 ms** before any AI. This is why distant NPCs must skip whole ticks. |
| H9 | `RoutineGoal.currentPlan`, `UnitAi.guardArea`, `UnitTargeting.judge` | Small records allocated several times per tick per NPC; `judge` runs per candidate per scan and re-reads attachments and the owner. | Sub-microsecond each, but ~100k short-lived objects per second at 1,500 NPCs. | GC pressure shows up as max-tick spikes. |
| H10 | `spawn/NpcSpawner.currentWildCount` | Counts wild NPCs by iterating every entity in the level, at most every 20 ticks, only during spawn passes. | ~0.1-0.5 ms per pass. | Fine now; replaced by the index's counters for free. |

**Sum.** A 150-fighter battle costs roughly 13-15 ms per tick on average but spikes by 20-60 ms every 20 ticks (H1). 300 other NPCs near players add ~12 ms; 600 farther out at full cost add ~18 ms. About 1,000 NPCs today land at **45-55 ms mean with 80+ ms spikes**, under 20 TPS. Fine for the G2 pilot; not for G3.

Not expensive (checked): the `getAttributeValue` override (a holder compare per call; damage maths only when attacking), retaliation ticking, witness scans on death (event-driven), anchor checks (every 600 ticks), sword orders (only while orders exist), name and speech code.

## Player-facing behavior

1. Near you (within 48 blocks) every NPC behaves exactly as it does today: looks at you, chats, strolls, fights, opens gates. Nothing is ever simplified within 48 blocks of a player.
2. Farther out NPCs keep doing the same things, a little less smoothly: they decide a bit later, stroll less and never chat. You cannot tell from 50+ blocks.
3. Beyond 128 blocks loaded NPCs stand still and wait. A fight that starts there wakes the fighters up.
4. Squads move as squads, and each people fights its own way. Gondor locks shields with pikes over them, dwarves form an unbreakable phalanx, elves volley in ranks, Uruk-hai and Easterlings advance in drilled blocks: hard to beat head-on, slow to turn, so hit them in the flank. Rohan crashes in as a wedge, then fights loose; Rangers wait hidden and skirmish. Orcs, Dunlendings and ruffians come as a fast swarm that scatters when you kill their captain. Rigid blocks march in step, brace shields and lower pikes as you approach, and every routed army visibly flees.
5. If the captain falls, the next in rank takes over; the squad does not mill about.
6. Patrols and caravans that leave loaded chunks keep travelling along their road as data, and appear where they should be when you ride out to meet them. Battles between such groups that nobody sees are decided off-screen.
7. Under heavy load the world gets quieter at the edges (fewer new wild NPCs, fewer events) before anything near a player is simplified.
8. Server admins get `/lotr perf` to see tiers, queues and the load state.

## Rules and numbers

All numbers are config defaults (`lotrrevival-npc-server.toml`, section `[performance]`) unless marked fixed.

### 1. AI level-of-detail tiers

#### 1a. Tiers

Distance is to the nearest non-spectator player in the same level (horizontal and vertical, 3D distance).

| Tier | Enter (promote) | Leave (demote) | What runs |
| --- | --- | --- | --- |
| **FULL** | a player within **48** blocks, checked **every tick** (see 1c) | no player within **56** blocks for **100 ticks** | Every goal at vanilla rates: goal selector every tick (running goals) / every 2 ticks (start checks), target scan every 20 ticks, idle look, chatter and speech, stroll at its normal interval, entity pushing every tick, priority P0/P1 paths. |
| **REDUCED** | a player within **128** blocks (and not FULL) | no player within **144** blocks for **100 ticks** | Goal start checks every **4** ticks and running goals every **2** ticks; target scan every **40** ticks; no idle look-at-player, no speech or ambient lines; stroll interval x3; entity pushing every **4** ticks; squad followers steer only (no own paths unless stuck); P2 paths. |
| **DORMANT** | loaded, no player within 144 | (n/a) | **Settled** NPCs skip whole entity ticks: one maintenance tick every **20** ticks (staggered). Not settled = ticks like REDUCED. Target scan every **100** ticks, enemy NPCs only. No strolling, no routine moves (a routine NPC away from home walks home at REDUCED rates, then settles). P3 paths only. |
| **OFF-SCREEN** | squad members' chunks unloading (section 2i) | squad comes back within 128 of a player in a loaded, entity-ticking chunk | Nothing ticks. The squad is a record in SavedData moved along its route by the worker executor. Static NPCs (villagers, garrisons) are simply saved with their chunk as today. |

**Settled** (DORMANT): on the ground, not in a fluid, not on fire, not riding or ridden, no target, no retaliation or flee mark, navigation idle, not hurt in the last 100 ticks, not in a squad that is moving. A DORMANT NPC that stops being settled ticks every tick until it settles again.

Fixed rule (maintainer): **an NPC with a player within the FULL radius always runs at full fidelity.** No cap, governor state or budget may demote it or thin its goals. Under load only path latency (section 3) and spawning (section 6) may change near players.

#### 1b. Combat raises the tier

| Situation | Tier floor |
| --- | --- |
| Has a target, a retaliation target or a flee mark | REDUCED for as long as it lasts, plus 100 ticks |
| Hit by anything | REDUCED for 200 ticks |
| In a squad whose leader is in combat | REDUCED |
| Taking part in a world event or invasion | REDUCED (events run on force-loaded chunks) |
| Any squad member is FULL | the whole squad is at least REDUCED; the leader is FULL (so it plans with full fidelity for members a player can see) |

**Unwatched combat cap.** Combat with no player within 144 blocks is promoted from DORMANT to REDUCED for at most **160** NPCs server-wide (enough for one full off-screen battle). NPCs beyond the cap stay DORMANT and do not start new fights (their 100-tick scan is skipped) until a player approaches or the event or invasion running them resolves it off-screen (section 5). World events already resolve unwatched events off-screen in the governor's Red state (PR #182); this cap is the steady-state guard.

#### 1c. Tier evaluation

- **Promotion to FULL is immediate:** each tick, for each player, the spatial index (section 4) lists NPCs in the cells within 48 blocks; any of them not FULL is promoted that tick. Player teleports, respawns, fast travel and dimension changes promote at the destination on the same tick.
- **Everything else is lazy:** each NPC re-checks its tier every **20** ticks at its own phase (section 4d), using the player positions list for its level (at most a few dozen players, O(players) per check).
- **Hysteresis:** 8 blocks on each boundary (48/56, 128/144) and a minimum stay of **100 ticks** in a tier before demotion. Promotion never waits.
- **On promotion** an NPC immediately runs one target scan and one goal start check, so it reacts the tick a player arrives.
- **On demotion** nothing is cancelled: a running path or goal finishes at the lower rate.
- Tier is transient (never saved, never synced). A freshly loaded NPC starts DORMANT and is promoted by the same rules on its first tick.

#### 1d. Per-goal behavior by tier

Each installed goal declares its minimum tier and a rate multiplier (see Data and APIs, `TieredGoal`). Current goals:

| Goal (installer) | FULL | REDUCED | DORMANT |
| --- | --- | --- | --- |
| Float, fire panic, door, fence gate (`SafetyGoals`) | yes | yes | when not settled |
| Flee threats (civilians) | yes; threat scan through the index every 4 ticks | yes; every 8 ticks | no (wakes on hit) |
| Creeper avoid (fighters) | yes; via the index's monster bucket | yes | no |
| Target selection | every 20 ticks | every 40 ticks | every 100 ticks, NPC enemies only |
| Melee chase | yes | yes | not settled anyway |
| Heal while idle | yes | yes, every 2nd heal step doubled | heals on maintenance ticks (same HP per second) |
| Routine (`RoutineGoals`) | yes | yes, wander interval x3 | only the walk home |
| Stroll (`IdleGoals`) | interval 120 | interval 360 | no |
| Look at player (`IdleGoals`) | yes | no | no |
| Hired halt, follow, guard (`UnitGoals`) | yes | yes | follow/guard only (owner away rules) |
| Speech, witness lines, idle unit lines | yes | no | no |

### 2. Squads

#### 2a. Model

A **squad** is 2 to **24** NPCs with one **leader**, a **formation**, a **route** and a **state** (MARCH, HOLD, COMBAT, REGROUP, ROUT). Squads are created by the systems that spawn groups (event, invasion, patrol, caravan, garrison alert, hiring). Squad membership is an NPC attachment (`lotrrevival:squad`: squad id, slot index); the squad itself lives in the per-level squad manager and is saved in SavedData `lotrrevival_squads` only while it has members off-screen or a route in progress.

| Role | Does |
| --- | --- |
| Leader | Plans the squad's path (section 3), picks the formation, decides engage and regroup. Moves at the squad's speed: the slowest member's speed x 0.9 while marching, so followers can keep up. |
| Follower | Holds its **slot**: leader position + its offset rotated to the leader's heading. Steers locally; requests its own path only when local steering fails. |

#### 2b. Formations

Spacing is between slot centres. "Front" is the formation's heading. A **block** is one formation body of at most 16 slots; a bigger squad forms several blocks side by side or one behind the other.

| Shape | Layout | Spacing | Used by (through doctrine, 2c) |
| --- | --- | --- | --- |
| **Column** | 2 wide (3 if more than 12), rows behind the leader | 2.0 | Everyone on the march and on roads (road half-width is 4, so a column fits) |
| **Line** | one rank of up to 12 centred on the leader, a second rank 2.0 behind | 2.0 | Organized contact default |
| **Wedge** | leader at the point; row k behind has k + 1 slots | 2.0 | Cavalry and charges (Rohan, Dol Amroth knights) |
| **Shield wall** | two ranks: shield-bearers front, spears and ranged behind; leader in the second rank centre | 1.2 | Gondor defence; needs at least 4 shield-bearers, else line |
| **Pike line** | front rank shields, second rank pikes or spears reaching past them, optional third rank of bows | 1.2 | Gondor attack and hold, Uruk-hai/Isengard drilled ranks |
| **Phalanx** | 3 ranks deep, front rank shields, rear ranks spears, axes and mattocks; the tightest block | 1.0 | Dwarves (Durin's folk, Blue Mountains) |
| **Archer ranks** | 1 rank of melee (blades, spears) in front, 2 ranks of archers behind that volley in turn (2e) | 1.5 | Elves (Lothlórien, High Elves, Wood Elves) |
| **Battalion** | a square block 4 wide and up to 4 deep, spears front, blades inside | 1.5 | Easterlings (incl. Variags) |
| **Beast front** | war-beasts in the front slots, a line of foot 6 blocks behind them | 2.0 behind the beasts | Harad |
| **Open order** | members 4-6 blocks apart in a loose arc, no ranks; uses cover; may wait hidden (2e) | 4-6 | Rangers (skirmish and ambush) |
| **Swarm** | no slots: members keep 3-10 blocks from the leader at a random bearing each, re-rolled every 200 ticks | - | Loose discipline |

- Slot order: higher-ranked and tougher members nearer the front and centre; shields front; pikes and spears second; ranged members always in the rear rank.
- Members without the gear for their slot (a shield-wall slot without a shield) swap with one that has it; if the block lacks the gear, it falls back: shield wall and pike line to line, phalanx to line, archer ranks to line with archers behind, battalion to line, beast front to line.
- **No formation at all:** trolls (any faction), Ents and huorns, wargs and other beasts that are not war-beasts. They join a squad for movement and targets only and never take a slot; they keep within 12 blocks of the leader.
- Where the slot is not standable (wall, water, drop of more than 3), the follower uses the nearest standable spot within 3 blocks of it, else trails the leader's path (2d).

#### 2c. Discipline

Every faction has a **discipline** level in its doctrine data (`squad_doctrine/<faction>.json`, Data and APIs), with optional overrides per unit group (for example Dol Amroth knights inside Gondor, Morgul and Black Númenórean units inside Mordor). Discipline decides how a squad fights, not just how it stands.

| | **RIGID** | **ORGANIZED** | **LOOSE** |
| --- | --- | --- | --- |
| Gameplay intent | Very hard to beat head-on. Slow to turn, so **flanking pays**: hit it from the side or rear, or make it turn. | Forms up and hits hard on contact, then becomes a melee you can pick apart. Recovers if left alone. | Fast, numerous, scary in the open. **Brittle**: kill the captain and it collapses. |
| March | Column, in step (marching animation, 2h) | Column | Swarm around the leader; moves at each member's own speed (no slowest-member limit) |
| Contact | Doctrine's contact shape, held through the fight | Doctrine's contact shape, then charge | No shape: rush the nearest enemies |
| In combat | **Holds formation.** Members keep their slots and only fight targets within reach of their slot (3 blocks; pikes and spears 4; ranged as 2e). Front rank braces shields and blocks frontal hits; rear ranks attack past it. | Charges when the enemy is within 8 blocks, then **breaks** into individual melee (today's design, 2e). | Individual melee from the start; members swarm the target the leader or nearest ally is fighting. |
| Turning | **The block turns as a unit**, pivoting on its centre at its turn rate (default 45° per second, phalanx 30°, wedge charge 60°); slots are rotated, members never run around the block | Slots follow the leader instantly (members re-steer) | n/a |
| Flanks | Shield brace and blocking work only against attackers within 60° of the block's heading. Hits on members from the flank or rear do full damage and count double for the break check | Normal | Normal |
| Break | Breaks to ORGANIZED behaviour when it has lost **50%** of its starting members (config `rigid_break_losses`; Dwarves 65%: "hardest to break") or loses its leader **and** 30% | Breaks at contact by design; ROUT below 25% strength if the type can rout | **Leader death: morale collapse** (below) |
| Regroup | Re-forms 60 ticks after the last enemy within 12 blocks dies, even mid-battle | 100 ticks without targets (2e) | Gathers around the leader, no slots |
| Morale | Never routs while the formation holds | Routs below 25% | Collapses on captain death |

**Loose morale collapse.** When a LOOSE squad's leader dies (or a nemesis captain leading it escapes or dies), each member rolls: 60% rout at once; the rest rout when the squad's losses reach 30%. If a new leader is chosen (2f) the survivors who did not rout follow it. Nemesis traits can change this (the nemesis spec decides; for example a captain whose death enrages instead of scatters): the squad fires a **leader-lost hook** (squad id, discipline, old leader, cause, and whether the leader was a nemesis) that other systems can cancel or replace before the morale roll.

**Faction assignments (maintainer decision, data defaults):**

| Discipline | Factions and units | Shapes (march / contact / defend) |
| --- | --- | --- |
| RIGID | `gondor` | column / pike line / shield wall |
| RIGID | Dol Amroth knights (Gondor unit group) | column / wedge (held through the charge, then re-forms) / line |
| RIGID | `durins_folk`, `blue_mountains` | column / phalanx / phalanx (break at 65%, turn 30°/s) |
| RIGID | `lothlorien`, `high_elf`, `wood_elf` | column / archer ranks / archer ranks (volleys, 2e) |
| RIGID | `isengard` (Uruk-hai) | column / pike line / pike line |
| RIGID | `easterling` (incl. Variags) | column / battalion / battalion |
| ORGANIZED | `rohan` | column / wedge (cavalry), then loose melee / line |
| ORGANIZED | `dale` | column / line / shield wall (when defending) |
| ORGANIZED | `dunedain_north` (Rangers) | open order / open order (skirmish and ambush, no lines) / open order |
| ORGANIZED | `near_harad`, `far_harad`, `harad_jungle` | column / beast front / line |
| ORGANIZED | Morgul and Black Númenórean units (Mordor unit groups) | column / line / line |
| LOOSE | `mordor`, `gundabad`, `angmar` (orcs and hill-men), `dol_guldur` (orcs) | swarm |
| LOOSE | `dunland`, `half_troll`, `ruffians` | swarm |
| LOOSE | `hobbit` (shirriffs and bounders) | swarm |
| LOOSE | `bree`, `dorwinion` (militia) | swarm |
| none | trolls (any faction), Ents, beasts | no slots |

Unlisted factions default to ORGANIZED with column / line / line. Unit groups are entity-type tags (`#lotrrevival:doctrine/<group>`), so new units join a group by tag.

**AI cost per discipline** (main-thread, FULL tier, in combat; reference machine):

| Discipline | Extra work | Budget per member |
| --- | --- | --- |
| RIGID | slot upkeep every tick through the fight, block rotation, brace and flank checks, reach-limited targeting, volley timing | 70 µs |
| ORGANIZED | slots until the charge, then plain melee | 60 µs |
| LOOSE | no slots; one bearing per member re-rolled every 200 ticks | 45 µs |

RIGID is the most expensive, so it is capped (defaults, **tune in playtest**, like the break losses and turn rates above): a block holds at most **16** members; a squad has at most 2 blocks; at most **12 RIGID blocks may hold formation in combat at once** server-wide (192 fighters, enough for two full armies). When a 13th block makes contact, it fights as ORGANIZED (forms up, charges, breaks) until a slot frees up; blocks nearest a player claim slots first. This is a fixed design rule, not a load reaction, so it applies near players too. Formation upkeep is cheaper in REDUCED (slot updates every 4 ticks) and blocks with no player within 144 hold formation without steering until a player comes close.

#### 2d. Movement and re-pathing

**Leader re-paths when:** the destination moves more than 4 blocks; the path's last 25% is reached and the route continues (the next segment is requested at 60% so there is no stop); no progress of 1 block in 60 ticks (stuck: also invalidates the snapshot cells around it, section 3e); the route changes (new order, new target squad).

**Follower steering, in order:**
1. **Direct steer** to the slot when within 12 blocks of it and the straight line is walkable (sampled every block on the navigation grid: step up at most 1, drop at most 3, no fluid with a positive malus, no fire or damaging blocks). No path is computed.
2. **Breadcrumbs:** otherwise follow the leader's current path nodes, offset sideways by the slot's lateral offset, clamped to the walkable width.
3. **Own path** (P1/P2 priority) when more than 24 blocks from the slot or stuck for 40 ticks.
4. More than 64 blocks away for 200 ticks: the follower leaves the squad (becomes a loose NPC with the squad's home or route end as home). Hired units use their own teleport rule instead.

#### 2e. Engagement

| Event | Behavior |
| --- | --- |
| Leader sees an enemy within 16 blocks (or any member reports one) | squad state COMBAT; the leader forms the contact shape facing the enemy if there is time (enemy more than 12 blocks away); then it fights by discipline (2c) |
| ORGANIZED follower acquires a target within 8 blocks of its slot, or is hit | breaks off and fights with its full combat AI; leash 24 blocks from the leader; beyond it, it drops the target unless it is its retaliation target |
| RIGID member | keeps its slot; fights only targets in reach of its slot; front rank braces (2h) while an enemy is within 6 blocks in front; pikes and spears lowered in the second rank; the block turns toward the biggest threat at its turn rate |
| RIGID archer ranks | the leader picks one target point (the nearest enemy group's centre); ranks **volley in turn**: rank A draws together, holds up to 20 ticks for the order, all release within 2 ticks; rank B draws while A nocks again; cadence about 40 ticks per rank. Each archer aims at its own target inside the group (no per-archer scan: the leader's target list is shared) |
| ORGANIZED open order (Rangers) | ambush: members wait crouched and still (no strolling, no speech) at their spots until an enemy comes within 12 blocks or one of them is hit, then shoot and close; they never form lines; they withdraw 8-12 blocks from melee when their bows are loaded |
| ORGANIZED beast front (Harad) | war-beasts charge first; the foot line follows 6 blocks behind and breaks into melee when the beasts reach the enemy |
| LOOSE | everyone rushes; each member takes the nearest enemy the leader or a neighbour is fighting, else its own scan |
| Shared knowledge | members share the squad's target list: a target spotted by one is a candidate for all (no extra scans) |
| No target for any member for 100 ticks (RIGID: 60 ticks with no enemy within 12) | REGROUP: members return to slots; the squad continues when 75% are within 6 blocks of their slots or after 200 ticks |
| Below 25% of starting strength and the squad type can rout | ROUT: flee away from the nearest enemy squad toward the route start or home (rout animation, 2h), and despawn when unseen (event and invasion NPCs only) |

#### 2f. Leader death and succession

- On leader death the new leader is chosen the same tick: highest class (captain > guard > civilian), then unit level (hired) or nemesis rank, then current health, then lowest slot index. Only members within 32 blocks of the old leader count.
- The new leader keeps the route, the cached path and the formation; slots are recomputed (one shift forward). LOOSE squads roll morale collapse first (2c).
- No member within 32 blocks: the squad splits; members within 16 blocks of each other form new squads (if 2 or more), the rest become loose NPCs.
- Squad types flagged `routs_on_leader_death` (invasion waves led by a nemesis, PR #182) rout instead of succeeding.
- Hired squads: the leader is always the owner (a player), never an NPC, so succession does not apply (2g).

#### 2g. Mapping existing and planned groups to squads

| Group | Squad mapping |
| --- | --- |
| **Hired units** | One squad per owner per squadron name (the existing free-text squadron field); unnamed units form the owner's default squad. More than 12 in one squadron splits into squads of 12. The **owner is the virtual leader** while units follow: units take column slots behind the owner on roads and line slots elsewhere. In combat hired squads fight as ORGANIZED whatever their faction's discipline (a player is not a formation anchor in a fight); formation orders for hired units are left to a later hiring spec. Guarding units have no formation (each holds its guard point). Halt, guard, teleport and owner-away rules from `hiring-trading.md` are unchanged. Horn commands address the squad. |
| **Garrisons** (PR #184 slots) | Loose while idle (routine and posts as today). On alert (any member gets a target), all members within the slot's count box join an ad-hoc COMBAT squad led by the highest rank; it dissolves 200 ticks after combat ends and everyone walks back to their post. |
| **Village guards and residents** | Never squads; residents are always loose NPCs. Guards follow the garrison rule. |
| **Wild warrior groups** (spawn tables) | A group spawn of 3+ fighters becomes a squad (column, wander route around its spawn point) for its lifetime. |
| **Warbands** (world events, invasions) | One squad per 8-16 fighters, leader = event lead or nemesis; contact shape from doctrine. |
| **Patrols** (border patrol events, territory road patrols) | Squad on a road route between two venues; march shape column. |
| **Caravans** | Squad with traders and pack animals in the middle slots, guards in front and rear slots; column; speed of the slowest animal. |
| **Off-screen battles** | Between squad records (section 5). |

#### 2h. Animation hooks for formations

Formations need five visual states. They follow PR #185's rules (`specs/core/rendering-animation.md`): clip keys are `animation.<rig>.<group>.<name>[.<variant>]`, looping states go in the `ANIM_ACTION` byte, one-shots in `ANIM_PULSE`, and anything vanilla already syncs costs nothing. **Request to PR #185 / core:** append these values to the `AnimAction` and `AnimPulse` enums (append only, never reorder) and these clips and fallbacks to its tables.

| Hook | Server sets | Clip (humanoid; the `orc` and `half_troll` rigs of PR #185 use the same names) | Fallback | When |
| --- | --- | --- | --- | --- |
| Shield brace | `ANIM_ACTION = BRACE` | `animation.humanoid.defend.brace.shield` | `defend.block.shield` | RIGID front rank with a shield while an enemy is within 6 blocks in front; cleared when none is |
| Pike lower | `ANIM_ACTION = PIKE_LOWER` | `animation.humanoid.hold.pike.lowered` | `hold.pike`, then `hold.sword` | RIGID second rank with a pike, spear or polearm while the block is in COMBAT or advancing on an enemy within 16 |
| Volley and draw in rank | `ANIM_ACTION = VOLLEY_READY` while waiting for the order; the draw and release themselves are vanilla's using-item state (no extra sync) | `animation.humanoid.ranged.bow.ready`, then the existing `ranged.bow.draw` / `ranged.bow.release` | `ranged.bow.draw` (first frame held) | archer ranks between volleys; the leader sends `ANIM_PULSE = ORDER` (`animation.humanoid.emote.order`, fallback `emote.point`) on each volley |
| March in step | `ANIM_ACTION = MARCH` | `animation.humanoid.move.march` | `move.walk` | RIGID squads in MARCH state. The client plays the clip with its phase taken from the level's game time (not the entity's own walk distance), so every member of every marching block steps together with no extra sync |
| Rout / flee | `ANIM_ACTION = ROUT` | `animation.humanoid.move.flee` | `move.run` | members in ROUT (any discipline) and LOOSE members collapsing after their captain dies |

- Sync cost: action bytes change only on state changes (a few per member per minute; volleys about 2 per archer per 40 ticks). A 200-fighter battle stays within PR #185's estimate of about 4 KB/s per player.
- Animations are cosmetic: brace and blocking rules are server-side (2c) and work with the vanilla biped too.
- Animation LOD (PR #185) applies as usual; at the cheapest client tier these show as key poses.

#### 2i. Off-screen squads (packing)

- When a chunk holding a member of a **travelling** squad (patrol, caravan, warband, wild group on a route) unloads, the whole squad is **packed**: every member's full entity data is saved into the squad record, the entities are removed (not killed: no drops, no death events), and the record continues along its route at walking speed (about 3 blocks per second), advanced every 100 ticks on the worker executor.
- When the record's position is in a loaded, entity-ticking chunk within 128 blocks of a player, it is **unpacked** on the main thread: members are recreated from their saved data at their formation slots around the record position (standable spots only; up to 2 squads unpacked per tick).
- Static NPCs (homes, anchors, garrisons, villagers) and hired units are never packed; vanilla saves them with their chunk as today.
- Packing and unpacking never duplicate or lose a member: the record is written and saved before entities are removed; an unpack removes the record's member data in the same tick it adds the entities. Startup recovery: a record whose members also exist as loaded entities (crash between save points) keeps the entities and drops its member data.
- A packed squad's route ends: it unpacks at its destination when a player is near, else it is resolved as arrived (event outcome rules apply) and deleted.

### 3. Pathfinding on worker threads

#### 3a. Shape

```
main thread                         path workers (fixed pool)
-----------                         -------------------------
goal asks PathService for a path
  -> request queued (priority)
start of level tick:
  dispatch up to budget:
    capture: mob profile + snapshot  -> A* over the snapshot
    (copy missing sections)             (no Level access)
                                     <- result queued
start of next level tick:
  drain results: validate, apply to navigation
```

#### 3b. Thread-safety rules (fixed)

1. **Never touch the Level, an entity, a chunk, SavedData, an attachment or any mutable registry off the server thread.** Workers see only the request object.
2. A request carries an immutable **mob profile** captured on the main thread: bounding box size, step height, path malus table (copied), can-open-doors, gate rule, can-float, max node count, start position, goal position(s) and accepted distance.
3. Terrain comes from **snapshots**: copies of chunk section block-state containers made on the main thread (the copy is never written), or derived navigation-grid entries built from those copies. Block entities and entity collisions are not part of a snapshot; the path follower handles them on the main thread (doors and gates are opened by goals as today).
4. Results are plain data (a list of nodes and a reached flag). They are applied on the main thread only, at the start of a level tick.
5. Workers may log only through a rate-limited logger; an exception in a job fails that request (sync fallback, 3h) and is logged once per 5 minutes.
6. Datapack reload or server stop: the pool drains, all pending requests are cancelled and the snapshot cache is cleared before the reload or shutdown continues.

#### 3c. Pool and budgets

| Thing | Value | Notes |
| --- | --- | --- |
| Worker threads | `path_threads` = 0 (auto): clamp(CPU count - 2, 1, 3) | Fixed pool, daemon threads named `lotr-path-N`, below normal priority. 2-CPU machines get 1 |
| Queue | 512 jobs | Full queue: the lowest-priority oldest job is dropped and its requester is told "retry" |
| Dispatch per tick | 24 requests, and at most 48 section copies | Server-wide, P0 first |
| Results applied per tick | 64 | The rest wait a tick (results are cheap to apply) |
| Node limit per job | follow range x 16 (256 today) for chase and short goals; 1,024 for leader segments | Off-thread cost does not hit the tick, but bounds latency |
| Latency target | P0 p95 <= 2 ticks, P1 <= 5, P2 <= 20 | Measured by the load test |

#### 3d. Priority

| Priority | Who | Notes |
| --- | --- | --- |
| P0 | FULL NPCs in combat (chase, flee) | |
| P1 | FULL non-combat: squad leaders, hired followers, routine walks, stuck followers | |
| P2 | REDUCED | |
| P3 | DORMANT (walk home), road segment prefetch, reachability checks | Dropped in the Red load state |

Waiting requests age up one level every 20 ticks. **One outstanding request per NPC:** a new request supersedes the old one (generation counter); the old result is discarded on arrival.

#### 3e. Snapshots and the navigation grid cache

- **Unit:** one chunk section (16 x 16 x 16). A request needs the sections covering the box from start to goal, expanded by 8 blocks horizontally and 4 vertically (capped at 7 x 7 x 4 sections for leader segments, 3 x 3 x 2 for chase paths).
- **Cache:** per level, keyed by section position; holds the copied states plus a lazily built byte-per-block walkability classification (built by the worker on first use). Entries live **200 ticks**, are refreshed on reuse, max 4,096 sections (about 20-40 MB; config).
- **Invalidation:** block changes in the section (neighbour-notify block events, explosions, our own war-camp and wild-march placements, piston moves), chunk unload, and stuck detection (the 3 x 3 sections around a stuck NPC). An invalidated section is recopied on next use.
- **Stale results are acceptable:** a path through a block placed after the snapshot is caught by stuck detection, which invalidates and re-requests. A path is never trusted for safety: the follower still checks the next node is standable before stepping onto it.

#### 3f. Close-quarters steering (no path)

For a melee chase, when the target is within **6** blocks, at most 1 block higher or 3 lower, and the straight line is walkable (as 2d), the NPC steers straight at it and requests nothing. This removes most battle paths (H2). Further out, or blocked, it requests a P0 path and keeps steering until the result arrives.

#### 3g. Path reuse and roads

- **Segment cache:** computed paths are stored keyed by (start 4 x 4 cell, goal 4 x 4 cell, profile class); reused by any NPC with the same profile class for **6,000 ticks** unless a section it crosses was invalidated. Patrols repeating a loop compute it once.
- **Roads** (core `RoadRoutes`, see Data and APIs): a leader whose destination is more than 64 blocks away plans a **road route** first: nearest road entry point, the road graph's shortest route (junctions and ends as graph nodes, computed off-thread once per server start and cached), then the exit. Road stretches need no A*: the leader follows the road centreline samples (offset by formation) because roads are flat, 8-9 blocks wide and bridged by design. A* is used only for the off-road ends (at most 64 blocks each) and for detours when stuck on the road.
- Without road data (other dimensions, no roads nearby), long trips are chained A* segments of up to 48 blocks.

#### 3h. Cancellation, staleness and fallback

| Case | Rule |
| --- | --- |
| Result for a removed, dead, unloaded or other-level NPC | dropped |
| Superseded request (newer generation) | dropped |
| Goal moved more than 4 blocks (chase: more than 2 blocks) since the request | dropped and re-requested if the NPC still wants it |
| Older than 40 ticks | dropped |
| Pool disabled (`async_paths = false`) | everything synchronous, as today |
| P0 request with no current path and no result after **4** ticks, target within 16 | **synchronous fallback** on the main thread with a 128-node limit; at most **4 sync fallbacks per tick** server-wide |
| Worker exception | that request falls back to sync once |
| GameTests that assert exact paths | `PathService` inline mode (work runs immediately on the calling thread) |

Mixing: the result must equal what synchronous vanilla pathing would return on the same terrain, for the same profile. A GameTest compares both on fixture terrain (walls, water, doors, gates, stairs, slabs).

### 4. Targeting through a spatial index

#### 4a. Index

Per level, a grid of **16 x 16 block columns** (chunk-aligned cells, full height; Y is filtered per query). Each cell holds buckets:

| Bucket | Contents | Maintained by |
| --- | --- | --- |
| One per faction (by faction registry index) | NPCs (`FactionMember`) and tag-faction entities | join/leave events, cell-change check in their tick |
| Players | non-spectator players | player tick |
| Monsters | vanilla monster-category mobs | join/leave and a cell-change check in the entity tick event (a few ns per entity) |

- Each cell also keeps counts per bucket and per tier, giving free per-chunk counts for caps (section 6) and the wild spawn count (replaces H10).
- **Hostility matrix:** faction x faction relation bits precomputed on datapack load and reload (from core relations, including the Utumno rule). A query for "enemies of F" walks only the hostile buckets.
- **Player hostility cache:** per (player, faction) the deterministic hostility and the grey-zone chance, invalidated by core's alignment-changed and pledge-changed events.
- The index is main-thread only. Workers that need positions get a copied view (section 5).

#### 4b. Target scan

1. Retaliation slot first (event-driven, as today).
2. Candidate gathering: cells within the sense range (16 horizontal, 8 vertical), hostile faction buckets, the players bucket (cached hostility), the monsters bucket; plus the squad's shared target list. Ranks as today (attacker, owner target, player, NPC, monster; hired-unit verdicts).
3. Sort by (rank, distance); **line-of-sight checks only in that order, at most 4 per scan**; the first visible candidate wins. If none of the 4 is visible, no new target this scan.
4. While fighting, the re-check every scan interval may switch only to a strictly better rank, as today.

#### 4c. Scan intervals

| Tier | Interval | LOS checks per scan |
| --- | --- | --- |
| FULL | 20 ticks | 4 |
| REDUCED | 40 ticks | 2 |
| DORMANT | 100 ticks (NPC enemies only) | 1 |

Civilian threat scans (flee) use the same index at the rates in 1d and need no LOS. Witness reactions on a player kill query the index's own-and-ally buckets.

#### 4d. Staggering and per-tick budget

- Every periodic job (target scan, tier check, routine re-path, follow re-path, teleport check, heal step, anchor checks) runs when `(gameTime + phase) % interval == 0`, where `phase` is a mixed hash of the entity id (not `tickCount`, which stops while a DORMANT NPC skips ticks). Spawn time and synchronized day-time changes therefore never line up NPCs.
- **Dusk and dawn:** a routine mode change is applied at the NPC's own phase within the first 100 ticks after the boundary, not on the boundary tick.
- **Scan budget:** at most **96** target scans per tick per level. Extra scans wait for the next tick in a round-robin queue (FULL-in-combat first). Retaliation never waits.

### 5. Other off-thread work and the handoff API

#### 5a. What goes off-thread

| Work | Owner | Inputs captured on main | Applied on main |
| --- | --- | --- | --- |
| Nemesis politics (every 30 min) | npc (PR #182) | copy of the nemesis records and faction relations | record changes, rumours, events |
| Off-screen battle resolution | npc | the two squad records (ranks, counts, seeds) | deaths, renown, squad updates, territory deltas |
| Off-screen squad movement | npc | squad records, road route | new positions; unpack requests |
| World-event scheduling rolls (site search part) | npc (PR #182) | player positions, cooldowns, a copied venue list | the event start (spawns, tickets) |
| Territory drift and square housekeeping | world (PR #183) | copy of province and square data | the new values; flips and their side effects |
| Structure index queries (`StructureVenues.find`) | world (PR #184) | an immutable per-region index view | the chosen venue |
| Road graph build and road routes | world backend, npc requester | the road network (immutable after load) | the route |

Anything that must spawn, remove, place blocks, send packets, fire events or write SavedData does so in the apply step.

#### 5b. The handoff API (core)

Because the world pod also needs it, the executor and the load signal live in core (`pod:core` request, section Data and APIs):

- **Submit:** a named job with a priority, a deadline in ticks, a capture step (runs immediately, on main, must be cheap and return immutable data), a work step (runs on a worker, gets only the captured data), and an apply step (runs on main, gets the result). Returns a handle with `cancel()` and `isDone()`.
- **Apply budget:** apply steps run at the start of the server tick, in submit order per job name, until **2 ms** have been spent; the rest wait for the next tick. A job past its deadline is dropped before apply (its apply step is told so, to clean up).
- **Epoch:** the executor carries a server epoch, bumped on datapack reload and on stop; results from an old epoch are dropped.
- **Executor:** one single-thread `lotr-sim` worker (ordered, so jobs of one system never race each other), separate from the path pool. Queue 256; a full queue rejects with a "try later" result and logs once.
- **Inline mode** for GameTests: capture, work and apply run immediately in order on the calling thread.
- **Rules** are the same as 3b.

### 6. Caps and graceful degradation

#### 6a. Load signal (shared with the world-events governor)

One server-wide **load state**, computed once per second in core from our own tick timing (server tick start to end, all levels), shared by NPC caps, world events (PR #182) and territory's wild marches (PR #183):

| State | Enter | Leave |
| --- | --- | --- |
| Green | mean tick time over the last 100 ticks < 30 ms | |
| Yellow | 30-40 ms | below 25 ms (5 ms hysteresis) after at least 10 s in the state |
| Red | > 40 ms | below 35 ms after at least 10 s |

These are exactly PR #182's governor numbers. **Required change to PR #182:** the governor reads this shared state (and keeps its own chunk-budget rule on top) instead of computing its own average. The signal also exposes p95 and max over the last 100 ticks and the current chunk-ticket budget use, for `/lotr perf` and the load test.

#### 6b. Caps

| Cap | Value | When reached |
| --- | --- | --- |
| Per chunk | 16 NPCs spawned into one chunk by any spawner (wild, garrison, anchor, event, unpack); 32 present | spawns refused; unpack spills to neighbour chunks, else waits |
| Per structure | the sum of its slots' caps (PR #184), at most 64; towns 120 | slot refills wait |
| Per area (wild) | today's rule: 100 per 196 eligible chunks, with multiplayer damping | wild spawns stop |
| FULL server-wide | 300 | **never demoted**; new spawns within 56 blocks of a player are refused or deferred (events and invasions spawn their next wave later); unpacking near players waits |
| REDUCED server-wide | 600 | the farthest REDUCED NPCs not in combat are demoted to DORMANT |
| Loaded total | 1,500 | wild spawning stops; DORMANT wild non-persistent NPCs (never home-bound, hired, named, nemesis or event) despawn farthest first; travelling squads in DORMANT are packed early |
| Unwatched combat | 160 (1b) | no new unwatched fights |

#### 6c. Degradation by load state

| State | NPC effects (FULL is never touched) |
| --- | --- |
| Green | normal |
| Yellow | wild spawn cap x 0.5; REDUCED radius 128 -> 96 (beyond is DORMANT); REDUCED goal start checks every 8 ticks; P2 and P3 path dispatch halved; world events as PR #182 Yellow |
| Red | no wild spawns, no garrison refills, no unpacking farther than 64 blocks from a player; REDUCED radius -> 80; P3 paths dropped; after 30 s in Red, DORMANT travelling squads are packed and DORMANT wild non-persistent NPCs beyond 96 blocks despawn; world events as PR #182 Red |

State changes are logged once at INFO and shown in `/lotr perf`.

### 7. Network

| Rule | Value |
| --- | --- |
| Synced entity data per NPC | at most what is needed to draw it: today role, gender, name and name seed. New synced fields need lead review; prefer one packed int over several fields; strings are set once at spawn and never per tick. AI state (tier, squad state, path) is never synced |
| Tracking range | civilians 6 chunks (96 blocks), fighters and mounts 8 chunks (128 blocks, today's value) |
| Update interval | vanilla default (3 ticks) for now. A distance-scaled send interval (every 6 ticks beyond 64 blocks, every 10 beyond 96) needs a mixin; it is a separate ticket that runs only if the load test shows entity tracking above 10% of tick time, and needs Opus 1's approval (AGENTS.md: no mixins without lead approval) |
| Custom payloads | never per tick. Event-driven only (screens, orders, map markers); status lists (unit screen, squads) at most once per 20 ticks per player and only while a screen that shows them is open |
| Formation and squad state | not sent; clients see positions and vanilla animations only |
| Admin tip | servers with very large armies can lower `entity-broadcast-range-percentage` in `server.properties` |

### 8. CI load test and profiling

#### 8a. Scenarios

Built by a perf GameTest that runs only when the system property `lotrrevival.perf=true` is set (a conditional test generator), so normal `runGameTestServer` stays fast. It uses its own area far from the other tests (x >= 10,000), holds its chunks with a test-only ticket controller, lays a flat floor and simple box buildings, and places mock players (removed in a `finally`).

| Scenario | Contents | Players |
| --- | --- | --- |
| **S1 target battle** | 1 battle of 200 fighters (2 sides x 10 squads of 10, hostile factions; one side RIGID, the other LOOSE) + 2 villages of 40 + 10 patrol squads of 8 on a 200-block loop road | one in the battle centre, one in village 1 |
| **S2 worst case** (the CI gate) | 3 battles of 150 (2 sides x 75 in squads; battle A is RIGID (pike line and archer ranks) vs LOOSE, B is ORGANIZED vs LOOSE, C is RIGID vs RIGID so the rigid-block cap is hit), 2 villages of 40 (civilians, guards, homes, routines), 10 patrolling squads of 8 | battle A centre (FULL), 100 blocks from battle B (REDUCED), village 1; battle C and village 2 have no player within 144 (DORMANT, with the unwatched-combat cap) |

Factions: whatever hostile NPC pairs exist when the test runs (today man guards vs a hostile test faction override); the test picks from data so new races are covered automatically.

#### 8b. Run and measure

1. Warm-up 400 ticks (JIT), excluded.
2. Measure **1,200 ticks** (60 s): per-tick server tick time (our own timing in core), and our own counters: time in NPC AI per tier, path dispatch and apply, index updates and queries, sim apply, path queue depth and latency per priority, sync fallbacks, LOS checks per tick, tier counts, time per member by discipline, rigid blocks holding vs over the cap, GC pauses (from the JVM's GC beans).
3. Write `build/reports/npc-perf/report.json` and `report.md` (mean, p50, p95, p99, max, per-phase breakdown, top counters); CI uploads them as an artifact and prints the markdown summary to the job log.
4. Fail if a threshold is exceeded (8c), or if any battle did not resolve to at least 50% casualties on one side within the run (proves AI was actually fighting, not frozen).

#### 8c. Thresholds and scaling

CI runners are slower and noisier than the maintainer's server. The maintainer describes the runner as 2 CPU; GitHub's current docs give **4 vCPU** for standard Linux runners on public repos (this repo is public). The test works on either:

- **Calibration:** before the scenario the test runs a fixed CPU workload in the same JVM (a set of synchronous paths and raycasts on fixed fixture terrain, the kind of work we do), giving `calib_ms`. The scale is `k = calib_ms / reference_calib_ms`, where `reference_calib_ms` is recorded once on the **reference machine** (a mid-range desktop, Ryzen 5 5600 / i5-12400 class, 16 GB; maintainer decision, standing in for the server) and committed as `perf/reference.json`. k is clamped to [1, 4].
- **Gate (S2):** mean <= **35 x k** ms, p95 <= **50 x k** ms, and fewer than 1% of ticks over **100 x k** ms. (Max alone is too noisy on shared runners; it is reported, not gated.)
- **S1** is reported and gated at mean <= 35 x k (the maintainer's battle target).
- **Regression check:** each run compares with the last report from `main` (artifact); more than 20% worse mean is a warning at first and becomes a failure once three consecutive main runs are within 10% of each other.
- Path pool size on CI follows the auto rule (1 thread on 2 CPUs, 2 on 4).
- Runs as its own CI job `perf-test` (after `build`, timeout 20 min), with a Gradle run `runPerfTest` (a `gameTestServer` run with `lotrrevival.perf=true`). It touches `build.gradle` and the workflow (needs Opus 1).

#### 8d. Budget per tier (reference machine: Ryzen 5 5600 / i5-12400 class, 16 GB)

Main-thread cost per NPC per tick, averaged over the run. The load test reports these and fails the ticket that breaks them (not the build) when more than 25% over:

| Tier | Budget |
| --- | --- |
| FULL, in combat | 60 µs (by discipline: RIGID 70, ORGANIZED 60, LOOSE 45; section 2c) |
| FULL, idle | 20 µs |
| REDUCED, in combat | 25 µs |
| REDUCED, idle | 8 µs |
| DORMANT | 1.5 µs |

Check against the target: 300 FULL (half fighting) ~12 ms + 600 REDUCED ~6 ms + 600 DORMANT ~1 ms = ~19 ms of NPC work, leaving ~16 ms for vanilla, players, chunks and the other pods within the 35 ms aim.

#### 8e. Spark how-to for playtests

1. Install the **spark** mod build for NeoForge 1.21.1 on the server (and client for singleplayer).
2. Watch for spikes: `/spark tickmonitor --threshold-tick 50` reports every tick over 50 ms in chat.
3. Profile a big moment (a battle, dusk in a town) for 2 minutes, all threads so the path workers show: `/spark profiler start --timeout 120 --thread *`.
4. Profile only the spikes: `/spark profiler start --only-ticks-over 50 --timeout 300`.
5. Overall health: `/spark health` (TPS, MSPT, CPU, memory, GC).
6. Our own view: `/lotr perf` (tier counts, load state, queue depths, per-tier µs, top 5 costliest NPC types) and `/lotr perf report` (writes the same report as CI to the server's `lotrrevival/perf/` folder).
7. In the spark viewer, search `lotrrevival` and note: time under NPC entity ticks, `lotr-path-*` threads (busy is fine; a long queue is not), the server thread's entity tracker (network), GC. Paste the spark link and the `/lotr perf report` file into the ticket or the playtest checklist.

### 9. Migration of existing goals

Incremental; every step keeps behavior and tests green and is measured by the harness from P1.

| Step | Change | Fixes |
| --- | --- | --- |
| M1 | Measure today's code with the harness (no behavior change) | baseline numbers replace section 0's estimates |
| M2 | Stagger helper and phases: target goal, routine re-paths, follow re-paths, teleport check, dusk/dawn spread | H1 spikes, H3 spikes |
| M3 | Spatial index + hostility matrix; target goal, flee goal, creeper avoid and witnesses query it; LOS capped at 4 per scan | H1 cost, H5, H10 |
| M4 | Tiers: tier manager, tier gating of goals (wrapping existing goals in the installers, no rewrites), pushing gate, DORMANT tick skipping | H7, H8 |
| M5 | Path service (async, snapshots, budgets, fallback) behind `async_paths`; NPC navigation routes `moveTo` through it; melee chase uses close-quarters steering | H2, H4 (failure backoff: a failed request waits 40 ticks), H6 |
| M6 | Reachability check for hired-unit teleport becomes a P3 async request | H6 |
| M7 | Squads: model, formations, steering, succession; hired squadrons and wild groups mapped | |
| M8 | Road routes and the segment cache | |
| M9 | Caps, load signal integration, degradation; PR #182's governor on the shared signal | |
| M10 | Off-screen packing and off-screen battle resolution | |
| M11 | Allocation cleanup in routine plans, guard areas and unit judging | H9 |

## Edge cases

- **Multiplayer:** tiers use the nearest player of any kind (not only owners); two players at opposite ends keep two FULL islands. Spectators never count. A player in creative counts (they still see NPCs).
- **Fake players** (other mods' machines): never count for tiers (`isFakePlayer`).
- **Death, logout, dimension change:** a logout or dimension change removes the player from tier sources at once; NPCs near them demote lazily with hysteresis. Hired units travelling with their owner (fast travel) are promoted at the destination on arrival.
- **Teleports and fast travel:** promotion at the destination the same tick (1c).
- **Server stop:** path pool and sim executor drain and cancel; packed squads are saved; apply steps of pending sim jobs are not run (their captures are discarded, records unchanged).
- **Datapack reload:** hostility matrix and player hostility cache rebuilt; snapshot cache and segment cache cleared; epoch bumped.
- **World events and chunk tickets:** event NPCs are at least REDUCED (1b); force-loaded chunks with no player keep them out of DORMANT so events play out. Red-state shedding (PR #182) resolves them off-screen.
- **Chunk unloads mid-path:** the result is dropped (NPC unloaded); travelling squads pack (2i).
- **NPC on a boat or mount:** never settled; mounts of riders share the rider's tier; mount pathing uses the rider's request.
- **Hired units of an offline owner:** owner-away rules; they are static, can go DORMANT, and are never packed.
- **Interaction with other pods:** territory (PR #183) drift and housekeeping and the structure index (PR #184) may use the core executor; both must hand immutable views to the worker (**required note to PR #184:** the index exposes a read-only per-region view safe to read off-thread, or queries are captured on main and only the ranking runs off-thread).
- **Determinism in GameTests:** inline modes for paths and sim jobs; tier distances overridable per test.

## Data and APIs

**NPC pod (new package `npc.perf`, plus `npc.squad`):**

| Name | What |
| --- | --- |
| `NpcTier` | enum FULL, REDUCED, DORMANT (OFF-SCREEN is a squad-record state, not an entity tier) |
| `TierManager` | per level: promotion sweep, lazy checks, hysteresis, combat floors, unwatched-combat cap; `tier(npc)` |
| `TieredGoal` | wrapper used by goal installers: minimum tier, start-check interval per tier, running-tick rate per tier; wraps existing goals without changing them |
| `Phases` | the stagger helper (`due(entity, interval, gameTime)`) |
| `NpcSpatialIndex` | per level grid, faction/player/monster buckets, per-cell counts, hostility matrix, player hostility cache; queries: `enemiesNear`, `alliesNear`, `playersNear`, `countIn(chunk)` |
| `PathService` | requests, priorities, budgets, snapshot cache, segment cache, workers, results, fallback, inline mode |
| `Squad`, `SquadManager`, `Formation`, `SquadDoctrine` | squads (section 2); attachment `lotrrevival:squad`; SavedData `lotrrevival_squads`; data `data/<ns>/lotrrevival/squad_doctrine/<faction>.json`: `discipline` (`rigid`, `organized`, `loose`), `march`, `contact` and `defend` shapes, optional `break_losses` (RIGID, default 0.5), `turn_rate` (degrees per second, default 45), `rout_on_leader_loss` chance (LOOSE, default 0.6) and `groups` (overrides per unit tag `#lotrrevival:doctrine/<group>`, same fields); datagen writes the defaults of section 2c. Leader-lost hook as a NeoForge event (`SquadLeaderLostEvent`, cancellable, carries whether the leader was a nemesis) |
| `PerfCaps` | caps and degradation (section 6) |
| `PerfStats` | counters and per-tier timing used by `/lotr perf` and the load test |
| NpcEntity hooks (small, additive) | tier-gated `serverAiStep` rates and pushing; `EntityTickEvent.Pre` cancellation for settled DORMANT NPCs (NeoForge's pre-tick entity event is cancellable) |
| Commands | `/lotr perf`, `/lotr perf report`, `/lotr perf tier <entity>` (op level 2) |
| Config `[performance]` | radii and hysteresis, intervals, `async_paths`, `path_threads`, budgets, cache sizes, caps, unwatched-combat cap, scenario thresholds |

**Core requests (`pod:core`, Opus 1):**

1. `core.api.ServerLoad`: per-tick timing of the whole server tick, mean/p95/max over 100 ticks, the Green/Yellow/Red state with 6a's thresholds and hysteresis, chunk-ticket budget input from npc world events, an injectable time source for tests.
2. `core.api.OffThread`: the sim executor and handoff API of 5b (submit/cancel/apply budget/epoch/inline mode).
3. `core.api.RoadRoutes` holder (default empty): nearest road entry within a distance, road graph route between two points, centreline samples of a route. World registers the backend from its road network.

**World request (`pod:world`, Opus 2):** the `RoadRoutes` backend (graph build off-thread at server start from the existing road network), and a thread-safe read view of the structure index if world wants `StructureVenues.find` off-thread.

**Build/CI (Opus 1):** `runPerfTest` run in `build.gradle`, `perf-test` job in the workflow.

## Out of scope

- Multithreaded entity ticking or tracking (Paper-fork style); we keep vanilla's single server thread for entity logic.
- Client-side rendering performance (models, LOD meshes for large armies): a separate client spec if playtests need it.
- Mounted combat and ranged combat AI themselves (their own specs); they must declare tiers and use the path service and index. This spec only fixes how they sit in formations (wedge charges, volleys).
- Formation orders for hired units (maintainer decision: a later feature; hired squads fight ORGANIZED for now).
- The clips themselves: authored under PR #185's pipeline; this spec only names the hooks.
- The distance-scaled tracker update mixin (separate, conditional ticket).
- Invasion rules (invasion spec); this spec only gives them squads and budgets.

## Acceptance tests

- [ ] GameTest: tiers: an NPC 40 blocks from a mock player is FULL; at 60 it stays FULL for 100 ticks then demotes to REDUCED; at 150 it becomes DORMANT; a player teleporting next to a DORMANT NPC promotes it the same tick; spectators and fake players do not count.
- [ ] GameTest: combat floors: a DORMANT NPC hit by a skeleton becomes REDUCED for 200 ticks; the unwatched-combat cap holds at the configured value.
- [ ] GameTest: settled DORMANT NPCs skip ticks; an NPC in water, on fire or falling never skips; a skipped NPC does not drift or drown over 2,000 ticks.
- [ ] GameTest: the FULL radius is never shrunk by any load state (inject Red through the test time source).
- [ ] GameTest: stagger: 40 NPCs spawned on one tick run their target scans spread over at least 10 different ticks of every 20; dusk re-pathing spreads over 100 ticks.
- [ ] GameTest: spatial index: buckets and counts stay right through moves across cell borders, unloads and deaths; enemy query returns the same candidates as a brute-force box scan on a fixture; LOS checks per scan <= 4.
- [ ] GameTest: target priorities unchanged from `npc-base.md` (existing combat tests pass unchanged on the index).
- [ ] GameTest: path service: async results equal synchronous vanilla paths on fixture terrain; superseded, stale and unloaded results are dropped; queue overflow drops lowest priority; sync fallback after 4 ticks with the per-tick limit; inline mode deterministic.
- [ ] GameTest: snapshot invalidation: a wall placed across a cached path causes stuck detection, invalidation and a new path within 80 ticks.
- [ ] GameTest: close-quarters steering: a guard chasing a target 4 blocks away on flat ground makes no path requests.
- [ ] GameTest: squads: column slots on a straight road; followers direct-steer; leader death promotes the right member; split when no one is within 32; regroup after 100 ticks without targets; shield wall holds (members ignore targets beyond 3 blocks of their slot).
- [ ] GameTest: doctrine data: every enabled faction resolves a discipline; unit-group overrides win (a Dol Amroth knight squad charges in a held wedge inside Gondor); unlisted factions default to ORGANIZED; trolls never take a slot.
- [ ] GameTest: RIGID holds: a Gondor pike line attacked head-on keeps every member within 1.5 blocks of its slot until 50% losses, then breaks to ORGANIZED; a dwarf phalanx holds until 65%.
- [ ] GameTest: RIGID turning: an enemy appearing 90° to the side makes the block rotate at no more than its turn rate (45°/s, phalanx 30°/s); members keep their relative slots while it turns.
- [ ] GameTest: flanking: a braced front-rank shield-bearer blocks a frontal melee hit and takes full damage from the same hit from the side or rear; flank hits count double toward the break check.
- [ ] GameTest: volleys: an elven archer block's rank A releases within 2 ticks of each other; ranks alternate; no archer runs its own target scan while the leader's list has targets.
- [ ] GameTest: ORGANIZED: a Rohan squad forms a wedge, charges, breaks into individual melee within 8 blocks and regroups 100 ticks after the last enemy dies; Rangers wait still in open order until an enemy is within 12 blocks.
- [ ] GameTest: LOOSE: no slots, members within 3-10 blocks of the leader; on captain death (fixed seed) about 60% rout at once and the rest rout at 30% losses; a cancelled leader-lost event stops the roll.
- [ ] GameTest: rigid block cap: with 13 RIGID blocks in contact, exactly 12 hold formation (nearest a player first) and the 13th fights ORGANIZED until one frees up.
- [ ] GameTest: animation hooks: BRACE is set on the front rank when an enemy enters 6 blocks in front and cleared when it leaves; PIKE_LOWER on the second rank in COMBAT; VOLLEY_READY between volleys with an ORDER pulse per volley; MARCH only while a RIGID squad marches; ROUT on routing members; each byte changes only on state changes (count of entity-data updates per member per minute stays under 6).
- [ ] GameTest: hired squadrons form squads per owner per squadron name and keep all existing hiring tests green.
- [ ] GameTest: packing and unpacking a patrol across an unload/load keeps member count, names, gear and health exactly; no duplicates after a simulated crash between saves.
- [ ] GameTest: caps: per-chunk spawn refusal; REDUCED over cap demotes the farthest non-combat NPCs; loaded-total cap despawns only eligible wild NPCs; FULL NPCs are never demoted.
- [ ] GameTest: `ServerLoad` states with injected tick times match PR #182's governor table (shared).
- [ ] Load test S2 passes its scaled thresholds on CI; S1 reports mean <= 35 x k; per-discipline member costs are within 25% of 2c's budgets.
- [ ] Playtest: attack a Gondor pike line head-on and lose; flank it and win. Kill an orc captain and watch the swarm scatter. Watch an elven block volley in turns and a Uruk column march in step.
- [ ] Playtest: on the maintainer's server, a 150-200 fighter battle with the player in it holds 20 TPS; `/spark tickmonitor --threshold-tick 50` reports no recurring spikes; `/lotr perf` shows sensible tier counts; dusk in a busy town shows no spike.

## Tickets

Proposed, not opened until this spec is approved. 1-2 day tickets, `pod:npc` unless marked. **Gate (maintainer decision):** the G3 NPC content wave **waits until the perf load test passes** (P12 green on CI with S1 and S2 gating). P1-P12 with P11a-P11c are that pre-content set; P13-P17 can follow during G3.5. Suggested agents follow the current npc rotation; Opus 3 adjusts when opening.

| # | Pod | Ticket | Days | Needs | Shared files |
| --- | --- | --- | --- | --- | --- |
| P1 | npc + core | Perf harness and baseline: `lotrrevival.perf` conditional GameTest, scenario builders (battle, village, patrol), mock players, tick timing, report writer (json/md), calibration workload; run on today's code and post numbers in the ticket | 2 | - | `build.gradle` (`runPerfTest`), workflow (`perf-test` job, report-only, not gating yet): Opus 1 |
| P2 | core | `core.api.ServerLoad` (timing, mean/p95/max, Green/Yellow/Red with hysteresis and hold, injectable time source, chunk budget input) | 1 | - | core |
| P3 | core | `core.api.OffThread` executor and handoff API (capture/work/apply, apply budget, deadlines, epoch, inline mode) | 1.5 | - | core |
| P4 | npc | Stagger helper and phases across target goal, routine, follow, teleport check, anchors; dusk/dawn spread; travel failure backoff | 1 | P1 | `NpcEntity` (none expected) |
| P5 | npc | Spatial index, hostility matrix, player hostility cache, per-cell counts; migrate target goal (LOS cap), flee goal, creeper avoid, witnesses, wild count | 2 | P4 | - |
| P6 | npc | Tier manager, `TieredGoal`, tier gating in the existing installers, pushing gate, DORMANT tick skipping, combat floors, unwatched-combat cap, `/lotr perf tier` | 2 | P5 | `NpcEntity` (small additive hooks) |
| P7 | npc | Path service part 1: request API, priorities, generations, snapshot copies and cache with invalidation, worker pool, budgets, result apply, inline mode, equality GameTests | 2 | P1 | - |
| P8 | npc | Path service part 2: navigation integration (`moveTo` through the service), sync fallback, close-quarters steering in melee chase, hired follow/guard and routine on the service, teleport reachability as P3 | 2 | P7, P6 | - |
| P9 | npc | Caps and degradation (`PerfCaps`) on `ServerLoad`; spawners, anchors and unpacking honour per-chunk/structure/area/tier caps | 1.5 | P2, P6 | - |
| P10 | npc | Squads part 1: model, attachment, manager, base formations (column, line, wedge, swarm), blocks, slot assignment and gear swaps, follower steering and breadcrumbs, doctrine data format (discipline, shapes, groups) and datagen of 2c's defaults | 2 | P8 | - |
| P11 | npc | Squads part 2: ORGANIZED engagement (charge and break), shared targets, regroup, rout, leader succession and split; hired squadrons (always ORGANIZED), garrison alerts and wild groups mapped | 2 | P10 | - |
| P11a | npc | RIGID discipline: hold-through-combat, reach-limited targeting, block rotation at turn rate, frontal brace and flank rule, break at losses, re-form, rigid block cap; shapes shield wall, pike line, phalanx, battalion | 2 | P11 | - |
| P11b | npc | Archer ranks and volleys; open order with ambush (Rangers); beast front (Harad); LOOSE swarm, morale collapse and `SquadLeaderLostEvent`; trolls and beasts with no slot | 2 | P11 | - |
| P11c | npc + core | Formation animation hooks: append `BRACE`, `PIKE_LOWER`, `VOLLEY_READY`, `MARCH`, `ROUT` and pulse `ORDER` to PR #185's enums (core) and its clip and fallback tables; set them from squad states; game-time phase for `move.march` in the client wrapper | 1 | P11a, P11b, PR #185's anim API | core `AnimAction`/`AnimPulse` (append only), `client/core/anim` resolver (Opus 1) |
| P12 | npc + core | Load test gating: thresholds with calibration scale, regression check vs main, S1 and S2 gates on (with the discipline mix); spark how-to in `docs/tooling.md` | 1 | P1, P6, P8, P11a, P11b | workflow (Opus 1) |
| P13 | core | `core.api.RoadRoutes` holder | 0.5 | - | core |
| P14 | world | `RoadRoutes` backend: road graph, routes, centreline samples (Opus 2 sizes) | 1 | P13 | world |
| P15 | npc | Road routes for leaders and the segment cache | 1.5 | P10, P13 | - |
| P16 | npc | Off-screen squads: packing, record movement on `OffThread`, unpacking, crash recovery; off-screen battle resolution shared with PR #182's power roll | 2 | P3, P11 | - |
| P17 | npc | Network trims: tracking ranges per class, payload audit; allocation cleanup (H9) | 1 | P1 | `NpcEntityTypes` |
| P18 | npc (conditional) | Distance-scaled tracker send interval (mixin) only if P12 shows tracking > 10% of tick time | 1 | P12 | needs Opus 1 approval |

Cross-spec follow-ups (no new tickets): PR #182 ticket 13 (governor) is built on `ServerLoad` (P2) instead of its own average; PR #182 event and invasion spawns use squads (P10/P11) and the caps (P9); PR #184's index exposes a read-only view if world moves queries off-thread.

## Open questions for the maintainer

1. **CI runner.** You said 2 CPU; GitHub's docs give 4 vCPU for public-repo Linux runners. Is the perf job meant to run somewhere else (self-hosted)? The test scales either way.
2. **Radii.** FULL 48 and REDUCED 128 as you proposed. Do you want REDUCED tied to the server's view distance instead (for example, view distance x 16 minus 32)?
3. **Unwatched battles.** Cap 160 fighters promoted with no player near, rest frozen until a player arrives or the event resolves them off-screen. OK, or resolve all unwatched loaded fights off-screen at once?
4. **Packing travelling squads** removes their entities when their chunks unload and recreates them later from saved data. OK for patrols, caravans and warbands? (Static NPCs are never packed.)
5. **Mixin** for distance-scaled tracking updates (P18): allowed if the numbers justify it?

## Sources

- Spigot/Paper entity activation range (tick less often outside a per-category range; periodic wake-ups): https://docs.papermc.io/paper/reference/spigot-configuration/
- Pufferfish "Dynamic Activation of Brain" (tick frequency grows with squared distance beyond a start distance, with a maximum interval): https://docs.pufferfish.host/setup/pufferfish-fork-configuration/ and the Paper discussion https://github.com/PaperMC/Paper/pull/9712 ; the known drowning bug when distant mobs skip ticks in water, which our "settled" rule avoids: https://github.com/pufferfish-gg/Pufferfish/issues/58
- Async pathfinding in Paper forks (Petal, then Kaiiju and Leaf): a fixed pool sized from the core count, a bounded queue with a reject policy, callers wait for the result instead of blocking the server thread: https://github.com/Bloom-host/Petal , https://docs.leafmc.one/reference/config/leaf-global/ , https://www.leafmc.one/en/docs/how-to/optimize-leaf-server , https://deepwiki.com/Winds-Studio/Leaf/5.2-async-configuration-options
- MineColonies: its own A* path jobs run on a background executor over a chunk-cache snapshot of the world, results returned to the main thread, with a block-lookup cache and node limits: https://deepwiki.com/ldtteam/minecolonies/2.4-pathfinding-system , https://github.com/ldtteam/minecolonies/pull/9837
- spark profiler commands and the lag-spike guide: https://spark.lucko.me/docs/Command-Usage , https://spark.lucko.me/docs/guides/Finding-lag-spikes , https://docs.papermc.io/paper/profiling/
- GitHub-hosted runner sizes (4 vCPU for public repos): https://docs.github.com/en/actions/reference/runners/github-hosted-runners , https://github.blog/news-insights/product-news/github-hosted-runners-double-the-power-for-open-source/

All of these are read for ideas and numbers only; no code is copied (several are GPL-3.0).
