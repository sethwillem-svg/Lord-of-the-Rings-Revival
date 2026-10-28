package io.github.sethwillemsvg.lotrrevival.core.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side facade for waypoints and fast travel, in the {@link AchievementCounters} pattern. Owner:
 * Core. The World pod's client code registers the {@link Backend} once; the Progression pod's map page
 * calls the static methods, so Progression never imports {@code world}. Until a backend is registered
 * every read is empty and every request does nothing. Client side only.
 */
public final class WaypointClient {
    private static final Backend NONE = new Backend() {
        @Override
        public List<WaypointView> waypoints() {
            return List.of();
        }

        @Override
        public int clockTicks() {
            return 0;
        }

        @Override
        public int customLimit() {
            return 0;
        }

        @Override
        public Optional<TravelStatus> status() {
            return Optional.empty();
        }

        @Override
        public void requestTravel(String ref) {}

        @Override
        public void createCustom(String name) {}

        @Override
        public void renameCustom(int id, String name) {}

        @Override
        public void deleteCustom(int id) {}
    };

    private static volatile Backend current = NONE;

    private WaypointClient() {}

    /** The implementation, supplied by the World pod's client code. */
    public interface Backend {
        List<WaypointView> waypoints();

        /** The player's travel clock in ticks. */
        int clockTicks();

        /** Maximum number of custom waypoints. */
        int customLimit();

        Optional<TravelStatus> status();

        void requestTravel(String ref);

        void createCustom(String name);

        void renameCustom(int id, String name);

        void deleteCustom(int id);
    }

    /** Registers the implementation (World only). Throws if one is already registered. */
    public static synchronized void register(Backend backend) {
        Objects.requireNonNull(backend);
        if (current != NONE) {
            throw new IllegalStateException("A WaypointClient backend is already registered");
        }
        current = backend;
    }

    public static List<WaypointView> waypoints() {
        return current.waypoints();
    }

    public static int clockTicks() {
        return Math.max(0, current.clockTicks());
    }

    public static int customLimit() {
        return Math.max(0, current.customLimit());
    }

    public static Optional<TravelStatus> status() {
        return current.status();
    }

    public static void requestTravel(String ref) {
        current.requestTravel(ref);
    }

    public static void createCustom(String name) {
        current.createCustom(name);
    }

    public static void renameCustom(int id, String name) {
        current.renameCustom(id, name);
    }

    public static void deleteCustom(int id) {
        current.deleteCustom(id);
    }
}
