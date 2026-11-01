import type { Clip, Interpolation, Keyframe, LoopMode, Vec3 } from '../model/types.js';
export interface ClipOptions {
    name: string;
    keys: Keyframe[];
    length: number;
    loop?: LoopMode;
    framesPerSecond?: number;
    blendWeight?: string;
}
/** Assemble a clip from one or more keyframe lists, de-duplicated and sorted. */
export declare function clip(options: ClipOptions): Clip;
export interface PoseEntry {
    time: number;
    rotations?: Record<string, Vec3>;
    positions?: Record<string, Vec3>;
    interpolation?: Interpolation;
}
/**
 * Build keyframes from pose snapshots: a list of `{ time, rotations, positions }` frames.
 * This is how one-shot clips (attack, roar, jump) are written — the shape of the action is
 * readable in the spec instead of buried in curve maths.
 */
export declare function pose(entries: PoseEntry[], interpolation?: Interpolation): Keyframe[];
/** A clip whose keys are supplied and whose length is inferred from the keys. */
export declare function clipAuto(options: Omit<ClipOptions, 'length'> & {
    length?: number;
}): Clip;
//# sourceMappingURL=clip.d.ts.map