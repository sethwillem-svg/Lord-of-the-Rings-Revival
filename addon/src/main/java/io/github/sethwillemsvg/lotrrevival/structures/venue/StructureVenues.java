package io.github.sethwillemsvg.lotrrevival.structures.venue;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.StructureStats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Venue lookups for the nemesis world events and others (specs/addon/structures.md, Event venues). In memory only, server
 * thread only; never loads or generates chunks. Also installed as the {@code core.api.StructureVenues} lookup.
 */
public final class StructureVenues {
    static final String SYS = StructureStats.SYS;

    private StructureVenues() {}

    /**
     * Venues of any of the kinds whose centre is minDist..maxDist blocks (horizontal) from {@code from}, sorted by
     * |distance - (minDist + maxDist) / 2|, then by distance. Complete venues only unless the query asks otherwise.
     * Empty list outside venue dimensions.
     */
    public static List<Venue> find(ServerLevel level, BlockPos from, int minDist, int maxDist, VenueQuery query) {
        if (!VenueIngest.indexed(level)) return List.of();
        return find(level, VenueIndex.get(level), from, minDist, maxDist, query);
    }

    /** {@link #find} on a given index (tests use their own index in any level). */
    public static List<Venue> find(ServerLevel level, VenueIndex index, BlockPos from, int minDist, int maxDist,
                                   VenueQuery query) {
        long t0 = System.nanoTime();
        double mid = (minDist + maxDist) / 2.0;
        record Hit(Venue v, double d) {}
        List<Hit> hits = new ArrayList<>();
        int[] scanned = {0};
        index.forEachNear(from.getX() - maxDist, from.getZ() - maxDist, from.getX() + maxDist, from.getZ() + maxDist, e -> {
            scanned[0]++;
            if (!query.includeIncomplete() && !e.complete()) return;
            VenueResolver.Resolved r = VenueResolver.resolve(level.registryAccess(), e.structure);
            if (r.kinds().stream().noneMatch(query.kinds()::contains)) return;
            if (!query.faction().test(r.info().faction())) return;
            Venue v = toVenue(e, r);
            double d = Math.sqrt(Math.pow(v.centre().getX() - from.getX(), 2) + Math.pow(v.centre().getZ() - from.getZ(), 2));
            if (d < minDist || d > maxDist) return;
            hits.add(new Hit(v, d));
        });
        hits.sort(Comparator.<Hit>comparingDouble(h -> Math.abs(h.d() - mid)).thenComparingDouble(Hit::d));
        List<Venue> out = hits.stream().limit(query.limit()).map(Hit::v).collect(Collectors.toList());
        long nanos = System.nanoTime() - t0;
        StructureStats.lookup(nanos);
        LotrLog.dbg(SYS, "find from={},{},{} band={}..{} kinds={} incomplete={} scanned={} -> {} ({} ms) {}", from.getX(),
                from.getY(), from.getZ(), minDist, maxDist, query.kinds(), query.includeIncomplete(), scanned[0], out.size(),
                String.format("%.3f", nanos / 1e6), out.stream().map(v -> v.structureId() + "@" + v.centre().toShortString())
                        .collect(Collectors.joining(" ")));
        return out;
    }

    /** The venue whose bounds contain pos (complete or not). */
    public static Optional<Venue> at(ServerLevel level, BlockPos pos) {
        if (!VenueIngest.indexed(level)) return Optional.empty();
        return at(level, VenueIndex.get(level), pos);
    }

    public static Optional<Venue> at(ServerLevel level, VenueIndex index, BlockPos pos) {
        List<Venue> found = new ArrayList<>();
        index.forEachNear(pos.getX(), pos.getZ(), pos.getX(), pos.getZ(), e -> {
            if (e.box.isInside(pos)) found.add(toVenue(e, VenueResolver.resolve(level.registryAccess(), e.structure)));
        });
        found.sort(Comparator.comparing((Venue v) -> !v.complete()));
        Optional<Venue> out = found.stream().findFirst();
        LotrLog.dbg(SYS, "at {} -> {}", pos.toShortString(), out.map(v -> v.structureId().toString()).orElse("none"));
        return out;
    }

    /**
     * Up to {@code count} open floor spots inside the bounds (2 air over a solid top face, not water), only in loaded
     * chunks. TODO(spec §Event venues): skip structure-void columns (needs the template's void mask).
     */
    public static List<BlockPos> openFloor(ServerLevel level, Venue venue, int count, RandomSource random) {
        BoundingBox b = venue.bounds();
        List<BlockPos> out = new ArrayList<>();
        int tries = Math.max(32, count * 16);
        for (int i = 0; i < tries && out.size() < count; i++) {
            int x = b.minX() + random.nextInt(b.getXSpan());
            int z = b.minZ() + random.nextInt(b.getZSpan());
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            for (int y = b.maxY(); y > b.minY(); y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState below = level.getBlockState(p.below());
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && below.isFaceSturdy(level, p.below(), Direction.UP) && below.getFluidState().isEmpty()) {
                    if (!out.contains(p)) out.add(p);
                    break;
                }
            }
        }
        LotrLog.dbg(SYS, "openFloor {} count={} -> {}", venue.structureId(), count, out.size());
        return out;
    }

    static Venue toVenue(VenueIndex.Entry e, VenueResolver.Resolved r) {
        BoundingBox b = e.box;
        BlockPos centre = new BlockPos((b.minX() + b.maxX()) / 2, b.minY() + r.info().groundOffset(), (b.minZ() + b.maxZ()) / 2);
        return new Venue(e.structure, r.kinds(), b, centre, r.info().faction(), r.info().residents(), e.start, e.complete(),
                r.info().build());
    }

    /** Adapter for {@code core.api.StructureVenues} (the seam the nemesis code calls). */
    public static final class CoreLookup implements io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues.Lookup {
        @Override
        public List<io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues.Venue> find(ServerLevel level, BlockPos from,
                int minDist, int maxDist, String kind, int maxChunkChecks) {
            Optional<VenueKind> k = VenueKind.byId(kind);
            if (k.isEmpty()) {
                LotrLog.warn(SYS, "venue lookup for unknown kind {}", kind);
                return List.of();
            }
            return StructureVenues.find(level, from, minDist, maxDist, VenueQuery.of(k.get())).stream()
                    .map(v -> new io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues.Venue(v.structureId(), v.centre(),
                            v.bounds(), v.faction().orElse(null), kind))
                    .collect(Collectors.toList());
        }
    }
}
