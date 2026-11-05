package io.github.sethwillemsvg.lotrrevival.structures.venue;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.StructureStats;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Venue index of one level ({@code lotr_revival_venues}): structure starts in venue tags from chunks that have generated,
 * in cells of 512 x 512 blocks. Each entry keeps one bit per chunk its bounds cover; it is complete when all are set.
 * Server thread only.
 */
public final class VenueIndex extends SavedData {
    public static final String NAME = "lotr_revival_venues";
    public static final int CELL_SHIFT = 9;   // 512 blocks
    static final String SYS = StructureStats.SYS;

    public static final class Entry {
        public final ResourceLocation structure;
        public final ChunkPos start;
        public final BoundingBox box;
        final BitSet bits;
        final int minCx, minCz, width, count;

        Entry(ResourceLocation structure, ChunkPos start, BoundingBox box, BitSet bits) {
            this.structure = structure;
            this.start = start;
            this.box = box;
            this.minCx = box.minX() >> 4;
            this.minCz = box.minZ() >> 4;
            this.width = (box.maxX() >> 4) - minCx + 1;
            this.count = width * ((box.maxZ() >> 4) - minCz + 1);
            this.bits = bits;
        }

        public boolean covers(int cx, int cz) {
            return cx >= minCx && cx < minCx + width && cz >= minCz && cz <= (box.maxZ() >> 4);
        }

        int bit(int cx, int cz) {
            return (cx - minCx) + (cz - minCz) * width;
        }

        /** Sets the chunk's bit; true when this made the entry complete. */
        boolean mark(int cx, int cz) {
            if (!covers(cx, cz)) return false;
            boolean was = complete();
            bits.set(bit(cx, cz));
            return !was && complete();
        }

        public boolean complete() {
            return bits.cardinality() >= count;
        }

        public int bitsSet() {
            return bits.cardinality();
        }

        public int bitCount() {
            return count;
        }

        public void forEachChunk(Consumer<ChunkPos> c) {
            for (int i = 0; i < count; i++) c.accept(new ChunkPos(minCx + i % width, minCz + i / width));
        }

        String key() {
            return structure + "@" + start.x + "," + start.z;
        }
    }

    private final Map<Long, List<Entry>> cells = new HashMap<>();
    private final Map<String, Entry> byKey = new HashMap<>();

    public VenueIndex() {}

    public static SavedData.Factory<VenueIndex> factory() {
        return new SavedData.Factory<>(VenueIndex::new, VenueIndex::load);
    }

    public static VenueIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static long cell(int blockX, int blockZ) {
        return ChunkPos.asLong(blockX >> CELL_SHIFT, blockZ >> CELL_SHIFT);
    }

    /** Adds a start once. Returns the entry (new or existing). */
    public Entry add(ResourceLocation structure, ChunkPos start, BoundingBox box) {
        Entry e = new Entry(structure, start, box, new BitSet());
        Entry old = byKey.putIfAbsent(e.key(), e);
        if (old != null) return old;
        cells.computeIfAbsent(cell(start.getMinBlockX(), start.getMinBlockZ()), k -> new ArrayList<>()).add(e);
        setDirty();
        return e;
    }

    /** Marks a chunk as generated in every entry covering it; returns the entries that became complete. */
    public List<Entry> markChunk(int cx, int cz) {
        List<Entry> done = new ArrayList<>();
        int bx = cx << 4, bz = cz << 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<Entry> list = cells.get(ChunkPos.asLong((bx >> CELL_SHIFT) + dx, (bz >> CELL_SHIFT) + dz));
                if (list == null) continue;
                for (Entry e : list) {
                    if (!e.covers(cx, cz)) continue;
                    int before = e.bitsSet();
                    if (e.mark(cx, cz)) done.add(e);
                    if (e.bitsSet() != before) setDirty();
                }
            }
        }
        return done;
    }

    /** Entries whose start cell overlaps the square [minX..maxX] x [minZ..maxZ] (plus one cell of margin). */
    public void forEachNear(int minX, int minZ, int maxX, int maxZ, Consumer<Entry> c) {
        for (int x = (minX >> CELL_SHIFT) - 1; x <= (maxX >> CELL_SHIFT) + 1; x++) {
            for (int z = (minZ >> CELL_SHIFT) - 1; z <= (maxZ >> CELL_SHIFT) + 1; z++) {
                List<Entry> list = cells.get(ChunkPos.asLong(x, z));
                if (list != null) list.forEach(c);
            }
        }
    }

    public int size() {
        return byKey.size();
    }

    public int completeCount() {
        return (int) byKey.values().stream().filter(Entry::complete).count();
    }

    public void clear() {
        cells.clear();
        byKey.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : byKey.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("s", e.structure.toString());
            t.putInt("cx", e.start.x);
            t.putInt("cz", e.start.z);
            t.putIntArray("box", new int[]{e.box.minX(), e.box.minY(), e.box.minZ(), e.box.maxX(), e.box.maxY(), e.box.maxZ()});
            t.put("bits", new LongArrayTag(e.bits.toLongArray()));
            list.add(t);
        }
        tag.put("entries", list);
        LotrLog.info(SYS, "venues saved: {} entries ({} complete)", size(), completeCount());
        return tag;
    }

    public static VenueIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        VenueIndex idx = new VenueIndex();
        int dropped = 0;
        var lookup = registries.lookup(Registries.STRUCTURE);
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            ResourceLocation id = ResourceLocation.tryParse(t.getString("s"));
            int[] b = t.getIntArray("box");
            if (id == null || b.length != 6) {
                dropped++;
                continue;
            }
            if (lookup.isPresent() && lookup.get().get(ResourceKey.create(Registries.STRUCTURE, id)).isEmpty()) {
                dropped++;   // withdrawn build or a removed datapack structure
                continue;
            }
            ChunkPos start = new ChunkPos(t.getInt("cx"), t.getInt("cz"));
            Entry e = new Entry(id, start, new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]),
                    BitSet.valueOf(t.getLongArray("bits")));
            idx.byKey.put(e.key(), e);
            idx.cells.computeIfAbsent(cell(start.getMinBlockX(), start.getMinBlockZ()), k -> new ArrayList<>()).add(e);
        }
        if (dropped > 0) {
            LotrLog.warn(SYS, "venues: dropped {} entries of unknown structures at load", dropped);
            StructureStats.add("venue.dropped", dropped);
            idx.setDirty();
        }
        LotrLog.info(SYS, "venues loaded: {} entries ({} complete)", idx.size(), idx.completeCount());
        return idx;
    }
}
