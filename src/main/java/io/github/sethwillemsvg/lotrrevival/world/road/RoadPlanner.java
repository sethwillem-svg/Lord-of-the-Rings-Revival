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

        /** The water over a column: its top water block and the top solid block under it, or null when dry. */
        @Nullable
        default Water water(int x, int z) {
            return null;
        }
    }

    /**
     * Water over a column.
     *
     * @param surface y of the top water block
     * @param floor y of the top solid block under the water
     */
    public record Water(int surface, int floor) {
        /** Depth {@code d}: water surface y minus the top solid block under it. */
        public int depth() {
            return surface - floor;
        }
    }

    /** Depth beyond which the deck stops rising (deck height is at most 5 blocks). */
    public static final int MAX_DEPTH = 10;

    /** Arc length between piers, in blocks. */
    public static final int PIER_SPACING = 8;

    /**
     * One bridge column to build.
     *
     * @param surface y of the top water block
     * @param floor y of the top solid block under the water
     * @param deckY y of the deck block (a bottom slab when {@code halfStep})
     * @param rail whether an outermost column carries a fence on the deck
     * @param pier whether a pier runs from the deck down to the bed
     */
    public record Bridge(
            int x,
            int z,
            int surface,
            int floor,
            int deckY,
            boolean halfStep,
            boolean rail,
            boolean pier,
            BridgeStyle style) {}

    /**
     * Deck y for water of depth {@code d}: {@code min(d, 10) / 2} blocks above the water surface, the remaining half
     * as a slab. A whole height h puts the deck block at {@code surface + h}; a height of h + 0.5 puts the slab at
     * {@code surface + h + 1}, so its top is h + 0.5 above the water's top face.
     */
    public static int deckY(int surface, int depth) {
        return surface + (Math.min(depth, MAX_DEPTH) + 1) / 2;
    }

    /** Whether the deck for this depth ends in a half step. */
    public static boolean deckHasHalf(int depth) {
        return Math.min(depth, MAX_DEPTH) % 2 == 1;
    }

    /**
     * Plans the bridge columns in a rectangle (inclusive): road columns over water. Structure boxes and the repair
     * roll follow the land rules. Rails sit where the distance from the centre is above {@code width - 1}; piers
     * where, on a rail column, floor(arc length) is a multiple of 8.
     */
    public static List<Bridge> planBridges(
            RoadNetwork network, Terrain terrain, int minX, int minZ, int maxX, int maxZ, List<BoundingBox> skip) {
        List<Bridge> out = new ArrayList<>();
        if (network.isEmpty()) {
            return out;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (network.edgeDistance(x, z, 0) > 0) {
                    continue;
                }
                Water water = terrain.water(x, z);
                if (water == null || water.depth() < 1) {
                    continue;
                }
                ResourceKey<Biome> biome = terrain.biome(x, z, water.floor());
                if (RoadHash.unit(x, z, RoadHash.REPAIR) >= RoadStyles.forBiome(biome).repair()) {
                    continue;
                }
                int deck = deckY(water.surface(), water.depth());
                if (inBox(skip, x, z, water.floor(), deck + 1)) {
                    continue;
                }
                RoadNetwork.Hit hit = network.nearest(x, z, network.maxHalfWidth() + 2.0);
                boolean rail = hit != null && hit.distance() > hit.halfWidth() - 1;
                boolean pier = rail && (long) Math.floor(hit.arcLength()) % PIER_SPACING == 0;
                out.add(new Bridge(
                        x,
                        z,
                        water.surface(),
                        water.floor(),
                        deck,
                        deckHasHalf(water.depth()),
                        rail,
                        pier,
                        RoadStyles.bridgeFor(biome)));
            }
        }
        return out;
    }

    private static boolean inBox(List<BoundingBox> skip, int x, int z, int minY, int maxY) {
        if (skip.isEmpty()) {
            return false;
        }
        BoundingBox column = new BoundingBox(x, minY, z, x, maxY, z);
        for (BoundingBox box : skip) {
            if (box.intersects(column)) {
                return true;
            }
        }
        return false;
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
