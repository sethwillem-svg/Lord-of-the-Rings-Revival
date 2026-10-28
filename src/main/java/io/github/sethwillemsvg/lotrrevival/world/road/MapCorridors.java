package io.github.sethwillemsvg.lotrrevival.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code lotrrevival:map_corridors}: {@code input x mask(x, z)}. The mask is 0 within a road's half-width and
 * smoothsteps to 1 at half-width plus {@code falloff} (see {@link RoadNetwork#mask}). Wrapped around the detail
 * term of the Middle-earth noise settings, so the painted base is untouched and roads run through gentle corridors.
 *
 * <p>Extension point: other mask sources (structures spec: landmark radii, 0 inside the radius and ramping to 1
 * over 64 blocks) call {@link #addMask(Mask)}; the final mask is the smallest of all sources.
 */
public record MapCorridors(DensityFunction input, double falloff) implements DensityFunction {
    public static final double DEFAULT_FALLOFF = 32.0;

    public static final MapCodec<MapCorridors> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(MapCorridors::input),
                    Codec.doubleRange(1.0, 1024.0)
                            .optionalFieldOf("falloff", DEFAULT_FALLOFF)
                            .forGetter(MapCorridors::falloff))
            .apply(i, MapCorridors::new));

    public static final KeyDispatchDataCodec<MapCorridors> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** An additional source of terrain-detail mask, for example landmark radii. */
    @FunctionalInterface
    public interface Mask {
        /** 0 (flatten fully) to 1 (untouched) at block (x, z). Must be thread safe. */
        double at(double x, double z);
    }

    private static final List<Mask> EXTRA_MASKS = new CopyOnWriteArrayList<>();

    /** Registers another mask source; call during mod init. */
    public static void addMask(Mask mask) {
        EXTRA_MASKS.add(mask);
    }

    /** The combined mask at block (x, z) for the given falloff. */
    public static double mask(double x, double z, double falloff) {
        double mask = RoadNetworks.current().mask(x, z, falloff);
        for (Mask extra : EXTRA_MASKS) {
            if (mask <= 0) {
                return 0;
            }
            mask = Math.min(mask, extra.at(x, z));
        }
        return mask;
    }

    @Override
    public double compute(FunctionContext context) {
        double mask = mask(context.blockX(), context.blockZ(), falloff);
        return mask <= 0 ? 0 : input.compute(context) * mask;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new MapCorridors(input.mapAll(visitor), falloff));
    }

    @Override
    public double minValue() {
        return Math.min(input.minValue(), 0);
    }

    @Override
    public double maxValue() {
        return Math.max(input.maxValue(), 0);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
