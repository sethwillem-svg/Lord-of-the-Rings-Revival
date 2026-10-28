package io.github.sethwillemsvg.lotrrevival.core.coin;

import io.github.sethwillemsvg.lotrrevival.core.item.CoreItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side silver coin API. All coin movement (trading, change, quest rewards) goes through here.
 *
 * <p>Amounts are total coin value. Coins are counted and paid only from the main inventory, hotbar and off
 * hand: never armor slots, bundles or other containers, or the ender chest.
 *
 * <p>Reserved, not implemented yet: a future "stolen" flag on coin stacks. When pickpocketing exists,
 * {@link #count(Player)} and {@link #pay(ServerPlayer, int)} will skip flagged stacks (traders refuse them
 * too); everything else here stays unchanged.
 */
public final class Coins {
    private Coins() {}

    /** One coin denomination. */
    public record Denomination(int value, Supplier<Item> item) {
        public ItemStack stack(int count) {
            return new ItemStack(item.get(), count);
        }
    }

    /** Denominations in descending value (largest first). */
    public static final List<Denomination> DENOMINATIONS = List.of(
            new Denomination(100, CoinItems.SILVER_COIN_100),
            new Denomination(10, CoinItems.SILVER_COIN_10),
            new Denomination(1, CoreItems.SILVER_COIN));

    /** Value of one coin item, 0 if the item is not a coin. */
    public static int unitValue(Item item) {
        for (Denomination d : DENOMINATIONS) {
            if (d.item().get() == item) {
                return d.value();
            }
        }
        return 0;
    }

    /** Total value of the stack (coin value times count); 0 for non-coins and empty stacks. */
    public static int value(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return unitValue(stack.getItem()) * stack.getCount();
    }

    /** Total coin value the player carries in the main inventory, hotbar and off hand. */
    public static int count(Player player) {
        long total = 0;
        for (ItemStack stack : carried(player.getInventory())) {
            total += value(stack);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Takes {@code amount} from the player, all or nothing. Coin stacks are used lowest value first (inventory
     * order within a value) until the removed value reaches the price; any excess is handed back as change.
     *
     * @return false (and nothing is removed) if the player holds less than {@code amount}; true for
     *     {@code amount <= 0}
     */
    public static boolean pay(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (count(player) < amount) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : carried(player.getInventory())) {
            if (value(stack) > 0) {
                stacks.add(stack);
            }
        }
        stacks.sort((a, b) -> Integer.compare(unitValue(a.getItem()), unitValue(b.getItem())));
        long removed = 0;
        for (ItemStack stack : stacks) {
            if (removed >= amount) {
                break;
            }
            int unit = unitValue(stack.getItem());
            long needed = amount - removed;
            int take = (int) Math.min(stack.getCount(), (needed + unit - 1) / unit);
            stack.shrink(take);
            removed += (long) take * unit;
        }
        player.getInventory().setChanged();
        int change = (int) (removed - amount);
        if (change > 0) {
            give(player, change);
        }
        return true;
    }

    /** Gives {@code amount} as the fewest coins; whatever does not fit is dropped at the player's feet. */
    public static void give(ServerPlayer player, int amount) {
        for (ItemStack stack : split(amount)) {
            insert(player.getInventory(), stack);
            if (!stack.isEmpty()) {
                dropStack((ServerLevel) player.level(), player.position(), stack);
            }
        }
    }

    /** Drops {@code amount} as the fewest coins, as item entities at {@code pos}. */
    public static void drop(ServerLevel level, Vec3 pos, int amount) {
        for (ItemStack stack : split(amount)) {
            dropStack(level, pos, stack);
        }
    }

    /** The fewest-coins split of {@code amount}: largest denominations first, stacks capped at 64. */
    public static List<ItemStack> split(int amount) {
        List<ItemStack> out = new ArrayList<>();
        int remaining = amount;
        for (Denomination d : DENOMINATIONS) {
            int coins = remaining / d.value();
            remaining -= coins * d.value();
            while (coins > 0) {
                int n = Math.min(coins, d.item().get().getDefaultMaxStackSize());
                out.add(d.stack(n));
                coins -= n;
            }
        }
        return out;
    }

    /**
     * Moves as much of {@code stack} as fits into the main inventory (top up existing stacks, then empty slots),
     * leaving the rest in {@code stack}. Used instead of {@link Inventory#add(ItemStack)}, which silently
     * voids what does not fit for creative players.
     */
    private static void insert(Inventory inventory, ItemStack stack) {
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < inventory.items.size() && !stack.isEmpty(); i++) {
                ItemStack slot = inventory.items.get(i);
                if (pass == 0) {
                    if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                        int room = slot.getMaxStackSize() - slot.getCount();
                        int moved = Math.min(room, stack.getCount());
                        if (moved > 0) {
                            slot.grow(moved);
                            stack.shrink(moved);
                        }
                    }
                } else if (slot.isEmpty()) {
                    int moved = Math.min(stack.getMaxStackSize(), stack.getCount());
                    inventory.items.set(i, stack.split(moved));
                }
            }
        }
        inventory.setChanged();
    }

    private static List<ItemStack> carried(Inventory inventory) {
        List<ItemStack> all = new ArrayList<>(inventory.items);
        all.addAll(inventory.offhand);
        return all;
    }

    private static void dropStack(ServerLevel level, Vec3 pos, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack, 0, 0, 0);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }
}
