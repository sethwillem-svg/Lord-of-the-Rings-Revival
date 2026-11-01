import type { Model } from '../model/model.js';
import { type JavaOptions } from './java.js';
export interface OraxenOptions extends JavaOptions {
    /** Item id inside the pack. Defaults to the model's file stem. */
    itemId?: string;
    displayName?: string;
    /** Namespace prefix used in the item's texture reference. */
    namespace?: string;
    /** Material the item is bound to. */
    material?: string;
    /** Emit the `items/<id>.yml` config alongside the pack files. */
    writeConfig?: boolean;
}
export interface OraxenResult {
    modelPath: string;
    texturePath: string;
    configPath?: string;
    warnings: string[];
}
/**
 * Emit an Oraxen resource-pack fragment.
 *
 * Layout written (relative to `outDir`, which should be `plugins/Oraxen`):
 *   pack/models/<stem>.json      Java item model
 *   pack/textures/<stem>.png     the atlas
 *   items/<stem>.yml             item definition pointing at the model
 *
 * Oraxen itself is lenient about the texture reference string, but it must match the key
 * used in the `textures` map of the model — both are derived from the same namespace here.
 * Exact layout has varied between Oraxen releases, so treat this as a conventional default.
 */
export declare function writeOraxen(model: Model, outDir: string, options?: OraxenOptions): OraxenResult;
//# sourceMappingURL=oraxen.d.ts.map