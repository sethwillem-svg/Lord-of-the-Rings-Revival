package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.network.WorldEventsPayload;
import io.github.sethwillemsvg.lotrrevival.events.types.AmbushEvent;
import io.github.sethwillemsvg.lotrrevival.events.types.MusterEvent;
import io.github.sethwillemsvg.lotrrevival.events.types.RaidEvent;
import io.github.sethwillemsvg.lotrrevival.events.types.WarbandClashEvent;
import io.github.sethwillemsvg.lotrrevival.nemesis.Manifestation;
import io.github.sethwillemsvg.lotrrevival.nemesis.MeRanks;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisApi;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisConfig;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisSavedData;
import io.github.sethwillemsvg.lotrrevival.nemesis.Renown;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.initializer.NpcEntityBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The event state machine (spec E1): start, once-a-second checks, end, cleanup, release and handover, rumour lines and the
 * started / ended game events. Active events live in memory; they never resume after a restart.
 */
public final class WorldEventManager {
    public static final Map<ResourceLocation, WorldEventType> TYPES = new LinkedHashMap<>();
    private static final Map<UUID, WorldEvent> ACTIVE = new LinkedHashMap<>();
    /** Ended events waiting for their survivors to be removed (30 s). */
    private static final Map<UUID, WorldEvent> CLOSING = new LinkedHashMap<>();
    public static final Map<WorldEvent.Outcome, Integer> ENDED_COUNT = new EnumMap<>(WorldEvent.Outcome.class);
    public static int started, endedOffscreen;

    static {
        register(new WarbandClashEvent());
        register(new MusterEvent());
        register(new RaidEvent());
        register(AmbushEvent.INSTANCE);   // staged by nemesis.Ambush through AmbushEvent.begin, never by the scheduler (weight 0)
    }

    private WorldEventManager() {}

    public static void register(WorldEventType t) {
        TYPES.put(t.id(), t);
    }

    public static List<WorldEvent> active() {
        return new ArrayList<>(ACTIVE.values());
    }

    public static @Nullable WorldEvent get(UUID id) {
        WorldEvent e = ACTIVE.get(id);
        return e != null ? e : CLOSING.get(id);
    }

    public static @Nullable WorldEvent byShortId(String s) {
        for (WorldEvent e : ACTIVE.values()) if (e.id.toString().startsWith(s)) return e;
        return null;
    }

    public static int entityTotal() {
        int n = 0;
        for (WorldEvent e : ACTIVE.values()) n += e.npcs.size();
        return n;
    }

    // ---------------------------------------------------------------- start

    /** Stages an event (type and pick already chosen). Null when cancelled or nothing could be placed. */
    public static @Nullable WorldEvent start(ServerLevel level, WorldEventType type, BlockPos centre, UUID owner, SiteRoster.Pick pick,
                                             @Nullable io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues.Venue venue) {
        long tick = level.getGameTime();
        WorldEvent ev = new WorldEvent(UUID.randomUUID(), type.id(), level.dimension().location(), centre, owner, tick);
        ev.venue = venue;
        ev.leadFactions.add(pick.lead());
        if (pick.opposition() != null) ev.oppFactions.add(pick.opposition());   // an ambushed player may have no faction
        ev.deadline = tick + type.durationSeconds() * 20L;
        if (NeoForge.EVENT_BUS.post(new WorldEventGameEvents.Started(ev)).isCanceled()) {
            LotrLog.dbg("events", "event {} {} cancelled by a listener", type.id(), ev.shortId());
            return null;
        }
        ACTIVE.put(ev.id, ev);
        int chunks = type.forcesChunks() ? EventChunkTickets.forceAround(level, ev.id, centre) : 0;
        int placed = type.stage(ev, level, pick);
        ev.initialLead = ev.count(EventParticipant.LEAD);
        ev.initialOpposition = ev.count(EventParticipant.OPPOSITION);
        if (placed <= 0) {
            ACTIVE.remove(ev.id);
            EventChunkTickets.release(ev.id, "staging failed");
            removeNpcs(level, ev);
            LotrLog.dbg("events", "event {} {} at {}: nothing placed -> dropped", type.id(), ev.shortId(), centre.toShortString());
            return null;
        }
        if (venue != null) VenueFinder.cooldown(level, venue, tick);
        started++;
        LotrLog.dbg("events", "event started: id={} type={} centre={} venue={} lead={} ({} npcs) opposition={} ({} npcs) nemeses={} owner={} "
                        + "chunks={} deadline={} tick={}", ev.shortId(), type.id(), centre.toShortString(), venue == null ? "open ground" : VenueFinder.key(venue),
                ev.leadFactions, ev.initialLead, ev.oppFactions, ev.initialOpposition, ev.nemeses.size(), LotrLog.id(owner), chunks, ev.deadline, tick);
        if (type.scheduled()) hint(level, ev);
        WorldEventsPayload.changed(level.getServer());
        return ev;
    }

    /** Action-bar hint for the player it was staged for: direction and rough distance. */
    private static void hint(ServerLevel level, WorldEvent ev) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(ev.owner);
        if (p == null) return;
        double dx = ev.centre.getX() - p.getX(), dz = ev.centre.getZ() - p.getZ();
        String[] dirs = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        int idx = (int) Math.floorMod(Math.round(ang / 45.0), 8);
        int dist = (int) (Math.round(Math.sqrt(dx * dx + dz * dz) / 50.0) * 50);
        WorldEventType t = TYPES.get(ev.type);
        p.displayClientMessage(Component.translatable("lotr_revival.event.hint." + ev.type.getPath(),
                Component.translatable("lotr_revival.event.dir." + dirs[idx]), dist), true);
        LotrLog.dbg("events", "hint to {}: {} {} ~{} blocks ({})", LotrLog.id(p.getUUID()), ev.type.getPath(), dirs[idx], dist, t == null ? "?" : t.titleKey("lead"));
    }

    /** Spawns one participant of a side; null when the type cannot be built or the entity caps are reached. */
    public static @Nullable NpcEntity spawnNpc(ServerLevel level, WorldEvent ev, ResourceLocation type, BlockPos pos, String side, String role) {
        if (ev.npcs.size() >= EventsConfig.i(EventsConfig.ENTITIES_PER_EVENT)) {
            LotrLog.warn("events", "entity cap {} per event reached: event {}", EventsConfig.i(EventsConfig.ENTITIES_PER_EVENT), ev.shortId());
            return null;
        }
        if (entityTotal() >= EventsConfig.i(EventsConfig.ENTITIES_TOTAL)) {
            LotrLog.warn("events", "total entity cap {} reached: event {}", EventsConfig.i(EventsConfig.ENTITIES_TOTAL), ev.shortId());
            return null;
        }
        NpcEntity npc = new NpcEntityBuilder(level, pos).withNpcType(type).build();
        if (npc == null || npc.getNpcType() == null) {
            LotrLog.warn("events", "Middle-earth could not build {} for event {}", type, ev.shortId());
            return null;
        }
        npc.setData(EventParticipant.TYPE, new EventParticipant(ev.id, side, role));
        npc.setPersistenceRequired();
        level.addFreshEntity(npc);
        ev.npcs.put(npc.getUUID(), side);
        return npc;
    }

    /** Makes an AT_LARGE nemesis of this faction lead a side (grudges on the owner first, then highest renown). */
    public static @Nullable NpcEntity spawnNemesis(ServerLevel level, WorldEvent ev, ResourceLocation faction, BlockPos pos, String side) {
        NemesisSavedData data = NemesisSavedData.get(level.getServer());
        NemesisRecord best = null;
        for (NemesisRecord r : data.living(faction)) {
            if (r.status != NemesisRecord.Status.AT_LARGE) continue;
            if (best == null || r.grudge(ev.owner) > best.grudge(ev.owner)
                    || (r.grudge(ev.owner) == best.grudge(ev.owner) && r.renown > best.renown)) best = r;
        }
        if (best == null || data.manifestCount() >= NemesisConfig.i(NemesisConfig.MAX_MANIFEST)) return null;
        NpcEntity body = Manifestation.spawn(level, best, pos, "world event " + ev.shortId());
        if (body == null) return null;
        best.eventId = ev.id;
        body.setData(EventParticipant.TYPE, new EventParticipant(ev.id, side, "nemesis"));
        ev.npcs.put(body.getUUID(), side);
        ev.nemeses.put(best.id, side);
        return body;
    }

    // ---------------------------------------------------------------- tick (once a second)

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        boolean shedDone = false;
        for (WorldEvent ev : new ArrayList<>(ACTIVE.values())) {
            try {
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ev.dimension));
                if (level == null) {
                    end(server, ev, null, true, "dimension gone");
                    continue;
                }
                long now = level.getGameTime();
                prune(level, ev);
                if (level.getDifficulty() == Difficulty.PEACEFUL) {
                    end(server, ev, null, true, "peaceful");
                    continue;
                }
                for (ResourceLocation f : ev.leadFactions) if (Sides.faction(level, f) == null) {
                    end(server, ev, null, true, "faction " + f + " disappeared");
                }
                if (ev.state != WorldEvent.State.ACTIVE) continue;
                WorldEventType type = TYPES.get(ev.type);
                WorldEvent.Outcome o = type.check(ev, level);
                if (o == WorldEvent.Outcome.NONE && now >= ev.deadline) {
                    o = type.onTimeout();
                    LotrLog.dbg("events", "event {} timed out -> {}", ev.shortId(), o);
                }
                if (o != WorldEvent.Outcome.NONE) {
                    end(server, ev, o, false, "decided on screen");
                    continue;
                }
                if (!ownerRules(server, level, ev, now)) continue;
                if (!shedDone && LoadGovernor.shedding(tick)) {
                    var near = level.getNearestPlayer(ev.centre.getX(), ev.centre.getY(), ev.centre.getZ(), 64, false);
                    if (near == null) {
                        shedDone = true;
                        end(server, ev, null, true, "governor red: shed (no player within 64)");
                    } else {
                        LotrLog.dbg("events", "shed skipped: event {} has player {} at {} blocks ({})", ev.shortId(), LotrLog.id(near.getUUID()),
                                String.format("%.1f", Math.sqrt(near.distanceToSqr(ev.centre.getCenter()))), LoadGovernor.describe(tick));
                    }
                }
            } catch (RuntimeException e) {
                LotrLog.error("events", "event " + ev.shortId() + " tick failed; resolving off-screen", e);
                end(server, ev, null, true, "error");
            }
        }
        for (WorldEvent ev : new ArrayList<>(CLOSING.values())) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ev.dimension));
            if (level == null || level.getGameTime() >= ev.removeNpcsAtTick) {
                if (level != null) removeNpcs(level, ev);
                CLOSING.remove(ev.id);
            }
        }
    }

    private static void prune(ServerLevel level, WorldEvent ev) {
        ev.npcs.entrySet().removeIf(en -> {
            Entity e = level.getEntity(en.getKey());
            return e == null || !e.isAlive();
        });
        NemesisSavedData data = NemesisSavedData.get(level.getServer());
        ev.nemeses.entrySet().removeIf(en -> {
            NemesisRecord r = data.find(en.getKey());
            boolean gone = r == null || r.status != NemesisRecord.Status.MANIFEST || !ev.id.equals(r.eventId);
            if (gone) LotrLog.dbg("events", "event {}: nemesis {} left ({})", ev.shortId(), LotrLog.id(en.getKey()), r == null ? "gone" : r.status);
            return gone;
        });
    }

    /** Owner left (logout, dimension, more than 384 blocks for 60 s): hand over to a participant within 128, else off-screen. */
    private static boolean ownerRules(MinecraftServer server, ServerLevel level, WorldEvent ev, long now) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ev.owner);
        int release = EventsConfig.i(EventsConfig.RELEASE_DISTANCE);
        boolean gone = owner == null || owner.level() != level;
        boolean far = !gone && owner.blockPosition().distSqr(ev.centre) > (double) release * release;
        if (!gone && !far) {
            ev.ownerFarSinceTick = -1;
            return true;
        }
        if (far && !gone) {
            if (ev.ownerFarSinceTick < 0) ev.ownerFarSinceTick = now;
            if (now - ev.ownerFarSinceTick < 1200) return true;
        }
        for (UUID u : ev.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && !u.equals(ev.owner) && p.level() == level && p.blockPosition().distSqr(ev.centre) <= 128 * 128) {
                LotrLog.dbg("events", "handover: event {} owner {} -> {}", ev.shortId(), LotrLog.id(ev.owner), LotrLog.id(u));
                ev.owner = u;
                ev.ownerFarSinceTick = -1;
                WorldEventsPayload.changed(server);
                return true;
            }
        }
        LotrLog.warn("events", "event {}: owner {} {} and no participant within 128 for handover", ev.shortId(), LotrLog.id(ev.owner),
                gone ? "left" : "is far away");
        end(server, ev, null, true, "owner " + (gone ? "left" : "far away") + ", no handover");
        return false;
    }

    /** Player logged out: their events are handed over or resolved on the next check; tickets release then. */
    public static void onLogout(ServerPlayer p) {
        for (WorldEvent ev : ACTIVE.values()) if (ev.owner.equals(p.getUUID())) ev.ownerFarSinceTick = -2;
    }

    // ---------------------------------------------------------------- end

    /** Ends an event: {@code outcome} null means resolve off-screen with power rolls. */
    public static void end(MinecraftServer server, WorldEvent ev, @Nullable WorldEvent.Outcome outcome, boolean offscreen, String reason) {
        if (ev.state != WorldEvent.State.ACTIVE) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ev.dimension));
        if (offscreen || outcome == null) {
            outcome = level == null ? WorldEvent.Outcome.CANCELLED : OffscreenResolver.resolve(level, ev);
            offscreen = true;
            endedOffscreen++;
        }
        ev.outcome = outcome;
        ev.offscreen = offscreen;
        ev.state = WorldEvent.State.ENDED;
        ev.endTick = level == null ? server.overworld().getGameTime() : level.getGameTime();
        ACTIVE.remove(ev.id);
        ENDED_COUNT.merge(outcome, 1, Integer::sum);
        EventChunkTickets.release(ev.id, "event ended (" + reason + ")");
        LotrLog.dbg("events", "event ended: id={} type={} outcome={} offscreen={} reason={} lead_left={}/{} opposition_left={}/{} players={} tick={}",
                ev.shortId(), ev.type, outcome, offscreen, reason, ev.count(EventParticipant.LEAD), ev.initialLead,
                ev.count(EventParticipant.OPPOSITION), ev.initialOpposition, ev.players.size(), ev.endTick);
        records(server, ev, offscreen);
        if (!offscreen && level != null) Participation.payout(level, ev);
        WorldEventSavedData data = WorldEventSavedData.get(server);
        WorldEventType type = TYPES.get(ev.type);
        if (type == null || type.scheduled())
            data.playerCooldownUntil.put(ev.owner, ev.endTick + EventsConfig.i(EventsConfig.COOLDOWN_SECONDS) * 20L);
        data.addEnded(ev.summary(), ev.endTick);
        if (level != null) rumour(level, ev);
        ev.removeNpcsAtTick = ev.endTick + 600;
        CLOSING.put(ev.id, ev);
        if (offscreen && level != null) removeNpcs(level, ev);
        NeoForge.EVENT_BUS.post(new WorldEventGameEvents.Ended(ev));
        WorldEventsPayload.changed(server);
    }

    /** Winning side's nemeses +3 renown; the type adds its own (muster); nemeses still in the event leave with their survivors. */
    private static void records(MinecraftServer server, WorldEvent ev, boolean offscreen) {
        String winner = ev.outcome == WorldEvent.Outcome.LEAD_WON ? EventParticipant.LEAD
                : ev.outcome == WorldEvent.Outcome.OPPOSITION_WON ? EventParticipant.OPPOSITION : null;
        NemesisSavedData data = NemesisSavedData.get(server);
        WorldEventType type = TYPES.get(ev.type);
        int renown = type == null ? NemesisConfig.i(NemesisConfig.RENOWN_EVENT_WIN) : type.winRenown();
        for (var en : ev.nemeses.entrySet()) {
            NemesisRecord r = data.find(en.getKey());
            if (r == null || !r.alive()) continue;
            if (en.getValue().equals(winner) && renown > 0) Renown.add(server, r, renown, "won world event " + ev.shortId());
            if (ev.type.getPath().equals("muster") && EventParticipant.LEAD.equals(winner) && en.getValue().equals(winner)) {
                r.extraBodyguards++;
                LotrLog.dbg("events", "muster undisturbed: {} gains a bodyguard for its next manifestation", r.shortId());
            }
            r.eventId = null;
        }
    }

    private static void removeNpcs(ServerLevel level, WorldEvent ev) {
        int n = 0;
        for (UUID u : new ArrayList<>(ev.npcs.keySet())) {
            Entity e = level.getEntity(u);
            if (e == null || e.isRemoved()) continue;
            EventParticipant part = EventParticipant.of(e);
            if (part != null && "resident".equals(part.role())) {
                e.removeData(EventParticipant.TYPE);   // settlement NPCs stay
                continue;
            }
            NemesisRecord r = NemesisApi.bodyOf(e);
            if (r != null) Manifestation.retreat(level.getServer(), r, "world event over");
            else e.discard();
            n++;
        }
        ev.npcs.clear();
        if (n > 0) LotrLog.dbg("events", "event {}: {} survivor(s) removed", ev.shortId(), n);
    }

    private static void rumour(ServerLevel level, WorldEvent ev) {
        Component line = Component.translatable("lotr_revival.event.rumour." + ev.outcome.key(),
                Component.translatable("lotr_revival.event.type." + ev.type.getPath())).withStyle(ChatFormatting.GRAY);
        int r = EventsConfig.i(EventsConfig.RELEASE_DISTANCE);
        List<UUID> told = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(ev.centre) <= (double) r * r) {
                p.sendSystemMessage(line);
                told.add(p.getUUID());
            }
        }
        NemesisSavedData data = NemesisSavedData.get(level.getServer());
        for (UUID id : ev.nemeses.keySet()) {
            NemesisRecord rec = data.find(id);
            if (rec == null) continue;
            for (UUID u : rec.grudges.keySet()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
                if (p != null && !told.contains(u)) {
                    p.sendSystemMessage(line);
                    told.add(u);
                }
            }
        }
    }

    /** Server stop: tickets released, nothing resumes. */
    public static void onServerStopping(MinecraftServer server) {
        for (WorldEvent ev : new ArrayList<>(ACTIVE.values())) {
            LotrLog.dbg("events", "server stopping: event {} dropped", ev.shortId());
            NemesisSavedData data = NemesisSavedData.get(server);
            for (UUID id : ev.nemeses.keySet()) {
                NemesisRecord r = data.find(id);
                if (r != null) r.eventId = null;
            }
        }
        ACTIVE.clear();
        CLOSING.clear();
        EventChunkTickets.releaseAll("server stopping");
    }

    /** Participants saved in chunks from an earlier run belong to no live event: removed on load. */
    public static boolean staleParticipant(Entity e) {
        EventParticipant p = EventParticipant.of(e);
        return p != null && get(p.eventId()) == null;
    }

    public static List<WorldEvent> byAge() {
        List<WorldEvent> l = active();
        l.sort(Comparator.comparingLong(e -> e.startTick));
        return l;
    }

    /** Scheduled events staged for this player (an ambush is extra: at most one besides). */
    public static int countFor(UUID owner) {
        int n = 0;
        for (WorldEvent e : ACTIVE.values()) if (e.owner.equals(owner) && scheduled(e)) n++;
        return n;
    }

    /** Active scheduled events (the server cap counts these; ambushes have their own rules). */
    public static int scheduledCount() {
        int n = 0;
        for (WorldEvent e : ACTIVE.values()) if (scheduled(e)) n++;
        return n;
    }

    public static boolean scheduled(WorldEvent e) {
        WorldEventType t = TYPES.get(e.type);
        return t == null || t.scheduled();
    }

    /** Game tests: register a hand-built event as active. */
    public static void putForTest(WorldEvent ev) {
        ACTIVE.put(ev.id, ev);
    }

    /** Hard reset (tests). */
    public static void clearForTest(MinecraftServer server) {
        for (WorldEvent ev : new ArrayList<>(ACTIVE.values())) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ev.dimension));
            if (level != null) removeNpcs(level, ev);
        }
        ACTIVE.clear();
        CLOSING.clear();
        EventChunkTickets.releaseAll("test reset");
    }
}
