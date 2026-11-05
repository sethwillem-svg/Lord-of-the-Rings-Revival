package io.github.sethwillemsvg.lotrrevival.nodemap.test;

import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.check;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.flat;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.node;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.pass;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.road;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.run;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.camp.CampService;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeAccess;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CombatTagTracker;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelService;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Camps: limits, names, spacing, link range, links to the two nearest known nodes, travel to a camp, rename and delete. */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CampGameTests {
    private CampGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void campRules(GameTestHelper h) {
        String name = "campRules";
        run(h, name, () -> {
            Vec3 at = NodeMapTestSupport.spot(h);
            GraphNode a = node("camp_a", at.x, at.z, null, false);
            GraphNode b = node("camp_b", at.x + 80, at.z, null, false);
            GraphNode c = node("camp_c", at.x + 400, at.z, null, false);
            GraphNode far = node("camp_far", at.x + 5000, at.z, null, false);
            NodeGraph g = new NodeGraph(List.of(a, b, c, far), List.of(road(a, b), road(b, c)));
            NodeMapContext ctx = NodeMapTestSupport.context(g, NodeMapSettings.defaults().withChargeTicks(10), NodeMapTestSupport.neutral(),
                    new CombatTagTracker());
            CampService camps = new CampService(ctx);
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                NodeMapData d = NodeMapAttachment.get(p);

                // nothing known yet: no link within 3,000 blocks
                p.moveTo(at.x + 30, at.y, at.z + 20);   // a at 36 blocks, b at 54
                check(name, camps.create(p, "Lonely").code().equals("no_link"), "camp with no known node in range was made");
                for (GraphNode n : List.of(a, b, c, far)) d.addKnown(n.ref().fixed());

                check(name, camps.create(p, "   ").code().equals("bad_name"), "blank name accepted");
                check(name, camps.create(p, "x".repeat(33)).code().equals("bad_name"), "33-character name accepted");

                CampService.Result first = camps.create(p, "  Oak Hollow  ");
                check(name, first.ok() && first.camp().name().equals("Oak Hollow"), "valid camp refused: " + first.code());
                NodeAccess acc = new NodeAccess(ctx, p);
                List<NodeRef> links = CampService.linksOf(acc, first.camp().id()).stream().map(e -> e.other(NodeRef.camp(first.camp().id()))).toList();
                check(name, links.equals(List.of(a.ref(), b.ref())), "camp links " + links + ", expected the two nearest known [a, b]");

                p.moveTo(at.x + 60, at.y, at.z + 20);   // 30 blocks from the first camp
                check(name, camps.create(p, "Too near").code().equals("too_close"), "camp 30 blocks from another was made");

                for (int i = 1; i <= 4; i++) {
                    p.moveTo(at.x + 30 + 60 * i, at.y, at.z + 20);
                    check(name, camps.create(p, "Camp " + i).ok(), "camp " + (i + 1) + " of 5 refused");
                }
                p.moveTo(at.x + 400, at.y, at.z + 300);
                check(name, camps.create(p, "Sixth").code().equals("limit"), "6th camp made at limit 5");

                // travel from node a to the first camp
                p.moveTo(at.x, at.y, at.z);
                TravelService svc = new TravelService(ctx);
                NodeRef campRef = NodeRef.camp(first.camp().id());
                check(name, svc.request(p, campRef), "travel to the camp refused");
                for (int i = 0; i < 10; i++) svc.tick(p);
                Vec3 campAt = Vec3.atBottomCenterOf(first.camp().pos());
                check(name, svc.charge(p) == null && flat(p.position(), campAt.x, campAt.z) < 3, "did not arrive at the camp: " + p.position());

                check(name, camps.rename(p, first.camp().id(), "Old Oak").ok() && d.camp(first.camp().id()).name().equals("Old Oak"), "rename failed");
                check(name, camps.delete(p, first.camp().id()).ok() && d.camp(first.camp().id()) == null, "delete failed");
                check(name, !camps.delete(p, 999).ok(), "deleting a missing camp succeeded");
                pass(h, name, "rules hold; " + d.camps().size() + " camps left; links " + links);
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    static String edges(List<GraphEdge> l) {
        return l.stream().map(GraphEdge::id).toList().toString();
    }
}
