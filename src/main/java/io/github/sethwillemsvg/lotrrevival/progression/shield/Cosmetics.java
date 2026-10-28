package io.github.sethwillemsvg.lotrrevival.progression.shield;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Data of the {@code lotrrevival:cosmetics} player attachment. The selected shield stays selected even when its
 * unlock no longer holds or its definition is gone; it is only validated when selecting.
 */
public record Cosmetics(Optional<ResourceLocation> shield) {
    public static final Cosmetics EMPTY = new Cosmetics(Optional.empty());

    public static final Codec<Cosmetics> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.optionalFieldOf("shield").forGetter(Cosmetics::shield))
            .apply(i, Cosmetics::new));
}
