import type { Rgba } from './canvas.js';
export type Rgb = [number, number, number];
export interface RampOptions {
    /** Number of discrete steps from darkest to lightest. 3 is the sweet spot for a face. */
    steps?: number;
    /** How far apart the darkest and lightest step sit, 0..1. */
    contrast?: number;
    /** Percentage of the ramp that sits at or below the base colour. */
    bias?: number;
}
/**
 * Build a discrete light→shadow ramp around a base colour.
 *
 * Minecraft art reads well when shading is made of a small number of flat, clearly
 * separated colour steps — not a smooth gradient and definitely not a dither pattern.
 * Everything in this toolkit that "shades" does it by picking from a ramp like this.
 */
export declare function ramp(base: Rgb | string, options?: RampOptions): Rgb[];
/** Wrap an RGB triple as an opaque RGBA. */
export declare const rgba: (rgb: Rgb, alpha?: number) => Rgba;
export interface PaletteEntry {
    name: string;
    base: Rgb;
    steps: Rgb[];
}
/**
 * Named palette. A texture's colours should be a deliberate list, not whatever the noise
 * produced; registering them here gives the metrics something to count and gives the
 * painter named handles (`palette.dark('skin')`) instead of magic RGB tuples.
 */
export declare class Palette {
    private readonly entries;
    define(name: string, color: Rgb | string, options?: RampOptions): PaletteEntry;
    get(name: string): PaletteEntry;
    has(name: string): boolean;
    /** Step index 0 = darkest, last = lightest. Index is clamped, so loops are safe. */
    shade(name: string, index: number): Rgb;
    dark(name: string): Rgba;
    baseRgba(name: string): Rgba;
    light(name: string): Rgba;
    /** Every distinct step across every entry — what the palette-size metric counts. */
    allSteps(): Rgb[];
    size(): number;
    names(): string[];
}
/** Blend a ramp step toward another colour, e.g. tinting scales toward a darker back. */
export declare function tint(step: Rgb, toward: Rgb, amount: number): Rgb;
//# sourceMappingURL=palette.d.ts.map