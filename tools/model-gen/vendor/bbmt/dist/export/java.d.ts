import type { Model } from '../model/model.js';
import type { FaceName, Vec3 } from '../model/types.js';
export interface JavaDisplayTransform {
    rotation?: [number, number, number];
    translation?: [number, number, number];
    scale?: [number, number, number];
}
export type JavaDisplay = Record<string, JavaDisplayTransform>;
/** The default Java item-model display block Blockbench writes for a held item. */
export declare const DEFAULT_JAVA_DISPLAY: JavaDisplay;
export interface JavaElementFace {
    uv: [number, number, number, number];
    texture: string;
    rotation?: number;
}
export interface JavaElement {
    name: string;
    from: [number, number, number];
    to: [number, number, number];
    rotation?: {
        origin: [number, number, number];
        axis: 'x' | 'y' | 'z';
        angle: number;
        rescale: boolean;
    };
    faces: Partial<Record<FaceName, JavaElementFace>>;
}
export interface JavaModel {
    credit: string;
    texture_size: [number, number];
    textures: Record<string, string>;
    elements: JavaElement[];
    display: JavaDisplay;
}
export interface JavaOptions {
    /** Height in Java units the model is scaled to occupy. `null` keeps raw units. */
    fitHeight?: number | null;
    /** Java's y axis points down; keep this true unless you already authored y-down. */
    flipY?: boolean;
    display?: JavaDisplay | false;
    credit?: string;
    /** Texture variable map. Defaults to `{ '0': <first texture name>, particle: <same> }`. */
    textures?: Record<string, string>;
    /** Horizontal centre in Java units (usually 8). */
    center?: number;
}
export interface JavaExportResult {
    model: JavaModel;
    /** Non-fatal problems (e.g. a multi-axis element rotation Java cannot express). */
    warnings: string[];
}
/**
 * Convert the toolkit's model space (Y up, feet near y=0) into a Java Block/Item model.
 *
 * Java models are y-down and conventionally live inside a 0..16 box, so the transform is:
 * scale the model to `fitHeight`, mirror Y about that height, and centre it horizontally.
 * Skeletal animation is not representable here — Java carries geometry, UV and texture
 * only; the rig and clips stay in the `.bbmodel`.
 */
export declare function toJavaModel(model: Model, options?: JavaOptions): JavaExportResult;
export interface JavaWriteOptions extends JavaOptions {
    /** Also write a sibling `<stem>.png` with the model's first texture. */
    writeTexture?: boolean;
    /** Also write the `texture_size` PNG even without a model file. */
    writeMcmeta?: boolean;
}
export interface JavaWriteResult {
    modelPath: string;
    texturePath?: string;
    mcmetaPath?: string;
    warnings: string[];
}
/**
 * Write `<outDir>/<stem>.json`, optionally the texture PNG and a `.mcmeta` sidecar for an
 * animated texture.
 */
export declare function writeJavaModel(model: Model, outDir: string, options?: JavaWriteOptions): JavaWriteResult;
/** The `.mcmeta` document for an animated texture. */
export declare function toMcmeta(frametime: number, frames: number[], interpolate?: boolean): {
    animation: {
        frametime: number;
        interpolate: boolean;
        frames: number[];
    };
};
/** Convenience used by tests: the transform a Java export applied. */
export declare function javaTransform(model: Model, options?: JavaOptions): (point: Vec3) => Vec3;
//# sourceMappingURL=java.d.ts.map