package io.github.sethwillemsvg.lotrrevival.nodemap.test;

import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.check;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.pass;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.run;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.nodemap.MapCoords;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelCost;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pure-logic checks (no world state used), run as game tests so CI runs them with the rest: map coordinates, the route
 * search fixture from the spec, and the cost formula.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NodeMapUnitTests {
    private NodeMapUnitTests() {}

    @GameTest(template = "empty")
    public static void mapCoordsConvertAndBorder(GameTestHelper h) {
        String name = "mapCoordsConvertAndBorder";
        run(h, name, () -> {
            double[] b = MapCoords.mapToBlock(1500, 1500);
            check(name, Math.abs(b[0] - 48000) < 0.5 && Math.abs(b[1] - 48000) < 0.5, "map [1500,1500] -> " + b[0] + "," + b[1] + ", expected 48000,48000");
            double[] pure = MapCoords.mapToBlock(1500, 1500, 32, 32);
            check(name, pure[0] == 48000 && pure[1] == 48000, "pure conversion gave " + pure[0] + "," + pure[1]);
            check(name, !MapCoords.inBorder(-10, 5), "block -10,5 should be outside the border");
            check(name, MapCoords.inBorder(48000, 48000), "block 48000,48000 should be inside the border");
            pass(h, name, "1500,1500 px -> " + b[0] + "," + b[1] + "; -10,5 outside");
        });
    }

    /** Spec fixture: A-B road 1000, B-C wild 1000 (1600), A-D road 1500, D-C road 1500, E alone. */
    static NodeGraph fixture() {
        GraphNode a = GraphNode.simple(NodeMapTestSupport.id("a"), 0, 0, null, false);
        GraphNode b = GraphNode.simple(NodeMapTestSupport.id("b"), 1000, 0, null, false);
        GraphNode c = GraphNode.simple(NodeMapTestSupport.id("c"), 1000, 1000, null, false);
        GraphNode d = GraphNode.simple(NodeMapTestSupport.id("d"), 0, 1500, null, false);
        GraphNode e = GraphNode.simple(NodeMapTestSupport.id("e"), 5000, 5000, null, false);
        return new NodeGraph(List.of(a, b, c, d, e), List.of(
                GraphEdge.fixedLength(a, b, RouteKind.ROAD, 1000, 1.0),
                GraphEdge.fixedLength(b, c, RouteKind.WILD, 1000, 1.6),
                GraphEdge.fixedLength(a, d, RouteKind.ROAD, 1500, 1.0),
                GraphEdge.fixedLength(d, c, RouteKind.ROAD, 1500, 1.0)));
    }

    @GameTest(template = "empty")
    public static void routeSearchFixture(GameTestHelper h) {
        String name = "routeSearchFixture";
        run(h, name, () -> {
            NodeGraph g = fixture();
            NodeRef a = NodeMapTestSupport.ref("a"), b = NodeMapTestSupport.ref("b"), c = NodeMapTestSupport.ref("c"),
                    d = NodeMapTestSupport.ref("d"), e = NodeMapTestSupport.ref("e");
            PathResult r1 = g.path(a, c, n -> true, n -> false);
            check(name, r1.found() && r1.chain().equals(List.of(a, b, c)) && Math.abs(r1.length() - 2600) < 1e-6, "A->C: " + r1.describe());
            Set<NodeRef> barB = Set.of(b);
            PathResult r2 = g.path(a, c, n -> true, barB::contains);
            check(name, r2.found() && r2.chain().equals(List.of(a, d, c)) && Math.abs(r2.length() - 3000) < 1e-6, "A->C, B barred: " + r2.describe());
            Set<NodeRef> barBD = Set.of(b, d);
            PathResult r3 = g.path(a, c, n -> true, barBD::contains);
            check(name, r3.status() == PathResult.Status.BLOCKED && barBD.contains(r3.blockedBy()), "A->C, B and D barred: " + r3.describe());
            PathResult r4 = g.path(a, e, n -> true, n -> false);
            check(name, r4.status() == PathResult.Status.NO_PATH, "A->E: " + r4.describe());
            PathResult r5 = g.path(a, c, n -> !n.equals(b) && !n.equals(d), n -> false);
            check(name, r5.status() == PathResult.Status.NO_PATH, "A->C with B and D unknown: " + r5.describe());
            check(name, g.components() == 2, "components " + g.components() + ", expected 2");
            pass(h, name, r1.describe() + " | " + r2.describe() + " | " + r3.describe() + " | " + r4.describe());
        });
    }

    @GameTest(template = "empty")
    public static void campLinksPickNearestKnown(GameTestHelper h) {
        String name = "campLinksPickNearestKnown";
        run(h, name, () -> {
            NodeGraph g = fixture();
            GraphNode camp = GraphNode.camp(1, "camp", 900, 200);   // c at 806, a at 922, b (141) not linkable
            List<GraphEdge> links = g.linkCamps(List.of(camp), n -> !n.ref().equals(NodeMapTestSupport.ref("b")), 3000, 2, 1.6);
            List<NodeRef> ends = links.stream().map(l -> l.other(camp.ref())).toList();
            check(name, ends.equals(List.of(NodeMapTestSupport.ref("c"), NodeMapTestSupport.ref("a"))), "links " + ends + ", expected [c, a] (b not linkable)");
            check(name, Math.abs(links.get(0).length() - camp.dist(1000, 1000) * 1.6) < 1e-6, "wild factor not applied");
            PathResult r = g.path(camp.ref(), NodeMapTestSupport.ref("d"), n -> true, n -> false, List.of(camp), links);
            check(name, r.found() && r.chain().get(0).equals(camp.ref()), "camp->D " + r.describe());
            pass(h, name, "links " + ends + "; " + r.describe());
        });
    }

    @GameTest(template = "empty")
    public static void costFormula(GameTestHelper h) {
        String name = "costFormula";
        run(h, name, () -> {
            TravelCost c = TravelCost.DEFAULTS;
            int[] ls = {0, 10_000, 50_000, 100_000};
            int[] cds = {600, 2600, 10600, 12000};
            for (int i = 0; i < ls.length; i++) check(name, c.cooldown(ls[i]) == cds[i], "cooldown(" + ls[i] + ")=" + c.cooldown(ls[i]) + ", expected " + cds[i]);
            check(name, Math.abs(c.exhaustion(10_000) - 20f) < 1e-4, "exhaustion(10000)=" + c.exhaustion(10_000));
            check(name, Math.abs(c.exhaustion(50_000) - 40f) < 1e-4, "exhaustion(50000)=" + c.exhaustion(50_000));
            check(name, c.exhaustion(0) == 0f && c.coins(10_000) == 0, "L=0 exhaustion or coins not 0");
            check(name, TravelCost.clock(2600).equals("2:10") && TravelCost.clock(12000).equals("10:00"), "clock " + TravelCost.clock(2600));
            pass(h, name, "cooldowns 600/2600/10600/12000, exhaustion 20/40");
        });
    }

    @GameTest(template = "empty")
    public static void nodeRefRoundTrip(GameTestHelper h) {
        String name = "nodeRefRoundTrip";
        run(h, name, () -> {
            NodeRef f = NodeRef.parse("fixed:lotr_revival:bree"), c = NodeRef.parse("camp:7"), bare = NodeRef.parse("lotr_revival:bree");
            check(name, f != null && !f.isCamp() && f.toString().equals("fixed:lotr_revival:bree"), "fixed ref " + f);
            check(name, c != null && c.isCamp() && c.camp() == 7 && c.toString().equals("camp:7"), "camp ref " + c);
            check(name, f.equals(bare), "bare id should parse as fixed");
            check(name, NodeRef.parse("camp:x") == null, "camp:x should not parse");
            pass(h, name, f + " " + c);
        });
    }
}
