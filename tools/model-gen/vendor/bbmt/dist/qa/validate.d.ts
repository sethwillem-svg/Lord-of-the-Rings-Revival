import type { Model } from '../model/model.js';
export interface ValidationReport {
    errors: string[];
    warnings: string[];
    info: string[];
    ok: boolean;
}
export interface ValidateOptions {
    /** Treat warnings as failures. */
    strict?: boolean;
    /** Warn when a clip animates a bone with no cube under it. */
    warnOnEmptyBones?: boolean;
}
/**
 * Check a model before it is written.
 *
 * This is the cheap safety net that catches the mistakes a generator actually makes:
 * a UV window hanging off the texture, a face with no UV at all, an animation keyed to a
 * bone that was renamed, a clip that ended up empty, duplicate cube names (which would
 * collide on the same uuid). It is deliberately independent of the exporters so it can run
 * before anything touches the disk.
 */
export declare function validate(model: Model, options?: ValidateOptions): ValidationReport;
/** Throwing variant for build scripts that should stop on the first problem. */
export declare function assertValid(model: Model, options?: ValidateOptions): ValidationReport;
/** Render a report as a compact multi-line string for the console. */
export declare function formatReport(report: ValidationReport): string;
//# sourceMappingURL=validate.d.ts.map