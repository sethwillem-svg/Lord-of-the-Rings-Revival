package io.github.sethwillemsvg.lotrrevival.npc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * One line of a trade pool: an item or an item tag, the count per lot and the base price (before the +-25% roll).
 * Exactly one of {@code item} and {@code tag} is present. Tags are only meaningful for player-sells entries.
 */
public record TradeEntry(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, int count, int price) {
    /** A tag is written {@code "#namespace:path"}; the leading hash is optional when reading. */
    private static final Codec<ResourceLocation> TAG_CODEC = Codec.STRING.comapFlatMap(
            text -> ResourceLocation.read(text.startsWith("#") ? text.substring(1) : text), id -> "#" + id);

    public static final Codec<TradeEntry> CODEC = RecordCodecBuilder.<TradeEntry>create(i -> i.group(
                            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(TradeEntry::item),
                            TAG_CODEC.optionalFieldOf("tag").forGetter(TradeEntry::tag),
                            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(TradeEntry::count),
                            Codec.intRange(1, 1_000_000).fieldOf("price").forGetter(TradeEntry::price))
                    .apply(i, TradeEntry::new))
            .validate(entry -> entry.item.isPresent() == entry.tag.isPresent()
                    ? DataResult.error(() -> "trade entry needs exactly one of item and tag")
                    : DataResult.success(entry));

    public static TradeEntry item(ItemLike item, int count, int price) {
        return new TradeEntry(Optional.of(BuiltInRegistries.ITEM.getKey(item.asItem())), Optional.empty(), count, price);
    }

    public static TradeEntry item(ItemLike item, int price) {
        return item(item, 1, price);
    }

    public static TradeEntry tag(ResourceLocation tag, int count, int price) {
        return new TradeEntry(Optional.empty(), Optional.of(tag), count, price);
    }
}
