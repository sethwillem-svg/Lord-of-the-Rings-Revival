package io.github.sethwillemsvg.lotrrevival.map;

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

/** Painted windows from {@code map_regions.json} and the landing point, in heightmap pixels. */
final class Regions {
    /** Heightmap pixels [minU, maxU) x [minV, maxV). */
    record Window(String id, int minU, int minV, int maxU, int maxV) {
        boolean containsPixel(int u, int v) {
            return u >= minU && u < maxU && v >= minV && v < maxV;
        }

        boolean containsPoint(double u, double v) {
            return u >= minU && u <= maxU && v >= minV && v <= maxV;
        }
    }

    private Regions() {}

    static List<Window> load(Path file) {
        List<Window> windows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("regions")) {
                JsonObject region = e.getAsJsonObject();
                var min = region.getAsJsonArray("min");
                var max = region.getAsJsonArray("max");
                windows.add(new Window(
                        region.get("id").getAsString(),
                        min.get(0).getAsInt(),
                        min.get(1).getAsInt(),
                        max.get(0).getAsInt(),
                        max.get(1).getAsInt()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        return windows;
    }

    /** Landing point from {@code data/lotrrevival/lotrrevival/middle_earth.json}. */
    static double[] loadLanding(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var pixel = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("landing_point");
            return new double[] {pixel.get(0).getAsDouble(), pixel.get(1).getAsDouble()};
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
