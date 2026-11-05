package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.server.MinecraftServer;

import java.util.function.ToDoubleFunction;

/**
 * Load governor (spec Load governor, simplified): Green / Yellow / Red from the average tick time and the chunk budget, with a
 * 10 s minimum hold and 5 ms hysteresis on the way down. The tick-time source is injectable ({@link #tickSource}) for tests.
 */
public final class LoadGovernor {
    public enum State { GREEN, YELLOW, RED }

    /** Average tick time in ms; the default reads the server's own average (last 100 ticks). */
    public static ToDoubleFunction<MinecraftServer> tickSource = s -> s.getAverageTickTimeNanos() / 1_000_000.0;

    /** Game tests: stop the real tick source from moving the state (a slow CI server must not block test events). */
    public static volatile boolean frozen;

    private static State state = State.GREEN;
    private static long since;
    private static long redSince = -1;
    private static double lastMs;

    private LoadGovernor() {}

    public static State state() {
        return state;
    }

    public static double lastMs() {
        return lastMs;
    }

    /** True once Red has lasted the shed delay (active events far from players resolve off-screen). */
    public static boolean shedding(long tick) {
        return state == State.RED && redSince >= 0 && tick - redSince >= EventsConfig.i(EventsConfig.GOV_SHED_SECONDS) * 20L;
    }

    /** Once a second from the events tick. */
    public static void tick(MinecraftServer server) {
        if (frozen) return;
        update(server.getTickCount(), tickSource.applyAsDouble(server), EventChunkTickets.budgetFraction());
    }

    /** One governor step: {@code tick} in server ticks, {@code ms} average tick time, {@code chunkFraction} used / budget. */
    public static State update(long tick, double ms, double chunkFraction) {
        lastMs = ms;
        double yellow = EventsConfig.d(EventsConfig.GOV_YELLOW_MS), red = EventsConfig.d(EventsConfig.GOV_RED_MS);
        double hyst = EventsConfig.d(EventsConfig.GOV_HYSTERESIS_MS);
        long hold = EventsConfig.i(EventsConfig.GOV_HOLD_SECONDS) * 20L;
        State want;
        if (ms > red || chunkFraction >= 1.0) want = State.RED;
        else if (ms >= yellow || chunkFraction >= 0.75) want = State.YELLOW;
        else want = State.GREEN;
        // dropping a level needs the average 5 ms below that level's entry value
        if (want.ordinal() < state.ordinal()) {
            if (state == State.RED && (ms > red - hyst || chunkFraction >= 1.0)) want = State.RED;
            else if (state == State.RED) want = State.YELLOW;
            else if (state == State.YELLOW && (ms >= yellow - hyst || chunkFraction >= 0.75)) want = State.YELLOW;
        }
        if (want != state && tick - since >= hold) {
            // one level at a time on the way down
            if (want.ordinal() < state.ordinal()) want = State.values()[state.ordinal() - 1];
            LotrLog.info("events", "governor {} -> {} (avg tick {} ms, chunks {}%, tick {})", state, want, String.format("%.1f", ms),
                    Math.round(chunkFraction * 100), tick);
            state = want;
            since = tick;
            redSince = want == State.RED ? tick : -1;
        }
        return state;
    }

    /** Game tests: jump straight to a state (no hold afterwards). */
    public static void forceForTest(State s) {
        state = s;
        since = Long.MIN_VALUE / 2;
        redSince = s == State.RED ? Long.MIN_VALUE / 4 : -1;
    }

    /** Game tests: back to Green with no hold. */
    public static void reset() {
        state = State.GREEN;
        since = Long.MIN_VALUE / 2;
        redSince = -1;
    }
}
