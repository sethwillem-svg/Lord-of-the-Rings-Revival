package io.github.sethwillemsvg.lotrrevival.npc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

/**
 * A rolled offer on a trader: what one lot is, its rolled price, the recent value traded on it and its timed lock.
 * Mutable: the counters change as the trader is used. Owned by one {@link TraderStock}.
 */
public final class TradeOffer {
    public static final Codec<TradeOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.optionalFieldOf("item").forGetter(o -> o.item),
                    ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(o -> o.tag),
                    Codec.INT.fieldOf("count").forGetter(o -> o.count),
                    Codec.INT.fieldOf("price").forGetter(o -> o.price),
                    Codec.INT.optionalFieldOf("recent_value", 0).forGetter(o -> o.recentValue),
                    Codec.INT.optionalFieldOf("lock_ticks", 0).forGetter(o -> o.lockTicks),
                    Codec.BOOL.optionalFieldOf("value_locked", false).forGetter(o -> o.valueLocked))
            .apply(i, TradeOffer::new));

    private final Optional<ResourceLocation> item;
    private final Optional<ResourceLocation> tag;
    private final int count;
    private final int price;
    private int recentValue;
    private int lockTicks;
    /** Set when recent value reaches the lock value; stays set until the value has decayed to 0. */
    private boolean valueLocked;

    public TradeOffer(
            Optional<ResourceLocation> item,
            Optional<ResourceLocation> tag,
            int count,
            int price,
            int recentValue,
            int lockTicks,
            boolean valueLocked) {
        this.item = item;
        this.tag = tag;
        this.count = count;
        this.price = price;
        this.recentValue = recentValue;
        this.lockTicks = lockTicks;
        this.valueLocked = valueLocked;
    }

    public static TradeOffer of(TradeEntry entry, int price) {
        return new TradeOffer(entry.item(), entry.tag(), entry.count(), price, 0, 0, false);
    }

    public boolean isValueLocked() {
        return valueLocked;
    }

    public void setValueLocked(boolean valueLocked) {
        this.valueLocked = valueLocked;
    }

    /** The lot as a stack; empty for tag offers (they only match) and for items that no longer exist. */
    public ItemStack stack() {
        return item.flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(found -> new ItemStack(found, count))
                .orElse(ItemStack.EMPTY);
    }

    /** Whether a stack can be sold into this offer (item or tag match; the count is not checked). */
    public boolean matches(ItemStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (item.isPresent()) {
            return BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(item.get());
        }
        return tag.isPresent() && candidate.is(TagKey.create(Registries.ITEM, tag.get()));
    }

    public Optional<ResourceLocation> item() {
        return item;
    }

    public Optional<ResourceLocation> tag() {
        return tag;
    }

    public int count() {
        return count;
    }

    public int price() {
        return price;
    }

    public int recentValue() {
        return recentValue;
    }

    public int lockTicks() {
        return lockTicks;
    }

    public void setRecentValue(int recentValue) {
        this.recentValue = recentValue;
    }

    public void setLockTicks(int lockTicks) {
        this.lockTicks = lockTicks;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TradeOffer o
                && count == o.count
                && price == o.price
                && recentValue == o.recentValue
                && lockTicks == o.lockTicks
                && valueLocked == o.valueLocked
                && item.equals(o.item)
                && tag.equals(o.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, tag, count, price, recentValue, lockTicks, valueLocked);
    }

    @Override
    public String toString() {
        return "TradeOffer[" + item.map(ResourceLocation::toString).orElseGet(() -> "#" + tag.orElseThrow())
                + " x" + count + " @" + price + " recent=" + recentValue + " lock=" + lockTicks + "]";
    }
}
