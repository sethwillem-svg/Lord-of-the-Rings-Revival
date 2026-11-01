import type { Vec3 } from './math.js';
/**
 * Column-major 4×4 matrices, indexed `m[column * 4 + row]` (the three.js convention).
 *
 * Lives in `util` rather than in the renderer because the model layer needs it too: a cube's
 * element rotation has to be evaluated when computing its corners (for bounds, for rendering,
 * and for the coincidence check). One implementation means a rotated cube's geometry is
 * described identically everywhere.
 */
export type Mat4 = Float64Array;
export declare function identity(): Mat4;
export declare function multiply(a: Mat4, b: Mat4): Mat4;
export declare function translation(x: number, y: number, z: number): Mat4;
export declare function scaleMatrix(x: number, y: number, z: number): Mat4;
export declare function rotationX(degrees: number): Mat4;
export declare function rotationY(degrees: number): Mat4;
export declare function rotationZ(degrees: number): Mat4;
/**
 * Euler → matrix using the ZYX order Blockbench applies to group and element rotations, so a
 * rotation written in a script previews the way Blockbench would show it.
 */
export declare function rotationZYX(rotation: Vec3): Mat4;
export declare function transformPoint(m: Mat4, point: Vec3): Vec3;
/** Apply only the linear part — used for normals. */
export declare function transformDirection(m: Mat4, direction: Vec3): Vec3;
/** `T(origin) · R · S · T(−origin)`: rotate and scale around a point. */
export declare function aroundOrigin(rotation: Vec3, origin: Vec3, scale?: Vec3): Mat4;
/** Swap the 3×3 linear block's rows and columns, leaving translation alone. */
export declare function transposeLinear(m: Mat4): Mat4;
/**
 * The inverse of `aroundOrigin(rotation, origin)`.
 *
 * Not `aroundOrigin` with the angles negated: `rotationZYX` composes as `Rz·Ry·Rx`, so its
 * inverse is `Rxᵀ·Ryᵀ·Rzᵀ` and negating the angles rebuilds them in the wrong order. Transposing
 * the linear block is correct for any composition order, which matters because the QA checks use
 * this to map a world point back into a rotated cube's own frame.
 */
export declare function invertAround(rotation: Vec3, origin: Vec3): Mat4;
//# sourceMappingURL=mat4.d.ts.map