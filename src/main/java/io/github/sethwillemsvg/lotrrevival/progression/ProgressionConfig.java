package io.github.sethwillemsvg.lotrrevival.progression;

import java.util.List;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Progression config. Server settings live in {@code lotrrevival-progression-server.toml},
 * the client setting in {@code lotrrevival-progression-client.toml}.
 */
public final class ProgressionConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.BooleanValue MINI_QUESTS_ENABLED;
    public static final ModConfigSpec.BooleanValue BOUNTIES_ENABLED;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_QUESTS_PER_FACTION;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_QUESTS_TOTAL;
    public static final ModConfigSpec.BooleanValue TITLES_ENABLED;
    public static final ModConfigSpec.BooleanValue STRICT_FACTION_TITLES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ACHIEVEMENT_BROADCAST_DENYLIST;

    public static final ModConfigSpec.ConfigValue<String> QUEST_TRACKER_SIDE;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("progression");
        MINI_QUESTS_ENABLED = server.comment("Enable mini-quests.").define("miniQuestsEnabled", true);
        BOUNTIES_ENABLED = server.comment("Enable faction bounties.").define("bountiesEnabled", true);
        MAX_ACTIVE_QUESTS_PER_FACTION = server.comment("Maximum active quests per faction (failed quests do not count).")
                .defineInRange("maxActiveQuestsPerFaction", 5, 1, 20);
        MAX_ACTIVE_QUESTS_TOTAL = server.comment("Maximum active quests in total (failed quests do not count).")
                .defineInRange("maxActiveQuestsTotal", 20, 5, 100);
        TITLES_ENABLED = server.comment("Enable titles (chat prefix and Titles page).")
                .define("titlesEnabled", true);
        STRICT_FACTION_TITLES = server.comment("Require the matching pledge for faction titles.")
                .define("strictFactionTitles", false);
        ACHIEVEMENT_BROADCAST_DENYLIST = server.comment("Achievement ids whose earning is not announced in chat.")
                .defineListAllowEmpty(
                        "achievementBroadcastDenylist",
                        List.of("lotrrevival:shire/kill_hobbit"),
                        () -> "lotrrevival:shire/kill_hobbit",
                        o -> o instanceof String);
        server.pop();
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("progression");
        QUEST_TRACKER_SIDE = client.comment("Screen side of the quest tracker HUD.")
                // Arrays.asList, not List.of: NeoForge calls contains(null) while correcting a fresh config,
                // and immutable List.of throws on null.
                .defineInList("questTrackerSide", "left", java.util.Arrays.asList("left", "right"));
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private ProgressionConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "lotrrevival-progression-server.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "lotrrevival-progression-client.toml");
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
