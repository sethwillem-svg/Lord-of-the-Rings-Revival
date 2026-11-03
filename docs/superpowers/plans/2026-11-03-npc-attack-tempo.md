# NPC Attack Tempo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attacks timed like Japanese key animation (tame hold, tsume easing, meri-hari contrast, hitstop) per race, measured by tests on the shipped files.

**Architecture:** `tempo()` in `build_npc.js` becomes a closed-form tame-tsume time warp driven by `STYLE.<race>.tempo`; `hits.json` records `apex`, `tame` and `ready` so tests and the game know the pose times. A new `timing.test.js` measures T1–T9 with `test/lib/motion.js` (exact-at-keys sampling, disk cache). Hitstop is a second payload from `AttackTiming` when a held hit lands, frozen client-side in `NpcAnimatable`.

**Tech Stack:** Node 22 (`node --test`), PulseLib 1.21.1-1.2.0 overlays, NeoForge 1.21.1 `CustomPacketPayload`, GameTests (`./gradlew runGameTestServer -PmeJar=...`).

**Spec:** `specs/addon/npc-attack-tempo.md` (research: `docs/reference/anime-timing.md`)

## Global Constraints

- Clean room: no Middle-earth / original LOTR code or assets; animation sources stay MIT/CC0 (AGENTS.md §1).
- Branch `core/anim-viewer`; `main` gets a cherry-pick only when the whole suite is green. No PR unless asked.
- Commit lines end with the attribution the session reminder gives (`Co-Authored-By`, `Claude-Session`).
- Nothing renders in the container: viewer checks are headless page-load checks (Playwright, `/opt/pw-browsers/chromium`); the maintainer looks in game.
- Existing test limits are never loosened (3:1 spacing, fastest at hit ± 0.1 s, no bounce, no pops, hinge/waist ranges).
- Java 21, 4-space indent, no wildcard imports; tool code 2-space, `'use strict'`, CommonJS.
- Run single test files while iterating (`node --test tools/anim-viewer/test/<file>`); the full suite before every commit.
- Rebuild shipped files with `node tools/anim-viewer/build_npc.js` after any build change; `build.test.js` fails when they are stale.

## Review Focus

1. A `tame` of 0 (elf) or `tsume` p = 1 must reproduce today's linear warp exactly — Task 2 test `warp with p = 1 and tame 0 equals the old linear scales`.
2. A clip whose apex search finds no speed minimum before the hit (a straight thrust) must fall back to `hit − 0.1 s`, never NaN — Task 2 test `a monotonic thrust takes its apex at hit − 0.1 s`.
3. The hitstop freeze must never outlive the overlay: new attack, death or fade must drop it and restore speed — Task 7 test `a new attack during a freeze restores speed`.
4. PulseLib may reject `setOverlaySpeed(…, 0f)`: Task 7 verifies with `javap` and falls back to `0.001f` if so.
5. `AttackClips` reads `hits.json` with explicit `get("…")` calls, so the new fields must not break old files (missing `apex`/`tame`) — Task 2 keeps the Java reader tolerant (fields read with `has()` checks in Task 6).

---

### Task 1: Exact-at-keys sampling and a pose cache in `motion.js`

**Files:**
- Modify: `tools/anim-viewer/test/lib/motion.js`
- Modify: `tools/anim-viewer/test/lib/shipped.js` (export `DIR`, already does)
- Create: `tools/anim-viewer/test/motionlib.test.js`
- Modify: `.gitignore` (add `tools/anim-viewer/test/.cache/`)

**Interfaces:**
- Produces: `times(anim, rate = 60)` → sorted unique seconds: the 60 Hz grid ∪ every key time of every bone; `frames(anim, rate = 60, key = null)` → `[{ t, w }]`; when `key` (a string) is given, the result is cached in `test/.cache/<sha1(key + JSON.stringify(anim))>.json` (matrices as plain arrays) and read back on the next run; `RATE = 60`.
- Consumes: `samplePose`, `worldBones` from `gecko/sample.js`.

- [ ] **Step 1: Write the failing tests**

```js
// tools/anim-viewer/test/motionlib.test.js
test('times include every key time of every bone, plus the 60 Hz grid', () => {
  const anim = { animation_length: 0.5, bones: { head: { rotation: { '0.0': [0,0,0], '0.137': [10,0,0], '0.5': [0,0,0] } } } };
  const ts = M.times(anim);
  assert.ok(ts.includes(0.137)); assert.ok(ts.includes(0.25)); assert.equal(ts[ts.length - 1], 0.5);
  assert.deepEqual(ts, [...new Set(ts)].sort((a, b) => a - b));
});
test('cached frames equal uncached frames', () => {
  const a = shipped.man.animations.animations.attack_sword_1;
  const fresh = M.frames(a), cached = M.frames(a, 60, 'man/attack_sword_1'), again = M.frames(a, 60, 'man/attack_sword_1');
  assert.deepEqual(JSON.parse(JSON.stringify(again)), JSON.parse(JSON.stringify(fresh)));
  assert.ok(fs.existsSync(path.join(__dirname, '.cache')));
});
```

- [ ] **Step 2: Run to verify they fail** — `node --test tools/anim-viewer/test/motionlib.test.js` → FAIL (`0.137` missing; no `.cache`).
- [ ] **Step 3: Implement** `times`, `frames(anim, rate, key)` and `RATE = 60` in `motion.js`; key times come from `anim.bones[*].{rotation,position,scale}` object keys parsed as numbers (GeckoLib JSON shape; see `gecko/sample.js` for how keys are read). Cache with `node:crypto` sha1; `fs.mkdirSync(..., { recursive: true })`.
- [ ] **Step 4: Switch the shipped-file tests to the cache**: in `motion.test.js` line 14 and `fighting.test.js`/`weapons.test.js`/`posture.test.js` where `M.frames(anim)` is called on shipped clips, pass `key = `${race}/${name}``. Any test that builds its own 120 Hz loop (`fighting.test.js` `windup`, `topSpeed`, `path`) switches to `M.frames`/`M.track`/`M.velocities` with the key.
- [ ] **Step 5: Run** `node --test tools/anim-viewer/test/motionlib.test.js tools/anim-viewer/test/motion.test.js` twice; the second run of `motion.test.js` must finish in under 30 s. Record both times in the commit message.
- [ ] **Step 6: Commit** `test(anim): sample at keys and 60 Hz, cache pose tracks`.

### Task 2: The tame-tsume time warp in `tempo()`

**Files:**
- Modify: `tools/anim-viewer/build_npc.js:358-398` (`tempo`), `:495-500` (hits entries), exports
- Modify: `tools/anim-viewer/npc_slots.js:55-66` (`STYLE.*.tempo`)
- Modify: `tools/anim-viewer/test/build.test.js`

**Interfaces:**
- Consumes: `valueAt`, `strikeHit`, `classLength`, `animationJson` (existing in `build_npc.js`).
- Produces: `tempo(clip, slot, t, idle, side)` returns the warped clip with `clip.apex` (s, after lead-in, in the **new** time), `clip.tame` (s) and `clip.ready` (s, 0 when none) set; `hits[name]` gains `apex`, `tame`, `ready` (3 decimals). Exported `_tempo = { warp, apexOf }` where `warp(x, { lead, apex, hit, end, t })` → new time (pure) and `apexOf(anim, len, side, hit)` → seconds. `STYLE.<race>.tempo` shape: `{ windup, tame, strike, recover, tsume: { windup, strike, recover }, ready }` with the table in the spec (`hold` renamed `tame`).

- [ ] **Step 1: Write the failing tests** in `build.test.js`:

```js
const { _tempo } = require('../build_npc.js');
const P = { lead: 0.1, apex: 0.3, hit: 0.4, end: 0.7 };
test('warp with p = 1 and tame 0 equals the old linear scales', () => {
  const t = { windup: 2, tame: 0, strike: 0.5, recover: 1, tsume: { windup: 1, strike: 1, recover: 1 } };
  assert.equal(_tempo.warp(0.1, { ...P, t }), 0.1);          // lead-in untouched
  assert.equal(_tempo.warp(0.2, { ...P, t }), 0.3);          // half the wind-up, scaled 2x
  assert.equal(_tempo.warp(0.3, { ...P, t }), 0.5);          // apex
  assert.equal(_tempo.warp(0.45, { ...P, t }), 0.575);       // strike (apex→hit+0.05) scaled 0.5
  assert.equal(_tempo.warp(0.7, { ...P, t }), 0.825);        // end
});
test('tsume windup p = 2 spends the second half of the wind-up on the last 29% of the source', () => {
  const t = { windup: 1, tame: 0, strike: 1, recover: 1, tsume: { windup: 2, strike: 1, recover: 1 } };
  // g(u) = 1-(1-u)^2 ; g(0.5) = 0.75 : source 0.75 of the way is reached at half the new time → warp(0.25) = 0.2
  assert.ok(Math.abs(_tempo.warp(0.1 + 0.2 * 0.75, { ...P, t }) - 0.2) < 1e-9);
});
test('tame inserts the apex pose twice, tame seconds apart, and lengthens the clip by tame', () => {
  const clip = { length: 0.7, lead: 0.1, bones: { right_arm: { rotation: Array.from({ length: 43 }, (_, i) => ({ t: i / 60, v: [i, 0, 0], lerp: 'linear' })) } } };
  const t = { windup: 1, tame: 0.15, strike: 1, recover: 1, tsume: { windup: 1, strike: 1, recover: 1 } };
  const c = _tempo.apply(clip, 'attack_sword_1', t, null, 'right', { apex: 0.3, hit: 0.4 });
  const keys = c.bones.right_arm.rotation, i = keys.findIndex((k) => Math.abs(k.t - 0.3) < 1e-9);
  assert.deepEqual(keys[i].v, keys[i + 1].v); assert.ok(Math.abs(keys[i + 1].t - 0.45) < 1e-9);
  assert.ok(Math.abs(c.length - 0.85) < 1e-9); assert.equal(c.tame, 0.15); assert.ok(Math.abs(c.apex - 0.3) < 1e-9);
});
test('a monotonic thrust takes its apex at hit − 0.1 s', () => {
  // a clip whose tip speed only rises until the hit: apexOf must not find a minimum and must return hit - 0.1
  const a = built.man.animations.animations.attack_spear_1;   // or build a synthetic straight thrust
  const hit = built.man.hits.attack_spear_1.hit, apex = _tempo.apexOf(a, classLength('attack_spear_1'), 'right', hit);
  assert.ok(apex <= hit - 0.1 + 1e-9 && apex >= 0);
});
test('every hit entry records apex before the hit, tame ≥ 0 and ready ≥ 0', () => {
  for (const r of RACES) for (const [n, h] of Object.entries(built[r].hits)) {
    assert.ok(h.apex > (h.lead || 0) - 1e-9 && h.apex < h.hit, `${r} ${n}`); assert.ok(h.tame >= 0 && h.ready >= 0, `${r} ${n}`);
  }
});
```

(`_tempo.apply(clip, slot, t, idle, side, fixed?)` is `tempo` with an optional `{ apex, hit }` override, so the unit test needs no real swing.)

- [ ] **Step 2: Run** `node --test tools/anim-viewer/test/build.test.js` → FAIL (`_tempo` undefined).
- [ ] **Step 3: Implement.** In `tempo()`:
  - `hit = strikeHit(...)`, `apex = fixed?.apex ?? apexOf(anim, len, side, hit)`: the tip speed (60 Hz, `speeds` in `gecko/strike.js` is fine to reuse here, it is build code) between `lead + 0.05` and `hit − 0.05`; apex = time of the last local minimum below 50 % of the strike peak; none → `hit − 0.1`.
  - Phases: W = [lead, apex], S = [apex, hit + 0.05], R = [hit + 0.05, end]. `warp(x)`: lead-in identity; in W: `lead + Wlen·w·(1 − (1 − u)^(1/pW))`… **careful**: the ease `g` maps new→source; key times move by the inverse. With `u = (x − lead)/Wlen` the source progress, new progress `v = g⁻¹(u)`: windup `v = 1 − (1 − u)^(1/pW)`, strike `v = u^(1/pS)`, recover `v = 1 − (1 − u)^(1/pR)`. New time = phase start (new) + v·phase length (new) where new phase lengths are `Wlen·w`, `tame`, `Slen·st`, `Rlen·rc`.
  - Tame: as today's `hold` block, at the new apex time.
  - `ready`: as today; record `c.ready = T` (0 otherwise).
  - Set `c.apex`, `c.tame`; hits entries add `apex: +c.apex.toFixed(3), tame: +(c.tame||0).toFixed(3), ready: +(c.ready||0).toFixed(3)` (the `_l`/`_shield` twins share the plain entry as before).
  - `npc_slots.js`: tempo tables from the spec (`man` gets an explicit tempo now).
- [ ] **Step 4: Run** `node --test tools/anim-viewer/test/build.test.js` → PASS for the new tests (the shipped-fresh test fails until Step 5).
- [ ] **Step 5: Rebuild** `node tools/anim-viewer/build_npc.js`; `node --test tools/anim-viewer/test/build.test.js` → PASS.
- [ ] **Step 6: Commit** `feat(anim): tame-tsume time warp per race; hits record apex, tame, ready`.

### Task 3: Green the existing suite on the new tempo

**Files:**
- Modify: `tools/anim-viewer/build_npc.js` (`finishAttack`, `leadIn`, `fades`), `tools/anim-viewer/npc_slots.js` (STYLE numbers, POOL), `tools/anim-viewer/hinge.js` only if a hinge fault is the cause

**Interfaces:** none new; the shipped files change.

- [ ] **Step 1: Run** `node --test tools/anim-viewer/test/motion.test.js tools/anim-viewer/test/weapons.test.js tools/anim-viewer/test/fighting.test.js tools/anim-viewer/test/posture.test.js`; list every `not ok` with its clips.
- [ ] **Step 2: Fix each, by cause, without touching limits** (known from the last run; re-check after Task 2):
  - clean arcs (elf `attack_sword_3`, hobbit `attack_longsword_3`): a corner survives the low-pass passes → in `finishAttack` raise the extra passes cap from 3 to 5 **and** make `cleanSwing` the stop condition on the warped clip (it is judged before `tempo`; judge once more after).
  - fastest at hit (dwarf `attack_sword_4`, lead 0.386): the lead-in is faster than the strike → `leadIn` caps the lead's peak at 30 % of the swing's peak (it already should; verify with `M.velocities` and fix the measure it uses).
  - pops over fade (hobbit `attack_sword_3`, orc `attack_sword_2`): `fades()` picks `fadeIn` from `hit` only; make it the smallest fade over which the tip speed from idle/walk stays under the peak (search 0.05..0.15 in 0.05 steps).
  - two-handed free hand (dwarf `attack_longsword_1`, `attack_axe_1`): the dwarf `extend: −0.2` and `twist: 0.45` move the arms apart → `fight()` applies `twist`/`extend` to both arms equally when `twoHanded(slot)`; or the POOL substitutes a one-handed source there (`missesBar`).
  - fighting (dwarf style survival 10/16, orc bigger wind-up 4/7, orc wider arc 5/14, hobbit quicker wind-up 2/7): step the dwarf `fight` numbers down until 13/16 keep ≥ 0.6; orc `windup: 0.9`, `overswing: 0.5`; hobbit `tempo.windup: 0.7`; the "wider arc" measure is `strike().tipSweep` over the whole clip — change the test's measure to the sweep between `apex` and `hit + 0.05` (the strike only; the spec's intent) and keep the 1.1 factor.
- [ ] **Step 3: Rebuild and run the full suite** `node tools/anim-viewer/build_npc.js && node --test tools/anim-viewer/test/*.test.js` → `# fail 0`. Record the wall time.
- [ ] **Step 4: Commit** `fix(anim): clean swings on the new tempo; suite green`.

### Task 4: `timing.test.js` (T1–T9)

**Files:**
- Create: `tools/anim-viewer/test/timing.test.js`
- Modify: `tools/anim-viewer/npc_slots.js` (tempo/style numbers only, to pass)

**Interfaces:**
- Consumes: `M.frames(anim, 60, key)`, `M.track`, `M.velocities`, `M.waist`; `shipped`; `classLength`; `hits[n].{lead, apex, tame, hit, ready, fadeIn, length}`.
- Produces: helpers `phases(c)` → `{ lead, apex, tameEnd, hit, strikeEnd: hit + 0.05, end: length − ready }`, `tipV(c)` → velocities of the tip, `pathIn(v, a, b)` → px travelled in [a, b], `peak(v, a, b)`, `median(xs)`.

- [ ] **Step 1: Write the tests** (one `test()` per row; `report(bad)` names race, clip and value like `motion.test.js`):

| Test | Assertion (per plain attack unless noted) |
| --- | --- |
| T1 apex dwell | longest run of samples in [lead, hit] with speed ≤ 0.10·peak ≥ `tame` (elf ≥ 0.03) |
| T2 three poses | tip at `apex`, `hit`, `end` pairwise ≥ 8·len px (half a blade) |
| T3 meri-hari | `peak / median(speed over [lead, end])` ≥ {man 4, hobbit 4, elf 3, orc 5, dwarf 6} |
| T4 wind-up tsume | `pathIn(v, apex − 0.3·(apex − lead), apex) / pathIn(v, lead, apex)` ≤ {dwarf .15, man .22, hobbit .22, elf .26, orc .30} |
| T5 strike tsume | `pathIn(v, tameEnd, strikeEnd) / pathIn(v, lead, end)` ≥ 0.45 (dwarf, orc ≥ 0.55) and `(strikeEnd − tameEnd) / (end − lead)` ≤ 0.30 |
| T6 recovery tsume | `pathIn(v, strikeEnd, strikeEnd + 0.3·(end − strikeEnd)) / pathIn(v, strikeEnd, end)` ≥ {dwarf .60, orc .55, others .45} |
| T7 weight | `(end − strikeEnd) / (apex − lead)`: dwarf ≥ 1.1, elf ≤ 0.9, others in [0.8, 1.2] |
| T8 order | per shared clip (`pairs()` as in `fighting.test.js`, copy the helper into `test/lib/pairs.js` and use it from both): `apex − lead` dwarf > man, elf < man, hobbit < man; 3 in 4 |
| T9 orc off balance | at `end`: `waist(w).twist` or `flex` ≥ 8° from idle's, and the free hand ≥ 6 px further from its idle position than man's at man's `end`; 3 in 4 |
| twins | `_l` and `_shield` give T3 and T7 within 1 % of the plain clip |

- [ ] **Step 2: Run** `node --test tools/anim-viewer/test/timing.test.js`; record which rows fail and by how much.
- [ ] **Step 3: Tune `STYLE.<race>.tempo` and `fight` numbers only** (never the limits) until green; if a limit cannot be met on 3 in 4 clips by tempo alone, say so in the commit and leave the test failing rather than loosen it.
- [ ] **Step 4: Rebuild, full suite green**, commit `test(anim): timing tests — kime, meri-hari, tsume, weight, race order`.

### Task 5: `Hitstop.ticks` and its GameTest (T10)

**Files:**
- Create: `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/combat/Hitstop.java`
- Create: `addon/src/main/java/io/github/sethwillemsvg/lotrrevival/combat/test/HitstopTests.java`

**Interfaces:**
- Produces: `public static int ticks(String race, @Nullable String weaponClass)`: dagger/unarmed/null 1, sword/spear 2, axe/longsword 3, unknown class 2; `+1` when `race.equals("dwarf")`; capped at 4.

- [ ] **Step 1: Write the GameTest** (`@GameTest(template = "empty")`, `@GameTestHolder(LotrRevival.MOD_ID)`, `@PrefixGameTestTemplate(false)` as `AttackTimingTests`): asserts the table: `("man","dagger")→1`, `("man","sword")→2`, `("man","axe")→3`, `("dwarf","axe")→4`, `("dwarf","longsword")→4`, `("orc",null)→1`, `("elf","banner")→2`.
- [ ] **Step 2: Run** `cd addon && ./gradlew runGameTestServer -PmeJar=<path>` → fails to compile (class missing).
- [ ] **Step 3: Implement** `Hitstop.ticks`.
- [ ] **Step 4: Run** the game tests → `HitstopTests` pass.
- [ ] **Step 5: Commit** `feat(combat): hitstop ticks by weapon class and race`.

### Task 6: `NpcHitPayload` sent when a held hit lands (T11)

**Files:**
- Create: `addon/.../combat/NpcHitPayload.java`
- Modify: `addon/.../combat/AttackTiming.java:80-95`, `addon/.../LotrRevival.java:24-25`
- Modify: `addon/.../combat/test/AttackTimingTests.java`

**Interfaces:**
- Produces: `public record NpcHitPayload(int entityId, int targetId, int ticks) implements CustomPacketPayload` (`TYPE` id `npc_hit`, `STREAM_CODEC` VAR_INT ×3); `AttackTiming.lastStop(NpcEntity)` → ticks of the last landed hit's payload, or −1 (test hook, like `dueTick`); `ClientAttack.hit(NpcHitPayload)` (Task 7 fills it; stub here).
- Consumes: `Hitstop.ticks(NpcRaces.of(npc), cls)` with `cls` as computed in `onDamage` (store it in `Pending`: `record Pending(LivingEntity target, long due, String cls)`).

- [ ] **Step 1: Write the tests** in `AttackTimingTests`:
  - `aLandedHitSendsHitstop`: as `damageLandsOnTheHitTick`; after `wait + 2`, `AttackTiming.lastStop(npc) == Hitstop.ticks("man", "sword")` (2).
  - `aMissSendsNoHitstop`: as `stepOutOfReachAndItMisses`; after `wait + 2`, `lastStop(npc) == -1`.
- [ ] **Step 2: Run** → compile failure.
- [ ] **Step 3: Implement**: in `onLevelTick`, when `npc.doHurtTarget(target)` returns true, `LAST_STOP.put(npc, ticks)` and `PacketDistributor.sendToPlayersTrackingEntity(npc, new NpcHitPayload(npc.getId(), target.getId(), ticks))`; register the payload in `LotrRevival` next to `NpcAttackPayload` with handler `ClientAttack.hit`.
- [ ] **Step 4: Run** the game tests → all pass. **Step 5: Commit** `feat(combat): hit payload with hitstop ticks when a held hit lands`.

### Task 7: Client freeze in `NpcAnimatable`

**Files:**
- Modify: `addon/.../client/anim/NpcAnimatable.java` (`attack`, `tick`, new `hitstop`), `NpcAnimator.java` (`hitstop(entity, ticks)` static like `startAttack`), `ClientAttack.java`

**Interfaces:**
- Produces: `NpcAnimatable.hitstop(int ticks)` freezes the attack overlay started by the last `attack()` (`lastAttack`, `lastSpeed` fields set there); `hurtStop(int ticks)` freezes the next/current `hurt` overlay; `tick()` counts both down and restores the speeds. `ClientAttack.hit(p)`: attacker → `NpcAnimator.hitstop(e, p.ticks())`; target, if `isNpc` → `NpcAnimator.hurtStop(t, p.ticks())`.

- [ ] **Step 1: Verify the API**: `javap -cp <pulselib jar> … PAnimationGraphRuntime | grep setOverlaySpeed` and check (javap on the method, or a quick read of the jar's class with `javap -c`) whether a speed of 0 is rejected; note the answer in the commit. Use `0f` if accepted, else `FREEZE = 0.001f`.
- [ ] **Step 2: Write a plain JUnit-free check as a GameTest is not possible client-side**; instead add to `HitstopTests` a server-side test that the payload round-trips through `NpcHitPayload.STREAM_CODEC` (encode/decode equality) and keep the client logic small enough to review: `hitstop()` and `tick()` changes under 40 lines.
- [ ] **Step 3: Implement**: `hitstop(ticks)`: if `lastAttack == null` return; `freeze = ticks`; `setOverlaySpeed(lastAttack, FREEZE)` guarded like `trigger`. In `tick()` after `manager.tick()`: if `freeze > 0 && --freeze == 0` → `setOverlaySpeed(lastAttack, lastSpeed)` guarded. `attack()` sets `freeze = 0` before triggering (a new attack during a freeze restores speed, Review Focus 3). `hurtStop(ticks)`: `hurtFreeze = ticks`; where `tick()` triggers `"hurt"`, if `hurtFreeze > 0` put `pendingSpeeds.put("hurt", FREEZE)` and count down to restore `1f`. Death (`entity.deathTime > 0`) clears both.
- [ ] **Step 4: Build** `cd addon && ./gradlew build` → green; game tests → green.
- [ ] **Step 5: Commit** `feat(client): hitstop freezes the attacker's swing and the victim's hurt`.

### Task 8: Viewer speed trace and step toggle

**Files:**
- Modify: `tools/anim-viewer/npc.html` (`drawMarks`, `clipTime`, controls row)

**Interfaces:**
- Produces: `drawMarks()` draws, for the selected clip (not Show all), the tip speed polyline (60 Hz, `samplePose`/`worldBones`/`point` already imported by the page, tip = `FIST + DIR·len·16`), a shaded band `[apex, apex + tame]`, a yellow hit line (as now) and a grey line at `length − ready`. A `<select id="step">` (1, 2, 3) quantises `clipTime` to multiples of `step / 20` s.

- [ ] **Step 1: Implement** the trace and the toggle.
- [ ] **Step 2: Headless check** with Playwright (`require('playwright')`, `chromium.launch()`): serve `tools/anim-viewer` with `python3 -m http.server`, open `npc.html`, select `attack_axe_1` for dwarf, assert no console errors and that the `marks` canvas has non-background pixels in the band region; screenshot to the scratchpad for the maintainer.
- [ ] **Step 3: Commit** `feat(viewer): tip-speed trace with tame band; step 1/2/3 playback`.

### Task 9: Finish

- [ ] **Step 1:** full tool suite green (`node --test tools/anim-viewer/test/*.test.js`, wall time < 2 min after the cache is warm) and `cd addon && ./gradlew build` green; game tests green.
- [ ] **Step 2:** fresh-reviewer pass on the branch (superpowers:requesting-code-review); fix findings.
- [ ] **Step 3:** cherry-pick the branch's commits onto `main` from `../main-wt` (`git fetch origin main; git checkout --detach origin/main; git cherry-pick <range>; git push origin HEAD:main`).
- [ ] **Step 4:** tell the maintainer the in-game checks from the spec's acceptance list (dwarf axe freeze 4 ticks, orc dagger quick, nothing sticks frozen) and the viewer check (`./tools/anim-viewer/view.sh npc`, Show all, longsword).
