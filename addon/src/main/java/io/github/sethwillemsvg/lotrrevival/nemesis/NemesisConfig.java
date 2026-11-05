package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * The {@code [nemesis]} config section (specs/addon/nemesis-events.md, Config). Registered as its own common file
 * {@code lotr_revival-nemesis.toml} so it cannot clash with another system's common config. Every getter falls back to the
 * default when the config is not loaded yet (early game tests, data loading).
 */
public final class NemesisConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_FACTIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;

    public static final ModConfigSpec.DoubleValue PLAYER_KILL_CHANCE, LEADER_BONUS, UNIT_KILL_CHANCE, UNIT_KILL_PER_LEVEL;
    public static final ModConfigSpec.IntValue RENOWN_PLAYER_KILL, RENOWN_UNIT_KILL, RENOWN_UNIT_PER_LEVEL, RENOWN_AMBUSH_KILL,
            RENOWN_ESCAPE, RENOWN_CHEAT_DEATH, RENOWN_CHALLENGE, RENOWN_EVENT_WIN, RENOWN_ASCENSION, RENOWN_CAP;
    public static final ModConfigSpec.IntValue GRUDGE_CAP, GRUDGE_HOLDERS;
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> RANK_THRESHOLDS, RANK_SEATS, ESCAPE_CHANCES, CHEAT_DEATH_CHANCES;
    public static final ModConfigSpec.DoubleValue ESCAPE_GRUDGE_BONUS, HEAVY_CHANCE, WEAKNESS_LOSS_CHANCE;
    public static final ModConfigSpec.IntValue MAX_MANIFEST, RETREAT_DISTANCE, RETREAT_DELAY_SECONDS, REPUTATION_MULTIPLIER, FADE_DAYS,
            DEAD_KEEP_DAYS;
    public static final ModConfigSpec.IntValue POLITICS_PERIOD_MINUTES, CHALLENGE_WEIGHT, ASCENSION_WEIGHT;
    public static final ModConfigSpec.DoubleValue POLITICS_CHANCE;
    public static final ModConfigSpec.IntValue AMBUSH_CHECK_SECONDS, AMBUSH_COOLDOWN_MINUTES, AMBUSH_COOLDOWN_WON_MINUTES, SAFE_RADIUS,
            AMBUSH_MIN_ONLINE_MINUTES;
    public static final ModConfigSpec.DoubleValue AMBUSH_CHANCE;
    public static final ModConfigSpec.IntValue MAX_SERGEANTS;
    public static final ModConfigSpec.ConfigValue<String> GEAR_REROLL_RANK, MOUNT_RANK;
    public static final ModConfigSpec.IntValue GUARD_FOLLOW_DISTANCE, GUARD_LEASH_DISTANCE, GUARD_TELEPORT_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("nemesis");
        ENABLED = b.define("enabled", true);
        DISABLED_FACTIONS = b.defineListAllowEmpty("disabled_factions", Arrays.<String>asList(), () -> "middle-earth:mordor",
                o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        ALLOWED_DIMENSIONS = b.defineListAllowEmpty("allowed_dimensions", Arrays.asList("middle-earth:middle_earth"),
                () -> "middle-earth:middle_earth", o -> o instanceof String s && ResourceLocation.tryParse(s) != null);

        b.push("promotion");
        PLAYER_KILL_CHANCE = b.defineInRange("player_kill_chance", 0.35, 0, 1);
        LEADER_BONUS = b.defineInRange("leader_bonus", 0.20, 0, 1);
        UNIT_KILL_CHANCE = b.defineInRange("unit_kill_chance", 0.10, 0, 1);
        UNIT_KILL_PER_LEVEL = b.defineInRange("unit_kill_per_level", 0.03, 0, 1);
        b.pop();

        b.push("renown");
        RENOWN_PLAYER_KILL = b.defineInRange("player_kill", 10, 0, 200);
        RENOWN_UNIT_KILL = b.defineInRange("unit_kill", 2, 0, 200);
        RENOWN_UNIT_PER_LEVEL = b.defineInRange("unit_kill_per_level", 1, 0, 200);
        RENOWN_AMBUSH_KILL = b.defineInRange("ambush_kill", 5, 0, 200);
        RENOWN_ESCAPE = b.defineInRange("escape", 4, 0, 200);
        RENOWN_CHEAT_DEATH = b.defineInRange("cheat_death", 6, 0, 200);
        RENOWN_CHALLENGE = b.defineInRange("challenge_win", 8, 0, 200);
        RENOWN_EVENT_WIN = b.defineInRange("event_win", 3, 0, 200);
        RENOWN_ASCENSION = b.defineInRange("ascension", 6, 0, 200);
        RENOWN_CAP = b.defineInRange("cap", 200, 1, 10000);
        GRUDGE_CAP = b.defineInRange("grudge_cap", 10, 1, 100);
        GRUDGE_HOLDERS = b.defineInRange("grudge_holders", 8, 1, 64);
        b.pop();

        b.push("ranks");
        RANK_THRESHOLDS = b.defineList("thresholds", Arrays.asList(0, 15, 35, 70, 120), () -> 0, o -> o instanceof Number);
        RANK_SEATS = b.defineList("seats", Arrays.asList(12, 8, 5, 3, 1), () -> 1, o -> o instanceof Number);
        HEAVY_CHANCE = b.defineInRange("heavy_chance", 0.30, 0, 1);
        WEAKNESS_LOSS_CHANCE = b.defineInRange("weakness_loss_chance", 0.25, 0, 1);
        b.pop();

        b.push("bodies");
        MAX_MANIFEST = b.defineInRange("max_manifest", 8, 0, 64);
        RETREAT_DISTANCE = b.defineInRange("retreat_distance", 128, 16, 1024);
        RETREAT_DELAY_SECONDS = b.defineInRange("retreat_delay_seconds", 60, 1, 3600);
        ESCAPE_CHANCES = b.defineList("escape_chances", Arrays.asList(0.5, 0.4, 0.3, 0.2, 0.1), () -> 0.0, o -> o instanceof Number);
        ESCAPE_GRUDGE_BONUS = b.defineInRange("escape_grudge_bonus", 0.2, 0, 1);
        CHEAT_DEATH_CHANCES = b.defineList("cheat_death_chances", Arrays.asList(0.3, 0.15), () -> 0.0, o -> o instanceof Number);
        REPUTATION_MULTIPLIER = b.defineInRange("nemesis_reputation_multiplier", 25, 0, 1000);
        FADE_DAYS = b.defineInRange("fade_days", 30, 1, 3650);
        DEAD_KEEP_DAYS = b.defineInRange("dead_keep_days", 7, 0, 365);
        GEAR_REROLL_RANK = b.comment("Lowest rank whose gear is re-rolled once from the faction's LEADER (else VETERAN) gear")
                .define("gear_reroll_rank", "commander", o -> o instanceof String s && isRank(s));
        MOUNT_RANK = b.comment("Lowest rank that rides a mount (its type's, else one of a LEADER type of its faction)")
                .define("mount_rank", "warlord", o -> o instanceof String s && isRank(s));
        GUARD_FOLLOW_DISTANCE = b.comment("Bodyguards out of a fight walk back once further than this from their nemesis")
                .defineInRange("bodyguard_follow_distance", 6, 2, 64);
        GUARD_LEASH_DISTANCE = b.comment("Bodyguards break off a fight once further than this from their nemesis")
                .defineInRange("bodyguard_leash_distance", 12, 4, 128);
        GUARD_TELEPORT_DISTANCE = b.comment("Bodyguards further than this (a mounted nemesis rode off) are moved next to it")
                .defineInRange("bodyguard_teleport_distance", 32, 8, 256);
        b.pop();

        b.push("politics");
        POLITICS_PERIOD_MINUTES = b.defineInRange("period_minutes", 30, 1, 1440);
        POLITICS_CHANCE = b.defineInRange("chance", 0.6, 0, 1);
        CHALLENGE_WEIGHT = b.defineInRange("challenge_weight", 3, 0, 100);
        ASCENSION_WEIGHT = b.defineInRange("ascension_weight", 1, 0, 100);
        b.pop();

        b.push("ambush");
        AMBUSH_CHECK_SECONDS = b.defineInRange("check_seconds", 30, 1, 3600);
        AMBUSH_CHANCE = b.defineInRange("chance", 0.01, 0, 1);
        AMBUSH_COOLDOWN_MINUTES = b.defineInRange("cooldown_minutes", 75, 0, 1440);
        AMBUSH_COOLDOWN_WON_MINUTES = b.defineInRange("cooldown_won_minutes", 60, 0, 1440);
        SAFE_RADIUS = b.defineInRange("safe_radius", 128, 0, 4096);
        AMBUSH_MIN_ONLINE_MINUTES = b.defineInRange("min_online_minutes", 15, 0, 1440);
        b.pop();

        MAX_SERGEANTS = b.defineInRange("max_sergeants", 3, 0, 30);
        b.pop();
        SPEC = b.build();
    }

    /** Game tests: allow every dimension (the test level is the overworld). */
    public static volatile boolean testAnyDimension;

    private NemesisConfig() {}

    public static <T> T get(Supplier<T> value, T fallback) {
        try {
            T v = value.get();
            return v == null ? fallback : v;
        } catch (RuntimeException e) {   // not loaded yet
            return fallback;
        }
    }

    public static boolean enabled() {
        return get(ENABLED, true);
    }

    public static int i(ModConfigSpec.IntValue v) {
        return get(v, v.getDefault());
    }

    public static double d(ModConfigSpec.DoubleValue v) {
        return get(v, v.getDefault());
    }

    public static double listAt(ModConfigSpec.ConfigValue<List<? extends Number>> v, int index, double fallback) {
        List<? extends Number> l = get(v, v.getDefault());
        if (l == null || l.isEmpty()) return fallback;
        return l.get(Math.min(index, l.size() - 1)).doubleValue();
    }

    private static boolean isRank(String s) {
        for (Rank r : Rank.values()) if (r.key().equals(s)) return true;
        return false;
    }

    /** A rank-valued setting (lower-case rank name); the default when unset or unknown. */
    public static Rank rank(ModConfigSpec.ConfigValue<String> v) {
        String s = get(v, v.getDefault());
        return isRank(s) ? Rank.byName(s) : Rank.byName(v.getDefault());
    }

    public static boolean factionDisabled(ResourceLocation faction) {
        return get(DISABLED_FACTIONS, List.<String>of()).contains(faction.toString());
    }

    public static boolean dimensionAllowed(Level level) {
        if (testAnyDimension) return true;
        return get(ALLOWED_DIMENSIONS, List.of("middle-earth:middle_earth")).contains(level.dimension().location().toString());
    }

    public static void logInUse() {
        LotrLog.info("nemesis", "config: enabled={} disabled_factions={} allowed_dimensions={} max_manifest={} thresholds={} seats={} "
                        + "player_kill_chance={} unit_kill_chance={} escape={} cheat_death={} rep_x={} fade_days={} ambush_chance={} "
                        + "ambush_cooldown={}min safe_radius={} max_sergeants={} gear_reroll_rank={} mount_rank={} guard_follow={} guard_leash={} "
                        + "guard_teleport={}",
                enabled(), get(DISABLED_FACTIONS, List.of()), get(ALLOWED_DIMENSIONS, List.of()), i(MAX_MANIFEST),
                get(RANK_THRESHOLDS, List.of()), get(RANK_SEATS, List.of()), d(PLAYER_KILL_CHANCE), d(UNIT_KILL_CHANCE),
                get(ESCAPE_CHANCES, List.of()), get(CHEAT_DEATH_CHANCES, List.of()), i(REPUTATION_MULTIPLIER), i(FADE_DAYS),
                d(AMBUSH_CHANCE), i(AMBUSH_COOLDOWN_MINUTES), i(SAFE_RADIUS), i(MAX_SERGEANTS), rank(GEAR_REROLL_RANK), rank(MOUNT_RANK),
                i(GUARD_FOLLOW_DISTANCE), i(GUARD_LEASH_DISTANCE), i(GUARD_TELEPORT_DISTANCE));
    }
}
