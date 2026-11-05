package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Venues (spec Venues): one lookup per roll through {@link StructureVenues} (loaded chunks only, 64 chunk checks), cached per
 * 512-block region for 5 min, venue cooldown 30 min, floor placement inside the bounds, open-ground fallback.
 */
public final class VenueFinder {
    private record CacheKey(String dim, int rx, int rz, String kind) {}

    private record Cached(List<StructureVenues.Venue> venues, long until) {}

    private static final Map<CacheKey, Cached> CACHE = new HashMap<>();
    public static int lookups, cacheHits;

    private VenueFinder() {}

    public static String key(StructureVenues.Venue v) {
        return v.structure() + "@" + v.centre().getX() + "," + v.centre().getZ();
    }

    /** First venue of the preferred kinds in the band that is off cooldown, else null (open ground). */
    public static @Nullable StructureVenues.Venue find(ServerLevel level, BlockPos from, List<String> kinds, long tick) {
        WorldEventSavedData data = WorldEventSavedData.get(level.getServer());
        int min = EventsConfig.i(EventsConfig.BAND_MIN), max = EventsConfig.i(EventsConfig.BAND_MAX);
        for (String kind : kinds) {
            CacheKey k = new CacheKey(level.dimension().location().toString(), from.getX() >> 9, from.getZ() >> 9, kind);
            Cached c = CACHE.get(k);
            List<StructureVenues.Venue> found;
            if (c != null && c.until() > tick) {
                found = c.venues();
                cacheHits++;
            } else {
                found = StructureVenues.get().find(level, from, min, max, kind, EventsConfig.i(EventsConfig.LOOKUP_BUDGET));
                CACHE.put(k, new Cached(found, tick + EventsConfig.i(EventsConfig.LOOKUP_CACHE_SECONDS) * 20L));
                lookups++;
            }
            for (StructureVenues.Venue v : found) {
                Long until = data.venueCooldownUntil.get(key(v));
                if (until != null && until > tick) {
                    LotrLog.dbg("events", "venue {} on cooldown for {} s", key(v), (until - tick) / 20);
                    continue;
                }
                LotrLog.dbg("events", "venue lookup: kind={} found {} at {} (owner {}) cached={}", kind, v.structure(), v.centre().toShortString(),
                        v.owner(), c != null && c.until() > tick);
                return v;
            }
            LotrLog.dbg("events", "venue lookup: kind={} none in {}-{} of {}", kind, min, max, from.toShortString());
        }
        return null;
    }

    public static void cooldown(ServerLevel level, StructureVenues.Venue v, long tick) {
        WorldEventSavedData data = WorldEventSavedData.get(level.getServer());
        data.venueCooldownUntil.put(key(v), tick + EventsConfig.i(EventsConfig.VENUE_COOLDOWN_MINUTES) * 1200L);
        data.venueCooldownUntil.values().removeIf(u -> u < tick);
        data.setDirty();
    }

    /** Open floor: two air blocks over a solid block, not water. Inside the venue bounds when there is one, else around the centre. */
    public static List<BlockPos> floor(ServerLevel level, BlockPos centre, @Nullable BoundingBox bounds, int want, int spread) {
        List<BlockPos> out = new ArrayList<>();
        for (int tries = 0; tries < want * 8 && out.size() < want; tries++) {
            int x, z;
            if (bounds != null) {
                x = NemesisRandom.between(bounds.minX(), bounds.maxX());
                z = NemesisRandom.between(bounds.minZ(), bounds.maxZ());
            } else {
                x = centre.getX() + NemesisRandom.between(-spread, spread);
                z = centre.getZ() + NemesisRandom.between(-spread, spread);
            }
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (bounds != null && (p.getY() < bounds.minY() || p.getY() > bounds.maxY() + 1)) continue;
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isSolid()
                    && level.getFluidState(p.below()).isEmpty() && level.getFluidState(p).isEmpty()) out.add(p);
        }
        return out;
    }

    /** Open ground: surface, not water, height varies by 3 or less over 9x9. */
    public static boolean flatEnough(ServerLevel level, BlockPos centre) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dx = -4; dx <= 4; dx += 2)
            for (int dz = -4; dz <= 4; dz += 2) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX() + dx, centre.getZ() + dz);
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre);
        return hi - lo <= 3 && level.getFluidState(top.below()).isEmpty();
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
