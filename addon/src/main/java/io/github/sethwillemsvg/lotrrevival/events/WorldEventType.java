package io.github.sethwillemsvg.lotrrevival.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** One kind of world event (fixed in code for the first set: warband clash, muster, raid). */
public interface WorldEventType {
    ResourceLocation id();

    /** Venue kinds it prefers, in order; empty for none. */
    List<String> venueKinds();

    /** True when it cannot happen without a venue (raid). */
    default boolean venueRequired() {
        return false;
    }

    /** The scheduler's pick for this type: factions and centre; the type fills in its own needs. Null when this site cannot host it. */
    default String rejectReason(SiteRoster.Pick pick) {
        return null;
    }

    /** Spawns the participants. Returns the number placed (0 = failed). */
    int stage(WorldEvent event, ServerLevel level, SiteRoster.Pick pick);

    /** Called once a second while active: decide whether a side has won. Return NONE to keep going. */
    WorldEvent.Outcome check(WorldEvent event, ServerLevel level);

    /** Who wins when the time runs out (the lead loses, except for Muster). */
    default WorldEvent.Outcome onTimeout() {
        return WorldEvent.Outcome.OPPOSITION_WON;
    }

    /** Reputation bonus for each paid participant of the winning side. */
    int bonus(WorldEvent.Outcome outcome);

    /** Lang key of the title for a viewer of this side (lead or opposition). */
    String titleKey(String viewerSide);
}
