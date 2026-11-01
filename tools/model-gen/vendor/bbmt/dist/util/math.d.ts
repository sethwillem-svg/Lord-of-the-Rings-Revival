/**
 * Small numeric helpers shared by every layer. Kept dependency-free and pure so the
 * animation samplers stay reproducible across runs (same input → same keyframes).
 */
export type Vec3 = [number, number, number];
export declare const clamp: (value: number, min: number, max: number) => number;
export declare const lerp: (a: number, b: number, t: number) => number;
/** Blockbench serialises floats with limited precision; match that to stay stable. */
export declare const round3: (value: number) => number;
export declare const addVec: (a: Vec3, b: Vec3) => Vec3;
export declare const subVec: (a: Vec3, b: Vec3) => Vec3;
export declare const scaleVec: (a: Vec3, s: number) => Vec3;
export declare const dotVec: (a: Vec3, b: Vec3) => number;
export declare const crossVec: (a: Vec3, b: Vec3) => Vec3;
export declare const lengthVec: (a: Vec3) => number;
export declare const normalizeVec: (a: Vec3) => Vec3;
export declare const midVec: (a: Vec3, b: Vec3) => Vec3;
/** Deterministic 32-bit FNV-1a hash. Used for stable, seedable noise and UV placement. */
export declare function hashString(text: string): number;
/** Value noise with optional horizontal tiling (`periodX`), smoothed with a cubic fade. */
export declare function noise2(x: number, y: number, periodX?: number): number;
/** Fractal (fBm) noise: a few octaves of `noise2` for organic mottling. */
export declare function fbm2(x: number, y: number, octaves?: number, periodX?: number): number;
/** '#rrggbb' → [r, g, b]. */
export declare function hexToRgb(value: string): [number, number, number];
/** [r, g, b] → '#rrggbb'. */
export declare function rgbToHex(rgb: [number, number, number]): string;
/** Blend two colours by `t` (0 → a, 1 → b). */
export declare function mixRgb(a: [number, number, number], b: [number, number, number], t: number): [number, number, number];
/** Shift a colour's brightness by a signed amount in [-1, 1]. */
export declare function shadeRgb(rgb: [number, number, number], amount: number): [number, number, number];
//# sourceMappingURL=math.d.ts.map