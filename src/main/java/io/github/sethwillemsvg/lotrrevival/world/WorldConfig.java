package io.github.sethwillemsvg.lotrrevival.world;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * World pod server config, {@code lotrrevival-world-server.toml}. Every key from
 * {@code specs/world/waypoints-travel.md} lives here so later tickets only read it.
 */
public final class WorldConfig {
    public static final ModConfigSpec SERVER_SPEC;

    // travel.*
    public static final ModConfigSpec.BooleanValue TRAVEL_ENABLED;
    public static final ModConfigSpec.BooleanValue TRAVEL_COOLDOWN;
    public static final ModConfigSpec.IntValue TRAVEL_MIN_SECONDS;
    public static final ModConfigSpec.IntValue TRAVEL_MAX_SECONDS;
    public static final ModConfigSpec.DoubleValue TRAVEL_USE_DECAY;
    public static final ModConfigSpec.DoubleValue TRAVEL_DISTANCE_FACTOR;
    public static final ModConfigSpec.IntValue TRAVEL_COUNTDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue TRAVEL_HOSTILE_RANGE;
    public static final ModConfigSpec.DoubleValue TRAVEL_COMPANION_RANGE;
    public static final ModConfigSpec.DoubleValue TRAVEL_OFFLINE_FRACTION;

    // waypoints.*
    public static final ModConfigSpec.IntValue WAYPOINTS_CUSTOM_BASE;
    public static final ModConfigSpec.IntValue WAYPOINTS_CUSTOM_PER_ACHIEVEMENTS;
    public static final ModConfigSpec.IntValue WAYPOINTS_CUSTOM_MIN_Y;

    // portal.*
    public static final ModConfigSpec.BooleanValue PORTAL_BLAST_BREAKS_BLOCKS;

    // respawn.*
    public static final ModConfigSpec.BooleanValue RESPAWN_ENABLED;
    public static final ModConfigSpec.IntValue RESPAWN_BED_DISTANCE;
    public static final ModConfigSpec.IntValue RESPAWN_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue RESPAWN_MIN_OFFSET;
    public static final ModConfigSpec.IntValue RESPAWN_MAX_OFFSET;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Fast travel between waypoints.").push("travel");
        TRAVEL_ENABLED = b.comment("Enable fast travel.").define("enabled", true);
        TRAVEL_COOLDOWN = b.comment("Legacy-style wait between trips (min/max_seconds below). Off: travel any time.")
                .define("cooldown", false);
        TRAVEL_MIN_SECONDS = b.comment("Shortest required time between trips, in seconds, after many uses.")
                .defineInRange("min_seconds", 180, 0, 1_000_000);
        TRAVEL_MAX_SECONDS = b.comment("Required time for a first-time trip, in seconds.")
                .defineInRange("max_seconds", 1800, 0, 1_000_000);
        TRAVEL_USE_DECAY = b.comment("Factor applied to the variable part of the required time per previous use.")
                .defineInRange("use_decay", 0.9, 0.0, 1.0);
        TRAVEL_DISTANCE_FACTOR = b.comment("Extra required-time multiplier per block of horizontal distance.")
                .defineInRange("distance_factor", 0.000012, 0.0, 1.0);
        TRAVEL_COUNTDOWN_TICKS = b.comment("Ticks the player must stand still before travelling.")
                .defineInRange("countdown_ticks", 200, 1, 100_000);
        TRAVEL_HOSTILE_RANGE = b.comment("Travel is refused when a mob within this range (blocks) targets the player.")
                .defineInRange("hostile_range", 16.0, 0.0, 1024.0);
        TRAVEL_COMPANION_RANGE = b.comment("Companions within this 3D range (blocks) travel with the player.")
                .defineInRange("companion_range", 256.0, 0.0, 100_000.0);
        TRAVEL_OFFLINE_FRACTION = b.comment("Fraction of offline time credited to the travel clock (multiplayer only).")
                .defineInRange("offline_fraction", 0.1, 0.0, 1.0);
        b.pop();

        b.comment("Waypoints.").push("waypoints");
        WAYPOINTS_CUSTOM_BASE = b.comment("Custom waypoints every player may create.")
                .defineInRange("custom_base", 5, 0, 1000);
        WAYPOINTS_CUSTOM_PER_ACHIEVEMENTS = b.comment("One extra custom waypoint per this many achievements.")
                .defineInRange("custom_per_achievements", 5, 1, 1000);
        WAYPOINTS_CUSTOM_MIN_Y = b.comment("Custom waypoints cannot be created below this Y. -2048 means no limit.")
                .defineInRange("custom_min_y", -2048, -2048, 2048);
        b.pop();

        b.comment("Ring portal.").push("portal");
        PORTAL_BLAST_BREAKS_BLOCKS = b.comment("Whether the blast that opens the ring portal breaks blocks.")
                .define("blast_breaks_blocks", false);
        b.pop();

        b.comment("Respawning in Middle-earth.").push("respawn");
        RESPAWN_ENABLED = b.comment("Enable the far-death rule (deaths in Middle-earth always respawn there).")
                .define("enabled", true);
        RESPAWN_BED_DISTANCE = b.comment("Respawn near the death point when it is farther than this (blocks) from the bed.")
                .defineInRange("bed_distance", 5000, 0, 10_000_000);
        RESPAWN_SPAWN_DISTANCE = b.comment("Same, measured from Middle-earth's spawn when the player has no bed there.")
                .defineInRange("spawn_distance", 2000, 0, 10_000_000);
        RESPAWN_MIN_OFFSET = b.comment("Minimum distance (blocks) of the far-death respawn from the death point.")
                .defineInRange("min_offset", 500, 0, 10_000_000);
        RESPAWN_MAX_OFFSET = b.comment("Maximum distance (blocks) of the far-death respawn from the death point.")
                .defineInRange("max_offset", 1500, 0, 10_000_000);
        b.pop();

        SERVER_SPEC = b.build();
    }

    private WorldConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "lotrrevival-world-server.toml");
    }

    /** Reads a value, falling back to its default when the config is not loaded yet (e.g. in GameTests). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}
