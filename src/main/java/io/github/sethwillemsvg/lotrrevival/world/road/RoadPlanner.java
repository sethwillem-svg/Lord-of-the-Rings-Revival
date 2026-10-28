package io.github.sethwillemsvg.lotrrevival.world.road;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Decides which columns of a rectangle get road and what goes there, without touching a level: the terrain comes in
 * through {@link Terrain}. Rules from {@code specs/world/waypoints-travel.md} (Roads): half-width columns, repair
 * chance from a deterministic hash, half steps from the neighbour mean, structure boxes skipped, water left alone.
 */
public final class RoadPlanner {
    /** Marks a column with no ground to build on (water, void). */
    public static final int NO_GROUND = Integer.MIN_VALUE;

    /** A column gets a half step when the mean top of its road neighbours is at least this much above its own. */
    public static final double STEP_THRESHOLD = 0.5;

    private RoadPlanner() {}

    /** The natural terrain under a road, before any road block is placed. */
    public interface Terrain {
        /** Y of the top solid block of the column, or {@link #NO_GROUND} when it is a water column or empty. */
        int top(int x, int z);

        /** Biome of the column at the given top block. */
        @Nullable
        ResourceKey<Biome> biome(int x, int z, int top);
    }

    /**
     * One column to build.
     *
     * @param top y of the top solid block; the surface goes here and the base fills the 3 below
     * @param step whether a half step goes on top
     */
    public record Column(int x, int z, int top, RoadStyle style, RoadStyle.Surface surface, boolean step) {}

    /**
     * Plans the columns {@code minX..maxX} by {@code minZ..maxZ} (inclusive).
     *
     * @param skip boxes of structure pieces: a column whose build range touches one is left alone
     */
    public static List<Column> plan(
            RoadNetwork network,
            Terrain terrain,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            List<BoundingBox> skip) {
        List<Column> out = new ArrayList<>();
        if (network.isEmpty()) {
            return out;
        }
        // Grid with a 1 column border so the 8 neighbours of every column are known.
        int width = maxX - minX + 3;
        int depth = maxZ - minZ + 3;
        int[] tops = new int[width * depth];
        for (int gx = 0; gx < width; gx++) {
            for (int gz = 0; gz < depth; gz++) {
                int x = minX - 1 + gx;
                int z = minZ - 1 + gz;
                int top = NO_GROUND;
                if (network.edgeDistance(x, z, 0) <= 0) {
                    top = terrain.top(x, z);
                }
                tops[gx * depth + gz] = top;
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int gx = x - minX + 1;
                int gz = z - minZ + 1;
                int top = tops[gx * depth + gz];
                if (top == NO_GROUND || inStructure(skip, x, z, top)) {
                    continue;
                }
                RoadStyles.Choice choice = RoadStyles.forBiome(terrain.biome(x, z, top));
                if (RoadHash.unit(x, z, RoadHash.REPAIR) >= choice.repair()) {
                    continue;
                }
                RoadStyle.Surface surface = choice.style().pick(RoadHash.unit(x, z, RoadHash.PICK));

                double sum = 0;
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int n = tops[(gx + dx) * depth + gz + dz];
                        if (n != NO_GROUND) {
                            sum += n;
                            count++;
                        }
                    }
                }
                boolean step = count > 0 && sum / count - top >= STEP_THRESHOLD;
                out.add(new Column(x, z, top, choice.style(), surface, step));
            }
        }
        return out;
    }

    private static boolean inStructure(List<BoundingBox> skip, int x, int z, int top) {
        if (skip.isEmpty()) {
            return false;
        }
        BoundingBox column = new BoundingBox(x, top - 3, z, x, top + 1, z);
        for (BoundingBox box : skip) {
            if (box.intersects(column)) {
                return true;
            }
        }
        return false;
    }
}
