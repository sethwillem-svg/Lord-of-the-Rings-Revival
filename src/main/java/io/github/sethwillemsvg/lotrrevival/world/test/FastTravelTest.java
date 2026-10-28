package io.github.sethwillemsvg.lotrrevival.world.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.FastTravelEvent;
import io.github.sethwillemsvg.lotrrevival.world.travel.ArrivalFinder;
import io.github.sethwillemsvg.lotrrevival.world.travel.FastTravel;
import io.github.sethwillemsvg.lotrrevival.world.travel.TravelChecks;
import io.github.sethwillemsvg.lotrrevival.world.travel.TravelTarget;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fast travel requests, the countdown, companions and arrival. Mock players never leave the level's player list. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FastTravelTest {
    /** Blocks east of the test origin where the destination platform sits. */
    private static final int DEST_X = 24;

    private static final int DEST_Z = 24;

    private FastTravelTest() {}

    /** A fake player whose creative flag can be set (FakePlayer is survival by default). */
    private static final class TestPlayer extends FakePlayer {
        boolean creative;

        TestPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "fast-travel-test"));
        }

        @Override
        public boolean isCreative() {
            return creative;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }
    }

    private static TestPlayer player(GameTestHelper helper) {
        TestPlayer player = new TestPlayer(helper.getLevel());
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    /** Adds a custom waypoint on a fresh stone platform at the destination and returns its ref. */
    private static WaypointRef customDestination(GameTestHelper helper, ServerPlayer player) {
        BlockPos dest = destination(helper);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                helper.getLevel().setBlock(dest.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(dest.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(dest.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        TravelData data = TravelData.get(player).withNewCustom("Test Camp", dest);
        TravelData.set(player, data);
        return new WaypointRef.Custom(data.nextCustomId() - 1);
    }

    private static BlockPos destination(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(DEST_X, 1, DEST_Z));
    }

    private static void withMiddleEarth(Runnable body) {
        var previous = TravelChecks.middleEarth;
        TravelChecks.middleEarth = level -> true;
        try {
            body.run();
        } finally {
            TravelChecks.middleEarth = previous;
        }
    }

    private static void tickFully(ServerPlayer player) {
        for (int i = 0; i < 400 && FastTravel.isCountingDown(player); i++) {
            FastTravel.tick(player);
        }
    }

    private static <T extends Entity> T spawn(GameTestHelper helper, EntityType<T> type, Vec3 absolute) {
        T entity = type.create(helper.getLevel());
        entity.moveTo(absolute.x, absolute.y, absolute.z);
        helper.getLevel().getChunk(BlockPos.containing(absolute));
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    @GameTest(template = "empty")
    public static void clockBelowRequiredIsRefused(GameTestHelper helper) {
        TestPlayer player = player(helper);
        withMiddleEarth(() -> {
            WaypointRef ref = customDestination(helper, player);
            FastTravel.Result result = FastTravel.request(player, ref);
            helper.assertTrue(!result.started(), "clock 0 must be refused");
            helper.assertTrue(
                    result.reason().map(c -> c.getString().contains("must wait")).orElse(false),
                    "reason should be the wait message, got " + result.reason());
            helper.assertTrue(!FastTravel.isCountingDown(player), "no countdown after a refusal");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownAndLockedWaypointsAreRefused(GameTestHelper helper) {
        TestPlayer player = player(helper);
        withMiddleEarth(() -> {
            helper.assertTrue(
                    !FastTravel.request(player, new WaypointRef.Custom(99)).started(), "unknown custom refused");
            helper.assertTrue(
                    !FastTravel.request(player, new WaypointRef.Fixed(LOTRRevival.id("no_such_place"))).started(),
                    "unknown fixed refused");
            Optional<net.minecraft.network.chat.Component> locked =
                    FastTravel.request(player, new WaypointRef.Fixed(LOTRRevival.id("bree"))).reason();
            helper.assertTrue(
                    locked.map(c -> c.getString().contains("not discovered")).orElse(false),
                    "locked waypoint refused with its reason, got " + locked);
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void travelIsRefusedOutsideMiddleEarth(GameTestHelper helper) {
        TestPlayer player = player(helper);
        WaypointRef ref = customDestination(helper, player);
        TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
        helper.assertTrue(!FastTravel.request(player, ref).started(), "the GameTest level is not Middle-earth");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zombieTargetingBlocksTravelButNotInCreative(GameTestHelper helper) {
        TestPlayer player = player(helper);
        Zombie zombie = spawn(helper, EntityType.ZOMBIE, helper.absoluteVec(new Vec3(6.5, 1, 0.5)));
        try {
            withMiddleEarth(() -> {
                WaypointRef ref = customDestination(helper, player);
                TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
                zombie.setNoAi(true);
                zombie.setTarget(player);

                FastTravel.Result blocked = FastTravel.request(player, ref);
                helper.assertTrue(!blocked.started(), "a zombie targeting within 16 blocks must block travel");
                helper.assertTrue(
                        blocked.reason().map(c -> c.getString().contains("hunting")).orElse(false),
                        "reason should name the hunters, got " + blocked.reason());

                player.creative = true;
                helper.assertTrue(FastTravel.request(player, ref).started(), "creative players ignore targeting");
                FastTravel.cancel(player);
                player.creative = false;

                zombie.setPos(zombie.getX() + 30, zombie.getY(), zombie.getZ());
                helper.assertTrue(FastTravel.request(player, ref).started(), "a zombie 36 blocks away does not block");
                FastTravel.cancel(player);
            });
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingCancelsTheCountdown(GameTestHelper helper) {
        TestPlayer player = player(helper);
        withMiddleEarth(() -> {
            WaypointRef ref = customDestination(helper, player);
            TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
            helper.assertTrue(FastTravel.request(player, ref).started(), "request accepted");
            for (int i = 0; i < 20; i++) {
                FastTravel.tick(player);
            }
            helper.assertTrue(FastTravel.isCountingDown(player), "standing still keeps counting");
            player.setPos(player.getX() + 0.04, player.getY(), player.getZ());
            FastTravel.tick(player);
            helper.assertTrue(FastTravel.isCountingDown(player), "0.04 blocks is within tolerance");
            player.setPos(player.getX() + 1.0, player.getY(), player.getZ());
            FastTravel.tick(player);
            helper.assertTrue(!FastTravel.isCountingDown(player), "moving 1 block cancels");
            helper.assertTrue(
                    TravelData.get(player).clock() == TravelData.MAX_CLOCK, "a cancelled trip spends no time");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aNewRequestRestartsTheCountdown(GameTestHelper helper) {
        TestPlayer player = player(helper);
        withMiddleEarth(() -> {
            WaypointRef ref = customDestination(helper, player);
            TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
            FastTravel.request(player, ref);
            for (int i = 0; i < 50; i++) {
                FastTravel.tick(player);
            }
            int before = FastTravel.ticksLeft(player);
            FastTravel.request(player, ref);
            helper.assertTrue(
                    FastTravel.ticksLeft(player) == 200 && before < 200, "a new request starts a fresh countdown");
            FastTravel.cancel(player);
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void completedTripMovesCompanionsAndRecordsIt(GameTestHelper helper) {
        TestPlayer player = player(helper);
        List<Entity> spawned = new ArrayList<>();
        AtomicInteger resyncs = new AtomicInteger();
        Consumer<ServerPlayer> resync = p -> resyncs.incrementAndGet();
        FastTravel.addResyncListener(resync);
        try {
            withMiddleEarth(() -> {
                WaypointRef ref = customDestination(helper, player);
                TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));

                Wolf standing = spawn(helper, EntityType.WOLF, helper.absoluteVec(new Vec3(4.5, 1, 0.5)));
                Wolf sitting = spawn(helper, EntityType.WOLF, helper.absoluteVec(new Vec3(4.5, 1, 2.5)));
                Wolf stranger = spawn(helper, EntityType.WOLF, helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
                Sheep leashed = spawn(helper, EntityType.SHEEP, helper.absoluteVec(new Vec3(-4.5, 1, 0.5)));
                Sheep loose = spawn(helper, EntityType.SHEEP, helper.absoluteVec(new Vec3(-4.5, 1, 3.5)));
                Vec3 farPos = helper.absoluteVec(new Vec3(300.5, 1, 0.5));
                Sheep far = spawn(helper, EntityType.SHEEP, farPos);
                spawned.addAll(List.of(standing, sitting, stranger, leashed, loose, far));
                for (Wolf wolf : List.of(standing, sitting)) {
                    wolf.setTame(true, false);
                    wolf.setOwnerUUID(player.getUUID());
                }
                sitting.setOrderedToSit(true);
                stranger.setTame(true, false);
                stranger.setOwnerUUID(UUID.randomUUID());
                leashed.setLeashedTo(player, false);
                far.setLeashedTo(player, false);
                Vec3 sittingBefore = sitting.position();
                Vec3 strangerBefore = stranger.position();
                Vec3 looseBefore = loose.position();

                FastTravel.request(player, ref);
                tickFully(player);

                BlockPos dest = destination(helper);
                Vec3 expected = new Vec3(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
                helper.assertTrue(
                        player.position().distanceTo(expected) < 0.01,
                        "player should stand at the custom waypoint " + expected + ", is at " + player.position());
                helper.assertTrue(nearPlayer(player, standing, 4.5), "standing wolf comes along");
                helper.assertTrue(nearPlayer(player, leashed, 4.5), "leashed sheep comes along");
                helper.assertTrue(leashed.getLeashHolder() == player, "the lead stays attached");
                helper.assertTrue(sitting.position().equals(sittingBefore), "a sitting wolf stays");
                helper.assertTrue(stranger.position().equals(strangerBefore), "someone else's wolf stays");
                helper.assertTrue(loose.position().equals(looseBefore), "an unleashed sheep stays");
                helper.assertTrue(far.position().distanceTo(farPos) < 0.01, "a leashed sheep 300 blocks away stays");

                TravelData data = TravelData.get(player);
                helper.assertTrue(data.clock() == 0, "clock resets, is " + data.clock());
                helper.assertTrue(data.custom().get(0).uses() == 1, "uses go up by one");
                helper.assertTrue(
                        data.last().equals(Optional.of(ref)), "last waypoint is set, is " + data.last());
                helper.assertTrue(resyncs.get() == 1, "the resync hook runs once, ran " + resyncs.get());
            });
        } finally {
            FastTravel.removeResyncListener(resync);
            spawned.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrivalPostsTheTravelEvent(GameTestHelper helper) {
        TestPlayer player = player(helper);
        AtomicInteger events = new AtomicInteger();
        AtomicInteger customFlag = new AtomicInteger();
        Consumer<FastTravelEvent> listener = event -> {
            events.incrementAndGet();
            if (event.isCustom() && event.getHorizontalDistance() > 20) {
                customFlag.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            withMiddleEarth(() -> {
                WaypointRef ref = customDestination(helper, player);
                TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
                FastTravel.request(player, ref);
                tickFully(player);
            });
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.assertTrue(events.get() == 1, "one FastTravelEvent, got " + events.get());
        helper.assertTrue(customFlag.get() == 1, "event carries the custom flag and a real distance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void mountedPlayerArrivesMounted(GameTestHelper helper) {
        ServerPlayer player = mockRider(helper);
        Boat horse = spawn(helper, EntityType.BOAT, helper.absoluteVec(new Vec3(0.5, 1, 0.5)));
        try {
            withMiddleEarth(() -> {
                WaypointRef ref = customDestination(helper, player);
                TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
                player.startRiding(horse, true);
                helper.assertTrue(player.getVehicle() == horse, "mounted before the trip");

                helper.assertTrue(FastTravel.request(player, ref).started(), "a mounted request is accepted");
                tickFully(player);

                BlockPos dest = destination(helper);
                helper.assertTrue(player.getVehicle() == horse, "still riding after arrival");
                helper.assertTrue(
                        horse.position().distanceTo(new Vec3(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5)) < 0.01,
                        "the mount arrives at the waypoint, is at " + horse.position());
                helper.assertTrue(TravelData.get(player).clock() == 0, "trip recorded");
            });
        } finally {
            player.stopRiding();
            horse.discard();
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** FakePlayer refuses to ride, so mount tests use the helper's mock (always creative, always removed in finally). */
    @SuppressWarnings("removal")
    private static ServerPlayer mockRider(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    @GameTest(template = "empty")
    public static void mountingDuringTheCountdownCancelsIt(GameTestHelper helper) {
        ServerPlayer player = mockRider(helper);
        Boat horse = spawn(helper, EntityType.BOAT, helper.absoluteVec(new Vec3(0.5, 1, 0.5)));
        try {
            withMiddleEarth(() -> {
                WaypointRef ref = customDestination(helper, player);
                TravelData.set(player, TravelData.get(player).withClock(TravelData.MAX_CLOCK));
                FastTravel.request(player, ref);
                player.startRiding(horse, true);
                FastTravel.tick(player);
                helper.assertTrue(!FastTravel.isCountingDown(player), "getting on a mount cancels the countdown");
            });
        } finally {
            player.stopRiding();
            horse.discard();
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fallbackColumnsRunNearestFirst(GameTestHelper helper) {
        List<int[]> order = ArrivalFinder.columnOrder(ArrivalFinder.SEARCH_RADIUS);
        helper.assertTrue(order.size() == 25, "5 x 5 columns");
        helper.assertTrue(order.get(0)[0] == 0 && order.get(0)[1] == 0, "the waypoint column comes first");
        int last = -1;
        for (int[] o : order) {
            int d = o[0] * o[0] + o[1] * o[1];
            helper.assertTrue(d >= last, "columns are ordered by distance");
            last = d;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsafeStoredSpotFallsBackToTheColumnTop(GameTestHelper helper) {
        TestPlayer player = player(helper);
        BlockPos dest = destination(helper);
        WaypointRef ref = customDestination(helper, player);
        // Bury the stored spot: solid stone where the traveller's feet would go.
        helper.getLevel().setBlock(dest, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(dest.above(), Blocks.STONE.defaultBlockState(), 3);
        TravelTarget target = TravelTarget.resolve(player, ref).orElseThrow();
        helper.assertTrue(!ArrivalFinder.isSafe(helper.getLevel(), dest), "the stored spot is buried");
        Vec3 spot = ArrivalFinder.find(helper.getLevel(), target);
        BlockPos feet = BlockPos.containing(spot);
        helper.assertTrue(ArrivalFinder.isSafe(helper.getLevel(), feet), "the arrival spot is safe, is " + feet);
        helper.assertTrue(
                Math.abs(feet.getX() - dest.getX()) <= 2 && Math.abs(feet.getZ() - dest.getZ()) <= 2,
                "within the 5 x 5 area, is " + feet);
        helper.succeed();
    }

    private static boolean nearPlayer(ServerPlayer player, Entity entity, double within) {
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        return Math.abs(dx) <= within && Math.abs(dz) <= within;
    }
}
