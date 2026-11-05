package io.github.sethwillemsvg.lotrrevival.nemesis.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.NpcParry;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.EventChunkTickets;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventManager;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventSavedData;
import io.github.sethwillemsvg.lotrrevival.events.network.WorldEventsPayload;
import io.github.sethwillemsvg.lotrrevival.events.types.AmbushEvent;
import io.github.sethwillemsvg.lotrrevival.nemesis.Ambush;
import io.github.sethwillemsvg.lotrrevival.nemesis.BodyguardGoals;
import io.github.sethwillemsvg.lotrrevival.nemesis.LeaderGear;
import io.github.sethwillemsvg.lotrrevival.nemesis.Manifestation;
import io.github.sethwillemsvg.lotrrevival.nemesis.MeRanks;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisConfig;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisFactory;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRecord;
import io.github.sethwillemsvg.lotrrevival.nemesis.Rank;
import io.github.sethwillemsvg.lotrrevival.nemesis.Renown;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcType;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcTypeLookup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.GONDOR;
import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.MORDOR;
import static io.github.sethwillemsvg.lotrrevival.nemesis.test.NemesisTestPlayers.me;

/**
 * Round-2 nemesis tests (specs/addon/nemesis-events.md, tickets N6 and N8): leader gear re-roll and mounts, the bodyguard follow
 * and defend goals, and the ambush run as the {@code ambush} world-event type. One batch per test.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NemesisLeaderTests {
    private static final ResourceLocation WARRIOR = me("mordor.warrior");
    private static final BlockPos P = new BlockPos(1, 1, 1);

    private NemesisLeaderTests() {}

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static NemesisRecord record(GameTestHelper h, Rank rank) {
        return record(h, rank, WARRIOR);
    }

    private static NemesisRecord record(GameTestHelper h, Rank rank, ResourceLocation type) {
        NemesisRecord r = NemesisFactory.create(h.getLevel(), MORDOR, type, NemesisFactory.raceOfType(h.getLevel(), type));
        r.renown = rank.threshold();
        Renown.setRank(server(h), r, rank, "test");
        return r;
    }

    private static NpcEntity body(GameTestHelper h, NemesisRecord r, BlockPos rel, boolean ai) {
        NpcEntity e = Manifestation.spawn(h.getLevel(), r, h.absolutePos(rel), "test");
        h.assertTrue(e != null, "body of " + r.shortId() + " (" + r.rank + ") did not spawn");
        if (!ai) e.setNoAi(true);
        return e;
    }

    private static Map<EquipmentSlot, ItemStack> worn(NpcEntity e) {
        Map<EquipmentSlot, ItemStack> m = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (s == EquipmentSlot.BODY) continue;
            ItemStack st = e.getItemBySlot(s);
            if (!st.isEmpty()) m.put(s, st.copy());
        }
        return m;
    }

    private static boolean sameGear(Map<EquipmentSlot, ItemStack> a, Map<EquipmentSlot, ItemStack> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (var en : a.entrySet()) if (!ItemStack.isSameItemSameComponents(en.getValue(), b.get(en.getKey()))) return false;
        return true;
    }

    private static String gear(Map<EquipmentSlot, ItemStack> m) {
        List<String> out = new ArrayList<>();
        m.forEach((s, st) -> out.add(s.getName() + "=" + BuiltInRegistries.ITEM.getKey(st.getItem())));
        return out.toString();
    }

    // ------------------------------------------------------------------ N6: leader gear and mounts

    @GameTest(template = "empty", batch = "nemesis_leader_gear")
    public static void gearReroll(GameTestHelper h) {
        NemesisTestPlayers.run(h, "gear_reroll", () -> {
            var f = Sides.faction(h.getLevel(), MORDOR);
            h.assertTrue(f != null, "no Mordor faction");
            // a Marked nemesis keeps the gear it rose with
            NemesisRecord marked = record(h, Rank.MARKED);
            NpcEntity m = body(h, marked, P, false);
            h.assertFalse(marked.gearRerolled, "a Marked nemesis re-rolled its gear");
            h.assertTrue(sameGear(marked.gear, worn(m)), "Marked body " + gear(worn(m)) + " != record " + gear(marked.gear));
            // a Commander re-rolls once from the LEADER (else VETERAN) gear of its race
            NemesisRecord cmd = record(h, Rank.COMMANDER);
            cmd.gear.put(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));   // what it "rose with"
            LeaderGear.lastGearType = null;
            NpcEntity c = body(h, cmd, new BlockPos(3, 1, 1), false);
            h.assertTrue(cmd.gearRerolled, "Commander gear not re-rolled");
            h.assertTrue(LeaderGear.lastGearType != null, "no LEADER or VETERAN type with gear in Mordor's pools "
                    + "(LEADER " + MeRanks.pool(f, NpcRank.LEADER) + ", VETERAN " + MeRanks.pool(f, NpcRank.VETERAN) + ")");
            h.assertTrue(LeaderGear.lastGearRank == NpcRank.LEADER || LeaderGear.lastGearRank == NpcRank.VETERAN,
                    "gear from rank " + LeaderGear.lastGearRank);
            h.assertTrue(MeRanks.pool(f, LeaderGear.lastGearRank).contains(LeaderGear.lastGearType),
                    LeaderGear.lastGearType + " is not in Mordor's " + LeaderGear.lastGearRank + " pool");
            // Mordor's warriors are orcs, its captains and veterans uruks: an orc commander falls back to any race's LEADER gear
            boolean orcLeader = LeaderGearTestView.hasGearOfRace(h, f, NpcRank.LEADER, WARRIOR);
            boolean orcVeteran = LeaderGearTestView.hasGearOfRace(h, f, NpcRank.VETERAN, WARRIOR);
            NpcRank expected = orcLeader ? NpcRank.LEADER : orcVeteran ? NpcRank.VETERAN : NpcRank.LEADER;
            h.assertTrue(LeaderGear.lastGearSameRace == (orcLeader || orcVeteran) && LeaderGear.lastGearRank == expected,
                    "warrior commander: gear from " + LeaderGear.lastGearRank + " " + LeaderGear.lastGearType + " same_race="
                            + LeaderGear.lastGearSameRace + ", expected " + expected + " same_race=" + (orcLeader || orcVeteran));
            Map<EquipmentSlot, ItemStack> rolled = worn(c);
            h.assertTrue(sameGear(cmd.gear, rolled), "record gear " + gear(cmd.gear) + " != body " + gear(rolled));
            LotrLog.info("test", "gear_reroll: {} from {} {} -> {}", cmd.shortId(), LeaderGear.lastGearRank, LeaderGear.lastGearType, gear(rolled));
            // kept on the next manifestation: no second roll
            Manifestation.retreat(server(h), cmd, "test");
            LeaderGear.lastGearType = null;
            NpcEntity c2 = body(h, cmd, new BlockPos(3, 1, 1), false);
            h.assertTrue(LeaderGear.lastGearType == null, "gear re-rolled a second time from " + LeaderGear.lastGearType);
            h.assertTrue(sameGear(rolled, worn(c2)), "second body " + gear(worn(c2)) + " != first " + gear(rolled));
            // a veteran (uruk) commander finds same-race LEADER gear (the uruk captain) when Mordor has it
            ResourceLocation veteranType = me("mordor.veteran");
            if (LeaderGearTestView.hasGearOfRace(h, f, NpcRank.LEADER, veteranType)) {
                NemesisRecord vet = record(h, Rank.COMMANDER, veteranType);
                LeaderGear.lastGearType = null;
                body(h, vet, new BlockPos(7, 1, 1), false);
                h.assertTrue(LeaderGear.lastGearSameRace && LeaderGear.lastGearRank == NpcRank.LEADER,
                        "uruk commander: gear from " + LeaderGear.lastGearRank + " " + LeaderGear.lastGearType + " same_race="
                                + LeaderGear.lastGearSameRace + ", expected a same-race LEADER type");
                Manifestation.retreat(server(h), vet, "test");
            } else {
                LotrLog.info("test", "gear_reroll: Mordor has no LEADER type of the veteran's race with gear; same-race case skipped");
            }
            // mounts: none below Warlord; a Warlord rides when its type or a LEADER type of Mordor has mount data
            h.assertTrue(c2.getVehicle() == null, "a Commander rides " + (c2.getVehicle() == null ? "" : c2.getVehicle().getType().toShortString()));
            NemesisRecord war = record(h, Rank.WARLORD);
            NpcEntity w = body(h, war, new BlockPos(5, 1, 1), false);
            boolean anyMount = LeaderGearTestView.typeHasMount(h, WARRIOR);
            for (ResourceLocation id : MeRanks.pool(f, NpcRank.LEADER)) anyMount |= LeaderGearTestView.typeHasMount(h, id);
            LotrLog.info("test", "gear_reroll: Warlord {} mount data available={} rides={}", war.shortId(), anyMount,
                    w.getVehicle() == null ? "nothing" : w.getVehicle().getType().toShortString());
            h.assertTrue(anyMount == (w.getVehicle() != null), anyMount ? "a Warlord with mount data available rides nothing"
                    : "a Warlord rides although no mount data exists");
            if (w.getVehicle() != null) {
                var mount = w.getVehicle();
                h.assertTrue(mount.getTags().contains(LeaderGear.MOUNT_TAG), "mount not tagged");
                Manifestation.retreat(server(h), war, "test");
                h.assertTrue(mount.isRemoved(), "the mount stayed after its nemesis retreated");
            }
        }, () -> NemesisTestPlayers.clear(server(h)));
    }

    /** Small lookups of Middle-earth NPC type data for the assertions above. */
    private static final class LeaderGearTestView {
        static boolean typeHasMount(GameTestHelper h, ResourceLocation id) {
            NpcType t = NpcTypeLookup.getNpcType(h.getLevel(), id);
            return t != null && t.hasMount();
        }

        /** A type of this rank pool, of the same race as {@code type}, with gear. */
        static boolean hasGearOfRace(GameTestHelper h, net.sevenstars.middleearth.resources.datas.factions.Faction f, NpcRank pool,
                                     ResourceLocation type) {
            NpcType own = NpcTypeLookup.getNpcType(h.getLevel(), type);
            if (own == null) return false;
            for (ResourceLocation id : MeRanks.pool(f, pool)) {
                NpcType t = NpcTypeLookup.getNpcType(h.getLevel(), id);
                if (t != null && own.getRace().equals(t.getRace()) && t.getGear() != null) return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ N6: bodyguards follow and defend

    @GameTest(template = "empty", batch = "nemesis_bodyguards", timeoutTicks = 600)
    public static void bodyguardFollow(GameTestHelper h) {
        String name = "bodyguard_follow";
        NemesisTestPlayers.begin(h, name);
        NemesisRecord r;
        NpcEntity e;
        List<NpcEntity> guards;
        int follow = NemesisConfig.i(NemesisConfig.GUARD_FOLLOW_DISTANCE);
        try {
            r = record(h, Rank.CAPTAIN);
            e = body(h, r, P, false);
            var b = Manifestation.body(r.id);
            h.assertTrue(b != null && b.guards.size() == Rank.CAPTAIN.bodyguards, "bodyguards " + (b == null ? -1 : b.guards.size()));
            guards = new ArrayList<>(b.guards);
            for (NpcEntity g : guards) {
                boolean hasFollow = false, hasDefend = false;
                for (WrappedGoal w : g.goalSelector.getAvailableGoals()) hasFollow |= w.getGoal() instanceof BodyguardGoals.Follow;
                for (WrappedGoal w : g.targetSelector.getAvailableGoals()) hasDefend |= w.getGoal() instanceof BodyguardGoals.Defend;
                h.assertTrue(hasFollow && hasDefend, "guard " + LotrLog.id(g.getUUID()) + " follow=" + hasFollow + " defend=" + hasDefend);
                NpcParry.chanceOverride(g, 0.0);
            }
            // the nemesis walks off (moved 12 blocks, onto the ground there): its guards must catch up
            BlockPos to = h.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.blockPosition().offset(12, 0, 0));
            e.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
            for (NpcEntity g : guards) g.setTarget(null);
            double far = 0;
            for (NpcEntity g : guards) far = Math.max(far, g.distanceTo(e));
            LotrLog.info("test", "{}: nemesis moved to {}, farthest guard {} blocks", name, e.blockPosition().toShortString(), String.format("%.1f", far));
            h.assertTrue(far > follow, "guards already within " + follow + " after the move (" + far + ")");
        } catch (RuntimeException ex) {
            NemesisTestPlayers.fail(name, ex);
            NemesisTestPlayers.clear(server(h));
            throw ex;
        }
        NpcEntity body = e;
        List<NpcEntity> gs = guards;
        Zombie[] attacker = new Zombie[1];
        long start = h.getLevel().getGameTime();
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (NpcEntity g : gs) {
                        h.assertTrue(g.isAlive(), "a guard died");
                        if (g.distanceTo(body) > follow + 1) {
                            String state = guardState(g, body, h.getLevel().getGameTime() - start);
                            long waited = h.getLevel().getGameTime() - start;
                            if (waited % 100 == 0) LotrLog.info("test", "{}: waiting: {}", name, state);
                            h.fail(state);
                        }
                    }
                })
                .thenExecute(() -> {
                    LotrLog.info("test", "{}: every guard within {} of the nemesis", name, follow + 1);
                    Zombie z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, h.relativePos(body.blockPosition()).offset(2, 0, 2));
                    z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));   // no burning in daylight
                    attacker[0] = z;
                    body.setLastHurtByMob(z);
                })
                .thenWaitUntil(() -> {
                    Zombie z = attacker[0];
                    boolean defended = false;
                    for (NpcEntity g : gs) defended |= g.getTarget() == z || z.getLastHurtByMob() == g;
                    h.assertTrue(defended, "no guard turned on the nemesis's attacker (defends counted " + BodyguardGoals.defends + ")");
                })
                .thenExecute(() -> {
                    NemesisTestPlayers.pass(name);
                    NemesisTestPlayers.clear(server(h));
                    if (attacker[0] != null) attacker[0].discard();
                })
                .thenSucceed();
    }

    /** Why a guard is not where it should be: distance, AI, target, running goals and its path. */
    private static String guardState(NpcEntity g, NpcEntity leader, long waited) {
        List<String> running = new ArrayList<>();
        String nav = "-";
        for (WrappedGoal w : g.goalSelector.getAvailableGoals()) {
            if (w.isRunning()) running.add(w.getGoal().getClass().getSimpleName());
            if (w.getGoal() instanceof BodyguardGoals.Follow f) nav = f.lastNav;
        }
        for (WrappedGoal w : g.targetSelector.getAvailableGoals()) if (w.isRunning()) running.add(w.getGoal().getClass().getSimpleName());
        var path = g.getNavigation().getPath();
        return "guard " + LotrLog.id(g.getUUID()) + " is " + String.format("%.1f", g.distanceTo(leader)) + " from its nemesis after "
                + waited + " ticks: guard at " + g.blockPosition().toShortString() + " nemesis at " + leader.blockPosition().toShortString()
                + " no_ai=" + g.isNoAi() + " passenger=" + g.isPassenger() + " target=" + (g.getTarget() == null ? "-"
                : g.getTarget().getType().toShortString() + ":" + LotrLog.id(g.getTarget().getUUID())) + " running=" + running
                + " follow_nav=" + nav + " nav_done=" + g.getNavigation().isDone() + " path=" + (path == null ? "none"
                : (path.canReach() ? "reaches" : "cannot reach") + " " + path.getNextNodeIndex() + "/" + path.getNodeCount())
                + " speed=" + String.format("%.3f", g.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
    }

    // ------------------------------------------------------------------ N8: ambush as a world event

    @GameTest(template = "empty", batch = "nemesis_ambush_event", timeoutTicks = 60)
    public static void ambushEvent(GameTestHelper h) {
        ServerPlayer[] ps = new ServerPlayer[1];
        NemesisTestPlayers.run(h, "ambush_event", () -> {
            h.assertTrue(WorldEventManager.TYPES.get(AmbushEvent.ID) == AmbushEvent.INSTANCE, "ambush not a registered event type: "
                    + WorldEventManager.TYPES.keySet());
            h.assertTrue(Ambush.stager != null, "the events module did not hook the ambush stager");
            ServerPlayer p = ps[0] = NemesisTestPlayers.player(h, GONDOR, P);
            long cooldownBefore = WorldEventSavedData.get(server(h)).playerCooldownUntil.getOrDefault(p.getUUID(), -1L);
            // 1. the nemesis dies: the opposition (the player) wins
            NemesisRecord r = record(h, Rank.CAPTAIN);
            r.addGrudge(p.getUUID(), 3, h.getLevel().getGameTime());
            NpcEntity body = Ambush.start(p, r);
            h.assertTrue(body != null, "forced ambush did not spawn (no spot near " + p.blockPosition().toShortString() + "?)");
            h.assertTrue(r.eventId != null, "the ambush has no world event");
            WorldEvent ev = WorldEventManager.get(r.eventId);
            h.assertTrue(ev != null && ev.type.equals(AmbushEvent.ID), "event " + (ev == null ? "missing" : ev.type));
            h.assertTrue(ev.owner.equals(p.getUUID()), "event owner is not the target");
            h.assertTrue(ev.nemeses.containsKey(r.id), "the nemesis is not on the lead side");
            h.assertTrue(ev.npcs.size() == 1 + Rank.CAPTAIN.bodyguards, "event npcs " + ev.npcs.size() + " != nemesis + bodyguards");
            h.assertTrue(EventChunkTickets.forcedFor(ev.id) == 0, "an ambush forced " + EventChunkTickets.forcedFor(ev.id) + " chunks");
            h.assertTrue(WorldEventManager.countFor(p.getUUID()) == 0, "the ambush counts as the player's scheduled event");
            boolean marker = false;
            for (var en : WorldEventsPayload.build(p)) marker |= en.id().equals(ev.id.toString()) && en.own();
            h.assertTrue(marker, "no own marker for the ambush event");
            h.assertTrue(ev.deadline - ev.startTick <= 300 * 20L, "ambush runs " + (ev.deadline - ev.startTick) / 20 + " s");
            NpcParry.chanceOverride(body, 0.0);
            NemesisRandom.force(0.99, 0.99);   // no escape, no cheat-death
            body.hurt(body.damageSources().playerAttack(p), 10000);
            h.assertTrue(!body.isAlive(), "the kill did not land");
            WorldEventManager.tick(server(h));
            h.assertTrue(ev.state == WorldEvent.State.ENDED && ev.outcome == WorldEvent.Outcome.OPPOSITION_WON && !ev.offscreen,
                    "after the nemesis died: state=" + ev.state + " outcome=" + ev.outcome + " offscreen=" + ev.offscreen);
            boolean listed = false;
            for (WorldEvent.Summary s : WorldEventSavedData.get(server(h)).ended) listed |= s.id().equals(ev.id);
            h.assertTrue(listed, "ended ambush not in the ended-events list");
            h.assertTrue(WorldEventSavedData.get(server(h)).playerCooldownUntil.getOrDefault(p.getUUID(), -1L) == cooldownBefore,
                    "the ambush started the scheduled-event cooldown");
            // 2. the target is killed: the lead wins, without the "won an event" renown (the kill pays its own)
            NemesisRecord r2 = record(h, Rank.CAPTAIN);
            r2.addGrudge(p.getUUID(), 3, h.getLevel().getGameTime());
            NpcEntity body2 = Ambush.start(p, r2);
            h.assertTrue(body2 != null && r2.eventId != null, "second ambush did not start as an event");
            WorldEvent ev2 = WorldEventManager.get(r2.eventId);
            int renown = r2.renown;
            Ambush.onAmbushEnded(server(h), r2, p.getUUID(), false);   // what the kill listener does when the target dies
            WorldEventManager.tick(server(h));
            h.assertTrue(ev2.state == WorldEvent.State.ENDED && ev2.outcome == WorldEvent.Outcome.LEAD_WON,
                    "after the target died: state=" + ev2.state + " outcome=" + ev2.outcome);
            h.assertTrue(r2.renown == renown, "event win renown paid for an ambush: " + renown + " -> " + r2.renown);
            h.assertTrue(!Ambush.ENDED.containsKey(r.id) && !Ambush.ENDED.containsKey(r2.id), "ambush results left behind");
        }, () -> {
            WorldEventManager.clearForTest(server(h));
            NemesisTestPlayers.remove(ps[0]);
            NemesisTestPlayers.clear(server(h));
        });
    }
}
