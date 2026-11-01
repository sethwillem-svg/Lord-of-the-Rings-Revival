import { PNG } from 'pngjs';
import { clamp } from '../util/math.js';
export const TRANSPARENT = [0, 0, 0, 0];
export const WHITE = [255, 255, 255, 255];
export const BLACK = [0, 0, 0, 255];
/**
 * A plain RGBA raster.
 *
 * Deliberately not built on a 2D canvas library: the texture sizes here are tiny (16–256
 * px), the operations are pixel-exact, and staying on a `Uint8Array` means a rebuild is
 * bit-identical on every machine. `pngjs` is used only to encode/decode PNG bytes.
 */
export class Canvas {
    width;
    height;
    data;
    constructor(width, height, fill = TRANSPARENT) {
        if (width <= 0 || height <= 0)
            throw new Error(`canvas: bad size ${width}×${height}`);
        this.width = width;
        this.height = height;
        this.data = new Uint8Array(width * height * 4);
        if (fill[3] !== 0)
            this.fill(fill);
    }
    index(x, y) {
        return (y * this.width + x) * 4;
    }
    inBounds(x, y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height;
    }
    get(x, y) {
        if (!this.inBounds(x, y))
            return [...TRANSPARENT];
        const i = this.index(x, y);
        return [this.data[i], this.data[i + 1], this.data[i + 2], this.data[i + 3]];
    }
    set(x, y, color) {
        if (!this.inBounds(x, y))
            return;
        const i = this.index(x, y);
        this.data[i] = clamp(Math.round(color[0]), 0, 255);
        this.data[i + 1] = clamp(Math.round(color[1]), 0, 255);
        this.data[i + 2] = clamp(Math.round(color[2]), 0, 255);
        this.data[i + 3] = clamp(Math.round(color[3]), 0, 255);
    }
    fill(color) {
        for (let y = 0; y < this.height; y += 1) {
            for (let x = 0; x < this.width; x += 1)
                this.set(x, y, color);
        }
    }
    /** Fill a half-open rectangle, clipped to the canvas. */
    rect(x1, y1, x2, y2, color) {
        const left = Math.max(0, Math.min(x1, x2));
        const right = Math.min(this.width, Math.max(x1, x2));
        const top = Math.max(0, Math.min(y1, y2));
        const bottom = Math.min(this.height, Math.max(y1, y2));
        for (let y = top; y < bottom; y += 1) {
            for (let x = left; x < right; x += 1)
                this.set(x, y, color);
        }
    }
    /** Stroke a 1px rectangle outline (drawn inside the bounds). */
    outlineRect(x1, y1, x2, y2, color) {
        for (let x = x1; x < x2; x += 1) {
            this.set(x, y1, color);
            this.set(x, y2 - 1, color);
        }
        for (let y = y1; y < y2; y += 1) {
            this.set(x1, y, color);
            this.set(x2 - 1, y, color);
        }
    }
    /** Bresenham line. */
    line(x0, y0, x1, y1, color) {
        let x = Math.round(x0);
        let y = Math.round(y0);
        const tx = Math.round(x1);
        const ty = Math.round(y1);
        const dx = Math.abs(tx - x);
        const dy = Math.abs(ty - y);
        const sx = x < tx ? 1 : -1;
        const sy = y < ty ? 1 : -1;
        let err = dx - dy;
        for (;;) {
            this.set(x, y, color);
            if (x === tx && y === ty)
                break;
            const e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    /** Filled ellipse centred on (cx, cy) with radii (rx, ry). */
    ellipse(cx, cy, rx, ry, color) {
        if (rx <= 0 || ry <= 0)
            return;
        for (let y = Math.floor(cy - ry); y <= Math.ceil(cy + ry); y += 1) {
            for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x += 1) {
                const nx = (x + 0.5 - cx) / rx;
                const ny = (y + 0.5 - cy) / ry;
                if (nx * nx + ny * ny <= 1)
                    this.set(x, y, color);
            }
        }
    }
    /** Copy another canvas in at (x, y). */
    blit(source, x, y) {
        for (let sy = 0; sy < source.height; sy += 1) {
            for (let sx = 0; sx < source.width; sx += 1) {
                this.set(x + sx, y + sy, source.get(sx, sy));
            }
        }
    }
    clone() {
        const copy = new Canvas(this.width, this.height);
        copy.data.set(this.data);
        return copy;
    }
    /** Mirror the left half onto the right (or right onto left) — classic skin symmetry. */
    mirrorHorizontal(from = 'left') {
        const half = Math.floor(this.width / 2);
        for (let y = 0; y < this.height; y += 1) {
            for (let x = 0; x < half; x += 1) {
                const sourceX = from === 'left' ? x : this.width - 1 - x;
                const targetX = from === 'left' ? this.width - 1 - x : x;
                this.set(targetX, y, this.get(sourceX, y));
            }
        }
    }
    toPng() {
        const png = new PNG({ width: this.width, height: this.height });
        png.data = Buffer.from(this.data);
        return new Uint8Array(PNG.sync.write(png));
    }
    static fromPng(bytes) {
        const png = PNG.sync.read(Buffer.from(bytes));
        const canvas = new Canvas(png.width, png.height);
        canvas.data.set(png.data);
        return canvas;
    }
    /** Distinct opaque colours, for metrics. */
    distinctColors() {
        const counts = new Map();
        for (let y = 0; y < this.height; y += 1) {
            for (let x = 0; x < this.width; x += 1) {
                const [r, g, b, a] = this.get(x, y);
                if (a === 0)
                    continue;
                const key = `${r},${g},${b}`;
                counts.set(key, (counts.get(key) ?? 0) + 1);
            }
        }
        return counts;
    }
}
//# sourceMappingURL=canvas.js.map