package io.github.sethwillemsvg.lotrrevival.world.portal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stops ping-pong: an entity that just arrived through a ring portal is ignored by every ring portal until it has
 * stopped touching one for {@code gracePeriod} ticks. Time is passed in so tests do not need a server.
 */
public final class ReentryGuard {
    public static final int DEFAULT_TICKS = 60;

    private final int gracePeriod;
    private final Map<UUID, Long> lastTouch = new HashMap<>();

    public ReentryGuard(int gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    /** Marks an arrival at time {@code now}. */
    public void mark(UUID id, long now) {
        lastTouch.put(id, now);
    }

    /**
     * Called for every entity touching a portal. Returns true when the entity must be ignored; this counts as
     * touching, so the grace period restarts.
     */
    public boolean blocks(UUID id, long now) {
        Long last = lastTouch.get(id);
        if (last == null) {
            return false;
        }
        if (now - last > gracePeriod) {
            lastTouch.remove(id);
            return false;
        }
        lastTouch.put(id, now);
        return true;
    }

    /** Drops entries that expired. */
    public void purge(long now) {
        lastTouch.values().removeIf(last -> now - last > gracePeriod);
    }

    public void clear() {
        lastTouch.clear();
    }

    public int size() {
        return lastTouch.size();
    }
}
