import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
export const REPO = path.resolve(here, '../../..');
const require = createRequire(import.meta.url);
/** The viewer's retargeter and rig maps (CommonJS UMD modules). */
export const retarget = require(path.join(REPO, 'tools/anim-viewer/retarget.js'));
export const RIG_MAPS = require(path.join(REPO, 'tools/anim-viewer/rigs.js'));
