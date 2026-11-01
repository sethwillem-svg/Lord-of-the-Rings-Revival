import type { Rect } from './canvas.js';
import { Canvas } from './canvas.js';
import type { FaceName, Texture, UvRect } from '../model/types.js';
import type { Cube } from '../model/types.js';
import type { UvRule } from '../model/uv.js';
/**
 * A texture atlas: one image, divided into named islands.
 *
 * Islands are how you keep materials apart — teeth, claws, eyes, mouth and the main skin
 * each get their own patch, and the UV policy points faces at the right one. Two ways to
 * claim an island: `define()` for a fixed rectangle you want to control, `alloc()` for a
 * shelf-packed patch when you don't care where it lands.
 */
export declare class Atlas {
    readonly canvas: Canvas;
    private readonly islands;
    private cursorX;
    private cursorY;
    private shelfHeight;
    constructor(width: number, height: number, fill?: [number, number, number, number]);
    get width(): number;
    get height(): number;
    /** Claim a fixed rectangle. Throws on overlap so a layout mistake surfaces at build time. */
    define(name: string, rect: UvRect): Rect;
    /** Shelf-pack a patch of `w`×`h` starting from the current cursor. */
    alloc(name: string, w: number, h: number, options?: {
        gap?: number;
        origin?: [number, number];
    }): Rect;
    /** Convenience: allocate a square island. */
    allocSquare(name: string, size: number, options?: {
        gap?: number;
        origin?: [number, number];
    }): Rect;
    get(name: string): Rect;
    has(name: string): boolean;
    names(): string[];
    /** Run a paint callback scoped to an island, passing its rect. */
    paint(name: string, fn: (rect: Rect, canvas: Canvas) => void): void;
    /** A UV rule that points matching faces at an island. */
    rule(id: string, island: string, when: (cube: Cube, face: FaceName) => boolean): UvRule;
    /** Wrap the painted image as a Texture ready to attach to a model. */
    toTexture(name: string, options?: Partial<Pick<Texture, 'particle' | 'useAsDefault' | 'renderMode' | 'renderSides' | 'wrapMode' | 'frameOrder' | 'frameTime' | 'frameInterpolate'>>): Texture;
}
/**
 * Build a UV rule list from a name → island map. `patterns` matches by cube-name prefix
 * (and optionally a specific face), which is enough for the common "all teeth use the
 * teeth island" shape without writing predicates by hand.
 */
export declare function islandRules(atlas: Atlas, patterns: Array<{
    island: string;
    prefix?: string;
    suffix?: string;
    exact?: string;
    face?: FaceName | FaceName[];
    id?: string;
}>): UvRule[];
//# sourceMappingURL=atlas.d.ts.map