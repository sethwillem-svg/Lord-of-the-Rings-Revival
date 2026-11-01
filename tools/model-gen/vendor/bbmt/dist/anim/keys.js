import { round3 } from '../util/math.js';
/** Build one keyframe. Times and values are rounded like Blockbench does. */
export function kf(bone, channel, time, value, interpolation = 'catmullrom') {
    return {
        bone,
        channel,
        time: round3(time),
        value: [round3(value[0]), round3(value[1]), round3(value[2])],
        interpolation,
    };
}
export const rot = (bone, time, value, interpolation = 'catmullrom') => kf(bone, 'rotation', time, value, interpolation);
export const rot3 = (bone, time, x, y, z, interpolation = 'catmullrom') => kf(bone, 'rotation', time, [x, y, z], interpolation);
export const pos = (bone, time, value, interpolation = 'catmullrom') => kf(bone, 'position', time, value, interpolation);
export const pos3 = (bone, time, x, y, z, interpolation = 'catmullrom') => kf(bone, 'position', time, [x, y, z], interpolation);
export const scale = (bone, time, value, interpolation = 'catmullrom') => kf(bone, 'scale', time, value, interpolation);
/** Uniform scale keyframe. */
export const scaleUniform = (bone, time, value, interpolation = 'catmullrom') => kf(bone, 'scale', time, [value, value, value], interpolation);
/**
 * Sort keyframes by bone, then channel (rotation, position, scale), then time — the order
 * Blockbench writes and the order that makes a diff readable.
 */
export function sortKeys(keys) {
    const channelRank = { rotation: 0, position: 1, scale: 2 };
    return [...keys].sort((a, b) => {
        if (a.bone !== b.bone)
            return a.bone < b.bone ? -1 : 1;
        if (a.channel !== b.channel)
            return channelRank[a.channel] - channelRank[b.channel];
        return a.time - b.time;
    });
}
/** Drop exact duplicates (same bone/channel/time) keeping the first occurrence. */
export function dedupeKeys(keys) {
    const seen = new Set();
    const out = [];
    for (const key of keys) {
        const id = `${key.bone}|${key.channel}|${key.time}`;
        if (seen.has(id))
            continue;
        seen.add(id);
        out.push(key);
    }
    return out;
}
/** Flatten a list of key lists into one clip-ready array. */
export function mergeKeys(...groups) {
    return dedupeKeys(sortKeys(groups.flat()));
}
/** Count keys per bone, for QA and summaries. */
export function keyCountByBone(keys) {
    const counts = new Map();
    for (const key of keys)
        counts.set(key.bone, (counts.get(key.bone) ?? 0) + 1);
    return counts;
}
/** The set of bones touched by a key list. */
export function animatedBones(keys) {
    return new Set(keys.map((key) => key.bone));
}
/** Highest keyframe time in a list (0 when empty). */
export function maxTime(keys) {
    return keys.reduce((max, key) => Math.max(max, key.time), 0);
}
//# sourceMappingURL=keys.js.map