'use strict';
/* Every race as build_npc.js builds it today ({ geo, animations, hits, clips } from buildRace), made once per process and
 * cached in test/.cache/build-<sha1>.json (gitignored). The sha1 covers the contents of every build input: the build code
 * (build_npc.js, npc_slots.js, hinge.js, gecko/, import/ and every other module build_npc.js loads), the weapon grips it
 * reads as it loads (classLength), every source clip file loadSources() reads and the BVH libraries the stances come from; test/build.test.js checks that the build
 * reads nothing else. Any change to them misses the cache and rebuilds (clearing older builds), so test/build.test.js
 * still catches shipped files that were not rebuilt. An unreadable cache file is rebuilt too. The build is plain JSON data
 * (no functions or typed arrays), so the cached copy is the build as written to disk (-0 reads back as 0). */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { buildRace, loadSources } = require('../../build_npc.js');
const { RACES } = require('../../npc_slots.js');

const ROOT = path.join(__dirname, '../..');
const BUILD = require.resolve('../../build_npc.js');
const LIB = path.join(ROOT, 'library');
const GRIPS = path.join(ROOT, '../../addon/src/main/resources/assets/lotr_revival/weapon_grips.json');
const CACHE = path.join(__dirname, '../.cache');

const filesIn = (dir, keep = () => true) => fs.readdirSync(dir).filter(keep).sort().map((f) => path.join(dir, f)).filter((f) => fs.statSync(f).isFile());

/** Every file the build reads: its code, the weapon grips, then the source clips (as loadSources() picks them). */
function inputs() {
  const code = new Set([...['build_npc.js', 'npc_slots.js', 'hinge.js'].map((f) => path.join(ROOT, f)), ...filesIn(path.join(ROOT, 'gecko')), ...filesIn(path.join(ROOT, 'import'))]);
  const seen = new Set();
  const walk = (m) => {   // every module build_npc.js loads, however deep
    if (!m || seen.has(m.filename) || !m.filename.startsWith(ROOT + path.sep)) return;
    seen.add(m.filename); code.add(m.filename);
    m.children.forEach(walk);
  };
  walk(require.cache[BUILD]);
  const clips = [path.join(LIB, 'animationsplus/model.bbmodel'), ...['mobends', 'seriousplayer', 'emotecraft'].flatMap((d) => filesIn(path.join(LIB, d), (f) => f.endsWith('.json')))];
  // the BVH libraries the formation stances are picked from (build_npc.js stanceRaw reads the pool's takes from these)
  clips.push(...['ual', 'kaykit', 'cmu'].flatMap((d) => filesIn(path.join(LIB, d), (f) => f.endsWith('.bvh'))));
  return [...[...code].sort(), GRIPS, ...clips];
}

/** The sha1 of every build input's name and contents. */
function buildKey() {
  const h = crypto.createHash('sha1');
  for (const f of inputs()) h.update(`${path.relative(ROOT, f)}\0`).update(fs.readFileSync(f)).update('\0');
  return h.digest('hex');
}

let src = null;
/** The source clips (loadSources()), loaded once and only when asked for. */
const sources = () => src || (src = loadSources());

function load() {
  const name = `build-${buildKey()}.json`, file = path.join(CACHE, name);
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { /* not built yet, or unreadable: build */ }
  const out = Object.fromEntries(RACES.map((r) => [r, buildRace(r, sources())]));
  fs.mkdirSync(CACHE, { recursive: true });
  for (const f of fs.readdirSync(CACHE)) if (/^build-[0-9a-f]+\.json$/.test(f) && f !== name) fs.rmSync(path.join(CACHE, f), { force: true });   // older builds
  const tmp = `${file}.${process.pid}.tmp`;   // test files run in parallel: write aside, then rename into place
  fs.writeFileSync(tmp, JSON.stringify(out));
  fs.renameSync(tmp, file);
  return JSON.parse(JSON.stringify(out));   // the same values a cache hit gives
}

const built = load();
module.exports = { built, sources, buildKey, inputs };
