package io.github.sethwillemsvg.lotrrevival.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * The {@code lotrrevival:world_scale} level attachment (kept on the overworld): the scale this world's Middle-earth
 * was first generated at. Written once, on the first start after this attachment existed, and only read afterwards
 * to warn when the active scale no longer matches.
 *
 * @param madeWith the scale recorded for this world; empty until the first start
 * @param assumed true when the world already had Middle-earth terrain before scales existed, so Legacy was assumed
 */
public record WorldScaleLock(Optional<WorldScale> madeWith, boolean assumed) {
    public static final WorldScaleLock EMPTY = new WorldScaleLock(Optional.empty(), false);

    public static final Codec<WorldScaleLock> CODEC = RecordCodecBuilder.create(i -> i.group(
                    WorldScale.CODEC.optionalFieldOf("made_with").forGetter(WorldScaleLock::madeWith),
                    Codec.BOOL.optionalFieldOf("assumed", false).forGetter(WorldScaleLock::assumed))
            .apply(i, WorldScaleLock::new));

    /**
     * The lock for a world being started with {@code active} as its data-pack scale.
     *
     * @param hasTerrain whether Middle-earth chunks were already saved; with no lock yet that means the world
     *     predates world scales and was generated at Legacy scale
     */
    public WorldScaleLock settle(WorldScale active, boolean hasTerrain) {
        if (madeWith.isPresent()) {
            return this;
        }
        return hasTerrain
                ? new WorldScaleLock(Optional.of(WorldScale.LEGACY), true)
                : new WorldScaleLock(Optional.of(active), false);
    }

    /** The recorded scale when it differs from {@code active}. */
    public Optional<WorldScale> mismatch(WorldScale active) {
        return madeWith.filter(made -> made != active);
    }
}
