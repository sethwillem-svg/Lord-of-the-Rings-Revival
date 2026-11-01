import { FACE_NAMES } from '../model/types.js';
import { UuidSpace } from '../util/uuid.js';
import { mergeKeys } from '../anim/keys.js';
import { writeText } from './io.js';
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
export function toBbmodel(model, options = {}) {
    // Make sure every face has a concrete window before serialising.
    model.assignUv();
    const space = new UuidSpace(`${model.name}:${options.identifier ?? model.identifier}`);
    const boneUuid = (name) => space.get(`bone:${name}`);
    const elementUuid = (name) => space.get(`element:${name}`);
    const groups = model.rig.ordered().map((bone) => ({
        name: bone.name,
        uuid: boneUuid(bone.name),
        export: true,
        locked: false,
        scope: bone.scope,
        selected: false,
        visibility: bone.visibility,
        _static: { properties: {}, temp_data: {} },
        origin: [...bone.pivot],
        rotation: [...bone.rotation],
        color: bone.color,
        children: [],
        reset: false,
        shade: true,
        mirror_uv: false,
        autouv: 0,
        isOpen: true,
        primary_selected: false,
    }));
    const elements = model.cubes.map((cube) => {
        const faces = {};
        for (const face of FACE_NAMES) {
            const uv = cube.faces[face];
            if (!uv)
                continue;
            faces[face] = { uv: [...uv], texture: cube.texture };
        }
        const element = {
            name: cube.name,
            box_uv: false,
            render_order: 'default',
            locked: false,
            export: true,
            scope: 0,
            allow_mirror_modeling: true,
            from: [...cube.from],
            to: [...cube.to],
            autouv: 0,
            color: cube.color,
            origin: [...cube.origin],
            faces,
            type: 'cube',
            uuid: elementUuid(cube.name),
        };
        if (cube.rotation.some((value) => value !== 0))
            element.rotation = [...cube.rotation];
        if (cube.inflate !== 0)
            element.inflate = cube.inflate;
        return element;
    });
    const buildNode = (boneName) => {
        const children = [];
        for (const child of model.rig.childrenOf(boneName))
            children.push(buildNode(child.name));
        for (const cube of model.cubesOf(boneName))
            children.push(elementUuid(cube.name));
        return { uuid: boneUuid(boneName), isOpen: true, children };
    };
    const outliner = [];
    for (const bone of model.rig.all()) {
        if (bone.parent === null)
            outliner.push(buildNode(bone.name));
    }
    // A model may legitimately have no rig; put loose cubes at the root.
    if (outliner.length === 0) {
        for (const cube of model.cubes)
            outliner.push({ uuid: elementUuid(cube.name), isOpen: true, children: [] });
    }
    const textures = model.textures.map((texture, index) => ({
        name: texture.name,
        path: '',
        folder: '',
        namespace: '',
        id: String(index),
        group: '',
        scope: 0,
        width: texture.width,
        height: texture.height,
        uv_width: texture.width,
        uv_height: texture.height,
        particle: texture.particle,
        use_as_default: texture.useAsDefault,
        layers_enabled: false,
        sync_to_project: '',
        file_format: 'png',
        render_mode: texture.renderMode,
        render_sides: texture.renderSides,
        wrap_mode: texture.wrapMode,
        pbr_channel: 'color',
        fps: texture.frameTime > 0 ? Math.max(1, Math.round(20 / texture.frameTime)) : 10,
        frame_time: texture.frameTime,
        frame_order_type: texture.frameOrder.length > 0 ? 'loop' : 'loop',
        frame_order: texture.frameOrder.join(','),
        frame_interpolate: texture.frameInterpolate,
        visible: true,
        internal: false,
        saved: false,
        uuid: space.get(`texture:${index}`),
        source: `data:image/png;base64,${Buffer.from(texture.data).toString('base64')}`,
    }));
    const animations = [];
    if (!options.omitAnimations) {
        for (const clip of model.clips) {
            const keys = mergeKeys(clip.keys);
            const byAnimator = new Map();
            // Group by bone+channel, preserving the sort order mergeKeys produced.
            const grouped = new Map();
            for (const key of keys) {
                const animatorKey = `${key.bone}|${key.channel}`;
                const frame = {
                    channel: key.channel,
                    data_points: [{ x: key.value[0], y: key.value[1], z: key.value[2] }],
                    uuid: space.get(`kf:${clip.name}:${key.bone}:${key.channel}:${key.time}`),
                    time: key.time,
                    color: -1,
                    interpolation: key.interpolation,
                };
                const list = grouped.get(animatorKey);
                if (list)
                    list.push(frame);
                else
                    grouped.set(animatorKey, [frame]);
            }
            for (const bone of model.rig.ordered()) {
                const channels = ['rotation', 'position', 'scale'];
                const frames = [];
                for (const channel of channels)
                    frames.push(...(grouped.get(`${bone.name}|${channel}`) ?? []));
                if (frames.length === 0)
                    continue;
                byAnimator.set(boneUuid(bone.name), {
                    name: bone.name,
                    type: 'bone',
                    rotation_global: false,
                    quaternion_interpolation: false,
                    keyframes: frames,
                });
            }
            animations.push({
                uuid: space.get(`anim:${clip.name}`),
                name: clip.name,
                loop: clip.loop,
                override: false,
                length: clip.length,
                snapping: clip.framesPerSecond,
                selected: false,
                group_name: '',
                scope: 0,
                anim_time_update: '',
                blend_weight: clip.blendWeight,
                start_delay: '',
                loop_delay: '',
                animators: Object.fromEntries(byAnimator),
            });
        }
    }
    return {
        meta: {
            format_version: options.formatVersion ?? '5.0',
            model_format: options.modelFormat ?? 'free',
            box_uv: false,
        },
        name: options.name ?? model.name,
        model_identifier: options.identifier ?? model.identifier,
        visible_box: [1, 1, 0],
        variable_placeholders: '',
        multi_file_ruleset: '',
        variable_placeholder_buttons: [],
        timeline_setups: [],
        unhandled_root_fields: {},
        resolution: { width: model.resolution[0], height: model.resolution[1] },
        elements,
        groups,
        outliner,
        textures,
        animations,
    };
}
/** Serialise to a `.bbmodel` string. */
export function serializeBbmodel(model, options = {}) {
    return `${JSON.stringify(toBbmodel(model, options), null, 2)}\n`;
}
/** Write a `.bbmodel` to disk. Returns the path written. */
export function writeBbmodel(model, file, options = {}) {
    return writeText(file, serializeBbmodel(model, options));
}
//# sourceMappingURL=bbmodel.js.map