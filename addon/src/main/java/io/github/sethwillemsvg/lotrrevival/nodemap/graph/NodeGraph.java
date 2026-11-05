package io.github.sethwillemsvg.lotrrevival.nodemap.graph;

import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * The travel network: fixed nodes, two-way routes, a 512-block grid index and Dijkstra. Pure Java over GraphNode / GraphEdge
 * (no level, no player), read-only once built, so unit tests and the client build their own. Camps are not stored here:
 * a query passes the owner's camps and their links ({@link #linkCamps}) as extras.
 */
public final class NodeGraph {
    public static final int CELL = 512;
    public static final NodeGraph EMPTY = new NodeGraph(List.of(), List.of());

    private final Map<NodeRef, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges;
    private final Map<NodeRef, List<GraphEdge>> adjacency = new HashMap<>();
    private final Map<Long, List<GraphNode>> grid = new HashMap<>();

    public NodeGraph(Collection<GraphNode> nodeList, Collection<GraphEdge> edgeList) {
        for (GraphNode n : nodeList) {
            nodes.put(n.ref(), n);
            grid.computeIfAbsent(cell(n.x(), n.z()), k -> new ArrayList<>()).add(n);
        }
        List<GraphEdge> kept = new ArrayList<>();
        for (GraphEdge e : edgeList) {
            if (!nodes.containsKey(e.a()) || !nodes.containsKey(e.b())) continue;   // loader already warned
            kept.add(e);
            adjacency.computeIfAbsent(e.a(), k -> new ArrayList<>()).add(e);
            adjacency.computeIfAbsent(e.b(), k -> new ArrayList<>()).add(e);
        }
        edges = List.copyOf(kept);
    }

    private static long cell(double x, double z) {
        return ((long) Math.floorDiv((int) Math.floor(x), CELL) << 32) ^ (Math.floorDiv((int) Math.floor(z), CELL) & 0xFFFFFFFFL);
    }

    public Collection<GraphNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public List<GraphEdge> edges() {
        return edges;
    }

    @Nullable
    public GraphNode node(NodeRef ref) {
        return nodes.get(ref);
    }

    public List<GraphEdge> routes(NodeRef ref) {
        return adjacency.getOrDefault(ref, List.of());
    }

    /** Fixed nodes whose position is within {@code radius} blocks (horizontal) of x,z, nearest first. */
    public List<GraphNode> near(double x, double z, double radius) {
        List<GraphNode> out = new ArrayList<>();
        int r = (int) Math.ceil(radius / CELL);
        int cx = Math.floorDiv((int) Math.floor(x), CELL), cz = Math.floorDiv((int) Math.floor(z), CELL);
        double r2 = radius * radius;
        for (int i = cx - r; i <= cx + r; i++) {
            for (int j = cz - r; j <= cz + r; j++) {
                List<GraphNode> l = grid.get(((long) i << 32) ^ (j & 0xFFFFFFFFL));
                if (l == null) continue;
                for (GraphNode n : l) if (n.distSq(x, z) <= r2) out.add(n);
            }
        }
        out.sort(Comparator.comparingDouble(n -> n.distSq(x, z)));
        return out;
    }

    /** Number of connected components (log line on build). */
    public int components() {
        Set<NodeRef> seen = new HashSet<>();
        int c = 0;
        for (NodeRef start : nodes.keySet()) {
            if (!seen.add(start)) continue;
            c++;
            List<NodeRef> stack = new ArrayList<>(List.of(start));
            while (!stack.isEmpty()) {
                NodeRef n = stack.remove(stack.size() - 1);
                for (GraphEdge e : routes(n)) if (seen.add(e.other(n))) stack.add(e.other(n));
            }
        }
        return c;
    }

    /**
     * Wild links for camps: each camp links to the {@code count} nearest fixed nodes within {@code range} that pass
     * {@code linkable} (known, not barred). Worked out per query, so new discoveries can give a camp better links.
     */
    public List<GraphEdge> linkCamps(List<GraphNode> camps, Predicate<GraphNode> linkable, double range, int count, double wildFactor) {
        List<GraphEdge> out = new ArrayList<>();
        for (GraphNode camp : camps) {
            int n = 0;
            for (GraphNode f : near(camp.x(), camp.z(), range)) {
                if (n >= count) break;
                if (!linkable.test(f)) continue;
                out.add(GraphEdge.of("camp" + camp.ref().camp() + "-" + f.ref().shortName(), camp, f, RouteKind.WILD, List.of(),
                        wildFactor));
                n++;
            }
        }
        return out;
    }

    /**
     * Shortest journey from {@code from} to {@code to}. Every node on the way (ends included) must pass {@code usable}
     * (known). Nodes passing {@code barred} cannot be departure, destination or passed through; when the only way goes
     * through one, the result is BLOCKED naming the first barred node on that way.
     */
    public PathResult path(NodeRef from, NodeRef to, Predicate<NodeRef> usable, Predicate<NodeRef> barred,
                           List<GraphNode> extraNodes, List<GraphEdge> extraEdges) {
        Map<NodeRef, List<GraphEdge>> extraAdj = new HashMap<>();
        Set<NodeRef> extraRefs = new HashSet<>();
        for (GraphNode n : extraNodes) extraRefs.add(n.ref());
        for (GraphEdge e : extraEdges) {
            extraAdj.computeIfAbsent(e.a(), k -> new ArrayList<>()).add(e);
            extraAdj.computeIfAbsent(e.b(), k -> new ArrayList<>()).add(e);
        }
        boolean exists = (nodes.containsKey(from) || extraRefs.contains(from)) && (nodes.containsKey(to) || extraRefs.contains(to));
        if (!exists || !usable.test(from) || !usable.test(to)) return PathResult.noPath();
        PathResult clear = dijkstra(from, to, n -> usable.test(n) && !barred.test(n), extraAdj);
        if (clear != null) return clear;
        PathResult through = dijkstra(from, to, usable, extraAdj);
        if (through != null) {
            for (NodeRef r : through.chain()) {
                if (barred.test(r)) return new PathResult(PathResult.Status.BLOCKED, through.chain(), through.legs(), through.length(), r);
            }
        }
        return PathResult.noPath();
    }

    public PathResult path(NodeRef from, NodeRef to, Predicate<NodeRef> usable, Predicate<NodeRef> barred) {
        return path(from, to, usable, barred, List.of(), List.of());
    }

    @Nullable
    private PathResult dijkstra(NodeRef from, NodeRef to, Predicate<NodeRef> ok, Map<NodeRef, List<GraphEdge>> extraAdj) {
        if (!ok.test(from) || !ok.test(to)) return null;
        Map<NodeRef, Double> dist = new HashMap<>();
        Map<NodeRef, GraphEdge> via = new HashMap<>();
        PriorityQueue<Map.Entry<NodeRef, Double>> pq = new PriorityQueue<>(Map.Entry.comparingByValue());
        dist.put(from, 0.0);
        pq.add(Map.entry(from, 0.0));
        while (!pq.isEmpty()) {
            Map.Entry<NodeRef, Double> cur = pq.poll();
            NodeRef n = cur.getKey();
            if (cur.getValue() > dist.getOrDefault(n, Double.MAX_VALUE)) continue;
            if (n.equals(to)) break;
            List<GraphEdge> out = new ArrayList<>(routes(n));
            out.addAll(extraAdj.getOrDefault(n, List.of()));
            for (GraphEdge e : out) {
                NodeRef m = e.other(n);
                if (!ok.test(m)) continue;
                double d = cur.getValue() + e.length();
                if (d < dist.getOrDefault(m, Double.MAX_VALUE)) {
                    dist.put(m, d);
                    via.put(m, e);
                    pq.add(Map.entry(m, d));
                }
            }
        }
        if (!dist.containsKey(to)) return null;
        List<NodeRef> chain = new ArrayList<>();
        List<Double> legs = new ArrayList<>();
        NodeRef n = to;
        chain.add(n);
        while (!n.equals(from)) {
            GraphEdge e = via.get(n);
            legs.add(e.length());
            n = e.other(n);
            chain.add(n);
        }
        Collections.reverse(chain);
        Collections.reverse(legs);
        return new PathResult(PathResult.Status.FOUND, List.copyOf(chain), List.copyOf(legs), dist.get(to), null);
    }
}
