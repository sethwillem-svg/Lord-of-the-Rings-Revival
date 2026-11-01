/**
 * The default policy, generalised from the T-Rex generator: every face samples a shared
 * "skin field" at a vertical offset derived from the cube's height, so the gradient alone
 * produces lighter upper surfaces and a darker underside with no painted-on shading.
 * The horizontal position is a hash of the cube name and face, which keeps the layout
 * stable between rebuilds while spreading faces across the field.
 */
export function skinFieldPolicy(options = {}) {
    const region = options.region ?? [0, 0, 64, 64];
    const [rx1, ry1, rx2, ry2] = region;
    const regionWidth = rx2 - rx1;
    const regionHeight = ry2 - ry1;
    const reference = options.heightReference ?? 38;
    const litFromAbove = options.litFromAbove ?? true;
    const upNudge = options.upNudge ?? -7;
    const downNudge = options.downNudge ?? 6;
    return (cube, face) => {
        const [x1, y1, z1] = cube.from;
        const [x2, y2, z2] = cube.to;
        const sizeX = x2 - x1;
        const sizeY = y2 - y1;
        const sizeZ = z2 - z1;
        const [width, height] = face === 'north' || face === 'south'
            ? [sizeX, sizeY]
            : face === 'east' || face === 'west'
                ? [sizeZ, sizeY]
                : [sizeX, sizeZ];
        const w = Math.min(Math.max(Math.round(width), 1), regionWidth - 1);
        const h = Math.min(Math.max(Math.round(height), 1), regionHeight - 1);
        const centerY = (y1 + y2) / 2;
        const normalized = Math.min(Math.max(centerY / reference, 0), 1);
        const brightness = litFromAbove ? 1 - normalized : normalized;
        let v = ry1 + Math.round(brightness * (regionHeight - h));
        if (face === 'up')
            v += upNudge;
        else if (face === 'down')
            v += downNudge;
        v = Math.round(Math.min(Math.max(v, ry1), ry2 - h));
        const span = regionWidth - w;
        let u = rx1;
        if (span > 0) {
            let hash = 2166136261;
            const key = `${cube.name}|${face}`;
            for (let i = 0; i < key.length; i += 1) {
                hash ^= key.charCodeAt(i);
                hash = Math.imul(hash, 16777619);
            }
            u = rx1 + (hash >>> 0) % span;
        }
        return [u, v, u + w, v + h];
    };
}
/**
 * Resolve a UV window for every face of every cube. Returns both the mutated cubes (so
 * exporters see concrete windows) and a trace of where each window came from, which the
 * validator surfaces when a face is sampling outside the texture.
 */
export function assignUv(cubes, policy = {}, resolution = [64, 64]) {
    // When no fallback is supplied the skin field is sized to the texture, so a 32×32 model
    // never receives windows computed against a 64×64 field.
    const fallback = policy.fallback ?? skinFieldPolicy({ region: [0, 0, resolution[0], resolution[1]] });
    const trace = [];
    for (const cube of cubes) {
        for (const face of ['north', 'south', 'east', 'west', 'up', 'down']) {
            const explicit = cube.faces[face];
            if (explicit) {
                trace.push({ cube, face, rect: explicit, source: 'explicit' });
                continue;
            }
            const rule = policy.rules?.find((candidate) => candidate.when(cube, face));
            if (rule) {
                const rect = typeof rule.rect === 'function' ? rule.rect(cube, face) : rule.rect;
                cube.faces[face] = rect;
                trace.push({ cube, face, rect, source: 'rule', material: rule.id });
                continue;
            }
            const rect = fallback(cube, face);
            cube.faces[face] = rect;
            trace.push({ cube, face, rect, source: 'fallback' });
        }
    }
    return trace;
}
/**
 * Classic "box UV": every face of a cube reads a contiguous 3×2 patch, the way vanilla
 * Minecraft textures are laid out. Origin is the top-left of the patch in the texture.
 */
export function boxUv(cube, origin) {
    const [x1, y1, z1] = cube.from;
    const [x2, y2, z2] = cube.to;
    const sx = x2 - x1;
    const sy = y2 - y1;
    const sz = z2 - z1;
    const [ox, oy] = origin;
    const rect = (u, v, w, h) => [ox + u, oy + v, ox + u + w, oy + v + h];
    return {
        //  width = sizeZ + sizeX + sizeZ + sizeX, height = sizeY + sizeZ
        east: rect(0, sz, sz, sy),
        north: rect(sz, sz, sx, sy),
        west: rect(sz + sx, sz, sz, sy),
        south: rect(sz + sx + sz, sz, sx, sy),
        up: rect(sz + sx, 0, sx, sz),
        down: rect(sz + sx + sx, 0, sx, sz),
    };
}
//# sourceMappingURL=uv.js.map