package io.github.sethwillemsvg.lotrrevival.nodemap.discovery;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapStats;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphEdge;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import io.github.sethwillemsvg.lotrrevival.nodemap.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.sevenstars.middleearth.resources.datas.factions.data.SpawnData;
import net.sevenstars.middleearth.resources.persistent_datas.PlayerDataService;

/**
 * Discovery (spec Player-facing behaviour / Discovery): coming within a node's radius makes it known and rumours its
 * non-hidden neighbours; the first time in Middle-earth, nodes near the faction spawn become known (home reveal).
 */
public final class NodeDiscovery {
    /** Nodes may override the radius; the grid search looks this far at most. */
    private static final double MAX_SEARCH = 1024;

    private final NodeMapContext ctx;

    public NodeDiscovery(NodeMapContext ctx) {
        this.ctx = ctx;
    }

    /** Per player tick (server): home reveal once, then a discovery check every {@code discover_interval} ticks. */
    public void tick(ServerPlayer player) {
        if (!ctx.activeIn(player.level()) || player.isSpectator()) return;
        NodeMapSettings s = ctx.settings.get();
        long t = player.level().getGameTime() + (player.getUUID().hashCode() & 0xFF);   // spread players over the interval
        if (t % Math.max(1, s.discoverInterval()) != 0) return;
        NodeMapData data = NodeMapAttachment.get(player);
        if (!data.homeRevealed()) {
            SpawnData spawn = PlayerDataService.getPlayerSpawnData(player, player.level());
            if (spawn != null) homeReveal(player, spawn.getWorldCoordinateBlockPos());
            // no faction chosen yet: try again next check
        }
        check(player);
    }

    /** Discovers every node in range of the player; returns the newly known ones. */
    public List<GraphNode> check(ServerPlayer player) {
        NodeGraph g = ctx.graph.get();
        NodeMapSettings s = ctx.settings.get();
        NodeMapData data = NodeMapAttachment.get(player);
        List<GraphNode> found = new ArrayList<>();
        for (GraphNode n : g.near(player.getX(), player.getZ(), Math.max(s.discoverRadius(), MAX_SEARCH))) {
            if (data.isKnown(n.ref().fixed())) continue;
            double r = n.discoverRadius() > 0 ? n.discoverRadius() : s.discoverRadius();
            double d = n.dist(player.getX(), player.getZ());
            if (d > r) continue;
            List<String> rumoured = discover(player, g, data, n);
            found.add(n);
            NodeMapStats.nodemap("discoveries");
            LotrLog.dbg("nodemap", "discover: player={} node={} dist={} radius={} rumoured={} at={},{},{} tick={}",
                    LotrLog.id(player.getUUID()), n.ref().shortName(), Math.round(d), Math.round(r), rumoured, player.getBlockX(),
                    player.getBlockY(), player.getBlockZ(), player.level().getGameTime());
            player.displayClientMessage(Msg.t("discovered", "Discovered: %s.", n.name()), true);
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
        if (!found.isEmpty()) ctx.onChange.accept(player);
        return found;
    }

    /** Makes {@code n} known and rumours its non-hidden route neighbours; returns the ids newly rumoured. */
    public static List<String> discover(ServerPlayer player, NodeGraph g, NodeMapData data, GraphNode n) {
        data.addKnown(n.ref().fixed());
        List<String> rumoured = new ArrayList<>();
        for (GraphEdge e : g.routes(n.ref())) {
            GraphNode m = g.node(e.other(n.ref()));
            if (m == null || m.hidden()) continue;
            if (data.addRumoured(m.ref().fixed())) rumoured.add(m.ref().shortName());
        }
        return rumoured;
    }

    /** Home reveal: nodes within {@code home_reveal_radius} of the spawn become known (hidden ones excepted), once. */
    public List<GraphNode> homeReveal(ServerPlayer player, BlockPos spawn) {
        NodeMapData data = NodeMapAttachment.get(player);
        if (data.homeRevealed()) return List.of();
        data.setHomeRevealed(true);
        int radius = ctx.settings.get().homeRevealRadius();
        List<GraphNode> known = new ArrayList<>();
        if (radius > 0) {
            NodeGraph g = ctx.graph.get();
            for (GraphNode n : g.near(spawn.getX() + 0.5, spawn.getZ() + 0.5, radius)) {
                if (n.hidden()) continue;   // choice: hidden nodes are only found by going there
                discover(player, g, data, n);
                known.add(n);
            }
        }
        LotrLog.dbg("nodemap", "home reveal: player={} spawn={},{},{} radius={} known={}", LotrLog.id(player.getUUID()), spawn.getX(),
                spawn.getY(), spawn.getZ(), radius, known.stream().map(n -> n.ref().shortName()).toList());
        NodeMapStats.nodemap("home_reveals");
        ctx.onChange.accept(player);
        return known;
    }
}
