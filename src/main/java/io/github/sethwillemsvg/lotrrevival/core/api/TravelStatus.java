package io.github.sethwillemsvg.lotrrevival.core.api;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Client-side fast travel countdown state. Owner: Core (shape). Produced by the World pod through
 * {@link WaypointClient#status()}, read by the Progression pod's map page.
 *
 * @param state countdown, cancelled or arrived
 * @param targetRef waypoint ref being travelled to
 * @param ticksLeft ticks until the teleport (0 once cancelled or arrived)
 * @param reason why the travel was cancelled, if it was
 */
public record TravelStatus(State state, String targetRef, int ticksLeft, Optional<Component> reason) {
    public enum State {
        COUNTDOWN,
        CANCELLED,
        ARRIVED
    }
}
