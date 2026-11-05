package io.github.sethwillemsvg.lotrrevival.structures.venue;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.StructureStats;
import io.github.sethwillemsvg.lotrrevival.structures.StructuresConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Fills venue bits for covered chunks that are not loaded by reading each chunk's saved status off-thread (never loads,
 * generates or ticks a chunk). Budget: {@code venues.probe_budget} reads per second.
 */
public final class ChunkProbe {
    static final String SYS = StructureStats.SYS;
    static final int QUEUE_MAX = 4096;

    private static final class Queue {
        final ArrayDeque<Long> pending = new ArrayDeque<>();
        final Set<Long> queued = new HashSet<>();
        double tokens;
        boolean warnedFull;
    }

    private static final Map<ServerLevel, Queue> QUEUES = new WeakHashMap<>();

    private ChunkProbe() {}

    public static void enqueue(ServerLevel level, ChunkPos pos) {
        Queue q = QUEUES.computeIfAbsent(level, l -> new Queue());
        long key = pos.toLong();
        if (q.queued.contains(key)) return;
        if (q.pending.size() >= QUEUE_MAX) {
            if (!q.warnedFull) {
                q.warnedFull = true;
                LotrLog.warn(SYS, "venue probe queue full ({}) in {}: probes delayed", QUEUE_MAX, level.dimension().location());
            }
            return;
        }
        q.pending.add(key);
        q.queued.add(key);
        StructureStats.count("probe.queued");
    }

    /** Called every level tick. */
    public static void tick(ServerLevel level) {
        Queue q = QUEUES.get(level);
        if (q == null || q.pending.isEmpty()) return;
        q.tokens = Math.min(q.tokens + StructuresConfig.get(StructuresConfig.PROBE_BUDGET, 64) / 20.0, 64);
        while (q.tokens >= 1 && !q.pending.isEmpty()) {
            q.tokens -= 1;
            long key = q.pending.poll();
            q.queued.remove(key);
            ChunkPos pos = new ChunkPos(key);
            if (level.hasChunk(pos.x, pos.z)) {          // loaded meanwhile: ChunkEvent.Load marked it
                VenueIngest.markChunk(level, pos.x, pos.z, "loaded");
                continue;
            }
            // TODO(verify): ChunkMap#read is the region-file read used by chunk loading; it does not load the chunk.
            level.getChunkSource().chunkMap.read(pos).thenAcceptAsync((Optional<CompoundTag> tag) -> {
                StructureStats.count("probe.done");
                if (tag.isPresent() && isFull(tag.get())) VenueIngest.markChunk(level, pos.x, pos.z, "probe");
            }, level.getServer()).exceptionally(t -> {
                LotrLog.error(SYS, "venue probe of " + pos + " failed", t);
                return null;
            });
        }
        if (q.pending.isEmpty()) q.warnedFull = false;
    }

    static boolean isFull(CompoundTag chunk) {
        String status = chunk.getString("Status");
        return status.equals("minecraft:full") || status.equals("full");
    }

    public static int queued(ServerLevel level) {
        Queue q = QUEUES.get(level);
        return q == null ? 0 : q.pending.size();
    }
}
