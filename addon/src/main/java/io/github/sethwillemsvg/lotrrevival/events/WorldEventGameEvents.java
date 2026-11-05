package io.github.sethwillemsvg.lotrrevival.events;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** {@code WorldEventStartedEvent} (cancellable) and {@code WorldEventEndedEvent} on the NeoForge bus. */
public final class WorldEventGameEvents {
    private WorldEventGameEvents() {}

    public static final class Started extends Event implements ICancellableEvent {
        private final WorldEvent event;

        public Started(WorldEvent event) {
            this.event = event;
        }

        public WorldEvent getEvent() {
            return event;
        }
    }

    /** Type, outcome, venue, participants and sides are all on the event. */
    public static final class Ended extends Event {
        private final WorldEvent event;

        public Ended(WorldEvent event) {
            this.event = event;
        }

        public WorldEvent getEvent() {
            return event;
        }
    }
}
