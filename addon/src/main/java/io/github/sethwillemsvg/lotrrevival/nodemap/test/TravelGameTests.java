package io.github.sethwillemsvg.lotrrevival.nodemap.test;

import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.check;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.flat;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.node;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.pass;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.road;
import static io.github.sethwillemsvg.lotrrevival.nodemap.test.NodeMapTestSupport.run;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.api.NodeTravelEvent;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CombatTagTracker;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelChecks;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fast travel end to end on a fixture graph: refusals, the charge (cancel by moving, arrival, cost, Arrive event), the
 * Depart veto and companions. Mock players do not tick by themselves, so each test drives {@link TravelService#tick}.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TravelGameTests {
    private TravelGameTests() {}

    /** A at the player, D 24 blocks south by road, R 40 blocks east by road (for the rumour case). */
    private record Fixture(GraphNode a, GraphNode d, GraphNode r, NodeGraph graph) {}

    private static Fixture fixture(GameTestHelper h, String prefix) {
        Vec3 at = NodeMapTestSupport.spot(h);
        GraphNode a = node(prefix + "_a", at.x, at.z, null, false);
        GraphNode d = node(prefix + "_d", at.x, at.z + 24, null, false);
        GraphNode r = node(prefix + "_r", at.x + 40, at.z, null, false);
        return new Fixture(a, d, r, new NodeGraph(List.of(a, d, r), List.of(road(a, d), road(a, r))));
    }

    private static void know(ServerPlayer p, GraphNode... nodes) {
        NodeMapData d = NodeMapAttachment.get(p);
        for (GraphNode n : nodes) d.addKnown(n.ref().fixed());
    }

    private static void ticks(TravelService s, ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) s.tick(p);
    }

    private static String code(TravelChecks.Outcome o) {
        return o.ok() ? "ok" : o.refusal().code();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void refusals(GameTestHelper h) {
        String name = "travelRefusals";
        run(h, name, () -> {
            Fixture f = fixture(h, "ref");
            CombatTagTracker combat = new CombatTagTracker();
            NodeMapContext ctx = NodeMapTestSupport.context(f.graph(), NodeMapSettings.defaults(), NodeMapTestSupport.neutral(), combat);
            ServerPlayer p = null;
            Zombie z = null;
            List<String> seen = new ArrayList<>();
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                Vec3 at = p.position();
                know(p, f.a(), f.d());
                NodeMapData data = NodeMapAttachment.get(p);
                check(name, code(TravelChecks.check(ctx, p, f.d().ref())).equals("ok"), "baseline trip refused: " + NodeMapGameTests.describe(TravelChecks.check(ctx, p, f.d().ref())));

                p.moveTo(at.x + 200, at.y, at.z);
                String c = code(TravelChecks.check(ctx, p, f.d().ref()));
                check(name, c.equals("no_departure"), "200 blocks from any node: " + c);
                seen.add(c);
                p.moveTo(at.x, at.y, at.z);

                data.addRumoured(f.r().ref().fixed());
                c = code(TravelChecks.check(ctx, p, f.r().ref()));
                check(name, c.equals("destination_not_known"), "rumoured destination: " + c);
                seen.add(c);

                data.setCooldown(1);
                c = code(TravelChecks.check(ctx, p, f.d().ref()));
                check(name, c.equals("cooldown"), "1 tick of cooldown: " + c);
                seen.add(c);
                data.setCooldown(0);

                p.getFoodData().setFoodLevel(5);
                c = code(TravelChecks.check(ctx, p, f.d().ref()));
                check(name, c.equals("hungry"), "food 5: " + c);
                seen.add(c);
                p.getFoodData().setFoodLevel(20);

                z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(11, 1, 1));
                z.setTarget(p);
                c = code(TravelChecks.check(ctx, p, f.d().ref()));
                check(name, c.equals("hostile_nearby"), "zombie targeting at 10 blocks: " + c);
                seen.add(c);
                z.moveTo(at.x + 20, at.y, at.z);
                c = code(TravelChecks.check(ctx, p, f.d().ref()));
                check(name, c.equals("ok"), "zombie at 20 blocks should not block: " + c);
                z.moveTo(at.x + 10, at.y, at.z);

                long now = h.getLevel().getGameTime();
                z.setTarget(null);
                combat.tag(p, now - 60);
                c = code(TravelChecks.check(ctx, p, f.d().ref()));
                check(name, c.equals("combat_tag"), "hurt 3 s ago: " + c);
                seen.add(c);
                combat.tag(p, now - 120);
                c = code(TravelChecks.check(ctx, p, f.d().ref()));
                check(name, c.equals("ok"), "hurt 6 s ago should not block: " + c);

                // creative: ignores the zombie, no cooldown afterwards
                z.setTarget(p);
                p.setGameMode(GameType.CREATIVE);
                TravelService svc = new TravelService(NodeMapTestSupport.context(f.graph(), NodeMapSettings.defaults().withChargeTicks(20),
                        NodeMapTestSupport.neutral(), combat));
                check(name, svc.request(p, f.d().ref()), "creative request refused with a zombie near");
                ticks(svc, p, 20);
                check(name, svc.charge(p) == null && flat(p.position(), f.d().x(), f.d().z()) < 3, "creative trip did not arrive");
                check(name, data.cooldown() == 0, "creative got a cooldown " + data.cooldown());
                pass(h, name, "refused " + seen + "; zombie at 20 and old combat tag pass; creative travels free");
            } finally {
                if (z != null) z.discard();
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chargeCancelThenArrive(GameTestHelper h) {
        String name = "chargeCancelThenArrive";
        run(h, name, () -> {
            Fixture f = fixture(h, "chg");
            NodeMapContext ctx = NodeMapTestSupport.context(f.graph(), NodeMapTestSupport.neutral());
            TravelService svc = new TravelService(ctx);
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                Vec3 at = p.position();
                know(p, f.a(), f.d());
                NodeMapData data = NodeMapAttachment.get(p);
                float exh0 = p.getFoodData().getExhaustionLevel();

                check(name, svc.request(p, f.d().ref()), "request refused");
                ticks(svc, p, 100);
                check(name, svc.charge(p) != null, "charge ended early");
                p.moveTo(at.x + 1, at.y, at.z);
                svc.tick(p);
                check(name, svc.charge(p) == null, "moving 1 block did not cancel");
                check(name, data.cooldown() == 0 && data.trips() == 0 && flat(p.position(), at.x + 1, at.z) < 0.01, "a cancelled charge spent something");

                p.moveTo(at.x, at.y, at.z);
                check(name, svc.request(p, f.d().ref()), "second request refused");
                ticks(svc, p, 199);
                check(name, svc.charge(p) != null && NodeMapTestSupport.ARRIVALS.getOrDefault(p.getUUID(), List.of()).isEmpty(), "arrived before 200 ticks");
                svc.tick(p);
                check(name, svc.charge(p) == null, "charge still running after 200 ticks");
                check(name, flat(p.position(), f.d().x(), f.d().z()) < 3, "not at the destination: " + p.position());
                double len = f.graph().edges().get(0).length();
                int expectCd = NodeMapSettings.defaults().cost().cooldown(len);
                check(name, data.cooldown() == expectCd, "cooldown " + data.cooldown() + ", expected " + expectCd + " for L=" + len);
                float exh = p.getFoodData().getExhaustionLevel() - exh0;
                float expectExh = NodeMapSettings.defaults().cost().exhaustion(len);
                check(name, Math.abs(exh - expectExh) < 1e-4, "exhaustion +" + exh + ", expected " + expectExh);
                check(name, data.trips() == 1 && f.d().ref().equals(data.lastNode()), "trips/last node not updated: " + data);
                List<NodeTravelEvent.Arrive> arrivals = NodeMapTestSupport.ARRIVALS.getOrDefault(p.getUUID(), List.of());
                check(name, arrivals.size() == 1, arrivals.size() + " Arrive events, expected 1");
                NodeTravelEvent.Arrive ev = arrivals.get(0);
                check(name, ev.getFrom().equals(f.a().ref()) && ev.getTo().equals(f.d().ref()) && Math.abs(ev.getLength() - len) < 1e-6,
                        "Arrive event from=" + ev.getFrom() + " to=" + ev.getTo() + " L=" + ev.getLength());
                pass(h, name, "cancelled by moving; arrived L=" + Math.round(len) + " cooldown=" + data.cooldown() + " exhaustion=" + exh);
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void departVetoSpendsNothing(GameTestHelper h) {
        String name = "departVetoSpendsNothing";
        run(h, name, () -> {
            Fixture f = fixture(h, "veto");
            TravelService svc = new TravelService(NodeMapTestSupport.context(f.graph(), NodeMapSettings.defaults().withChargeTicks(10),
                    NodeMapTestSupport.neutral(), new CombatTagTracker()));
            ServerPlayer p = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                Vec3 at = p.position();
                know(p, f.a(), f.d());
                NodeMapTestSupport.VETO.add(p.getUUID());
                check(name, svc.request(p, f.d().ref()), "request refused");
                ticks(svc, p, 10);
                NodeMapData data = NodeMapAttachment.get(p);
                check(name, svc.charge(p) == null, "charge still running");
                check(name, flat(p.position(), at.x, at.z) < 0.01, "player moved despite the veto");
                check(name, data.cooldown() == 0 && data.trips() == 0, "veto spent something: " + data);
                check(name, NodeMapTestSupport.ARRIVALS.getOrDefault(p.getUUID(), List.of()).isEmpty(), "Arrive posted despite the veto");
                pass(h, name, "vetoed, nothing spent");
            } finally {
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void companionsComeAlong(GameTestHelper h) {
        String name = "companionsComeAlong";
        run(h, name, () -> {
            Fixture f = fixture(h, "comp");
            TravelService svc = new TravelService(NodeMapTestSupport.context(f.graph(), NodeMapSettings.defaults().withChargeTicks(10),
                    NodeMapTestSupport.neutral(), new CombatTagTracker()));
            ServerPlayer p = null;
            List<Entity> spawned = new ArrayList<>();
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                know(p, f.a(), f.d());
                Horse horse = h.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(1, 1, 1));
                spawned.add(horse);
                check(name, p.startRiding(horse, true), "could not mount the horse");
                Sheep led = h.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(11, 1, 1));
                spawned.add(led);
                led.setLeashedTo(p, true);
                Wolf pet = h.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(1, 1, -4));
                spawned.add(pet);
                pet.tame(p);
                pet.setOrderedToSit(false);
                Wolf sitting = h.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(-3, 1, 1));
                spawned.add(sitting);
                sitting.tame(p);
                sitting.setOrderedToSit(true);
                BlockPos fenceRel = new BlockPos(1, 1, -8);
                h.setBlock(fenceRel, Blocks.OAK_FENCE);
                Sheep tied = h.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(2, 1, -8));
                spawned.add(tied);
                LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), h.absolutePos(fenceRel));
                spawned.add(knot);
                tied.setLeashedTo(knot, true);
                Vec3 sittingAt = sitting.position(), tiedAt = tied.position();

                check(name, svc.request(p, f.d().ref()), "request refused");
                ticks(svc, p, 10);
                check(name, svc.charge(p) == null && flat(p.position(), f.d().x(), f.d().z()) < 3, "player did not arrive: " + p.position());
                check(name, p.getVehicle() == horse, "player not in the saddle on arrival");
                check(name, flat(horse.position(), f.d().x(), f.d().z()) < 3, "horse not at the destination");
                check(name, flat(led.position(), f.d().x(), f.d().z()) < 5, "led sheep did not come: " + led.position());
                check(name, flat(pet.position(), f.d().x(), f.d().z()) < 5, "standing wolf did not come: " + pet.position());
                check(name, sitting.position().distanceTo(sittingAt) < 0.01, "sitting wolf moved");
                check(name, tied.position().distanceTo(tiedAt) < 0.01, "sheep tied to a fence moved");
                pass(h, name, "horse, led sheep and standing wolf came; sitting wolf and tied sheep stayed");
            } finally {
                if (p != null && p.isPassenger()) p.stopRiding();
                spawned.forEach(Entity::discard);
                NodeMapTestSupport.remove(h, p);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void boatStaysBehind(GameTestHelper h) {
        String name = "boatStaysBehind";
        run(h, name, () -> {
            Fixture f = fixture(h, "boat");
            TravelService svc = new TravelService(NodeMapTestSupport.context(f.graph(), NodeMapSettings.defaults().withChargeTicks(10),
                    NodeMapTestSupport.neutral(), new CombatTagTracker()));
            ServerPlayer p = null;
            Boat boat = null;
            try {
                p = NodeMapTestSupport.player(h, GameType.SURVIVAL);
                know(p, f.a(), f.d());
                boat = EntityType.BOAT.create(h.getLevel());
                Vec3 at = p.position();
                boat.moveTo(at.x, at.y, at.z, 0, 0);
                h.getLevel().addFreshEntity(boat);
                check(name, p.startRiding(boat, true), "could not board the boat");
                Vec3 boatAt = boat.position();
                check(name, svc.request(p, f.d().ref()), "request refused: boat steered by the player should be allowed");
                ticks(svc, p, 10);
                check(name, svc.charge(p) == null && flat(p.position(), f.d().x(), f.d().z()) < 3, "player did not arrive");
                check(name, !p.isPassenger(), "player still riding after arrival");
                check(name, boat.position().distanceTo(boatAt) < 0.01, "boat moved");
                pass(h, name, "arrived on foot, boat stayed");
            } finally {
                if (p != null && p.isPassenger()) p.stopRiding();
                if (boat != null) boat.discard();
                NodeMapTestSupport.remove(h, p);
            }
        });
    }
}
