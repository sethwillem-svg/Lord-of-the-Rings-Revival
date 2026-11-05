package io.github.sethwillemsvg.lotrrevival.nodemap.state;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * One player's view of the graph for one query: which nodes are known, rumoured or barred, their camps as nodes with
 * their current links, and the route search over all that. Reputation is read once per faction per view.
 */
public final class NodeAccess {
    public enum State { UNKNOWN, RUMOURED, KNOWN, BARRED, CUSTOM }

    public final NodeMapContext ctx;
    public final ServerPlayer player;
    public final NodeMapData data;
    public final NodeGraph graph;
    private final NodeMapSettings settings;
    private final Map<ResourceLocation, Boolean> barredFaction = new HashMap<>();
    private final List<GraphNode> camps = new ArrayList<>();
    private final List<GraphEdge> campLinks;

    public NodeAccess(NodeMapContext ctx, ServerPlayer player) {
        this.ctx = ctx;
        this.player = player;
        this.data = NodeMapAttachment.get(player);
        this.graph = ctx.graph.get();
        this.settings = ctx.settings.get();
        for (NodeMapData.Camp c : data.camps()) camps.add(GraphNode.camp(c.id(), c.name(), c.pos().getX() + 0.5, c.pos().getZ() + 0.5));
        this.campLinks = graph.linkCamps(camps, n -> isKnown(n.ref()) && !isBarred(n), settings.customLinkRange(),
                settings.customLinks(), settings.wildFactor());
    }

    public List<GraphNode> camps() {
        return camps;
    }

    public List<GraphEdge> campLinks() {
        return campLinks;
    }

    @Nullable
    public GraphNode node(NodeRef ref) {
        if (!ref.isCamp()) return graph.node(ref);
        for (GraphNode c : camps) if (c.ref().equals(ref)) return c;
        return null;
    }

    public boolean isKnown(NodeRef ref) {
        if (ref.isCamp()) return data.camp(ref.camp()) != null;
        return graph.node(ref) != null && (data.isKnown(ref.fixed()) || graph.node(ref).startKnown());
    }

    public boolean isBarred(GraphNode n) {
        if (n.faction() == null) return false;
        return barredFaction.computeIfAbsent(n.faction(), f -> {
            int rep = ctx.standing.reputation(player, f);
            return rep < 0;
        });
    }

    public boolean isBarred(NodeRef ref) {
        GraphNode n = node(ref);
        return n != null && isBarred(n);
    }

    public int reputation(GraphNode n) {
        return n.faction() == null ? 0 : ctx.standing.reputation(player, n.faction());
    }

    public State state(GraphNode n) {
        if (n.ref().isCamp()) return State.CUSTOM;
        if (isKnown(n.ref())) return isBarred(n) ? State.BARRED : State.KNOWN;
        if (data.isRumoured(n.ref().fixed())) return State.RUMOURED;
        return State.UNKNOWN;
    }

    /** Known, unbarred nodes (fixed and camps) within {@code radius} of the player, nearest first. */
    public List<GraphNode> usableNear(double radius) {
        List<GraphNode> out = new ArrayList<>();
        for (GraphNode n : graph.near(player.getX(), player.getZ(), radius)) if (isKnown(n.ref()) && !isBarred(n)) out.add(n);
        double r2 = radius * radius;
        for (GraphNode c : camps) if (c.distSq(player.getX(), player.getZ()) <= r2) out.add(c);
        out.sort(java.util.Comparator.comparingDouble(n -> n.distSq(player.getX(), player.getZ())));
        return out;
    }

    public PathResult path(NodeRef from, NodeRef to) {
        PathResult r = graph.path(from, to, this::isKnown, this::isBarred, camps, campLinks);
        LotrLog.dbg("nodemap", "path: player={} from={} to={} {}", LotrLog.id(player.getUUID()), from, to, r.describe());
        return r;
    }
}
