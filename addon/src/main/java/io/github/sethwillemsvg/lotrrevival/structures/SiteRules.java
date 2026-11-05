package io.github.sethwillemsvg.lotrrevival.structures;

/**
 * Site checks on base heights (specs/addon/structures.md, Placement). Pure functions over sampled columns, so they are
 * tested on synthetic data; BuildStructure samples the columns from the chunk generator without generating chunks.
 */
public final class SiteRules {
    private SiteRules() {}

    /** One sampled column: surface (WORLD_SURFACE_WG) and floor (OCEAN_FLOOR_WG) heights. Water when surface > floor. */
    public record Column(int surface, int floor) {
        public boolean water() {
            return surface > floor;
        }

        public int waterDepth() {
            return Math.max(0, surface - floor);
        }
    }

    public record Verdict(boolean ok, String reason, int slope) {
        static Verdict pass(int slope) {
            return new Verdict(true, "ok", slope);
        }

        static Verdict fail(String reason, int slope) {
            return new Verdict(false, reason, slope);
        }
    }

    /** Height range over the sampled floor heights. */
    public static int slope(Column... cols) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (Column c : cols) {
            lo = Math.min(lo, c.floor());
            hi = Math.max(hi, c.floor());
        }
        return cols.length == 0 ? 0 : hi - lo;
    }

    /** {@code site: land}: slope at most maxSlope, and no sampled column under water. */
    public static Verdict land(int maxSlope, Column... cols) {
        int s = slope(cols);
        for (Column c : cols) {
            if (c.water()) return Verdict.fail("water", s);
        }
        if (s > maxSlope) return Verdict.fail("slope " + s + "/" + maxSlope, s);
        return Verdict.pass(s);
    }

    /**
     * {@code site: river_crossing}: centre column is water at most 3 deep, both ends dry and at most 3 apart in height.
     * Returns the index (0-3) of the first rotation that fits, or -1. {@code ends[r]} holds the two end columns of
     * rotation r. TODO(spec §Placement, ticket 11): wire into BuildStructure and pick the jigsaw rotation from it.
     */
    public static int riverCrossing(Column centre, Column[][] ends) {
        if (!centre.water() || centre.waterDepth() > 3) return -1;
        for (int r = 0; r < ends.length; r++) {
            Column a = ends[r][0], b = ends[r][1];
            if (!a.water() && !b.water() && Math.abs(a.surface() - b.surface()) <= 3) return r;
        }
        return -1;
    }
}
