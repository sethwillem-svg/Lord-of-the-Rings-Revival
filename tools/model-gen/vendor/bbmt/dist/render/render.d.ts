import type { Model } from '../model/model.js';
import { Canvas, type Rgba } from '../texture/canvas.js';
import { type RenderOptions } from './projector.js';
export interface SheetOptions extends RenderOptions {
    /** Views to include, left to right. Defaults to the four orthogonal sides plus iso. */
    views?: string[];
    /** Gap between tiles in pixels. */
    gap?: number;
    /** Draw a label bar under each tile. */
    background?: Rgba;
    tileBackground?: Rgba;
}
/**
 * Render a contact sheet: one tile per named view, laid out left-to-right. This is the
 * image an agent looks at to check a model, so each tile is rendered from the same world
 * lighting and fitted to its own frame.
 */
export declare function contactSheet(model: Model, options?: SheetOptions): Canvas;
export interface StripOptions extends RenderOptions {
    /** Clip name to render. */
    clip: string;
    /** Discrete times (seconds) to sample. */
    times: number[];
    view?: string;
    gap?: number;
    tileBackground?: Rgba;
}
/**
 * Render one clip as a horizontal strip of poses.
 *
 * Each tile is rendered at a sampled time from the actual skeleton, so the strip shows the
 * motion rather than the rest pose. All tiles share one frame — the union of every sampled
 * pose and the rest pose — so the model does not appear to change size as the animation plays,
 * and a foot sinking through the floor stays visible instead of being refitted away.
 */
export declare function clipStrip(model: Model, options: StripOptions): Canvas;
/** Render one view and write it as a PNG. */
export declare function renderToFile(model: Model, view: string, file: string, options?: RenderOptions): string;
//# sourceMappingURL=render.d.ts.map