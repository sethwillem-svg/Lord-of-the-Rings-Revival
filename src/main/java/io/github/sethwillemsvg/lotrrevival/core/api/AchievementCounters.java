package io.github.sethwillemsvg.lotrrevival.core.api;

import java.util.Objects;
import net.minecraft.world.entity.player.Player;

/**
 * Holder for the single {@link AchievementCounter}. Consumers call {@link #count(Player)}; until
 * Progression registers its implementation the count is 0.
 */
public final class AchievementCounters {
    private static final AchievementCounter NONE = player -> 0;
    private static volatile AchievementCounter current = NONE;

    private AchievementCounters() {}

    /** Registers the implementation (Progression only). Throws if one is already registered. */
    public static synchronized void register(AchievementCounter counter) {
        Objects.requireNonNull(counter);
        if (current != NONE) {
            throw new IllegalStateException("An AchievementCounter is already registered");
        }
        current = counter;
    }

    /** Number of counted achievements the player has; 0 before Progression registers. */
    public static int count(Player player) {
        return Math.max(0, current.achievementCount(player));
    }
}
