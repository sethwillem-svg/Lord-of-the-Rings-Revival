import type { Model } from '../model/model.js';
import type { FaceName } from '../model/types.js';
/**
 * Geometry hygiene.
 *
 * Two faces that occupy the same plane and overlap are the most common reason a model looks
 * broken in motion: the rasteriser has to pick a winner per pixel, tiny numerical differences
 * flip that choice as the view moves, and the two colours shimmer into each other along the
 * shared region. Blockbench exposes alignment/pivot tooling to avoid it by hand; this module
 * finds it and removes it.
 *
 * Two cases are reported separately because the fix differs:
 *
 *  - **interior** — opposite normals on the same plane. Invisible from outside, so hiding
 *    both faces costs nothing and ends the z-fighting.
 *  - **duplicate** — same normal on the same plane. Two pieces of geometry claiming one
 *    surface; one must win deterministically.
 */
export type CoincidenceKind = 'interior' | 'duplicate';
export interface CoincidentPair {
    kind: CoincidenceKind;
    a: {
        cube: string;
        face: FaceName;
    };
    b: {
        cube: string;
        face: FaceName;
    };
    /** Overlapping area, model units². */
    overlap: number;
    /** Overlap as a fraction of the smaller face's area, 0..1. */
    coverage: number;
    /** Distance between the two planes. */
    gap: number;
}
export interface CoincidenceReport {
    pairs: CoincidentPair[];
    /** Cubes with identical bounds and rotation — almost always an accidental duplicate. */
    duplicates: Array<{
        a: string;
        b: string;
    }>;
}
export interface FindOptions {
    /** Plane-distance tolerance, model units. Default 0.02. */
    tolerance?: number;
    /** Ignore overlaps smaller than this, model units². Default 0.001. */
    minOverlap?: number;
}
/** Find every pair of faces that occupy the same plane and overlap. */
export declare function findCoincidentFaces(model: Model, options?: FindOptions): CoincidenceReport;
/** Cubes with identical bounds and rotation. */
export declare function findDuplicateCubes(model: Model): Array<{
    a: string;
    b: string;
}>;
export interface HygieneOptions extends FindOptions {
    /** Hide a face when the overlap covers at least this fraction of it. Default 0.9. */
    hideCoverage?: number;
    /** `hide` removes faces, `separate` nudges geometry apart, `report` only inspects. */
    mode?: 'hide' | 'separate' | 'report';
    /** Nudge distance for `mode: 'separate'`. Default 0.05. */
    epsilon?: number;
}
export interface HygieneResult {
    report: CoincidenceReport;
    resolved: Array<{
        cube: string;
        face: FaceName;
        reason: CoincidenceKind;
    }>;
    hiddenByCube: Record<string, FaceName[]>;
}
/** Visible face area per `cube|face` key, model units². */
export declare function faceSurfaceAreas(model: Model, tolerance?: number): Map<string, number>;
/**
 * Remove coincident faces.
 *
 * Coverage is accumulated **per face**, not per pair. That distinction matters: a small ridge
 * sitting on a wide back is coincident with the back face only where it touches, so hiding the
 * back face because "the overlap covers 100% of the smaller face" would punch a hole straight
 * through the model. A face is hidden only once its neighbours between them cover
 * `hideCoverage` of *its own* area.
 *
 * Interior pairs are invisible from outside, so both faces are candidates. For a duplicate pair
 * the cube that appears first in the model keeps its surface, which makes the surviving surface
 * deterministic across rebuilds.
 *
 * Partial overlaps below `hideCoverage` are left alone and only reported: a partly-covered face
 * is doing real work.
 */
export declare function geometricHygiene(model: Model, options?: HygieneOptions): HygieneResult;
/** Human-readable coincidence report. */
export declare function formatCoincidence(report: CoincidenceReport): string;
//# sourceMappingURL=coincident.d.ts.map