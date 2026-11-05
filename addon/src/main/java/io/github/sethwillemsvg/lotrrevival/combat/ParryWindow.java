package io.github.sethwillemsvg.lotrrevival.combat;

/** Pure parry timing (spec "Parry" and "Latency"); no game state, so GameTests can check it directly. */
public final class ParryWindow {
    private ParryWindow() {}

    public enum Result {
        /** No press to judge. */
        NONE,
        /** The press is in [hit - window, hit + grace]. */
        PERFECT,
        /** Blade guard only: the press was before the perfect window but its guard is still up at the hit. */
        GLANCE,
        /** The press was too early for the window (a shield may still block the vanilla way). */
        EARLY,
        /** The press came after hit + grace. */
        LATE
    }

    /**
     * @param press  the server tick of the press (ParryState.NONE for none)
     * @param hit    the hit frame's tick
     * @param window perfect window W (ticks after the press)
     * @param guard  blade guard length in ticks (0 for shields)
     * @param grace  latency grace g
     */
    public static Result classify(long press, long hit, int window, int guard, int grace) {
        if (press == ParryState.NONE) return Result.NONE;
        if (press > hit + grace) return Result.LATE;
        long sincePress = hit - press;   // negative when the press came after the hit, inside the grace
        if (sincePress <= window) return Result.PERFECT;
        if (guard > 0 && sincePress < guard) return Result.GLANCE;
        return Result.EARLY;
    }

    /** Grace ticks for a player's latency: min(ceil(ms / 50), cap). */
    public static int grace(int latencyMs, int cap) {
        if (latencyMs <= 0) return 0;
        return Math.min((latencyMs + 49) / 50, cap);
    }

    public static int grace(int latencyMs) {
        return grace(latencyMs, CombatConfig.graceCap());
    }
}
