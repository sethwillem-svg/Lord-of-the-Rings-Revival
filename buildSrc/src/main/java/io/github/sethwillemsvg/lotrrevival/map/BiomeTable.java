package io.github.sethwillemsvg.lotrrevival.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The biome colour table, read from {@code world/MiddleEarthBiomes.java} so that file stays the single source
 * of truth (the spec says so). It looks for {@code NAME = key("id")} constants and
 * {@code new Entry(NAME, 0xRRGGBB, amplitude)} rows.
 */
final class BiomeTable {
    record Biome(String id, int color, int amplitude) {}

    private static final Pattern KEY = Pattern.compile("ResourceKey<Biome>\\s+(\\w+)\\s*=\\s*key\\(\"([a-z0-9_]+)\"\\)");
    private static final Pattern ENTRY =
            Pattern.compile("new\\s+Entry\\(\\s*(\\w+)\\s*,\\s*0x([0-9A-Fa-f]{6})\\s*,\\s*(\\d+)\\s*\\)");

    final List<Biome> biomes;
    private final Map<String, Biome> byId = new LinkedHashMap<>();
    private final Map<Integer, Biome> byColor = new HashMap<>();
    /** Duplicate colours found while parsing; the validator reports them. */
    final List<String> duplicateColors = new ArrayList<>();

    BiomeTable(List<Biome> biomes) {
        this.biomes = biomes;
        for (Biome biome : biomes) {
            byId.put(biome.id(), biome);
            Biome previous = byColor.put(biome.color(), biome);
            if (previous != null) {
                duplicateColors.add(String.format(
                        "colour #%06X is used by both %s and %s", biome.color(), previous.id(), biome.id()));
            }
        }
    }

    static BiomeTable parse(Path javaFile) {
        String source;
        try {
            source = Files.readString(javaFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the colour table " + javaFile, e);
        }
        Map<String, String> idsByConstant = new HashMap<>();
        Matcher keys = KEY.matcher(source);
        while (keys.find()) {
            idsByConstant.put(keys.group(1), keys.group(2));
        }
        List<Biome> biomes = new ArrayList<>();
        Matcher entries = ENTRY.matcher(source);
        while (entries.find()) {
            String id = idsByConstant.get(entries.group(1));
            if (id == null) {
                throw new IllegalStateException("Colour table row uses unknown constant " + entries.group(1));
            }
            biomes.add(new Biome(id, Integer.parseInt(entries.group(2), 16), Integer.parseInt(entries.group(3))));
        }
        if (biomes.isEmpty()) {
            throw new IllegalStateException("No colour table rows found in " + javaFile);
        }
        return new BiomeTable(biomes);
    }

    Biome byId(String id) {
        return byId.get(id);
    }

    Biome byColor(int color) {
        return byColor.get(color);
    }

    Biome ocean() {
        return byId.get("ocean");
    }
}
