package io.github.sethwillemsvg.lotrrevival.client.progression;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEligibility;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntries;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementsDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementsFullPayload;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Read-only client mirror of the local player's earned achievement ids. Only the payload handlers write to
 * it. Holds plain data only (no client-only classes) so common code such as {@code ProgressionApi} may read it.
 */
public final class ClientAchievements {
    private static final Set<ResourceLocation> EARNED = new HashSet<>();
    private static final Set<ResourceLocation> PENDING = new HashSet<>();

    private ClientAchievements() {}

    /** Collects the parts of a full sync; the earned set is swapped in when the last part arrives. */
    public static synchronized void applyFull(AchievementsFullPayload payload) {
        PENDING.addAll(payload.ids());
        if (payload.last()) {
            EARNED.clear();
            EARNED.addAll(PENDING);
            PENDING.clear();
        }
    }

    public static synchronized void applyDelta(AchievementsDeltaPayload payload) {
        EARNED.removeAll(payload.removed());
        EARNED.addAll(payload.added());
    }

    /** Forgets everything (on disconnect). */
    public static synchronized void clear() {
        EARNED.clear();
        PENDING.clear();
    }

    /** Snapshot of the earned ids (including entries that are not eligible right now). */
    public static synchronized Set<ResourceLocation> earned() {
        return Set.copyOf(EARNED);
    }

    public static synchronized boolean isEarned(ResourceLocation id) {
        return EARNED.contains(id);
    }

    /** Earned, eligible and counted entries; only meaningful for the local player. */
    public static int count(Player player) {
        int count = 0;
        for (ResourceLocation id : earned()) {
            AchievementEntry entry = AchievementEntries.find(player.level().registryAccess(), id)
                    .orElse(null);
            if (entry != null && entry.counted() && AchievementEligibility.isEligible(player, entry)) {
                count++;
            }
        }
        return count;
    }
}
