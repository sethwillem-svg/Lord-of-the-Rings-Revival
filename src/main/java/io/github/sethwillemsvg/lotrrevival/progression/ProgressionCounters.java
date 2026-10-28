package io.github.sethwillemsvg.lotrrevival.progression;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Read API over {@code progress_counters} for other Progression systems. Only Progression code
 * writes the attachment.
 */
public final class ProgressionCounters {
    private ProgressionCounters() {}

    public static ProgressCounters get(Player player) {
        return player.getData(ProgressionAttachments.PROGRESS_COUNTERS);
    }

    public static int visitedBiomeCount(Player player) {
        return get(player).visitedBiomes().size();
    }

    public static Set<ResourceLocation> visitedBiomes(Player player) {
        return get(player).visitedBiomes();
    }

    public static int killsByFaction(Player player, ResourceLocation faction) {
        return get(player).killsByFaction().getOrDefault(faction, 0);
    }

    public static int tradesTotal(Player player) {
        return get(player).tradesTotal();
    }
}
