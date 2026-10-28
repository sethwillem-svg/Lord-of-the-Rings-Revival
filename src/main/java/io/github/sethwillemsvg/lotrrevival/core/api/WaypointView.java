package io.github.sethwillemsvg.lotrrevival.core.api;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/**
 * Client-side snapshot of one waypoint as the map page shows it. Owner: Core (shape). Produced by the
 * World pod through {@link WaypointClient}, read by the Progression pod's map page.
 *
 * @param ref wire reference, {@code fixed:<namespace:path>} or {@code custom:<id>}
 * @param name display name
 * @param lore lore text shown in the tooltip (empty component for custom waypoints)
 * @param u heightmap pixel x
 * @param v heightmap pixel y
 * @param kind fixed or custom
 * @param state locked, usable or barred
 * @param barringFaction the faction that bars the player, present only when {@code state} is {@code BARRED}
 * @param uses arrivals at this waypoint
 * @param requiredTicks travel clock ticks needed before the player may travel here
 */
public record WaypointView(
        String ref,
        Component name,
        Component lore,
        int u,
        int v,
        Kind kind,
        State state,
        Optional<ResourceKey<Faction>> barringFaction,
        int uses,
        int requiredTicks) {
    public enum Kind {
        FIXED,
        CUSTOM
    }

    public enum State {
        LOCKED,
        USABLE,
        BARRED
    }
}
