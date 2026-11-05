package io.github.sethwillemsvg.lotrrevival.structures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Section {@code [structures]} (specs/addon/structures.md, Config). Registered as its own common file
 * {@code lotr_revival-structures.toml} (several systems each register a common config). Values are read through the
 * safe getters below, which fall back to the defaults before the config has loaded (for example in worldgen threads of
 * a game test server that starts before the file is read).
 */
public final class StructuresConfig {
    public static final String FILE = "lotr_revival-structures.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final Map<String, RegionValues> REGIONS = new LinkedHashMap<>();
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_BUILDS;
    public static final ModConfigSpec.IntValue EXCLUSION_MIDDLE_EARTH;
    public static final ModConfigSpec.IntValue EXCLUSION_OWN;
    public static final ModConfigSpec.BooleanValue CREDITS_ANNOUNCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> VENUE_DIMENSIONS;
    public static final ModConfigSpec.IntValue PROBE_BUDGET;

    public record RegionValues(ModConfigSpec.BooleanValue enabled, ModConfigSpec.IntValue spacing,
                               ModConfigSpec.IntValue separation, ModConfigSpec.IntValue crossingsSpacing,
                               ModConfigSpec.IntValue crossingsSeparation) {}

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("structures");
        ENABLED = b.comment("All of the add-on's structure sets").define("enabled", true);
        b.push("regions");
        for (Regions.Region r : Regions.ALL) {
            b.push(r.id());
            REGIONS.put(r.id(), new RegionValues(
                    b.define("enabled", true),
                    b.comment("Chunks between candidate starts; locked per world on first start").defineInRange("spacing", r.spacing(), 2, 4096),
                    b.comment("Minimum chunks between starts; must be below spacing").defineInRange("separation", r.separation(), 1, 4095),
                    b.defineInRange("crossings_spacing", Regions.CROSSINGS_SPACING, 2, 4096),
                    b.defineInRange("crossings_separation", Regions.CROSSINGS_SEPARATION, 1, 4095)));
            b.pop();
        }
        b.pop();
        DISABLED_BUILDS = b.comment("Build ids that never place").defineListAllowEmpty("disabled_builds", List.of(), () -> "",
                o -> o instanceof String);
        b.push("exclusion");
        EXCLUSION_MIDDLE_EARTH = b.comment("No start within this many chunks of a start of the region's Middle-earth set")
                .defineInRange("middle_earth_chunks", 3, 0, 32);
        EXCLUSION_OWN = b.comment("No start within this many chunks of a start of another lotr_revival set")
                .defineInRange("own_chunks", 2, 0, 32);
        b.pop();
        b.push("credits");
        CREDITS_ANNOUNCE = b.comment("Chat line crediting a community build when a player first enters it").define("announce", true);
        b.pop();
        b.push("venues");
        VENUE_DIMENSIONS = b.comment("Levels the venue index runs in").defineListAllowEmpty("dimensions",
                List.of("middle-earth:middle_earth"), () -> "", o -> o instanceof String);
        PROBE_BUDGET = b.comment("Saved-chunk status reads per second while completing venue entries")
                .defineInRange("probe_budget", 64, 0, 10_000);
        b.pop();
        b.pop();
        SPEC = b.build();
    }

    private StructuresConfig() {}

    public static <T> T get(Supplier<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {   // config not loaded yet
            return fallback;
        }
    }

    public static boolean enabled() {
        return get(ENABLED, true);
    }

    public static boolean regionEnabled(String region) {
        RegionValues v = REGIONS.get(region);
        return v == null || get(v.enabled(), true);
    }

    public static boolean buildDisabled(String build) {
        return get(DISABLED_BUILDS, List.<String>of()).contains(build);
    }

    public static List<? extends String> venueDimensions() {
        return get(VENUE_DIMENSIONS, List.of("middle-earth:middle_earth"));
    }
}
