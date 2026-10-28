package io.github.sethwillemsvg.lotrrevival.progression.banner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * An entry of the synced {@code lotrrevival:banner_design} datapack registry: the faction a banner stands for and its
 * colours. Files at {@code data/<ns>/lotrrevival/banner_design/<id>.json}.
 *
 * @param color packed 0xRRGGBB cloth colour
 * @param emblemColor packed 0xRRGGBB emblem colour
 * @param texture optional cloth texture; the generic cloth is used when absent
 */
public record BannerDesign(
        ResourceKey<Faction> faction, int color, int emblemColor, Optional<ResourceLocation> texture) {
    public static final ResourceKey<Registry<BannerDesign>> REGISTRY =
            ResourceKey.createRegistryKey(LOTRRevival.id("banner_design"));

    public static final Codec<BannerDesign> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceKey.codec(ModDataRegistries.FACTION).fieldOf("faction").forGetter(BannerDesign::faction),
                    Faction.COLOR_CODEC.fieldOf("color").forGetter(BannerDesign::color),
                    Faction.COLOR_CODEC.fieldOf("emblem_color").forGetter(BannerDesign::emblemColor),
                    ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(BannerDesign::texture))
            .apply(i, BannerDesign::new));

    /** The generic cloth texture. */
    public static final ResourceLocation DEFAULT_TEXTURE = LOTRRevival.id("textures/entity/banner/cloth.png");

    /** The emblem overlay texture. */
    public static final ResourceLocation EMBLEM_TEXTURE = LOTRRevival.id("textures/entity/banner/emblem.png");

    /** Design ids shipped by datagen: one per player-facing faction, {@code lotrrevival:<faction path>}. */
    public static List<ResourceLocation> defaultIds() {
        return Factions.REAL.stream().map(ResourceKey::location).toList();
    }

    /** The id of the default design of a faction. */
    public static ResourceLocation idFor(ResourceKey<Faction> faction) {
        return faction.location();
    }

    public ResourceLocation clothTexture() {
        return texture.orElse(DEFAULT_TEXTURE);
    }
}
