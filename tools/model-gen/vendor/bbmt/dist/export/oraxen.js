import { fileStem, writeBinary, writeJson, writeText } from './io.js';
import { toJavaModel } from './java.js';
import { toYaml } from './yaml.js';
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
export function writeOraxen(model, outDir, options = {}) {
    const stem = fileStem(model.name);
    const itemId = options.itemId ?? stem;
    const namespace = options.namespace ?? 'oraxen';
    const textureRef = `${namespace}:item/${stem}`;
    const { model: javaModel, warnings } = toJavaModel(model, {
        ...options,
        textures: options.textures ?? { '0': textureRef, particle: textureRef },
    });
    const modelPath = writeJson(`${outDir}/pack/models/${stem}.json`, javaModel);
    const texture = model.textures[0];
    const texturePath = texture
        ? writeBinary(`${outDir}/pack/textures/${stem}.png`, texture.data)
        : `${outDir}/pack/textures/${stem}.png`;
    let configPath;
    if (options.writeConfig !== false) {
        configPath = writeText(`${outDir}/items/${itemId}.yml`, toYaml({
            [itemId]: {
                displayname: options.displayName ?? model.name,
                material: options.material ?? 'PAPER',
                pack: {
                    generate_model: false,
                    model: stem,
                    texture: textureRef,
                },
            },
        }));
    }
    return { modelPath, texturePath, ...(configPath ? { configPath } : {}), warnings };
}
//# sourceMappingURL=oraxen.js.map