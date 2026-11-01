import { Model } from './model/model.js';
import type { FaceName, LoopMode, UvRect } from './model/types.js';
import { Atlas } from './texture/atlas.js';
import { type Vec3 } from './util/math.js';
import { type PoseEntry } from './anim/clip.js';
import { breathe, headTurn, idleSway, locomotion, tailWave, type MotionRig } from './anim/motion.js';
import type { Keyframe } from './model/types.js';
export interface MaterialSpec {
    /** Palette entry this material shades with. */
    palette?: string;
    /** Discrete bands across the island. 1 = flat. */
    steps?: number;
    contrast?: number;
    axis?: 'vertical' | 'horizontal';
    inverted?: boolean;
    /** Faint mottling amplitude; keep small. */
    grain?: number;
    /** Island size in pixels. Defaults to 16. */
    size?: number;
    /** Draw a light/dark bevel around the island. */
    bevel?: boolean;
}
export interface BoneSpec {
    name: string;
    pivot: Vec3;
    parent?: string | null;
    color?: number;
}
export interface CubeSpec {
    name?: string;
    from: Vec3;
    to: Vec3;
    bone: string;
    material?: string;
    faces?: Partial<Record<FaceName, UvRect>>;
    inflate?: number;
}
export type AnimationSpec = ({
    kind: 'locomotion';
    name: string;
} & Omit<Parameters<typeof locomotion>[0], 'rig'>) | ({
    kind: 'tailWave';
    name: string;
} & Omit<Parameters<typeof tailWave>[0], 'rig'>) | ({
    kind: 'breathe';
    name: string;
} & Omit<Parameters<typeof breathe>[0], 'rig'>) | ({
    kind: 'headTurn';
    name: string;
} & Omit<Parameters<typeof headTurn>[0], 'rig'>) | ({
    kind: 'idleSway';
    name: string;
} & Omit<Parameters<typeof idleSway>[0], 'rig'>) | {
    kind: 'poses';
    name: string;
    length: number;
    loop?: LoopMode;
    poses: PoseEntry[];
    keyframes?: Keyframe[];
};
export interface JsonSpec {
    name: string;
    resolution?: [number, number];
    identifier?: string;
    /** Named colours, `#rrggbb`. */
    palette: Record<string, string>;
    /** Material definitions. Defaults to one material per palette entry. */
    materials?: Record<string, MaterialSpec>;
    bones: BoneSpec[];
    cubes: CubeSpec[];
    animations?: AnimationSpec[];
    /** Override the motion-rig bone names used by the animation generators. */
    motionRig?: Partial<MotionRig>;
    createdAtlas?: boolean;
}
export interface ResolvedSpec {
    model: Model;
    atlas: Atlas;
    materials: Record<string, MaterialSpec>;
    warnings: string[];
}
/**
 * Turn a declarative JSON spec into a live `Model`.
 *
 * This exists so a model can be described entirely as data — the agent writes one JSON
 * object instead of a TypeScript program. Materials are the texture story: each material
 * claims a small atlas island painted with a discrete band ramp from the palette, and every
 * cube face assigned to it samples that island. Animations reference the built-in motion
 * generators by name, so a spec can describe a full locomotion cycle without curves.
 */
export declare function resolveSpec(spec: JsonSpec): ResolvedSpec;
//# sourceMappingURL=spec.d.ts.map