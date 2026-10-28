package io.github.sethwillemsvg.lotrrevival.world.road;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Registrations of the road system: the synced road registry, the corridor density function and the index. */
public final class RoadMod {
    /** Feature {@code lotrrevival:road}. */
    public static final DeferredHolder<Feature<?>, RoadFeature> ROAD_FEATURE =
            ModRegistries.FEATURES.register("road", () -> new RoadFeature(NoneFeatureConfiguration.CODEC));

    public static final ResourceKey<ConfiguredFeature<?, ?>> ROAD_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, LOTRRevival.id("road"));

    public static final ResourceKey<PlacedFeature> ROAD_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, LOTRRevival.id("road"));

    private RoadMod() {}

    public static void init() {
        ModDataRegistries.registerSynced(ModDataRegistries.<Road>road(), Road.CODEC);
        ModRegistries.DENSITY_FUNCTION_TYPES.register("map_corridors", () -> MapCorridors.MAP_CODEC);
        RoadNetworks.register();
    }
}
