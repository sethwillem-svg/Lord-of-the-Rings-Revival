package io.github.sethwillemsvg.lotrrevival.world.travel;

import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointView;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelRules;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointCommands;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** Every reason a fast travel request is refused, run when the request is made and again when the countdown ends. */
public final class TravelChecks {
    /**
     * Which levels count as Middle-earth. GameTest servers have no Middle-earth level, so tests swap this for a
     * predicate that accepts the test level and restore it in a {@code finally}.
     */
    public static volatile Predicate<ServerLevel> middleEarth =
            level -> level.dimension().equals(ModDimensions.MIDDLE_EARTH);

    private TravelChecks() {}

    /** The outcome of checking a request: the resolved target (when it exists) and the refusal reason, if any. */
    public record Outcome(Optional<TravelTarget> target, Optional<Component> refusal) {
        public boolean allowed() {
            return refusal.isEmpty();
        }
    }

    public static Outcome check(ServerPlayer player, WaypointRef ref) {
        if (!WorldConfig.get(WorldConfig.TRAVEL_ENABLED)) {
            return refused(Optional.empty(), "message.lotrrevival.travel.disabled");
        }
        if (!middleEarth.test(player.serverLevel())) {
            return refused(Optional.empty(), "message.lotrrevival.travel.wrong_dimension");
        }
        Optional<TravelTarget> resolved = TravelTarget.resolve(player, ref);
        if (resolved.isEmpty()) {
            return refused(Optional.empty(), "message.lotrrevival.travel.unknown");
        }
        TravelTarget target = resolved.get();
        if (target.fixed().isPresent()) {
            var status = TravelRules.status(target.fixed().get(), player);
            if (status.state() == WaypointView.State.LOCKED) {
                return new Outcome(
                        resolved,
                        Optional.of(Component.translatable("message.lotrrevival.travel.locked", target.name())));
            }
            if (status.state() == WaypointView.State.BARRED) {
                Component faction = status.barredBy().map(Factions::displayName).orElse(Component.empty());
                return new Outcome(
                        resolved, Optional.of(Component.translatable("message.lotrrevival.waypoint_barred", faction)));
            }
        }
        int clock = TravelData.get(player).clock();
        int required = requiredTicks(player, target);
        if (clock < required) {
            return new Outcome(
                    resolved,
                    Optional.of(Component.translatable(
                            "message.lotrrevival.travel.wait",
                            WaypointCommands.format(required - clock),
                            target.name())));
        }
        if (isTargeted(player)) {
            return refused(resolved, "message.lotrrevival.travel.targeted");
        }
        if (player.isSleeping()) {
            return refused(resolved, "message.lotrrevival.travel.sleeping");
        }
        return new Outcome(resolved, Optional.empty());
    }

    /** Required clock ticks for this trip from where the player stands. */
    public static int requiredTicks(ServerPlayer player, TravelTarget target) {
        double distance = Math.hypot(player.getX() - target.x(), player.getZ() - target.z());
        return TravelRules.requiredTicks(target.uses(), distance);
    }

    /** Whether a mob within the hostile range has this player as its attack target. Creative and spectators are exempt. */
    public static boolean isTargeted(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        double range = WorldConfig.get(WorldConfig.TRAVEL_HOSTILE_RANGE);
        return !player.level()
                .getEntitiesOfClass(
                        Mob.class,
                        player.getBoundingBox().inflate(range),
                        mob -> mob.isAlive() && mob.getTarget() == player && mob.distanceToSqr(player) <= range * range)
                .isEmpty();
    }

    private static Outcome refused(Optional<TravelTarget> target, String key) {
        return new Outcome(target, Optional.of(Component.translatable(key)));
    }
}
