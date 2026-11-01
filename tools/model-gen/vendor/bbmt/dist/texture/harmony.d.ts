import type { Model } from '../model/model.js';
import { hexToRgb, mixRgb, rgbToHex } from '../util/math.js';
import type { Pose } from '../render/scene.js';
import { type Rgba } from './canvas.js';
type Rgb = [number, number, number];
/**
 * Colour harmony and seam softening.
 *
 * The complaint these solve is specific: a model whose materials were chosen independently
 * looks fine on each part and wrong where the parts meet. A light olive back against a
 * separate dark swamp-green belly is not a shading problem — it is two hues that were never
 * related. `harmonize` pulls a set of colours toward a shared hue while preserving how light
 * or dark each one is, and `deriveShade` builds a secondary material *from* the primary so it
 * starts in the right family.
 *
 * `softenSeams` then deals with the pixel-level version of the same problem: two faces that
 * meet at a 3D edge, each painted from a different part of the atlas, with an abrupt colour
 * change right at the join. It finds those joins, maps the shared edge into the texture on
 * both sides, and blends a band of pixels across it.
 *
 * How aggressive any of this is belongs to the prompt, so every entry point takes a strength
 * and `SURFACE_STYLES` bundles the numbers into the three answers people actually ask for:
 * a crisp voxel look, a balanced one, and a soft one.
 */
export declare function rgbToHsl(rgb: Rgb): [number, number, number];
export declare function hslToRgb(hsl: [number, number, number]): Rgb;
export interface HarmonizeOptions {
    /** 0 leaves the colours untouched, 1 collapses them onto one hue. Default 0.55. */
    strength?: number;
    /** Target hue to move toward. Defaults to the saturation-weighted mean of the input. */
    targetHue?: number;
    /** Indices that must not move (e.g. an accent colour that should stay loud). */
    pinned?: number[];
}
/**
 * Pull a set of colours toward a shared hue.
 *
 * Lightness and saturation are preserved, so a set that already reads as "the same material at
 * different depths" keeps its depth — it just stops jumping between, say, olive and swamp.
 * That is the whole difference between the belly of a model looking shaded and looking like a
 * different creature.
 */
export declare function harmonize(colors: Rgb[], options?: HarmonizeOptions): Rgb[];
export interface DeriveShadeOptions {
    /** Signed lightness offset, −1..1. */
    lightness?: number;
    /** Multiplier on saturation. 1 keeps it. */
    saturation?: number;
    /** Hue offset in degrees. */
    hueShift?: number;
}
/**
 * Derive a secondary material from a primary one, in the same family.
 *
 * `deriveShade(skin, { lightness: -0.22 })` is how a belly, a scute plate or a shadowed
 * underside should be chosen — same hue, darker — instead of picking a plausible-looking but
 * unrelated colour and discovering the join only when the model is viewed from below.
 */
export declare function deriveShade(base: Rgb | string, options?: DeriveShadeOptions): Rgb;
/** Intermediate colours between two materials, for a bridge band. */
export declare function bridgeColors(a: Rgb | string, b: Rgb | string, steps?: number): Rgb[];
export type SurfaceStyleName = 'sharp' | 'balanced' | 'soft';
export interface SurfaceStyle {
    /** Pixels of gradient written across a cube join. 0 = a hard line. */
    edgeSoftness: number;
    /** How strongly the seam bands are blended, 0..1. */
    seamStrength: number;
    /** How far a palette is pulled toward a shared hue, 0..1. */
    paletteHarmony: number;
    /** Discrete bands in a material ramp. */
    bands: number;
    /** Ramp contrast. */
    contrast: number;
}
/**
 * The three answers people actually give when asked how the surface should look.
 * `sharp` is the classic crisp voxel model, `soft` leans into smooth transitions, and
 * `balanced` is the default this toolkit ships examples with.
 */
export declare const SURFACE_STYLES: Record<SurfaceStyleName, SurfaceStyle>;
/** Accept a style name or a partial override and produce a complete style. */
export declare function resolveStyle(style?: SurfaceStyleName | Partial<SurfaceStyle>): SurfaceStyle;
export interface SoftenSeamsOptions {
    /** How many pixels wide the transition is, counting the solid join. Default from the style. */
    softness?: number;
    /** Density of the dithered bands beyond the solid join, 0..1. Default from the style. */
    strength?: number;
    /** Preset, or a partial style override. */
    style?: SurfaceStyleName | Partial<SurfaceStyle>;
    /**
     * Only blend when the two faces' colours differ by at least this much in Manhattan distance
     * (0..765). The default skips the steps *within* one material, so a banded skin field is not
     * smoothed into a gradient — only genuine material changes are bridged.
     */
    minDifference?: number;
    /** Skeleton pose to measure the geometry in. Defaults to the rest pose. */
    pose?: Pose;
    /** How far apart two edges may be and still count as joined, model units. Default 0.02. */
    tolerance?: number;
    /**
     * Only bridge where the two faces sample different materials. Default true.
     *
     * Without this the pass also bridges the steps *within* one material — the neighbouring bands
     * of a skin ramp differ by enough to trip the colour test — and a clean banded texture is
     * quietly smoothed into a gradient. Set false to bridge every join regardless.
     */
    acrossMaterialsOnly?: boolean;
    /**
     * Allow writes to texels sampled by several faces, so a join on a shared atlas island gets a
     * rim on the whole island. Default false, because a small island cannot take one.
     */
    shareIslands?: boolean;
}
export interface SoftenSeamsResult {
    /** Adjacent face pairs considered. */
    pairs: number;
    /** Pairs that were actually bridged. */
    blended: number;
    /** Texture pixels written. */
    pixels: number;
    softness: number;
    strength: number;
    /**
     * How many materials were skipped because their joins fall on texels that more than one face
     * samples. A nonzero count is not a failure: it means those joins belong to a shared atlas
     * island, where the fix is to give the island its own region or derive its colour from the
     * base material rather than to paint a rim. Raise it with `shareIslands: true` to give the
     * whole island the rim instead.
     */
    sharedJoins: number;
}
/**
 * Soften the colour join across cube boundaries.
 *
 * For every pair of faces that meet along a 3D edge — including two faces of the *same* cube,
 * which is where a UV policy most often puts two different atlas islands side by side — the
 * shared edge is mapped into the texture on both sides and the boundary pixels are given the
 * neighbouring material's colour.
 *
 * Three rules keep this from turning a model into mush, and all three were learned by getting
 * them wrong first:
 *
 *  - **Only the neighbour's own colour is ever written.** Blending two colours and snapping the
 *    result to the palette looks reasonable and is not: halfway between olive skin and pink
 *    tongue is a colour that belongs to neither, and it lands as a pink speck on the model.
 *    Writing the neighbour's colour makes the join a discrete interleave instead, so the
 *    palette cannot grow and no unrelated colour can appear.
 *  - **Only texels no other face samples are written.** Islands are shared: the belly island is
 *    about four pixels square and forty faces read it, so bridging a seam for one of them
 *    would recolour the other thirty-nine and put the colour anywhere on the model.
 *  - **Only joins between different materials are bridged.** Two bands of one skin ramp meeting
 *    at a cube edge is the texture working as intended.
 *
 * The first band is written solid, so the boundary reads as a deliberate line. Further bands
 * (`softness`) fade inward as a dither, which is how a pixel artist widens a transition without
 * inventing colours. `softness: 0` — the `sharp` style — leaves every join crisp.
 */
export declare function softenSeams(model: Model, options?: SoftenSeamsOptions): SoftenSeamsResult;
/** RGBA tuple for this RGB colour, at full opacity. */
export declare function rgbaOf(rgb: Rgb): Rgba;
/** Hex helpers, re-exported so a build script needs one import for colour work. */
export declare const colourUtils: {
    hexToRgb: typeof hexToRgb;
    rgbToHex: typeof rgbToHex;
    rgbToHsl: typeof rgbToHsl;
    hslToRgb: typeof hslToRgb;
    mixRgb: typeof mixRgb;
    deriveShade: typeof deriveShade;
    harmonize: typeof harmonize;
};
export {};
//# sourceMappingURL=harmony.d.ts.map