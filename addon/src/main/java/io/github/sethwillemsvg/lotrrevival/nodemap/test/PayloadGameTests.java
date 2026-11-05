package io.github.sethwillemsvg.lotrrevival.nodemap.test;

import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.check;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.flat;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.node;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.pass;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.road;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.run;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.test.TestPlayers;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.camp.CampService;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.CampEditPayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.NodeMapServerHandlers;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CombatTagTracker;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server side of the client packets (spec Packets): travel_cancel (N10) and camp_edit plus a camp travel_request
 * (N12), called through {@link NodeMapServerHandlers} with a test context, so nothing the client sends is trusted.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PayloadGameTests {
    private PayloadGameTests() {}

    private static NodeMapContext context(GameTestHelper h, String prefix, List<GraphNode> out) {
        Vec3 at = NodeMapTestSupport.spot(h);
        GraphNode a = node(prefix + "_a", at.x, at.z, null, false);
        GraphNode d = node(prefix + "_d", at.x, at.z + 24, null, false);
        out.add(a);
        out.add(d);
        return NodeMapTestSupport.context(new NodeGraph(List.of(a, d), List.of(road(a, d))),
                NodeMapSettings.defaults().withChargeTicks(20), NodeMapTestSupport.neutral(), new CombatTagTracker());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancelPayload(GameTestHelper h) {
        String name = "cancelPayload";
        run(h, name, () -> {
            List<GraphNode> n = new ArrayList<>();
            NodeMapContext ctx = context(h, "pcancel", n);
            TravelService svc = new TravelService(ctx);
            List<String> states = new ArrayList<>();
            svc.status = (pl, state, target, left, total, reason) -> states.add(state);
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                Vec3 start = p.position();
                NodeMapData data = NodeMapAttachment.get(p);
                data.addKnown(n.get(0).ref().fixed());
                data.addKnown(n.get(1).ref().fixed());

                check(name, !NodeMapServerHandlers.cancel(svc, p), "cancel with no charge reported a cancel");
                check(name, NodeMapServerHandlers.travelRequest(svc, p, n.get(1).ref().toString()), "travel_request refused");
                for (int i = 0; i < 5; i++) svc.tick(p);
                check(name, NodeMapServerHandlers.cancel(svc, p), "cancel during the charge did nothing");
                check(name, svc.charge(p) == null, "charge still running after cancel");
                check(name, states.contains("cancelled"), "no cancelled status sent: " + states);
                for (int i = 0; i < 30; i++) svc.tick(p);
                check(name, flat(p.position(), start.x, start.z) < 0.01 && data.trips() == 0 && data.cooldown() == 0,
                        "something was spent or the player moved: trips " + data.trips() + " cooldown " + data.cooldown());
                check(name, !NodeMapServerHandlers.travelRequest(svc, p, "camp:x") && !NodeMapServerHandlers.travelRequest(svc, p, "camp:-3"),
                        "a bad ref started a charge");
                check(name, svc.charge(p) == null, "a bad ref left a charge");
                pass(h, name, "cancel with reason player; statuses " + states + "; nothing spent");
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void campEditPayload(GameTestHelper h) {
        String name = "campEditPayload";
        run(h, name, () -> {
            List<GraphNode> n = new ArrayList<>();
            NodeMapContext ctx = context(h, "pcamp", n);
            CampService camps = new CampService(ctx);
            TravelService svc = new TravelService(ctx);
            ServerPlayer p = null, other = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                other = TestPlayers.mock(h);
                Vec3 at = p.position();
                NodeMapData data = NodeMapAttachment.get(p);
                data.addKnown(n.get(0).ref().fixed());
                data.addKnown(n.get(1).ref().fixed());

                p.moveTo(at.x + 70, at.y, at.z);   // outside the 64-block departure radius of node a
                CampService.Result made = NodeMapServerHandlers.campEdit(camps, p, new CampEditPayload("create", 0, " Fern Hollow "));
                check(name, made.ok() && made.camp().name().equals("Fern Hollow"), "create refused: " + made.code());
                int id = made.camp().id();

                check(name, NodeMapServerHandlers.campEdit(camps, p, new CampEditPayload("create", 0, "Bad§cname")).code().equals("bad_name"),
                        "a name with a formatting code was accepted");
                check(name, NodeMapServerHandlers.campEdit(camps, p, new CampEditPayload("burn", id, "x")).code().equals("bad_op"),
                        "an unknown op was not refused");
                check(name, NodeMapServerHandlers.campEdit(camps, p, new CampEditPayload("rename", id, "")).code().equals("bad_name"),
                        "an empty rename was accepted");
                check(name, NodeMapServerHandlers.campEdit(camps, p, new CampEditPayload("rename", id + 99, "Elsewhere")).code().equals("no_camp"),
                        "renaming a missing camp succeeded");

                // another player sends the same id: their own (empty) camps are checked, the owner's camp is untouched
                check(name, NodeMapServerHandlers.campEdit(camps, other, new CampEditPayload("rename", id, "Stolen")).code().equals("no_camp"),
                        "another player renamed the camp");
                check(name, NodeMapServerHandlers.campEdit(camps, other, new CampEditPayload("delete", id, "")).code().equals("no_camp"),
                        "another player deleted the camp");
                check(name, data.camp(id) != null && data.camp(id).name().equals("Fern Hollow"), "owner's camp changed by another player");

                check(name, NodeMapServerHandlers.campEdit(camps, p, new CampEditPayload("rename", id, "Bracken Hollow")).ok()
                        && data.camp(id).name().equals("Bracken Hollow"), "rename failed");

                // travel to the camp from node a, as the Camps tab's Travel button does
                p.moveTo(at.x, at.y, at.z);
                check(name, NodeMapServerHandlers.travelRequest(svc, p, "camp:" + id), "travel to the camp refused");
                for (int i = 0; i < 20; i++) svc.tick(p);
                Vec3 campAt = Vec3.atBottomCenterOf(data.camp(id).pos());
                check(name, svc.charge(p) == null && flat(p.position(), campAt.x, campAt.z) < 3, "did not arrive at the camp: " + p.position());

                check(name, NodeMapServerHandlers.campEdit(camps, p, new CampEditPayload("delete", id, "")).ok() && data.camp(id) == null,
                        "delete failed");
                check(name, !NodeMapServerHandlers.travelRequest(svc, p, "camp:" + id), "travel to a deleted camp started");
                pass(h, name, "create, rename, delete, travel; bad names, ops, ids and other players refused");
            } finally {
                NodeMapTestSupport.remove(h, p);
                TestPlayers.remove(other);
            }
        });
    }
}
