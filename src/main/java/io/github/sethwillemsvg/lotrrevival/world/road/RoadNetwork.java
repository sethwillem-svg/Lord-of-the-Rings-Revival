package io.github.sethwillemsvg.lotrrevival.world.road;

import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthMap;
import io.github.sethwillemsvg.lotrrevival.world.WorldScale;
import io.github.sethwillemsvg.lotrrevival.world.WorldScales;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable index of every road's sampled centre line. A grid of 64-block cells maps to the segments that touch
 * them, so a distance query costs O(segments in the cells around the point). Thread safe (read only).
 */
public final class RoadNetwork {
    public static final RoadNetwork EMPTY = new RoadNetwork(List.of());

    /** Grid cell size in blocks. */
    public static final int CELL_SIZE = 64;

    /**
     * Each segment is listed in every cell within this many blocks of it, so queries with a radius up to this
     * (the usual half-width 4 plus falloff 32) read one cell. Larger radii scan the neighbouring cells too.
     */
    public static final int CELL_MARGIN = 40;

    /** One road ready for indexing. */
    public record Entry(ResourceLocation id, int halfWidth, RoadSpline.Samples samples) {}

    /**
     * Nearest road centre to a point.
     *
     * @param road the road's registry id
     * @param distance block distance to the centre line
     * @param arcLength arc length along the road at the nearest point
     * @param halfWidth that road's half-width
     */
    public record Hit(ResourceLocation road, double distance, double arcLength, int halfWidth) {
        /** Whether the point is a road column (within the half-width of the centre line). */
        public boolean onRoad() {
            return distance <= halfWidth;
        }
    }

    private final List<Entry> roads;
    private final Long2ObjectOpenHashMap<long[]> grid;
    private final int maxHalfWidth;

    private RoadNetwork(List<Entry> roads) {
        this.roads = List.copyOf(roads);
        int widest = 0;
        Long2ObjectOpenHashMap<LongArrayList> cells = new Long2ObjectOpenHashMap<>();
        for (int r = 0; r < this.roads.size(); r++) {
            Entry road = this.roads.get(r);
            widest = Math.max(widest, road.halfWidth());
            RoadSpline.Samples s = road.samples();
            for (int i = 0; i + 1 < s.size(); i++) {
                int minCx = cell(Math.min(s.x()[i], s.x()[i + 1]) - CELL_MARGIN);
                int maxCx = cell(Math.max(s.x()[i], s.x()[i + 1]) + CELL_MARGIN);
                int minCz = cell(Math.min(s.z()[i], s.z()[i + 1]) - CELL_MARGIN);
                int maxCz = cell(Math.max(s.z()[i], s.z()[i + 1]) + CELL_MARGIN);
                long ref = ((long) r << 32) | i;
                for (int cx = minCx; cx <= maxCx; cx++) {
                    for (int cz = minCz; cz <= maxCz; cz++) {
                        cells.computeIfAbsent(key(cx, cz), k -> new LongArrayList()).add(ref);
                    }
                }
            }
        }
        this.grid = new Long2ObjectOpenHashMap<>(cells.size());
        cells.long2ObjectEntrySet().forEach(e -> grid.put(e.getLongKey(), e.getValue().toLongArray()));
        this.maxHalfWidth = widest;
    }

    /** Builds an index from already sampled roads (block coordinates). */
    public static RoadNetwork of(List<Entry> roads) {
        return roads.isEmpty() ? EMPTY : new RoadNetwork(roads);
    }

    /** Builds the index at the running world's scale. */
    public static RoadNetwork fromRoads(Map<ResourceLocation, Road> roads) {
        return fromRoads(roads, WorldScales.current());
    }

    /**
     * Builds the index from registry roads: control points are heightmap pixels, converted to blocks at
     * {@code scale} here. Widths stay in blocks, so roads are equally wide at every scale.
     */
    public static RoadNetwork fromRoads(Map<ResourceLocation, Road> roads, WorldScale scale) {
        List<Entry> entries = new ArrayList<>();
        roads.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            List<Road.Point> points = e.getValue().points();
            if (points.size() < 2) {
                return;
            }
            double[] xs = new double[points.size()];
            double[] zs = new double[points.size()];
            for (int i = 0; i < xs.length; i++) {
                xs[i] = MiddleEarthMap.blockX(points.get(i).u(), scale);
                zs[i] = MiddleEarthMap.blockZ(points.get(i).v(), scale);
            }
            try {
                entries.add(new Entry(
                        e.getKey(), e.getValue().width(), RoadSpline.sample(e.getKey().toString(), xs, zs)));
            } catch (IllegalArgumentException ignored) {
                // fewer than 2 distinct points: nothing to index
            }
        });
        return of(entries);
    }

    public boolean isEmpty() {
        return roads.isEmpty();
    }

    public List<Entry> roads() {
        return roads;
    }

    /** Widest half-width of any road, 0 when empty. */
    public int maxHalfWidth() {
        return maxHalfWidth;
    }

    /** The road whose centre line is nearest to (x, z), or null when none lies within {@code maxDistance}. */
    @Nullable
    public Hit nearest(double x, double z, double maxDistance) {
        Best best = search(x, z, maxDistance, false);
        return best == null ? null : best.hit(roads);
    }

    /**
     * Smallest {@code distance to centre - half-width} over all roads within reach (negative inside a road), or
     * positive infinity when no road lies within {@code maxHalfWidth + falloff}.
     */
    public double edgeDistance(double x, double z, double falloff) {
        if (roads.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        Best best = search(x, z, maxHalfWidth + falloff, true);
        return best == null ? Double.POSITIVE_INFINITY : best.score;
    }

    /**
     * Terrain-detail mask: 0 within a road's half-width of its centre line, smoothstep up to 1 at half-width plus
     * {@code falloff}, 1 away from every road.
     */
    public double mask(double x, double z, double falloff) {
        double edge = edgeDistance(x, z, falloff);
        if (edge <= 0) {
            return 0;
        }
        if (edge >= falloff) {
            return 1;
        }
        double t = edge / falloff;
        return t * t * (3 - 2 * t);
    }

    private static int cell(double blocks) {
        return (int) Math.floor(blocks / CELL_SIZE);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private record Best(int road, int segment, double t, double distance, double score) {
        Hit hit(List<Entry> roads) {
            Entry entry = roads.get(road);
            double[] arc = entry.samples().arc();
            double length = arc[segment] + t * (arc[segment + 1] - arc[segment]);
            return new Hit(entry.id(), distance, length, entry.halfWidth());
        }
    }

    /** Scans the cells within {@code radius} of the point; {@code byEdge} scores distance minus half-width. */
    @Nullable
    private Best search(double x, double z, double radius, boolean byEdge) {
        if (roads.isEmpty()) {
            return null;
        }
        // Segments are listed in the cells within CELL_MARGIN of them, so a small radius needs only the point's cell.
        double reach = radius <= CELL_MARGIN ? 0 : radius;
        int minCx = cell(x - reach);
        int maxCx = cell(x + reach);
        int minCz = cell(z - reach);
        int maxCz = cell(z + reach);
        double bestScore = Double.POSITIVE_INFINITY;
        int bestRoad = -1;
        int bestSeg = 0;
        double bestT = 0;
        double bestDist = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long[] refs = grid.get(key(cx, cz));
                if (refs == null) {
                    continue;
                }
                for (long ref : refs) {
                    int r = (int) (ref >>> 32);
                    int i = (int) ref;
                    Entry entry = roads.get(r);
                    double[] xs = entry.samples().x();
                    double[] zs = entry.samples().z();
                    double ax = xs[i];
                    double az = zs[i];
                    double dx = xs[i + 1] - ax;
                    double dz = zs[i + 1] - az;
                    double len2 = dx * dx + dz * dz;
                    double t = len2 == 0 ? 0 : ((x - ax) * dx + (z - az) * dz) / len2;
                    t = t < 0 ? 0 : Math.min(t, 1);
                    double px = ax + dx * t - x;
                    double pz = az + dz * t - z;
                    double d = Math.sqrt(px * px + pz * pz);
                    double score = byEdge ? d - entry.halfWidth() : d;
                    if (score < bestScore) {
                        bestScore = score;
                        bestRoad = r;
                        bestSeg = i;
                        bestT = t;
                        bestDist = d;
                    }
                }
            }
        }
        if (bestRoad < 0 || (!byEdge && bestDist > radius)) {
            return null;
        }
        return new Best(bestRoad, bestSeg, bestT, bestDist, bestScore);
    }
}
