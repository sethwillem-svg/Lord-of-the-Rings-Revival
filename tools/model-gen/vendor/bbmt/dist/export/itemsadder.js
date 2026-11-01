import { fileStem, writeBinary, writeJson, writeText } from './io.js';
import { toJavaModel } from './java.js';
import { toYaml } from './yaml.js';
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
export function writeItemsAdder(model, outDir, options = {}) {
    const stem = fileStem(model.name);
    const namespace = options.namespace ?? 'trex';
    const itemId = options.itemId ?? stem;
    const textureRef = `${namespace}:${stem}`;
    const { model: javaModel, warnings } = toJavaModel(model, {
        ...options,
        textures: options.textures ?? { '0': textureRef, particle: textureRef },
    });
    const base = `${outDir}/data/items_packs/${namespace}`;
    const modelPath = writeJson(`${base}/models/${stem}.json`, javaModel);
    const texture = model.textures[0];
    const texturePath = texture
        ? writeBinary(`${base}/textures/${stem}.png`, texture.data)
        : `${base}/textures/${stem}.png`;
    let configPath;
    if (options.writeConfig !== false) {
        configPath = writeText(`${base}/configs/${stem}.yml`, toYaml({
            info: { namespace },
            items: {
                [itemId]: {
                    display_name: options.displayName ?? model.name,
                    resource: {
                        material: options.material ?? 'PAPER',
                        generate: true,
                        textures: [textureRef],
                    },
                    model_path: stem,
                },
            },
        }));
    }
    return { modelPath, texturePath, ...(configPath ? { configPath } : {}), warnings };
}
//# sourceMappingURL=itemsadder.js.map