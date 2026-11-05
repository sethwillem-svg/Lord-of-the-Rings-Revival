package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Our ticket controller {@code lotr_revival:world_events}: forced ticking chunks owned by the event id, at most 9 per event (4 in
 * Yellow) and 48 in total. Leftover tickets are dropped on server start by the validation callback; events never resume.
 */
public final class EventChunkTickets {
    public static final TicketController CONTROLLER = new TicketController(LotrRevival.id("world_events"), (level, helper) -> {
        int n = helper.getEntityTickets().size() + helper.getBlockTickets().size();
        for (UUID owner : new ArrayList<>(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
        for (BlockPos owner : new ArrayList<>(helper.getBlockTickets().keySet())) helper.removeAllTickets(owner);
        LotrLog.info("events", "ticket validation in {}: dropped {} leftover ticket owner(s)", level.dimension().location(), n);
    });

    private record Forced(ServerLevel level, List<ChunkPos> chunks) {}

    private static final Map<UUID, Forced> FORCED = new HashMap<>();

    private EventChunkTickets() {}

    public static void register(RegisterTicketControllersEvent e) {
        e.register(CONTROLLER);
    }

    public static int used() {
        int n = 0;
        for (Forced f : FORCED.values()) n += f.chunks().size();
        return n;
    }

    public static int forcedFor(UUID event) {
        Forced f = FORCED.get(event);
        return f == null ? 0 : f.chunks().size();
    }

    public static double budgetFraction() {
        int budget = EventsConfig.i(EventsConfig.CHUNK_BUDGET);
        return budget <= 0 ? 1.0 : (double) used() / budget;
    }

    public static int perEventLimit() {
        return LoadGovernor.state() == LoadGovernor.State.GREEN ? EventsConfig.i(EventsConfig.MAX_CHUNKS_PER_EVENT)
                : Math.min(4, EventsConfig.i(EventsConfig.MAX_CHUNKS_PER_EVENT));
    }

    /** Forces up to the per-event limit around the centre (3x3, nearest first), never past the total budget. */
    public static int forceAround(ServerLevel level, UUID event, BlockPos centre) {
        int limit = perEventLimit(), budget = EventsConfig.i(EventsConfig.CHUNK_BUDGET);
        ChunkPos c = new ChunkPos(centre);
        List<ChunkPos> want = new ArrayList<>();
        want.add(c);
        int[][] ring = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] o : ring) want.add(new ChunkPos(c.x + o[0], c.z + o[1]));
        List<ChunkPos> got = new ArrayList<>();
        for (ChunkPos p : want) {
            if (got.size() >= limit) break;
            if (used() + got.size() >= budget) {
                LotrLog.warn("events", "chunk budget {} reached: event {} gets {} chunk(s)", budget, LotrLog.id(event), got.size());
                break;
            }
            if (CONTROLLER.forceChunk(level, event, p.x, p.z, true, true)) got.add(p);
        }
        FORCED.put(event, new Forced(level, got));
        LotrLog.dbg("events", "tickets added: event={} chunks={} around {},{} (limit {}, governor {}) budget used {}/{}", LotrLog.id(event),
                got.size(), c.x, c.z, limit, LoadGovernor.state(), used(), budget);
        return got.size();
    }

    public static void release(UUID event, String why) {
        Forced f = FORCED.remove(event);
        if (f == null) return;
        for (ChunkPos p : f.chunks()) CONTROLLER.forceChunk(f.level(), event, p.x, p.z, false, true);
        LotrLog.dbg("events", "tickets released: event={} chunks={} reason={} budget used {}/{}", LotrLog.id(event), f.chunks().size(), why,
                used(), EventsConfig.i(EventsConfig.CHUNK_BUDGET));
    }

    public static void releaseAll(String why) {
        for (UUID id : new ArrayList<>(FORCED.keySet())) release(id, why);
    }
}
