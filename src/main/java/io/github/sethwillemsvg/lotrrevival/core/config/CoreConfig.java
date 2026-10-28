package io.github.sethwillemsvg.lotrrevival.core.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (lotrrevival-server.toml). */
public final class CoreConfig {
    public static final ModConfigSpec SPEC;

    // [alignment]
    public static final ModConfigSpec.DoubleValue KILL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ALLY_SHARE;
    public static final ModConfigSpec.DoubleValue FRIEND_SHARE;
    public static final ModConfigSpec.DoubleValue ENEMY_SHARE;
    public static final ModConfigSpec.DoubleValue MORTAL_ENEMY_SHARE;
    public static final ModConfigSpec.BooleanValue MORTAL_ENEMY_DRAIN;
    public static final ModConfigSpec.IntValue DRAIN_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue DRAIN_AMOUNT;
    public static final ModConfigSpec.IntValue PLEDGE_WARNING_TICKS;
    public static final ModConfigSpec.IntValue PLEDGE_COOLDOWN_BASE_TICKS;
    public static final ModConfigSpec.IntValue PLEDGE_COOLDOWN_EXTRA_TICKS;
    public static final ModConfigSpec.DoubleValue PLEDGE_COOLDOWN_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("alignment");
        KILL_MULTIPLIER = builder.comment("Multiplier applied to every kill alignment change.")
                .defineInRange("kill_multiplier", 1.0, 0.0, 1000.0);
        ALLY_SHARE = builder.comment("Share of the kill penalty applied to factions allied to the victim's faction.")
                .defineInRange("ally_share", 1.0, 0.0, 1000.0);
        FRIEND_SHARE = builder.comment("Share of the kill penalty applied to factions friendly to the victim's faction.")
                .defineInRange("friend_share", 1.0, 0.0, 1000.0);
        ENEMY_SHARE = builder.comment("Share of the kill bonus applied to enemies of the victim's faction.")
                .defineInRange("enemy_share", 1.0, 0.0, 1000.0);
        MORTAL_ENEMY_SHARE = builder.comment("Share of the kill bonus applied to mortal enemies of the victim's faction.")
                .defineInRange("mortal_enemy_share", 1.0, 0.0, 1000.0);
        MORTAL_ENEMY_DRAIN = builder.comment("Holding positive alignment with two mortal-enemy factions drains both.")
                .define("mortal_enemy_drain", true);
        DRAIN_INTERVAL_TICKS = builder.comment("Online ticks between mortal-enemy drains.")
                .defineInRange("drain_interval_ticks", 1000, 1, Integer.MAX_VALUE);
        DRAIN_AMOUNT = builder.comment("Alignment lost per faction per drain.")
                .defineInRange("drain_amount", 5.0, 0.0, 10000.0);
        PLEDGE_WARNING_TICKS = builder.comment("Online ticks a pledge warning stays active (a second ally kill breaks the pledge).")
                .defineInRange("pledge_warning_ticks", 24000, 1, Integer.MAX_VALUE);
        PLEDGE_COOLDOWN_BASE_TICKS = builder.comment("Minimum online ticks of cooldown after a pledge is broken.")
                .defineInRange("pledge_cooldown_base_ticks", 36000, 0, Integer.MAX_VALUE);
        PLEDGE_COOLDOWN_EXTRA_TICKS = builder.comment("Extra cooldown ticks added at full scale (alignment far above the pledge threshold).")
                .defineInRange("pledge_cooldown_extra_ticks", 180000, 0, Integer.MAX_VALUE);
        PLEDGE_COOLDOWN_SCALE = builder.comment("Alignment above the pledge threshold at which the extra cooldown is complete.")
                .defineInRange("pledge_cooldown_scale", 5000.0, 1.0, 100000.0);
        builder.pop();
        SPEC = builder.build();
    }

    private CoreConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "lotrrevival-server.toml");
    }

    /** Reads a value, falling back to its default when the config is not loaded yet (early or in unit contexts). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}
