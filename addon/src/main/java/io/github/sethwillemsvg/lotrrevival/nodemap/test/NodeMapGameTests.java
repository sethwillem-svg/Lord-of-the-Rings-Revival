package io.github.sethwillemsvg.lotrrevival.nodemap.test;

import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.check;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.node;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.pass;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.ref;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.road;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.run;

import com.mojang.serialization.DataResult;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapConfig;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.NodeDef;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.NodeRegistries;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteDef;
import io.github.sethwillemsvg.lotrrevival.nodemap.discovery.NodeDiscovery;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeStanding;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelChecks;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;

/** Data, discovery, home reveal, faction bars and persistence (spec Acceptance tests). */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NodeMapGameTests {
    private NodeMapGameTests() {}

    @GameTest(template = "empty")
    public static void starterDataLoads(GameTestHelper h) {
        String name = "starterDataLoads";
        run(h, name, () -> {
            var access = h.getLevel().registryAccess();
            Registry<NodeDef> nodes = access.registryOrThrow(NodeRegistries.NODE);
            Registry<RouteDef> routes = access.registryOrThrow(NodeRegistries.ROUTE);
            Set<ResourceLocation> ids = new HashSet<>(nodes.keySet());
            long ours = ids.stream().filter(i -> i.getNamespace().equals(LotrRevival.MOD_ID)).count();
            check(name, ours == 37, "expected 37 starter nodes, found " + ours);
            long ourRoutes = routes.keySet().stream().filter(i -> i.getNamespace().equals(LotrRevival.MOD_ID)).count();
            // the spec text says 41 routes but its list has 40
            check(name, ourRoutes >= 40, "expected at least 40 starter routes, found " + ourRoutes);
            for (var e : routes.entrySet()) {
                RouteDef r = e.getValue();
                check(name, ids.contains(r.from()) && ids.contains(r.to()), "route " + e.getKey().location() + " has an unknown end");
            }
            var factions = access.registryOrThrow(DynamicRegistriesME.FACTION);
            for (var e : nodes.entrySet()) {
                e.getValue().faction().ifPresent(f -> check(name, factions.containsKey(f),
                        "node " + e.getKey().location() + " names unknown faction " + f));
            }
            NodeGraph g = NodeRegistries.buildGraph(access, NodeMapConfig.factors(), 96, "test");
            check(name, g.nodes().size() == ids.size(), "graph kept " + g.nodes().size() + " of " + ids.size() + " nodes (see WARN lines)");
            check(name, g.edges().size() == routes.size(), "graph kept " + g.edges().size() + " of " + routes.size() + " routes");
            // TODO(spec Acceptance, optional): biome check of each starter node through MiddleEarthMapRuntime when map data is present
            pass(h, name, ours + " nodes, " + ourRoutes + " routes, " + g.components() + " components");
        });
    }

    @GameTest(template = "empty")
    public static void discoveryByDistance(GameTestHelper h) {
        String name = "discoveryByDistance";
        run(h, name, () -> {
            Vec3 at = NodeMapTestSupport.spot(h);
            GraphNode near = node("near90", at.x + 90, at.z, null, false);
            GraphNode far = node("far100", at.x - 100, at.z, null, false);
            GraphNode open = node("open_neighbour", at.x + 500, at.z, null, false);
            GraphNode hid = node("hidden_neighbour", at.x + 600, at.z + 50, null, true);
            NodeGraph g = new NodeGraph(List.of(near, far, open, hid), List.of(road(near, open), road(near, hid)));
            NodeDiscovery disc = new NodeDiscovery(NodeMapTestSupport.context(g, NodeMapTestSupport.neutral()));
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                List<GraphNode> found = disc.check(p);
                NodeMapData d = NodeMapAttachment.get(p);
                check(name, d.isKnown(near.ref().fixed()), "node at 90 blocks (radius 96) not known; found " + found);
                check(name, !d.isKnown(far.ref().fixed()), "node at 100 blocks became known");
                check(name, d.isRumoured(open.ref().fixed()), "non-hidden neighbour not rumoured");
                check(name, !d.isRumoured(hid.ref().fixed()) && !d.isKnown(hid.ref().fixed()), "hidden neighbour was rumoured");
                check(name, disc.check(p).isEmpty(), "second check discovered again");
                pass(h, name, "known=" + d.known() + " rumoured=" + d.rumoured());
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty")
    public static void homeRevealOnce(GameTestHelper h) {
        String name = "homeRevealOnce";
        run(h, name, () -> {
            Vec3 at = NodeMapTestSupport.spot(h);
            BlockPos spawn = BlockPos.containing(at.x + 5000, at.y, at.z);
            GraphNode in = node("home_in", spawn.getX() + 500, spawn.getZ(), null, false);
            GraphNode out = node("home_out", spawn.getX() + 1500, spawn.getZ(), null, false);
            GraphNode hidden = node("home_hidden", spawn.getX() + 300, spawn.getZ(), null, true);
            NodeGraph g = new NodeGraph(List.of(in, out, hidden), List.of(road(in, out)));
            NodeDiscovery disc = new NodeDiscovery(NodeMapTestSupport.context(g, NodeMapTestSupport.neutral()));
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                List<GraphNode> first = disc.homeReveal(p, spawn);
                NodeMapData d = NodeMapAttachment.get(p);
                check(name, d.isKnown(in.ref().fixed()), "node 500 blocks from spawn not known");
                check(name, !d.isKnown(out.ref().fixed()) && d.isRumoured(out.ref().fixed()), "node 1500 blocks away should be rumoured only");
                check(name, !d.isKnown(hidden.ref().fixed()), "hidden node revealed");
                check(name, d.homeRevealed(), "home_revealed not set");
                d.forget(in.ref().fixed());
                check(name, disc.homeReveal(p, spawn).isEmpty() && !d.isKnown(in.ref().fixed()), "home reveal ran twice");
                pass(h, name, "first reveal " + first.size() + " node(s); second none");
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty")
    public static void factionBarsNodes(GameTestHelper h) {
        String name = "factionBarsNodes";
        run(h, name, () -> {
            Vec3 at = NodeMapTestSupport.spot(h);
            GraphNode a = node("fa_start", at.x, at.z, null, false);
            GraphNode s = node("shire_node", at.x + 50, at.z, NodeMapTestSupport.SHIRE, false);
            GraphNode e = node("fa_end", at.x + 100, at.z, null, false);   // beyond the 64-block departure radius
            NodeGraph g = new NodeGraph(List.of(a, s, e), List.of(road(a, s), road(s, e)));
            NodeMapContext hostile = NodeMapTestSupport.context(g, NodeStanding.fixed(Map.of(NodeMapTestSupport.SHIRE, -1), 0));
            NodeMapContext neutral = NodeMapTestSupport.context(g, NodeStanding.fixed(Map.of(NodeMapTestSupport.SHIRE, 0), 0));
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                NodeMapData d = NodeMapAttachment.get(p);
                for (GraphNode n : List.of(a, s, e)) d.addKnown(n.ref().fixed());
                TravelChecks.Outcome toS = TravelChecks.check(hostile, p, s.ref());
                check(name, !toS.ok() && toS.refusal().code().equals("destination_barred"), "at -1 the shire node should be barred, got " + describe(toS));
                TravelChecks.Outcome through = TravelChecks.check(hostile, p, e.ref());
                check(name, !through.ok() && through.refusal().code().equals("barred_on_route")
                        && through.refusal().message().getString().contains("shire_node"), "trip through the shire node: " + describe(through));
                TravelChecks.Outcome ok = TravelChecks.check(neutral, p, e.ref());
                check(name, ok.ok() && ok.plan().path().chain().size() == 3, "at 0 the trip should pass: " + describe(ok));
                pass(h, name, "barred at -1 (" + through.refusal().message().getString() + "), usable at 0 (" + ok.plan().path().describe() + ")");
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty")
    public static void attachmentRoundTrip(GameTestHelper h) {
        String name = "attachmentRoundTrip";
        run(h, name, () -> {
            NodeMapData d = new NodeMapData();
            d.addKnown(LotrRevival.id("bree"));
            d.addKnown(LotrRevival.id("weathertop"));
            d.addRumoured(LotrRevival.id("last_bridge"));
            d.addCamp("Under the oak", new BlockPos(100, 64, -20), 1234L);
            d.addCamp("Hill fort", new BlockPos(400, 70, 30), 2000L);
            d.removeCamp(1);
            d.setCooldown(777);
            d.setLastNode(NodeRef.camp(2));
            d.addTrip();
            d.setHomeRevealed(true);
            DataResult<Tag> enc = NodeMapData.CODEC.encodeStart(NbtOps.INSTANCE, d);
            Tag tag = enc.getOrThrow();
            NodeMapData back = NodeMapData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            check(name, back.sameAs(d), "codec round trip changed the data: " + d + " -> " + back);
            check(name, back.nextCampId() == 3, "next_camp_id not kept: " + back.nextCampId());
            check(name, d.copy().sameAs(d), "copy differs");
            pass(h, name, back.toString());
        });
    }

    static String describe(TravelChecks.Outcome o) {
        return o.ok() ? "ok " + o.plan().path().describe() : "refused(" + o.refusal().code() + ": " + o.refusal().message().getString() + ")";
    }
}
