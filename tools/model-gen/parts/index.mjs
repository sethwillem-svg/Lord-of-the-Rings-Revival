/** A base man: head, hair, beard, clothes. Each part draws from its own random stream. */
import { addHead } from './head.mjs';
import { addHair } from './hair.mjs';
import { addBeard } from './beard.mjs';
import { addClothes } from './clothes.mjs';

export function addMan(asm, body, rng, overrides = {}) {
  const head = addHead(asm, body, rng.fork('head'), overrides);
  const hair = addHair(asm, body, rng.fork('hair'), overrides.hair);
  const beard = addBeard(asm, body, rng.fork('beard'), overrides.beard);
  addClothes(asm, body, rng.fork('clothes'));
  return { ...head, hair, beard };
}
