#!/usr/bin/env node
/**
 * Build generated NPC models.
 *
 *   node tools/model-gen/build.mjs man                      the race's showcase seeds into src/main/art/blockbench/man/
 *   node tools/model-gen/build.mjs man --seeds 1-8
 *   node tools/model-gen/build.mjs man --seeds 4,9 --out DIR
 *   node tools/model-gen/build.mjs man --palette rohan       faction colours into src/main/art/blockbench/man/rohan/
 *   node tools/model-gen/build.mjs man --palette all         every faction palette, one folder each
 *   node tools/model-gen/build.mjs man --seeds 1-8 --watch  rebuild into <repo>/clips/ on every save (viewer watches it)
 *
 * Writes <out>/<race>_base_<seed>.bbmodel, <out>/lod/<race>_base_<seed>_lod.bbmodel, <out>/preview.png and
 * tools/model-gen/out/<race>_report.txt (the preview uses the toolkit's 'back' views: our models face -Z). Exits 1 if any model fails its checks (nothing is written for it).
 */
import { mkdirSync, writeFileSync, existsSync, watch } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '../..');
const fail = (msg) => { process.stderr.write(msg + '\n'); process.exit(1); };

function parseArgs(argv) {
  const args = { race: argv[0], seeds: null, out: null, watch: false, palette: 'base' };
  for (let i = 1; i < argv.length; i++) {
    if (argv[i] === '--seeds') args.seeds = argv[++i];
    else if (argv[i] === '--out') args.out = argv[++i];
    else if (argv[i] === '--watch') args.watch = true;
    else if (argv[i] === '--palette') args.palette = argv[++i];
    else fail(`unknown argument ${argv[i]}`);
  }
  if (!args.race || args.race.startsWith('-')) fail('usage: build.mjs <race> --seeds A-B|A,B,C [--out DIR] [--watch]');
  const s = args.seeds || 'showcase';
  let seeds;
  if (s === 'showcase') seeds = null;
  else if (/^\d+-\d+$/.test(s)) { const [a, b] = s.split('-').map(Number); seeds = Array.from({ length: Math.max(0, b - a + 1) }, (_, k) => a + k); }
  else if (/^\d+(,\d+)*$/.test(s)) seeds = s.split(',').map(Number);
  else fail(`bad --seeds "${s}": use a range like 1-8 or a list like 4,9`);
  if (seeds && !seeds.length) fail(`--seeds "${s}" is empty`);
  args.seedList = seeds;
  return args;
}

async function loadLib() {
  let lib;
  try {
    lib = {
      ...(await import('./lib/build.mjs')),
      ...(await import('./lib/bbmodel.mjs')),
      ...(await import('./vendor/bbmt/dist/index.js')),
    };
  } catch (e) {
    if (/pngjs/.test(String(e && e.message))) fail('run npm install in tools/model-gen (the pngjs package is missing)');
    throw e;
  }
  return lib;
}

/** Build every requested palette: 'base' into the race folder, a faction into <race folder>/<faction>/. */
async function build(args) {
  const lib = await loadLib();
  if (args.palette !== 'all' && !lib.PALETTES.includes(args.palette)) {
    fail(`unknown palette "${args.palette}" (known: ${lib.PALETTES.join(', ')}, or all)`);
  }
  const palettes = args.palette === 'all' ? lib.PALETTES.filter((p) => p !== 'base') : [args.palette];
  const root = path.resolve(args.out || path.join(REPO, 'src/main/art/blockbench', args.race));
  let failed = 0;
  for (const palette of palettes) {
    const sub = args.palette === 'all' || (!args.out && palette !== 'base') ? palette : '';
    failed += await buildSet(lib, args, palette, path.join(root, sub));
  }
  return failed;
}

async function buildSet(lib, args, palette, out) {
  if (!args.seedList) args.seedList = lib.showcaseSeeds(args.race);
  const report = [], tiles = [];
  let failed = 0;
  for (const seed of args.seedList) {
    let full, lod;
    try {
      full = lib.buildModel(args.race, seed, { palette });
      lod = lib.buildModel(args.race, seed, { palette, lod: true });
    } catch (e) { fail(e.message); }
    const problems = [...full.qa.problems, ...lod.qa.problems.map((p) => `lod: ${p}`)];
    const st = full.styles;
    report.push(`${full.model.name}: ${full.body.height} px, build ${full.body.build.toFixed(2)}, head ${st.head}, nose ${st.nose}, ` +
      `brow ${st.brow}, ears ${st.ears}, hair ${st.hair}, beard ${st.beard}; ${full.qa.cubes} cubes, lod ${lod.qa.cubes}` +
      (problems.length ? `\n  FAILED: ${problems.join('\n  FAILED: ')}` : ''));
    if (problems.length) { failed++; continue; }
    mkdirSync(path.join(out, 'lod'), { recursive: true });
    writeFileSync(path.join(out, `${full.model.name}.bbmodel`), lib.serializeLegacy(full.model));
    writeFileSync(path.join(out, 'lod', `${lod.model.name}.bbmodel`), lib.serializeLegacy(lod.model));
    tiles.push(lib.contactSheet(full.model, { views: ['back', 'iso-back'], width: 160, height: 220 }));
  }
  if (tiles.length) {
    const W = tiles.reduce((s, t) => s + t.width, 0), H = Math.max(...tiles.map((t) => t.height));
    const sheet = new lib.Canvas(W, H, [40, 36, 32, 255]);
    let x = 0;
    for (const t of tiles) { sheet.blit(t, x, 0); x += t.width; }
    writeFileSync(path.join(out, 'preview.png'), sheet.toPng());
  }
  mkdirSync(path.join(HERE, 'out'), { recursive: true });
  writeFileSync(path.join(HERE, 'out', `${args.race}_${palette}_report.txt`), report.join('\n') + '\n');
  process.stdout.write(report.join('\n') + `\n${tiles.length} built into ${out}${failed ? `, ${failed} failed` : ''}\n`);
  return failed;
}

const args = parseArgs(process.argv.slice(2));
if (!args.watch) {
  process.exit((await build(args)) ? 1 : 0);
} else {
  const clips = path.join(REPO, 'clips');
  mkdirSync(clips, { recursive: true });
  if (!existsSync(path.join(clips, '.gitignore'))) writeFileSync(path.join(clips, '.gitignore'), '*\n');
  const child = ['--seeds', args.seeds || 'showcase', '--palette', args.palette, '--out', args.out || clips];
  const rebuild = () => {
    const r = spawnSync(process.execPath, [fileURLToPath(import.meta.url), args.race, ...child], { encoding: 'utf8' });
    const last = (r.stdout || '').trim().split('\n').pop();
    process.stdout.write(`[${new Date().toLocaleTimeString()}] ${r.status === 0 ? last : 'FAILED\n' + (r.stderr || r.stdout)}\n`);
  };
  rebuild();
  let timer = null;
  for (const dir of ['lib', 'parts', 'races', 'palettes']) {
    watch(path.join(HERE, dir), { recursive: true }, () => { clearTimeout(timer); timer = setTimeout(rebuild, 200); });
  }
  process.stdout.write(`watching tools/model-gen for changes; writing to ${args.out || clips} (Ctrl+C to stop)\n`);
}
