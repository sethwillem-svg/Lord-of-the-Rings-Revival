package io.github.sethwillemsvg.lotrrevival.world.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.building.GoldRingItems;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthLanding;
import io.github.sethwillemsvg.lotrrevival.world.portal.ContactTracker;
import io.github.sethwillemsvg.lotrrevival.world.portal.ReentryGuard;
import io.github.sethwillemsvg.lotrrevival.world.portal.RingPortalData;
import io.github.sethwillemsvg.lotrrevival.world.portal.RingPortalEntity;
import io.github.sethwillemsvg.lotrrevival.world.portal.RingPortalPlan;
import io.github.sethwillemsvg.lotrrevival.world.portal.RingPortalTravel;
import io.github.sethwillemsvg.lotrrevival.world.portal.RingPortalTypes;
import io.github.sethwillemsvg.lotrrevival.world.portal.RingPortals;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ring portal: the catalyst, the once-per-world flag, contact timing and the crossing plans. The GameTest server
 * has no Middle-earth level, so the crossing itself is tested through injectable inputs.
 */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RingPortalTest {
    private static final MiddleEarthLanding.Point LANDING = new MiddleEarthLanding.Point(1000, -2000);

    private RingPortalTest() {}

    private static List<RingPortalEntity> portalsNear(ServerLevel level, Vec3 centre) {
        return level.getEntitiesOfClass(RingPortalEntity.class, new AABB(centre, centre).inflate(8.0));
    }

    private static ItemEntity throwRing(ServerLevel level, Vec3 at) {
        ItemEntity ring = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(GoldRingItems.GOLD_RING.get()));
        ring.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(ring);
        return ring;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ringInFireOpensOnePortalOnceUntilReset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RingPortalData previous = RingPortals.data(level);
        RingPortals.setData(level, RingPortalData.EMPTY);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.NETHERRACK);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.FIRE);
        Vec3 fire = helper.absoluteVec(new Vec3(2.5, 3.0, 2.5));
        // Mock players start at the world spawn, far from the test.
        player.setPos(fire.x + 2.0, fire.y, fire.z);
        Runnable cleanUp = () -> {
            portalsNear(level, fire).forEach(Entity::discard);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(fire, fire).inflate(8.0)).forEach(Entity::discard);
            RingPortals.setData(level, previous);
            level.getServer().getPlayerList().remove(player);
        };
        try {
            throwRing(level, fire);
            helper.runAfterDelay(4, () -> {
                try {
                    helper.assertTrue(RingPortals.data(level).made(), "The flag must be set");
                    List<RingPortalEntity> first = portalsNear(level, fire);
                    helper.assertTrue(first.size() == 1, "Expected one portal, got " + first.size());
                    RingPortalEntity portal = first.get(0);
                    helper.assertTrue(
                            Math.abs(portal.getY() - (Math.floor(fire.y) + 1.5)) < 1.0e-6,
                            "Hit box bottom is 1.5 above the fire block");
                    helper.assertTrue(
                            RingPortals.data(level).overworldPortal().isPresent()
                                    && RingPortals.data(level).overworldEntity().equals(Optional.of(portal.getUUID())),
                            "The portal must be stored");
                    helper.assertTrue(
                            level.getEntitiesOfClass(ItemEntity.class, new AABB(fire, fire).inflate(4.0)).isEmpty(),
                            "The ring must be consumed");

                    // A second ring does nothing and stays.
                    throwRing(level, fire);
                    helper.runAfterDelay(4, () -> {
                        try {
                            helper.assertTrue(portalsNear(level, fire).size() == 1, "A second ring made a portal");
                            helper.assertTrue(
                                    level.getEntitiesOfClass(ItemEntity.class, new AABB(fire, fire).inflate(4.0))
                                                    .size()
                                            == 1,
                                    "The second ring must stay");

                            // Reset allows another.
                            RingPortals.setData(level, RingPortals.data(level).reset());
                            helper.runAfterDelay(4, () -> {
                                try {
                                    helper.assertTrue(
                                            portalsNear(level, fire).size() == 2,
                                            "Reset must allow another portal, got " + portalsNear(level, fire).size());
                                    helper.assertTrue(RingPortals.data(level).made(), "Flag set again");
                                    cleanUp.run();
                                    helper.succeed();
                                } catch (RuntimeException e) {
                                    cleanUp.run();
                                    throw e;
                                }
                            });
                        } catch (RuntimeException e) {
                            cleanUp.run();
                            throw e;
                        }
                    });
                } catch (RuntimeException e) {
                    cleanUp.run();
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            cleanUp.run();
            throw e;
        }
    }

    @GameTest(template = "empty")
    public static void ringWithoutFireDoesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RingPortalData previous = RingPortals.data(level);
        RingPortals.setData(level, RingPortalData.EMPTY);
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 3.0, 2.5));
        ItemEntity ring = null;
        try {
            // No fire here.
            helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
            ring = throwRing(level, spot);
            helper.assertTrue(!RingPortals.tryOpen(ring), "No fire, no portal");
            helper.assertTrue(!RingPortals.data(level).made(), "Flag must stay clear");
            helper.succeed();
        } finally {
            if (ring != null) {
                ring.discard();
            }
            RingPortals.setData(level, previous);
        }
    }

    @GameTest(template = "empty")
    public static void contactNeeds80TicksAndResetsOnLeaving(GameTestHelper helper) {
        ContactTracker tracker = new ContactTracker();
        UUID player = UUID.randomUUID();
        UUID mob = UUID.randomUUID();
        for (int tick = 1; tick <= 79; tick++) {
            helper.assertTrue(tracker.tick(Map.of(player, 80)).isEmpty(), "Sent early at tick " + tick);
        }
        helper.assertTrue(tracker.tick(Map.of(player, 80)).equals(List.of(player)), "Sent at tick 80");
        helper.assertTrue(tracker.contactOf(player) == 0, "Counter clears after the trip");

        for (int tick = 0; tick < 50; tick++) {
            tracker.tick(Map.of(player, 80));
        }
        tracker.tick(Map.of()); // steps out
        helper.assertTrue(tracker.contactOf(player) == 0, "Leaving resets the counter");
        for (int tick = 1; tick <= 79; tick++) {
            helper.assertTrue(tracker.tick(Map.of(player, 80)).isEmpty(), "Counter did not reset");
        }
        helper.assertTrue(tracker.tick(Map.of(mob, 1)).equals(List.of(mob)), "Mobs cross at once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reentryGuardIgnoresArrivalsUntilTheyStopTouching(GameTestHelper helper) {
        ReentryGuard guard = new ReentryGuard(60);
        UUID id = UUID.randomUUID();
        helper.assertTrue(!guard.blocks(id, 0), "Unknown entities are not blocked");
        guard.mark(id, 100);
        // Still touching for a long time: always blocked, the period keeps restarting.
        for (long now = 101; now < 400; now++) {
            helper.assertTrue(guard.blocks(id, now), "Blocked while touching at " + now);
        }
        // Stops touching for 60 ticks, then may be used again.
        helper.assertTrue(!guard.blocks(id, 400 + 61), "Free after 60 quiet ticks");
        helper.assertTrue(guard.size() == 0, "Entry removed");

        guard.mark(id, 0);
        guard.purge(1000);
        helper.assertTrue(guard.size() == 0, "Purge drops expired entries");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plansPreferTwinAndFallBack(GameTestHelper helper) {
        // Before any twin: 4 south of the landing point.
        RingPortalPlan.Arrival first = RingPortalPlan.middleEarthArrival(Optional.empty(), LANDING);
        helper.assertTrue(
                first.x() == 1000 && first.z() == -2000 + 4 && first.resolveHeight(), "Landing arrival " + first);
        // With a twin: 4 south of the twin.
        RingPortalPlan.Arrival withTwin =
                RingPortalPlan.middleEarthArrival(Optional.of(new BlockPos(1010, 90, -1990)), LANDING);
        helper.assertTrue(withTwin.x() == 1010 && withTwin.z() == -1990 + 4, "Twin arrival " + withTwin);

        // Twin is built when never stored or when the stored one is gone.
        helper.assertTrue(RingPortalPlan.needsTwin(Optional.empty(), false), "Never built");
        helper.assertTrue(RingPortalPlan.needsTwin(Optional.of(BlockPos.ZERO), false), "Stored but missing");
        helper.assertTrue(!RingPortalPlan.needsTwin(Optional.of(BlockPos.ZERO), true), "Stored and present");
        BlockPos stored = new BlockPos(5, 70, 6);
        helper.assertTrue(RingPortalPlan.twinColumn(Optional.of(stored), LANDING).equals(stored), "Rebuild in place");
        BlockPos column = RingPortalPlan.twinColumn(Optional.empty(), LANDING);
        helper.assertTrue(column.getX() == 1000 && column.getZ() == -2000, "First twin over the landing point");

        // Return: beside the overworld portal, else the overworld spawn.
        RingPortalPlan.Arrival back =
                RingPortalPlan.overworldReturn(Optional.of(new BlockPos(30, 70, 40)), new BlockPos(0, 64, 0));
        helper.assertTrue(back.x() == 30 && back.z() == 44 && back.resolveHeight(), "Return arrival " + back);
        RingPortalPlan.Arrival spawn = RingPortalPlan.overworldReturn(Optional.empty(), new BlockPos(7, 65, 9));
        helper.assertTrue(
                spawn.x() == 7 && spawn.y() == 65 && spawn.z() == 9 && !spawn.resolveHeight(), "Spawn fallback " + spawn);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void growingPortalWaitsFullPortalSendsAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 centre = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        RingPortalEntity portal = RingPortalTypes.RING_PORTAL.get().create(level);
        portal.setPos(centre.x, centre.y, centre.z);
        portal.setGrowth(0);
        level.addFreshEntity(portal);
        var pig = EntityType.PIG.create(level);
        pig.setPos(centre.x, centre.y, centre.z);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        // A creative mock player crosses after one tick of contact.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(centre.x + 0.5, centre.y, centre.z);

        Set<Entity> sent = new HashSet<>();
        RingPortalTravel.setOverride((p, e) -> sent.add(e));
        Runnable cleanUp = () -> {
            RingPortalTravel.setOverride(null);
            portal.discard();
            pig.discard();
            level.getServer().getPlayerList().remove(player);
        };
        try {
            helper.runAfterDelay(5, () -> {
                try {
                    helper.assertTrue(sent.isEmpty(), "A growing portal cannot be used");
                    helper.assertTrue(portal.getGrowth() > 0 && portal.getGrowth() < 120, "Growing");
                    portal.setGrowth(RingPortalEntity.FULL_GROWTH);
                    helper.assertTrue(RingPortalTravel.requiredTicks(pig) == 1, "Mobs cross at once");
                    helper.assertTrue(RingPortalTravel.requiredTicks(player) == 1, "Creative players cross at once");
                    helper.runAfterDelay(4, () -> {
                        try {
                            helper.assertTrue(sent.contains(pig), "The pig must be sent");
                            helper.assertTrue(sent.contains(player), "The creative player must be sent");
                            cleanUp.run();
                            helper.succeed();
                        } catch (RuntimeException e) {
                            cleanUp.run();
                            throw e;
                        }
                    });
                } catch (RuntimeException e) {
                    cleanUp.run();
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            cleanUp.run();
            throw e;
        }
    }

    @GameTest(template = "empty")
    public static void portalDataSurvivesTheCodec(GameTestHelper helper) {
        RingPortalData data = RingPortalData.EMPTY
                .withOverworld(new BlockPos(1, 70, 2), UUID.randomUUID())
                .withTwin(new BlockPos(3, 80, 4), UUID.randomUUID());
        var encoded = RingPortalData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        RingPortalData decoded = RingPortalData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(decoded.equals(data), "Round trip");
        RingPortalData reset = decoded.reset();
        helper.assertTrue(!reset.made() && reset.overworldPortal().isPresent(), "Reset only clears the flag");
        var empty = RingPortalData.CODEC.parse(NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).getOrThrow();
        helper.assertTrue(empty.equals(RingPortalData.EMPTY), "Missing fields decode as empty");
        helper.succeed();
    }
}
