package io.github.sethwillemsvg.lotrrevival.nodemap.travel;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Paying for a trip in Middle-earth's coins (spec Cost, {@code travel.coin_per_1000}; paid like hiring): copper, silver
 * and gold worth 1, 10 and 100 copper. Coins are taken from the inventory smallest first, one at a time, until the price
 * is covered; the overpay comes back as change in the largest coins that fit. {@link #plan} is the pure part.
 */
public final class CoinPurse {
    /** Middle-earth's coin item ids, smallest first (checked against Middle-earth's ResourceItemsME). */
    public static final ResourceLocation[] IDS = {
            ResourceLocation.fromNamespaceAndPath("middle-earth", "copper_coin"),
            ResourceLocation.fromNamespaceAndPath("middle-earth", "silver_coin"),
            ResourceLocation.fromNamespaceAndPath("middle-earth", "gold_coin")};
    /** Value of each coin in copper, same order as {@link #IDS}. */
    public static final int[] VALUES = {1, 10, 100};

    private static boolean warnedMissing;

    private CoinPurse() {}

    /** What a payment does: coins taken and coins given back as change, per kind (same order as {@link #IDS}). */
    public record Payment(int[] taken, int[] change, int paid, int changeValue) {}

    /** Total worth of the counts, in copper. */
    public static long worth(int[] counts) {
        long w = 0;
        for (int i = 0; i < VALUES.length; i++) w += (long) counts[i] * VALUES[i];
        return w;
    }

    /** Smallest first with change; null when the counts are worth less than the price. A price of 0 or less takes nothing. */
    @Nullable
    public static Payment plan(int[] counts, int price) {
        int[] taken = new int[VALUES.length];
        int[] change = new int[VALUES.length];
        if (price <= 0) return new Payment(taken, change, 0, 0);
        if (worth(counts) < price) return null;
        long left = price;
        for (int i = 0; i < VALUES.length && left > 0; i++) {
            while (left > 0 && taken[i] < counts[i]) {
                taken[i]++;
                left -= VALUES[i];
            }
        }
        int back = (int) -left;
        for (int i = VALUES.length - 1; i >= 0; i--) {
            change[i] = back / VALUES[i];
            back -= change[i] * VALUES[i];
        }
        return new Payment(taken, change, (int) (price - left), (int) -left);
    }

    /** The coin items, or empty when Middle-earth does not register one of them (then coin cost is skipped, with a WARN). */
    public static Optional<Item[]> items() {
        Item[] items = new Item[IDS.length];
        for (int i = 0; i < IDS.length; i++) {
            Optional<Item> it = BuiltInRegistries.ITEM.getOptional(IDS[i]);
            if (it.isEmpty()) {
                if (!warnedMissing) {
                    warnedMissing = true;
                    LotrLog.warn("travel", "coin item {} is not registered; fast travel coin cost is skipped", IDS[i]);
                }
                return Optional.empty();
            }
            items[i] = it.get();
        }
        return Optional.of(items);
    }

    /** Coins of each kind the player carries (main inventory, hotbar and offhand). */
    public static int[] count(ServerPlayer p, Item[] items) {
        int[] counts = new int[items.length];
        Inventory inv = p.getInventory();
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            for (int i = 0; i < items.length; i++) if (!st.isEmpty() && st.is(items[i])) counts[i] += st.getCount();
        }
        return counts;
    }

    /** True when the player can pay {@code price} copper (always true when coins are off or Middle-earth has none). */
    public static boolean canPay(ServerPlayer p, int price) {
        if (price <= 0) return true;
        Optional<Item[]> items = items();
        return items.isEmpty() || worth(count(p, items.get())) >= price;
    }

    /** Takes the coins and gives change; returns the payment, or null (nothing taken) when the player cannot pay. */
    @Nullable
    public static Payment pay(ServerPlayer p, int price) {
        if (price <= 0) return plan(new int[VALUES.length], 0);
        Optional<Item[]> found = items();
        if (found.isEmpty()) return plan(new int[VALUES.length], 0);
        Item[] items = found.get();
        Payment pay = plan(count(p, items), price);
        if (pay == null) return null;
        Inventory inv = p.getInventory();
        for (int i = 0; i < items.length; i++) {
            int need = pay.taken()[i];
            for (int s = 0; s < inv.getContainerSize() && need > 0; s++) {
                ItemStack st = inv.getItem(s);
                if (st.isEmpty() || !st.is(items[i])) continue;
                int n = Math.min(need, st.getCount());
                st.shrink(n);
                need -= n;
            }
        }
        for (int i = items.length - 1; i >= 0; i--) {
            int n = pay.change()[i];
            while (n > 0) {
                int k = Math.min(n, items[i].getDefaultInstance().getMaxStackSize());
                ItemStack st = new ItemStack(items[i], k);
                inv.add(st);
                if (!st.isEmpty()) p.drop(st, false);   // inventory full: change goes on the ground at the player's feet
                n -= k;
            }
        }
        inv.setChanged();
        return pay;
    }

    public static String describe(int[] counts) {
        return counts[0] + "c/" + counts[1] + "s/" + counts[2] + "g";
    }
}
