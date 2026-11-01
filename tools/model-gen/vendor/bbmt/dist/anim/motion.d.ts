import type { Keyframe } from '../model/types.js';
/**
 * Which bones a motion generator drives. Every field is optional, so a generator only
 * touches bones the model actually has — a legless model can still get a tail sway.
 */
export interface MotionRig {
    body?: string;
    chest?: string;
    head?: string;
    /** Neck chain, root first. */
    neck?: string[];
    /** Tail chain, root first. */
    tail?: string[];
    legs?: Array<{
        leg: string;
        shin?: string;
        foot?: string;
        toes?: string;
        phase: number;
    }>;
    arms?: Array<{
        arm: string;
        lower?: string;
        phase: number;
    }>;
}
/** The arrangement the toolkit's examples use, and a sensible default for a biped. */
export declare function defaultMotionRig(tailLength?: number): MotionRig;
/** A bone as declared in a spec — just enough to work out what drives what. */
export interface BoneHint {
    name: string;
    parent?: string | null;
}
/**
 * Work out a motion rig from the bones a spec actually declares.
 *
 * `defaultMotionRig()` names one specific biped — `body`, `chest`, `head`, `neck_01`,
 * `left_leg`, `tail_01` — so any model that names its bones differently gets *zero*
 * keyframes from every generator, silently. This reads the declared names instead, and
 * accepts both the side-first (`left_shin`) and side-last (`front_shin_left`) orders the
 * examples use, so a spec only has to pick sensible names. Anything it cannot read is
 * left out, and the generators skip bones the model lacks.
 *
 * Limb phases alternate by group *and* side, so a four-legged rig comes out on a
 * diagonal gait rather than a bound one.
 */
export declare function inferMotionRig(bones: BoneHint[]): MotionRig;
export interface LocomotionOptions {
    rig: MotionRig;
    length: number;
    /** Thigh swing amplitude in degrees. */
    stride: number;
    /** Peak knee flex. */
    knee: number;
    /** Vertical body bob in model units. */
    bob: number;
    /** Constant forward pitch of the body. */
    lean: number;
    /** Base tail lift. */
    tailLift: number;
    /** Arm swing amplitude. */
    armSwing: number;
    /** Head drop (negative lifts the head). */
    headDrop: number;
    samples?: number;
    kneeBase?: number;
}
/**
 * Locomotion cycle.
 *
 * A cube leg reads as a wind-up toy unless three things happen together: the knee flexes
 * while the leg swings, the foot counter-rotates to stay near the ground, and the toes grip
 * on the push-off. All three are derived here from one phase so a caller only tunes stride,
 * knee and bob.
 */
export declare function locomotion(options: LocomotionOptions): Keyframe[];
export interface TailWaveOptions {
    rig: MotionRig;
    length: number;
    samples?: number;
    /** Side-to-side amplitude at the base. */
    sway?: number;
    /** Extra sway per segment down the chain. */
    growth?: number;
    /** Vertical wobble amplitude. */
    lift?: number;
    /** Per-segment phase lag. */
    lag?: number;
    /** Frequency multiplier (2 = twice as fast). */
    frequency?: number;
}
/** A travelling wave down a chain — idle tail sway, or a menacing slow wave. */
export declare function tailWave(options: TailWaveOptions): Keyframe[];
export interface BreatheOptions {
    rig: MotionRig;
    length: number;
    samples?: number;
    /** Chest scale amplitude. */
    amount?: number;
    /** Body sink per breath. */
    sink?: number;
}
/** Breathing: chest expansion plus the small body sink that sells it. */
export declare function breathe(options: BreatheOptions): Keyframe[];
export interface HeadTurnOptions {
    rig: MotionRig;
    /** +1 = model's left, -1 = model's right. */
    direction: number;
    length?: number;
    samples?: number;
    /** Peak yaw at the neck root. */
    amount?: number;
}
/** Look left / look right: neck and head rotate together, tail counter-sways. */
export declare function headTurn(options: HeadTurnOptions): Keyframe[];
export interface IdleSwayOptions {
    rig: MotionRig;
    length: number;
    samples?: number;
    /** Body yaw amplitude. */
    sway?: number;
    /** Vertical sink. */
    sink?: number;
    headScan?: number;
}
/** A calm idle: weight shifts, tail drifts, head scans. Loops seamlessly. */
export declare function idleSway(options: IdleSwayOptions): Keyframe[];
//# sourceMappingURL=motion.d.ts.map