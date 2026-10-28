package io.github.sethwillemsvg.lotrrevival.npc.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTrade;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeData;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TradeEntry;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TradeOffer;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TradePool;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TraderStock;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TraderStocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** GameTests for trader offer rolling, locking, refresh and persistence (ticket #67). Mostly pure logic. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcTradeStockTest {
    private NpcTradeStockTest() {}

    private static List<TradeEntry> pool(int size) {
        List<Item> items = List.of(
                Items.BREAD, Items.WHEAT, Items.POTATO, Items.CARROT, Items.EGG, Items.APPLE, Items.SUGAR, Items.BONE,
                Items.STRING, Items.FEATHER, Items.LEATHER, Items.CAKE, Items.COOKIE, Items.PUMPKIN_PIE, Items.COD,
                Items.SALMON, Items.BEETROOT, Items.HAY_BLOCK, Items.MELON_SLICE, Items.HONEY_BOTTLE);
        List<TradeEntry> entries = new ArrayList<>();
        for (int n = 0; n < size; n++) {
            entries.add(TradeEntry.item(items.get(n), 1, 1 + n * 2));
        }
        return entries;
    }

    @GameTest(template = "empty")
    public static void rollSizesPricesAndNoRepeats(GameTestHelper helper) {
        List<TradeEntry> pool = pool(20);
        RandomSource random = RandomSource.create(67L);
        int[] sizes = new int[20];
        for (int roll = 0; roll < 10_000; roll++) {
            List<TradeOffer> offers = TraderStocks.roll(random, pool);
            helper.assertTrue(offers.size() >= 3 && offers.size() <= 9, "size " + offers.size());
            sizes[offers.size()]++;
            Set<Object> seen = new HashSet<>();
            for (TradeOffer offer : offers) {
                helper.assertTrue(seen.add(offer.item().get()), "repeat " + offer.item().get());
                TradeEntry base = pool.stream()
                        .filter(e -> e.item().equals(offer.item()))
                        .findFirst()
                        .orElseThrow();
                helper.assertTrue(offer.price() >= 1, "price below 1: " + offer.price());
                // Rounding half up moves a price by at most 0.5 from the real-valued roll.
                helper.assertTrue(offer.price() >= base.price() * 0.75 - 0.5, "price too low " + offer + " base " + base.price());
                helper.assertTrue(offer.price() <= base.price() * 1.25 + 0.5, "price too high " + offer + " base " + base.price());
            }
        }
        for (int size = 3; size <= 9; size++) {
            helper.assertTrue(sizes[size] > 0, "size " + size + " never rolled");
        }
        helper.assertTrue(Math.abs(sizes[3] - 10_000 / 27) < 120, "size 3 count " + sizes[3]);
        helper.assertTrue(Math.abs(sizes[9] - 10_000 / 27) < 120, "size 9 count " + sizes[9]);
        helper.assertTrue(TraderStocks.roll(random, pool(2)).size() == 2, "capped by a pool of 2");
        helper.assertTrue(TraderStocks.roll(random, List.of()).isEmpty(), "empty pool rolls nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void priceRoundsHalfUpAndNeverBelowOne(GameTestHelper helper) {
        RandomSource random = RandomSource.create(5L);
        for (int n = 0; n < 2000; n++) {
            helper.assertTrue(TraderStocks.rollPrice(random, 1) == 1, "base 1 stays 1");
        }
        helper.succeed();
    }

    private static TraderStock stockWithOffer(int price) {
        TraderStock stock = new TraderStock();
        TradePool pool = new TradePool(List.of(TradeEntry.item(Items.BREAD, 5)), List.of());
        TraderStocks.rollInto(stock, pool, RandomSource.create(1L));
        // Replace the rolled offer by one with a fixed price.
        stock.playerBuys().clear();
        stock.playerBuys().add(new TradeOffer(java.util.Optional.of(BuiltInRegistries.ITEM.getKey(Items.BREAD)), java.util.Optional.empty(), 1, price, 0, 0, false));
        return stock;
    }

    @GameTest(template = "empty")
    public static void offerLocksAtTwoHundredAndDecays(GameTestHelper helper) {
        TraderStock stock = stockWithOffer(200);
        TradeOffer offer = stock.playerBuys().get(0);
        TradePool pool = new TradePool(List.of(TradeEntry.item(Items.BREAD, 5)), List.of());
        RandomSource random = RandomSource.create(2L);
        helper.assertTrue(TraderStocks.isTradable(offer), "fresh offer is tradable");
        TraderStocks.recordLot(stock, pool, random, offer);
        helper.assertTrue(offer.recentValue() == 200 && stock.totalValue() == 200, "counters " + offer);
        helper.assertTrue(!TraderStocks.isTradable(offer), "locked at 200");
        for (int t = 0; t < TraderStocks.DECAY_INTERVAL; t++) {
            TraderStocks.tick(stock);
        }
        helper.assertTrue(offer.recentValue() == 199, "one decay step gives 199, has " + offer.recentValue());
        helper.assertTrue(!TraderStocks.isTradable(offer), "still locked at 199");
        for (int t = 0; t < 199 * TraderStocks.DECAY_INTERVAL - 1; t++) {
            TraderStocks.tick(stock);
        }
        helper.assertTrue(offer.recentValue() == 1 && !TraderStocks.isTradable(offer), "one tick short is locked: " + offer);
        TraderStocks.tick(stock);
        helper.assertTrue(offer.recentValue() == 0 && TraderStocks.isTradable(offer), "tradable at 0 after 12,000 ticks");
        TraderStocks.tick(stock);
        helper.assertTrue(offer.recentValue() == 0, "never below 0");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refreshAtFiveThousandRerollsAndLocksSixThousandTicks(GameTestHelper helper) {
        TradePool pool = new TradePool(pool(20), pool(20));
        RandomSource random = RandomSource.create(3L);
        TraderStock stock = new TraderStock();
        TraderStocks.rollInto(stock, pool, random);
        List<TradeOffer> oldBuys = new ArrayList<>(stock.playerBuys());
        TradeOffer first = stock.playerBuys().get(0);
        // Fresh offers are tradable even though total value is high elsewhere.
        for (int n = 0; n < 49; n++) {
            TraderStocks.recordLot(stock, pool, random, stock.playerSells().get(0));
            helper.assertTrue(stock.totalValue() < TraderStocks.REFRESH_VALUE, "no refresh yet");
        }
        helper.assertTrue(stock.playerBuys().equals(oldBuys), "lists unchanged below 5,000");
        int price = stock.playerSells().get(0).price();
        // Push total to just under, then over, with the first buy offer.
        int total = stock.totalValue();
        helper.assertTrue(total == 49 * price, "total " + total);
        while (stock.totalValue() < TraderStocks.REFRESH_VALUE) {
            helper.assertTrue(stock.playerBuys().size() >= 3, "still rolled");
            TraderStocks.recordLot(stock, pool, random, first);
            if (stock.totalValue() == 0) {
                break;
            }
        }
        helper.assertTrue(stock.totalValue() == 0, "total reset by refresh: " + stock.totalValue());
        helper.assertTrue(stock.playerBuys().size() >= 3 && stock.playerSells().size() >= 3, "both lists re-rolled");
        for (TradeOffer offer : all(stock)) {
            helper.assertTrue(offer.recentValue() == 0, "counter reset " + offer);
            helper.assertTrue(offer.lockTicks() == TraderStocks.REFRESH_LOCK_TICKS, "locked 6000 " + offer);
            helper.assertTrue(!TraderStocks.isTradable(offer), "untradable after refresh " + offer);
        }
        for (int t = 0; t < TraderStocks.REFRESH_LOCK_TICKS - 1; t++) {
            TraderStocks.tick(stock);
        }
        for (TradeOffer offer : all(stock)) {
            helper.assertTrue(!TraderStocks.isTradable(offer), "still locked one tick early " + offer);
        }
        TraderStocks.tick(stock);
        for (TradeOffer offer : all(stock)) {
            helper.assertTrue(TraderStocks.isTradable(offer), "tradable after 6000 ticks " + offer);
        }
        helper.succeed();
    }

    private static List<TradeOffer> all(TraderStock stock) {
        List<TradeOffer> offers = new ArrayList<>(stock.playerBuys());
        offers.addAll(stock.playerSells());
        return offers;
    }

    @GameTest(template = "empty")
    public static void stockSurvivesSaveAndLoad(GameTestHelper helper) {
        NpcEntity original = NpcEntityTypes.HOBBIT.get().create(helper.getLevel());
        original.setRole(NpcRole.FARMER);
        TraderStock stock = TraderStocks.stock(original);
        helper.assertTrue(stock.isRolled(), "farmer stock rolls lazily on first access");
        helper.assertTrue(stock.playerBuys().size() >= 3 && stock.playerSells().size() >= 3, "farmer lists rolled");
        TradePool pool = NpcTradeData.pool(original.getType(), NpcRole.FARMER).orElseThrow();
        TraderStocks.recordLot(stock, pool, RandomSource.create(9L), stock.playerBuys().get(0));
        TradeOffer sell = stock.playerSells().get(0);
        sell.setLockTicks(77);
        CompoundTag tag = original.saveWithoutId(new CompoundTag());

        NpcEntity copy = NpcEntityTypes.HOBBIT.get().create(helper.getLevel());
        copy.load(tag);
        TraderStock loaded = copy.getData(NpcTrade.TRADER_STOCK);
        helper.assertTrue(loaded.isRolled(), "loaded stock is rolled");
        helper.assertTrue(loaded.playerBuys().equals(stock.playerBuys()), "buys " + loaded.playerBuys() + " vs " + stock.playerBuys());
        helper.assertTrue(loaded.playerSells().equals(stock.playerSells()), "sells differ");
        helper.assertTrue(loaded.totalValue() == stock.totalValue() && loaded.totalValue() > 0, "total " + loaded.totalValue());
        helper.assertTrue(loaded.playerSells().get(0).lockTicks() == 77, "lock survived");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hobbitFarmerHealsLikeTraderAndKillValueTwo(GameTestHelper helper) {
        NpcEntity farmer = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(3, 1, 3));
        farmer.setRole(NpcRole.FARMER);
        // Shield from outside damage: the CI runtime-test runs inside a real world with mobs and nights.
        farmer.setInvulnerable(true);
        farmer.setHealth(8.0f);
        helper.assertTrue(farmer.getRole().isTrader(), "farmer is a trader role");
        helper.assertTrue(farmer.alignmentKillValue() == 2.0f, "kill value " + farmer.alignmentKillValue());
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(farmer.getHealth() == 9.0f, "farmer heals 1 HP in 40 ticks, has " + farmer.getHealth());
            farmer.discard();
            helper.succeed();
        });
    }
}
