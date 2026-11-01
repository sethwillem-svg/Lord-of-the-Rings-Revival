import type { Vec3 } from '../util/math.js';
import type { CubeOptions } from './cubes.js';
import type { Model } from './model.js';
/** Build `count` cubes, flattening away entries a callback returns `null`/`[]` for. */
export declare function series(count: number, fn: (index: number) => CubeOptions | CubeOptions[] | null): CubeOptions[];
export interface MirrorPairOptions {
    /** The cube on the model's left (+X). */
    from: Vec3;
    to: Vec3;
    bone: string;
    /** Base name without the side prefix. */
    name: string;
    /** Bone for the mirrored side, when it differs (e.g. left_arm → right_arm). */
    mirroredBone?: string;
    mirroredName?: string;
}
/** Produce the left/right halves of a symmetric part in one call. */
export declare function mirrorPair(options: MirrorPairOptions): [CubeOptions, CubeOptions];
export interface SpikesOptions {
    count: number;
    /** First spike's near corner. */
    start: Vec3;
    /** Distance between spike starts. */
    step: Vec3;
    /** Spike size (width, height, depth). */
    size: Vec3;
    bone: string;
    /** Name prefix; the index is appended. */
    prefix: string;
    /** Shrink each successive spike by this factor (1 = uniform). */
    taper?: number;
    /** Include both +X and −X rows, mirroring across the centre. */
    bothSides?: boolean;
}
/**
 * A row of small hard parts — teeth, claws, spikes, scutes along the tail. Each one is a
 * separate cube so it keeps a crisp edge instead of melting into the parent.
 */
export declare function spikes(options: SpikesOptions): CubeOptions[];
export interface BoneChainOptions {
    /** Bone names, root first. The first name's parent is `parent`. */
    names: string[];
    /** Pivot for each name; same length as `names`. */
    pivots: Vec3[];
    /** Parent of the first bone; defaults to the model root. */
    parent?: string | null;
    color?: number;
}
/** Declare a straight bone chain (neck, tail, tentacle) and return the names. */
export declare function boneChain(model: Model, options: BoneChainOptions): string[];
export interface SegmentedBoxesOptions {
    bone: string;
    /** Start of the first segment (min corner). */
    start: Vec3;
    /** Number of segments. */
    count: number;
    /** Size of the first segment. */
    size: Vec3;
    /** Per-segment size multiplier (tapering). */
    taper?: number;
    /** Per-segment offset of the next segment's min corner. */
    step: Vec3;
    /** Name prefix. */
    prefix: string;
}
/** A tapering run of boxes sharing one bone or spread across a chain — tails, trunks, necks. */
export declare function segmentedBoxes(options: SegmentedBoxesOptions): CubeOptions[];
/** A thin plate — fins, ridges, fins, sails. */
export declare function plate(options: {
    name: string;
    from: Vec3;
    to: Vec3;
    bone: string;
    thicknessAxis?: 'x' | 'y' | 'z';
    thickness?: number;
}): CubeOptions;
//# sourceMappingURL=shapes.d.ts.map