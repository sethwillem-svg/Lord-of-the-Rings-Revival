package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.core.api.TravelStatus;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Client-side copy of the latest {@code world/travel_status} message. Plain data with no client class references; the
 * client travel mirror (sync ticket) reads it to implement {@code WaypointClient.status()}.
 */
public final class TravelStatusMirror {
    @Nullable
    private static volatile TravelStatus latest;

    private TravelStatusMirror() {}

    public static void apply(TravelStatusPayload payload) {
        latest = payload.toStatus();
    }

    public static Optional<TravelStatus> latest() {
        return Optional.ofNullable(latest);
    }

    public static void clear() {
        latest = null;
    }
}
