import type { Vec3 } from '../util/math.js';
import type { Bone } from './types.js';
export interface BoneOptions {
    pivot: Vec3;
    parent?: string | null;
    rotation?: Vec3;
    color?: number;
    visibility?: boolean;
}
/**
 * A bone (Blockbench group) hierarchy.
 *
 * Bones own a pivot and a parent; cubes attach to bones. This is the only structure that
 * makes a model animatable, so every rig is built by declaring parents explicitly and the
 * class refuses cycles and duplicate names instead of silently producing a broken outliner.
 */
export declare class Rig {
    private readonly bones;
    add(name: string, options: BoneOptions): Bone;
    /** Add only if absent; returns the existing bone otherwise. Handy for idempotent specs. */
    ensure(name: string, options: BoneOptions): Bone;
    get(name: string): Bone;
    has(name: string): boolean;
    get size(): number;
    get names(): string[];
    all(): Bone[];
    childrenOf(name: string): Bone[];
    root(): Bone | undefined;
    /** Bones ordered so every parent precedes its children (insertion order as tiebreak). */
    ordered(): Bone[];
    /** Chain from the root down to `name`, inclusive. */
    ancestry(name: string): Bone[];
    /** `name` plus every descendant, in parent-before-child order. */
    subtree(name: string): Bone[];
    /** Depth of a bone from the root (root = 0). */
    depth(name: string): number;
}
//# sourceMappingURL=rig.d.ts.map