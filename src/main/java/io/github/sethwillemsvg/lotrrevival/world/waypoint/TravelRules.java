package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.api.AchievementCounters;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointView;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Pure waypoint and fast travel rules (specs/world/waypoints-travel.md, "Rules and numbers"). */
public final class TravelRules {
    private TravelRules() {}

    /**
     * State of a fixed waypoint for a player.
     *
     * @param listed false for a hidden waypoint whose region is still locked
     * @param state locked, usable or barred
     * @param barredBy the faction that bars the player, only when {@code state} is {@code BARRED}
     */
    public record Status(boolean listed, WaypointView.State state, Optional<ResourceKey<Faction>> barredBy) {}

    /**
     * Locked until its region is unlocked; then barred when it belongs to a faction the player's alignment with is
     * below 0; otherwise usable.
     */
    public static Status status(
            Waypoint waypoint, Set<ResourceLocation> unlockedRegions, ToDoubleFunction<ResourceKey<Faction>> alignment) {
        if (!unlockedRegions.contains(waypoint.region())) {
            return new Status(!waypoint.hidden(), WaypointView.State.LOCKED, Optional.empty());
        }
        Optional<ResourceKey<Faction>> faction = waypoint.factionKey();
        if (faction.isPresent() && alignment.applyAsDouble(faction.get()) < 0) {
            return new Status(true, WaypointView.State.BARRED, faction);
        }
        return new Status(true, WaypointView.State.USABLE, Optional.empty());
    }

    public static Status status(Waypoint waypoint, Player player) {
        return status(waypoint, TravelData.get(player).regions(), faction -> Alignment.get(player, faction));
    }

    /**
     * {@code ceil(20 x (min + (max - min) x decay^uses) x max(1, distance x factor))}, uses capped at 1,000.
     *
     * @param distance horizontal distance in blocks
     */
    public static int requiredTicks(
            int minSeconds, int maxSeconds, double decay, double distanceFactor, int uses, double distance) {
        int capped = Math.max(0, Math.min(TravelData.MAX_USES, uses));
        double seconds = minSeconds + (maxSeconds - minSeconds) * Math.pow(decay, capped);
        double multiplier = Math.max(1.0, distance * distanceFactor);
        // Subtract a hair so floating point noise (1.2240000000000002) never adds a tick.
        double ticks = Math.ceil(20.0 * seconds * multiplier - 1e-6);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, ticks));
    }

    /** Required ticks with the configured numbers. */
    public static int requiredTicks(int uses, double distance) {
        return requiredTicks(
                WorldConfig.get(WorldConfig.TRAVEL_MIN_SECONDS),
                WorldConfig.get(WorldConfig.TRAVEL_MAX_SECONDS),
                WorldConfig.get(WorldConfig.TRAVEL_USE_DECAY),
                WorldConfig.get(WorldConfig.TRAVEL_DISTANCE_FACTOR),
                uses,
                distance);
    }

    /** Custom waypoint limit: {@code base + floor(achievements / perAchievements)}. */
    public static int customLimit(int achievementCount, int base, int perAchievements) {
        return base + Math.max(0, achievementCount) / Math.max(1, perAchievements);
    }

    /** Custom waypoint limit for a player with the configured numbers and their counted achievements. */
    public static int customLimit(Player player) {
        return customLimit(
                AchievementCounters.count(player),
                WorldConfig.get(WorldConfig.WAYPOINTS_CUSTOM_BASE),
                WorldConfig.get(WorldConfig.WAYPOINTS_CUSTOM_PER_ACHIEVEMENTS));
    }

    /** Creating is refused at or over the limit; existing waypoints are never removed. */
    public static boolean canCreateCustom(int existing, int limit) {
        return existing < limit && existing < TravelData.MAX_CUSTOM;
    }

    /**
     * Clock ticks credited at login: {@code floor(fraction x offlineSeconds x 20)}, only on a dedicated server, and
     * nothing when the logout time is unknown (0) or in the future.
     */
    public static long offlineCreditTicks(double fraction, long lastLogoutMillis, long nowMillis, boolean dedicatedServer) {
        if (!dedicatedServer || lastLogoutMillis <= 0 || lastLogoutMillis >= nowMillis) {
            return 0L;
        }
        double offlineSeconds = (nowMillis - lastLogoutMillis) / 1000.0;
        return (long) Math.floor(fraction * offlineSeconds * 20.0 + 1e-9);
    }
}
