package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import net.minecraft.server.level.ServerPlayer;

/** Fires the banner achievement criteria. */
public final class BannerAchievements {
    private BannerAchievements() {}

    /** The first protecting banner a player places earns {@code general/banner_protector}. */
    public static void onProtectingBannerPlaced(ServerPlayer player) {
        ProgressionTriggers.PLACE_BANNER.get().trigger(player);
    }
}
