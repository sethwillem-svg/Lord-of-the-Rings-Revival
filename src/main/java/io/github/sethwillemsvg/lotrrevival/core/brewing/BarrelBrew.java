package io.github.sethwillemsvg.lotrrevival.core.brewing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The brew held by a barrel item (data component {@code lotrrevival:barrel_brew}): state (brewing or
 * full), drink item id, tier, servings and elapsed ticks.
 */
public record BarrelBrew(BarrelState state, ResourceLocation drink, int tier, int servings, int elapsed) {
    public static final Codec<BarrelBrew> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    BarrelState.CODEC.fieldOf("state").forGetter(BarrelBrew::state),
                    ResourceLocation.CODEC.fieldOf("drink").forGetter(BarrelBrew::drink),
                    Codec.intRange(DrinkTier.MIN_INDEX, DrinkTier.MAX_INDEX).fieldOf("tier").forGetter(BarrelBrew::tier),
                    Codec.intRange(0, BrewingBarrelBlockEntity.SERVINGS)
                            .fieldOf("servings")
                            .forGetter(BarrelBrew::servings),
                    Codec.intRange(0, BrewingBarrelBlockEntity.TOTAL_TICKS)
                            .fieldOf("elapsed")
                            .forGetter(BarrelBrew::elapsed))
            .apply(instance, BarrelBrew::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, BarrelBrew> STREAM_CODEC = StreamCodec.composite(
            BarrelState.STREAM_CODEC, BarrelBrew::state,
            ResourceLocation.STREAM_CODEC, BarrelBrew::drink,
            ByteBufCodecs.VAR_INT, BarrelBrew::tier,
            ByteBufCodecs.VAR_INT, BarrelBrew::servings,
            ByteBufCodecs.VAR_INT, BarrelBrew::elapsed,
            BarrelBrew::new);
}
