import { hexToRgb, mixRgb, rgbToHex, shadeRgb } from '../util/math.js';
/**
 * Build a discrete light→shadow ramp around a base colour.
 *
 * Minecraft art reads well when shading is made of a small number of flat, clearly
 * separated colour steps — not a smooth gradient and definitely not a dither pattern.
 * Everything in this toolkit that "shades" does it by picking from a ramp like this.
 */
export function ramp(base, options = {}) {
    const rgb = typeof base === 'string' ? hexToRgb(base) : base;
    const steps = Math.max(2, Math.round(options.steps ?? 3));
    const contrast = options.contrast ?? 0.42;
    const bias = options.bias ?? 0.5;
    const out = [];
    for (let i = 0; i < steps; i += 1) {
        const t = steps === 1 ? 0 : i / (steps - 1);
        // t=0 → darkest shade, t=1 → lightest tint.
        const amount = (t - bias) * 2 * contrast;
        out.push(shadeRgb(rgb, amount));
    }
    return out;
}
/** Wrap an RGB triple as an opaque RGBA. */
export const rgba = (rgb, alpha = 255) => [rgb[0], rgb[1], rgb[2], alpha];
/**
 * Named palette. A texture's colours should be a deliberate list, not whatever the noise
 * produced; registering them here gives the metrics something to count and gives the
 * painter named handles (`palette.dark('skin')`) instead of magic RGB tuples.
 */
export class Palette {
    entries = new Map();
    define(name, color, options = {}) {
        const base = typeof color === 'string' ? hexToRgb(color) : color;
        const entry = { name, base, steps: ramp(base, options) };
        this.entries.set(name, entry);
        return entry;
    }
    get(name) {
        const entry = this.entries.get(name);
        if (!entry)
            throw new Error(`palette: unknown colour "${name}"`);
        return entry;
    }
    has(name) {
        return this.entries.has(name);
    }
    /** Step index 0 = darkest, last = lightest. Index is clamped, so loops are safe. */
    shade(name, index) {
        const entry = this.get(name);
        const clamped = Math.min(Math.max(Math.round(index), 0), entry.steps.length - 1);
        return entry.steps[clamped];
    }
    dark(name) {
        return rgba(this.shade(name, 0));
    }
    baseRgba(name) {
        return rgba(this.get(name).base);
    }
    light(name) {
        const entry = this.get(name);
        return rgba(entry.steps[entry.steps.length - 1]);
    }
    /** Every distinct step across every entry — what the palette-size metric counts. */
    allSteps() {
        const seen = new Map();
        for (const entry of this.entries.values()) {
            for (const step of entry.steps)
                seen.set(rgbToHex(step), step);
        }
        return [...seen.values()];
    }
    size() {
        return this.allSteps().length;
    }
    names() {
        return [...this.entries.keys()];
    }
}
/** Blend a ramp step toward another colour, e.g. tinting scales toward a darker back. */
export function tint(step, toward, amount) {
    return mixRgb(step, toward, amount);
}
//# sourceMappingURL=palette.js.map