package io.github.sethwillemsvg.lotrrevival.core;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/** Keys for the Middle-earth dimension. The World pod owns the dimension JSON. */
public final class ModDimensions {
    public static final ResourceKey<Level> MIDDLE_EARTH =
            ResourceKey.create(Registries.DIMENSION, LOTRRevival.id("middle_earth"));
    public static final ResourceKey<LevelStem> MIDDLE_EARTH_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, LOTRRevival.id("middle_earth"));
    public static final ResourceKey<DimensionType> MIDDLE_EARTH_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, LOTRRevival.id("middle_earth"));

    private ModDimensions() {}
}
