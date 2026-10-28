package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.coin.CoinItems;
import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.item.CoreItems;
import io.github.sethwillemsvg.lotrrevival.npc.entity.HobbitEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteractEvent;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeEvent;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenu;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenus;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradePayloads;
import io.github.sethwillemsvg.lotrrevival.npc.trade.PayloadLimiter;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TradeOffer;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TraderStock;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TraderStocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** GameTests for the trade screen: opening, buy, sell, coin exchange, close rules and payload limits (ticket #69). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcTradeScreenTest {
    private NpcTradeScreenTest() {}

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "trade-tester"));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static HobbitEntity farmer(GameTestHelper helper) {
        HobbitEntity hobbit = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(2, 1, 2));
        hobbit.finalizeSpawn(
                helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(hobbit.blockPosition()), MobSpawnType.COMMAND, null);
        hobbit.setNoAi(true);
        hobbit.setRole(NpcRole.FARMER);
        // Touch the stock so it is rolled, then replace the lists by known ones.
        TraderStock stock = TraderStocks.stock(hobbit);
        stock.playerBuys().clear();
        stock.playerSells().clear();
        return hobbit;
    }

    private static TradeOffer offer(Item item, int count, int price) {
        return new TradeOffer(Optional.of(BuiltInRegistries.ITEM.getKey(item)), Optional.empty(), count, price, 0, 0, false);
    }

    private static void near(ServerPlayer player, HobbitEntity npc) {
        player.setPos(npc.getX() + 1.0, npc.getY(), npc.getZ());
    }

    private static int count(ServerPlayer player, Item item) {
        int sum = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                sum += stack.getCount();
            }
        }
        return sum;
    }

    @SuppressWarnings("removal")
    private static <E extends net.neoforged.bus.api.Event> Consumer<E> listen(Class<E> type, Consumer<E> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, type, listener);
        return listener;
    }

    // ------------------------------------------------------------------ opening

    /**
     * GameTest players have no connection that can receive a real open-screen packet, so the opener is replaced by a
     * recorder; the real {@code openMenu} call is covered by the in-world check in the PR.
     */
    @GameTest(template = "empty")
    public static void tradeOpensBuyAtZeroAndTalksBelow(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        TraderStocks.stock(npc).playerBuys().add(offer(Items.BREAD, 1, 5));
        near(player, npc);
        List<NpcTradeMenu.Tab> opened = new ArrayList<>();
        NpcTradeMenus.Opener previous = NpcTradeMenus.setOpener((p, n, tab) -> {
            if (p == player && n == npc) {
                opened.add(tab);
                return true;
            }
            return false;
        });
        List<NpcInteractEvent.Kind> kinds = new ArrayList<>();
        Consumer<NpcInteractEvent> interact = listen(NpcInteractEvent.class, e -> {
            if (e.getNpc() == npc) {
                kinds.add(e.getKind());
            }
        });
        try {
            Alignment.set(player, Factions.HOBBIT, 0.0f, AlignmentSource.command());
            npc.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.TRADE)), "at 0: " + kinds);
            helper.assertTrue(opened.equals(List.of(NpcTradeMenu.Tab.BUY)), "trade opens on Buy: " + opened);

            kinds.clear();
            opened.clear();
            Alignment.set(player, Factions.HOBBIT, -1.0f, AlignmentSource.command());
            npc.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(kinds.equals(List.of(NpcInteractEvent.Kind.TALK)), "at -1: " + kinds);
            helper.assertTrue(opened.isEmpty(), "no trade screen at -1");
        } finally {
            NeoForge.EVENT_BUS.unregister(interact);
            NpcTradeMenus.setOpener(previous);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ buy

    @GameTest(template = "empty")
    public static void buyPaysAndFiresOncePerLot(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        TraderStocks.stock(npc).playerBuys().add(offer(Items.BREAD, 1, 5));
        near(player, npc);
        NpcTradeMenu menu = new NpcTradeMenu(1, player.getInventory(), npc, NpcTradeMenu.Tab.BUY);
        List<NpcTradeEvent> events = new ArrayList<>();
        Consumer<NpcTradeEvent> listener = listen(NpcTradeEvent.class, e -> {
            if (e.getTrader() == npc) {
                events.add(e);
            }
        });
        try {
            Coins.give(player, 4);
            helper.assertTrue(!menu.buy(player, 0), "4 coins cannot buy a 5 offer");
            helper.assertTrue(Coins.count(player) == 4 && count(player, Items.BREAD) == 0, "nothing changed");
            helper.assertTrue(events.isEmpty(), "no event on a failed buy");

            Coins.give(player, 3);
            helper.assertTrue(menu.buy(player, 0), "7 coins buy a 5 offer");
            helper.assertTrue(Coins.count(player) == 2, "2 coins left: " + Coins.count(player));
            helper.assertTrue(count(player, Items.BREAD) == 1, "bread received");
            helper.assertTrue(events.size() == 1 && events.get(0).getPrice() == 5, "one event: " + events.size());
            helper.assertTrue(
                    events.get(0).getDirection() == NpcTradeEvent.Direction.BUY
                            && events.get(0).getStack().is(Items.BREAD),
                    "event describes the lot");
            helper.assertTrue(TraderStocks.stock(npc).playerBuys().get(0).recentValue() == 5, "recent value recorded");
            helper.assertTrue(!menu.buy(player, 9), "bad index ignored");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedOfferCannotBeBought(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        TradeOffer locked = offer(Items.BREAD, 1, 5);
        locked.setValueLocked(true);
        locked.setRecentValue(200);
        TraderStocks.stock(npc).playerBuys().add(locked);
        near(player, npc);
        NpcTradeMenu menu = new NpcTradeMenu(1, player.getInventory(), npc, NpcTradeMenu.Tab.BUY);
        Coins.give(player, 50);
        helper.assertTrue(!menu.buy(player, 0), "sold out offer is refused");
        helper.assertTrue(Coins.count(player) == 50 && count(player, Items.BREAD) == 0, "nothing taken");
        helper.succeed();
    }

    // ------------------------------------------------------------------ sell

    @GameTest(template = "empty")
    public static void sellPaysLotsAndKeepsUnmatchedItems(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        TraderStocks.stock(npc).playerSells().add(offer(Items.WHEAT, 1, 1));
        near(player, npc);
        NpcTradeMenu menu = new NpcTradeMenu(1, player.getInventory(), npc, NpcTradeMenu.Tab.SELL);
        List<NpcTradeEvent> events = new ArrayList<>();
        Consumer<NpcTradeEvent> listener = listen(NpcTradeEvent.class, e -> {
            if (e.getTrader() == npc) {
                events.add(e);
            }
        });
        try {
            menu.sellContainer().setItem(0, new ItemStack(Items.WHEAT, 10));
            menu.sellContainer().setItem(1, new ItemStack(Items.STICK, 3));
            helper.assertTrue(menu.sell(player) == 10, "paid 10");
            helper.assertTrue(Coins.count(player) == 10, "10 coins received: " + Coins.count(player));
            helper.assertTrue(menu.sellContainer().getItem(0).isEmpty(), "wheat consumed");
            helper.assertTrue(menu.sellContainer().getItem(1).is(Items.STICK), "stick stays in the slot");
            helper.assertTrue(events.size() == 10, "one SELL event per lot: " + events.size());
            helper.assertTrue(events.get(0).getDirection() == NpcTradeEvent.Direction.SELL, "direction");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sellCanCrossIntoTheLock(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        TraderStocks.stock(npc).playerSells().add(offer(Items.WHEAT, 1, 150));
        near(player, npc);
        NpcTradeMenu menu = new NpcTradeMenu(1, player.getInventory(), npc, NpcTradeMenu.Tab.SELL);
        menu.sellContainer().setItem(0, new ItemStack(Items.WHEAT, 3));
        helper.assertTrue(menu.sell(player) == 300, "two lots: the second crosses 200 and completes");
        helper.assertTrue(menu.sellContainer().getItem(0).getCount() == 1, "third item stays, offer is locked");
        helper.assertTrue(
                NpcTradeMenu.previewSell(
                                List.of(new ItemStack(Items.WHEAT, 3)),
                                List.of(io.github.sethwillemsvg.lotrrevival.npc.trade.OfferView.of(offer(Items.WHEAT, 1, 150))))
                        == 300,
                "preview agrees with the server");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void closingReturnsLeftovers(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        near(player, npc);
        NpcTradeMenu menu = new NpcTradeMenu(1, player.getInventory(), npc, NpcTradeMenu.Tab.SELL);
        menu.sellContainer().setItem(2, new ItemStack(Items.STICK, 3));
        menu.exchangeContainer().setItem(0, new ItemStack(CoreItems.SILVER_COIN.get(), 4));
        menu.removed(player);
        helper.assertTrue(count(player, Items.STICK) == 3, "sticks returned");
        helper.assertTrue(Coins.count(player) == 4, "coins returned");
        helper.assertTrue(menu.sellContainer().getItem(2).isEmpty(), "slots emptied");
        helper.succeed();
    }

    // ------------------------------------------------------------------ exchange

    @GameTest(template = "empty")
    public static void breakAndCombine(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        near(player, npc);
        NpcTradeMenu menu = new NpcTradeMenu(1, player.getInventory(), npc, NpcTradeMenu.Tab.EXCHANGE);

        menu.exchangeContainer().setItem(0, new ItemStack(CoinItems.SILVER_COIN_100.get()));
        helper.assertTrue(menu.exchange(player, false), "break works");
        helper.assertTrue(count(player, CoinItems.SILVER_COIN_10.get()) == 10, "10 x 10 received");
        helper.assertTrue(menu.exchangeContainer().getItem(0).isEmpty(), "100 consumed");
        helper.assertTrue(!menu.exchange(player, false), "nothing left to break");

        menu.exchangeContainer().setItem(0, new ItemStack(CoreItems.SILVER_COIN.get(), 23));
        helper.assertTrue(!menu.exchange(player, false), "a 1 coin cannot be broken");
        helper.assertTrue(menu.exchange(player, true), "combine works");
        helper.assertTrue(menu.exchangeContainer().getItem(0).getCount() == 3, "3 x 1 left in the slot");
        helper.assertTrue(count(player, CoinItems.SILVER_COIN_10.get()) == 12, "2 more 10 coins");

        menu.exchangeContainer().setItem(0, new ItemStack(CoinItems.SILVER_COIN_100.get(), 2));
        helper.assertTrue(!menu.exchange(player, true), "100 coins do not combine");
        helper.succeed();
    }

    // ------------------------------------------------------------------ close rules and limits

    @GameTest(template = "empty")
    public static void menuClosesFarAwayOrWhenTraderHasTarget(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        TraderStocks.stock(npc).playerBuys().add(offer(Items.BREAD, 1, 5));
        Alignment.set(player, Factions.HOBBIT, 0.0f, AlignmentSource.command());
        near(player, npc);
        NpcTradeMenu menu = new NpcTradeMenu(1, player.getInventory(), npc, NpcTradeMenu.Tab.BUY);
        helper.assertTrue(menu.stillValid(player), "valid up close");

        player.setPos(npc.getX() + 12.0, npc.getY(), npc.getZ());
        helper.assertTrue(menu.stillValid(player), "valid at 12 blocks");
        player.setPos(npc.getX() + 13.0, npc.getY(), npc.getZ());
        helper.assertTrue(!menu.stillValid(player), "closed at 13 blocks");

        near(player, npc);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 2));
        zombie.setNoAi(true);
        npc.setTarget(zombie);
        helper.assertTrue(!menu.stillValid(player), "closed when the trader has a target");
        npc.setTarget(null);
        helper.assertTrue(menu.stillValid(player), "valid again without a target");

        Alignment.set(player, Factions.HOBBIT, -50.0f, AlignmentSource.command());
        helper.assertTrue(!menu.stillValid(player), "closed when the player is hostile");
        Alignment.set(player, Factions.HOBBIT, 0.0f, AlignmentSource.command());

        npc.discard();
        helper.assertTrue(!menu.stillValid(player), "closed when the trader is removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void payloadsNeedTheOpenValidMenuAndRespectTheRateLimit(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HobbitEntity npc = farmer(helper);
        TraderStocks.stock(npc).playerBuys().add(offer(Items.BREAD, 1, 5));
        Alignment.set(player, Factions.HOBBIT, 0.0f, AlignmentSource.command());
        near(player, npc);
        try {
            helper.assertTrue(NpcTradePayloads.openMenu(player, 0) == null, "no trade menu open yet");
            NpcTradeMenu open = new NpcTradeMenu(7, player.getInventory(), npc, NpcTradeMenu.Tab.BUY);
            player.containerMenu = open;
            int id = 7;
            helper.assertTrue(NpcTradePayloads.openMenu(player, id) != null, "matching id and valid");
            helper.assertTrue(NpcTradePayloads.openMenu(player, id + 1) == null, "wrong container id refused");
            player.setPos(npc.getX() + 20.0, npc.getY(), npc.getZ());
            helper.assertTrue(NpcTradePayloads.openMenu(player, id) == null, "out of reach refused");
            net.minecraft.resources.ResourceLocation type = LOTRRevival.id("npc/test_limit");
            int allowed = 0;
            for (int i = 0; i < PayloadLimiter.MAX_PER_SECOND + 15; i++) {
                if (PayloadLimiter.allow(player, type)) {
                    allowed++;
                }
            }
            helper.assertTrue(allowed == PayloadLimiter.MAX_PER_SECOND, "20 per second, excess dropped: " + allowed);
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }
}
