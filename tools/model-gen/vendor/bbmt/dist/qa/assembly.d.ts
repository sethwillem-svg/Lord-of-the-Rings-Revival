import type { Vec3 } from '../util/math.js';
import type { Cube, FaceName } from '../model/types.js';
import type { Model } from '../model/model.js';
export interface SealOptions {
    /** Below this many units of shared material a joint is reported as open. */
    minDepth?: number;
}
export interface SealPair {
    a: string;
    b: string;
    /** How far the deeper box reaches into the shallower one, in model units. */
    depth: number;
    sealed: boolean;
    /** The point of deepest interpenetration, in world space. */
    at?: Vec3;
}
export interface SealReport {
    pairs: SealPair[];
    open: SealPair[];
    /** The shallowest sealed joint — the one that will open first when it rotates. */
    weakest: SealPair | null;
    ok: boolean;
}
/**
 * How much material two boxes share, along the direction in which they share the least.
 *
 * Computed over all fifteen candidate axes (three from each box, nine cross products): project
 * both boxes onto each axis, measure the width of the intersection of their shadows, and keep the
 * smallest. It is exactly zero when the boxes touch or miss, and it is the thinnest part of the
 * shared region otherwise — which is the number that decides whether a joint can survive being
 * rotated.
 *
 * An earlier version sampled a grid of points and measured how far the deepest one sat from the
 * target's surface. That reads sensibly and is wrong twice over. It cannot report more than half
 * the smaller box's thinnest side, so a thin fin *properly welded* into a thick body is reported
 * as an open joint forever; and it under-reports any overlap narrower than one sample step, so
 * the number moves when you change the sample count. Both are the kind of fault that makes a
 * check untrustworthy, which is worse than not having one.
 */
export declare function penetrationDepth(a: Cube, b: Cube): {
    depth: number;
    at?: Vec3;
};
/**
 * Audit a set of ordered cube chains — a tail, a neck, a horn, a wing arm — and report the
 * shallowest joint in each.
 *
 * Pass chains in the order the parts run along the limb. Consecutive pairs are the joints; a
 * chain of four cubes therefore checks three joints, which is the right granularity because those
 * are the shares that move against each other.
 */
export declare function auditSeal(model: Model, chains: string[][], options?: SealOptions): SealReport;
export declare function formatSeal(report: SealReport): string;
export interface BuriedFace {
    cube: string;
    face: FaceName;
    /** Fraction of the face's sample grid that lies inside another cube. 1 means fully hidden. */
    coverage: number;
    /** The cube doing the hiding, at the worst sample. */
    blockedBy: string;
}
export interface HiddenCube {
    cube: string;
    /** The cube doing the hiding, at the first buried face found. */
    blockedBy: string;
    /** How many of the cube's faces are buried. */
    faces: number;
}
export interface OcclusionReport {
    /**
     * Cubes with no visible face left. This is the actionable list: a cube here exists, is textured,
     * and cannot be seen from any direction.
     */
    hiddenCubes: HiddenCube[];
    /**
     * Every individual face buried inside another cube — hundreds on any jointed model, because a
     * joint interior is a buried face and so is the inward side of every part welded into a body.
     * Informational; do not gate on it.
     */
    buriedFaces: BuriedFace[];
    /** Faces with some visible area left — a deliberate overlap, or a detail half-hidden by a later
     *  part. */
    partlyBuried: BuriedFace[];
    ok: boolean;
}
/**
 * Find faces whose surface is inside another cube, and the cubes that have no face left.
 *
 * The two answers are deliberately separated. Almost every cube in a jointed model has a buried
 * face — the inward side of a part welded into a body is inside that body, and that is correct.
 * The dragon reports 398 of them and builds clean. What is never correct is a cube with *every*
 * face buried, because then the part was added to be seen and cannot be: that is `hiddenCubes`,
 * and it is the list worth failing a build over.
 */
export declare function findBuriedFaces(model: Model, options?: {
    samples?: number;
    slack?: number;
}): OcclusionReport;
export declare function formatOcclusion(report: OcclusionReport): string;
//# sourceMappingURL=assembly.d.ts.map