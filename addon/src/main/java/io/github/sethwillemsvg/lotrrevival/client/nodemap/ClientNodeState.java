package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapConfig;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.NodeRegistries;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.NodeStatePayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.TravelStatusPayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelCost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * What the client knows about the node map: the last node_state and travel_status from the server, and a graph built
 * from the synced registries (rebuilt when the registries change, i.e. on joining a world).
 */
public final class ClientNodeState {
    @Nullable private static NodeStatePayload state;
    @Nullable private static TravelStatusPayload status;
    private static long statusAt;
    private static NodeGraph graph = NodeGraph.EMPTY;
    @Nullable private static RegistryAccess graphFor;
    private static Set<ResourceLocation> known = Set.of(), rumoured = Set.of();
    private static Map<ResourceLocation, ResourceLocation> barred = Map.of();

    private ClientNodeState() {}

    public static void onState(NodeStatePayload p) {
        state = p;
        known = new HashSet<>(p.known());
        rumoured = new HashSet<>(p.rumoured());
        Map<ResourceLocation, ResourceLocation> b = new HashMap<>();
        for (NodeStatePayload.Barred x : p.barred()) b.put(x.node(), x.faction());
        barred = b;
        LotrLog.dbg("nodemap", "client state: known={} rumoured={} barred={} camps={} cooldown={} last={}", p.known().size(),
                p.rumoured().size(), p.barred().size(), p.camps().size(), p.cooldown(), p.lastNode());
    }

    public static void onStatus(TravelStatusPayload p) {
        status = p;
        statusAt = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        LotrLog.dbg("travel", "client status: {} target={} left={}/{} reason={}", p.state(), p.target(), p.ticksLeft(), p.total(),
                p.reason().getString());
    }

    @Nullable
    public static NodeStatePayload state() {
        return state;
    }

    @Nullable
    public static TravelStatusPayload status() {
        return status;
    }

    public static long statusAt() {
        return statusAt;
    }

    public static void clearStatus() {
        status = null;
    }

    public static NodeGraph graph() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return NodeGraph.EMPTY;
        RegistryAccess access = mc.level.registryAccess();
        if (access != graphFor) {
            graphFor = access;
            try {
                graph = NodeRegistries.buildGraph(access, NodeMapConfig.factors(), NodeMapConfig.discoverRadius(), "client");
            } catch (RuntimeException ex) {
                LotrLog.error("nodemap", "client: building the node graph failed", ex);
                graph = NodeGraph.EMPTY;
            }
        }
        return graph;
    }

    public static boolean isKnown(NodeRef r) {
        if (r.isCamp()) return camp(r.camp()) != null;
        GraphNode n = graph().node(r);
        return n != null && (known.contains(r.fixed()) || n.startKnown());
    }

    public static boolean isRumoured(NodeRef r) {
        return !r.isCamp() && rumoured.contains(r.fixed());
    }

    public static boolean isBarred(NodeRef r) {
        return !r.isCamp() && barred.containsKey(r.fixed());
    }

    @Nullable
    public static NodeStatePayload.CampInfo camp(int id) {
        if (state == null) return null;
        for (NodeStatePayload.CampInfo c : state.camps()) if (c.id() == id) return c;
        return null;
    }

    public static List<GraphNode> campNodes() {
        List<GraphNode> out = new ArrayList<>();
        if (state == null) return out;
        for (NodeStatePayload.CampInfo c : state.camps()) out.add(GraphNode.camp(c.id(), c.name(), c.pos().getX() + 0.5, c.pos().getZ() + 0.5));
        return out;
    }

    /** Camp links as sent by the server (it decides which nodes a camp links to). */
    public static List<GraphEdge> campEdges() {
        List<GraphEdge> out = new ArrayList<>();
        if (state == null) return out;
        NodeGraph g = graph();
        for (GraphNode c : campNodes()) {
            NodeStatePayload.CampInfo info = camp(c.ref().camp());
            for (String l : info.links()) {
                NodeRef r = NodeRef.parse(l);
                GraphNode f = r == null ? null : g.node(r);
                if (f != null) out.add(GraphEdge.of("camp" + info.id(), c, f, io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind.WILD,
                        List.of(), state.wildFactor()));
            }
        }
        return out;
    }

    @Nullable
    public static GraphNode node(NodeRef r) {
        if (!r.isCamp()) return graph().node(r);
        for (GraphNode c : campNodes()) if (c.ref().equals(r)) return c;
        return null;
    }

    /** Same search the server runs, for the gold highlight and the panel numbers. */
    public static PathResult path(NodeRef from, NodeRef to) {
        return graph().path(from, to, ClientNodeState::isKnown, ClientNodeState::isBarred, campNodes(), campEdges());
    }

    /** The nearest known, unbarred node within the departure radius of the player, or null. */
    @Nullable
    public static GraphNode departure() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state == null) return null;
        double px = mc.player.getX(), pz = mc.player.getZ(), r = state.departRadius();
        GraphNode best = null;
        List<GraphNode> all = new ArrayList<>(graph().near(px, pz, r));
        all.addAll(campNodes());
        for (GraphNode n : all) {
            if (n.distSq(px, pz) > r * r || !isKnown(n.ref()) || isBarred(n.ref())) continue;
            if (best == null || n.distSq(px, pz) < best.distSq(px, pz)) best = n;
        }
        return best;
    }

    public static TravelCost cost() {
        if (state == null) return TravelCost.DEFAULTS;
        return new TravelCost(state.cooldownBase(), state.cooldownPerBlock(), state.cooldownMax(), state.exhaustionPerBlock(),
                state.exhaustionCap(), 0);
    }
}
