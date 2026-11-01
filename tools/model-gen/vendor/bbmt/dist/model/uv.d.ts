import type { UvRect } from './types.js';
import type { Cube } from './types.js';
import type { FaceName } from './types.js';
/**
 * UV assignment.
 *
 * A cube face needs a rectangle in the texture. There are three ways to get one, tried in
 * order, and the order is the whole point: explicit beats rule beats fallback, so a spec
 * can paint one special face (an eye) while everything else flows through the default.
 *
 *   1. `cube.faces[face]`   — explicit window written in the spec.
 *   2. a matching `UvRule` — name/face predicates, used for material islands.
 *   3. `fallback(cube,face)` — the default policy.
 */
export interface UvRule {
    /** Human-readable label used in diagnostics. */
    id: string;
    /** Predicate: return true to claim this face. */
    when: (cube: Cube, face: FaceName) => boolean;
    /** Either a fixed window or a function of the face. */
    rect: UvRect | ((cube: Cube, face: FaceName) => UvRect);
}
export interface UvPolicy {
    rules?: UvRule[];
    fallback?: (cube: Cube, face: FaceName) => UvRect;
}
export interface SkinFieldOptions {
    /** Region every generic face samples from. Defaults to the full texture. */
    region?: UvRect;
    /** Model height that maps to the bottom of the region (darkest). */
    heightReference?: number;
    /** Flip so higher cubes read lighter (sun-lit) and lower ones darker. */
    litFromAbove?: boolean;
    /** Nudge applied to the up face (default -7) and down face (default +6). */
    upNudge?: number;
    downNudge?: number;
}
/**
 * The default policy, generalised from the T-Rex generator: every face samples a shared
 * "skin field" at a vertical offset derived from the cube's height, so the gradient alone
 * produces lighter upper surfaces and a darker underside with no painted-on shading.
 * The horizontal position is a hash of the cube name and face, which keeps the layout
 * stable between rebuilds while spreading faces across the field.
 */
export declare function skinFieldPolicy(options?: SkinFieldOptions): (cube: Cube, face: FaceName) => UvRect;
export interface AssignedFace {
    cube: Cube;
    face: FaceName;
    rect: UvRect;
    source: 'explicit' | 'rule' | 'fallback';
    /**
     * Identity of the rule that claimed the face — in practice, which texture island it samples.
     * `undefined` means the fallback painted it, i.e. it belongs to the base material. Recorded
     * so downstream passes can tell a material change across a cube boundary (an eye meeting the
     * skull) from a step *within* one material (two bands of the same skin ramp).
     */
    material?: string;
}
/**
 * Resolve a UV window for every face of every cube. Returns both the mutated cubes (so
 * exporters see concrete windows) and a trace of where each window came from, which the
 * validator surfaces when a face is sampling outside the texture.
 */
export declare function assignUv(cubes: Cube[], policy?: UvPolicy, resolution?: [number, number]): AssignedFace[];
/**
 * Classic "box UV": every face of a cube reads a contiguous 3×2 patch, the way vanilla
 * Minecraft textures are laid out. Origin is the top-left of the patch in the texture.
 */
export declare function boxUv(cube: Cube, origin: [number, number]): Partial<Record<FaceName, UvRect>>;
//# sourceMappingURL=uv.d.ts.map