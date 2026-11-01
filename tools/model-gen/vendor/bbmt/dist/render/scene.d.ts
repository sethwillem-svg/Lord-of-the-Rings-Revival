import type { Model } from '../model/model.js';
import type { Clip, FaceName, Texture, UvRect, Vec3 } from '../model/types.js';
import { aroundOrigin, identity, multiply, rotationX, rotationY, rotationZ, rotationZYX, scaleMatrix, transformDirection, transformPoint, translation, type Mat4 } from '../util/mat4.js';
export { identity, multiply, translation, scaleMatrix, rotationX, rotationY, rotationZ, rotationZYX, transformPoint, transformDirection, aroundOrigin, type Mat4, };
export interface BonePose {
    rotation: Vec3;
    position: Vec3;
    scale: Vec3;
}
export type Pose = Map<string, BonePose>;
/** Sample a clip at `time`, interpolating each channel between its keys. */
export declare function samplePose(clip: Clip, time: number): Pose;
/**
 * Fold the rig hierarchy into one world matrix per bone.
 *
 * A bone's world transform is `parent · T(pivot) · R · S · T(-pivot)`: rotate and scale around
 * the bone's own pivot, then inherit the parent. This is exactly the transform Blockbench
 * uses, which is why a previewed pose matches the editor.
 */
export declare function boneWorldTransforms(model: Model, pose: Pose): Map<string, Mat4>;
export interface SceneFace {
    /** Four corners, ordered to match the (u1,v1)→(u2,v2) corners of `uv`. */
    corners: [Vec3, Vec3, Vec3, Vec3];
    normal: Vec3;
    uv: UvRect | null;
    /** The cube this face came from, for diagnostics. */
    cube: string;
    face: FaceName;
}
export interface Scene {
    faces: SceneFace[];
    texture: Texture | null;
    bounds: {
        min: Vec3;
        max: Vec3;
    };
    /** True when the faces were produced by a posed rig. */
    posed: boolean;
}
/**
 * Flatten a model (optionally posed) into world-space faces.
 *
 * Two transforms are composed per face: the cube's **element** rotation around its own origin
 * (which is how a non-voxel model gets its angles), then its bone's world matrix. Baking both
 * into the corners keeps the rasteriser and the coincidence check completely ignorant of the
 * rig and of element rotation.
 */
export declare function buildScene(model: Model, pose?: Pose): Scene;
/** Rest-pose scene (no animation applied). */
export declare function restScene(model: Model): Scene;
/** Scene at a given time inside a clip. */
export declare function posedScene(model: Model, clip: Clip, time: number): Scene;
//# sourceMappingURL=scene.d.ts.map