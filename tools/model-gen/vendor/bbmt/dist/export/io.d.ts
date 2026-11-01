/** Create a directory (and parents) if it does not exist. */
export declare function ensureDir(dir: string): void;
/** Write text, creating parent directories as needed. Returns the path written. */
export declare function writeText(file: string, contents: string): string;
/** Write a pretty JSON file (2-space indent, trailing newline). */
export declare function writeJson(file: string, value: unknown): string;
/** Write binary data, creating parent directories as needed. */
export declare function writeBinary(file: string, data: Uint8Array): string;
/** Convert `geometry.t_rex` / `T Rex` style names into a safe file stem. */
export declare function fileStem(name: string): string;
//# sourceMappingURL=io.d.ts.map