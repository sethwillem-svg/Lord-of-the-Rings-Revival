package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Rolling, locking and refresh rules for trader stock (see {@code specs/npc/hiring-trading.md}). The pure methods take
 * the stock and a random source so they can be tested without an entity.
 */
public final class TraderStocks {
    /** An offer locks when its recent value reaches this. */
    public static final int LOCK_VALUE = 200;
    /** Both lists re-roll when the total value traded since the last refresh reaches this. */
    public static final int REFRESH_VALUE = 5000;
    /** Every offer is locked this long after a refresh. */
    public static final int REFRESH_LOCK_TICKS = 6000;
    /** Recent values drop by 1 this often while the trader is loaded. */
    public static final int DECAY_INTERVAL = 60;

    private TraderStocks() {}

    // ------------------------------------------------------------------ rolling

    /** Rolls a list: size 3 + r + r + r (r uniform 0..2) capped by the pool, no repeats, rolled prices. */
    public static List<TradeOffer> roll(RandomSource random, List<TradeEntry> pool) {
        int size = Math.min(pool.size(), 3 + random.nextInt(3) + random.nextInt(3) + random.nextInt(3));
        List<TradeEntry> remaining = new ArrayList<>(pool);
        List<TradeOffer> offers = new ArrayList<>(size);
        for (int n = 0; n < size; n++) {
            TradeEntry entry = remaining.remove(random.nextInt(remaining.size()));
            offers.add(TradeOffer.of(entry, rollPrice(random, entry.price())));
        }
        return offers;
    }

    /** max(1, round-half-up(base x uniform(0.75, 1.25))). */
    public static int rollPrice(RandomSource random, int base) {
        double factor = 0.75 + random.nextDouble() * 0.5;
        return Math.max(1, (int) Math.floor(base * factor + 0.5));
    }

    /** Rolls both lists of a stock from a pool and marks it rolled; counters are reset. */
    public static void rollInto(TraderStock stock, TradePool pool, RandomSource random) {
        stock.playerBuys.clear();
        stock.playerBuys.addAll(roll(random, pool.playerBuys()));
        stock.playerSells.clear();
        stock.playerSells.addAll(roll(random, pool.playerSells()));
        stock.totalValue = 0;
        stock.decayTicks = 0;
        stock.rolled = true;
    }

    // ------------------------------------------------------------------ bookkeeping

    /**
     * Whether the offer can be traded now: not value-locked (locks at 200 recent value and stays locked until the value
     * has decayed to 0) and its timed lock is clear.
     */
    public static boolean isTradable(TradeOffer offer) {
        return !offer.isValueLocked() && offer.lockTicks() <= 0;
    }

    /**
     * Records one traded lot: the price is added to the offer's recent value and to the stock total. Reaching
     * {@link #REFRESH_VALUE} re-rolls both lists and locks every new offer for {@link #REFRESH_LOCK_TICKS}.
     */
    public static void recordLot(TraderStock stock, TradePool pool, RandomSource random, TradeOffer offer) {
        offer.setRecentValue(offer.recentValue() + offer.price());
        if (offer.recentValue() >= LOCK_VALUE) {
            offer.setValueLocked(true);
        }
        stock.totalValue += offer.price();
        if (stock.totalValue >= REFRESH_VALUE) {
            rollInto(stock, pool, random);
            stock.playerBuys.forEach(o -> o.setLockTicks(REFRESH_LOCK_TICKS));
            stock.playerSells.forEach(o -> o.setLockTicks(REFRESH_LOCK_TICKS));
        }
    }

    /** One tick while the trader is loaded: timed locks count down, recent values decay by 1 every 60 ticks. */
    public static void tick(TraderStock stock) {
        boolean decay = ++stock.decayTicks >= DECAY_INTERVAL;
        if (decay) {
            stock.decayTicks = 0;
        }
        for (List<TradeOffer> list : List.of(stock.playerBuys, stock.playerSells)) {
            for (TradeOffer offer : list) {
                if (offer.lockTicks() > 0) {
                    offer.setLockTicks(offer.lockTicks() - 1);
                }
                if (decay && offer.recentValue() > 0) {
                    offer.setRecentValue(offer.recentValue() - 1);
                }
                if (offer.recentValue() == 0) {
                    offer.setValueLocked(false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ entity access

    /** The trader's stock, rolled lazily on first access when its role has a pool. */
    public static TraderStock stock(NpcEntity npc) {
        TraderStock stock = npc.getData(NpcTrade.TRADER_STOCK);
        if (!stock.isRolled() && npc.getRole().isTrader()) {
            NpcTradeData.pool(npc.getType(), npc.getRole()).ifPresent(pool -> rollInto(stock, pool, npc.getRandom()));
        }
        return stock;
    }

    /** Records a lot traded with this NPC (the trade screen calls this after taking payment). */
    public static void recordLot(NpcEntity npc, TradeOffer offer) {
        TradePool pool = NpcTradeData.pool(npc.getType(), npc.getRole()).orElse(TradePool.EMPTY);
        recordLot(stock(npc), pool, npc.getRandom(), offer);
    }
}
