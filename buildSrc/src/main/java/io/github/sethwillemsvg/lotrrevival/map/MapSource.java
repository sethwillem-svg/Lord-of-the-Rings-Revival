package io.github.sethwillemsvg.lotrrevival.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The map source under {@code src/main/map/}: see the README there for the format. */
final class MapSource {
    record Landmass(String name, Geometry.Polygon shape) {}

    record BiomePolygon(String name, String biome, Geometry.Polygon shape) {}

    /** A bump (positive) or dell (negative): Gaussian with sigma = radius / 3. */
    record Hill(String name, double u, double v, double height, double radius) {}

    /** A ridge: Gaussian across the line with sigma = width / 3; heights are per vertex. */
    record Ridge(String name, Geometry.Polyline line, double[] heights, double width) {}

    record River(String name, Geometry.Polyline line, double[] widths, double depth) {}

    record Lake(String name, Geometry.Polygon shape, double bed) {}

    record Landmark(String name, double u, double v, String expectBiome) {}

    /** A source coordinate with where it came from, for the in-window and canvas-edge checks. */
    record SourcePoint(String where, double u, double v) {}

    final List<Landmass> landmasses = new ArrayList<>();
    final List<BiomePolygon> biomePolygons = new ArrayList<>();
    final Map<String, Double> baseHeights = new LinkedHashMap<>();
    final List<Hill> hills = new ArrayList<>();
    final List<Ridge> ridges = new ArrayList<>();
    final List<River> rivers = new ArrayList<>();
    final List<Lake> lakes = new ArrayList<>();
    final List<Landmark> landmarks = new ArrayList<>();
    final List<SourcePoint> points = new ArrayList<>();

    static MapSource load(Path dir) {
        MapSource source = new MapSource();
        source.loadLandmass(dir.resolve("landmass.geojson"));
        source.loadBiomes(dir.resolve("biomes.geojson"));
        source.loadHeight(dir.resolve("height.geojson"));
        source.loadWater(dir.resolve("water.geojson"));
        source.loadLandmarks(dir.resolve("landmarks.geojson"));
        return source;
    }

    private void loadLandmass(Path file) {
        for (JsonObject feature : features(file)) {
            String name = name(feature);
            List<double[]> ring = polygon(file, feature);
            record(file, name, ring);
            landmasses.add(new Landmass(name, new Geometry.Polygon(ring)));
        }
    }

    private void loadBiomes(Path file) {
        JsonObject root = root(file);
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("base_height").entrySet()) {
            baseHeights.put(e.getKey(), e.getValue().getAsDouble());
        }
        for (JsonObject feature : features(root)) {
            String name = name(feature);
            String biome = props(feature).get("biome").getAsString();
            List<double[]> ring = polygon(file, feature);
            record(file, name, ring);
            biomePolygons.add(new BiomePolygon(name, biome, new Geometry.Polygon(ring)));
        }
    }

    private void loadHeight(Path file) {
        for (JsonObject feature : features(file)) {
            String name = name(feature);
            JsonObject props = props(feature);
            String type = geometry(feature).get("type").getAsString();
            switch (type) {
                case "Point" -> {
                    double[] c = coordinate(geometry(feature).getAsJsonArray("coordinates"));
                    points.add(new SourcePoint(file.getFileName() + ":" + name, c[0], c[1]));
                    hills.add(new Hill(
                            name, c[0], c[1], props.get("height").getAsDouble(), props.get("radius").getAsDouble()));
                }
                case "LineString" -> {
                    List<double[]> line = line(feature);
                    record(file, name, line);
                    double[] heights = new double[line.size()];
                    if (props.has("heights")) {
                        JsonArray array = props.getAsJsonArray("heights");
                        if (array.size() != heights.length) {
                            throw new IllegalStateException(name + ": heights must have one value per vertex");
                        }
                        for (int i = 0; i < heights.length; i++) {
                            heights[i] = array.get(i).getAsDouble();
                        }
                    } else {
                        java.util.Arrays.fill(heights, props.get("height").getAsDouble());
                    }
                    ridges.add(new Ridge(name, new Geometry.Polyline(line), heights, props.get("width").getAsDouble()));
                }
                default -> throw new IllegalStateException(file + ": " + name + ": unsupported geometry " + type);
            }
        }
    }

    private void loadWater(Path file) {
        for (JsonObject feature : features(file)) {
            String name = name(feature);
            JsonObject props = props(feature);
            String kind = props.get("kind").getAsString();
            switch (kind) {
                case "river" -> {
                    List<double[]> line = line(feature);
                    record(file, name, line);
                    double[] widths = new double[line.size()];
                    if (props.has("widths")) {
                        JsonArray array = props.getAsJsonArray("widths");
                        if (array.size() != widths.length) {
                            throw new IllegalStateException(name + ": widths must have one value per vertex");
                        }
                        for (int i = 0; i < widths.length; i++) {
                            widths[i] = array.get(i).getAsDouble();
                        }
                    } else {
                        java.util.Arrays.fill(widths, props.get("width").getAsDouble());
                    }
                    rivers.add(new River(name, new Geometry.Polyline(line), widths, props.get("depth").getAsDouble()));
                }
                case "lake" -> {
                    List<double[]> ring = polygon(file, feature);
                    record(file, name, ring);
                    lakes.add(new Lake(name, new Geometry.Polygon(ring), props.get("bed").getAsDouble()));
                }
                default -> throw new IllegalStateException(file + ": " + name + ": unknown water kind " + kind);
            }
        }
    }

    private void loadLandmarks(Path file) {
        for (JsonObject feature : features(file)) {
            String name = name(feature);
            double[] c = coordinate(geometry(feature).getAsJsonArray("coordinates"));
            points.add(new SourcePoint(file.getFileName() + ":" + name, c[0], c[1]));
            JsonObject props = props(feature);
            String expect = props.has("expect_biome") ? props.get("expect_biome").getAsString() : null;
            landmarks.add(new Landmark(name, c[0], c[1], expect));
        }
    }

    private void record(Path file, String name, List<double[]> coordinates) {
        for (double[] c : coordinates) {
            points.add(new SourcePoint(file.getFileName() + ":" + name, c[0], c[1]));
        }
    }

    // ---- JSON helpers ----

    private static JsonObject root(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read map source " + file, e);
        }
    }

    private static List<JsonObject> features(Path file) {
        return features(root(file));
    }

    private static List<JsonObject> features(JsonObject root) {
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("features")) {
            list.add(e.getAsJsonObject());
        }
        return list;
    }

    private static JsonObject props(JsonObject feature) {
        return feature.getAsJsonObject("properties");
    }

    private static JsonObject geometry(JsonObject feature) {
        return feature.getAsJsonObject("geometry");
    }

    private static String name(JsonObject feature) {
        return props(feature).get("name").getAsString();
    }

    private static double[] coordinate(JsonArray pair) {
        return new double[] {pair.get(0).getAsDouble(), pair.get(1).getAsDouble()};
    }

    private static List<double[]> line(JsonObject feature) {
        List<double[]> list = new ArrayList<>();
        for (JsonElement e : geometry(feature).getAsJsonArray("coordinates")) {
            list.add(coordinate(e.getAsJsonArray()));
        }
        return list;
    }

    /** First ring of a GeoJSON Polygon, without a repeated closing point. */
    private static List<double[]> polygon(Path file, JsonObject feature) {
        JsonObject geometry = geometry(feature);
        if (!"Polygon".equals(geometry.get("type").getAsString())) {
            throw new IllegalStateException(file + ": " + name(feature) + " must be a Polygon");
        }
        List<double[]> ring = new ArrayList<>();
        for (JsonElement e : geometry.getAsJsonArray("coordinates").get(0).getAsJsonArray()) {
            ring.add(coordinate(e.getAsJsonArray()));
        }
        if (ring.size() > 1) {
            double[] first = ring.get(0);
            double[] last = ring.get(ring.size() - 1);
            if (first[0] == last[0] && first[1] == last[1]) {
                ring.remove(ring.size() - 1);
            }
        }
        if (ring.size() < 3) {
            throw new IllegalStateException(file + ": " + name(feature) + " has fewer than 3 points");
        }
        return ring;
    }
}
