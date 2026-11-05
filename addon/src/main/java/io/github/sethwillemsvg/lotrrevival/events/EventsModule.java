package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.network.WorldEventsPayload;
import io.github.sethwillemsvg.lotrrevival.nemesis.Ambush;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Map;
import java.util.TreeMap;

/**
 * Wiring for world events (specs/addon/nemesis-events.md, E tickets): attachment, config, ticket controller, payload, listeners,
 * commands. {@code LotrRevival} calls {@link #init} once.
 */
public final class EventsModule {
    private EventsModule() {}

    public static void init(IEventBus modBus) {
        EventParticipant.register(modBus);
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, EventsConfig.SPEC, "lotr_revival-events.toml");
        modBus.addListener((RegisterTicketControllersEvent e) -> EventChunkTickets.register(e));
        modBus.addListener((RegisterPayloadHandlersEvent e) -> WorldEventsPayload.register(e));
        Ambush.governorRed = () -> LoadGovernor.state() == LoadGovernor.State.RED;

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerStartedEvent e) -> {
            EventsConfig.logInUse();
            WorldEventSavedData.get(e.getServer());
            LoadGovernor.reset();
        });
        bus.addListener((ServerStoppingEvent e) -> WorldEventManager.onServerStopping(e.getServer()));
        bus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        bus.addListener((LivingDamageEvent.Post e) -> Participation.onDamage(e));
        bus.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide() || !e.loadedFromDisk()) return;
            EventParticipant p = EventParticipant.of(e.getEntity());
            if (p == null || WorldEventManager.get(p.eventId()) != null) return;
            if ("resident".equals(p.role())) {
                e.getEntity().removeData(EventParticipant.TYPE);
                return;
            }
            e.setCanceled(true);
            LotrLog.dbg("events", "stale event participant removed on load: entity={} event={}", LotrLog.id(e.getEntity().getUUID()),
                    LotrLog.id(p.eventId()));
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) WorldEventsPayload.send(p);
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) WorldEventsPayload.send(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) WorldEventManager.onLogout(p);
        });
        bus.addListener((RegisterCommandsEvent e) -> EventCommands.register(e.getDispatcher()));
        LotrLog.info("events", "module registered: types={}", WorldEventManager.TYPES.keySet());
    }

    private static void tick(MinecraftServer server) {
        try {
            long tick = server.getTickCount();
            if (tick % 20 == 0) {
                LoadGovernor.tick(server);
                WorldEventManager.tick(server);
            }
            EventScheduler.tick(server);
            if (tick % 600 == 0) summary(server);
        } catch (RuntimeException e) {
            LotrLog.error("events", "server tick failed", e);
        }
    }

    private static void summary(MinecraftServer server) {
        if (!LotrLog.debug("events")) return;
        Map<String, Integer> ended = new TreeMap<>();
        WorldEventManager.ENDED_COUNT.forEach((k, v) -> ended.put(k.key(), v));
        LotrLog.dbg("events", "summary tick={}: rolls={} staged={} rejected={} started={} ended={} offscreen={} active={} chunks={}/{} "
                        + "entities={} joins={} venue_lookups={} cache_hits={} payloads={} governor={} avg_ms={}",
                server.getTickCount(), EventScheduler.rolls, EventScheduler.staged, EventScheduler.rejected, WorldEventManager.started, ended,
                WorldEventManager.endedOffscreen, WorldEventManager.active().size(), EventChunkTickets.used(), EventsConfig.i(EventsConfig.CHUNK_BUDGET),
                WorldEventManager.entityTotal(), Participation.joins, VenueFinder.lookups, VenueFinder.cacheHits, WorldEventsPayload.sent,
                LoadGovernor.state(), String.format("%.1f", LoadGovernor.lastMs()));
        EventScheduler.rolls = EventScheduler.staged = EventScheduler.rejected = 0;
        WorldEventManager.started = WorldEventManager.endedOffscreen = 0;
        WorldEventManager.ENDED_COUNT.clear();
        Participation.joins = VenueFinder.lookups = VenueFinder.cacheHits = WorldEventsPayload.sent = 0;
    }
}
