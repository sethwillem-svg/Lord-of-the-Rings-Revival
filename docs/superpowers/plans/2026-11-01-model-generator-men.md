# NPC Model Generator (base men PoC) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `node tools/model-gen/build.mjs man --seeds 1-8` writes 8 varied base men (plus low-detail copies, contact sheet, report) as `.bbmodel` files on the spec biped rig, at orc-pack detail.

**Architecture:** A vendored, compiled copy of the Blockbench Models Toolkit (`bbmt`) supplies the `Model`, `Atlas`, `.bbmodel` writer, seal/buried-face checks and renderer. Our code solves a seeded body from a race table, runs part functions that declare cubes with colour slots and paint styles, box-UV packs every cube into a 128×128 atlas, paints it, and runs QA. The CLI wraps one `buildModel()` call per seed.

**Tech Stack:** Node 20+ ESM (`.mjs`), `pngjs` (only runtime dependency, via the toolkit), `node --test`, the viewer's `tools/anim-viewer/retarget.js` (CommonJS, loaded with `createRequire`).

**Spec:** `specs/core/model-generator.md`

## Global Constraints

- Bones: exactly the biped list in rendering-animation 5b: `root > body > torso > head > head_armor`; `torso > right_arm > right_forearm > right_hand > right_item`, `right_arm > right_arm_armor` (and left); `torso > chest_armor`, `torso > cape`; `body > right_leg > right_shin`, `right_leg > right_leg_armor`, `right_leg > right_boot_armor` (and left).
- Anchor bones `*_armor`, `*_item`, `cape` have no cubes.
- 16 px = 1 block. Model front is −Z; the character's right is −X.
- Height 30 to 34 px. 40 to 80 cubes per model. Low-detail copy ≤ 60% of its model's cubes.
- Texture 128 × 128, one sheet per model, embedded. Flat 3–4 shade ramps, light from above, no noise or dithering.
- Colour slots: `skin`, `hair`, `eye`, `tunic`, `tunic_trim`, `trousers`, `boots`, `belt`, `metal`.
- Byte-identical rebuilds for the same seed.
- Toolkit vendored at commit `749793d8660aa035ab48b22b607fd78d8f0729f1` (MIT). Never a live dependency.
- Everything lives in `tools/model-gen/`; outputs in `src/main/art/blockbench/man/`. No change to `build.gradle`, game code or resources.
- Commits: conventional style, ending with the session's attribution lines.

## Review Focus

- Any integer seed (0, negative, above 2^31) must build a valid man; seeds are hashed, never used as indices.
- Every seed 1..200 must fit the 128×128 atlas (the stockiest body with long hair and a full beard is the worst case); overflow must fail with the seed and the island named.
- Running the CLI from any working directory must write to the same repo paths (paths resolve from the script's location).
- Running without `npm install` must exit with "run npm install in tools/model-gen", not a stack trace.
- Fractional sizes must never reach the UV packer: all cube sizes are whole pixels, so one texel maps to one model pixel.

---

### Task 1: Vendor the toolkit and set up the package

**Files:**
- Create: `tools/model-gen/vendor/bbmt/` (copy of the toolkit's compiled `dist/`, its `LICENSE`, `package.json`), `tools/model-gen/vendor/bbmt/VENDORED.md`
- Create: `tools/model-gen/package.json`, `tools/model-gen/.gitignore`
- Test: `tools/model-gen/test/vendor.test.mjs`

**Interfaces:**
- Produces: `import { Model, Atlas, toBbmodel, serializeBbmodel, auditSeal, findBuriedFaces, contactSheet } from '../vendor/bbmt/dist/index.js'`.

- [ ] **Step 1: Copy the build.** From the reviewed clone (scratchpad `bmt`, already built at `749793d`), copy `dist/`, `LICENSE`, `package.json` into `tools/model-gen/vendor/bbmt/`. Write `VENDORED.md`: source URL, commit, date 2026-11-01, what was reviewed (no install scripts, no network or child-process calls, one runtime dependency `pngjs`; build + 35 smoke checks + 121 unit tests passed), and "update by re-reviewing and re-copying, never via npm".
- [ ] **Step 2: Package files.** `package.json`: `"name": "lotr-model-gen"`, `"private": true`, `"type": "module"`, `"engines": {"node": ">=20"}`, `"dependencies": {"pngjs": "7.0.0"}`, `"scripts": {"test": "node --test test/"}`. `.gitignore`: `node_modules/`, `out/`.
- [ ] **Step 3: Write the failing test** `vendor.test.mjs`, test `toolkit model round-trips through the viewer's rig reader`: build `new Model({name:'t', resolution:[16,16]})`, bones `root` at `[0,0,0]` and `body` at `[0,12,0]` under root, one cube `[-4,0,-2]→[4,12,2]` on `body`; `JSON.parse(serializeBbmodel(model))` passed to `parseRig` (from `tools/anim-viewer/retarget.js` via `createRequire`) gives `bones.length === 2` and `bones.map(b=>b.name)` deep-equals `['root','body']`.
- [ ] **Step 4: Run** `cd tools/model-gen && npm install && npm test`. Expected: PASS (it fails before Step 1 with a module-not-found error).
- [ ] **Step 5: Commit** `feat(model-gen): vendor Blockbench Models Toolkit 749793d`.

### Task 2: Seeded random source, man race table, body solver

**Files:**
- Create: `tools/model-gen/lib/rng.mjs`, `tools/model-gen/races/man.mjs`, `tools/model-gen/lib/body.mjs`
- Test: `tools/model-gen/test/body.test.mjs`

**Interfaces:**
- Produces:
  - `makeRng(seed: number|string) -> { next(): number /*[0,1)*/, int(lo, hi): number /*inclusive*/, pick(arr): any, range([lo, hi]): number /*int*/, fork(label: string): Rng }`. Seed is hashed (FNV-1a of `String(seed)`) into mulberry32. `fork` gives an independent stream per part so adding a part never reshuffles others.
  - `MAN` (race table): integer ranges `legs:[11,13]`, `torso:[11,12]`, `head:[7,8]`, `shoulders:[8,10]`, `depth:[4,5]`, `arm:[11,12]`, `armThick:[3,4]`, `legThick:[3,4]`; colour lists `skin` (6 tones, pale to dark brown), `hair` (8 incl. black, browns, auburn, blond, grey), `eye` (4). Hex strings.
  - `solveBody(race, rng) -> Body` with integer fields `{ height, legs, thigh, shin, torso, head, shoulders, depth, arm, upperArm, forearm, hand, armThick, legThick, colours: {skin, hair, eye} }` and `pivots: Record<boneName, [x,y,z]>` for all 25 spec bones. Layout: feet at y=0; `body` pivot at `[0, legs, 0]`; `torso` at `[0, legs, 0]`; `head` at `[0, legs+torso, 0]`; arms at `[±(shoulders/2 + armThick/2), legs+torso-1, 0]` with right = negative X; legs at `[±legThick/2, legs, 0]`; `thigh = ceil(legs/2)`, `upperArm = ceil(arm/2)`, `hand = 2`, `forearm = arm - upperArm - hand`; anchors share their parent's pivot; `*_item` at the hand's bottom.

- [ ] **Step 1: Write the failing tests** in `body.test.mjs`:
  - `same seed gives the same body`: `solveBody(MAN, makeRng(7))` deep-equals a second call.
  - `heights stay in 30..34 for seeds 1..500` and `height === legs + torso + head`.
  - `every spec bone has a pivot`: keys of `pivots` equal the 25 names from Global Constraints.
  - `right side is negative X`: `pivots.right_arm[0] < 0 && pivots.left_arm[0] > 0`, same for legs.
  - `odd seeds build`: seeds `0, -1, 2**40, 'abc'` all return bodies within the ranges.
  - `forked streams are independent`: `makeRng(1).fork('hair').next() !== makeRng(1).fork('beard').next()`.
- [ ] **Step 2: Run** `npm test`. Expected: FAIL (modules missing).
- [ ] **Step 3: Implement** the three modules per the Interfaces block.
- [ ] **Step 4: Run** `npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(model-gen): seeded body solver and man race table`.

### Task 3: Assembly, UV packing and the painter

**Files:**
- Create: `tools/model-gen/lib/assembly.mjs`, `tools/model-gen/lib/paint.mjs`, `tools/model-gen/palettes/base.json`
- Test: `tools/model-gen/test/paint.test.mjs`

**Interfaces:**
- Consumes: `Body` (Task 2), toolkit `Model`, `Atlas`.
- Produces:
  - `class Assembly { constructor(body); add(spec: PartCube): void; cubes: PartCube[] }` where `PartCube = { name: string, bone: string, from: [x,y,z], to: [x,y,z], slot: SlotName, style?: 'flat'|'cloth'|'leather'|'skin'|'hair'|'metal', detail?: boolean, lodGroup?: string, stamps?: Partial<Record<FaceName, Stamp>> }` and `Stamp = { grid: string[], legend: Record<char, [SlotName, shadeIndex]> }`. `add` throws if a coordinate is not a whole number, the name repeats, or the bone is not a spec bone or is an anchor.
  - `resolvePalette(baseJson, colours: {skin, hair, eye}) -> Record<SlotName, Rgb[4]>`; each slot is a 4-shade ramp (lit, base, shade, dark) built with the toolkit's `ramp`.
  - `packUv(model: Model, atlas: Atlas) -> void`: for each cube, `atlas.alloc(cube.name, 2*(sx+sz), sy+sz)` and set `cube.faces = boxUv(cube, [x, y])`. Overflow error names the seed's cube.
  - `paintModel(model, atlas, partCubes, palette) -> void`: for each face, fill with the slot ramp by face (`up` = lit, sides = base with the bottom row shade, `down` = shade), apply the style's pattern (cloth: every other column one shade darker on alternate rows forming a weave; leather: a 1 px darker stitched line 1 px from the face's top edge; hair: vertical strands, alternating base/shade columns; skin, flat, metal: ramp only; metal adds a lit 1 px highlight on the top row), then draw stamps aligned to the face's top-left.
  - `palettes/base.json`: `{ "tunic": "#6b5a43", "tunic_trim": "#3f3326", "trousers": "#4b4a3e", "boots": "#3a2a1e", "belt": "#5a3b22", "metal": "#9a9a92" }`.

- [ ] **Step 1: Write the failing tests** in `paint.test.mjs` (build a 3-cube assembly by hand on a solved body):
  - `uv windows never overlap and stay inside 128`: after `packUv`, every pair of face rects across cubes is disjoint; all coordinates within `[0,128]`.
  - `top of a side face is lighter than its bottom row` for a `cloth` cube: mean luminance of row 0 > row last on its `north` face.
  - `same input gives byte-identical PNG`: two runs → `atlas.canvas.toPng()` buffers equal.
  - `fractional coordinates are rejected`: `add({..., from:[0,0.5,0]})` throws `/whole pixels/`.
  - `anchors take no cubes`: `add({..., bone:'head_armor'})` throws `/anchor/`.
- [ ] **Step 2: Run** `npm test`. Expected: FAIL.
- [ ] **Step 3: Implement** per the Interfaces block.
- [ ] **Step 4: Run** `npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(model-gen): assembly, box-UV packing and slot painter`.

### Task 4: Parts for base men

**Files:**
- Create: `tools/model-gen/parts/head.mjs`, `tools/model-gen/parts/hair.mjs`, `tools/model-gen/parts/beard.mjs`, `tools/model-gen/parts/clothes.mjs`, `tools/model-gen/parts/index.mjs`
- Test: `tools/model-gen/test/parts.test.mjs`

**Interfaces:**
- Consumes: `Assembly`, `Body`, `Rng.fork`.
- Produces: each part module exports `STYLES: string[]` and `addX(asm, body, rng, style?)`; `parts/index.mjs` exports `addMan(asm, body, rng, overrides?: {head?, nose?, brow?, ears?, hair?, beard?})` which forks `rng` per part (`'head'`, `'nose'`, `'brow'`, `'ears'`, `'hair'`, `'beard'`, `'clothes'`) and calls them in that order. Returns the chosen styles `{head, nose, brow, ears, hair, beard}`.
- Part content (names are cube-name prefixes; all whole-pixel; every limb joint overlaps by 1 px so the seal check passes):
  - **head:** `skull` (shapes `square`, `long` (+1 h), `broad` (+1 w)) on `head`, `slot: skin`, front face stamp with eyes, brows and mouth from 3 brow grids (`straight`, `heavy`, `arched`); `nose` (`small` 1×1×1, `long` 1×2×1, `broad` 2×1×1, `hooked` 1×2×2), `detail: true`; `ear_r`/`ear_l` (`small` 1×2×1, `large` 1×3×2), `detail: true`; `neck` 2–3 px wide, `slot: skin`.
  - **hair:** `bald` (no cubes), `short` (cap 1 px over the skull top and back), `side_part` (cap + 1 px fringe on one side), `long` (cap + back panel down to the shoulders), `shoulder` (cap + back + side panels to the jaw), `tied` (cap + `hair_tail` 2×4×2 behind, `detail: true`). Hair cubes `slot: hair`, `style: hair`, sit 1 px outside the skull so they are never buried.
  - **beard:** `none`, `stubble` (stamp only, darker skin shade on the jaw rows), `moustache` (`beard_m` 4×1×1, `detail: true`), `short` (`beard_jaw` wraps the jaw 2 px deep), `full` (`beard_jaw` + `beard_chin` below the jaw), `long` (`beard_jaw` + `beard_chin` + `beard_tip`, strands `detail: true`, `lodGroup: 'beard'`). Elves and dwarves are out of scope.
  - **clothes:** `tunic` on `torso` (`slot: tunic`, `style: cloth`, trim stamp `tunic_trim` on the bottom 1 px row and the neckline), `tunic_skirt` on `body` hanging 3 px below the waist; `belt` 1 px tall wrapping the waist (`slot: belt`, `style: leather`) and `buckle` 2×1×1 in front (`slot: metal`, `detail: true`); `sleeve_r/l` on `*_arm` (`slot: tunic`), `forearm_r/l` on `*_forearm` (`slot: skin`), `hand_r/l` on `*_hand` (`slot: skin`); `thigh_r/l` on `*_leg` and `shin_r/l` on `*_shin` (`slot: trousers`, `style: cloth`); `boot_r/l` on `*_shin` covering the bottom 4 px plus 1 px toe forward (`slot: boots`, `style: leather`) and `boot_cuff_r/l` (`detail: true`).

- [ ] **Step 1: Write the failing tests** in `parts.test.mjs`:
  - `every style builds`: for each part and each `STYLES` entry, `addX` on a fresh assembly does not throw.
  - `cube count is 40..80` for seeds 1..200 via `addMan`.
  - `limb cubes sit on their bones`: `sleeve_r.bone === 'right_arm'`, `forearm_r.bone === 'right_forearm'`, `shin_l.bone === 'left_shin'`, `skull.bone === 'head'`.
  - `bald has no hair cubes` and `none has no beard cubes`.
  - `overrides win`: `addMan(asm, body, rng, {hair:'long', beard:'full'})` returns `{hair:'long', beard:'full'}`.
- [ ] **Step 2: Run** `npm test`. Expected: FAIL.
- [ ] **Step 3: Implement** the part modules per the content list.
- [ ] **Step 4: Run** `npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(model-gen): head, hair, beard and clothes parts for men`.

### Task 5: buildModel, QA and the low-detail copy

**Files:**
- Create: `tools/model-gen/lib/build.mjs`, `tools/model-gen/lib/qa.mjs`
- Test: `tools/model-gen/test/build.test.mjs`

**Interfaces:**
- Consumes: Tasks 2–4, toolkit `Model`, `Atlas`, `toBbmodel`, `auditSeal`, `findBuriedFaces`.
- Produces:
  - `buildModel(race: 'man', seed, { lod?: boolean, palette?: object }) -> { model: Model, styles, body, qa: QaReport }`. Model name `man_base_<seed>` (`_lod` suffix for LOD), resolution `[128,128]`, all 25 bones created top-down from `body.pivots`. LOD: drop cubes with `detail: true`; cubes sharing a `lodGroup` are replaced by one cube spanning their bounds with the first cube's slot. UV pack, paint, attach `atlas.toTexture('<name>.png')`.
  - `runQa(model, partCubes) -> QaReport = { ok: boolean, problems: string[], seal, occlusion, cubes: number }`. Problems: missing/extra bones vs the 25 names; any cube on an anchor; `auditSeal` with chains `[skull, neck, tunic]`, `[sleeve, forearm, hand]` per side, `[tunic, tunic_skirt]`, `[thigh, shin]` per side, `minDepth: 1` not ok; `findBuriedFaces(model).hiddenCubes` non-empty; full model cubes outside 40..80.
- [ ] **Step 1: Write the failing tests** in `build.test.mjs`:
  - `qa passes for seeds 1..50` (full and LOD).
  - `lod has at most 60% of the cubes` for seeds 1..50.
  - `atlas fits for seeds 1..200` (no throw).
  - `same seed gives byte-identical bbmodel`: `JSON.stringify(toBbmodel(...))` equal across two `buildModel(…, 3)` calls.
  - `qa catches a gap`: build seed 1, move `forearm_r` down 2 px, `runQa` reports a problem matching `/seal/`.
  - `qa catches a buried cube`: add a 1×1×1 cube inside `tunic`, `runQa` reports `/hidden/`.
- [ ] **Step 2: Run** `npm test`. Expected: FAIL.
- [ ] **Step 3: Implement** per the Interfaces block.
- [ ] **Step 4: Run** `npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(model-gen): buildModel with QA and low-detail copies`.

### Task 6: CLI, contact sheet, report and watch mode

**Files:**
- Create: `tools/model-gen/build.mjs`, `tools/model-gen/README.md`
- Test: `tools/model-gen/test/cli.test.mjs`

**Interfaces:**
- Consumes: `buildModel` (Task 5), toolkit `serializeBbmodel`, `contactSheet`.
- Produces: CLI `node tools/model-gen/build.mjs <race> --seeds A-B|A,B,C [--out DIR] [--watch]`.
  - Default out: `<repo>/src/main/art/blockbench/<race>/` (models), `<out>/lod/` (LOD copies), `<out>/preview.png` (contact sheet of all built seeds, front view), `tools/model-gen/out/<race>_report.txt` (per seed: height, styles, cube counts, QA lines). Paths resolve from `import.meta.url`.
  - Exit code 1 and the QA problems printed if any seed fails QA; nothing is written for a failing seed.
  - `--watch`: write to `<repo>/clips/` instead (create it with a `*` `.gitignore` if missing), then rebuild whenever a file under `tools/model-gen/{lib,parts,races,palettes}` changes (re-import with a `?v=<n>` query to bypass the module cache). Print one line per rebuild.
  - Missing `pngjs` (import fails): print `run npm install in tools/model-gen` and exit 1.
- [ ] **Step 1: Write the failing tests** in `cli.test.mjs` (spawn with `process.execPath`, `--out` a temp dir):
  - `writes models, lod copies and a preview` for `man --seeds 1-2`: files `man_base_1.bbmodel`, `man_base_2.bbmodel`, `lod/man_base_1_lod.bbmodel`, `lod/man_base_2_lod.bbmodel`, `preview.png` exist; exit code 0.
  - `two builds are byte-identical`: two temp dirs, all files compare equal.
  - `runs from another working directory`: spawn with `cwd: os.tmpdir()`, same files written.
  - `seed list form works`: `--seeds 4,9` writes exactly seeds 4 and 9.
- [ ] **Step 2: Run** `npm test`. Expected: FAIL.
- [ ] **Step 3: Implement** the CLI and the README (setup, commands from the spec, how to add a part, how a later faction palette plugs in).
- [ ] **Step 4: Run** `npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(model-gen): build CLI with contact sheet, report and watch mode`.

### Task 7: Animation fit, the committed men and the viewer check

**Files:**
- Create: `tools/model-gen/test/animation.test.mjs`
- Create (generated): `src/main/art/blockbench/man/man_base_{1..8}.bbmodel`, `src/main/art/blockbench/man/lod/man_base_{1..8}_lod.bbmodel`, `src/main/art/blockbench/man/preview.png`
- Modify: `src/main/art/blockbench/README.md` (add the `man/` folder: generated by `tools/model-gen`, spec-biped rig, do not hand-edit), `tools/anim-viewer/README.md` (one line: base men from `tools/model-gen` load directly)

**Interfaces:**
- Consumes: `buildModel`, `tools/anim-viewer/{retarget.js, rigs.js}`, `tools/anim-viewer/library/cmu/{82_09,02_05}.bvh`.

- [ ] **Step 1: Write the failing test** `animation.test.mjs`, test `cmu walk and punch fit every committed seed`: for seeds 1..8, `parseRig(toBbmodel(model))`, `detectMap` returns the `spec-biped` map, and `retarget(parseBVH(clip), rig, map, {footPlant:true, inPlace:true, jointLimits:true, footLock:true, wrist:'auto'})` does not throw and gives `qa.footMin >= -1` for both clips.
- [ ] **Step 2: Run** `npm test`. Expected: PASS once Tasks 1–6 are in (if it fails, fix the part geometry or pivots, not the threshold).
- [ ] **Step 3: Pick the 8 committed seeds.** Run `--seeds 1-40`, read the report, and choose 8 seeds that cover slim and stocky, short and tall, bald, long-haired, bearded and clean-shaven. If seeds 1–8 do not cover them, keep the seed numbers 1–8 but use `overrides` from a `races/man.showcase.json` (`{seed: {hair, beard}}`) read by the CLI only when present. Then build them into the default out path.
- [ ] **Step 4: Viewer check.** Serve the repo (`python3 -m http.server`), open `tools/anim-viewer/?model=/src/main/art/blockbench/man/man_base_1.bbmodel` in headless Chromium (Playwright), confirm the rig map reads `spec-biped (auto)`, the race reads Man, **Load race set** and **Load 10 uniques** load with an empty error line, and take screenshots. Look at them and `preview.png`.
- [ ] **Step 5: Update the two READMEs**, run `npm test` (all PASS), run the repo's Python parity (`python3 tools/anim/parity.py <kimodo bvh> src/main/art/blockbench/mordor_orc/mordor_orc.bbmodel` stays 0.0000°; the viewer code is unchanged so this is a guard).
- [ ] **Step 6: Commit** `feat(model-gen): 8 generated base men with animation fit test` and push `core/anim-viewer`.
