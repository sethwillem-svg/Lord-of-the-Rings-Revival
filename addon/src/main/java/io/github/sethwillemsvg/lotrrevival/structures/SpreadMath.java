package io.github.sethwillemsvg.lotrrevival.structures;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * The random-spread grid maths, the same as vanilla's linear random spread so locate and existing tooling behave alike,
 * but with spacing, separation and salt supplied per call (they come from the world lock).
 */
public final class SpreadMath {
    private SpreadMath() {}

    /** The one candidate chunk of the grid cell holding chunk (x, z). */
    public static ChunkPos candidate(long seed, int x, int z, int spacing, int separation, int salt) {
        int sep = Math.min(separation, spacing - 1);
        int cx = Math.floorDiv(x, spacing);
        int cz = Math.floorDiv(z, spacing);
        WorldgenRandom r = new WorldgenRandom(new LegacyRandomSource(0L));
        r.setLargeFeatureWithSalt(seed, cx, cz, salt);
        int span = spacing - sep;
        int ox = r.nextInt(span);
        int oz = r.nextInt(span);
        return new ChunkPos(cx * spacing + ox, cz * spacing + oz);
    }

    public static boolean isCandidate(long seed, int x, int z, int spacing, int separation, int salt) {
        ChunkPos p = candidate(seed, x, z, spacing, separation, salt);
        return p.x == x && p.z == z;
    }

    /** Whether any candidate of this grid lies within {@code range} chunks (Chebyshev) of (x, z). */
    public static boolean candidateWithin(long seed, int x, int z, int range, int spacing, int separation, int salt) {
        if (range <= 0) return false;
        int c0x = Math.floorDiv(x - range, spacing), c1x = Math.floorDiv(x + range, spacing);
        int c0z = Math.floorDiv(z - range, spacing), c1z = Math.floorDiv(z + range, spacing);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                ChunkPos p = candidate(seed, cx * spacing, cz * spacing, spacing, separation, salt);
                if (Math.abs(p.x - x) <= range && Math.abs(p.z - z) <= range) return true;
            }
        }
        return false;
    }
}
