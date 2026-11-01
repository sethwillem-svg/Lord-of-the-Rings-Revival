/**
 * A tiny YAML emitter. The plugin configs we generate are small nested maps of scalars and
 * string lists; a full YAML dependency would be overkill and would add a runtime dep to a
 * package that otherwise needs only `pngjs`.
 */
type Scalar = string | number | boolean | null;
export type YamlValue = Scalar | YamlValue[] | {
    [key: string]: YamlValue;
};
/** Serialise a plain JS value to YAML text (no document header). */
export declare function toYaml(value: YamlValue): string;
export {};
//# sourceMappingURL=yaml.d.ts.map