package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.core.api.WaypointView;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds the {@link WaypointView}s the map page shows, from the synced waypoint registry and a player's travel data.
 * Pure (no client classes), so the client facade and the GameTests share it.
 */
public final class WaypointViews {
    private WaypointViews() {}

    /**
     * All waypoints the player may see: the fixed ones that are not hidden-and-locked, sorted by id, then the custom
     * ones in the order they were made.
     *
     * @param access registry access holding the synced {@code waypoint} registry (missing registry gives only customs)
     * @param data the player's travel data (regions, uses, custom list)
     * @param alignment the player's alignment with a faction
     * @param playerX the player's block x, for the distance part of the required time
     * @param playerZ the player's block z
     */
    public static List<WaypointView> build(
            RegistryAccess access,
            TravelData data,
            ToDoubleFunction<ResourceKey<Faction>> alignment,
            double playerX,
            double playerZ) {
        List<WaypointView> views = new ArrayList<>();
        Optional<Registry<Waypoint>> registry = access.registry(ModDataRegistries.<Waypoint>waypoint());
        if (registry.isPresent()) {
            List<ResourceLocation> ids = new ArrayList<>(registry.get().keySet());
            ids.sort(null);
            for (ResourceLocation id : ids) {
                Waypoint waypoint = registry.get().get(id);
                if (waypoint == null) {
                    continue;
                }
                TravelRules.Status status = TravelRules.status(waypoint, data.regions(), alignment);
                if (!status.listed()) {
                    continue;
                }
                double distance = Math.hypot(playerX - waypoint.blockX(), playerZ - waypoint.blockZ());
                int uses = data.usesOf(id);
                views.add(new WaypointView(
                        new WaypointRef.Fixed(id).wire(),
                        waypoint.name(),
                        waypoint.lore(),
                        (int) Math.round(waypoint.u()),
                        (int) Math.round(waypoint.v()),
                        WaypointView.Kind.FIXED,
                        status.state(),
                        status.barredBy(),
                        uses,
                        TravelRules.requiredTicks(uses, distance)));
            }
        }
        for (TravelData.Custom custom : data.custom()) {
            double x = custom.pos().getX() + 0.5;
            double z = custom.pos().getZ() + 0.5;
            views.add(new WaypointView(
                    new WaypointRef.Custom(custom.id()).wire(),
                    Component.literal(custom.name()),
                    Component.empty(),
                    pixelU(x),
                    pixelV(z),
                    WaypointView.Kind.CUSTOM,
                    WaypointView.State.USABLE,
                    Optional.empty(),
                    custom.uses(),
                    TravelRules.requiredTicks(custom.uses(), Math.hypot(playerX - x, playerZ - z))));
        }
        return views;
    }

    /** Heightmap pixel x (rounded) of a world block x. Inverse of {@link MiddleEarthMap#blockX}. */
    public static int pixelU(double blockX) {
        return (int) Math.round(MiddleEarthMap.ORIGIN_U + blockX / MiddleEarthMap.HEIGHT_BLOCKS_PER_PIXEL);
    }

    /** Heightmap pixel y (rounded) of a world block z. Inverse of {@link MiddleEarthMap#blockZ}. */
    public static int pixelV(double blockZ) {
        return (int) Math.round(MiddleEarthMap.ORIGIN_V + blockZ / MiddleEarthMap.HEIGHT_BLOCKS_PER_PIXEL);
    }
}
