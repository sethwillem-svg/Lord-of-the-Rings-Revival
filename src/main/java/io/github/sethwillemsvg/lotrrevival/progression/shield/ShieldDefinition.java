package io.github.sethwillemsvg.lotrrevival.progression.shield;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * An entry of the synced datapack registry {@code lotrrevival:shield}, files at
 * {@code data/<ns>/lotrrevival/shield/<name>.json}. Purely cosmetic.
 *
 * @param texture 32x32 texture; the design is on the front (2,2 size 10x16) and back (14,2) faces of the plate
 * @param hiddenUntilUnlocked menu hint: do not list the shield until the player can use it
 * @param sort menu order, ascending
 */
public record ShieldDefinition(ShieldUnlock unlock, ResourceLocation texture, boolean hiddenUntilUnlocked, int sort) {
    public static final ResourceKey<Registry<ShieldDefinition>> REGISTRY =
            ResourceKey.createRegistryKey(LOTRRevival.id("shield"));

    /** JSON codec; also the network codec of the synced registry. */
    public static final Codec<ShieldDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ShieldUnlock.CODEC.fieldOf("unlock").forGetter(ShieldDefinition::unlock),
                    ResourceLocation.CODEC.fieldOf("texture").forGetter(ShieldDefinition::texture),
                    Codec.BOOL.optionalFieldOf("hidden_until_unlocked", false).forGetter(ShieldDefinition::hiddenUntilUnlocked),
                    Codec.INT.optionalFieldOf("sort", 0).forGetter(ShieldDefinition::sort))
            .apply(i, ShieldDefinition::new));

    /** Lang key of a shield's name: {@code shield.<namespace>.<path with dots>}. */
    public static String nameKey(ResourceLocation id) {
        return "shield." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
}
