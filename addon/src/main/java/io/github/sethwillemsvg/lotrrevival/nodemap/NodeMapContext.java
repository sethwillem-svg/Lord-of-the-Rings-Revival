package io.github.sethwillemsvg.lotrrevival.nodemap;

import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeStanding;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.ArrivalFinder;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CombatTagTracker;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.sevenstars.middleearth.world.dimension.ModDimensions;

/**
 * What the node map services run against: the graph, the numbers, reputation, the arrival search and the combat tags.
 * {@link NodeMapModule#LIVE} uses the real ones and only acts in Middle-earth; game tests build their own (fixture graph,
 * fixed reputation, top-block arrival, any dimension), so tests never touch the live state.
 */
public final class NodeMapContext {
    public final String label;
    public final Supplier<NodeGraph> graph;
    public final Supplier<NodeMapSettings> settings;
    public final NodeStanding standing;
    public final ArrivalFinder arrival;
    public final CombatTagTracker combat;
    public final boolean requireMiddleEarth;
    /** Called after a player's node data changed (live: send node_state; tests: nothing, mock players have no channel). */
    public final Consumer<ServerPlayer> onChange;

    public NodeMapContext(String label, Supplier<NodeGraph> graph, Supplier<NodeMapSettings> settings, NodeStanding standing,
                          ArrivalFinder arrival, CombatTagTracker combat, boolean requireMiddleEarth, Consumer<ServerPlayer> onChange) {
        this.label = label;
        this.graph = graph;
        this.settings = settings;
        this.standing = standing;
        this.arrival = arrival;
        this.combat = combat;
        this.requireMiddleEarth = requireMiddleEarth;
        this.onChange = onChange;
    }

    /** True where the services act: Middle-earth for the live context, anywhere for a test context. */
    public boolean activeIn(Level level) {
        return !requireMiddleEarth || ModDimensions.isInMiddleEarth(level);
    }
}
