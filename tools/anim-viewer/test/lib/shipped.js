'use strict';
/* The race files the game ships (written by build_npc.js: animations, hits.json, draw.json), read once for every test file: tests judge what is committed.
 * test/build.test.js checks these files are what the build code makes today. */
const fs = require('node:fs');
const path = require('node:path');
const { RACES } = require('../../npc_slots.js');

const DIR = path.join(__dirname, '../../../../addon/src/main/resources/assets/lotr_revival/geckolib/animations/entity');
const read = (f) => JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8'));
/** A file that may not be there yet (draw.json, the bow clips' draw timing): null without it, so the tests can say so. */
const maybe = (f) => (fs.existsSync(path.join(DIR, f)) ? read(f) : null);
const shipped = Object.fromEntries(RACES.map((r) => [r, { animations: read(`npc_biped_${r}.animation.json`), hits: read(`npc_biped_${r}.hits.json`), draw: maybe(`npc_biped_${r}.draw.json`) }]));
module.exports = { shipped, DIR };
