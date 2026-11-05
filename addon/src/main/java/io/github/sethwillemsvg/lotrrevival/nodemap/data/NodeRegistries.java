package io.github.sethwillemsvg.lotrrevival.nodemap.data;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.MapCoords;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;

/**
 * Datapack registries {@code lotr_revival:node} and {@code lotr_revival:route} (synced to clients), and the step that turns
 * them into a {@link NodeGraph}: positions resolved, bad entries skipped with a WARN.
 */
public final class NodeRegistries {
    public static final ResourceKey<Registry<NodeDef>> NODE = ResourceKey.createRegistryKey(LotrRevival.id("node"));
    public static final ResourceKey<Registry<RouteDef>> ROUTE = ResourceKey.createRegistryKey(LotrRevival.id("route"));

    private NodeRegistries() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((DataPackRegistryEvent.NewRegistry e) -> {
            e.dataPackRegistry(NODE, NodeDef.CODEC, NodeDef.CODEC);
            e.dataPackRegistry(ROUTE, RouteDef.CODEC, RouteDef.CODEC);
        });
    }

    /** Builds the graph from the registries in {@code access}; {@code side} ("server"/"client") only labels the log lines. */
    public static NodeGraph buildGraph(RegistryAccess access, Map<RouteKind, Double> factors, int defaultRadius, String side) {
        Optional<Registry<NodeDef>> nodeReg = access.registry(NODE);
        Optional<Registry<RouteDef>> routeReg = access.registry(ROUTE);
        if (nodeReg.isEmpty() || routeReg.isEmpty()) {
            LotrLog.warn("nodemap", "{}: node/route registries missing; node map is empty", side);
            return NodeGraph.EMPTY;
        }
        var factions = access.registry(DynamicRegistriesME.FACTION);
        if (factions.isEmpty()) LotrLog.warn("nodemap", "{}: Middle-earth faction registry missing; faction ids not checked", side);

        List<GraphNode> nodes = new ArrayList<>();
        Map<ResourceLocation, GraphNode> byId = new HashMap<>();
        for (Map.Entry<ResourceKey<NodeDef>, NodeDef> en : nodeReg.get().entrySet()) {
            ResourceLocation id = en.getKey().location();
            NodeDef d = en.getValue();
            if (d.map().isPresent() == d.block().isPresent()) {
                LotrLog.warn("nodemap", "node {} skipped: needs exactly one of \"map\" or \"block\"", id);
                continue;
            }
            if (d.kind() == NodeKind.CAMP) {
                LotrLog.warn("nodemap", "node {} skipped: kind \"camp\" is for players' camps only", id);
                continue;
            }
            double[] xz = d.map().map(m -> MapCoords.mapToBlock(m.x(), m.z())).orElseGet(() -> new double[] {d.block().get().x(), d.block().get().z()});
            if (!MapCoords.inBorder(xz[0], xz[1])) {
                LotrLog.warn("nodemap", "node {} skipped: {},{} is outside the Middle-earth map border", id, Math.round(xz[0]), Math.round(xz[1]));
                continue;
            }
            ResourceLocation faction = d.faction().orElse(null);
            if (faction != null && factions.isPresent() && !factions.get().containsKey(faction)) {
                LotrLog.warn("nodemap", "node {}: unknown faction id {}; treated as unowned", id, faction);
                faction = null;
            }
            GraphNode n = new GraphNode(NodeRef.fixed(id), d.name(), d.description(), d.kind(), xz[0], xz[1], faction,
                    d.discoverRadius().orElse(-1), d.hidden(), d.startKnown());
            nodes.add(n);
            byId.put(id, n);
        }

        List<GraphEdge> edges = new ArrayList<>();
        for (Map.Entry<ResourceKey<RouteDef>, RouteDef> en : routeReg.get().entrySet()) {
            ResourceLocation id = en.getKey().location();
            RouteDef r = en.getValue();
            GraphNode a = byId.get(r.from()), b = byId.get(r.to());
            if (a == null || b == null) {
                LotrLog.warn("nodemap", "route {} skipped: unknown end {}", id, a == null ? r.from() : r.to());
                continue;
            }
            if (a == b) {
                LotrLog.warn("nodemap", "route {} skipped: both ends are {}", id, r.from());
                continue;
            }
            List<XZ> bends = new ArrayList<>();
            for (XZ v : r.via()) {
                double[] w = MapCoords.mapToBlock(v.x(), v.z());
                bends.add(new XZ(w[0], w[1]));
            }
            bends.addAll(r.viaBlock());
            GraphEdge e = GraphEdge.of(id.toString(), a, b, r.kind(), bends, factors.getOrDefault(r.kind(), r.kind().defaultFactor));
            if (e.rawLength() < 1e-3) {
                LotrLog.warn("nodemap", "route {} skipped: zero length", id);
                continue;
            }
            edges.add(e);
        }

        NodeGraph g = new NodeGraph(nodes, edges);
        Map<String, List<String>> perNs = new TreeMap<>(nodes.stream().collect(Collectors.groupingBy(
                n -> n.ref().fixed().getNamespace(), Collectors.mapping(n -> n.ref().fixed().getPath(), Collectors.toList()))));
        LotrLog.info("nodemap", "{}: loaded {} node files, {} route files; nodes by namespace {}", side, nodeReg.get().size(),
                routeReg.get().size(), perNs);
        LotrLog.info("nodemap", "{}: graph built: nodes={} routes={} components={} (default discover radius {})", side,
                g.nodes().size(), g.edges().size(), g.components(), defaultRadius);
        return g;
    }
}
