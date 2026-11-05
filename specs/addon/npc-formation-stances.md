# NPC formation stances

- **Pod:** npc (add-on)
- **Lead:** Opus
- **Status:** built (the maintainer left the choices to the lead, 2026-11-05; see "Choices the lead made")
- **Depends on:** `specs/addon/hiring-reputation.md` (formations and their stance clips), `specs/addon/npc-animation-pulselib.md`
  (rig, sources, build), `specs/addon/npc-attack-tempo.md` (tests and ratchets)

## Summary

Units in formation hold a stance while still and march while moving, so a squad looks drilled rather than idle. Today the four
stance slots use the first AnimationsPlus clip that roughly fit. This spec picks each stance by measurement from every licensed
source the repo has. That includes the mocap and hand-animated libraries (UAL, KayKit, CMU), which the build could not read
until now. It also gives spear and bow holders their own guard and march, so a spearman doesn't stand like a swordsman.

## Player-facing behavior

| Formation (hiring spec) | Still | Moving |
| --- | --- | --- |
| Line, Wedge | guard | march |
| Shield wall | shield_brace | march |
| Spear line | spear_brace | march |
| Column | idle | march |
| Loose | idle | walk |

- **Guard:** ready stance with knees bent, weapon in front, and a little breathing. A spear or glaive is held across the body
  with the tip up and forward. A bow is held low in front, with an arrow hand near the string.
- **Shield brace:** the shield-wall crouch: low, lead foot forward, shield facing front over the chest with the face clear. The
  weapon arm waits behind the shield's edge. A spear is levelled over the top of the shield, phalanx style.
- **Spear brace:** a low lunge with the back foot braced and the spear levelled forward and slightly up, both hands on the shaft.
- **March:** upright, even cadence, arms swinging less than a walk, weapon carried steady. A spear rides upright at the side
  with its butt low. A bow is carried in the hand at the side.

## Rules and numbers

### Slots

| Slot | Who plays it | Shield version |
| --- | --- | --- |
| `guard` | blades, axes, daggers, unarmed | `guard_shield` |
| `guard_spear` | spear, glaive (class spear) | `guard_spear_shield` |
| `guard_bow` | bow, longbow, crossbow | none (bows are two-handed) |
| `march` | everyone else | `march_shield` |
| `march_spear` | class spear | `march_spear_shield` |
| `march_bow` | bow classes | none |
| `shield_brace` | shield in the off hand, any weapon but spear | itself |
| `shield_brace_spear` | shield plus spear | itself |
| `spear_brace` | class spear without a shield | none |

- **Variant lookup in game:** `stance` → `stance_<group>` if the race file has it, where the group is `spear` for class spear
  and `bow` for the bow classes. Then add `_shield` if the unit holds a shield and that version exists. Otherwise fall back to
  the plain stance. This extends `NpcAnimatable`'s existing version operator; no new graph states. Shield wall with a spear
  plays `shield_brace_spear`. Spear line with a shield plays `shield_brace_spear` too, since a spear can't be braced two-handed
  with a shield on the arm.

### Sources and choice

- **New importer:** `tools/anim-viewer/import/mocap.js`, over the existing `retarget.js`, reads BVH from `library/ual` (UAL 1
  and 2, CC0), `library/kaykit` (CC0) and `library/cmu` (CMU terms). This is what the pulselib spec already planned.
  `CREDITS.md` gains each clip used.
- **Candidates:** every clip in every source that a stance could come from, listed in `npc_slots.js` (`STANCE_POOL`) with a
  one-line note each. The minimum set:
  - guard: AP `combatReady`, AP `blockR`, UAL `Sword_Idle`, CMU `76_04`, Mo' Bends `stance_*`
  - guard_spear: KayKit `2H_Melee_Idle`, CMU `124_08`, AP `Spear_Swing_2R` held at a frame
  - guard_bow: KayKit `Ranged_Bow_Idle`, `Ranged_Bow_Aiming_Idle`, Serious Player `bow_idle`
  - shield_brace and shield_brace_spear: AP `blockL`, Serious Player `shield` and `shield_sneak`, UAL `Idle_Shield_Loop`
  - spear_brace: AP `Spear_Swing_1R/2R/3R` held at their strike, KayKit `2H_Melee_Idle`, CMU `124_08`
  - march and its variants: AP `walk`, Serious Player `walking`, UAL `Walk_Formal_Loop`, CMU `142_11`, `138_01`, `21_06`
- **Combining:** a candidate may lend its upper body and another its legs, splitting at `upper`/`lower` as the build already
  does for posture. A held frame may be given life by the breathing of a source idle (UAL `Idle_Loop`), at most 0.5 px of
  sway.
- **Pick:** the build scores every candidate, or pair of candidates, that passes all the hard rules below, and ships the best
  per slot and race. Ties go to the earlier pool entry. The pick, its score and the runner-up are printed by the build and
  recorded in a comment in `npc_slots.js`. Race posture applies on top as for every clip. Dwarves and hobbits march with their
  shorter stride.

### Hard rules (tests, every race, every stance clip and its shield version)

| Rule | Value |
| --- | --- |
| Loops without a pop | every bone within 0.5 px end to start (existing loop test) |
| Feet planted (still stances) | each foot moves ≤ 0.5 px over the loop; lower foot −1.5..3.5 px of the floor |
| Steady but alive (still stances) | weapon tip sways 0.2–2 px over the loop |
| Anatomy | shoulder range, arms out of the body, twist and rate limits (existing tests) |
| Blade out of the body and head | existing calm-clip test |
| Head level | within 30° of the horizon (existing) |
| guard | weapon tip in front of the chest (z ≤ −4 px), above the hips, within 45° of straight ahead; knees bent 10–40° |
| guard_spear | shaft 20–60° above level, tip forward; both hands within 2.5 px of the shaft |
| guard_bow | bow upright within 30° (bow test geometry), held below the chin; drawing hand within 6 px of the string |
| shield_brace | shield tests (faces front within 35°, covers the chest, face clear, other arm outside the plate); hips ≥ 1.5 px lower than idle; lead foot ≥ 3 px ahead of the other |
| shield_brace_spear | as shield_brace; spear shaft within 15° of level, tip ≥ 20 px in front of the shield's face, shaft passing over the shield's top edge |
| spear_brace | shaft 5–25° above level, tip ≥ 28 px in front; both hands on the shaft (2.5 px); hips ≥ 2 px lower than idle; feet ≥ 4 px apart front to back |
| march | in phase with walk (same first foot, same stride count per loop); torso within 8° of upright; arm swing ≤ 60% of walk's; weapon tip moves ≤ 4 px per stride relative to the chest; no foot skate beyond walk's |
| march_spear | as march; shaft within 20° of vertical, tip up, butt below the hip |
| march_bow | as march; bow in the hand at the side, limb axis within 30° of vertical |

### Score (among clips that pass)

- **For still stances:** closeness to the stance's key pose, as the sum of the measures' distances from the middle of their
  ranges in the table, normalised. Less weapon jitter is better, but some breathing is required.
- **For march:** the lowest weapon movement, then the most upright torso, then stride regularity: each step's length and time
  within 10% of the mean.

### Choices the lead made (2026-11-05, while building it)

Where the rules above leave a choice, this is how it was made and why. The measures are in
`tools/anim-viewer/test/lib/stance.js`; the build's steps in `build_npc.js` (the formation stances section).

**Importer.** `import/mocap.js` retargets with `retarget.js` and the browser viewer's settings as the page opens, with the
clip library's (in place, no weapon IK), on `rigs.js` spec-biped renamed onto the NPC rig's bones (its pelvis is `lower`, its
torso `upper`). No hand or weapon bone is mapped: the weapon bones hold the item where Minecraft seats it in the fist.
Takes that travel (CMU walks) keep only their hips' height and drop the foot lock, which would hold a planted foot under hips
that no longer travel (the planted foot skated 16.8 px/s on CMU 21_06 with it, 3.4 without). Walks are cut to whole gait
cycles (the lag at which the thighs' swing repeats best), starting with the right thigh furthest forward as `walk` does, the
seam spread over the cycle. UAL's head-top joint sits ahead of the head, so its neutral idle looks 15° down: every UAL head is
lifted by the mean pitch of UAL `Idle_Loop`. KayKit idles read within 4° of level; CMU actors keep their own heads.

**Grip.** Minecraft seats every held item 45° forward and up from the fist, so a library pose rarely points a spear or bow
the way its stance needs. A pool entry may say how the hand turns what it holds (`grip`), solved at each key within a wrist's
range (the item turned at most 80° from its seat): `shaft` (the spear runs from the right fist through the left), `up`
(carried upright), `level` (levelled forward), `bow` (the bow's limbs upright). It is the viewer's weapon-angle setting,
aimed at a direction instead of set by hand.

**Bow.** The stance checks hold the bow as the game does (`gecko/bow.js`, the same as `client.render.WeaponGrip.bow`): a
generated bow seated with its grip on the forearm's inner side just above the fist, its limbs along the forearm's front,
the string toward the archer, its class length. The guard's drawing hand is judged against that string, and the march's
"bow hand at the side" by the fist (the grip sits 2.25 px in from it).

**Arms set for a stance** (where no library pose holds the weapon facing the front). A held upper body may have its arms
set by the bow clips' own chains (`bow_arms.js`): `bowArms` puts the bow's grip at a place in front of the body, upright,
with an arrow nocked and the drawing hand on the string; `onShaft` puts the right fist at the hip and the left one along a
shaft line (its rise, length and turn across the body given), the grip `shaft` then running the spear through both fists.
The libraries' two-handed spear holds and bow stances all stand side-on (chests 26-68 degrees off the facing).

**Combining and breathing.** The upper body is every bone but `root`, `lower` and the legs (posture's split). A pair runs as
long as the part that moves; the other part's loop is fitted to it a whole number of times. A held upper body breathes with
UAL `Idle_Loop`'s torso and head turns about their mean, scaled so the neck sways at most 0.5 px and the weapon tip at most
1.1 px (the middle of 0.2-2: a spear's tip, 45 px out, would otherwise sway four times the neck). The shield arm is put on
before the breathing, so a held pose keeps one shield arm that breathes with the body.

**Finishing.** As every clip: race posture (march slots with the race's march stride), the hips following the torso, true
hinges; then the arms swivelled within the shoulder's range and out of the body as far as a swivel brings them (as every
attack ships); still stances stand on the floor by the sole's middle (the retarget plants the boot's lowest corner, which on
a deep crouch leaves the sole's middle 3 px up). **Dwarves** have no stride of their own in `STYLE`, so their march stride is
0.8 (`MARCH_STRIDE`); hobbits keep their 0.65.

**Anatomy** in the stance checks is test/lib/anatomy.js's shoulder range and body clearance (which the existing tests apply
to attacks only) and motion.test.js's spine range for the torso over the hips: a pairing can break the last (an upright
chest over the UAL crouch's forward-tipped hips bends 34° back), so a pairing is judged on it once built in full.

**Rules added after a render review (2026-11-05).** Rendered at t = 0.3 s, man's bow guard stood with its chest 46 degrees
off the facing and both arms bunched at the chest, and his spear guard's shaft rose from the crotch out to his left, the
right hand at 1.6 px over the hip joint. Three rules catch these (test/lib/stance.js):
- *Facing* (still stances): the chest within 15 degrees of the NPC's facing. On the files as merged it failed 10 of 40 still
  clips (every bow guard, 38-49 degrees; every spear brace, 26-68 degrees).
- *guard_spear*: the lower hand at the waist or above, the waist 3 px over the hip joint (the belt line, a quarter up the
  torso: the bottom 3 px of a Minecraft skin's torso are belt and trousers). It failed 10 of 10 guard_spear clips.
- *guard_spear*: the spear's tip within 45 degrees of straight ahead, as the guard's blade (the merged shaft pointed 46-52
  degrees across the body for man, elf and hobbit: 6 of 10 clips). It also joins the guard_spear score (range -45..45).

**Measures.** "Guard" tip rules hold for every class that plays it (dagger, sword, axe, longsword). "Within N°" is the signed
range −N..N, so its middle is its aim. Knee bend is each knee's hinge. "In front" is the world's −z. March: "first foot" is the
foot ahead at the loop's start; strides per loop count the feet's order flipping; the torso's lean is from the race's own
upright (its posture's `hunch`), since race posture applies on top; arm swing is the upper arm's front-back range in the
chest's frame (the shield arm left out in shield versions); the weapon's movement is its tip's path, in the chest's frame,
over each gait cycle; skate is how far a planted foot's ground velocity strays from the gait's steady slide. "At the side"
for a carried bow is the bow hand at least 4 px out from the torso's middle and below the armpit (2 px under the shoulder
pivot): the hand at the chest's middle, the first reading, failed every orc whose posture lifts the arms (0.1-0.6 px).

**Score ranges** (where the table gives one end): tip z −20..−4 px; tip 0..12 px over the hips; shield brace hips 1.5..4.5 px
under idle, lead foot 3..9 px; spear tip over the shield 20..40 px; spear brace reach 28..56 px (the spear's length ahead of
the fist), hips 2..6 px down, feet 4..10 px apart. Sway counts as one more measure, (sway − 0.2) / 1.8. March: weapon movement
over 4 px, plus half the lean over 8°, plus a quarter of the irregularity over 10% (a strict order would let a hundredth of a
px of movement outweigh any lean).

**Pick.** Every pool entry is built and judged whole; every pairing is estimated from its parts' results (the upper body's
rules from one, the legs' from the other) and the best few that pass in estimate are built in full and judged, with their
shield versions, until two pass; the better is shipped, the other is the runner-up. Ties go to the earlier pool entry. The
picks, their scores and runners-up are in `npc_slots.js` `STANCE_PICKS` (a test checks them against the build). The whole
build took 249 s before the stances and 221-281 s with them (the same machine; its load varied).

**Rules measured against the shield code's placement** (the shield arm is the shield code's: `build_npc.js` `withShield`,
`gecko/shield.js`):
- *Face clear* is judged on the element shields (103 of Middle-earth's 106): the three entity-model shields (kite, heater,
  round) are 22 px tall, and strapped over the chest their top stands 3.5 px above the eyes in every shield clip. It reads "at
  least 2/5 of the face (10 of 25 points over the head's front) seen from straight ahead": not half, because the strap puts
  an elf's plate top at its eyes (the elf posture leans the chest back); the elves' own `walk_shield` shows 12 of 25 points
  and every elf guard candidate 10-12.
- *Over the shield's top edge* (and the tip's 20 px ahead of it) is judged on the element shields for the same reason: a
  level spear clears the entity shields' top only with the fist over the head, which one pose in the libraries holds (SP
  `swimming`), and it fails elves.

No number in the hard-rule table was changed.

## Edge cases

- **Mixed squads** (hiring spec): each unit plays its own variant, so a mixed line shows spearmen and swordsmen correctly.
- **No variant:** a race file without a variant (an older build) falls back to the plain stance.
- **Off-hand weapons:** two daggers or a dagger and a sword play plain `guard`, the off hand held low (existing free-arm rules).
- **Transitions:** blending in and out of a stance uses the graph's existing 3-tick blend. No new transitions.
- **`/lotrrender stance`:** this debug command keeps working and accepts the four stance names. Variants are picked
  automatically.

## Data and APIs

- **Race files:** `npc_biped_<race>.animation.json` gains `guard_spear`, `guard_bow`, `march_spear`, `march_bow`,
  `shield_brace_spear` and their shield versions as listed above. `hits.json` is unchanged.
- **`npc_slots.js`:** a `STANCE_POOL` object (slot → candidate list); stance slots point at the build's pick, recorded in
  `STANCE_PICKS` with its score and runner-up.
- **Build:** `build_npc.js` evaluates the pool, reusing the test measures in `test/lib/` and `gecko/`.
- **Game:** `NpcAnimatable` version operator: variant by weapon group, then shield. `NpcGraphs.STATES` is unchanged; the two
  brace states now play through the version operator too (they played the plain clip before).
- **Tests:** `tools/anim-viewer/test/stances.test.js` checks the hard rules above. A GameTest checks the variant lookup:
  spear and shield in a shield wall plays `shield_brace_spear`; a bow in a line plays `guard_bow`; a race file without
  `guard_bow` falls back to `guard`.

## Tests

- [x] `stances.test.js`: every hard rule, every race, every stance slot and shield version.
- [x] Build prints each pick with its score and runner-up; the shipped files match the build (existing).
- [x] GameTest (`client.anim.test.StanceVariantTests`; run outside the game against the compiled classes): variant lookup and fallback.
- [ ] Playtest (maintainer): `/lotrrender stance guard|shield_brace|spear_brace|march` on a mixed group (sword, spear, bow,
      with and without shields) of each race, still and walking.
