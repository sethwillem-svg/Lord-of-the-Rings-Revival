package io.github.sethwillemsvg.lotrrevival.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * The map validator from specs/world/dimension-and-mapgen.md. It fails the build when:
 * <ul>
 *   <li>the heightmap is not 6,400 x 8,000 16-bit grayscale or the biome map is not 3,200 x 4,000 RGB;
 *   <li>painted y plus the biome's amplitude exceeds 300 at any heightmap pixel;
 *   <li>a biome map colour is not in the table, or the table has a duplicate colour;
 *   <li>a pixel outside every {@code map_regions.json} window is not placeholder (8,000 / ocean);
 *   <li>a landmass, biome, height, water or landmark point, or the landing point, is outside a painted window
 *       or within 1,024 blocks of the canvas edge;
 *   <li>land is below sea level, a lake is above it, or land touches the rim of a window;
 *   <li>a landmark or the landing point is not in the biome it expects;
 *   <li>source biome ids are missing from the colour table.
 * </ul>
 * The committed-relief-image check from the spec belongs to datagen (the relief is built from the same biome
 * map there); see the PR notes.
 */
final class MapValidator {
    interface HeightGrid {
        int width();

        int height();

        /** Fills {@code out} (length = width) with the row's uint16 samples. */
        void row(int y, int[] out);
    }

    interface BiomeGrid {
        int width();

        int height();

        /** Fills {@code out} (length = width) with 0xRRGGBB colours. */
        void row(int y, int[] out);
    }

    /** Collects errors, keeping a few examples per category so a bad map does not print millions of lines. */
    static final class Report {
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, List<String>> examples = new LinkedHashMap<>();

        void add(String category, String detail) {
            counts.merge(category, 1, Integer::sum);
            List<String> list = examples.computeIfAbsent(category, k -> new ArrayList<>());
            if (list.size() < 5) {
                list.add(detail);
            }
        }

        boolean isEmpty() {
            return counts.isEmpty();
        }

        List<String> lines() {
            List<String> lines = new ArrayList<>();
            for (var e : counts.entrySet()) {
                lines.add(e.getKey() + " (" + e.getValue() + " problem" + (e.getValue() == 1 ? "" : "s") + "): "
                        + String.join("; ", examples.get(e.getKey())));
            }
            return lines;
        }

        boolean has(String category) {
            return counts.keySet().stream().anyMatch(key -> key.startsWith(category));
        }
    }

    private MapValidator() {}

    // ---- region file and source checks ----

    static Report validateRegions(List<Regions.Window> windows) {
        Report report = new Report();
        if (windows.isEmpty()) {
            report.add("regions", "map_regions.json lists no painted window");
        }
        for (Regions.Window w : windows) {
            if (w.minU() >= w.maxU() || w.minV() >= w.maxV()) {
                report.add("regions", w.id() + " is empty or inverted");
            }
            if (w.minU() % 2 != 0 || w.minV() % 2 != 0 || w.maxU() % 2 != 0 || w.maxV() % 2 != 0) {
                report.add("regions", w.id() + " must have even coordinates (the biome map is half-resolution)");
            }
            if (w.minU() < Canvas.EDGE_MARGIN_PX
                    || w.minV() < Canvas.EDGE_MARGIN_PX
                    || w.maxU() > Canvas.HEIGHT_WIDTH - Canvas.EDGE_MARGIN_PX
                    || w.maxV() > Canvas.HEIGHT_HEIGHT - Canvas.EDGE_MARGIN_PX) {
                report.add("regions", w.id() + " is within 1,024 blocks of the canvas edge");
            }
        }
        return report;
    }

    static Report validateSource(MapSource src, BiomeTable table, List<Regions.Window> windows, double[] landing) {
        Report report = new Report();
        for (MapSource.SourcePoint p : src.points) {
            checkPoint(report, windows, p.where(), p.u(), p.v());
        }
        checkPoint(report, windows, "landing point", landing[0], landing[1]);
        for (MapSource.BiomePolygon polygon : src.biomePolygons) {
            if (table.byId(polygon.biome()) == null) {
                report.add("unknown biome id", polygon.name() + " uses '" + polygon.biome() + "'");
            } else if (!src.baseHeights.containsKey(polygon.biome())) {
                report.add("missing base height", polygon.biome());
            }
        }
        for (MapSource.Landmark landmark : src.landmarks) {
            if (landmark.expectBiome() != null && table.byId(landmark.expectBiome()) == null) {
                report.add("unknown biome id", landmark.name() + " expects '" + landmark.expectBiome() + "'");
            }
        }
        for (String duplicate : table.duplicateColors) {
            report.add("duplicate colour", duplicate);
        }
        return report;
    }

    private static void checkPoint(Report report, List<Regions.Window> windows, String where, double u, double v) {
        if (u < Canvas.EDGE_MARGIN_PX
                || v < Canvas.EDGE_MARGIN_PX
                || u > Canvas.HEIGHT_WIDTH - Canvas.EDGE_MARGIN_PX
                || v > Canvas.HEIGHT_HEIGHT - Canvas.EDGE_MARGIN_PX) {
            report.add("point near or outside the canvas edge", where + " at (" + u + ", " + v + ")");
            return;
        }
        for (Regions.Window window : windows) {
            if (window.containsPoint(u, v)) {
                return;
            }
        }
        report.add("point outside every painted window", where + " at (" + u + ", " + v + ")");
    }

    // ---- image checks ----

    static Report validateImages(
            HeightGrid heights,
            BiomeGrid biomes,
            BiomeTable table,
            List<Regions.Window> windows,
            double[] landing,
            List<MapSource.Landmark> landmarks) {
        Report report = new Report();
        if (heights.width() != Canvas.HEIGHT_WIDTH || heights.height() != Canvas.HEIGHT_HEIGHT) {
            report.add(
                    "heightmap size",
                    "is " + heights.width() + " x " + heights.height() + ", expected " + Canvas.HEIGHT_WIDTH + " x "
                            + Canvas.HEIGHT_HEIGHT);
        }
        if (biomes.width() != Canvas.BIOME_WIDTH || biomes.height() != Canvas.BIOME_HEIGHT) {
            report.add(
                    "biome map size",
                    "is " + biomes.width() + " x " + biomes.height() + ", expected " + Canvas.BIOME_WIDTH + " x "
                            + Canvas.BIOME_HEIGHT);
        }
        if (!report.isEmpty()) {
            return report;
        }

        final int bw = Canvas.BIOME_WIDTH;
        final int bh = Canvas.BIOME_HEIGHT;
        // Index into table.biomes per biome pixel, -1 for unmapped.
        short[] biomeIndex = new short[bw * bh];
        int[] row = new int[bw];
        List<BiomeTable.Biome> list = table.biomes;
        Map<Integer, Integer> indexByColor = new java.util.HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            indexByColor.putIfAbsent(list.get(i).color(), i);
        }
        int oceanIndex = indexByColor.get(table.ocean().color());
        int lakeIndex = indexByColor.get(table.byId("lake").color());
        for (int by = 0; by < bh; by++) {
            biomes.row(by, row);
            for (int bx = 0; bx < bw; bx++) {
                Integer index = indexByColor.get(row[bx] & 0xFFFFFF);
                if (index == null) {
                    report.add("unmapped biome colour", String.format("#%06X at biome px (%d, %d)", row[bx] & 0xFFFFFF, bx, by));
                    biomeIndex[by * bw + bx] = -1;
                    continue;
                }
                biomeIndex[by * bw + bx] = index.shortValue();
                if (index != oceanIndex && !inAnyWindow(windows, bx * 2, by * 2)) {
                    report.add(
                            "paint outside every painted window",
                            list.get(index).id() + " at biome px (" + bx + ", " + by + ")");
                }
            }
        }

        int w = Canvas.HEIGHT_WIDTH;
        int[] top = new int[w];
        int[] bottom = new int[w];
        for (int by = 0; by < bh; by++) {
            heights.row(by * 2, top);
            heights.row(by * 2 + 1, bottom);
            for (int bx = 0; bx < bw; bx++) {
                int idx = biomeIndex[by * bw + bx];
                int x = bx * 2;
                int y = by * 2;
                int[] values = {top[x], top[x + 1], bottom[x], bottom[x + 1]};
                int[] xs = {x, x + 1, x, x + 1};
                int[] ys = {y, y, y + 1, y + 1};
                boolean inside = inAnyWindow(windows, x, y);
                double sum = 0;
                for (int k = 0; k < 4; k++) {
                    int value = values[k];
                    sum += value;
                    if (!inside) {
                        if (value != Canvas.PLACEHOLDER_HEIGHT_VALUE) {
                            report.add(
                                    "paint outside every painted window",
                                    "height " + value + " at heightmap px (" + xs[k] + ", " + ys[k] + ")");
                        }
                        continue;
                    }
                    if (idx >= 0) {
                        double surface = value * Canvas.VERTICAL_SCALE + list.get(idx).amplitude();
                        if (surface > Canvas.MAX_SURFACE) {
                            report.add(
                                    "painted y + amplitude exceeds " + Canvas.MAX_SURFACE,
                                    String.format(
                                            "%.1f + %d at heightmap px (%d, %d)",
                                            value * Canvas.VERTICAL_SCALE,
                                            list.get(idx).amplitude(),
                                            xs[k],
                                            ys[k]));
                        }
                    }
                    if (value != Canvas.PLACEHOLDER_HEIGHT_VALUE && nearWindowRim(windows, xs[k], ys[k])) {
                        report.add(
                                "land inside the 16 px rim of a window",
                                "height " + value + " at heightmap px (" + xs[k] + ", " + ys[k] + ")");
                    }
                }
                if (inside && idx >= 0 && idx != oceanIndex && idx != lakeIndex) {
                    double average = sum / 4 * Canvas.VERTICAL_SCALE;
                    if (average < Canvas.SEA_LEVEL) {
                        report.add(
                                "land below sea level",
                                String.format("%s averages y %.1f at biome px (%d, %d)", list.get(idx).id(), average, bx, by));
                    }
                } else if (inside && idx == lakeIndex) {
                    double average = sum / 4 * Canvas.VERTICAL_SCALE;
                    // Shore pixels half land, half lake average a little over 63; the rasterizer's land cut is 63.5.
                    if (average >= Canvas.SEA_LEVEL + 0.5) {
                        report.add(
                                "lake above sea level",
                                String.format("averages y %.1f at biome px (%d, %d)", average, bx, by));
                    }
                }
            }
        }

        // Named points must be in the biome the source says.
        checkExpected(report, biomeIndex, bw, list, "landing point", landing[0], landing[1], "shire");
        for (MapSource.Landmark landmark : landmarks) {
            if (landmark.expectBiome() != null) {
                checkExpected(report, biomeIndex, bw, list, landmark.name(), landmark.u(), landmark.v(), landmark.expectBiome());
            }
        }
        return report;
    }

    private static void checkExpected(
            Report report, short[] biomeIndex, int bw, List<BiomeTable.Biome> list, String name, double u, double v, String expected) {
        int bx = (int) Math.floor(u / 2);
        int by = (int) Math.floor(v / 2);
        if (bx < 0 || by < 0 || bx >= bw || by * bw + bx >= biomeIndex.length) {
            return; // reported as an out-of-canvas point
        }
        int idx = biomeIndex[by * bw + bx];
        String actual = idx < 0 ? "unmapped" : list.get(idx).id();
        if (!actual.equals(expected)) {
            report.add("named point in the wrong biome", name + " is " + actual + ", expected " + expected);
        }
    }

    private static boolean inAnyWindow(List<Regions.Window> windows, int u, int v) {
        for (Regions.Window window : windows) {
            if (window.containsPixel(u, v)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearWindowRim(List<Regions.Window> windows, int u, int v) {
        for (Regions.Window window : windows) {
            if (window.containsPixel(u, v)) {
                int edge = Math.min(Math.min(u - window.minU(), window.maxU() - 1 - u), Math.min(v - window.minV(), window.maxV() - 1 - v));
                return edge < Canvas.EDGE_MARGIN_PX;
            }
        }
        return false;
    }

    // ---- self test with deliberately broken fixtures ----

    private record FixtureHeights(int width, int height, IntBinaryOperator at) implements HeightGrid {
        @Override
        public void row(int y, int[] out) {
            for (int x = 0; x < width; x++) {
                out[x] = at.applyAsInt(x, y);
            }
        }
    }

    private record FixtureBiomes(int width, int height, IntBinaryOperator at) implements BiomeGrid {
        @Override
        public void row(int y, int[] out) {
            for (int x = 0; x < width; x++) {
                out[x] = at.applyAsInt(x, y);
            }
        }
    }

    /**
     * Proves the validator catches what it should: a clean fixture passes, and fixtures with a wrong size,
     * an unmapped colour, a height over 300, land below sea level and paint outside a window each fail.
     *
     * @return null when everything behaves, otherwise what went wrong
     */
    static String selfTest(BiomeTable table) {
        List<Regions.Window> windows = List.of(new Regions.Window("fixture", 1236, 1268, 2004, 1652));
        double[] landing = {1620, 1460};
        int ocean = table.ocean().color();
        int shire = table.byId("shire").color();
        IntBinaryOperator flat = (x, y) -> Canvas.PLACEHOLDER_HEIGHT_VALUE;
        IntBinaryOperator sea = (x, y) -> ocean;
        // A clean window: ocean rim, shire land (y 70) well inside, the landing point on it.
        IntBinaryOperator cleanHeights = (x, y) -> (x >= 1400 && x < 1800 && y >= 1400 && y < 1500)
                ? Canvas.toValue(70)
                : Canvas.PLACEHOLDER_HEIGHT_VALUE;
        IntBinaryOperator cleanBiomes = (x, y) -> (x >= 700 && x < 900 && y >= 700 && y < 750) ? shire : ocean;

        String problem = expect(
                "clean fixture",
                null,
                validateImages(
                        new FixtureHeights(6400, 8000, cleanHeights),
                        new FixtureBiomes(3200, 4000, cleanBiomes),
                        table,
                        windows,
                        landing,
                        List.of()));
        if (problem != null) {
            return problem;
        }
        problem = expect(
                "wrong size",
                "heightmap size",
                validateImages(
                        new FixtureHeights(100, 100, flat),
                        new FixtureBiomes(3200, 4000, sea),
                        table,
                        windows,
                        landing,
                        List.of()));
        if (problem != null) {
            return problem;
        }
        problem = expect(
                "unmapped colour",
                "unmapped biome colour",
                validateImages(
                        new FixtureHeights(6400, 8000, flat),
                        new FixtureBiomes(3200, 4000, (x, y) -> x == 700 && y == 700 ? 0x123456 : ocean),
                        table,
                        windows,
                        landing,
                        List.of()));
        if (problem != null) {
            return problem;
        }
        problem = expect(
                "height over 300",
                "painted y + amplitude exceeds",
                validateImages(
                        new FixtureHeights(6400, 8000, (x, y) -> x == 1500 && y == 1400 ? Canvas.toValue(299.5) : Canvas.PLACEHOLDER_HEIGHT_VALUE),
                        new FixtureBiomes(3200, 4000, (x, y) -> x == 750 && y == 700 ? shire : ocean),
                        table,
                        windows,
                        landing,
                        List.of()));
        if (problem != null) {
            return problem;
        }
        problem = expect(
                "land below sea level",
                "land below sea level",
                validateImages(
                        new FixtureHeights(6400, 8000, (x, y) -> x >= 1500 && x < 1502 && y >= 1400 && y < 1402 ? Canvas.toValue(50) : Canvas.PLACEHOLDER_HEIGHT_VALUE),
                        new FixtureBiomes(3200, 4000, (x, y) -> x == 750 && y == 700 ? shire : ocean),
                        table,
                        windows,
                        landing,
                        List.of()));
        if (problem != null) {
            return problem;
        }
        problem = expect(
                "paint outside a window",
                "paint outside every painted window",
                validateImages(
                        new FixtureHeights(6400, 8000, (x, y) -> x == 10 && y == 10 ? 9000 : Canvas.PLACEHOLDER_HEIGHT_VALUE),
                        new FixtureBiomes(3200, 4000, sea),
                        table,
                        windows,
                        landing,
                        List.of()));
        if (problem != null) {
            return problem;
        }
        problem = expect(
                "biome paint outside a window",
                "paint outside every painted window",
                validateImages(
                        new FixtureHeights(6400, 8000, flat),
                        new FixtureBiomes(3200, 4000, (x, y) -> x == 5 && y == 5 ? shire : ocean),
                        table,
                        windows,
                        landing,
                        List.of()));
        if (problem != null) {
            return problem;
        }
        return null;
    }

    private static String expect(String fixture, String category, Report report) {
        if (category == null) {
            return report.isEmpty() ? null : "validator self-test: fixture '" + fixture + "' should be clean but reported " + report.lines();
        }
        return report.has(category) ? null : "validator self-test: fixture '" + fixture + "' did not report '" + category + "'; got " + report.lines();
    }
}
