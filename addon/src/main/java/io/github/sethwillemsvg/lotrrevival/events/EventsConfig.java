package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The {@code [events]} config section, in its own file {@code lotr_revival-events.toml}. {@code force_type} stages that event
 * type for the next eligible player on the next roll, regardless of chance and cooldown (CI and manual testing).
 */
public final class EventsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> FORCE_TYPE;
    public static final ModConfigSpec.IntValue ROLL_SECONDS, COOLDOWN_SECONDS, BAND_MIN, BAND_MAX, MIN_ONLINE_SECONDS;
    public static final ModConfigSpec.DoubleValue CHANCE, SIDE_BALANCE;
    public static final ModConfigSpec.IntValue WEIGHT_CLASH, WEIGHT_MUSTER, WEIGHT_RAID;
    public static final ModConfigSpec.IntValue MAX_EVENTS_BASE, PLAYERS_PER_EXTRA_EVENT, MAX_EVENTS, MAX_CHUNKS_PER_EVENT, CHUNK_BUDGET,
            ENTITIES_PER_EVENT, ENTITIES_TOTAL, DURATION_SECONDS, AMBUSH_DURATION_SECONDS, RELEASE_DISTANCE, SPAWN_EXCLUSION, EVENT_SPACING,
            VENUE_COOLDOWN_MINUTES, LOOKUP_BUDGET, LOOKUP_CACHE_SECONDS;
    public static final ModConfigSpec.IntValue BONUS_CLASH, BONUS_MUSTER, BONUS_RAID, BONUS_DEFENCE;
    public static final ModConfigSpec.DoubleValue GOV_YELLOW_MS, GOV_RED_MS, GOV_HYSTERESIS_MS;
    public static final ModConfigSpec.IntValue GOV_HOLD_SECONDS, GOV_SHED_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("events");
        ENABLED = b.define("enabled", true);
        FORCE_TYPE = b.comment("Stage this event type (warband_clash, muster, raid) on the next roll, ignoring chance and cooldown; empty = off")
                .define("force_type", "");
        ROLL_SECONDS = b.defineInRange("roll_seconds", 30, 1, 3600);
        CHANCE = b.defineInRange("chance", 0.05, 0, 1);
        COOLDOWN_SECONDS = b.defineInRange("cooldown_seconds", 300, 0, 86400);
        MIN_ONLINE_SECONDS = b.defineInRange("min_online_seconds", 180, 0, 86400);
        BAND_MIN = b.defineInRange("band_min", 64, 8, 2048);
        BAND_MAX = b.defineInRange("band_max", 256, 16, 4096);
        SIDE_BALANCE = b.defineInRange("side_balance", 0.6, 0, 1);
        WEIGHT_CLASH = b.defineInRange("weight_warband_clash", 3, 0, 100);
        WEIGHT_MUSTER = b.defineInRange("weight_muster", 2, 0, 100);
        WEIGHT_RAID = b.defineInRange("weight_raid", 2, 0, 100);
        MAX_EVENTS_BASE = b.defineInRange("max_events_base", 2, 0, 64);
        PLAYERS_PER_EXTRA_EVENT = b.defineInRange("players_per_extra_event", 3, 1, 100);
        MAX_EVENTS = b.defineInRange("max_events", 8, 0, 64);
        MAX_CHUNKS_PER_EVENT = b.defineInRange("max_chunks_per_event", 9, 0, 25);
        CHUNK_BUDGET = b.defineInRange("chunk_budget", 48, 0, 1024);
        ENTITIES_PER_EVENT = b.defineInRange("entities_per_event", 16, 1, 256);
        ENTITIES_TOTAL = b.defineInRange("entities_total", 120, 1, 2048);
        DURATION_SECONDS = b.defineInRange("duration_seconds", 480, 10, 7200);
        AMBUSH_DURATION_SECONDS = b.defineInRange("ambush_duration_seconds", 300, 10, 7200);
        RELEASE_DISTANCE = b.defineInRange("release_distance", 384, 32, 4096);
        SPAWN_EXCLUSION = b.defineInRange("spawn_exclusion", 128, 0, 4096);
        EVENT_SPACING = b.defineInRange("event_spacing", 96, 0, 4096);
        VENUE_COOLDOWN_MINUTES = b.defineInRange("venue_cooldown_minutes", 30, 0, 1440);
        LOOKUP_BUDGET = b.defineInRange("lookup_budget", 64, 1, 1024);
        LOOKUP_CACHE_SECONDS = b.defineInRange("lookup_cache_seconds", 300, 0, 3600);
        BONUS_CLASH = b.defineInRange("bonus_warband_clash", 15, 0, 1000);
        BONUS_MUSTER = b.defineInRange("bonus_muster_opposition", 20, 0, 1000);
        BONUS_RAID = b.defineInRange("bonus_raid", 15, 0, 1000);
        BONUS_DEFENCE = b.defineInRange("bonus_defence", 20, 0, 1000);
        GOV_YELLOW_MS = b.defineInRange("governor_yellow_ms", 35.0, 1, 1000);
        GOV_RED_MS = b.defineInRange("governor_red_ms", 45.0, 1, 1000);
        GOV_HYSTERESIS_MS = b.defineInRange("governor_hysteresis_ms", 5.0, 0, 100);
        GOV_HOLD_SECONDS = b.defineInRange("governor_hold_seconds", 10, 0, 600);
        GOV_SHED_SECONDS = b.defineInRange("governor_shed_seconds", 30, 0, 3600);
        b.pop();
        SPEC = b.build();
    }

    private EventsConfig() {}

    public static int i(ModConfigSpec.IntValue v) {
        return NemesisConfig.get(v, v.getDefault());
    }

    public static double d(ModConfigSpec.DoubleValue v) {
        return NemesisConfig.get(v, v.getDefault());
    }

    public static boolean enabled() {
        return NemesisConfig.get(ENABLED, true);
    }

    public static String forceType() {
        return NemesisConfig.get(FORCE_TYPE, "");
    }

    public static void logInUse() {
        LotrLog.info("events", "config: enabled={} roll={}s chance={} cooldown={}s band={}-{} side_balance={} weights clash/muster/raid={}/{}/{} "
                        + "caps base={} per_players={} max={} chunks/event={} chunk_budget={} entities={}/{} duration={}s governor={}/{}ms force_type='{}'",
                enabled(), i(ROLL_SECONDS), d(CHANCE), i(COOLDOWN_SECONDS), i(BAND_MIN), i(BAND_MAX), d(SIDE_BALANCE), i(WEIGHT_CLASH),
                i(WEIGHT_MUSTER), i(WEIGHT_RAID), i(MAX_EVENTS_BASE), i(PLAYERS_PER_EXTRA_EVENT), i(MAX_EVENTS), i(MAX_CHUNKS_PER_EVENT),
                i(CHUNK_BUDGET), i(ENTITIES_PER_EVENT), i(ENTITIES_TOTAL), i(DURATION_SECONDS), d(GOV_YELLOW_MS), d(GOV_RED_MS), forceType());
    }
}
