package io.github.sethwillemsvg.lotrrevival.structures.venue;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.StructureStats;
import io.github.sethwillemsvg.lotrrevival.structures.StructuresConfig;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Feeds the venue index from chunks as they load at full status (specs/addon/structures.md, Index). */
public final class VenueIngest {
    static final String SYS = StructureStats.SYS;

    private VenueIngest() {}

    public static boolean indexed(ServerLevel level) {
        return StructuresConfig.venueDimensions().contains(level.dimension().location().toString());
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!indexed(level)) return;
        try {
            ingest(level, chunk);
            markChunk(level, chunk.getPos().x, chunk.getPos().z, "loaded");
        } catch (RuntimeException e) {
            LotrLog.error(SYS, "venue ingest failed at chunk " + chunk.getPos(), e);
        }
    }

    /** Adds every start owned by this chunk that is in a venue tag. Returns how many were new. */
    public static int ingest(ServerLevel level, ChunkAccess chunk) {
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        VenueIndex index = VenueIndex.get(level);
        int added = 0;
        for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
            StructureStart start = e.getValue();
            if (start == null || !start.isValid() || !start.getChunkPos().equals(chunk.getPos())) continue;
            ResourceLocation id = reg.getKey(e.getKey());
            if (id == null) continue;
            Holder<Structure> holder = reg.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, id));
            if (VenueResolver.kinds(holder).isEmpty()) continue;
            if (add(level, index, id, start.getChunkPos(), start.getBoundingBox())) added++;
        }
        return added;
    }

    /** The ingest path shared with tests: adds the entry once, marks loaded chunks, queues probes for the rest. */
    public static boolean add(ServerLevel level, VenueIndex index, ResourceLocation id, ChunkPos startChunk,
                              net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        int before = index.size();
        VenueIndex.Entry entry = index.add(id, startChunk, box);
        if (index.size() == before) return false;
        StructureStats.count("venue.added");
        int[] loaded = {0};
        entry.forEachChunk(c -> {
            if (level.hasChunk(c.x, c.z)) {
                entry.mark(c.x, c.z);
                loaded[0]++;
            } else {
                ChunkProbe.enqueue(level, c);
            }
        });
        if (entry.complete()) StructureStats.count("venue.completed");
        LotrLog.dbg(SYS, "venue + {} start={},{} bounds={},{},{}..{},{},{} bits {}/{} level={}", id, startChunk.x, startChunk.z,
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), entry.bitsSet(), entry.bitCount(),
                level.dimension().location());
        return true;
    }

    public static void markChunk(ServerLevel level, int cx, int cz, String how) {
        for (VenueIndex.Entry e : VenueIndex.get(level).markChunk(cx, cz)) {
            StructureStats.count("venue.completed");
            LotrLog.dbg(SYS, "venue complete {} start={},{} ({} chunk {},{})", e.structure, e.start.x, e.start.z, how, cx, cz);
        }
    }

    /** {@code /lotr_revival venues reindex}: re-ingests loaded chunks near players (never loads one). */
    public static int reindex(ServerLevel level) {
        int added = 0, scanned = 0;
        int view = level.getServer().getPlayerList().getViewDistance();
        for (var player : level.players()) {
            ChunkPos c = player.chunkPosition();
            for (int dx = -view; dx <= view; dx++) {
                for (int dz = -view; dz <= view; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(c.x + dx, c.z + dz);
                    if (chunk == null) continue;
                    scanned++;
                    added += ingest(level, chunk);
                    markChunk(level, chunk.getPos().x, chunk.getPos().z, "reindex");
                }
            }
        }
        LotrLog.info(SYS, "venues reindex {}: scanned {} loaded chunks, {} new entries", level.dimension().location(), scanned, added);
        return added;
    }
}
