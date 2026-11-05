package io.github.sethwillemsvg.lotrrevival.nodemap;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counters for the 600-tick summary lines (debug-logging.md): discoveries and camps for {@code nodemap}; requests,
 * refusals by reason, cancels by reason and trips for {@code travel}. Server thread only.
 */
public final class NodeMapStats {
    public static final int WINDOW = 600;

    private static final Map<String, Integer> NODEMAP = new TreeMap<>();
    private static final Map<String, Integer> TRAVEL = new TreeMap<>();

    private NodeMapStats() {}

    public static synchronized void nodemap(String key) {
        NODEMAP.merge(key, 1, Integer::sum);
    }

    public static synchronized void travel(String key) {
        TRAVEL.merge(key, 1, Integer::sum);
    }

    /** Called every server tick; logs and resets once per window while debug is on. */
    public static synchronized void tick(long gameTime) {
        if (gameTime % WINDOW != 0) return;
        if (LotrLog.debug("nodemap")) LotrLog.dbg("nodemap", "summary {} ticks @{}: {}", WINDOW, gameTime, NODEMAP.isEmpty() ? "nothing" : NODEMAP);
        if (LotrLog.debug("travel")) LotrLog.dbg("travel", "summary {} ticks @{}: {}", WINDOW, gameTime, TRAVEL.isEmpty() ? "nothing" : TRAVEL);
        NODEMAP.clear();
        TRAVEL.clear();
    }
}
