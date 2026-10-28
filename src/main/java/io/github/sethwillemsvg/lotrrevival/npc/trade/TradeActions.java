package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/** Server-side buy, sell and coin exchange rules of the trade screen (see {@code specs/npc/hiring-trading.md}). */
public final class TradeActions {
    private TradeActions() {}

    private static void say(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("npc.lotrrevival.trade.msg." + key), true);
    }

    private static void tradeSound(NpcEntity npc) {
        npc.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------ buy

    /** Buys one lot of the offer at {@code index}. Returns whether anything was traded. */
    public static boolean buy(ServerPlayer player, NpcEntity npc, int index) {
        TraderStock stock = TraderStocks.stock(npc);
        if (index < 0 || index >= stock.playerBuys().size()) {
            return false;
        }
        TradeOffer offer = stock.playerBuys().get(index);
        ItemStack lot = offer.stack();
        if (lot.isEmpty()) {
            return false;
        }
        if (!TraderStocks.isTradable(offer)) {
            say(player, "sold_out");
            return false;
        }
        int price = offer.price();
        if (!Coins.pay(player, price)) {
            say(player, "cannot_afford");
            return false;
        }
        ItemReturn.give(player, lot.copy());
        TraderStocks.recordLot(npc, offer);
        tradeSound(npc);
        NeoForge.EVENT_BUS.post(new NpcTradeEvent(player, npc, NpcTradeEvent.Direction.BUY, lot.copy(), price));
        return true;
    }

    // ------------------------------------------------------------------ sell

    /**
     * Sells every full lot the slots hold: for each sell offer in order, while it is tradable and the slots hold at
     * least {@code count} matching items, consumes them and pays the price (a lot may push the offer into its lock).
     * Returns the coin value paid.
     */
    public static int sell(ServerPlayer player, NpcEntity npc, Container slots) {
        TraderStock stock = TraderStocks.stock(npc);
        int total = 0;
        boolean refreshed = false;
        for (TradeOffer offer : new ArrayList<>(stock.playerSells())) {
            while (!refreshed && TraderStocks.isTradable(offer) && offer.count() > 0 && held(slots, offer) >= offer.count()) {
                ItemStack sold = take(slots, offer, offer.count());
                int price = offer.price();
                Coins.give(player, price);
                total += price;
                TraderStocks.recordLot(npc, offer);
                NeoForge.EVENT_BUS.post(new NpcTradeEvent(player, npc, NpcTradeEvent.Direction.SELL, sold, price));
                // A refresh re-rolls the lists: the old offers are gone.
                refreshed = stock.playerSells().stream().noneMatch(current -> current == offer);
            }
            if (refreshed) {
                break;
            }
        }
        if (total > 0) {
            tradeSound(npc);
        } else {
            say(player, "nothing_sold");
        }
        return total;
    }

    private static int held(Container slots, TradeOffer offer) {
        int sum = 0;
        for (int i = 0; i < slots.getContainerSize(); i++) {
            ItemStack stack = slots.getItem(i);
            if (offer.matches(stack)) {
                sum += stack.getCount();
            }
        }
        return sum;
    }

    private static ItemStack take(Container slots, TradeOffer offer, int count) {
        ItemStack taken = ItemStack.EMPTY;
        int remaining = count;
        for (int i = 0; i < slots.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = slots.getItem(i);
            if (offer.matches(stack)) {
                int n = Math.min(remaining, stack.getCount());
                if (taken.isEmpty()) {
                    taken = stack.copyWithCount(n);
                } else {
                    taken.grow(n);
                }
                stack.shrink(n);
                remaining -= n;
                if (stack.isEmpty()) {
                    slots.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        slots.setChanged();
        return taken;
    }

    // ------------------------------------------------------------------ exchange

    /**
     * Break takes one 10 or 100 coin from the slot and gives 10 coins of the next lower value. Combine takes every
     * full group of 10 coins of value 1 or 10 and gives one coin of the next higher value per group. Results go to the
     * inventory (overflow dropped). Returns whether anything changed.
     */
    public static boolean exchange(ServerPlayer player, Container slot, boolean combine) {
        ItemStack stack = slot.getItem(0);
        int unit = Coins.unitValue(stack.getItem());
        if (stack.isEmpty() || unit == 0) {
            return false;
        }
        if (!combine) {
            if (unit == 1) {
                return false;
            }
            Coins.Denomination lower = denomination(unit / 10);
            stack.shrink(1);
            ItemReturn.give(player, lower.stack(10));
        } else {
            if (unit == 100) {
                return false;
            }
            int groups = stack.getCount() / 10;
            if (groups == 0) {
                return false;
            }
            Coins.Denomination higher = denomination(unit * 10);
            stack.shrink(groups * 10);
            ItemReturn.give(player, higher.stack(groups));
        }
        if (stack.isEmpty()) {
            slot.setItem(0, ItemStack.EMPTY);
        }
        slot.setChanged();
        return true;
    }

    private static Coins.Denomination denomination(int value) {
        for (Coins.Denomination d : Coins.DENOMINATIONS) {
            if (d.value() == value) {
                return d;
            }
        }
        throw new IllegalArgumentException("no coin worth " + value);
    }
}
