package io.github.sethwillemsvg.lotrrevival.core.log;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One logger per add-on system (specs/addon/debug-logging.md). Normal lines: {@code log.info(LotrLog.tag("combat") + "...")}
 * or {@link #info}. Debug lines go through {@link #dbg} and only print when {@code -Dlotr_revival.debug=all} or a list
 * naming the system is set; they print at INFO with the prefix {@code [DBG <system>]} so they reach latest.log.
 */
public final class LotrLog {
    public static final String PROPERTY = "lotr_revival.debug";

    private static final Map<String, Logger> LOGGERS = new ConcurrentHashMap<>();
    private static volatile Set<String> debugSystems = parse(System.getProperty(PROPERTY, ""));

    private LotrLog() {}

    public static Logger get(String system) {
        return LOGGERS.computeIfAbsent(system, s -> LoggerFactory.getLogger("lotr_revival." + s));
    }

    public static boolean debug(String system) {
        Set<String> d = debugSystems;
        return d.contains("all") || d.contains(system);
    }

    /** Replaces the debug list (for example from the config); the JVM property wins when set. */
    public static void setDebugSystems(String list) {
        String prop = System.getProperty(PROPERTY);
        debugSystems = parse(prop != null && !prop.isBlank() ? prop : list);
    }

    public static void info(String system, String msg, Object... args) {
        get(system).info("[" + system + "] " + msg, args);
    }

    public static void warn(String system, String msg, Object... args) {
        get(system).warn("[" + system + "] " + msg, args);
    }

    public static void error(String system, String msg, Throwable t) {
        get(system).error("[" + system + "] " + msg, t);
    }

    public static void dbg(String system, String msg, Object... args) {
        if (debug(system)) get(system).info("[DBG " + system + "] " + msg, args);
    }

    /** Short form of a UUID for log lines: the first 8 hex digits. */
    public static String id(java.util.UUID uuid) {
        return uuid == null ? "-" : uuid.toString().substring(0, 8);
    }

    private static Set<String> parse(String list) {
        return Arrays.stream(list.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
