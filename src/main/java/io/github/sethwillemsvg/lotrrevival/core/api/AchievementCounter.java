package io.github.sethwillemsvg.lotrrevival.core.api;

import net.minecraft.world.entity.player.Player;

/**
 * Number of earned, eligible, counted achievements for a player. Defined in Core so World, NPC and
 * other pods never depend on the Progression package; Progression implements it and registers it
 * once through {@link AchievementCounters#register(AchievementCounter)}. Works on both logical
 * sides: the implementation reads whatever data it has on the side it is called on.
 */
@FunctionalInterface
public interface AchievementCounter {
    int achievementCount(Player player);
}
