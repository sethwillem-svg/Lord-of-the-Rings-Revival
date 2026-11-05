package io.github.sethwillemsvg.lotrrevival.nodemap;

import io.github.sethwillemsvg.lotrrevival.nodemap.data.RouteKind;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelCost;

/**
 * A snapshot of the node map and travel numbers. The live services read {@link #live()} (the config) on each use; tests
 * build their own with {@link #defaults()} and the {@code with...} copies.
 */
public record NodeMapSettings(boolean enabled, int departRadius, int chargeTicks, int minFood, int hostileRange, int combatTagTicks,
                              TravelCost cost, int discoverInterval, int discoverRadius, int homeRevealRadius, int customMax,
                              int customSpacing, int customLinkRange, int customLinks, double wildFactor) {
    public static NodeMapSettings live() {
        return new NodeMapSettings(NodeMapConfig.enabled(), NodeMapConfig.departRadius(), NodeMapConfig.chargeTicks(),
                NodeMapConfig.minFood(), NodeMapConfig.hostileRange(), NodeMapConfig.combatTagTicks(), NodeMapConfig.cost(),
                NodeMapConfig.discoverInterval(), NodeMapConfig.discoverRadius(), NodeMapConfig.homeRevealRadius(),
                NodeMapConfig.customMax(), NodeMapConfig.customSpacing(), NodeMapConfig.customLinkRange(), NodeMapConfig.customLinks(),
                NodeMapConfig.factors().getOrDefault(RouteKind.WILD, RouteKind.WILD.defaultFactor));
    }

    public static NodeMapSettings defaults() {
        return new NodeMapSettings(true, 64, 200, 6, 16, 100, TravelCost.DEFAULTS, 20, 96, 1000, 5, 48, 3000, 2,
                RouteKind.WILD.defaultFactor);
    }

    public NodeMapSettings withChargeTicks(int t) {
        return new NodeMapSettings(enabled, departRadius, t, minFood, hostileRange, combatTagTicks, cost, discoverInterval,
                discoverRadius, homeRevealRadius, customMax, customSpacing, customLinkRange, customLinks, wildFactor);
    }

    public NodeMapSettings withEnabled(boolean e) {
        return new NodeMapSettings(e, departRadius, chargeTicks, minFood, hostileRange, combatTagTicks, cost, discoverInterval,
                discoverRadius, homeRevealRadius, customMax, customSpacing, customLinkRange, customLinks, wildFactor);
    }
}
