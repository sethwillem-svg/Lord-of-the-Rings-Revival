package io.github.sethwillemsvg.lotrrevival.events.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.EventChunkTickets;
import io.github.sethwillemsvg.lotrrevival.events.EventParticipant;
import io.github.sethwillemsvg.lotrrevival.events.EventScheduler;
import io.github.sethwillemsvg.lotrrevival.events.LoadGovernor;
import io.github.sethwillemsvg.lotrrevival.events.SiteRoster;
import io.github.sethwillemsvg.lotrrevival.events.VenueFinder;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventManager;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventSavedData;
import io.github.sethwillemsvg.lotrrevival.events.network.WorldEventsPayload;
import io.github.sethwillemsvg.lotrrevival.events.types.MusterEvent;
import io.github.sethwillemsvg.lotrrevival.events.types.RaidEvent;
import io.github.sethwillemsvg.lotrrevival.events.types.WarbandClashEvent;
import io.github.sethwillemsvg.lotrrevival.nemesis.Ambush;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.GONDOR;
import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.MORDOR;
import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.me;

/** World-event acceptance tests (specs/addon/nemesis-events.md). One batch per test: they share the event manager. */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EventTests {
    private static final ResourceLocation WARRIOR = me("mordor.warrior"), SOLDIER = me("gondor.soldier"), THUG = me("brigand.thug");
    private static final BlockPos P = new BlockPos(1, 1, 1);

    private EventTests() {}

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static void reset(GameTestHelper h) {
        WorldEventManager.clearForTest(server(h));
        LoadGovernor.frozen = true;
        LoadGovernor.reset();
        SiteRoster.testTable = null;
        EventScheduler.testSpawnExclusion = -1;
        WorldEventSavedData.get(server(h)).ended.clear();
        VenueFinder.clearCache();
    }

    private static void cleanup(GameTestHelper h, ServerPlayer... ps) {
        reset(h);
        for (ServerPlayer p : ps) NemesisTestPlayers.remove(p);
        NemesisTestPlayers.clear(server(h));
    }

    private static WorldEvent event(GameTestHelper h, BlockPos centre, UUID owner) {
        WorldEvent ev = new WorldEvent(UUID.randomUUID(), WarbandClashEvent.ID, h.getLevel().dimension().location(), centre, owner,
                h.getLevel().getGameTime());
        ev.leadFactions.add(MORDOR);
        ev.oppFactions.add(GONDOR);
        ev.deadline = h.getLevel().getGameTime() + 6000;
        WorldEventManager.putForTest(ev);
        return ev;
    }

    // ------------------------------------------------------------------ governor

    @GameTest(template = "empty", batch = "events_governor")
    public static void governor(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        NemesisTestPlayers.run(h, "governor", () -> {
            reset(h);
            h.assertTrue(LoadGovernor.update(0, 20, 0) == LoadGovernor.State.GREEN, "20 ms not green");
            h.assertTrue(LoadGovernor.update(10, 38, 0) == LoadGovernor.State.YELLOW, "38 ms not yellow");
            h.assertTrue(LoadGovernor.update(20, 50, 0) == LoadGovernor.State.YELLOW, "left yellow inside the 10 s hold");
            h.assertTrue(LoadGovernor.update(220, 50, 0) == LoadGovernor.State.RED, "50 ms after the hold not red");
            h.assertTrue(LoadGovernor.update(430, 42, 0) == LoadGovernor.State.RED, "42 ms dropped out of red (needs < 40)");
            h.assertTrue(LoadGovernor.update(440, 39, 0) == LoadGovernor.State.YELLOW, "39 ms did not drop to yellow");
            h.assertTrue(LoadGovernor.update(650, 31, 0) == LoadGovernor.State.YELLOW, "31 ms dropped out of yellow (needs < 30)");
            h.assertTrue(LoadGovernor.update(660, 29, 0) == LoadGovernor.State.GREEN, "29 ms did not drop to green");
            h.assertTrue(LoadGovernor.update(870, 10, 1.0) == LoadGovernor.State.RED, "full chunk budget not red");
            h.assertFalse(LoadGovernor.shedding(870 + 599), "shedding before 30 s in red");
            h.assertTrue(LoadGovernor.shedding(870 + 600), "not shedding after 30 s in red");
            // Red sheds an event with no player near and releases its chunks
            ServerPlayer owner = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            owner.moveTo(owner.getX() + 100, owner.getY(), owner.getZ());   // within the 384 release distance, beyond 64
            BlockPos c = h.absolutePos(P);
            WorldEvent ev = event(h, c, owner.getUUID());
            NpcEntity l = WorldEventManager.spawnNpc(h.getLevel(), ev, WARRIOR, c, EventParticipant.LEAD, "soldier");
            NpcEntity o = WorldEventManager.spawnNpc(h.getLevel(), ev, SOLDIER, c.offset(2, 0, 0), EventParticipant.OPPOSITION, "soldier");
            h.assertTrue(l != null && o != null, "could not spawn the bands");
            l.setNoAi(true);
            o.setNoAi(true);
            EventChunkTickets.forceAround(h.getLevel(), ev.id, ev.centre);
            h.assertTrue(EventChunkTickets.forcedFor(ev.id) > 0, "no chunks forced");
            LoadGovernor.forceForTest(LoadGovernor.State.RED);
            WorldEventManager.tick(server(h));
            h.assertTrue(ev.state == WorldEvent.State.ENDED && ev.offscreen, "red did not shed the event: " + ev.state);
            h.assertTrue(EventChunkTickets.forcedFor(ev.id) == 0, "chunks not released");
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ tickets

    @GameTest(template = "empty", batch = "events_tickets")
    public static void tickets(GameTestHelper h) {
        NemesisTestPlayers.run(h, "tickets", () -> {
            reset(h);
            BlockPos c = h.absolutePos(P);
            UUID a = UUID.randomUUID();
            h.assertTrue(EventChunkTickets.forceAround(h.getLevel(), a, c) == 9, "green event did not get 9 chunks");
            LoadGovernor.forceForTest(LoadGovernor.State.YELLOW);
            UUID b = UUID.randomUUID();
            h.assertTrue(EventChunkTickets.forceAround(h.getLevel(), b, c) == 4, "yellow event did not get 4 chunks");
            LoadGovernor.forceForTest(LoadGovernor.State.GREEN);
            List<UUID> more = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                UUID id = UUID.randomUUID();
                more.add(id);
                EventChunkTickets.forceAround(h.getLevel(), id, c);
                h.assertTrue(EventChunkTickets.used() <= 48, "budget exceeded: " + EventChunkTickets.used());
            }
            h.assertTrue(EventChunkTickets.used() == 48, "budget not filled: " + EventChunkTickets.used());
            EventChunkTickets.release(a, "test end");
            h.assertTrue(EventChunkTickets.forcedFor(a) == 0 && EventChunkTickets.used() == 39, "release on end: used " + EventChunkTickets.used());
            EventChunkTickets.releaseAll("test");
            h.assertTrue(EventChunkTickets.used() == 0, "release all left " + EventChunkTickets.used());
            // TODO(spec §Acceptance tickets): release on owner logout is covered by WorldEventManager.ownerRules, not driven here
        }, () -> cleanup(h));
    }

    // ------------------------------------------------------------------ site factions

    @GameTest(template = "empty", batch = "events_site_factions")
    public static void siteFactions(GameTestHelper h) {
        NemesisTestPlayers.run(h, "site_factions", () -> {
            reset(h);
            BlockPos c = h.absolutePos(P);
            SiteRoster.testTable = Map.of(WARRIOR, 10, SOLDIER, 10);
            SiteRoster.Pick both = SiteRoster.pick(h.getLevel(), c, null, Sides.Side.EVIL);
            h.assertTrue(both != null && both.lead().equals(MORDOR) && both.opposition().equals(GONDOR), "Mordor/Gondor clash not picked: " + both);
            SiteRoster.Pick g = SiteRoster.pick(h.getLevel(), c, null, Sides.Side.GOOD);
            h.assertTrue(g != null && g.lead().equals(GONDOR) && g.opposition().equals(MORDOR), "good lead not picked: " + g);
            SiteRoster.testTable = Map.of(WARRIOR, 10);
            SiteRoster.Pick sworn = SiteRoster.pick(h.getLevel(), c, null, Sides.Side.GOOD);
            h.assertTrue(sworn != null && sworn.lead().equals(MORDOR) && sworn.opposition().equals(GONDOR), "sworn enemy fallback: " + sworn);
            SiteRoster.testTable = Map.of(THUG, 10);
            h.assertTrue(SiteRoster.pick(h.getLevel(), c, null, Sides.Side.GOOD) == null, "a brigand-only site was not rejected");
        }, () -> cleanup(h));
    }

    // ------------------------------------------------------------------ scheduler

    @GameTest(template = "empty", batch = "events_scheduler")
    public static void scheduler(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        NemesisTestPlayers.run(h, "scheduler", () -> {
            reset(h);
            h.assertTrue(EventScheduler.cap(0) == 2 && EventScheduler.cap(3) == 3 && EventScheduler.cap(6) == 4 && EventScheduler.cap(60) == 8,
                    "player-scaled cap wrong");
            NemesisRandom.forTest(42);
            int good = 0;
            for (int i = 0; i < 1000; i++) if (EventScheduler.chooseLeadSide(Sides.Side.GOOD) == Sides.Side.GOOD) good++;
            LotrLog.info("test", "scheduler: side balance {} / 1000 led by the player's side", good);
            h.assertTrue(good >= 570 && good <= 630, "side balance " + good + "/1000 not 60/40 within 3 points");
            BlockPos spawn = h.getLevel().getSharedSpawnPos();
            h.assertTrue(EventScheduler.siteProblem(h.getLevel(), spawn, false) != null, "world spawn not excluded");
            EventScheduler.testSpawnExclusion = 0;
            BlockPos c = h.absolutePos(P);
            WorldEvent other = event(h, c.offset(50, 0, 0), UUID.randomUUID());
            String spacing = EventScheduler.siteProblem(h.getLevel(), c, false);
            h.assertTrue(spacing != null && spacing.contains("event"), "96-block spacing not applied: " + spacing);
            WorldEventManager.clearForTest(server(h));
            h.assertTrue(EventScheduler.siteProblem(h.getLevel(), c.offset(200, 0, 0), false) == null, "a clear site was rejected");
            for (int i = 0; i < 20; i++) {
                BlockPos b = EventScheduler.bandPoint(h.getLevel(), c);
                if (b == null) continue;
                double d = Math.sqrt(Math.pow(b.getX() - c.getX(), 2) + Math.pow(b.getZ() - c.getZ(), 2));
                h.assertTrue(d >= 63 && d <= 257, "band point " + d + " blocks away");
            }
            ServerPlayer p = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            Ambush.SESSION_START.put(p.getUUID(), (long) server(h).getTickCount() - 10 * 60 * 20);
            String ok = EventScheduler.whyNotEligible(p, false);
            h.assertTrue(ok == null, "player not eligible: " + ok);
            WorldEventSavedData.get(server(h)).playerCooldownUntil.put(p.getUUID(), h.getLevel().getGameTime() + 1000);
            h.assertTrue(EventScheduler.whyNotEligible(p, false) != null, "cooldown ignored");
            h.assertTrue(EventScheduler.whyNotEligible(p, true) == null, "forced events should skip the cooldown");
            WorldEventSavedData.get(server(h)).playerCooldownUntil.remove(p.getUUID());
            for (int i = 0; i < EventScheduler.serverCap(server(h)); i++) event(h, c.offset(1000 + 200 * i, 0, 0), UUID.randomUUID());
            h.assertTrue(EventScheduler.whyNotEligible(p, false) != null, "server cap ignored");
            LotrLog.info("test", "scheduler: spacing checked against event {}", other.shortId());
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ venues

    @GameTest(template = "empty", batch = "events_venues")
    public static void venues(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        StructureVenues.Lookup prev = StructureVenues.get();
        NemesisTestPlayers.run(h, "venues", () -> {
            reset(h);
            BlockPos c = h.absolutePos(P);
            long tick = h.getLevel().getGameTime();
            ServerPlayer p = ps[0] = NemesisTestPlayers.player(h, MORDOR, P);
            StructureVenues.set((level, from, min, max, kind, budget) -> List.of());
            VenueFinder.clearCache();
            h.assertTrue(EventScheduler.stage(p, RaidEvent.ID) == null, "a raid was staged without a village venue");
            BlockPos vc = c.offset(2, 0, 2);
            StructureVenues.Venue v = new StructureVenues.Venue(ResourceLocation.fromNamespaceAndPath("lotr_revival", "test_camp"), vc,
                    new BoundingBox(vc.getX() - 3, vc.getY() - 1, vc.getZ() - 3, vc.getX() + 3, vc.getY() + 4, vc.getZ() + 3), MORDOR, "camp");
            StructureVenues.set((level, from, min, max, kind, budget) -> kind.equals("camp") ? List.of(v) : List.of());
            VenueFinder.clearCache();
            StructureVenues.Venue found = VenueFinder.find(h.getLevel(), c, new MusterEvent().venueKinds(), tick);
            h.assertTrue(found == v, "test camp not found");
            VenueFinder.cooldown(h.getLevel(), v, tick);
            h.assertTrue(VenueFinder.find(h.getLevel(), c, new MusterEvent().venueKinds(), tick) == null, "venue cooldown ignored");
            List<BlockPos> floor = VenueFinder.floor(h.getLevel(), v.centre(), v.bounds(), 4, 0);
            for (BlockPos f : floor) h.assertTrue(v.bounds().isInside(f) || f.getY() == v.bounds().maxY() + 1, "floor spot outside the venue: " + f);
            WorldEventSavedData.get(server(h)).venueCooldownUntil.remove(VenueFinder.key(v));
            // TODO(spec §Acceptance venues): a real structure placed in a loaded chunk with a test tag, once structures.md lands
        }, () -> {
            StructureVenues.set(prev);
            VenueFinder.clearCache();
            cleanup(h, ps);
        });
    }

    // ------------------------------------------------------------------ outcomes

    @GameTest(template = "empty", batch = "events_outcomes")
    public static void eventOutcomes(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[2];
        NemesisTestPlayers.run(h, "event_outcomes", () -> {
            reset(h);
            BlockPos c = h.absolutePos(P);
            ServerPlayer a = ps[0] = NemesisTestPlayers.player(h, MORDOR, P);
            ServerPlayer b = ps[1] = NemesisTestPlayers.player(h, MORDOR, P);
            b.moveTo(b.getX() + 120, b.getY(), b.getZ());
            int repA = ReputationHooks.get().reputation(a, MORDOR), repB = ReputationHooks.get().reputation(b, MORDOR);
            WorldEvent ev = event(h, c, a.getUUID());
            NpcEntity l1 = WorldEventManager.spawnNpc(h.getLevel(), ev, WARRIOR, c.offset(1, 0, 0), EventParticipant.LEAD, "soldier");
            NpcEntity o1 = WorldEventManager.spawnNpc(h.getLevel(), ev, SOLDIER, c.offset(3, 0, 0), EventParticipant.OPPOSITION, "soldier");
            h.assertTrue(l1 != null && o1 != null, "could not spawn the bands");
            l1.setNoAi(true);
            o1.setNoAi(true);
            var pa = io.github.sethwillemsvg.lotrrevival.events.Participation.join(ev, a.getUUID());
            pa.damageToOpposition = 10;
            var pb = io.github.sethwillemsvg.lotrrevival.events.Participation.join(ev, b.getUUID());
            pb.damageToOpposition = 1;
            o1.discard();   // the opposition is wiped out by the test
            WorldEventManager.tick(server(h));
            h.assertTrue(ev.state == WorldEvent.State.ENDED && ev.outcome == WorldEvent.Outcome.LEAD_WON && !ev.offscreen,
                    "clash outcome " + ev.state + " " + ev.outcome + " offscreen=" + ev.offscreen);
            int gotA = ReputationHooks.get().reputation(a, MORDOR) - repA, gotB = ReputationHooks.get().reputation(b, MORDOR) - repB;
            if (ReputationHooks.isFallback()) {
                h.assertTrue(gotA == 15, "participant over 20% got " + gotA);
                h.assertTrue(gotB == 0, "participant under 20% and far away got " + gotB);
            }
            // off-screen with fixed rolls (both 1d10 = 1): two Mordor warriors outweigh one Gondor soldier
            WorldEvent off = event(h, c.offset(0, 0, 6), a.getUUID());
            WorldEventManager.spawnNpc(h.getLevel(), off, WARRIOR, c.offset(0, 0, 6), EventParticipant.LEAD, "soldier");
            WorldEventManager.spawnNpc(h.getLevel(), off, WARRIOR, c.offset(1, 0, 6), EventParticipant.LEAD, "soldier");
            WorldEventManager.spawnNpc(h.getLevel(), off, SOLDIER, c.offset(2, 0, 6), EventParticipant.OPPOSITION, "soldier");
            NemesisRandom.force(0.0, 0.0);
            WorldEventManager.end(server(h), off, null, true, "test");
            h.assertTrue(off.outcome == WorldEvent.Outcome.LEAD_WON && off.offscreen, "off-screen outcome " + off.outcome);
            // TODO(spec §Acceptance event_outcomes): nemesis deaths in an off-screen loss with a fixed seed
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ payload

    @GameTest(template = "empty", batch = "events_payload")
    public static void eventsPayload(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[2];
        NemesisTestPlayers.run(h, "events_payload", () -> {
            reset(h);
            BlockPos c = h.absolutePos(P);
            ServerPlayer good = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            ServerPlayer evil = ps[1] = NemesisTestPlayers.player(h, MORDOR, P);
            WorldEvent mine = event(h, c.offset(100, 0, 0), good.getUUID());
            WorldEvent theirs = event(h, c.offset(300, 0, 0), evil.getUUID());
            event(h, c.offset(3000, 0, 0), evil.getUUID());
            List<WorldEventsPayload.Entry> list = WorldEventsPayload.build(good);
            h.assertTrue(list.size() == 2, "expected 2 events within 1024, got " + list.size());
            h.assertTrue(list.get(0).id().equals(mine.id.toString()), "not nearest first");
            h.assertTrue(list.get(0).own() && !list.get(1).own(), "own flag wrong");
            h.assertTrue(list.get(1).id().equals(theirs.id.toString()), "another player's event missing");
            for (int i = 0; i < 20; i++) event(h, c.offset(10 * i, 0, 500), UUID.randomUUID());
            h.assertTrue(WorldEventsPayload.build(good).size() == 16, "more than 16 entries");
            WorldEventManager.clearForTest(server(h));
            WorldEvent muster = new WorldEvent(UUID.randomUUID(), MusterEvent.ID, h.getLevel().dimension().location(), c, evil.getUUID(),
                    h.getLevel().getGameTime());
            muster.leadFactions.add(MORDOR);
            muster.oppFactions.add(GONDOR);
            WorldEventManager.putForTest(muster);
            String goodTitle = WorldEventsPayload.build(good).get(0).titleKey(), evilTitle = WorldEventsPayload.build(evil).get(0).titleKey();
            h.assertTrue(goodTitle.endsWith("opposition") && evilTitle.endsWith("lead"), "titles by side: good=" + goodTitle + " evil=" + evilTitle);
            // TODO(spec §Acceptance events_payload): grudge events further than 1024 blocks
        }, () -> cleanup(h, ps));
    }

    // ------------------------------------------------------------------ persistence of the ended list

    @GameTest(template = "empty", batch = "events_persistence")
    public static void endedListPersistence(GameTestHelper h) {
        NemesisTestPlayers.run(h, "events_persistence", () -> {
            reset(h);
            WorldEventSavedData d = new WorldEventSavedData();
            WorldEvent ev = new WorldEvent(UUID.randomUUID(), WarbandClashEvent.ID, h.getLevel().dimension().location(), h.absolutePos(P),
                    UUID.randomUUID(), 100);
            ev.leadFactions.add(MORDOR);
            ev.oppFactions.add(GONDOR);
            ev.outcome = WorldEvent.Outcome.LEAD_WON;
            ev.endTick = 200;
            d.addEnded(ev.summary(), 200);
            d.playerCooldownUntil.put(ev.owner, 999L);
            d.venueCooldownUntil.put("x@1,2", 888L);
            CompoundTag t = d.save(new CompoundTag(), h.getLevel().registryAccess());
            WorldEventSavedData back = WorldEventSavedData.load(t, h.getLevel().registryAccess());
            h.assertTrue(back.ended.size() == 1 && back.ended.get(0).equals(ev.summary()), "ended list changed: " + back.ended);
            h.assertTrue(back.playerCooldownUntil.get(ev.owner) == 999L && back.venueCooldownUntil.get("x@1,2") == 888L, "cooldowns lost");
        }, () -> cleanup(h));
    }
}
