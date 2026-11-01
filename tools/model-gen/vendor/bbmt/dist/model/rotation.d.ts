import type { Vec3 } from '../util/math.js';
import type { Cube } from './types.js';
import type { CubeOptions } from './cubes.js';
import type { Mat4 } from '../util/mat4.js';
export type Axis = 'x' | 'y' | 'z';
/**
 * Element rotation exists on every cube in a `.bbmodel` and is completely legal — a voxel
 * model simply never uses it. These helpers make it usable, which is what turns a stack of
 * boxes into something with sloped snouts, tilted fins and chamfered edges.
 *
 * The rotation is applied around the cube's own `origin`, in the ZYX order Blockbench uses,
 * and is evaluated by `posedFaceCorners` so bounds, rendering and the geometry checks all
 * agree about where the rotated box actually is.
 */
/** The transform a cube's element rotation applies: `T(origin) · R · T(−origin)`. */
export declare function elementMatrix(cube: {
    rotation: Vec3;
    origin: Vec3;
}): Mat4;
/** Set a cube's rotation, leaving its origin alone (defaults to the cube centre). */
export declare function setRotation(cube: Cube, rotation: Vec3, origin?: Vec3): Cube;
/** Compose a single-axis rotation onto a cube's existing rotation. */
export declare function rotateAround(cube: Cube, axis: Axis, degrees: number, origin?: Vec3): Cube;
/** Clear a cube's rotation (back to pure voxel). */
export declare function clearRotation(cube: Cube): Cube;
/** `CubeOptions` with a rotation, for use with `model.cube({...})`. */
export declare function rotated(options: CubeOptions, rotation: Vec3, origin?: Vec3): CubeOptions;
export type EdgeName = 'top-front' | 'top-back' | 'top-left' | 'top-right' | 'bottom-front' | 'bottom-back' | 'bottom-left' | 'bottom-right' | 'front-left' | 'front-right' | 'back-left' | 'back-right';
export interface BevelEdgesOptions {
    name: string;
    from: Vec3;
    to: Vec3;
    bone: string;
    /** Which edges to bevel. */
    edges: EdgeName[];
    /** How far the bevel reaches along each adjoining face. */
    chamfer?: number;
    /**
     * How deep the bevel strip sits inside the box, as a fraction of `chamfer`.
     * `0.5` (the default) leaves a slim diagonal ridge proud of the corner;
     * `1` buries it completely; `0.25` makes it stand out more.
     */
    inset?: number;
    color?: number;
}
/**
 * Build 45° bevel strips along the edges of a box.
 *
 * A cube model cannot subtract material, so a bevel is built the other way round: a square
 * strip rotated 45° about the edge, sunk into the parent by `inset`, so that only a thin
 * diagonal face pokes out. That is enough to break a hard corner, and it costs one cube per
 * edge instead of a rebuilt silhouette.
 *
 * Buried faces still produce z-fighting inside the parent, so run `geometricHygiene()` after
 * adding bevels — it will hide whichever faces are covered.
 */
export declare function bevelEdges(options: BevelEdgesOptions): CubeOptions[];
/**
 * A rotated slab: the general tool for a slope, a fin, an angled plate.
 *
 * The useful trick is that the buried end is invisible, so a box rotated 15–25° can slope a
 * surface without any subtraction — put the rotation origin at the buried edge and the
 * visible part becomes a wedge.
 */
export declare function slab(options: {
    name: string;
    from: Vec3;
    to: Vec3;
    bone: string;
    /** Rotation in degrees on one axis. */
    axis: Axis;
    degrees?: number;
    /** Pivot for the rotation. Defaults to the slab's centre. */
    origin?: Vec3;
    color?: number;
}): CubeOptions;
//# sourceMappingURL=rotation.d.ts.map