package io.github.sethwillemsvg.lotrrevival.world.road;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;

/** Registrations of the road system: the synced road registry, the corridor density function and the index. */
public final class RoadMod {
    private RoadMod() {}

    public static void init() {
        ModDataRegistries.registerSynced(ModDataRegistries.<Road>road(), Road.CODEC);
        ModRegistries.DENSITY_FUNCTION_TYPES.register("map_corridors", () -> MapCorridors.MAP_CODEC);
        RoadNetworks.register();
    }
}
