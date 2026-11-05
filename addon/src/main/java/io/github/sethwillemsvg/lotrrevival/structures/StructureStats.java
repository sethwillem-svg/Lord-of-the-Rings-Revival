package io.github.sethwillemsvg.lotrrevival.structures;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters for the 600-tick DBG summary (specs/addon/structures.md, Logging). Thread safe: placement checks run on
 * worldgen worker threads.
 */
public final class StructureStats {
    public static final String SYS = "structures";
    public static final int SUMMARY_TICKS = 600;

    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong LOOKUP_NANOS = new AtomicLong();

    private StructureStats() {}

    public static void count(String key) {
        add(key, 1);
    }

    public static void add(String key, long n) {
        COUNTS.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(n);
    }

    public static void lookup(long nanos) {
        count("lookups");
        LOOKUP_NANOS.addAndGet(nanos);
    }

    public static long get(String key) {
        AtomicLong v = COUNTS.get(key);
        return v == null ? 0 : v.get();
    }

    /** Logs and resets the window. Called every {@link #SUMMARY_TICKS} server ticks while debug is on. */
    public static void summary(long tick) {
        if (!LotrLog.debug(SYS)) return;
        Map<String, Long> snap = new TreeMap<>();
        COUNTS.forEach((k, v) -> snap.put(k, v.getAndSet(0)));
        long nanos = LOOKUP_NANOS.getAndSet(0);
        long lookups = snap.getOrDefault("lookups", 0L);
        StringBuilder skips = new StringBuilder();
        snap.forEach((k, v) -> {
            if (k.startsWith("skip.") && v > 0) skips.append(' ').append(k.substring(5)).append('=').append(v);
        });
        LotrLog.dbg(SYS, "summary tick={} candidates={} placed={} skipped:[{} ] venues added={} completed={} dropped={} "
                        + "probes done={} queued={} lookups={} avg={} ms credits={}",
                tick, snap.getOrDefault("candidates", 0L), snap.getOrDefault("placed", 0L), skips,
                snap.getOrDefault("venue.added", 0L), snap.getOrDefault("venue.completed", 0L),
                snap.getOrDefault("venue.dropped", 0L), snap.getOrDefault("probe.done", 0L),
                snap.getOrDefault("probe.queued", 0L), lookups,
                lookups == 0 ? "0" : String.format("%.3f", nanos / 1e6 / lookups), snap.getOrDefault("credits", 0L));
    }
}
