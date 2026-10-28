package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * {@code lotrrevival_banners}: the per-dimension index of protecting banners. It answers protection queries without
 * the banners' chunks being loaded. Updated on place, break, edit and base-block change.
 */
public final class BannerIndex extends SavedData {
    public static final String NAME = "lotrrevival_banners";
    private static final SavedData.Factory<BannerIndex> FACTORY = new SavedData.Factory<>(BannerIndex::new, BannerIndex::load);

    private final Map<BlockPos, BannerEntry> entries = new HashMap<>();
    /** Banner entries by the chunk of the banner block, for the range queries. */
    private final Map<Long, List<BannerEntry>> byChunk = new HashMap<>();

    public static BannerIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static BannerIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        BannerIndex index = new BannerIndex();
        for (Tag raw : tag.getList("banners", Tag.TAG_COMPOUND)) {
            BannerEntry.CODEC
                    .parse(NbtOps.INSTANCE, raw)
                    .resultOrPartial(error -> LOTRRevival.LOGGER.warn("Skipping a bad banner index entry: {}", error))
                    .ifPresent(index::insert);
        }
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BannerEntry entry : entries.values()) {
            BannerEntry.CODEC.encodeStart(NbtOps.INSTANCE, entry).result().ifPresent(list::add);
        }
        tag.put("banners", list);
        return tag;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public BannerEntry get(BlockPos pos) {
        return entries.get(pos);
    }

    /** Adds or replaces the banner at the entry's position. */
    public void put(BannerEntry entry) {
        BannerEntry old = entries.get(entry.pos());
        if (old != null && old.equals(entry)) {
            return;
        }
        remove(entry.pos());
        insert(entry);
        setDirty();
    }

    /** Removes the banner at the position; returns whether there was one. */
    public boolean remove(BlockPos pos) {
        BannerEntry old = entries.remove(pos);
        if (old == null) {
            return false;
        }
        long key = ChunkPos.asLong(old.pos());
        List<BannerEntry> bucket = byChunk.get(key);
        if (bucket != null) {
            bucket.remove(old);
            if (bucket.isEmpty()) {
                byChunk.remove(key);
            }
        }
        setDirty();
        return true;
    }

    private void insert(BannerEntry entry) {
        BlockPos pos = entry.pos().immutable();
        BannerEntry stored = new BannerEntry(pos, entry.design(), entry.range(), entry.settings());
        entries.put(pos, stored);
        byChunk.computeIfAbsent(ChunkPos.asLong(pos), k -> new ArrayList<>()).add(stored);
    }

    public List<BannerEntry> all() {
        return List.copyOf(entries.values());
    }

    /** Banners whose cube contains {@code pos}. */
    public List<BannerEntry> covering(BlockPos pos) {
        return near(pos.getX(), pos.getZ(), BannerBases.MAX_RANGE, entry -> entry.covers(pos));
    }

    /** Banners whose cube shares at least one block with the cube of the given centre and range. */
    public List<BannerEntry> overlapping(BlockPos centre, int range) {
        return near(centre.getX(), centre.getZ(), BannerBases.MAX_RANGE + range, entry -> entry.overlaps(centre, range));
    }

    /** Banners whose cube touches the box. */
    public List<BannerEntry> intersecting(BlockPos min, BlockPos max) {
        int cx = (min.getX() + max.getX()) / 2;
        int cz = (min.getZ() + max.getZ()) / 2;
        int reach = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) / 2 + 1 + BannerBases.MAX_RANGE;
        return near(cx, cz, reach, entry -> entry.pos().getX() + entry.range() >= min.getX()
                && entry.pos().getX() - entry.range() <= max.getX()
                && entry.pos().getY() + entry.range() >= min.getY()
                && entry.pos().getY() - entry.range() <= max.getY()
                && entry.pos().getZ() + entry.range() >= min.getZ()
                && entry.pos().getZ() - entry.range() <= max.getZ());
    }

    private List<BannerEntry> near(int x, int z, int reach, java.util.function.Predicate<BannerEntry> filter) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<BannerEntry> result = null;
        int minChunkX = (x - reach) >> 4;
        int maxChunkX = (x + reach) >> 4;
        int minChunkZ = (z - reach) >> 4;
        int maxChunkZ = (z + reach) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                List<BannerEntry> bucket = byChunk.get(ChunkPos.asLong(cx, cz));
                if (bucket == null) {
                    continue;
                }
                for (BannerEntry entry : bucket) {
                    if (filter.test(entry)) {
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(entry);
                    }
                }
            }
        }
        return result == null ? List.of() : result;
    }
}
