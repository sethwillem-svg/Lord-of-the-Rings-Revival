package io.github.sethwillemsvg.lotrrevival.npc;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NPC pod server config, in {@code lotrrevival-npc-server.toml}. */
public final class NpcConfig {
    public static final ModConfigSpec SERVER_SPEC;

    public static final ModConfigSpec.BooleanValue WILD_SPAWNING_ENABLED;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL;
    public static final ModConfigSpec.DoubleValue SPAWN_DAMPING;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("spawning");
        WILD_SPAWNING_ENABLED = server.comment("Spawn wild NPCs in Middle-earth.").define("wild_spawning_enabled", true);
        SPAWN_INTERVAL = server.comment(
                        "The wild spawner runs every N ticks and makes N times as many attempts per chunk, so a higher value costs less per tick.")
                .defineInRange("spawn_interval", 1, 1, 100);
        SPAWN_DAMPING = server.comment(
                        "Multiplayer damping d: the global cap is scaled by max(0.5, 1 / (1 + d * (players - 1))).")
                .defineInRange("spawn_damping", 0.1, 0.0, 10.0);
        server.pop();
        SERVER_SPEC = server.build();
    }

    private NpcConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "lotrrevival-npc-server.toml");
    }

    /** Reads a value, falling back to its default when the config is not loaded yet. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}
