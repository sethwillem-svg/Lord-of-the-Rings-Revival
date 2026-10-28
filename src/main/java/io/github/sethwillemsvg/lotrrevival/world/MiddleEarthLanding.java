package io.github.sethwillemsvg.lotrrevival.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads the landing point from {@code data/lotrrevival/lotrrevival/middle_earth.json}. */
public final class MiddleEarthLanding {
    /** Block x/z of the landing point. */
    public record Point(int x, int z) {}

    private static final ResourceLocation FILE = LOTRRevival.id("lotrrevival/middle_earth.json");

    private MiddleEarthLanding() {}

    /** The landing point, or block (0, 0) when the file is missing or malformed. */
    public static Point load(ResourceManager resources) {
        Optional<Point> point = Optional.empty();
        try {
            var resource = resources.getResource(FILE);
            if (resource.isPresent()) {
                try (BufferedReader reader = resource.get().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    var pixel = json.getAsJsonArray("landing_point");
                    point = Optional.of(new Point(
                            (int) Math.round(MiddleEarthMap.blockX(pixel.get(0).getAsDouble())),
                            (int) Math.round(MiddleEarthMap.blockZ(pixel.get(1).getAsDouble()))));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOTRRevival.LOGGER.error("Could not read Middle-earth landing point {}", FILE, e);
        }
        return point.orElse(new Point(0, 0));
    }
}
