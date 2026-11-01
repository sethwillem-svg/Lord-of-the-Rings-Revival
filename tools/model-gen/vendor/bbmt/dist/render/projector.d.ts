import type { Model } from '../model/model.js';
import type { Vec3 } from '../model/types.js';
import { Canvas, type Rgba } from '../texture/canvas.js';
import { type Scene } from './scene.js';
export interface ViewBasis {
    /** Unit vector the camera looks along (into the scene). */
    forward: Vec3;
    /** Screen-up axis. */
    up: Vec3;
    /** Screen-right axis (derived). */
    right: Vec3;
}
/** The named orthographic views plus two three-quarter views. */
export declare const VIEWS: Record<string, ViewBasis>;
export declare function viewBasis(name: string): ViewBasis;
export interface RenderOptions {
    width?: number;
    height?: number;
    /** Pixel padding inside the frame. */
    padding?: number;
    background?: Rgba;
    /** World light direction; faces pointing at it render brighter. */
    light?: Vec3;
    /** Ambient floor, 0..1. */
    ambient?: number;
    /** Darken the outline that touches the background. */
    outline?: boolean;
    /** Force a px-per-unit scale instead of fitting the frame. */
    scale?: number;
    /** Nearest-neighbour sampling (authentic) or bilinear. */
    filter?: 'nearest' | 'linear';
    /**
     * Fit the frame to these bounds instead of the scene's own. A strip of poses rendered from
     * one clip must share a frame, otherwise the model appears to change size as it animates
     * and a foot sinking through the floor is invisible.
     */
    frame?: {
        min: Vec3;
        max: Vec3;
    };
}
/**
 * Rasterise a scene from one orthographic view.
 *
 * Uses a per-pixel depth buffer instead of painter's-algorithm sorting, because posed rigs
 * interpenetrate freely and a centroid sort draws the wrong box on top the moment two
 * overlap. Each visible face is split into two triangles; a pixel keeps its interpolated
 * depth (depth-tested) and interpolated UV (sampled from the atlas). Lighting is a fixed
 * world light, so a surface is the same brightness in every view — that is what makes the
 * contact sheets usable for spotting geometry and UV problems.
 */
export declare function projectScene(scene: Scene, view: ViewBasis, options?: RenderOptions): Canvas;
/** Render a model in its rest pose. */
export declare function projectModel(model: Model, view: ViewBasis, options?: RenderOptions): Canvas;
//# sourceMappingURL=projector.d.ts.map