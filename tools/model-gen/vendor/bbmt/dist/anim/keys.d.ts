import { type Vec3 } from '../util/math.js';
import type { AnimationChannel, Interpolation, Keyframe } from '../model/types.js';
/** Build one keyframe. Times and values are rounded like Blockbench does. */
export declare function kf(bone: string, channel: AnimationChannel, time: number, value: Vec3, interpolation?: Interpolation): Keyframe;
export declare const rot: (bone: string, time: number, value: Vec3, interpolation?: Interpolation) => Keyframe;
export declare const rot3: (bone: string, time: number, x: number, y: number, z: number, interpolation?: Interpolation) => Keyframe;
export declare const pos: (bone: string, time: number, value: Vec3, interpolation?: Interpolation) => Keyframe;
export declare const pos3: (bone: string, time: number, x: number, y: number, z: number, interpolation?: Interpolation) => Keyframe;
export declare const scale: (bone: string, time: number, value: Vec3, interpolation?: Interpolation) => Keyframe;
/** Uniform scale keyframe. */
export declare const scaleUniform: (bone: string, time: number, value: number, interpolation?: Interpolation) => Keyframe;
/**
 * Sort keyframes by bone, then channel (rotation, position, scale), then time — the order
 * Blockbench writes and the order that makes a diff readable.
 */
export declare function sortKeys(keys: Keyframe[]): Keyframe[];
/** Drop exact duplicates (same bone/channel/time) keeping the first occurrence. */
export declare function dedupeKeys(keys: Keyframe[]): Keyframe[];
/** Flatten a list of key lists into one clip-ready array. */
export declare function mergeKeys(...groups: Keyframe[][]): Keyframe[];
/** Count keys per bone, for QA and summaries. */
export declare function keyCountByBone(keys: Keyframe[]): Map<string, number>;
/** The set of bones touched by a key list. */
export declare function animatedBones(keys: Keyframe[]): Set<string>;
/** Highest keyframe time in a list (0 when empty). */
export declare function maxTime(keys: Keyframe[]): number;
//# sourceMappingURL=keys.d.ts.map