import type { Canvas, Rect, Rgba } from './canvas.js';
import type { Rgb } from './palette.js';
/** Iterate every pixel of a half-open rect, clipped to the canvas. */
export declare function eachPixel(canvas: Canvas, rect: Rect, fn: (x: number, y: number) => void): void;
/** Flat fill. The workhorse. */
export declare function fill(canvas: Canvas, rect: Rect, color: Rgba): void;
export interface GrainOptions {
    /** Peak ± channel offset. Default 6 — subtle; large values read as noise/dirt. */
    amount?: number;
    /** Noise frequency. Lower = broader mottling. */
    scale?: number;
    /** Seed salt so two fills with the same options differ. */
    seed?: number;
    /** Add occasional darker blotches (natural skin mottling). */
    blotch?: number;
}
/**
 * Fill with very low-amplitude value noise. This is the *only* noise helper, and its
 * default amplitude is deliberately small: heavy noise or a dither pattern is what makes
 * generated Minecraft art look dirty, so the toolkit keeps it faint and off the shading path.
 */
export declare function grain(canvas: Canvas, rect: Rect, color: Rgb, options?: GrainOptions): void;
export interface BandOptions {
    steps?: number;
    contrast?: number;
    bias?: number;
    /** Direction of the light→shadow progression. */
    axis?: 'vertical' | 'horizontal';
    /** Reverse so the light end is at the top/left. */
    inverted?: boolean;
    /** Blend each band toward another colour by this amount. */
    tintToward?: Rgb;
    tintAmount?: number;
}
/**
 * Fill a rect with discrete flat bands sampled from a ramp.
 *
 * This is the idiomatic Minecraft shade: three or so clearly separated values, no blending
 * between them. Band boundaries are snapped to whole pixels so the result stays crisp at
 * texture scale.
 */
export declare function bands(canvas: Canvas, rect: Rect, base: Rgb, options?: BandOptions): void;
export interface BevelOptions {
    /** Brightness of the top/left edge. */
    light?: number;
    /** Brightness of the bottom/right edge. */
    dark?: number;
    /** Thickness of the bevel in pixels. */
    thickness?: number;
    inset?: number;
}
/** Light the top/left edge and darken the bottom/right — a hint of 3D without painting it. */
export declare function bevel(canvas: Canvas, rect: Rect, base: Rgb, options?: BevelOptions): void;
export interface SeamOptions {
    amount?: number;
    /** Which edges to darken. */
    edges?: {
        top?: boolean;
        bottom?: boolean;
        left?: boolean;
        right?: boolean;
    };
    thickness?: number;
}
/**
 * Darken the edges of a face so a cube reads as a cube. Applied per face after the base
 * fill; this is what stops adjacent boxes of the same colour from melting together.
 */
export declare function seam(canvas: Canvas, rect: Rect, base: Rgb, options?: SeamOptions): void;
/** Blend a colour into a pixel by `amount` (1 = replace). */
export declare function blendPixel(canvas: Canvas, x: number, y: number, color: Rgb, amount?: number): void;
export interface EyeOptions {
    sclera?: Rgb;
    iris?: Rgb;
    pupil?: Rgb;
    /** Brow shadow along the top edge. */
    brow?: Rgb;
    /** Shift the pupil one pixel toward the model's left/right. */
    gaze?: 'center' | 'left' | 'right';
}
/**
 * Draw a stylised eye into a small rect: flat sclera, a flat iris, a 2×2 pupil and a brow
 * shadow. Kept to flat colours on purpose — an eye is the one place a gradient would look
 * out of place next to the rest of the model.
 */
export declare function eye(canvas: Canvas, rect: Rect, options?: EyeOptions): void;
export interface BlotchOptions {
    chance?: number;
    scale?: number;
    color: Rgb;
    seed?: number;
    /** Only paint where the mask returns true. */
    mask?: (x: number, y: number) => boolean;
}
/** Scatter flat blotches using low-frequency noise — natural mottling, not random pixels. */
export declare function blotch(canvas: Canvas, rect: Rect, options: BlotchOptions): void;
/** 1px outline around a rect, drawn inside. */
export declare function outline(canvas: Canvas, rect: Rect, color: Rgba, thickness?: number): void;
export interface RadialOptions {
    inner: Rgb;
    outer: Rgb;
    steps?: number;
    power?: number;
}
/**
 * Radial shade, quantised into flat rings by default. A true smooth gradient is available
 * with `steps: 0`, but the ringed default keeps the art on-style.
 */
export declare function radial(canvas: Canvas, rect: Rect, options: RadialOptions): void;
/**
 * Ordered dither between two colours. Present for completeness, and deliberately not used
 * by any default: dithering a Minecraft texture is the fastest way to make it look noisy.
 */
export declare function dither(canvas: Canvas, rect: Rect, a: Rgb, b: Rgb, matrix?: number[][]): void;
export declare const BAYER_2: number[][];
/** Deterministic scatter helper shared by detail brushes. */
export declare function scatterHash(seed: number, x: number, y: number): number;
//# sourceMappingURL=brush.d.ts.map