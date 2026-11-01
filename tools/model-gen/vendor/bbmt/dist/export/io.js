import fs from 'node:fs';
import path from 'node:path';
/** Create a directory (and parents) if it does not exist. */
export function ensureDir(dir) {
    fs.mkdirSync(dir, { recursive: true });
}
/** Write text, creating parent directories as needed. Returns the path written. */
export function writeText(file, contents) {
    ensureDir(path.dirname(file));
    fs.writeFileSync(file, contents, 'utf8');
    return file;
}
/** Write a pretty JSON file (2-space indent, trailing newline). */
export function writeJson(file, value) {
    return writeText(file, `${JSON.stringify(value, null, 2)}\n`);
}
/** Write binary data, creating parent directories as needed. */
export function writeBinary(file, data) {
    ensureDir(path.dirname(file));
    fs.writeFileSync(file, data);
    return file;
}
/** Convert `geometry.t_rex` / `T Rex` style names into a safe file stem. */
export function fileStem(name) {
    return name
        .replace(/^.*\//, '')
        .replace(/[^A-Za-z0-9_.-]+/g, '_')
        .replace(/^_+|_+$/g, '')
        .toLowerCase();
}
//# sourceMappingURL=io.js.map