package io.github.sethwillemsvg.lotrrevival.map;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the vector map source into the heightmap and biome map.
 *
 * <p>Only the painted window is computed (the pilot is 768 x 384 px); everything else on the fixed
 * 6,400 x 8,000 canvas is the placeholder ocean. The result is a pure function of the source files, with no
 * randomness, so the images are identical on every build.
 *
 * <p>Pipeline, all at heightmap resolution inside the window:
 * <ol>
 *   <li>Land mask from the landmass polygons, lake mask from the lake polygons.
 *   <li>Land height: biome base heights blended with a Gaussian (land only), plus hills and ridges, fading
 *       to just above sea level over 5 px inland of any water.
 *   <li>River valleys: land near a river is lowered to y 65 (the river_cut ticket carves the channel).
 *   <li>Water bed: sea floor y 40 over 10 px offshore, lake beds over 3 px.
 *   <li>Biome map: one pixel per 2 x 2 heightmap pixels. Land if the block averages at least y 63.5,
 *       then the top-most biome polygon at its centre; otherwise lake or ocean.
 * </ol>
 */
final class Rasterizer {
    static final double COAST_RAMP_PX = 5.0;
    static final double SEA_RAMP_PX = 10.0;
    static final double LAKE_RAMP_PX = 3.0;
    static final double BASE_BLUR_SIGMA = 4.0;
    static final double VALLEY_Y = 65.0;
    static final double VALLEY_CORE_PX = 1.5;
    static final double VALLEY_BANK_PX = 3.0;
    /** Water surface neighbours: the lowest dry land pixel is just over y 64, the first water pixel here. */
    static final double SHORE_WATER_Y = 62.5;

    /** Output of {@link #rasterize}: the painted window of each image. */
    static final class Raster {
        final int x0;
        final int y0;
        final int width;
        final int height;
        /** Heightmap values (uint16), row-major over the window. */
        final int[] heights;
        /** Biome map RGB (0xRRGGBB), row-major over the biome-pixel window. */
        final int[] biomes;

        Raster(int x0, int y0, int width, int height, int[] heights, int[] biomes) {
            this.x0 = x0;
            this.y0 = y0;
            this.width = width;
            this.height = height;
            this.heights = heights;
            this.biomes = biomes;
        }

        int biomeX0() {
            return x0 / 2;
        }

        int biomeY0() {
            return y0 / 2;
        }

        int biomeWidth() {
            return width / 2;
        }

        int biomeHeight() {
            return height / 2;
        }
    }

    private Rasterizer() {}

    static Raster rasterize(MapSource src, BiomeTable table, List<Regions.Window> windows) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (Regions.Window window : windows) {
            x0 = Math.min(x0, window.minU());
            y0 = Math.min(y0, window.minV());
            x1 = Math.max(x1, window.maxU());
            y1 = Math.max(y1, window.maxV());
        }
        if (windows.isEmpty() || x0 % 2 != 0 || y0 % 2 != 0 || x1 % 2 != 0 || y1 % 2 != 0) {
            throw new IllegalStateException("Painted windows must exist and have even pixel coordinates");
        }
        int w = x1 - x0;
        int h = y1 - y0;
        int n = w * h;

        checkBiomeIds(src, table);

        // ---- masks ----
        boolean[] land = new boolean[n];
        boolean[] lake = new boolean[n];
        double[] lakeBed = new double[n];
        int[] poly = new int[n];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int idx = j * w + i;
                double u = x0 + i + 0.5;
                double v = y0 + j + 0.5;
                for (MapSource.Landmass landmass : src.landmasses) {
                    if (landmass.shape().contains(u, v)) {
                        land[idx] = true;
                        break;
                    }
                }
                if (land[idx]) {
                    for (MapSource.Lake l : src.lakes) {
                        if (l.shape().contains(u, v)) {
                            lake[idx] = true;
                            lakeBed[idx] = l.bed();
                            break;
                        }
                    }
                }
                poly[idx] = topPolygon(src, u, v);
            }
        }
        boolean[] water = new boolean[n];
        boolean[] dryLand = new boolean[n];
        for (int i = 0; i < n; i++) {
            water[i] = !land[i] || lake[i];
            dryLand[i] = land[i] && !lake[i];
        }
        double[] distToWater = distanceTo(water, w, h);
        double[] distToLand = distanceTo(dryLand, w, h);

        // ---- base height ----
        double[] weighted = new double[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            if (dryLand[i] && poly[i] >= 0) {
                weight[i] = 1;
                weighted[i] = src.baseHeights.get(src.biomePolygons.get(poly[i]).biome());
            }
        }
        double[] blurredHeight = blur(weighted, w, h, BASE_BLUR_SIGMA);
        double[] blurredWeight = blur(weight, w, h, BASE_BLUR_SIGMA);

        // ---- final heights ----
        int[] heights = new int[n];
        double[] param = new double[1];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int idx = j * w + i;
                double u = x0 + i + 0.5;
                double v = y0 + j + 0.5;
                double y;
                if (dryLand[idx]) {
                    double base = blurredWeight[idx] > 1e-9 ? blurredHeight[idx] / blurredWeight[idx] : 66.0;
                    double target = base;
                    for (MapSource.Hill hill : src.hills) {
                        double sigma = hill.radius() / 3.0;
                        double du = u - hill.u();
                        double dv = v - hill.v();
                        double d2 = du * du + dv * dv;
                        if (d2 < hill.radius() * hill.radius()) {
                            target += hill.height() * Math.exp(-d2 / (2 * sigma * sigma));
                        }
                    }
                    for (MapSource.Ridge ridge : src.ridges) {
                        double d = ridge.line().distance(u, v, param);
                        if (d < ridge.width()) {
                            double sigma = ridge.width() / 3.0;
                            target += heightAlong(ridge.heights(), param[0]) * Math.exp(-d * d / (2 * sigma * sigma));
                        }
                    }
                    target = Math.max(target, 64.5);
                    double t = Geometry.smoothstep((distToWater[idx] - 0.5) / COAST_RAMP_PX);
                    y = 64.1 + (target - 64.1) * t;

                    double valley = 0;
                    for (MapSource.River river : src.rivers) {
                        double d = river.line().distance(u, v, null);
                        valley = Math.max(valley, 1 - Geometry.smoothstep((d - VALLEY_CORE_PX) / VALLEY_BANK_PX));
                    }
                    if (valley > 0) {
                        double carved = Math.min(y, VALLEY_Y);
                        y = y * (1 - valley) + carved * valley;
                    }
                    y = Math.max(y, 64.0);
                } else {
                    boolean isLake = lake[idx];
                    double bed = isLake ? lakeBed[idx] : Canvas.SEA_FLOOR;
                    double ramp = isLake ? LAKE_RAMP_PX : SEA_RAMP_PX;
                    double s = Geometry.smoothstep((distToLand[idx] - 0.5) / ramp);
                    y = SHORE_WATER_Y - (SHORE_WATER_Y - bed) * s;
                }
                heights[idx] = Canvas.toValue(y);
            }
        }

        // ---- biome map ----
        int bw = w / 2;
        int bh = h / 2;
        int[] biomes = new int[bw * bh];
        List<String> problems = new ArrayList<>();
        for (int bj = 0; bj < bh; bj++) {
            for (int bi = 0; bi < bw; bi++) {
                int i = bi * 2;
                int j = bj * 2;
                int sum = heights[j * w + i] + heights[j * w + i + 1] + heights[(j + 1) * w + i] + heights[(j + 1) * w + i + 1];
                double average = sum / 4.0 * Canvas.VERTICAL_SCALE;
                int lakeCount = (lake[j * w + i] ? 1 : 0)
                        + (lake[j * w + i + 1] ? 1 : 0)
                        + (lake[(j + 1) * w + i] ? 1 : 0)
                        + (lake[(j + 1) * w + i + 1] ? 1 : 0);
                int color;
                if (average >= Canvas.SEA_LEVEL + 0.5) {
                    int p = topPolygon(src, x0 + i + 1.0, y0 + j + 1.0);
                    if (p < 0) {
                        if (problems.size() < 10) {
                            problems.add(String.format(
                                    "land at heightmap px (%d, %d) is not covered by any biome polygon",
                                    x0 + i + 1, y0 + j + 1));
                        }
                        p = 0;
                    }
                    color = table.byId(src.biomePolygons.get(p).biome()).color();
                } else if (lakeCount >= 2) {
                    color = table.byId("lake").color();
                } else {
                    color = table.byId("ocean").color();
                }
                biomes[bj * bw + bi] = color;
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Map source problems:\n  " + String.join("\n  ", problems));
        }
        return new Raster(x0, y0, w, h, heights, biomes);
    }

    private static void checkBiomeIds(MapSource src, BiomeTable table) {
        for (MapSource.BiomePolygon polygon : src.biomePolygons) {
            BiomeTable.Biome biome = table.byId(polygon.biome());
            if (biome == null) {
                throw new IllegalStateException(
                        "Biome polygon '" + polygon.name() + "' uses biome '" + polygon.biome() + "' which is not in the colour table");
            }
            if (polygon.biome().equals("ocean") || polygon.biome().equals("lake")) {
                throw new IllegalStateException("Biome polygon '" + polygon.name() + "' paints water; use landmass and lake features");
            }
            if (!src.baseHeights.containsKey(polygon.biome())) {
                throw new IllegalStateException("Biome '" + polygon.biome() + "' has no entry in biomes.geojson base_height");
            }
        }
        if (table.byId("ocean") == null || table.byId("lake") == null) {
            throw new IllegalStateException("The colour table needs ocean and lake");
        }
    }

    private static int topPolygon(MapSource src, double u, double v) {
        for (int p = src.biomePolygons.size() - 1; p >= 0; p--) {
            if (src.biomePolygons.get(p).shape().contains(u, v)) {
                return p;
            }
        }
        return -1;
    }

    private static double heightAlong(double[] heights, double param) {
        int i = (int) Math.floor(param);
        if (i >= heights.length - 1) {
            return heights[heights.length - 1];
        }
        double f = param - i;
        return heights[i] * (1 - f) + heights[i + 1] * f;
    }

    // ---- distance transform (Felzenszwalb & Huttenlocher) ----

    private static final double INF = 1e20;

    /** Euclidean distance in pixels from every pixel to the nearest seed pixel. */
    static double[] distanceTo(boolean[] seed, int w, int h) {
        double[] f = new double[w * h];
        for (int i = 0; i < f.length; i++) {
            f[i] = seed[i] ? 0 : INF;
        }
        int max = Math.max(w, h);
        double[] column = new double[max];
        double[] out = new double[max];
        int[] v = new int[max];
        double[] z = new double[max + 1];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                column[y] = f[y * w + x];
            }
            transform1d(column, h, out, v, z);
            for (int y = 0; y < h; y++) {
                f[y * w + x] = out[y];
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                column[x] = f[y * w + x];
            }
            transform1d(column, w, out, v, z);
            for (int x = 0; x < w; x++) {
                f[y * w + x] = Math.sqrt(out[x]);
            }
        }
        return f;
    }

    private static void transform1d(double[] f, int n, double[] d, int[] v, double[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = -INF;
        z[1] = INF;
        for (int q = 1; q < n; q++) {
            double s = intersection(f, q, v[k]);
            while (s <= z[k]) {
                k--;
                s = intersection(f, q, v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = INF;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) {
                k++;
            }
            double dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }

    private static double intersection(double[] f, int q, int p) {
        return ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * q - 2.0 * p);
    }

    // ---- Gaussian blur ----

    private static double[] blur(double[] src, int w, int h, double sigma) {
        int radius = (int) Math.ceil(sigma * 3);
        double[] kernel = new double[radius * 2 + 1];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        double[] tmp = new double[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double acc = 0;
                for (int k = -radius; k <= radius; k++) {
                    int xx = x + k;
                    if (xx >= 0 && xx < w) {
                        acc += kernel[k + radius] * src[y * w + xx];
                    }
                }
                tmp[y * w + x] = acc;
            }
        }
        double[] result = new double[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double acc = 0;
                for (int k = -radius; k <= radius; k++) {
                    int yy = y + k;
                    if (yy >= 0 && yy < h) {
                        acc += kernel[k + radius] * tmp[yy * w + x];
                    }
                }
                result[y * w + x] = acc;
            }
        }
        return result;
    }

    // ---- image output ----

    static void writeImages(Raster raster, Path outputDir) throws IOException {
        Path heightmap = outputDir.resolve("data/lotrrevival/novoatlas/heightmap/middle_earth.png");
        Path biomeMap = outputDir.resolve("data/lotrrevival/novoatlas/biome_map/middle_earth.png");

        byte[] blankHeightRow = new byte[Canvas.HEIGHT_WIDTH * 2];
        for (int x = 0; x < Canvas.HEIGHT_WIDTH; x++) {
            blankHeightRow[x * 2] = (byte) (Canvas.PLACEHOLDER_HEIGHT_VALUE >> 8);
            blankHeightRow[x * 2 + 1] = (byte) Canvas.PLACEHOLDER_HEIGHT_VALUE;
        }
        PngWriter.writeGray16(heightmap, Canvas.HEIGHT_WIDTH, Canvas.HEIGHT_HEIGHT, (y, raw) -> {
            System.arraycopy(blankHeightRow, 0, raw, 0, raw.length);
            int j = y - raster.y0;
            if (j >= 0 && j < raster.height) {
                for (int i = 0; i < raster.width; i++) {
                    int value = raster.heights[j * raster.width + i];
                    int px = (raster.x0 + i) * 2;
                    raw[px] = (byte) (value >> 8);
                    raw[px + 1] = (byte) value;
                }
            }
        });

        byte[] blankBiomeRow = new byte[Canvas.BIOME_WIDTH * 3];
        for (int x = 0; x < Canvas.BIOME_WIDTH; x++) {
            blankBiomeRow[x * 3] = (byte) (Canvas.OCEAN_COLOR >> 16);
            blankBiomeRow[x * 3 + 1] = (byte) (Canvas.OCEAN_COLOR >> 8);
            blankBiomeRow[x * 3 + 2] = (byte) Canvas.OCEAN_COLOR;
        }
        PngWriter.writeRgb8(biomeMap, Canvas.BIOME_WIDTH, Canvas.BIOME_HEIGHT, (y, raw) -> {
            System.arraycopy(blankBiomeRow, 0, raw, 0, raw.length);
            int j = y - raster.biomeY0();
            if (j >= 0 && j < raster.biomeHeight()) {
                for (int i = 0; i < raster.biomeWidth(); i++) {
                    int rgb = raster.biomes[j * raster.biomeWidth() + i];
                    int px = (raster.biomeX0() + i) * 3;
                    raw[px] = (byte) (rgb >> 16);
                    raw[px + 1] = (byte) (rgb >> 8);
                    raw[px + 2] = (byte) rgb;
                }
            }
        });
    }
}
