package io.github.sethwillemsvg.lotrrevival.world.travel;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.Waypoint;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A waypoint resolved for one player.
 *
 * @param ref the reference as requested
 * @param name display name
 * @param x block x of the destination column
 * @param z block z of the destination column
 * @param customPos the stored block position, for custom waypoints only
 * @param uses arrivals at this waypoint so far
 * @param fixed the registry entry, for fixed waypoints only
 */
public record TravelTarget(
        WaypointRef ref,
        Component name,
        double x,
        double z,
        Optional<BlockPos> customPos,
        int uses,
        Optional<Waypoint> fixed) {
    public boolean custom() {
        return ref instanceof WaypointRef.Custom;
    }

    /** Block column of the destination. */
    public BlockPos column() {
        return customPos.orElseGet(() -> BlockPos.containing(x, 0, z));
    }

    /** Resolves a ref against the registry and the player's custom list; empty when it no longer exists. */
    public static Optional<TravelTarget> resolve(ServerPlayer player, WaypointRef ref) {
        TravelData data = TravelData.get(player);
        if (ref instanceof WaypointRef.Fixed fixed) {
            Registry<Waypoint> registry =
                    player.server.registryAccess().registryOrThrow(ModDataRegistries.<Waypoint>waypoint());
            Waypoint waypoint = registry.get(fixed.id());
            if (waypoint == null) {
                return Optional.empty();
            }
            return Optional.of(new TravelTarget(
                    ref,
                    waypoint.name(),
                    waypoint.blockX(),
                    waypoint.blockZ(),
                    Optional.empty(),
                    data.usesOf(fixed.id()),
                    Optional.of(waypoint)));
        }
        int id = ((WaypointRef.Custom) ref).id();
        for (TravelData.Custom custom : data.custom()) {
            if (custom.id() == id) {
                return Optional.of(new TravelTarget(
                        ref,
                        Component.literal(custom.name()),
                        custom.pos().getX() + 0.5,
                        custom.pos().getZ() + 0.5,
                        Optional.of(custom.pos()),
                        custom.uses(),
                        Optional.empty()));
            }
        }
        return Optional.empty();
    }
}
