package io.github.sethwillemsvg.lotrrevival.progression.api;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientAchievements;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Read API of the Progression pod. Other pods reach {@link #achievementCount} through
 * {@code core.api.AchievementCounters}, which this class backs.
 */
public final class ProgressionApi {
    private ProgressionApi() {}

    /**
     * Number of achievements the player has earned, is eligible for and that are counted. Works on both sides:
     * the server reads advancements (cached per player), the client reads the synced earned set, so the client
     * value is only meaningful for the local player.
     */
    public static int achievementCount(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return AchievementService.count(serverPlayer);
        }
        return ClientAchievements.count(player);
    }
}
