package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The one random source of the nemesis and world-event code, so game tests can seed it ({@link #forTest}) or force the next
 * results ({@link #force}). Forced values are consumed in order by {@link #roll()} and {@link #nextInt(int)} (as a fraction of
 * the bound). Server thread only.
 */
public final class NemesisRandom {
    private static Random random = new Random();
    private static final Deque<Double> FORCED = new ArrayDeque<>();

    private NemesisRandom() {}

    /** Uniform in [0, 1). */
    public static double roll() {
        Double f = FORCED.poll();
        if (f != null) {
            LotrLog.dbg("nemesis", "forced roll {}", f);
            return f;
        }
        return random.nextDouble();
    }

    /** Uniform in [0, bound). */
    public static int nextInt(int bound) {
        if (bound <= 1) return 0;
        Double f = FORCED.poll();
        if (f != null) return Math.min(bound - 1, (int) Math.floor(f * bound));
        return random.nextInt(bound);
    }

    /** Uniform in [min, max] inclusive. */
    public static int between(int min, int max) {
        return max <= min ? min : min + nextInt(max - min + 1);
    }

    public static <T> T pick(List<T> list) {
        return list.get(nextInt(list.size()));
    }

    public static boolean chance(double p) {
        return roll() < p;
    }

    public static void forTest(long seed) {
        random = new Random(seed);
        FORCED.clear();
    }

    public static void force(double... rolls) {
        for (double r : rolls) FORCED.add(r);
    }

    public static void reset() {
        random = new Random();
        FORCED.clear();
    }
}
