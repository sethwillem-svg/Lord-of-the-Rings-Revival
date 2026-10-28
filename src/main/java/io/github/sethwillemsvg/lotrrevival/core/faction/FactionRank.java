package io.github.sethwillemsvg.lotrrevival.core.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** One rung of a rank ladder. Ids match {@code [a-z0-9_]+}. */
public record FactionRank(String id, float threshold) {
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(
            id -> id.matches("[a-z0-9_]+") ? DataResult.success(id) : DataResult.error(() -> "Invalid rank id: " + id));

    public static final Codec<FactionRank> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ID_CODEC.fieldOf("id").forGetter(FactionRank::id),
                    Codec.FLOAT.fieldOf("threshold").forGetter(FactionRank::threshold))
            .apply(i, FactionRank::new));

    /** Ladder A, the default when a faction JSON omits {@code ranks}. */
    public static final List<FactionRank> LADDER_A = ladder(10, 50, 100, 200, 500, 1000, 1500, 3000);

    /** Builds a ladder with positional ids {@code r1..rN}. */
    public static List<FactionRank> ladder(float... thresholds) {
        FactionRank[] ranks = new FactionRank[thresholds.length];
        for (int n = 0; n < thresholds.length; n++) {
            ranks[n] = new FactionRank("r" + (n + 1), thresholds[n]);
        }
        return List.of(ranks);
    }
}
