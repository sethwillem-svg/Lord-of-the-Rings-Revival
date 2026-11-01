export type Rgba = [number, number, number, number];
/** A rectangle in image space: `[x1, y1, x2, y2]`, half-open (x2/y2 exclusive). */
export type Rect = [number, number, number, number];
export declare const TRANSPARENT: Rgba;
export declare const WHITE: Rgba;
export declare const BLACK: Rgba;
/**
 * A plain RGBA raster.
 *
 * Deliberately not built on a 2D canvas library: the texture sizes here are tiny (16–256
 * px), the operations are pixel-exact, and staying on a `Uint8Array` means a rebuild is
 * bit-identical on every machine. `pngjs` is used only to encode/decode PNG bytes.
 */
export declare class Canvas {
    readonly width: number;
    readonly height: number;
    readonly data: Uint8Array;
    constructor(width: number, height: number, fill?: Rgba);
    index(x: number, y: number): number;
    inBounds(x: number, y: number): boolean;
    get(x: number, y: number): Rgba;
    set(x: number, y: number, color: Rgba): void;
    fill(color: Rgba): void;
    /** Fill a half-open rectangle, clipped to the canvas. */
    rect(x1: number, y1: number, x2: number, y2: number, color: Rgba): void;
    /** Stroke a 1px rectangle outline (drawn inside the bounds). */
    outlineRect(x1: number, y1: number, x2: number, y2: number, color: Rgba): void;
    /** Bresenham line. */
    line(x0: number, y0: number, x1: number, y1: number, color: Rgba): void;
    /** Filled ellipse centred on (cx, cy) with radii (rx, ry). */
    ellipse(cx: number, cy: number, rx: number, ry: number, color: Rgba): void;
    /** Copy another canvas in at (x, y). */
    blit(source: Canvas, x: number, y: number): void;
    clone(): Canvas;
    /** Mirror the left half onto the right (or right onto left) — classic skin symmetry. */
    mirrorHorizontal(from?: 'left' | 'right'): void;
    toPng(): Uint8Array;
    static fromPng(bytes: Uint8Array): Canvas;
    /** Distinct opaque colours, for metrics. */
    distinctColors(): Map<string, number>;
}
//# sourceMappingURL=canvas.d.ts.map