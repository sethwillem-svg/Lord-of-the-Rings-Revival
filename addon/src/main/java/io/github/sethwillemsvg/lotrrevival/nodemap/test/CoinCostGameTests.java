package io.github.sethwillemsvg.lotrrevival.nodemap.test;

import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.check;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.node;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.pass;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.road;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.run;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CoinPurse;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CombatTagTracker;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelChecks;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The coin cost (spec Cost, {@code travel.coin_per_1000}, off by default): the pure smallest-first plan with change, and
 * a real trip that refuses with {@code cant_afford}, then takes the right coins and gives the right change.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoinCostGameTests {
    private CoinCostGameTests() {}

    private static String s(int[] a) {
        return Arrays.toString(a);
    }

    @GameTest(template = "empty")
    public static void coinPlanSmallestFirstWithChange(GameTestHelper h) {
        String name = "coinPlanSmallestFirstWithChange";
        run(h, name, () -> {
            check(name, CoinPurse.plan(new int[] {5, 0, 0}, 6) == null, "6 copper paid from 5");
            CoinPurse.Payment exact = CoinPurse.plan(new int[] {30, 2, 1}, 24);
            check(name, exact != null && Arrays.equals(exact.taken(), new int[] {24, 0, 0}) && exact.changeValue() == 0,
                    "24 from 30c/2s/1g: took " + (exact == null ? "null" : s(exact.taken())));
            CoinPurse.Payment silver = CoinPurse.plan(new int[] {3, 2, 0}, 15);
            check(name, silver != null && Arrays.equals(silver.taken(), new int[] {3, 2, 0}) && Arrays.equals(silver.change(), new int[] {8, 0, 0}),
                    "15 from 3c/2s: took " + (silver == null ? "null" : s(silver.taken()) + " change " + s(silver.change())));
            CoinPurse.Payment gold = CoinPurse.plan(new int[] {0, 0, 2}, 24);
            check(name, gold != null && Arrays.equals(gold.taken(), new int[] {0, 0, 1}) && Arrays.equals(gold.change(), new int[] {6, 7, 0}),
                    "24 from 2g: took " + (gold == null ? "null" : s(gold.taken()) + " change " + s(gold.change())));
            CoinPurse.Payment zero = CoinPurse.plan(new int[] {0, 0, 0}, 0);
            check(name, zero != null && zero.paid() == 0, "free trip should take nothing");
            pass(h, name, "short purse refused; exact, silver and gold payments with change");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coinCostOnTrip(GameTestHelper h) {
        String name = "coinCostOnTrip";
        run(h, name, () -> {
            Optional<Item[]> found = CoinPurse.items();
            check(name, found.isPresent(), "Middle-earth coin items " + Arrays.toString(CoinPurse.IDS) + " are not registered");
            Item[] coins = found.get();
            Vec3 at = NodeMapTestSupport.spot(h);
            GraphNode a = node("coin_a", at.x, at.z, null, false);
            GraphNode d = node("coin_d", at.x, at.z + 24, null, false);
            NodeGraph g = new NodeGraph(List.of(a, d), List.of(road(a, d)));
            // 1,000 copper per 1,000 blocks: the 24-block road costs 24 copper
            NodeMapContext ctx = NodeMapTestSupport.context(g, NodeMapSettings.defaults().withChargeTicks(5).withCoinPer1000(1000),
                    NodeMapTestSupport.neutral(), new CombatTagTracker());
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                NodeMapAttachment.get(p).addKnown(a.ref().fixed());
                NodeMapAttachment.get(p).addKnown(d.ref().fixed());
                p.getInventory().clearContent();

                p.getInventory().add(new ItemStack(coins[0], 20));
                TravelChecks.Outcome poor = TravelChecks.check(ctx, p, d.ref());
                check(name, !poor.ok() && poor.refusal().code().equals("cant_afford"),
                        "20 copper for a 24-copper trip: " + (poor.ok() ? "ok" : poor.refusal().code()));

                p.getInventory().add(new ItemStack(coins[2], 1));   // 20 copper + 1 gold = 120
                TravelChecks.Outcome rich = TravelChecks.check(ctx, p, d.ref());
                check(name, rich.ok() && rich.plan().coins() == 24, "with 120 copper: " + (rich.ok() ? "coins " + rich.plan().coins() : rich.refusal().code()));

                TravelService svc = new TravelService(ctx);
                check(name, svc.request(p, d.ref()), "request refused with enough coins");
                for (int i = 0; i < 5; i++) svc.tick(p);
                check(name, svc.charge(p) == null && NodeMapAttachment.get(p).trips() == 1, "trip did not happen");
                int[] left = CoinPurse.count(p, coins);
                // smallest first: 20 copper, then the gold (100) for the last 4; change 96 = 9 silver + 6 copper
                check(name, Arrays.equals(left, new int[] {6, 9, 0}), "coins after the trip " + s(left) + ", expected [6, 9, 0]");

                // creative pays nothing
                p.setGameMode(GameType.CREATIVE);
                NodeMapAttachment.get(p).setCooldown(0);
                p.moveTo(d.x(), at.y, d.z());
                check(name, svc.request(p, a.ref()), "creative request refused");
                for (int i = 0; i < 5; i++) svc.tick(p);
                int[] after = CoinPurse.count(p, coins);
                check(name, Arrays.equals(after, left), "creative trip took coins: " + s(after));
                pass(h, name, "cant_afford at 20c; paid 24 from 20c+1g with change " + s(left) + "; creative free");
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }
}
