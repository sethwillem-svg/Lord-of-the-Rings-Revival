'use strict';
/* The race files the game ships (written by build_npc.js), read once for every test file: tests judge what is committed.
 * test/build.test.js checks these files are what the build code makes today. */
const fs = require('node:fs');
const path = require('node:path');
const { RACES } = require('../../npc_slots.js');

const DIR = path.join(__dirname, '../../../../addon/src/main/resources/assets/lotr_revival/geckolib/animations/entity');
const read = (f) => JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8'));
const shipped = Object.fromEntries(RACES.map((r) => [r, { animations: read(`npc_biped_${r}.animation.json`), hits: read(`npc_biped_${r}.hits.json`) }]));
module.exports = { shipped, DIR };
