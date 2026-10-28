# Rendering and animation for crowds of NPCs

- **Pod:** core (the shared animation framework, dependency, benchmark and client config). Renderers per race stay in `client/npc/` (NPC pod).
- **Lead:** Opus 1
- **Status:** draft, revision 2 (maintainer's decisions on PR #185, see "Maintainer decisions" at the end: AzureLib is chosen; every NPC of every faction is animated; formation clips added for the discipline levels of `specs/npc/performance.md`, PR #186; pilot #10 becomes the bench rig; bundling, shared rig, adaptive detail, bump policy and Epic Fight scope accepted as proposed)
- **Depends on:** `specs/core/core-apis.md` (registers, shared constants, payload rules), draft `specs/npc/performance.md` (PR #186: squads, formations, discipline levels RIGID / ORGANIZED / LOOSE, formation animation hooks in its section 2h), `specs/core/combat-items.md` (weapon classes, `WeaponStats`), `specs/npc/npc-base.md` (races, roles, routines, nameplate rules, `client/npc/NpcHumanoidRenderer`). Feeds the NPC model content wave, villages, patrols, caravans and battles.
- **Sources:** research on 2026-10-28, listed with links in section 13. No original LOTR Mod models, animations or code were used. The original mod rendered every NPC with a static biped model and no keyframe animation, so there is nothing of its to port here: this whole system is ours.

## Summary

Seth wants Middle-earth to feel inhabited: villages of 20 to 40 people going about their day, patrols and caravans on the roads, and battles of 100 to 200 fighters around the player, all animated and all at playable FPS on a decent PC. Today NPCs use vanilla's procedural biped (`HumanoidModel`), which is cheap but cannot sit, work, talk, emote, or swing a pike differently from a dagger.

This spec picks the animation technology, defines **animation level of detail (LOD)** so cost falls with distance, sets **render budgets**, fixes the **authoring pipeline** (Blockbench, rigs, file layout, animation names), defines how **AI state drives animation** with almost no network cost, and plans an optional **Flywheel instanced crowd path** for later. It also adds **soft Epic Fight compatibility** (data files only, no dependency).

**Decision (maintainer, PR #185):** **AzureLib**, pinned to an exact version and bundled jar-in-jar, for **every NPC of every faction and every discipline level**, wrapped in a thin layer of our own (`client/core/anim`) that owns the LOD tiers, budgets, state-to-clip mapping and fallbacks. Before any NPC model content is made, a **benchmark gate** confirms the LOD settings and catches problems by measuring AzureLib against GeckoLib, vanilla keyframe animation and today's vanilla biped at 50, 100, 200 and 400 NPCs on Seth's PC. GeckoLib or vanilla keyframes replace AzureLib only if the gate fails badly (section 2c).

## Player-facing behavior

1. NPCs move like people: they breathe while standing, walk and run at speeds that match their feet, turn their heads to look at you, sit on benches, work at anvils and fields, talk with gestures, wave, cheer, salute and taunt.
2. In combat, each weapon class has its own moves: daggers stab fast, spears thrust, pikes thrust from far, axes and hammers swing heavily, archers draw and loose, crossbowmen aim and reload, slingers whirl. Shield bearers raise shields. Hits make NPCs flinch, and the dead fall.
3. Up close everything is fully animated and blended. Further out, animation gets simpler and steps at a lower rate; far away, NPCs hold a static pose that still shows what they are doing (walking, standing guard, fighting) and which side they are on (helmet, chest armour, main-hand weapon). Beyond entity render distance they are not drawn. With default settings the player should not notice these changes in normal play.
4. A big battle stays playable: on the reference PC (section 3), 200 fighters near the player keep at least 60 FPS on average.
5. A client option **Animation quality** (Low, Medium, High, Ultra) scales the LOD distances, and an **Adaptive crowd detail** toggle lets the game lower detail during heavy scenes. The F3 screen shows one line of crowd statistics.
6. Shaders (Iris) work. With Sodium, Iris and Entity Culling installed, everything still works, and the latter two can make it faster.
7. If the player also installs **Epic Fight**, our weapons get sensible Epic Fight move sets (a spear fights like a spear) with no extra download. Without Epic Fight nothing changes.

## 1. Options researched

All facts were checked on 2026-10-28 against Modrinth, GitHub and project docs (links in section 13).

| | **AzureLib** | **GeckoLib** | **Vanilla keyframe** (`AnimationDefinition` + `AnimationState`, as the Warden and Camel use) | **Vanilla procedural** (today's `HumanoidModel`) | **Flywheel 1.x instancing** |
| --- | --- | --- | --- | --- | --- |
| Maintained? | Very active. NeoForge 1.21.1 build 3.2.2 on 2026-10-27; 78 NeoForge 1.21.1 builds in total, a few marked alpha. One main maintainer. | Active, but the 1.21.1 line only gets fixes (4.9.3 on 2026-09-16). New work is in 5.x for newer Minecraft. | Part of Minecraft | Part of Minecraft | Active. 1.0.x builds for 1.21.1 on maven.createmod.net (1.0.7, Sept 2026). |
| Licence | MIT | MIT | Mojang | Mojang | MIT (Vanillin, the reference user, is also MIT) |
| Sides | Must be on client and server (it has its own packets) | Client-only use possible since 4.8.1 | Client | Client | Client |
| Blockbench | "AzureLib Animator" 2.2.0 plugin; plain Bedrock geo and animation JSON also load. Its project files are not guaranteed to work with the GeckoLib plugin. | "GeckoLib Models & Animations" 4.2.5 plugin | Built-in Modded Entity format with Mojang-mapping animation export, plus the "Animation to Java Converter" 1.2.0 plugin. **Output is Java code**, not data. | Code only (no Blockbench animation) | No authoring tool; a renderer we write ourselves |
| Blending | Crossfades, layered controllers with weights, Molang, easing, sound and particle keyframes, sequences with timed events | Same family (AzureLib is a fork of GeckoLib 4) | No crossfade; animations snap on start and stop | Formula-based only | None by itself |
| Per-entity CPU model | Per frame: sample keyframes for animated bones, walk the bone tree, then transform and write every cube's vertices on the CPU. Recent releases removed most per-frame allocation: Molang was about 830 bytes per entity per frame before 3.1.13; bone poses no longer allocate (3.1.18, about half the render-thread allocation); bones at rest are skipped (3.1.23). | Same pipeline, without AzureLib's 2026 optimization work and without LOD | Samples keyframes into `ModelPart` rotations; vertex writing goes through Sodium's fast cuboid path if Sodium is installed | Cheapest: a few trig calls, then `ModelPart` vertices (Sodium fast path) | The CPU writes one small instance record per part per frame; the GPU transforms vertices. Vanillin: "ten thousand of something have the same frame time impact as one of something" (minecarts) |
| Built-in LOD | Yes, opt-in per renderer: bone LOD (hide bones below a tree depth past a distance) and animation LOD (update every N ticks past a distance). It only works from 3.1.19 (2026-10-09); before that it computed and then ignored the decision. | No | No | No | No (we would write it) |
| Armour and held items on bones | `AzArmorLayer` (vanilla and custom armour per bone), `AzBlockAndItemLayer` (items on bones) | Equivalent layers | We write them | Vanilla `HumanoidArmorLayer`, `ItemInHandLayer` | We write them |
| Shaders (Iris) | Works through vanilla render types. Iris glowmask fix in 3.1.21. | PBR fix in 4.6 | Works | Works | **Off with a shader pack**: Flywheel falls back to vanilla rendering. A companion mod is needed: Iris Flywheel Compat (CC0, NeoForge 1.21.1 build 2.4.0, May 2026, patches shaders, partial) or Colorwheel (needs shader packs to support it). Iris's planned 2.0 shader standard ("Aperture") is expected to support Flywheel natively. |
| Known issues | Fast release pace with **breaking changes**: 3.1.x broke 3.0.x; 3.1.13 and 3.1.20 changed the network format ("clients and servers must run matching AzureLib versions"). Docs and changelog disagree on LOD defaults (docs: on by default, 20 and 28 blocks; 3.1.19 changelog: off unless `withLodConfig` is called, 40 and 48 blocks). 3.2.0 added bone hitboxes where clients report poses to the server. | Not much new on 1.21.1. Third-party "AcceleratedRendering" can speed it up (it has no AzureLib support). | Java code per animation; no blending; armour needs a custom layer on a non-humanoid model | Cannot express sit, work, emote or weapon styles | Big engineering cost; per-bone instances for skinned rigs; shader compatibility via a third-party mod |

**Epic Fight** (GPL-3.0-or-later, NeoForge 1.21.1 build 21.17.3.1 from 2026-05-31) and **Entity Culling** (tr7zw Protective License, client-only, 1.11.3 on 2026-10-08) are compatibility targets, not options. See sections 9 and 10.

## 2. Decision and benchmark gate

### 2a. Decision

AzureLib is the maintainer's decision. The reasons below are the ones that led to it.

1. **AzureLib for every NPC**, pinned to one exact version (3.2.2 today) and bundled jar-in-jar like NovoAtlas, with a version range in `neoforge.mods.toml` limited to that minor line (`[3.2.2,3.3)`). Reasons:
   - It is the only option with built-in distance LOD (bone depth and update rate), which is most of what crowds need.
   - Since June 2026 most of its releases have been crowd-performance work: allocation-free sampling, Molang and bone poses, and skipping bones at rest.
   - It has armour-on-bone and item-on-bone layers, crossfades and layered controllers, and a Blockbench plugin. Animations are data (JSON), so artist agents can make them without writing Java.
   - MIT licence, active maintenance on exactly our Minecraft and loader version.
2. **Our own thin wrapper** (`client/core/anim`) between NPC renderers and AzureLib. It owns tier selection, budgets, the adaptive governor, the state-to-clip resolver and fallbacks, and calls AzureLib's LOD hooks. Pods never call AzureLib's LOD or controller APIs directly. If AzureLib breaks or stalls, only the wrapper and the renderer base class change.
3. **Explicit configuration only.** We always pass our own LOD values (section 4) and never depend on AzureLib defaults, because its docs and changelog disagree about them.
4. **Not used:** AzureLib bone hitboxes (`AzHitboxHolder`). They make clients report poses to the server (upstream traffic, and a client the server must trust), and combat uses vanilla boxes. `AzCommand` server dispatch is not used for routine animation either (section 6).
5. **GeckoLib** is a fallback only: if the gate fails badly, or AzureLib later becomes unmaintained or unstable. Both read the same Bedrock geo format, so models move over with little work; animation files need checking for AzureLib-only loop modes.
6. **Vanilla keyframe** is the last fallback, only if both libraries fail the gate badly. We would keep our wrapper and LOD and give up crossfades, and the authoring output becomes generated Java.
7. **Flywheel** is a later, optional phase (section 8), only if the benchmark shows the CPU cost of drawing far crowds is still the bottleneck after LOD.

### 2b. Benchmark harness (tickets R0-1 to R0-4)

A dev-only client benchmark, built before any NPC model content, that renders N identical animated test NPCs with each technology and records frame data.

**Packaging.** A separate Gradle source set `bench` with its own mod id `lotrrevival_bench`. It is never part of the release jar. GeckoLib and AzureLib are bench-only runtime dependencies here (until the decision). AzureLib uses its own packages, so both can be loaded at once. A run configuration `runBenchClient` starts it. A `benchJar` task builds `lotrrevival-bench-<version>.jar` so Seth can drop it into his normal launcher instance with Sodium and Iris.

**Test rig: the Blockbench pilot #10 hobbit** (maintainer decision). Pilot #10 is rescoped to the AzureLib format, the `humanoid` bones of section 5b and the clip names of section 5c. It delivers `move.idle`, `move.walk`, `attack.sword.1`, `attack.sword.2` and `move.march` (about 18 bones, about 30 cubes, 64x64 texture, all original). The same Blockbench project is also exported as Mojang-mapped Java keyframes for the `keyframe` contender; GeckoLib loads the same Bedrock JSON. The pilot's time and number of review rounds size the content wave.

**Contenders** (one entity type each, same hitbox, texture and equipment):

| Id | What | Notes |
| --- | --- | --- |
| `baseline` | Today's `HumanoidModel` procedural renderer | The floor: the cheapest possible humanoid |
| `keyframe` | `HierarchicalModel` + `AnimationDefinition` | Warden/Camel style |
| `gecko` | GeckoLib 4.9.x renderer | No LOD |
| `azure` | AzureLib renderer, LOD off | |
| `azure_lod` | AzureLib + our tier wrapper with section 4 defaults | The candidate |

**Behaviour.** Bench NPCs have no AI. A server-side scripter moves them along fixed circular paths at walking speed (so walk animations, movement packets and interpolation are realistic). It swings each NPC's weapon every 1 to 2 s (random, seeded) and hurts 5% of them every 5 s. Each wears an iron helmet and chestplate and holds an iron sword. Commands turn armour and held items off to measure layer cost.

**Layouts.** Fixed camera position, yaw and pitch, superflat "bench" world, noon, clear weather.

- `crowd`: all N within 8 to 24 blocks in front of the camera (a village square or melee).
- `battle`: 25% within 16 blocks, 50% at 16 to 40, 25% at 40 to 64 (the real battle case; shows the effect of LOD).

**Commands** (op, bench mod only):

- `/lotrbench run <contender> <n> <layout> [seconds]` clears the area, spawns, warms up for 10 s, records for 30 s (default), then writes `run/bench/<date>-<contender>-<n>-<layout>.json` and prints a summary in chat.
- `/lotrbench suite [passes]` runs the whole matrix: 5 contenders x N in {50, 100, 200, 400} x 2 layouts, about 45 s each, or about 30 minutes per pass. It writes `run/bench/suite-<date>.csv`.
- `/lotrbench layers <armour|items|shadows|nametags> <on|off>`.

**Metrics** (per run):

- Frame count, average FPS, 1% low FPS, p50, p95 and p99 frame time, worst frame.
- **Entity CPU ms per frame**: wall time between NeoForge's `RenderLevelStageEvent` stages `AFTER_CUTOUT_BLOCKS` and `AFTER_ENTITIES`. If a renderer mod skips these events, the field is marked unavailable.
- **Render-thread allocation** in bytes per frame (`com.sun.management.ThreadMXBean#getThreadAllocatedBytes`), GC count and total GC pause in the window.
- Integrated-server MSPT (average and p95), to check that the scripter is not the bottleneck.
- Environment: CPU and GPU strings, driver, OS, JVM and heap, window size, render distance, simulation distance, entity distance scaling, graphics mode, VSync and FPS limit. Also which of Sodium, Iris (and the shader pack name), Entity Culling and Flywheel are loaded, with their versions.

**How Seth runs it** (`docs/perf/animation-benchmark.md`, written by R0-1):

1. Use a desktop on mains power with other apps closed. Fullscreen 1920x1080, VSync off, max FPS unlimited, render distance 12, simulation distance 8, entity distance 100%, Fancy graphics.
2. **Pass A, plain:** `./gradlew runBenchClient` on the bench branch, or the bench jar in a clean NeoForge 21.1.x instance. Create the "bench" world preset, then run `/lotrbench suite`.
3. **Pass B, typical player:** the same instance plus Sodium and Entity Culling. Run `/lotrbench suite`.
4. **Pass C, shaders:** pass B plus Iris with Complementary Reimagined (or Seth's usual pack) at default settings. Run `/lotrbench suite`.
5. Attach the three CSVs and the hardware line to the ticket. An agent then commits them to `docs/perf/results/` with a short summary table.

### 2c. What the gate does (gate G-R0, Seth signs off on the results)

AzureLib is already chosen. The gate exists to **confirm the LOD settings** (tier distances, caps, update rates, bone depths, the governor budget) and to **catch problems** before content production.

**Pass:** in pass B on the reference PC, `azure_lod` meets the section 3 targets in `battle` with 200 NPCs. Opus 1 then tunes the section 4 numbers from the results and freezes them.

**Problems, but AzureLib stays:** if `azure_lod` misses the targets by less than 25%, or is more than 15% slower than `keyframe` at p95, or allocates over 2 KB per NPC per frame, keep AzureLib. Then tighten the tiers (shorter T0 and T1 distances, lower caps), file upstream issues with the bench data, and pull the Flywheel phase (section 8) forward if entity CPU is the bottleneck.

**Fails badly (fallback):** switch to GeckoLib (or, failing that, vanilla keyframes) only if one of these happens:
- `azure_lod` misses the 200-NPC `battle` target by 25% or more on average FPS while GeckoLib or `keyframe` meets it.
- AzureLib crashes, leaks memory or corrupts poses in the bench.
- AzureLib breaks on a dedicated server.

That decision goes back to Seth with the data.

In every case, record pass C numbers as the shader-user expectation.

## 3. Render budgets and targets

**Reference PC** ("a decent PC"; Seth confirms his real hardware, see open questions): 6-core desktop CPU of the Ryzen 5 5600 or Core i5-12400 class, RTX 3060 or RX 6600 class GPU, 16 GB RAM, 1080p, Java 21, 4 to 6 GB heap, render distance 12, Sodium installed (pass B).

These are initial targets. R0-4 revises them from the benchmark, then they are frozen for the content wave.

| Scenario | Target | Notes |
| --- | --- | --- |
| Village, 30 NPCs in view | Average 120+ FPS, entity CPU 2 ms or less | Most of the time a player is in or near a settlement |
| Patrol or caravan, 10 to 20 NPCs plus mounts | No measurable drop from an empty scene (5% or less) | |
| Battle, 200 fighters within 64 blocks (`battle` layout) | **Average 60+ FPS, 1% low 40+, p95 frame 20 ms or less, entity CPU 6 ms or less** | The headline target |
| Stress, 400 within 64 blocks | Average 30+ FPS, no GC pause over 50 ms | Must not fall apart |
| Shaders (pass C) | Battle 200: average 40+ FPS on the reference GPU | Shadow passes render entities again |
| Allocation | Under 2 KB per animated NPC per frame (target near zero) | Render thread, steady state |
| Per-NPC CPU by tier | T0 60 µs or less, T1 25 µs or less, T2 10 µs or less | Measured as entity CPU / count in single-tier runs |

**Tier caps** apply at any distance, nearest first: at most **32 NPCs in T0** and **96 in T0 + T1**. Extra NPCs are pushed down to the next tier.

**Adaptive governor** (client config `adaptive_crowd_detail`, default on): keeps a 1-second moving average of entity CPU ms. If the average stays over the budget (6 ms by default, configurable) for 2 s, the T0 and T1 distances and caps shrink by 10% per step, down to 50% of their configured values. They recover by 5% per 2 s once the average has been under 70% of the budget for 4 s. The governor never culls; it only changes tiers.

**Server and network budgets** (rendering does not set these, but this spec must not break them): animation adds no packets per tick. State changes cost about 10 bytes each (section 6). NPC AI cost (MSPT) belongs to the NPC pod's combat and battle specs.

## 4. Animation LOD tiers

The tier is chosen per NPC on the client from the distance to the camera, scaled by the vanilla entity distance option and the Animation quality preset. Hysteresis is 2 blocks: an NPC moves down a tier at distance d and moves back up at d - 2. Tiers are re-evaluated every 5 ticks per NPC, staggered by entity id so not all NPCs switch on the same frame. Tier caps and the governor (section 3) apply after the distance rule.

Default distances (preset **High**; Low x0.6, Medium x0.8, Ultra x1.5):

| | **T0 Full** | **T1 Reduced** | **T2 Static** | **T3 Culled** |
| --- | --- | --- | --- | --- |
| Distance | 0 to 16 blocks | 16 to 40 | 40 to entity render limit | Beyond the vanilla entity render limit (a humanoid's box gives about 64 blocks at 100% entity distance), outside the view frustum, or hidden by Entity Culling if installed |
| Animation update | Every frame | Every 2 ticks (10 Hz), pose held in between (AzureLib animation LOD) | Not sampled. A **key pose** per state (mid-stride for walk and run, neutral for idle, guard for combat, seated, at work), refreshed when the state changes or every 20 ticks | None |
| Bones | All | Tree depth 3 or less (hides fingers, cloak tails, hair, ornaments) via AzureLib bone LOD | Depth 2 or less | - |
| Blending | Crossfades (4 ticks default per clip), layered controllers (base, upper, action), procedural head look | Snap changes (0 ticks), base and action only, no head look | None | - |
| Keyframe sounds and particles | Yes | Particles off, sounds on | Off | Off |
| Name tag | Per `npc-base` rules (crosshair within 8 blocks) | Never | Never | - |
| Held items | Main hand and off hand | Main hand; off hand only if it is a shield, banner or torch | Main hand only if it is a weapon, banner or tool longer than 1 block; otherwise none | - |
| Armour layers | All four pieces, trims and enchantment glint | All four, no glint | Helmet and chestplate only, no glint (faction silhouette) | - |
| Shadow | Vanilla blob shadow | Off beyond 24 blocks | Off | - |
| Iris shadow pass | Rendered with the pose already computed this frame (never animated twice) | Same | Skipped beyond the pack's entity shadow distance, or if Iris reports a shadow pass and the tier is T2 | - |

Rules for every tier:

- **Animate once per frame.** The wrapper stamps each NPC with the frame number when its pose was computed, so a second render in the same frame (Iris shadow pass, mirrors, maps) reuses the pose.
- **Correct on first sight.** The visual state is worked out from synced data at render time, not built up from client ticks. An NPC that Entity Culling skipped, or that just came into view, shows the right state on its first frame.
- **Death, hurt and attack one-shots** always play in T0 and T1. In T2 they switch the key pose (death holds the last frame of the death clip as its key pose).
- Bosses and named characters (later specs) may ask for "T0 up to 32 blocks" through a renderer flag. They do not count toward the T0 cap.

## 5. Authoring pipeline

### 5a. Tools and flow

1. **Blockbench** desktop with the **AzureLib Animator** plugin (2.2.0 or newer), project format "AzureLib entity". Artist agents use the Blockbench MCP server (`docs/tooling.md` section 5); humans use the desktop app.
2. The artist builds the model on a **rig template** (section 5b), paints the texture, then animates the clips listed for that rig (section 5c).
3. Export: geo to `geo/entity/`, animations to `animations/entity/`, texture to `textures/entity/` (section 5d). Files are committed as resources. They are not generated, so they live in `src/main/resources`; this is the same kind of exception AGENTS.md makes for textures.
4. Review: the artist posts turntable and per-clip GIFs (Blockbench screenshot tools) in the ticket. The pod lead approves. Seth playtests in game.
5. **Validator** (part of R1-1): at resource load the client checks every registered NPC renderer's geo for the required bones of its rig, and its animation file for every required clip (with fallbacks, section 5c). With `-Dlotrrevival.strictAssets=true` (dev runs and CI) a missing bone or clip, or a geo or animation file that does not load, is a hard error. In normal play it is logged once per file. This matters because AzureLib skips broken models with a log line instead of failing.

Pilot #10 (hobbit, Blockbench MCP) is rescoped to this pipeline (maintainer decision): AzureLib format, the `humanoid` rig bone names, and the clips listed for the bench rig in section 2b with the names below. It is the benchmark rig, and its time and review-round numbers size the content wave.

### 5b. Rigs and bone names

A **rig** is a bone hierarchy that animations are written against. Races with the same body plan share a rig and its animations; proportions may differ.

- **Rotation-only rule.** Animations on shared rigs key rotations only. The one exception is `root` translation for bob and crouch, in pixels, scaled per race by a rig-specific factor in the renderer. This lets one `walk` serve men, elves, hobbits and dwarves.

| Rig | Used by | Notes |
| --- | --- | --- |
| `humanoid` | men of every faction (Gondor, Rohan, Dale, Bree, Rangers, Harad, Easterlings, Dunlendings, hill-men, ruffians, Morgul and Black Númenórean units), elves, dwarves, hobbits (shirriffs, bounders) | One shared rig with proportion variants per race (maintainer decision: hobbits and dwarves included) |
| `orc` | orcs, goblins, Uruk-hai, half-orcs, half-trolls | Hunched and heavier; own clip set; same bone names as `humanoid`, so weapon and formation clips can be ported. Half-trolls use it at a larger scale. |
| `troll` | trolls of every kind | Own bones (no fingers, heavy club arm), own clip set (section 5c). Never takes a formation slot. |
| `horse` | horses and war-horses ridden by cavalry | Clip set in section 5c; owned by the mounts spec |
| `warg`, `spider`, ... | later specs | Each gets its own table when its spec is written |

`humanoid` and `orc` bones (exact names, all lowercase):

```
root
  body
    torso
      head
        head_armor        (helmet anchor)
      right_arm
        right_forearm
          right_hand
            right_item    (held item locator, no cubes)
        right_arm_armor   (shoulder/arm armour anchor)
      left_arm
        left_forearm
          left_hand
            left_item
        left_arm_armor
      chest_armor         (chestplate anchor)
      cape                (optional; depth 4, hidden in T1)
    right_leg
      right_shin
      right_leg_armor
      right_boot_armor
    left_leg
      left_shin
      left_leg_armor
      left_boot_armor
```

Anchor bones (`*_armor`, `*_item`) carry no cubes. `AzArmorLayer` and `AzBlockAndItemLayer` render onto them. Detail bones that should vanish at range (fingers, hair, ornaments, belt pouches) go at depth 4 or more.

### 5c. Animation state names

Clip keys inside the animation file are `animation.<rig>.<group>.<name>[.<variant>]`, lowercase, with dots between parts and underscores inside a part. Variants are numbered `1`, `2`, ... Weapon classes match `combat-items.md`.

| Group | Clips (humanoid) | Loop | Controller |
| --- | --- | --- | --- |
| `move` | `idle`, `idle.<n>` (fidgets), `walk`, `run`, `sneak`, `swim`, `climb`, `fall`, `ride`, `march` (in step), `flee` (rout), `charge` (berserk run, weapon raised) | loop | base |
| `pose` | `sit`, `sit_ground`, `sleep`, `guard` (standing at attention), `kneel` | loop | base |
| `work` | `work.farm`, `work.smith`, `work.chop`, `work.mine`, `work.carry`, `work.sweep`, `work.cook`, `work.trade` | loop | base |
| `social` | `talk`, `talk.<n>`, `listen` | loop | upper |
| `emote` | `wave`, `cheer`, `bow`, `point`, `salute`, `taunt`, `laugh`, `cower`, `order` (leader's volley or advance order), `war_cry` | once | action |
| `hold` | `hold.<class>` (how the arms carry the weapon while moving), `hold.pike.lowered` (pike or spear levelled past the front rank), `hold.shield`, `hold.banner`, `hold.torch` | loop | upper |
| `attack` | `attack.<class>.<n>` for class in `unarmed`, `dagger`, `sword`, `spear`, `polearm`, `pike`, `battleaxe`, `hammer`, `lance`, `throw` | once | action |
| `ranged` | `ranged.bow.ready` (arrow nocked, waiting for the order), `ranged.bow.draw`, `ranged.bow.release`, `ranged.crossbow.aim`, `ranged.crossbow.reload`, `ranged.sling.whirl`, `ranged.sling.release` | draw and aim hold the last frame | upper and action |
| `defend` | `block.shield`, `brace.shield` (front-rank shield braced, shoulder in, braced stance), `block.weapon`, `dodge` | hold and once | `brace.shield` base + upper; others upper and action |
| `ride` | `ride.lance.couched` (lance lowered under the arm for the charge), `ride.idle`, `ride.<class>` attacks fall back to `attack.<class>` | loop | upper |
| `react` | `hurt`, `hurt.<n>`, `stagger`, `knockdown`, `death`, `death.<n>` | once (death holds the last frame) | action |

**Every NPC is animated** (maintainer decision): every faction, every role and every discipline level gets a model on one of the rigs above and at least the minimum set below. No NPC type ships on the old vanilla biped once its race's model lands.

**Minimum sets.** These are written once per rig in `<rig>.animation.json` and shared by every race on that rig. Races add overrides only where they want personality.

| Set | Clips | Rigs that need it |
| --- | --- | --- |
| **Base** | `move.idle`, `move.walk`, `move.run`, `pose.sit`, `react.hurt`, `react.death`, `attack.unarmed.1`, `attack.sword.1`, `attack.sword.2`, `hold.sword`, `defend.block.shield`, `social.talk`, `emote.wave`, `work.farm`, `work.smith` | `humanoid`, `orc` (orcs swap `emote.wave` for `emote.taunt` and `work.farm` for `work.mine`) |
| **Formation, all disciplines** | `move.flee` (rout), `emote.order` | `humanoid`, `orc` |
| **Formation, RIGID and ORGANIZED** | `defend.brace.shield`, `hold.pike.lowered`, `ranged.bow.ready` (with `ranged.bow.draw` and `ranged.bow.release` for volleys in rank), `move.march` | `humanoid`, `orc`. Needed for the RIGID factions Gondor, dwarves, elves, Uruk-hai and Easterlings, and the ORGANIZED factions Rohan, Dale, Rangers, Harad and Morgul. The rig carries them once for all. |
| **Cavalry** | `ride.idle`, `ride.lance.couched` (couched-lance charge); the horse plays `move.gallop` and `move.charge` | `humanoid` riders (Rohan, Gondor knights, Dale, Harad, Easterlings) and `horse`. `orc` riders use the warg spec later. |
| **LOOSE flavour** | `emote.war_cry`, `move.charge` (berserk charge), `emote.taunt` | `orc` (orcs, goblins, half-trolls) and `humanoid` (Dunlendings, hill-men, ruffians; shirriffs use `emote.war_cry` only, played as a shout) |
| **Troll** | `move.idle`, `move.walk`, `move.run`, `move.charge`, `move.flee`, `react.hurt`, `react.death`, `attack.smash.1` (overhead two-handed smash), `attack.smash.2` (sweeping swat), `emote.war_cry` (roar) | `troll` only. No formation clips: trolls never take a slot (PR #186 section 2b). |

Discipline mostly changes **which** of these clips the server asks for (section 6), not the clip set. RIGID blocks brace, lower pikes and march in step. ORGANIZED squads march, then charge and break into normal combat clips. LOOSE swarms war-cry, berserk-charge and flee when their captain dies. Everything not listed falls back.

**Fallback table** (the resolver walks it until it finds a clip that exists):

| Missing | Falls back to |
| --- | --- |
| `attack.dagger.*` | `attack.sword.*` |
| `attack.polearm.*`, `attack.pike.*`, `attack.lance.*` | `attack.spear.*` |
| `attack.spear.*` | `attack.sword.*` |
| `attack.battleaxe.*`, `attack.hammer.*` | `attack.sword.*` |
| `attack.throw.*` | `attack.sword.1` |
| any `attack.*` | `attack.unarmed.1` |
| `hold.<class>` | `hold.sword`, then no upper-body overlay |
| `move.run` | `move.walk` at 1.5x speed |
| `move.march` | `move.walk` |
| `move.flee`, `move.charge` | `move.run` |
| `defend.brace.shield` | `defend.block.shield` |
| `hold.pike.lowered` | `hold.pike`, then `hold.spear`, then `hold.sword` |
| `ranged.bow.ready` | first frame of `ranged.bow.draw`, held |
| `ride.lance.couched` | `hold.spear` |
| `emote.order` | `emote.point` |
| `emote.war_cry` | `emote.taunt`, then none |
| `attack.smash.*` (troll) | `attack.unarmed.1` of the troll rig |
| `work.*`, `pose.kneel` | `move.idle` |
| `social.*`, `emote.*` | none (base continues) |
| `react.death.*` | `react.death` |

### 5d. File layout

```
src/main/resources/assets/lotrrevival/
  geo/entity/<race>.geo.json                    one model per race (gender variants: <race>_female)
  animations/entity/<rig>.animation.json        shared clips for the rig
  animations/entity/<race>.animation.json       race-only clips and overrides (optional)
  textures/entity/<race>/<variant>.png          skins; <race>/<variant>_glow.png for glowmasks
src/main/art/blockbench/<race>.bbmodel          Blockbench source projects (kept for re-export, never loaded)
```

The renderer loads `<rig>.animation.json` and, if it exists, `<race>.animation.json`, with race clips overriding rig clips that have the same key. (AzureLib supports `includes` between animation files; the wrapper uses that or merges them itself.)

## 6. How AI state drives animation

Most of what the client needs is already synced by vanilla. The client works it out at render time:

| Visual | Comes from | Network cost |
| --- | --- | --- |
| idle, walk, run, playback speed | Interpolated horizontal speed in blocks per tick: idle under 0.015, walk under 0.17, run at 0.17 or more. Playback speed = speed / the rig's reference speed, clamped to 0.6 to 1.6. Thresholds live in the rig config. | None (movement packets) |
| sneak, swim, fall, ride, sleep | Vanilla pose and flags, `isPassenger`, `onGround` | None |
| Attack one-shot and its variant | Vanilla `swing()` (already broadcast). Class = main-hand item's weapon class from `WeaponStats`. Variant = (entity id x 31 + local swing counter) mod the number of variants. | None. Different viewers may see different variants (cosmetic only). |
| Ranged draw and aim | `isUsingItem` + item | None |
| Shield block | `isBlocking` | None |
| Hurt, death | Vanilla hurt animation packet, `deathTime` | None |
| Head look | Vanilla head rotation | None |

What the server must say explicitly lives in **two bytes of entity data**, defined by core and attached by the NPC pod's entity class:

- `ANIM_ACTION` (byte): the low 5 bits are the action (`NONE`, `SIT`, `SIT_GROUND`, `SLEEP`, `GUARD`, `KNEEL`, `WORK`, `TALK`, `LISTEN`, `COWER`, ...; up to 32); the high 3 bits are its variant (for example which `work.*`). `NONE` means "use the locomotion rules above". This is a looping state: set it when the AI enters the activity and clear it when it leaves.
- `ANIM_PULSE` (byte): one-shot cosmetic events that vanilla does not cover (`WAVE`, `CHEER`, `BOW`, `POINT`, `SALUTE`, `TAUNT`, `LAUGH`, `STAGGER`, `KNOCKDOWN`, `DODGE`). The low 5 bits are the event; the high 3 bits are a counter that goes up on each send, so the same emote twice in a row plays twice. The client plays a pulse only if it arrives within 1 s of the change (it remembers the last value it saw), so an NPC coming into view does not replay an old emote.

The **server API** (core, `core.api.anim`):

- `AnimAction` and `AnimPulse` enums (stable ids, never reordered; new values are appended).
- `AnimSync.define(Class<? extends Entity>)` returns the two `EntityDataAccessor<Byte>`. It is called from the entity class's static init by the NPC pod.
- `AnimSync.setAction(entity, action, variant)`: no-op if unchanged.
- `AnimSync.pulse(entity, pulse)`.

All of this is plain common code with no client imports. The client reads it through `client/core/anim/AnimStateReader`.

**Formation and discipline values** (requested by PR #186 section 2h; appended to the enums in this order):

| Value | Byte | Clip | Set by the squad AI (NPC pod) when |
| --- | --- | --- | --- |
| `BRACE` | `ANIM_ACTION` | `defend.brace.shield` | a RIGID front-rank member with a shield has an enemy within 6 blocks in front |
| `PIKE_LOWER` | `ANIM_ACTION` | `hold.pike.lowered` | a RIGID second-rank member with a pike, spear or polearm is in COMBAT, or advancing on an enemy within 16 blocks |
| `VOLLEY_READY` | `ANIM_ACTION` | `ranged.bow.ready` | an archer in rank waits between volleys. The draw and release are vanilla's using-item state (no extra sync). |
| `MARCH` | `ANIM_ACTION` | `move.march` | a RIGID or ORGANIZED squad is in MARCH. The clip's phase comes from the level's game time, not the entity's walk distance, so a whole block steps together with no extra sync. |
| `ROUT` | `ANIM_ACTION` | `move.flee` | the member is routing (any discipline), including LOOSE members after their captain dies |
| `LANCE_COUCH` | `ANIM_ACTION` | `ride.lance.couched` | a mounted member with a lance or spear is in a charge (wedge charge or ORGANIZED charge) |
| `BERSERK` | `ANIM_ACTION` | `move.charge` | a LOOSE member rushes its target (and trolls charging) |
| `ORDER` | `ANIM_PULSE` | `emote.order` | the squad leader orders a volley or an advance |
| `WAR_CRY` | `ANIM_PULSE` | `emote.war_cry` | a LOOSE squad engages, an ORGANIZED squad starts its charge, or a troll starts a charge (roar) |

The troll smash is an ordinary attack (vanilla swing) resolved to `attack.smash.<n>` by the `troll` rig, so it needs no byte. With these values `ANIM_ACTION` uses 18 of its 32 action slots.

**Sync cost.** A changed byte goes out in vanilla's set-entity-data packet. That is about 8 bytes of payload (packet id, entity id varint, index, serializer id, value, end marker) plus about 2 bytes of framing, so roughly 10 bytes per change per tracking player. It is batched with any other data changes for that entity in the same tick. A battle of 200 NPCs with 2 changes each per second (formation states included, since they change only on state transitions) costs about 4 KB/s (about 32 kbit/s) per player, which is small next to the movement packets those 200 NPCs already send. Rules for the NPC pod:

- Never set animation data every tick.
- Never use AzureLib's `AzCommand` server dispatch for routine states, because its packets name the controller and animation by string and cost several times more.
- Never sync the locomotion state; the client works it out.

## 7. Client architecture (`client/core/anim`)

| Piece | Job |
| --- | --- |
| `NpcAnimRenderer<T>` (base renderer) | Extends AzureLib's entity renderer. Applies the tier from `AnimLod`, skips animation when the tier or frame stamp says so, adds armour and item layers per tier, controls the shadow and the name tag per tier. NPC pod race renderers extend this. |
| `AnimLod` | Pure tier function (distance, hysteresis, caps, preset, governor scale) → tier. **No client imports**, so GameTests can call it. |
| `CrowdGovernor` | Entity CPU timing (render stage events), moving average, scale factor, F3 line: `LOTR crowd: T0 24 T1 60 T2 80 hidden 36 | 3.2 ms (budget 6.0, scale 1.00)`. |
| `ClipResolver` | State + weapon class + rig → clip key through the fallback table. Pure and data-driven, unit tested. |
| `RigConfig` | Per rig: bone depths, locomotion thresholds, reference speed, root scale per race. A Java record (it is code, not data). |
| `AnimAssetValidator` | Section 5a step 5. |
| `ClientAnimConfig` | `lotrrevival-client.toml`: `animation_quality` (LOW, MEDIUM, HIGH, ULTRA), `adaptive_crowd_detail` (true), `entity_cpu_budget_ms` (6.0), `t0_cap` (32), `t01_cap` (96), `debug_tier_colors` (false; tints NPCs by tier for playtests). |

`client/core/anim` only exists on the client. Registration happens in client mod-bus events, as for every other `client/` package.

## 8. Flywheel crowd rendering (later phase, gated)

**Gate:** only if the benchmark (or a later battle playtest) shows that, with LOD, `battle` 200 or 400 still misses the targets and entity CPU is most of the frame. Otherwise this phase is not done.

**Plan:**

- An optional integration package `client/core/anim/flywheel`, loaded only when `ModList` has `flywheel` (players get it from Create, or install it on its own; we do not bundle it). It is compiled against Flywheel's API artifact (`compileOnly`), with the runtime jar only in dev runs.
- It applies to **T1 and T2 only**. T0 stays on the AzureLib path with all its layers.
- It uses Flywheel's entity visualizer API: one `TransformedInstance` per **bone part** of the rig, with instancers keyed by (rig, part, texture atlas page). A dynamic visual writes bone matrices each frame (T1: from the AzureLib bone snapshot or our sampler at 10 Hz; T2: cached key pose matrices). Skins use a per-rig texture atlas so the number of instancers stays small.
- Held items in T1 and T2 use per-item-model instances (Flywheel library model support) for the main hand only. Armour uses per-piece instanced models built from the vanilla armour model geometry and our armour textures.
- **Shaders:** Flywheel turns itself off when a shader pack is in use (`VisualizationManager.supportsVisualization` is false). The visualizer then hands the entity back to the normal renderer, so shader users just get the AzureLib path. Iris Flywheel Compat and Colorwheel are user choices and not tested by us beyond one smoke playtest.
- **Exit criteria for the spike (R2-1):** at least 2x lower entity CPU at 400 NPCs in `battle` (pass B) with no visible pose errors. If it does not reach that, the code is dropped.

Precedent: Vanillin (MIT) draws minecarts, chests and bells instanced with Flywheel, and Create draws trains and contraptions with it. Neither animates skinned characters, so this phase is a real spike.

## 9. Compatibility: Sodium, Iris, Entity Culling

- **Sodium** (NeoForge 1.21.1 build 0.8.13, Aug 2026): speeds up vanilla `ModelPart` vertex writing but not AzureLib or GeckoLib cubes. This favours `baseline` and `keyframe` in pass B, which is why the decision rule uses pass B. Nothing to do in code.
- **Iris** (NeoForge 1.21.1 builds 1.8.x, LGPL-3.0):
  - AzureLib draws through vanilla entity render types, so shader packs treat NPCs as entities and render them again in the shadow pass. The once-per-frame pose rule (section 4) keeps the shadow pass from animating twice.
  - The wrapper reads Iris's public API (`IrisApi`: whether a shader pack is in use and whether this is the shadow pass) through a soft-loaded adapter that is only touched when `ModList` has `iris`. It is a `compileOnly` dependency on Iris's API jar, or reflection if the API jar is not on a maven.
  - NPC textures use cutout, not translucency (capes included). Glowmasks (orc eyes, lanterns) use AzureLib's glow layer, which got its Iris fix in 3.1.21.
  - Playtest: one shader pack with PBR (Complementary Reimagined) and one without.
- **Entity Culling** (client-only, protective licence, never bundled): hides NPCs behind walls (good for villages and hobbit holes). Its tick culling stops client ticks of hidden entities, which is safe because our visuals are worked out at render time (section 4). We ship no whitelist entries. If a playtest shows a problem, our entity ids go into its whitelist config through its documented config, not code.

## 10. Epic Fight soft compatibility

**Verdict: practical, data only, no dependency.**

- **Licence:** Epic Fight is GPL-3.0-or-later. Our code is LGPL-3.0-only, and compiling against Epic Fight or calling its API would pull our jar under the GPL when distributed. So: **no compile or runtime dependency and no Epic Fight classes**. Seth's decision of no hard dependency stands, and the licence makes soft *code* compat a bad idea too.
- **How:** Epic Fight reads weapon (and armour) capabilities from datapack JSON at `data/<namespace>/capabilities/weapons/<item path>.json`, with `type` naming one of its weapon types and optional `attributes` (impact, armour negation, max strikes). Data in our jar is loaded only when Epic Fight is present and ignored otherwise. This is how the published "Epic Fight Weapons Compat" mod covers more than 500 weapons on NeoForge 1.21.1 with no code. Epic Fight also auto-assigns types by item id regex (`item_keyword`); our explicit files take priority over that.
- **Generated by datagen** (R3-1): a small `DataProvider` in `datagen/` writes one JSON per weapon item from core's weapon class table. It uses only string ids, with no Epic Fight classes. Proposed mapping (to check against the Epic Fight build we test):

| Our class | Epic Fight `type` | Notes |
| --- | --- | --- |
| sword | `epicfight:sword` | |
| dagger | `epicfight:dagger` | Poisoned daggers too |
| spear | `epicfight:spear` | One- and two-handed |
| polearm, pike | `epicfight:spear` | Two-handed attributes only |
| battleaxe | `epicfight:axe` | |
| hammer | `epicfight:greatsword` | Heavy two-handed set; revisit if Epic Fight adds a hammer type |
| lance | `epicfight:spear` | |
| throwing axe | `epicfight:axe` | |
| bows, crossbows | `epicfight:bow`, `epicfight:crossbow` | Sling: `epicfight:bow` or omitted (test) |
| shields (when core adds them) | `epicfight:shield` | |

- **Scope (maintainer decision):** weapon type only. Files carry `type` and no `attributes`, so Epic Fight's defaults for that type apply. No armour files.
- **NPCs:** **no Epic Fight mob patches** for our entities. A mob patch swaps the entity's model, armature and renderer for Epic Fight's and replaces its combat behaviour, which would bypass our renderer, LOD and AI. Epic Fight leaves unknown entities alone, so nothing needs disabling.
- **Test:** a manual playtest with Epic Fight 21.17.x on NeoForge 1.21.1: each weapon class uses the expected move set; our NPCs still render with our renderer; no errors in the log. CI does not install Epic Fight.

## 11. Dedicated-server safety

- Everything under `client/` (renderers, `client/core/anim`, Iris and Flywheel adapters) is registered only from client mod-bus events (`@EventBusSubscriber(value = Dist.CLIENT)` or `if (FMLEnvironment.dist.isClient())` guards in the mod constructor).
- Common code (`core.api.anim`, entity classes, AI) never imports `net.minecraft.client`, `com.mojang.blaze3d`, AzureLib render or animator packages, Flywheel or Iris.
- AzureLib must be installed on dedicated servers (its packets register on both sides). Bundling it jar-in-jar covers that. We use none of its server features (hitboxes, server dispatch), but its classes must still load cleanly; the `runGameTestServer` CI job is a dedicated-server boot and proves this.
- **Isolation check** (R1-1): a `checkClientIsolation` verification task in `buildSrc`, run by `./gradlew build`. It scans compiled classes outside `io/github/sethwillemsvg/lotrrevival/client/` for references to `net/minecraft/client/`, `com/mojang/blaze3d/`, `mod/azure/azurelib/.../render/` and `.../animation/` (client parts), `dev/engine_room/flywheel/` and `net/irisshaders/`, and fails with the class and reference. An allowlist file covers reviewed exceptions, each with a one-line reason.

## 12. GameTest and CI

- **GameTests** (`core/test`, `@GameTest(template = "empty")`):
  - `AnimAction` / `AnimPulse` byte codec round-trips every value and variant; the pulse counter wraps from 7 to 0.
  - `AnimLod`: tier boundaries per preset; hysteresis (an NPC at 16.5 blocks moving inward from T1 stays T1 until 14); caps (40 NPCs within 10 blocks give 32 T0 and 8 T1, nearest first); governor scale 0.5 halves the distances.
  - `ClipResolver`: every weapon class in core's table resolves to some clip for the `humanoid` minimum set; the fallback order matches section 5c.
  - Server: `AnimSync.setAction` with an unchanged value leaves the entity data clean (no packet); a change marks it dirty once.
- **runtime-test** (headless client smoke): AzureLib is inside our jar, so the staging step is unchanged. R1-1 adds `-Dlotrrevival.strictAssets=true` to the JVM args so a broken geo or animation file fails CI. Note that a headless client may not draw frames, so render-path bugs are found by the benchmark and playtests, not CI.
- **Bench** is never run in CI (needs a GPU). The `bench` source set must still compile in `./gradlew build` so it does not rot.
- **Playtests** (`docs/playtest/animation.md`, written by R1-4): village of 30, a 200-fighter battle, the same with Sodium, Iris and Entity Culling, Animation quality presets, `debug_tier_colors`, and Epic Fight installed.

## Rules and numbers

| Thing | Value | Notes |
| --- | --- | --- |
| Library | AzureLib 3.2.2, range `[3.2.2,3.3)`, jar-in-jar | Bumped once per milestone at most, in a core PR with a bench rerun |
| Tier distances (High) | T0 0-16, T1 16-40, T2 40 to render limit | Presets x0.6 / x0.8 / x1.0 / x1.5 |
| Hysteresis | 2 blocks | |
| Tier re-evaluation | every 5 ticks, staggered by entity id | |
| T1 update rate | every 2 ticks | |
| T2 key pose refresh | on state change or every 20 ticks | |
| Bone depth | T0 all, T1 3 or less, T2 2 or less | |
| Caps | T0 32, T0+T1 96 | nearest first |
| Entity CPU budget | 6.0 ms | governor |
| Governor | -10% per 2 s over budget, down to 50%; +5% per 2 s after 4 s under 70% | |
| Crossfade | 4 ticks default (T0 only) | per clip override in rig config |
| Locomotion thresholds | idle under 0.015 b/t, run at 0.17 b/t or more | per rig, tuned in playtest |
| Synced animation data | 2 bytes per NPC (`ANIM_ACTION`, `ANIM_PULSE`) | about 10 bytes per change per tracking player |
| Pulse freshness | 1 s | |
| Allocation | under 2 KB per NPC per frame | |

## Edge cases

- **Multiplayer:** each client chooses its own tiers; nothing about LOD is synced. Attack variants can differ between viewers (cosmetic). Pulses reach only players tracking the NPC; late joiners do not replay old pulses.
- **Death, logout, dimension change:** an NPC that dies plays `react.death` and holds its last frame until vanilla removes the body. `ANIM_ACTION` is saved with the NPC (NPC pod) so a seated innkeeper is still seated after a reload; `ANIM_PULSE` is not saved. Dimension change re-creates client entities, so they start fresh from synced data.
- **Mounted NPCs:** the rider uses `move.ride` (base) plus its upper and action controllers; the mount has its own rig and tiers. The rider's tier is never better than its mount's, so they do not visibly desync.
- **Many players near one battle:** each client pays its own render cost; the server cost does not change with LOD.
- **Resource reload (F3+T) and pack changes:** AzureLib swaps caches after a reload (3.1.19). Our validator runs again; cached T2 key poses are cleared.
- **Missing assets in normal play:** fall back to `move.idle`; never crash; log once.
- **Spectator, invisibility, glowing:** follow vanilla (AzureLib fixed parity in 3.0.33).
- **Other mods that change entity rendering** (Entity Model Features, AcceleratedRendering): not supported for our NPCs. Bug reports are triaged, and fixes are not promised.

## Data and APIs

- **Core common:** `core.api.anim.AnimAction`, `AnimPulse`, `AnimSync` (accessor definition, set, pulse). Constants only; no registry.
- **Core client:** `client/core/anim/*` (section 7); `lotrrevival-client.toml` client config, the first client config (registered by core in client setup).
- **NPC pod uses:** `NpcAnimRenderer` as the base for race renderers; `AnimSync` in `NpcEntity` and its AI goals; rig bone and clip names from this spec.
- **Datagen:** `datagen/compat/EpicFightCapabilities` (R3-1) writes `data/lotrrevival/capabilities/weapons/*.json`.
- **Build:**
  - `build.gradle`: AzureLib `jarJar` and `implementation`, the `bench` source set and `runBenchClient`, `checkClientIsolation`.
  - `gradle.properties`: `azurelib_version`.
  - `neoforge.mods.toml`: AzureLib as a required dependency with a range.
  - `NOTICE`: an AzureLib entry (MIT) like the NovoAtlas one.
  - These are all shared files, so Opus 1 serializes them.
- **No new packets.** No new registries.

## Out of scope

- Facial animation, lip sync, voice.
- Inverse kinematics (feet on slopes, hands on ladders). Revisit after G4.
- Ragdolls and physics.
- Player animations (first and third person). Players keep vanilla animation; Epic Fight users get Epic Fight's.
- Per-race clip sets beyond the minimum set: those are art tickets in the NPC model content wave.
- Server-side AI cost for big battles: NPC pod battle spec.
- Flywheel unless the gate in section 8 opens.

## Acceptance tests

- [ ] GameTest: the codec, `AnimLod`, `ClipResolver` and `AnimSync` cases in section 12.
- [ ] CI: `checkClientIsolation` passes; `runGameTestServer` boots a dedicated server with AzureLib bundled; runtime-test passes with `strictAssets`.
- [ ] Bench: Seth's passes A, B and C are committed to `docs/perf/results/` and the decision is recorded in this spec (status changes to approved with the chosen library).
- [ ] Playtest: village of 30 NPCs at 120+ FPS on the reference PC (pass B setup); tier changes not noticeable at the High preset when walking through.
- [ ] Playtest: 200-fighter battle at 60+ FPS average (pass B), 40+ with shaders (pass C).
- [ ] Playtest: Iris with a PBR pack: NPCs, armour, held items and glowmasks look right in the main view and in shadows.
- [ ] Playtest: Entity Culling installed: NPCs behind walls vanish and reappear in the right pose with no pop of old emotes.
- [ ] Playtest: Epic Fight installed: every weapon class uses a fitting Epic Fight move set; our NPCs keep our renderer; without Epic Fight nothing changes.
- [ ] Playtest: a dedicated server with two clients: seated, working and talking NPCs look the same to both; a wave plays once for each.

## 13. Sources (checked 2026-10-28)

- AzureLib on Modrinth (MIT; versions, changelogs 3.1.10 to 3.2.2, "required on both"): https://modrinth.com/mod/azurelib and https://api.modrinth.com/v2/project/azurelib/version
- AzureLib source and docs (renderer config, LOD, armour and item layers): https://github.com/AzureDoom/AzureLib, `docs/azurelib/misc/rendering/azrenderer_config.mdx`, `.../layers/adding_armor_layer.mdx`, `.../layers/adding_blockitem_layer.mdx`; https://moddedmc.wiki/en/project/azurelib/latest/docs/misc/rendering/azrenderer_config
- GeckoLib on Modrinth (MIT; 4.9.3 for NeoForge 1.21.1; 5.5.7 for newer Minecraft): https://modrinth.com/mod/geckolib ; source https://github.com/bernie-g/geckolib
- Blockbench plugin registry (AzureLib Animator 2.2.0, GeckoLib Models & Animations 4.2.5, Animation to Java Converter 1.2.0): https://github.com/JannisX11/blockbench-plugins/blob/master/plugins.json ; Modded Entity format Mojang animation templates: https://web.blockbench.net/docs/variables/generated_formats_java_modded_entity.AnimationTemplates.html
- Vanilla keyframe API (`KeyframeAnimations.animate`): https://mappings.dev/1.19.4/net/minecraft/client/animation/KeyframeAnimations.html
- Flywheel (MIT, API and maven): https://github.com/Engine-Room/Flywheel ; versions https://maven.createmod.net/dev/engine-room/flywheel/flywheel-neoforge-1.21.1/maven-metadata.xml ; Vanillin (MIT, "incompatible with shaders"): https://modrinth.com/mod/flw-vanillin ; Iris Flywheel Compat: https://modrinth.com/mod/iris-flw-compat ; Colorwheel: https://github.com/djefrey/Colorwheel ; Flywheel and shaders discussion: https://github.com/Engine-Room/Flywheel/issues/252
- Epic Fight (GPL-3.0-or-later; 21.17.3.1 for NeoForge 1.21.1): https://modrinth.com/mod/epic-fight ; weapon capability datapacks: https://epicfight-docs.readthedocs.io/Guides/Weapons/page1/ ; mob patch datapacks: https://epicfight-docs.readthedocs.io/Guides/Entities/page1/ ; data-only compat precedent: https://modrinth.com/project/Udv3YbH7 ; regex auto-assignment: https://www.patreon.com/yessman/posts/use-regex-for-129811638
- Entity Culling (tr7zw Protective License; client-only; tick culling; Sodium FAQ): https://modrinth.com/mod/entityculling , https://github.com/tr7zw/EntityCulling
- Sodium (Polyform Shield; 0.8.13 for NeoForge 1.21.1) and its `ModelPart` fast path: https://modrinth.com/mod/sodium , https://deepwiki.com/cogiraxi/sodium-1.20.1-stable-0.5/7.2-entity-and-item-rendering-optimizations
- Iris (LGPL-3.0; 1.8.x for NeoForge 1.21.1): https://modrinth.com/mod/iris

## Tickets

Proposed; opened after this spec is approved. Days are agent days. All `pod:core` unless marked, milestone **G3.5 Living world**. Shared files are noted so Opus 1 can serialize them.

### Phase R0: benchmark and decision (before any NPC model content)

| # | Ticket | Days | Agent | Blocked by | Shared files |
| --- | --- | --- | --- | --- | --- |
| R0-1 | Bench source set and mod `lotrrevival_bench`: `runBenchClient`, `benchJar`, bench world preset, scripted movers, `/lotrbench run, suite, layers`, metrics (stage timing, allocation, GC, MSPT, environment), JSON and CSV output, `docs/perf/animation-benchmark.md` with Seth's run steps | 2 | sonnet-1 | - | build.gradle, gradle.properties |
| R0-2 | **Pilot #10 rescoped** (art, edit the existing issue rather than opening a new one): the hobbit in AzureLib format on the section 5b bones, 64x64 texture, `move.idle`, `move.walk`, `move.march`, `attack.sword.1`, `attack.sword.2`; also exported as Mojang Java keyframes for the `keyframe` contender; record time and review rounds | 1 | artist | - | - |
| R0-3 | The five contender renderers and entity types in the bench mod (baseline, keyframe, gecko, azure, azure_lod with a minimal tier wrapper) | 2 | sonnet-2 | R0-1, R0-2 | - |
| R0-4 | **Seth runs passes A, B and C**; an agent commits the results and a summary to `docs/perf/results/`. Opus 1 updates section 3 targets and records the decision in this spec (gate G-R0). | 0.5 + Seth | Seth, Opus 1 | R0-3 | - |

### Phase R1: framework (after G-R0, before the content wave)

| # | Ticket | Days | Agent | Blocked by | Shared files |
| --- | --- | --- | --- | --- | --- |
| R1-1 | Adopt the chosen library: jarJar and pinned range, NOTICE, mods.toml; `checkClientIsolation` task; `strictAssets` flag in the runtime-test job; `AnimAssetValidator` | 1.5 | sonnet-1 | R0-4 | build.gradle, gradle.properties, NOTICE, build.yml |
| R1-2 | `core.api.anim`: `AnimAction`, `AnimPulse`, `AnimSync`; GameTests | 1 | sonnet-2 | R0-4 | - |
| R1-3 | `client/core/anim`: `AnimLod`, `CrowdGovernor` and F3 line, `ClipResolver` with the fallback table, `RigConfig` for `humanoid` and `orc`, `ClientAnimConfig`; GameTests for the pure parts | 2 | sonnet-1 | R1-1 | - |
| R1-4 | `NpcAnimRenderer` base: tiers per section 4 (layers, shadow, name tag, once-per-frame pose, Iris adapter); `debug_tier_colors`; `docs/playtest/animation.md` | 2 | sonnet-2 | R1-2, R1-3 | - |
| R1-5 (pod:npc, Opus 3) | Convert `NpcHumanoidRenderer` to `NpcAnimRenderer` with the humanoid rig; add `AnimSync` accessors to `NpcEntity` and set actions from routines (work, sit, talk) | 2 | NPC pod | R1-4 | NpcEntity |
| R1-6 (pod:npc, Opus 3) | Squad AI sets the formation values (`BRACE`, `PIKE_LOWER`, `VOLLEY_READY`, `MARCH`, `ROUT`, `LANCE_COUCH`, `BERSERK`, `ORDER`, `WAR_CRY`) per PR #186 section 2h; game-time phase for `move.march` in the renderer | 1 | NPC pod | R1-5, PR #186 squads | - |

### NPC model content wave (NPC pod and artists; starts after R1-4)

Every NPC type of every faction gets a model; none stays on the vanilla biped. The work is split into one ticket per race (model, skins and race overrides) and one per rig clip pack. The packs go in this order, following the section 5c minimum sets:

1. `humanoid` base, then `orc` base.
2. Formation packs for both rigs (rout and order, then brace, pike lowered, bow ready and volley, march).
3. Cavalry (`humanoid` rider and `horse`).
4. LOOSE flavour (war-cry, berserk charge, taunt).
5. The `troll` rig and its set.
6. The weapon-class attack packs, work, social and emote.

Each art ticket links section 5 and must pass the validator in `strictAssets` mode. Sizing comes from pilot #10's numbers. Opus 3 opens these, ordered so the factions in the first battle playtests (Gondor, Mordor orcs, Rohan, Uruk-hai) are complete first.

### Phase R2: Flywheel crowd spike (only if the section 8 gate opens)

| # | Ticket | Days | Agent | Blocked by |
| --- | --- | --- | --- | --- |
| R2-1 | Optional Flywheel visualizer for T1 and T2 humanoids (per-bone instances, atlas, main-hand items), fallback when Flywheel reports no support; bench contender `azure_lod_flw`; meets the 2x exit criterion or is dropped | 2 | sonnet-1 | R1-4, the gate |
| R2-2 | Flywheel armour pieces in T1 and T2, Iris Flywheel Compat smoke playtest | 1.5 | sonnet-2 | R2-1 |

### Phase R3: compatibility polish

| # | Ticket | Days | Agent | Blocked by | Shared files |
| --- | --- | --- | --- | --- | --- |
| R3-1 | Datagen `EpicFightCapabilities`: weapon capability JSON (`type` only) for every weapon item from the class table; manual Epic Fight playtest notes | 1 | sonnet-2 | - (can run any time) | DataGenerators |
| R3-2 | Shader and culling playtest pass (Iris PBR and non-PBR, Entity Culling) with fixes in the renderer base | 1 | sonnet-1 | R1-4 | - |

**Order:** R0 (gate G-R0, Seth) → R1 → content wave, with R3-1 in parallel any time and R3-2 alongside the first race. R2 only if its gate opens.

## Open questions for the maintainer

1. **Reference PC:** what CPU, GPU and RAM does Seth play on? The section 3 targets assume a Ryzen 5 5600 or i5-12400 class CPU with an RTX 3060 or RX 6600 class GPU.
2. **Flywheel phase:** accept that shader users never get the instanced path, even if R2 succeeds?
3. **Half-trolls on the `orc` rig** (scaled up) as proposed in section 5b, or a rig of their own?

## Maintainer decisions (PR #185)

1. **AzureLib is chosen.** The benchmark gate stays, to confirm the LOD settings and catch problems. GeckoLib and vanilla keyframes are fallbacks only if the gate fails badly (section 2c).
2. **Every NPC is animated**: all factions and all discipline levels. Formation clips are in the minimum sets, with the rigs that need them (section 5c), and their sync values are in section 6. Discipline levels follow PR #186: RIGID (Gondor, dwarves, elves, Uruk-hai, Easterlings), ORGANIZED (Rohan, Dale, Rangers, Harad, Morgul), LOOSE (orcs, Dunlendings, hill-men, half-trolls, trolls, ruffians, shirriffs).
3. **Pilot #10** is rescoped to the AzureLib format with our bone and clip names, and it is the benchmark rig (sections 2b and 5a, ticket R0-2).
4. **Defaults accepted:**
   - AzureLib is bundled in our jar.
   - One shared `humanoid` rig covers hobbits and dwarves.
   - Adaptive crowd detail is on by default.
   - AzureLib is bumped at most once per milestone, each time with a benchmark rerun.
   - Epic Fight compatibility is weapon-type data only: no `impact` or `armor_negation` tuning, and the optional armour files are dropped.
