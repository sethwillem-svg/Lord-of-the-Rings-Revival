package io.github.sethwillemsvg.lotrrevival.nodemap;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelCost;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Node map and travel settings (specs/addon/node-map-travel.md, Rules and numbers). Every getter falls back to the default
 * while the config is not loaded yet (unit tests, early client), so callers never see the "config not loaded" exception.
 */
public final class NodeMapConfig {
    public static final ModConfigSpec SPEC;

    // nodemap
    static final ModConfigSpec.IntValue DISCOVER_INTERVAL;
    static final ModConfigSpec.IntValue DISCOVER_RADIUS;
    static final ModConfigSpec.IntValue HOME_REVEAL_RADIUS;
    static final ModConfigSpec.IntValue CUSTOM_MAX;
    static final ModConfigSpec.IntValue CUSTOM_SPACING;
    static final ModConfigSpec.IntValue CUSTOM_LINK_RANGE;
    static final ModConfigSpec.IntValue CUSTOM_LINKS;
    // travel
    static final ModConfigSpec.BooleanValue ENABLED;
    static final ModConfigSpec.IntValue DEPART_RADIUS;
    static final ModConfigSpec.IntValue CHARGE_TICKS;
    static final ModConfigSpec.IntValue COOLDOWN_BASE;
    static final ModConfigSpec.DoubleValue COOLDOWN_PER_BLOCK;
    static final ModConfigSpec.IntValue COOLDOWN_MAX;
    static final ModConfigSpec.DoubleValue EXHAUSTION_PER_BLOCK;
    static final ModConfigSpec.DoubleValue EXHAUSTION_CAP;
    static final ModConfigSpec.IntValue MIN_FOOD;
    static final ModConfigSpec.IntValue COIN_PER_1000;
    static final ModConfigSpec.IntValue HOSTILE_RANGE;
    static final ModConfigSpec.IntValue COMBAT_TAG_TICKS;
    static final Map<RouteKind, ModConfigSpec.DoubleValue> FACTORS = new EnumMap<>(RouteKind.class);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("nodemap");
        DISCOVER_INTERVAL = b.comment("Ticks between discovery checks per player in Middle-earth").defineInRange("discover_interval", 20, 1, 1200);
        DISCOVER_RADIUS = b.comment("Horizontal discovery radius in blocks (a node's discover_radius overrides)").defineInRange("discover_radius", 96, 1, 4096);
        HOME_REVEAL_RADIUS = b.comment("On first entry, nodes this close to the faction spawn become known (0 = off)").defineInRange("home_reveal_radius", 1000, 0, 100000);
        CUSTOM_MAX = b.comment("Camps per player").defineInRange("custom_max", 5, 0, 64);
        CUSTOM_SPACING = b.comment("Minimum blocks between a player's camps").defineInRange("custom_spacing", 48, 0, 10000);
        CUSTOM_LINK_RANGE = b.comment("Camps link to known fixed nodes within this many blocks").defineInRange("custom_link_range", 3000, 1, 100000);
        CUSTOM_LINKS = b.comment("How many nearest known fixed nodes a camp links to").defineInRange("custom_links", 2, 1, 8);
        b.pop();
        b.push("travel");
        ENABLED = b.comment("Fast travel on or off").define("enabled", true);
        DEPART_RADIUS = b.comment("Must stand this close to a known usable node to depart").defineInRange("depart_radius", 64, 1, 4096);
        CHARGE_TICKS = b.comment("Ticks to stand still before travelling").defineInRange("charge_ticks", 200, 1, 12000);
        COOLDOWN_BASE = b.defineInRange("cooldown_base", 600, 0, 1_000_000);
        COOLDOWN_PER_BLOCK = b.defineInRange("cooldown_per_block", 0.2, 0.0, 100.0);
        COOLDOWN_MAX = b.defineInRange("cooldown_max", 12000, 0, 10_000_000);
        EXHAUSTION_PER_BLOCK = b.defineInRange("exhaustion_per_block", 0.002, 0.0, 10.0);
        EXHAUSTION_CAP = b.defineInRange("exhaustion_cap", 40.0, 0.0, 40.0);
        MIN_FOOD = b.defineInRange("min_food", 6, 0, 20);
        COIN_PER_1000 = b.comment("Copper coins per 1,000 blocks (0 = off)").defineInRange("coin_per_1000", 0, 0, 1_000_000);
        HOSTILE_RANGE = b.defineInRange("hostile_range", 16, 0, 128);
        COMBAT_TAG_TICKS = b.defineInRange("combat_tag_ticks", 100, 0, 12000);
        b.push("factor");
        for (RouteKind k : RouteKind.values()) FACTORS.put(k, b.defineInRange(k.getSerializedName(), k.defaultFactor, 0.01, 100.0));
        b.pop();
        b.pop();
        SPEC = b.build();
    }

    private NodeMapConfig() {}

    static int get(ModConfigSpec.IntValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    static double get(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    static boolean get(ModConfigSpec.BooleanValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static int discoverInterval() { return get(DISCOVER_INTERVAL); }
    public static int discoverRadius() { return get(DISCOVER_RADIUS); }
    public static int homeRevealRadius() { return get(HOME_REVEAL_RADIUS); }
    public static int customMax() { return get(CUSTOM_MAX); }
    public static int customSpacing() { return get(CUSTOM_SPACING); }
    public static int customLinkRange() { return get(CUSTOM_LINK_RANGE); }
    public static int customLinks() { return get(CUSTOM_LINKS); }
    public static boolean enabled() { return get(ENABLED); }
    public static int departRadius() { return get(DEPART_RADIUS); }
    public static int chargeTicks() { return get(CHARGE_TICKS); }
    public static int minFood() { return get(MIN_FOOD); }
    public static int coinPer1000() { return get(COIN_PER_1000); }
    public static int hostileRange() { return get(HOSTILE_RANGE); }
    public static int combatTagTicks() { return get(COMBAT_TAG_TICKS); }

    public static Map<RouteKind, Double> factors() {
        Map<RouteKind, Double> m = new EnumMap<>(RouteKind.class);
        FACTORS.forEach((k, v) -> m.put(k, get(v)));
        return m;
    }

    public static TravelCost cost() {
        return new TravelCost(get(COOLDOWN_BASE), get(COOLDOWN_PER_BLOCK), get(COOLDOWN_MAX), get(EXHAUSTION_PER_BLOCK),
                get(EXHAUSTION_CAP), get(COIN_PER_1000));
    }

    /** INFO line with every value in use (on config load). */
    public static void logValues() {
        LotrLog.info("nodemap", "config: discover_interval={} discover_radius={} home_reveal_radius={} custom_max={} custom_spacing={} "
                        + "custom_link_range={} custom_links={}", discoverInterval(), discoverRadius(), homeRevealRadius(), customMax(),
                customSpacing(), customLinkRange(), customLinks());
        LotrLog.info("travel", "config: enabled={} depart_radius={} charge_ticks={} min_food={} hostile_range={} combat_tag_ticks={} "
                        + "cost={} factors={}", enabled(), departRadius(), chargeTicks(), minFood(), hostileRange(), combatTagTicks(),
                cost(), factors());
    }
}
