package io.github.sethwillemsvg.lotrrevival.events.types;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.EventParticipant;
import io.github.sethwillemsvg.lotrrevival.events.EventsConfig;
import io.github.sethwillemsvg.lotrrevival.events.SiteRoster;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventManager;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventType;
import io.github.sethwillemsvg.lotrrevival.nemesis.Ambush;
import io.github.sethwillemsvg.lotrrevival.nemesis.Manifestation;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisSavedData;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ambush as a world event (spec Event types: Ambush, ticket N8). The nemesis ambush keeps its own schedule and eligibility
 * ({@code nemesis.Ambush}); once it fires it is staged here, so it goes through the event manager (started / ended game events,
 * participants, the load governor's shedding, rumour lines and node-map markers) like the other types.
 * <ul>
 *   <li>Lead: the nemesis and its bodyguards. Opposition: the target player and anyone helping (no NPCs).</li>
 *   <li>Lead wins when the target dies. Opposition wins when the nemesis dies or escapes.</li>
 *   <li>Cancelled (no winner, no renown) when no grudge holder is within 64 blocks of the nemesis for 30 s (the ambush leave
 *   rule: it retreats), when the target leaves the dimension or the game, or when the 5 minutes run out.</li>
 *   <li>No forced chunks, no action-bar hint, no reputation bonus and no "won an event" renown (the kill already pays +5);
 *   it does not count toward the player's one scheduled event, the server cap or the event cooldown.</li>
 * </ul>
 */
public final class AmbushEvent implements WorldEventType {
    public static final ResourceLocation ID = LotrRevival.id("ambush");
    public static final AmbushEvent INSTANCE = new AmbushEvent();
    private static final int LEAVE_DISTANCE = 64;
    private static final int LEAVE_TICKS = 600;

    /** What {@link #begin} hands to {@link #stage} (one ambush is staged at a time, on the server thread). */
    private static final class Pending {
        final ServerPlayer target;
        final NemesisRecord record;
        final BlockPos spot;
        @Nullable NpcEntity body;

        Pending(ServerPlayer target, NemesisRecord record, BlockPos spot) {
            this.target = target;
            this.record = record;
            this.spot = spot;
        }
    }

    private static @Nullable Pending pending;
    /** Event id -> game time a grudge holder was last within 64 blocks of the nemesis. */
    private static final Map<UUID, Long> LAST_NEAR = new HashMap<>();

    private AmbushEvent() {}

    /** {@code nemesis.Ambush}'s stager: starts the event for this target and record; returns the body or null. */
    public static @Nullable NpcEntity begin(ServerPlayer target, NemesisRecord r, BlockPos spot) {
        ServerLevel level = target.serverLevel();
        ResourceLocation opposition = Sides.playerFaction(target);
        SiteRoster.Pick pick = new SiteRoster.Pick(r.faction, opposition, Sides.sideOfFaction(level, r.faction), Map.of(), List.of(r.faction));
        Pending p = new Pending(target, r, spot);
        pending = p;
        try {
            WorldEvent ev = WorldEventManager.start(level, INSTANCE, target.blockPosition(), target.getUUID(), pick, null);
            LotrLog.dbg("events", "ambush event: nemesis={} target={} faction={} opposition={} -> {}", r.shortId(), LotrLog.id(target.getUUID()),
                    r.faction, opposition == null ? "none" : opposition, ev == null ? "not started" : "event " + ev.shortId());
            return ev == null ? null : p.body;
        } finally {
            pending = null;
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public List<String> venueKinds() {
        return List.of();
    }

    @Override
    public String rejectReason(SiteRoster.Pick pick) {
        return pending == null ? "ambushes are staged by the nemesis ambush schedule only" : null;
    }

    @Override
    public int stage(WorldEvent ev, ServerLevel level, SiteRoster.Pick pick) {
        Pending p = pending;
        if (p == null) {
            LotrLog.warn("events", "ambush event {} staged without a nemesis (start it through the ambush schedule): dropped", ev.shortId());
            return 0;
        }
        ev.radius = 32;
        NemesisRecord r = p.record;
        NpcEntity body = Ambush.manifest(p.target, r, p.spot, "ambush event " + ev.shortId() + " on " + LotrLog.id(p.target.getUUID()));
        if (body == null) return 0;
        r.eventId = ev.id;
        body.setData(EventParticipant.TYPE, new EventParticipant(ev.id, EventParticipant.LEAD, "nemesis"));
        ev.npcs.put(body.getUUID(), EventParticipant.LEAD);
        ev.nemeses.put(r.id, EventParticipant.LEAD);
        Manifestation.Body b = Manifestation.body(r.id);
        int guards = 0;
        if (b != null) {
            for (NpcEntity g : b.guards) {
                g.setData(EventParticipant.TYPE, new EventParticipant(ev.id, EventParticipant.LEAD, "bodyguard"));
                ev.npcs.put(g.getUUID(), EventParticipant.LEAD);
                guards++;
            }
        }
        p.body = body;
        LAST_NEAR.put(ev.id, level.getGameTime());
        Ambush.ENDED.remove(r.id);
        LotrLog.dbg("events", "ambush staged: event={} nemesis={} body={} guards={} spot={} target={} at {}", ev.shortId(), r.shortId(),
                LotrLog.id(body.getUUID()), guards, p.spot.toShortString(), LotrLog.id(p.target.getUUID()), p.target.blockPosition().toShortString());
        return 1 + guards;
    }

    @Override
    public WorldEvent.Outcome check(WorldEvent ev, ServerLevel level) {
        NemesisSavedData data = NemesisSavedData.get(level.getServer());
        UUID recordId = ev.nemeses.isEmpty() ? null : ev.nemeses.keySet().iterator().next();
        NemesisRecord r = recordId == null ? null : data.find(recordId);
        // 1. the ambush ended through the nemesis rules: the target was killed, or the nemesis died or escaped
        Boolean playerWon = null;
        if (r != null) playerWon = Ambush.ENDED.remove(r.id);
        if (playerWon == null) for (var en : Ambush.ENDED.entrySet()) {
            NemesisRecord gone = data.find(en.getKey());
            if (gone != null && ev.id.equals(gone.eventId)) {
                playerWon = Ambush.ENDED.remove(en.getKey());
                break;
            }
        }
        if (playerWon != null) return done(ev, playerWon ? WorldEvent.Outcome.OPPOSITION_WON : WorldEvent.Outcome.LEAD_WON,
                playerWon ? "the nemesis died or escaped" : "the target was killed");
        if (r == null) return done(ev, WorldEvent.Outcome.OPPOSITION_WON, "the nemesis left the field (died or escaped)");
        // 2. the target is gone
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(ev.owner);
        if (target == null || target.level() != level) return leave(level, ev, r, "target " + LotrLog.id(ev.owner) + " left");
        if (target.isDeadOrDying()) return done(ev, WorldEvent.Outcome.LEAD_WON, "the target is dead");
        // 3. the leave rule: no grudge holder within 64 blocks of the nemesis for 30 s
        Manifestation.Body b = Manifestation.body(r.id);
        if (b == null || b.entity.isRemoved()) return done(ev, WorldEvent.Outcome.OPPOSITION_WON, "the body is gone");
        long now = level.getGameTime();
        boolean near = false;
        for (ServerPlayer pl : level.players()) {
            if (r.activeGrudge(pl.getUUID()) > 0 && pl.distanceToSqr(b.entity) < (double) LEAVE_DISTANCE * LEAVE_DISTANCE) near = true;
        }
        if (near) LAST_NEAR.put(ev.id, now);
        else if (now - LAST_NEAR.getOrDefault(ev.id, now) > LEAVE_TICKS)
            return leave(level, ev, r, "no grudge holder within " + LEAVE_DISTANCE + " for " + LEAVE_TICKS / 20 + " s");
        return WorldEvent.Outcome.NONE;
    }

    private static WorldEvent.Outcome done(WorldEvent ev, WorldEvent.Outcome o, String why) {
        LAST_NEAR.remove(ev.id);
        LotrLog.dbg("events", "ambush {} decided: {} ({})", ev.shortId(), o, why);
        return o;
    }

    /** The nemesis gives up: it retreats now, no winner and no renown. */
    private static WorldEvent.Outcome leave(ServerLevel level, WorldEvent ev, NemesisRecord r, String why) {
        if (r.ambushTarget != null) Ambush.onAmbushEnded(level.getServer(), r, r.ambushTarget, false);
        Ambush.ENDED.remove(r.id);
        Manifestation.retreat(level.getServer(), r, "ambush: " + why);
        return done(ev, WorldEvent.Outcome.CANCELLED, why);
    }

    /** Five minutes and nobody won: called off like a leave. */
    @Override
    public WorldEvent.Outcome onTimeout() {
        return WorldEvent.Outcome.CANCELLED;
    }

    @Override
    public int durationSeconds() {
        return EventsConfig.i(EventsConfig.AMBUSH_DURATION_SECONDS);
    }

    @Override
    public boolean forcesChunks() {
        return false;
    }

    @Override
    public boolean scheduled() {
        return false;
    }

    @Override
    public int winRenown() {
        return 0;
    }

    @Override
    public int bonus(WorldEvent.Outcome outcome) {
        return 0;
    }

    @Override
    public String titleKey(String viewerSide) {
        return EventParticipant.LEAD.equals(viewerSide) ? "lotr_revival.event.title.ambush.lead" : "lotr_revival.event.title.ambush.opposition";
    }
}
