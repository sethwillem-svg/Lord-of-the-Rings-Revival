/** Build `count` cubes, flattening away entries a callback returns `null`/`[]` for. */
export function series(count, fn) {
    const out = [];
    for (let i = 0; i < count; i += 1) {
        const value = fn(i);
        if (!value)
            continue;
        if (Array.isArray(value))
            out.push(...value);
        else
            out.push(value);
    }
    return out;
}
/** Produce the left/right halves of a symmetric part in one call. */
export function mirrorPair(options) {
    const left = { ...options, name: options.name, from: options.from, to: options.to, bone: options.bone };
    const flippedFrom = [-options.to[0], options.from[1], options.from[2]];
    const flippedTo = [-options.from[0], options.to[1], options.to[2]];
    const right = {
        ...options,
        name: options.mirroredName ?? `right_${options.name.replace(/^left_/, '')}`,
        from: flippedFrom,
        to: flippedTo,
        bone: options.mirroredBone ?? `right_${options.bone.replace(/^left_/, '')}`,
    };
    return [left, right];
}
/**
 * A row of small hard parts — teeth, claws, spikes, scutes along the tail. Each one is a
 * separate cube so it keeps a crisp edge instead of melting into the parent.
 */
export function spikes(options) {
    const taper = options.taper ?? 1;
    const out = [];
    for (let i = 0; i < options.count; i += 1) {
        const scale = taper ** i;
        const start = [
            options.start[0] + options.step[0] * i,
            options.start[1] + options.step[1] * i,
            options.start[2] + options.step[2] * i,
        ];
        const size = [options.size[0] * scale, options.size[1] * scale, options.size[2] * scale];
        if (options.bothSides) {
            out.push({
                name: `${options.prefix}_left_${i}`,
                from: start,
                to: [start[0] + size[0], start[1] + size[1], start[2] + size[2]],
                bone: options.bone,
            });
            out.push({
                name: `${options.prefix}_right_${i}`,
                from: [-start[0] - size[0], start[1], start[2]],
                to: [-start[0], start[1] + size[1], start[2] + size[2]],
                bone: options.bone,
            });
        }
        else {
            out.push({
                name: `${options.prefix}_${i}`,
                from: start,
                to: [start[0] + size[0], start[1] + size[1], start[2] + size[2]],
                bone: options.bone,
            });
        }
    }
    return out;
}
/** Declare a straight bone chain (neck, tail, tentacle) and return the names. */
export function boneChain(model, options) {
    if (options.names.length !== options.pivots.length) {
        throw new Error('boneChain: names and pivots must be the same length');
    }
    let parent = options.parent ?? null;
    const created = [];
    options.names.forEach((name, index) => {
        model.bone(name, options.pivots[index], parent, options.color !== undefined ? { color: options.color } : {});
        parent = name;
        created.push(name);
    });
    return created;
}
/** A tapering run of boxes sharing one bone or spread across a chain — tails, trunks, necks. */
export function segmentedBoxes(options) {
    const taper = options.taper ?? 0.92;
    const out = [];
    for (let i = 0; i < options.count; i += 1) {
        const scale = taper ** i;
        const from = [
            options.start[0] + options.step[0] * i,
            options.start[1] + options.step[1] * i,
            options.start[2] + options.step[2] * i,
        ];
        const size = [options.size[0] * scale, options.size[1] * scale, options.size[2] * scale];
        out.push({
            name: `${options.prefix}_${i + 1}`,
            from,
            to: [from[0] + size[0], from[1] + size[1], from[2] + size[2]],
            bone: options.bone,
        });
    }
    return out;
}
/** A thin plate — fins, ridges, fins, sails. */
export function plate(options) {
    const axis = options.thicknessAxis ?? 'x';
    const thickness = options.thickness ?? 1;
    const from = [...options.from];
    const to = [...options.to];
    const center = (from[axis === 'x' ? 0 : axis === 'y' ? 1 : 2] + to[axis === 'x' ? 0 : axis === 'y' ? 1 : 2]) / 2;
    const index = axis === 'x' ? 0 : axis === 'y' ? 1 : 2;
    from[index] = center - thickness / 2;
    to[index] = center + thickness / 2;
    return { name: options.name, from, to, bone: options.bone };
}
//# sourceMappingURL=shapes.js.map