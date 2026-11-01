import type { Model } from '../model/model.js';
import type { BbmodelDocument } from '../model/types.js';
export interface BbmodelOptions {
    /** `model_identifier` written into the file. Defaults to the model's identifier. */
    identifier?: string;
    formatVersion?: string;
    modelFormat?: string;
    /** Overwrite the name stored in the document. */
    name?: string;
    /** Skip animations (useful for a geometry-only snapshot). */
    omitAnimations?: boolean;
}
/**
 * Serialise a model to Blockbench's native `.bbmodel` JSON.
 *
 * The output is hand-written rather than round-tripped through Blockbench, so it has to
 * respect the exact shape Blockbench's `bbmodel` codec expects: elements and groups carry
 * their own uuids, the hierarchy lives in `outliner` (groups as objects, elements as plain
 * uuid strings), textures embed a base64 data URI, and animation animators are keyed by
 * the *bone's* uuid. Ids are derived deterministically, so rebuilding a model produces the
 * same file byte for byte and diffs stay meaningful.
 */
export declare function toBbmodel(model: Model, options?: BbmodelOptions): BbmodelDocument;
/** Serialise to a `.bbmodel` string. */
export declare function serializeBbmodel(model: Model, options?: BbmodelOptions): string;
/** Write a `.bbmodel` to disk. Returns the path written. */
export declare function writeBbmodel(model: Model, file: string, options?: BbmodelOptions): string;
//# sourceMappingURL=bbmodel.d.ts.map