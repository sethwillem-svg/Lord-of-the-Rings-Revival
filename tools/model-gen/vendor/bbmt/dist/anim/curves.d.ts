import { type Vec3 } from '../util/math.js';
import type { AnimationChannel, Interpolation, Keyframe } from '../model/types.js';
export declare const TAU: number;
/**
 * Sinusoid with a phase offset in turns (0..1) and a frequency multiplier.
 * `sin(0.25)` peaks a quarter of the way through the cycle.
 */
export declare const sin: (phase?: number, frequency?: number) => (t: number) => number;
export declare const cos: (phase?: number, frequency?: number) => (t: number) => number;
/** 0→1 ramp that resets each cycle. */
export declare const saw: (phase?: number) => (t: number) => number;
/** Triangle wave, -1→1. */
export declare const triangle: (phase?: number, frequency?: number) => (t: number) => number;
/** Smooth 0→1→0 bump, useful for one-shot accents inside a loop. */
export declare const bump: (center?: number, width?: number) => (t: number) => number;
/** Gaussian pulse — the shape used for whip/snap accents. */
export declare const pulse: (center?: number, width?: number) => (t: number) => number;
/** Smoothstep ease between two values. */
export declare const ease: (from: number, to: number) => (t: number) => number;
/** Upward ramp with a configurable power curve. */
export declare const rampUp: (power?: number) => (t: number) => number;
/** Negative decay — the tail "catching up" shape. */
export declare const decay: (rate?: number) => (t: number) => number;
export declare function frac(value: number): number;
export declare function wrap01(value: number): number;
/** Multiply a scalar curve into a constant vector. */
export declare const vec: (f: (t: number) => number) => ((t: number) => Vec3);
/**
 * Sample a 0..1 cycle and emit keyframes for one bone/channel.
 *
 * `fn` may return a scalar (broadcast across X/Y/Z) or a `Vec3`, and may return `null` to
 * skip a sample. Sampling `samples + 1` times includes both endpoints, which matters for a
 * loop: t=0 and t=1 must carry the same pose or the animation pops.
 */
export declare function cycle(bone: string, channel: AnimationChannel, length: number, samples: number, fn: (t: number) => number | Vec3 | null, interpolation?: Interpolation): Keyframe[];
/** Sample a cycle across several bones at once, sharing the phase function. */
export declare function cycleBones(bones: string[], channel: AnimationChannel, length: number, samples: number, fn: (bone: string, index: number, t: number) => number | Vec3 | null, interpolation?: Interpolation): Keyframe[];
/**
 * Chain follower: each bone lags the one before it, so a tail or a whip propagates motion
 * instead of every segment moving in lockstep.
 */
export declare function chain(bones: string[], length: number, samples: number, fn: (index: number, t: number, lag: number, bone: string) => Vec3, options?: {
    lag?: number;
    interpolation?: Interpolation;
}): Keyframe[];
/** Key data derived from a pose map at a single moment. */
export declare function pose(entries: Array<[number, Record<string, Vec3>, Record<string, Vec3>?, Interpolation?]>, options?: {
    defaultInterpolation?: Interpolation;
}): Keyframe[];
//# sourceMappingURL=curves.d.ts.map