package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Inclusive integer range an objective rolls its target count from when an offer is generated. */
public record CountRange(int min, int max) {
    public static final Codec<CountRange> CODEC = RecordCodecBuilder.<CountRange>create(i -> i.group(
                    Codec.intRange(1, 100_000).fieldOf("min").forGetter(CountRange::min),
                    Codec.intRange(1, 100_000).fieldOf("max").forGetter(CountRange::max))
            .apply(i, CountRange::new))
            .validate(range -> range.max() < range.min()
                    ? DataResult.error(() -> "count max must not be below min")
                    : DataResult.success(range));

    public static final CountRange ONE = new CountRange(1, 1);

    /** Uniform in {@code [min, max]}. */
    public int roll(RandomSource random) {
        return Mth.nextInt(random, min, max);
    }

    public boolean contains(int value) {
        return value >= min && value <= max;
    }
}
