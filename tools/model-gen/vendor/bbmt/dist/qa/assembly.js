import { invertAround, transformPoint } from '../util/mat4.js';
import { elementMatrix } from '../model/rotation.js';
import { posedFaceCorners, visibleFaces } from '../model/cubes.js';
/**
 * Two questions a box-built model gets wrong, and that neither the coincidence check nor the
 * validator can see.
 *
 * **Is the joint sealed?** A limb assembled from a chain of boxes is only seamless while the
 * boxes *interpenetrate*. Two boxes that merely touch share a plane; the moment either end
 * rotates about a joint the overlap vanishes and a slit opens that you can look straight
 * through. Coincidence detection sees the touching faces and calls them coincident, which is
 * true and useless — it does not tell you the seam is one degree of rotation away from opening.
 * Measuring the penetration depth does.
 *
 * **Is the face buried?** A cube can be present, correctly textured, correctly UV-mapped, and
 * entirely hidden behind another cube that grew past it. This is how a model ends up with "no
 * eyes": the eye is there, at x 5.25, and the cheek plate reaches x 5.9. Nothing in the
 * validator objects, because nothing is malformed. Sample each visible face just off its surface
 * and ask whether that sample is inside another cube.
 */
/* ------------------------------------------------------------------ shared -- */
/** Map a world point into a cube's own unrotated frame. */
function toLocal(cube, point) {
    const rotation = cube.rotation;
    if (rotation[0] === 0 && rotation[1] === 0 && rotation[2] === 0)
        return point;
    return transformPoint(invertAround(rotation, cube.origin), point);
}
function inside(cube, local, slack = 0) {
    return (local[0] >= cube.from[0] - slack &&
        local[0] <= cube.to[0] + slack &&
        local[1] >= cube.from[1] - slack &&
        local[1] <= cube.to[1] + slack &&
        local[2] >= cube.from[2] - slack &&
        local[2] <= cube.to[2] + slack);
}
/** Distance from a point inside a box to the nearest of its six faces. */
function depthInside(local, cube) {
    return Math.min(local[0] - cube.from[0], cube.to[0] - local[0], local[1] - cube.from[1], cube.to[1] - local[1], local[2] - cube.from[2], cube.to[2] - local[2]);
}
const cubeByName = (model, name) => model.cubes.find((cube) => cube.name === name);
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
];
function obbOf(cube) {
    const half = [
        (cube.to[0] - cube.from[0]) / 2,
        (cube.to[1] - cube.from[1]) / 2,
        (cube.to[2] - cube.from[2]) / 2,
    ];
    const centre = [
        (cube.from[0] + cube.to[0]) / 2,
        (cube.from[1] + cube.to[1]) / 2,
        (cube.from[2] + cube.to[2]) / 2,
    ];
    const rotation = cube.rotation;
    if (rotation[0] === 0 && rotation[1] === 0 && rotation[2] === 0) {
        return { centre, axes: [[1, 0, 0], [0, 1, 0], [0, 0, 1]], half };
    }
    const m = elementMatrix({ rotation, origin: cube.origin });
    // Derive each axis by transforming a unit step, rather than reading matrix columns: this is
    // correct whatever the storage convention is, and the convention is easy to get subtly wrong.
    const lift = (v) => sub(transformPoint(m, v), transformPoint(m, [0, 0, 0]));
    const axes = [[1, 0, 0], [0, 1, 0], [0, 0, 1]].map((axis) => {
        const v = lift(axis);
        const length = Math.hypot(v[0], v[1], v[2]) || 1;
        return [v[0] / length, v[1] / length, v[2] / length];
    });
    return { centre: transformPoint(m, centre), axes, half };
}
/**
 * How much material two boxes share, along the direction in which they share the least.
 *
 * Computed over all fifteen candidate axes (three from each box, nine cross products): project
 * both boxes onto each axis, measure the width of the intersection of their shadows, and keep the
 * smallest. It is exactly zero when the boxes touch or miss, and it is the thinnest part of the
 * shared region otherwise — which is the number that decides whether a joint can survive being
 * rotated.
 *
 * An earlier version sampled a grid of points and measured how far the deepest one sat from the
 * target's surface. That reads sensibly and is wrong twice over. It cannot report more than half
 * the smaller box's thinnest side, so a thin fin *properly welded* into a thick body is reported
 * as an open joint forever; and it under-reports any overlap narrower than one sample step, so
 * the number moves when you change the sample count. Both are the kind of fault that makes a
 * check untrustworthy, which is worse than not having one.
 */
export function penetrationDepth(a, b) {
    const boxA = obbOf(a);
    const boxB = obbOf(b);
    const axes = [...boxA.axes, ...boxB.axes];
    for (const u of boxA.axes) {
        for (const v of boxB.axes) {
            const c = cross(u, v);
            const length = Math.hypot(c[0], c[1], c[2]);
            // Parallel axes give a degenerate cross product; the axis is already in the list.
            if (length > 1e-6)
                axes.push([c[0] / length, c[1] / length, c[2] / length]);
        }
    }
    let best = Infinity;
    for (const axis of axes) {
        // Project each box onto the axis: centre, then the half-width of the shadow.
        const centreA = dot(boxA.centre, axis);
        const centreB = dot(boxB.centre, axis);
        const radiusA = boxA.half[0] * Math.abs(dot(axis, boxA.axes[0])) +
            boxA.half[1] * Math.abs(dot(axis, boxA.axes[1])) +
            boxA.half[2] * Math.abs(dot(axis, boxA.axes[2]));
        const radiusB = boxB.half[0] * Math.abs(dot(axis, boxB.axes[0])) +
            boxB.half[1] * Math.abs(dot(axis, boxB.axes[1])) +
            boxB.half[2] * Math.abs(dot(axis, boxB.axes[2]));
        // The width of the *intersection* of the two shadows.
        //
        // Not `radiusA + radiusB - |centreA - centreB|`, which is the distance to push the boxes apart
        // and is equal to the shared width only while neither box contains the other on this axis.
        // For a thin fin welded inside a body that formula reports the fin's distance to the *far*
        // face — roughly the body's thickness — so every shallow detail looks like a gaping hole.
        // The shared width is what the question is about.
        const overlap = Math.min(centreA + radiusA, centreB + radiusB) - Math.max(centreA - radiusA, centreB - radiusB);
        if (overlap <= 0)
            return { depth: 0 };
        if (overlap < best)
            best = overlap;
    }
    if (!Number.isFinite(best))
        return { depth: 0 };
    // Report the midpoint of the two centres, so a failing build names a place rather than a number.
    const mid = sub(boxB.centre, boxA.centre);
    return { depth: best, at: [boxA.centre[0] + mid[0] / 2, boxA.centre[1] + mid[1] / 2, boxA.centre[2] + mid[2] / 2] };
}
/**
 * Audit a set of ordered cube chains — a tail, a neck, a horn, a wing arm — and report the
 * shallowest joint in each.
 *
 * Pass chains in the order the parts run along the limb. Consecutive pairs are the joints; a
 * chain of four cubes therefore checks three joints, which is the right granularity because those
 * are the shares that move against each other.
 */
export function auditSeal(model, chains, options = {}) {
    const minDepth = options.minDepth ?? 0.75;
    const pairs = [];
    for (const chain of chains) {
        for (let i = 0; i + 1 < chain.length; i += 1) {
            const a = cubeByName(model, chain[i]);
            const b = cubeByName(model, chain[i + 1]);
            // A chain naming a cube that does not exist is a stale list, not an open joint.
            if (!a || !b)
                continue;
            const { depth, at } = penetrationDepth(a, b);
            pairs.push({ a: a.name, b: b.name, depth, sealed: depth >= minDepth, at });
        }
    }
    const open = pairs.filter((pair) => !pair.sealed);
    const sealed = pairs.filter((pair) => pair.sealed);
    return {
        pairs,
        open,
        weakest: sealed.length
            ? sealed.reduce((worst, pair) => (pair.depth < worst.depth ? pair : worst))
            : null,
        ok: open.length === 0,
    };
}
export function formatSeal(report) {
    if (report.pairs.length === 0)
        return 'seal · no joints audited';
    const lines = [
        `seal · ${report.pairs.length - report.open.length}/${report.pairs.length} joint(s) interpenetrating`,
    ];
    if (report.weakest) {
        lines.push(`  weakest sealed joint ${report.weakest.depth.toFixed(2)}u — ${report.weakest.a} ↔ ${report.weakest.b}`);
    }
    for (const pair of report.open.slice(0, 8)) {
        lines.push(`  OPEN ${pair.a} ↔ ${pair.b} · overlap ${pair.depth.toFixed(2)}u`);
    }
    if (report.open.length > 8)
        lines.push(`  … ${report.open.length - 8} more`);
    return lines.join('\n');
}
/* ---------------------------------------------------------------- occlusion -- */
/** Outward normal of each face in the cube's own frame, before rotation. */
const FACE_NORMAL = {
    north: [0, 0, 1],
    south: [0, 0, -1],
    east: [1, 0, 0],
    west: [-1, 0, 0],
    up: [0, 1, 0],
    down: [0, -1, 0],
};
/**
 * Find faces whose surface is inside another cube, and the cubes that have no face left.
 *
 * The two answers are deliberately separated. Almost every cube in a jointed model has a buried
 * face — the inward side of a part welded into a body is inside that body, and that is correct.
 * The dragon reports 398 of them and builds clean. What is never correct is a cube with *every*
 * face buried, because then the part was added to be seen and cannot be: that is `hiddenCubes`,
 * and it is the list worth failing a build over.
 */
export function findBuriedFaces(model, options = {}) {
    const samples = options.samples ?? 3;
    const slack = options.slack ?? -0.02;
    const buriedFaces = [];
    const partlyBuried = [];
    /** Fully buried faces per cube, so the per-cube pass below is a lookup rather than a rescan. */
    const buriedPerCube = new Map();
    for (const cube of model.cubes) {
        for (const face of visibleFaces(cube)) {
            const corners = posedFaceCorners(cube, face);
            const normal = FACE_NORMAL[face];
            const rotation = cube.rotation;
            const posed = rotation[0] || rotation[1] || rotation[2]
                ? transformPoint(invertAround(rotation, [0, 0, 0]), normal)
                : normal;
            let hit = 0;
            let total = 0;
            let blockedBy = '';
            for (let i = 0; i < samples; i += 1) {
                for (let j = 0; j < samples; j += 1) {
                    const u = (i + 0.5) / samples;
                    const v = (j + 0.5) / samples;
                    // Bilinear across the posed quad: (0,1,2,3) runs around the face, so opposite corners
                    // pair as 0–2 and 1–3.
                    const point = [0, 1, 2].map((axis) => (1 - u) * (1 - v) * corners[0][axis] +
                        u * (1 - v) * corners[1][axis] +
                        (1 - u) * v * corners[3][axis] +
                        u * v * corners[2][axis]);
                    // Step off the surface so a cube is never reported as occluding itself.
                    const probe = [
                        point[0] + posed[0] * 0.06,
                        point[1] + posed[1] * 0.06,
                        point[2] + posed[2] * 0.06,
                    ];
                    total += 1;
                    for (const other of model.cubes) {
                        if (other === cube)
                            continue;
                        if (inside(other, toLocal(other, probe), slack)) {
                            hit += 1;
                            blockedBy = other.name;
                            break;
                        }
                    }
                }
            }
            if (hit === 0)
                continue;
            const entry = { cube: cube.name, face, coverage: hit / total, blockedBy };
            if (hit === total) {
                buriedFaces.push(entry);
                buriedPerCube.set(cube.name, (buriedPerCube.get(cube.name) ?? 0) + 1);
            }
            else
                partlyBuried.push(entry);
        }
    }
    const hiddenCubes = [];
    for (const cube of model.cubes) {
        const buried = buriedPerCube.get(cube.name) ?? 0;
        const faces = visibleFaces(cube).length;
        if (faces === 0 || buried < faces)
            continue;
        const first = buriedFaces.find((face) => face.cube === cube.name);
        hiddenCubes.push({ cube: cube.name, blockedBy: first?.blockedBy ?? '', faces });
    }
    return { hiddenCubes, buriedFaces, partlyBuried, ok: hiddenCubes.length === 0 };
}
export function formatOcclusion(report) {
    const lines = [
        `occlusion · ${report.hiddenCubes.length} hidden cube(s) · ${report.buriedFaces.length} buried face(s) · ${report.partlyBuried.length} partly`,
    ];
    for (const entry of report.hiddenCubes.slice(0, 8)) {
        lines.push(`  HIDDEN ${entry.cube} — present, textured, and behind ${entry.blockedBy}`);
    }
    if (report.hiddenCubes.length > 8)
        lines.push(`  … ${report.hiddenCubes.length - 8} more`);
    return lines.join('\n');
}
//# sourceMappingURL=assembly.js.map