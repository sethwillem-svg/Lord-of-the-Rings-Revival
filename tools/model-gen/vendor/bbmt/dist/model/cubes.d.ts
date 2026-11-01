import type { Vec3 } from '../util/math.js';
import type { Cube, FaceName, UvRect } from './types.js';
export interface CubeOptions {
    name?: string;
    from: Vec3;
    to: Vec3;
    bone: string;
    /** Explicit UV windows for specific faces; everything else is decided by the UV policy. */
    faces?: Partial<Record<FaceName, UvRect>>;
    /** Element rotation origin. Defaults to the cube centre. */
    origin?: Vec3;
    rotation?: Vec3;
    /** Grows every side by `inflate` units, exactly like Blockbench's inflate field. */
    inflate?: number;
    color?: number;
    mirrorUv?: boolean;
    texture?: number;
    /** Faces to hide from rendering (interior faces, coincident faces). */
    hidden?: Iterable<FaceName>;
}
/**
 * Build a cube element. `from`/`to` are normalised to min/max corners so a spec can be
 * written either way round — handy for mirrored limbs where the sign of X flips.
 */
export declare function makeCube(options: CubeOptions): Cube;
/** Faces this cube actually renders. */
export declare function visibleFaces(cube: Cube): FaceName[];
/** Hide one face (idempotent). */
export declare function hideFace(cube: Cube, face: FaceName): void;
/** Show a previously hidden face. */
export declare function showFace(cube: Cube, face: FaceName): void;
/** Size of the cube along each axis. */
export declare function cubeSize(cube: Cube): Vec3;
/** Centre of the cube. */
export declare function cubeCenter(cube: Cube): Vec3;
export interface FaceFrame {
    /** Corner that maps to the UV rect's (u1, v1) corner. */
    base: Vec3;
    /** Unit basis for the texture's U axis. */
    uAxis: Vec3;
    /** Unit basis for the texture's V axis (points "down" the texture). */
    vAxis: Vec3;
    width: number;
    height: number;
    center: Vec3;
}
/**
 * Texture-space frame for a face.
 *
 * The convention matters because it is shared by the UV policy and the renderer: a face
 * whose frame says V runs along -Y is shaded and sampled the same way everywhere. Frames
 * are chosen so the texture reads correctly when the face is looked at from outside the box.
 */
export declare function faceFrame(cube: Cube, face: FaceName): FaceFrame;
/** The four corners of a face, ordered to match a UV rect's (u1,v1)→(u2,v2) corners. */
export declare function faceCorners(cube: Cube, face: FaceName): [Vec3, Vec3, Vec3, Vec3];
/**
 * The four corners of a face with the cube's **element rotation** applied.
 *
 * This is the geometry that actually ends up in the model: a non-voxel part is a box with a
 * rotation on it, and its silhouette comes from rotating the corners around the element
 * origin. `faceCorners` gives the unrotated frame (the UV contract); this gives the shape.
 */
export declare function posedFaceCorners(cube: Cube, face: FaceName): [Vec3, Vec3, Vec3, Vec3];
/**
 * Mirror a cube across the X axis. Returns a cube on the opposite side carrying the
 * counterpart name, so `torso` style naming (`left_` / `right_`) stays consistent.
 */
export declare function mirrorCube(cube: Cube, name: string, bone: string, faces?: Partial<Record<FaceName, UvRect>>): Cube;
//# sourceMappingURL=cubes.d.ts.map