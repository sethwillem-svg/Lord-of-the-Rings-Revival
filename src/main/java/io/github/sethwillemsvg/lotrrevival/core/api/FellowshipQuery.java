package io.github.sethwillemsvg.lotrrevival.core.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Holder for the single fellowship query implementation. Progression registers it; the NPC pod's hired-unit
 * targeting calls the static methods. Until registered both answers are false.
 */
public final class FellowshipQuery {
    private static final Impl NONE = new Impl() {};
    private static volatile Impl current = NONE;

    private FellowshipQuery() {}

    /** The queries Progression answers. Every default is false. */
    public interface Impl {
        /** True if the two players share a fellowship that prevents PvP between them. */
        default boolean preventsPvp(UUID a, UUID b) {
            return false;
        }

        /** True if units owned by one player must not target the other player or their units. */
        default boolean protectsUnits(UUID a, UUID b) {
            return false;
        }
    }

    /** Registers the implementation (Progression only). Throws if one is already registered. */
    public static synchronized void register(Impl impl) {
        Objects.requireNonNull(impl);
        if (current != NONE) {
            throw new IllegalStateException("A FellowshipQuery implementation is already registered");
        }
        current = impl;
    }

    /** Test hook: replaces the implementation (null restores the defaults) and returns the previous one or null. */
    public static synchronized Impl swapForTesting(Impl impl) {
        Impl previous = current;
        current = impl == null ? NONE : impl;
        return previous == NONE ? null : previous;
    }

    public static boolean preventsPvp(UUID a, UUID b) {
        return current.preventsPvp(a, b);
    }

    public static boolean protectsUnits(UUID a, UUID b) {
        return current.protectsUnits(a, b);
    }
}
