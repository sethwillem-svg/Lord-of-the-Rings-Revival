package io.github.sethwillemsvg.lotrrevival.nodemap.test;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.api.NodeTravelEvent;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeStanding;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.ArrivalFinder;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CombatTagTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Shared pieces for the node map game tests: a test context (fixture graph, fixed reputation, top-block arrival, any
 * dimension, no packets), fixture nodes placed relative to the test, mock players, and the START / PASS / FAIL lines.
 */
final class NodeMapTestSupport {
    static final String NS = "lotr_test";
    static final ResourceLocation SHIRE = ResourceLocation.fromNamespaceAndPath("middle-earth", "shire");

    /** Arrive events seen, per player; players in {@link #VETO} get their Depart cancelled. */
    static final Map<UUID, List<NodeTravelEvent.Arrive>> ARRIVALS = new ConcurrentHashMap<>();
    static final Set<UUID> VETO = ConcurrentHashMap.newKeySet();

    static {
        NeoForge.EVENT_BUS.addListener((NodeTravelEvent.Arrive e) -> ARRIVALS.computeIfAbsent(e.getPlayer().getUUID(), k -> new ArrayList<>()).add(e));
        NeoForge.EVENT_BUS.addListener((NodeTravelEvent.Depart e) -> {
            if (VETO.contains(e.getPlayer().getUUID())) e.veto(Component.literal("The test says no."));
        });
    }

    private NodeMapTestSupport() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NS, path);
    }

    static NodeRef ref(String path) {
        return NodeRef.fixed(id(path));
    }

    static GraphNode node(String path, double x, double z, @Nullable ResourceLocation faction, boolean hidden) {
        return GraphNode.simple(id(path), x, z, faction, hidden);
    }

    static GraphEdge road(GraphNode a, GraphNode b) {
        return GraphEdge.of(a.ref().shortName() + "-" + b.ref().shortName(), a, b, RouteKind.ROAD, List.of(), 1.0);
    }

    static NodeMapContext context(NodeGraph g, NodeMapSettings s, NodeStanding standing, CombatTagTracker combat) {
        return new NodeMapContext("test", () -> g, () -> s, standing, ArrivalFinder.TOP_BLOCK, combat, false, p -> {});
    }

    static NodeMapContext context(NodeGraph g, NodeStanding standing) {
        return context(g, NodeMapSettings.defaults(), standing, new CombatTagTracker());
    }

    static NodeStanding neutral() {
        return NodeStanding.fixed(Map.of(), 0);
    }

    /** Where mock players stand: on the template's stone floor. */
    static Vec3 spot(GameTestHelper h) {
        return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 1)));
    }

    static ServerPlayer player(GameTestHelper h, GameType mode) {
        ServerPlayer p = io.github.sethwillemsvg.lotrrevival.core.test.TestPlayers.mock(h);
        p.setGameMode(mode);
        Vec3 at = spot(h);
        p.moveTo(at.x, at.y, at.z, 0, 0);
        return p;
    }

    static void remove(GameTestHelper h, @Nullable ServerPlayer p) {
        if (p == null) return;
        ARRIVALS.remove(p.getUUID());
        VETO.remove(p.getUUID());
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    static void start(String name) {
        LotrLog.info("test", "START {}", name);
    }

    static void check(String name, boolean ok, String reason) {
        if (!ok) {
            LotrLog.info("test", "FAIL {}: {}", name, reason);
            throw new GameTestAssertException(name + ": " + reason);
        }
    }

    static void pass(GameTestHelper h, String name, String detail) {
        LotrLog.info("test", "PASS {}: {}", name, detail);
        h.succeed();
    }

    /** Runs a whole synchronous test body; an unexpected exception is logged as FAIL and fails the test. */
    static void run(GameTestHelper h, String name, Runnable body) {
        start(name);
        try {
            body.run();
        } catch (GameTestAssertException e) {
            throw e;
        } catch (RuntimeException e) {
            LotrLog.info("test", "FAIL {}: {}", name, e.toString());
            LotrLog.error("test", name + " threw", e);
            throw new GameTestAssertException(name + ": " + e);
        }
    }

    static double flat(Vec3 a, double x, double z) {
        return Math.hypot(a.x - x, a.z - z);
    }
}
