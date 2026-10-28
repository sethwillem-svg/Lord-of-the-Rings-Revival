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
import java.util.List;
import java.util.Map;

/**
 * Waypoint regions, waypoints and roads from {@code waypoints.geojson} and {@code roads.geojson}
 * (specs/world/waypoints-travel.md, "Map source"). Coordinates are heightmap pixels.
 */
final class TravelSource {
    record Region(String id, String name, List<String> biomes) {}

    /** {@code faction} is null when the waypoint belongs to nobody. */
    record Waypoint(String id, String name, String region, String faction, String lore, boolean hidden, double u, double v) {}

    /** {@code width} is the half-width in blocks. */
    record Road(String id, String name, double width, boolean showOnMap, List<double[]> points, List<String> through) {}

    static final double DEFAULT_ROAD_WIDTH = 4;

    final List<Region> regions = new ArrayList<>();
    final List<Waypoint> waypoints = new ArrayList<>();
    final List<Road> roads = new ArrayList<>();

    static TravelSource load(Path dir) {
        TravelSource source = new TravelSource();
        source.loadWaypoints(dir.resolve("waypoints.geojson"));
        source.loadRoads(dir.resolve("roads.geojson"));
        return source;
    }

    private void loadWaypoints(Path file) {
        JsonObject root = root(file);
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("regions").entrySet()) {
            JsonObject region = e.getValue().getAsJsonObject();
            List<String> biomes = new ArrayList<>();
            for (JsonElement biome : region.getAsJsonArray("biomes")) {
                biomes.add(biome.getAsString());
            }
            regions.add(new Region(e.getKey(), region.get("name").getAsString(), biomes));
        }
        for (JsonElement element : root.getAsJsonArray("features")) {
            JsonObject feature = element.getAsJsonObject();
            JsonObject props = feature.getAsJsonObject("properties");
            JsonObject geometry = feature.getAsJsonObject("geometry");
            if (!"Point".equals(geometry.get("type").getAsString())) {
                throw new IllegalStateException(file + ": " + props.get("id") + " must be a Point");
            }
            JsonArray c = geometry.getAsJsonArray("coordinates");
            waypoints.add(new Waypoint(
                    props.get("id").getAsString(),
                    props.get("name").getAsString(),
                    props.get("region").getAsString(),
                    props.has("faction") ? props.get("faction").getAsString() : null,
                    props.get("lore").getAsString(),
                    props.has("hidden") && props.get("hidden").getAsBoolean(),
                    c.get(0).getAsDouble(),
                    c.get(1).getAsDouble()));
        }
    }

    private void loadRoads(Path file) {
        for (JsonElement element : root(file).getAsJsonArray("features")) {
            JsonObject feature = element.getAsJsonObject();
            JsonObject props = feature.getAsJsonObject("properties");
            JsonObject geometry = feature.getAsJsonObject("geometry");
            if (!"LineString".equals(geometry.get("type").getAsString())) {
                throw new IllegalStateException(file + ": " + props.get("id") + " must be a LineString");
            }
            List<double[]> points = new ArrayList<>();
            for (JsonElement p : geometry.getAsJsonArray("coordinates")) {
                JsonArray pair = p.getAsJsonArray();
                points.add(new double[] {pair.get(0).getAsDouble(), pair.get(1).getAsDouble()});
            }
            List<String> through = new ArrayList<>();
            if (props.has("through")) {
                for (JsonElement id : props.getAsJsonArray("through")) {
                    through.add(id.getAsString());
                }
            }
            roads.add(new Road(
                    props.get("id").getAsString(),
                    props.get("name").getAsString(),
                    props.has("width") ? props.get("width").getAsDouble() : DEFAULT_ROAD_WIDTH,
                    !props.has("show_on_map") || props.get("show_on_map").getAsBoolean(),
                    points,
                    through));
        }
    }

    private static JsonObject root(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read map source " + file, e);
        }
    }
}
