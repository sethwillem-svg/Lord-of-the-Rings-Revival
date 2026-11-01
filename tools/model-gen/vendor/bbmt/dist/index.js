/**
 * @trex/aimodel — build Minecraft models, textures, UV atlases and animations by script.
 *
 * Typical flow:
 *
 * ```ts
 * import { Model, Atlas, bandFill, defaultMotionRig, locomotion, clip, validate,
 *          writeBbmodel, writeJavaModel, contactSheet, metrics } from '@trex/aimodel';
 *
 * const model = new Model({ name: 'crate', resolution: [32, 32] });
 * model.bone('root', [0, 0, 0]);
 * model.bone('lid', [0, 12, 0], 'root');
 * model.cube({ name: 'body', from: [-8, 0, -8], to: [8, 12, 8], bone: 'root' });
 * ...
 * ```
 *
 * Everything is in-memory until an exporter writes it to disk, so a script can build,
 * validate, render and export in any order.
 */
export { auditSeal, findBuriedFaces, formatOcclusion, formatSeal, penetrationDepth, } from './qa/assembly.js';
export { Model } from './model/model.js';
export { Rig } from './model/rig.js';
export { makeCube, faceFrame, faceCorners, cubeSize, cubeCenter, mirrorCube, visibleFaces, posedFaceCorners, } from './model/cubes.js';
export { assignUv, skinFieldPolicy, boxUv, } from './model/uv.js';
export { series, mirrorPair, spikes, boneChain, segmentedBoxes, plate, } from './model/shapes.js';
export { setRotation, rotateAround, clearRotation, elementMatrix, bevelEdges, slab, } from './model/rotation.js';
export { findCoincidentFaces, findDuplicateCubes, geometricHygiene, formatCoincidence, } from './qa/coincident.js';
export { FACE_NAMES, FACE_NORMALS, } from './model/types.js';
export { Canvas, TRANSPARENT, WHITE, BLACK } from './texture/canvas.js';
export { harmonize, deriveShade, bridgeColors, softenSeams, resolveStyle, rgbToHsl, hslToRgb, rgbaOf, colourUtils, SURFACE_STYLES, } from './texture/harmony.js';
export { Atlas, islandRules } from './texture/atlas.js';
export { Palette, ramp, rgba, tint } from './texture/palette.js';
export { fill, grain, bands, bevel, seam, outline, radial, eye, blotch, dither, blendPixel, eachPixel, BAYER_2, } from './texture/brush.js';
export { kf, rot, rot3, pos, pos3, scale, scaleUniform, sortKeys, dedupeKeys, mergeKeys, keyCountByBone, animatedBones, maxTime, } from './anim/keys.js';
export { cycle, cycleBones, chain, sin, cos, saw, triangle, bump, pulse, ease, rampUp, decay, frac, wrap01, TAU, } from './anim/curves.js';
export { locomotion, tailWave, breathe, headTurn, idleSway, defaultMotionRig, inferMotionRig, } from './anim/motion.js';
export { clip, clipAuto, pose } from './anim/clip.js';
export * from './export/index.js';
export * from './render/index.js';
export { resolveSpec, } from './spec.js';
export { validate, assertValid, formatReport, } from './qa/validate.js';
export { clamp, lerp, round3, hashString, noise2, fbm2, hexToRgb, rgbToHex, mixRgb, shadeRgb } from './util/math.js';
export { uuidFor, UuidSpace } from './util/uuid.js';
//# sourceMappingURL=index.js.map