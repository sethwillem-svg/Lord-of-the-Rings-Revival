# NPC model generator

Builds NPC Blockbench models from code: a race table picks a body by seed, parts add named cubes to the
spec biped bones (`specs/core/rendering-animation.md` 5b) and paint them in colour slots, and checks fail
the build on open joints, hidden cubes or wrong bones. Spec: `specs/core/model-generator.md`.
Proof of concept: **base men** (no gear).

## Setup (once)

```sh
cd tools/model-gen && npm install && cd ../..
```

Node 20 or newer. The only package is `pngjs`; the model toolkit is vendored in `vendor/bbmt/` (see its
`VENDORED.md`).

## Commands

```sh
node tools/model-gen/build.mjs man --seeds 1-8            # src/main/art/blockbench/man/ (+ lod/, preview.png)
node tools/model-gen/build.mjs man --seeds 4,9 --out /tmp/men
node tools/model-gen/build.mjs man --seeds 1-8 --watch    # rebuilds into clips/ on every save
cd tools/model-gen && npm test                            # all checks
```

With `--watch`, open the viewer on a generated man (`animview man_base_1`, or the watch folder): every save
under `lib/`, `parts/`, `races/` or `palettes/` rebuilds the models in `clips/` and the viewer reloads them.

The report (`tools/model-gen/out/man_report.txt`) lists each seed's height, build, part styles and cube counts.
`races/man.showcase.json` (optional) fixes part styles for given seeds, for the committed set.

## How it fits together

| File | Does |
| --- | --- |
| `races/man.mjs` | Body ranges (whole pixels) and skin, hair and eye colours |
| `lib/body.mjs` | Seed -> body sizes and the pivot of every spec bone (front -Z, character's right -X) |
| `parts/*.mjs` | Head (shape, face stamp, nose, ears, chin), hair, beard, clothes; each adds cubes with a slot and style |
| `lib/paint.mjs` | Box-UV packing (one texel per pixel) and painting from 4-shade slot ramps, patterns and stamps |
| `lib/build.mjs` | One model: body, parts, low-detail copy, UVs, paint, checks |
| `lib/qa.mjs` | Spec bones, empty anchors, sealed joints (1 px shared), no hidden cubes, 40-80 cubes |
| `lib/bbmodel.mjs` | Writes the Blockbench 4.10 layout the viewer and orc packs use |
| `palettes/base.json` | Clothes colours. A faction palette (later) is another file with the same slots |

**Adding a part:** export `STYLES` and `addX(asm, body, rng, style)` from a file in `parts/`, add cubes with
`asm.add({ name, bone, from, to, slot, style, detail, lodGroup, stamps })`, call it from `parts/index.mjs`
with its own `rng.fork('x')` (so other parts keep their picks). Sizes must be whole pixels; positions may be
half pixels. Mark small pieces `detail: true` so the low-detail copy drops them. Run `npm test`: the seal and
hidden-cube checks tell you which cube to move.

**Later:** elves and dwarves are new race tables; faction looks are palettes plus faction clothing parts.
