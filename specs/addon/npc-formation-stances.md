# NPC formation stances

- **Pod:** npc (add-on)
- **Lead:** Opus
- **Status:** approved (the maintainer left the choices to the lead, 2026-11-05)
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
- **`npc_slots.js`:** a `STANCE_POOL` object (slot → candidate list); stance slots point at the build's pick.
- **Build:** `build_npc.js` evaluates the pool, reusing the test measures in `test/lib/` and `gecko/`.
- **Game:** `NpcAnimatable` version operator: variant by weapon group, then shield. `NpcGraphs.STATES` is unchanged.
- **Tests:** `tools/anim-viewer/test/stances.test.js` checks the hard rules above. A GameTest checks the variant lookup:
  spear and shield in a shield wall plays `shield_brace_spear`; a bow in a line plays `guard_bow`; a race file without
  `guard_bow` falls back to `guard`.

## Tests

- [ ] `stances.test.js`: every hard rule, every race, every stance slot and shield version.
- [ ] Build prints each pick with its score and runner-up; the shipped files match the build (existing).
- [ ] GameTest: variant lookup and fallback.
- [ ] Playtest (maintainer): `/lotrrender stance guard|shield_brace|spear_brace|march` on a mixed group (sword, spear, bow,
      with and without shields) of each race, still and walking.
