import type { Model } from '../model/model.js';
import { type JavaOptions } from './java.js';
export interface ItemsAdderOptions extends JavaOptions {
    /** Namespace (the folder under `data/items_packs`). */
    namespace?: string;
    itemId?: string;
    displayName?: string;
    material?: string;
    writeConfig?: boolean;
}
export interface ItemsAdderResult {
    modelPath: string;
    texturePath: string;
    configPath?: string;
    warnings: string[];
}
/**
 * Emit an ItemsAdder data pack fragment.
 *
 * Layout written (relative to `outDir`, typically `plugins/ItemsAdder`):
 *   data/items_packs/<namespace>/models/<stem>.json
 *   data/items_packs/<namespace>/textures/<stem>.png
 *   data/items_packs/<namespace>/configs/<stem>.yml
 *
 * ItemsAdder resolves `model_path` inside the namespace, and its textures are referenced
 * as `<namespace>:<path>` without the file extension. Like the Oraxen adapter this is a
 * conventional layout — verify against your ItemsAdder version if the item does not appear.
 */
export declare function writeItemsAdder(model: Model, outDir: string, options?: ItemsAdderOptions): ItemsAdderResult;
//# sourceMappingURL=itemsadder.d.ts.map