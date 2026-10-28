package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a viewer of the trade screen needs to know about an offer. {@code lotsLeft} is how many more lots can be
 * traded before the offer locks (the lot that crosses the lock still completes), 0 if it cannot be traded now.
 */
public record OfferView(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, int count, int price, int lotsLeft) {
    public static final StreamCodec<io.netty.buffer.ByteBuf, OfferView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            OfferView::item,
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            OfferView::tag,
            ByteBufCodecs.VAR_INT,
            OfferView::count,
            ByteBufCodecs.VAR_INT,
            OfferView::price,
            ByteBufCodecs.VAR_INT,
            OfferView::lotsLeft,
            OfferView::new);

    public static OfferView of(TradeOffer offer) {
        int lots = 0;
        if (TraderStocks.isTradable(offer)) {
            int room = Math.max(1, TraderStocks.LOCK_VALUE - offer.recentValue());
            lots = Math.max(1, (room + offer.price() - 1) / offer.price());
        }
        return new OfferView(offer.item(), offer.tag(), offer.count(), offer.price(), lots);
    }

    public boolean tradable() {
        return lotsLeft > 0;
    }

    /** The lot as a stack for display; for a tag offer the first member of the tag, a barrier if none is known. */
    public ItemStack displayStack() {
        if (item.isPresent()) {
            return BuiltInRegistries.ITEM
                    .getOptional(item.get())
                    .map(found -> new ItemStack(found, Math.max(1, Math.min(count, found.getDefaultMaxStackSize()))))
                    .orElse(new ItemStack(Items.BARRIER));
        }
        if (tag.isPresent()) {
            return BuiltInRegistries.ITEM
                    .getTag(TagKey.create(Registries.ITEM, tag.get()))
                    .flatMap(set -> set.stream().findFirst())
                    .map(holder -> new ItemStack(holder.value(), 1))
                    .orElse(new ItemStack(Items.BARRIER));
        }
        return new ItemStack(Items.BARRIER);
    }

    /** Whether a stack can be sold into this offer (same rule as {@link TradeOffer#matches}). */
    public boolean matches(ItemStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (item.isPresent()) {
            return BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(item.get());
        }
        return tag.isPresent() && candidate.is(TagKey.create(Registries.ITEM, tag.get()));
    }
}
