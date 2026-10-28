package io.github.sethwillemsvg.lotrrevival.world.road;

/**
 * Deterministic per-column randomness for roads. It depends on x and z only (never the world seed), so every world
 * gets the same repairs and the same surface mix.
 */
public final class RoadHash {
    /** Salt of the repair roll. */
    public static final long REPAIR = 0x52455041495253L;

    /** Salt of the surface pick. */
    public static final long PICK = 0x5049434b535552L;

    private RoadHash() {}

    /** A value in [0, 1) that is a pure function of the column and the salt. */
    public static double unit(int x, int z, long salt) {
        long h = salt ^ (((long) x) * 0x9E3779B97F4A7C15L) ^ (((long) z) * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
