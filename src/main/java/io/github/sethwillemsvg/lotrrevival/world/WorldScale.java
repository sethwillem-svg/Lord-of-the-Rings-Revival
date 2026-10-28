package io.github.sethwillemsvg.lotrrevival.world;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * How big Middle-earth is in blocks, relative to Legacy (see {@code specs/world/dimension-and-mapgen.md}, "World
 * scale"). Chosen when a world is created and fixed for that world: changing it later moves every coast, road and
 * waypoint, so already generated chunks would no longer line up with new ones.
 *
 * <p>The painted map never changes. Only the number of blocks per map pixel does: at {@link #HALF} one heightmap
 * pixel is 32 blocks instead of 64, so every horizontal distance is halved.
 *
 * <p>Heights are compressed toward sea level by {@link #verticalFactor()}, the square root of the horizontal factor
 * (rounded). Shrinking the map horizontally alone would make every painted slope 2x (half) or 4x (quarter) as
 * steep; shrinking heights by the same factor would flatten mountains into hills. The square root splits the
 * difference: painted slopes grow by only 1.4x (half) or 2x (quarter) while mountains keep 70% or 50% of their
 * height above the sea. The block-scale detail noise is not scaled at all, so small hills feel the same everywhere.
 *
 * @param factor horizontal blocks per Legacy block (1, 0.5 or 0.25)
 * @param verticalFactor multiplier applied to painted heights above (and depths below) sea level
 */
public enum WorldScale implements StringRepresentable {
    LEGACY(1.0, 1.0),
    HALF(0.5, 0.7),
    QUARTER(0.25, 0.5);

    /** The scale new worlds get unless a world-scale data pack is enabled at creation. */
    public static final WorldScale DEFAULT = HALF;

    public static final Codec<WorldScale> CODEC = StringRepresentable.fromEnum(WorldScale::values);

    private final double factor;
    private final double verticalFactor;

    WorldScale(double factor, double verticalFactor) {
        this.factor = factor;
        this.verticalFactor = verticalFactor;
    }

    /** Horizontal blocks per Legacy block. */
    public double factor() {
        return factor;
    }

    /** Painted heights are {@code sea + verticalFactor x (Legacy height - sea)}. */
    public double verticalFactor() {
        return verticalFactor;
    }

    /** {@code legacy}, {@code half} or {@code quarter}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    /** Blocks per heightmap pixel: 64, 32 or 16. */
    public double heightBlocksPerPixel() {
        return MiddleEarthMap.HEIGHT_BLOCKS_PER_PIXEL * factor;
    }

    /** Blocks per biome-map pixel: 128, 64 or 32. */
    public double biomeBlocksPerPixel() {
        return MiddleEarthMap.BIOME_BLOCKS_PER_PIXEL * factor;
    }

    /** Blocks per relief-map pixel: 256, 128 or 64. */
    public double reliefBlocksPerPixel() {
        return MiddleEarthMap.RELIEF_BLOCKS_PER_PIXEL * factor;
    }

    /** A horizontal distance written for Legacy scale (config defaults, spec numbers), in blocks at this scale. */
    public double blocks(double legacyBlocks) {
        return legacyBlocks * factor;
    }

    /** {@link #blocks(double)} rounded to whole blocks. */
    public int blocks(int legacyBlocks) {
        return (int) Math.round(legacyBlocks * factor);
    }

    /** A horizontal distance at this scale expressed in Legacy blocks (the inverse of {@link #blocks(double)}). */
    public double legacyBlocks(double blocks) {
        return blocks / factor;
    }

    /** A painted (Legacy-scale) surface height at this scale; sea level stays where it is. */
    public double surfaceY(double legacyY) {
        return MiddleEarthMap.SEA_LEVEL + verticalFactor * (legacyY - MiddleEarthMap.SEA_LEVEL);
    }

    /** Name of the built-in data pack that selects this scale; the default scale needs no pack. */
    public String packName() {
        return "world_scale_" + id();
    }
}
