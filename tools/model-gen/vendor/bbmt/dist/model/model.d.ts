import type { Vec3 } from '../util/math.js';
import { Rig, type BoneOptions } from './rig.js';
import { type CubeOptions } from './cubes.js';
import { type AssignedFace, type UvPolicy } from './uv.js';
import type { Bone, Clip, Cube, Texture } from './types.js';
export interface ModelOptions {
    name: string;
    /** Texture resolution in pixels. Defaults to 64×64 like a vanilla entity skin. */
    resolution?: [number, number];
    /** Identifier Blockbench stores for the model (bedrock/geckolib). */
    identifier?: string;
    uvPolicy?: UvPolicy;
}
/**
 * A model under construction: a rig, its cubes, its textures and its animations.
 *
 * Everything else in the toolkit reads or writes a `Model`. It deliberately has no notion
 * of Blockbench: it is a plain in-memory description that the exporters serialise into
 * whatever format is asked for.
 */
export declare class Model {
    readonly name: string;
    readonly rig: Rig;
    readonly cubes: Cube[];
    readonly textures: Texture[];
    readonly clips: Clip[];
    /**
     * Non-fatal problems collected while building (e.g. a motion generator driving a bone
     * this model does not have). Read by build scripts and surfaced by `validate()`.
     */
    readonly warnings: string[];
    resolution: [number, number];
    identifier: string;
    uvPolicy: UvPolicy;
    constructor(options: ModelOptions);
    /** Declare a bone. Parents must already exist — build the rig top-down. */
    bone(name: string, pivot: Vec3, parent?: string | null, extra?: Partial<BoneOptions>): Bone;
    /** Add a cube attached to an existing bone. */
    cube(options: CubeOptions): Cube;
    addTexture(texture: Texture): Texture;
    /**
     * Attach a clip. Keys that target bones this model does not have are dropped rather than
     * written — a motion generator names a conventional rig, and a model is free not to have
     * every bone. The dropped names are recorded on `warnings` so it is never silent.
     */
    addClip(clip: Clip): Clip;
    boneOf(cube: Cube): Bone;
    cubesOf(bone: string): Cube[];
    findCube(name: string): Cube | undefined;
    /** Resolve every face's UV window using the model's policy. Idempotent. */
    assignUv(policy?: UvPolicy): AssignedFace[];
    /** Which rule last claimed each face, after the most recent `assignUv()`. */
    uvTrace: AssignedFace[];
    /** Axis-aligned bounds over every cube, including inflate. */
    bounds(): {
        min: Vec3;
        max: Vec3;
    };
    /** Overall size per axis. */
    size(): Vec3;
    /** Set every cube's `bone` to a different bone. Useful when re-parenting a sub-tree. */
    reassign(fromBone: string, toBone: string): number;
    /** Move every cube in a bone's subtree by `delta`. Returns cubes touched. */
    translateBone(bone: string, delta: Vec3): number;
    summary(): string;
}
//# sourceMappingURL=model.d.ts.map