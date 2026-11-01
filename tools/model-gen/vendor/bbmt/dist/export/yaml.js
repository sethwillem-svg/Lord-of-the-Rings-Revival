/**
 * A tiny YAML emitter. The plugin configs we generate are small nested maps of scalars and
 * string lists; a full YAML dependency would be overkill and would add a runtime dep to a
 * package that otherwise needs only `pngjs`.
 */
function quoteIfNeeded(value) {
    if (value === '')
        return "''";
    if (/^[A-Za-z0-9_./-]+$/.test(value) && !/^(true|false|null|yes|no)$/i.test(value))
        return value;
    return `'${value.replace(/'/g, "''")}'`;
}
function scalarToString(value) {
    if (value === null)
        return 'null';
    if (typeof value === 'string')
        return quoteIfNeeded(value);
    return String(value);
}
function emit(value, indent, lines) {
    const pad = ' '.repeat(indent);
    if (Array.isArray(value)) {
        for (const item of value) {
            if (item !== null && typeof item === 'object') {
                lines.push(`${pad}-`);
                emit(item, indent + 2, lines);
            }
            else {
                lines.push(`${pad}- ${scalarToString(item)}`);
            }
        }
        return;
    }
    if (value !== null && typeof value === 'object') {
        for (const [key, child] of Object.entries(value)) {
            if (Array.isArray(child)) {
                if (child.length === 0) {
                    lines.push(`${pad}${key}: []`);
                }
                else {
                    lines.push(`${pad}${key}:`);
                    emit(child, indent + 2, lines);
                }
            }
            else if (child !== null && typeof child === 'object') {
                lines.push(`${pad}${key}:`);
                emit(child, indent + 2, lines);
            }
            else {
                lines.push(`${pad}${key}: ${scalarToString(child)}`);
            }
        }
        return;
    }
    lines.push(`${pad}${scalarToString(value)}`);
}
/** Serialise a plain JS value to YAML text (no document header). */
export function toYaml(value) {
    const lines = [];
    emit(value, 0, lines);
    return `${lines.join('\n')}\n`;
}
//# sourceMappingURL=yaml.js.map