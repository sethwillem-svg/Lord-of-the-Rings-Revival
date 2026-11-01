import type { Model } from '../model/model.js';
export interface MetricsReport {
    /** Number of distinct opaque colours in the atlas. Target 8–30. */
    palette: number;
    /** Mean distinct colours sampled inside one face's UV window. Target ≈3. */
    coloursPerFace: number;
    /** Fraction of faces with a single flat colour. Reported, never scored. */
    flatFaceShare: number;
    /** Faces per cube whose neighbours are a different colour band (edge contrast). */
    bandEdgesPerFace: number;
    /** 0..100 composite. */
    score: number;
    grade: 'S' | 'A' | 'B' | 'C' | 'D';
    notes: string[];
    /** Fraction of the texture's opaque pixels that are actually used by a face. */
    uvUtilisation: number;
    /** Fraction of bones that carry at least one keyframe across all clips. */
    animatedBoneShare: number;
    /** Orphaned animators: bones referenced by a clip but absent from the rig. */
    orphanAnimators: string[];
    silhouette: {
        /** Pixels covered by the model in the front view. */
        coverage: number;
        /** 0 = perfectly symmetric left/right, 1 = fully asymmetric. */
        asymmetry: number;
    };
}
export interface MetricsOptions {
    /** Resolution used for the silhouette pass. */
    viewSize?: number;
}
/**
 * Offline quality metrics.
 *
 * These encode what "good Minecraft art" means for this toolkit, and just as importantly
 * what it does not: flat blocks are *reported* but never *scored*, because scoring flatness
 * rewards dithering and noise and produces the dirty-looking textures the metric exists to
 * prevent. What is scored is a small deliberate palette, a consistent number of colour
 * bands per face, and clear band edges.
 */
export declare function metrics(model: Model, options?: MetricsOptions): MetricsReport;
/** Human-readable metrics block for the console. */
export declare function formatMetrics(report: MetricsReport): string;
//# sourceMappingURL=metrics.d.ts.map