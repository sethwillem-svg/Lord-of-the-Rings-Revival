package io.github.sethwillemsvg.lotrrevival.world.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadFeature;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadNetwork;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadPlanner;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadSpline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Road bridges ({@code specs/world/waypoints-travel.md}, Bridges): deck height from the water depth, rails on the
 * outermost columns, piers every 8 blocks of arc length, water kept under the deck, structures winning. Runs on a
 * platform built high above the test with a pool carved into it.
 */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadBridgeTest {
    private static final int POOL_START = -2;
    private static final int POOL_END = 38;

    private static RoadNetwork road(double x0, double z, double x1) {
        return RoadNetwork.of(List.of(new RoadNetwork.Entry(
                LOTRRevival.id("test_bridge_road"),
                4,
                RoadSpline.sample("lotrrevival:test_bridge_road", new double[] {x0, x1}, new double[] {z, z}))));
    }

    /**
     * Stone platform with grass on top at the returned y, and a pool across the whole road width from
     * {@code POOL_START} to {@code POOL_END} (relative x) whose floor is {@code depth} blocks under the water top,
     * which is the block under the grass level.
     */
    private static int buildPool(GameTestHelper helper, int depth) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = Math.min(base.getY() + 60, 270);
        for (int x = -50; x <= 50; x++) {
            for (int z = -14; z <= 14; z++) {
                boolean pool = x >= POOL_START && x <= POOL_END;
                int waterTop = y0 - 1;
                int floor = waterTop - depth;
                for (int y = y0 - 16; y <= y0 + 16; y++) {
                    BlockState state;
                    if (y < y0) {
                        state = pool && y > floor ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        if (pool && y == floor) {
                            state = Blocks.SAND.defaultBlockState();
                        }
                    } else if (y == y0) {
                        state = pool ? Blocks.AIR.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(new BlockPos(base.getX() + x, y, base.getZ() + z), state, 2);
                }
            }
        }
        return y0;
    }

    private static List<RoadPlanner.Column> bridgeOver(GameTestHelper helper, int y0, RoadNetwork network) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        return RoadFeature.placeRegion(
                level,
                network,
                new RoadFeature.LevelTerrain(level, x -> y0 + 12),
                base.getX() - 44,
                base.getZ() - 10,
                base.getX() + 44,
                base.getZ() + 10,
                List.of());
    }

    @GameTest(template = "empty")
    public static void roadOverSixDeepPoolBuildsDeckRailsAndPiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int bx = base.getX();
        int bz = base.getZ();
        int y0 = buildPool(helper, 6);
        int waterTop = y0 - 1;
        int floor = waterTop - 6;
        RoadNetwork network = road(bx - 40, bz, bx + 60);
        bridgeOver(helper, y0, network);

        int deckY = waterTop + 3;
        List<Integer> pierXs = new ArrayList<>();
        for (int dx = POOL_START + 1; dx <= POOL_END - 1; dx++) {
            int x = bx + dx;
            int railColumns = 0;
            for (int dz = -7; dz <= 7; dz++) {
                BlockPos column = new BlockPos(x, deckY, bz + dz);
                if (network.edgeDistance(x, bz + dz, 0) > 0) {
                    helper.assertTrue(
                            !level.getBlockState(column).is(Blocks.OAK_PLANKS), "deck outside the road at " + dz);
                    continue;
                }
                helper.assertTrue(
                        level.getBlockState(column).is(Blocks.OAK_PLANKS),
                        "deck 3 above the water missing at " + dx + "," + dz + ": " + level.getBlockState(column));
                // Air between the water and the deck; the water itself stays (a pier column is stone bricks).
                BlockState atWater = level.getBlockState(new BlockPos(x, waterTop, bz + dz));
                helper.assertTrue(
                        atWater.is(Blocks.WATER) || atWater.is(Blocks.STONE_BRICKS),
                        "water not left under the deck at " + dx + "," + dz + ": " + atWater);
                helper.assertTrue(level.getBlockState(column.above(2)).isAir(), "something over the rail");
                RoadNetwork.Hit hit = network.nearest(x, bz + dz, 6);
                boolean edge = hit != null && hit.distance() > hit.halfWidth() - 1;
                boolean fence = level.getBlockState(column.above()).is(Blocks.OAK_FENCE);
                helper.assertTrue(fence == edge, "rail wrong at " + dx + "," + dz + " edge=" + edge);
                if (edge) {
                    railColumns++;
                    boolean pier = level.getBlockState(column.below()).is(Blocks.STONE_BRICKS);
                    helper.assertTrue(
                            pier == ((long) Math.floor(hit.arcLength()) % 8 == 0),
                            "pier wrong at " + dx + "," + dz + " arc " + hit.arcLength());
                    if (pier) {
                        for (int y = floor + 1; y < deckY; y++) {
                            helper.assertTrue(
                                    level.getBlockState(new BlockPos(x, y, bz + dz)).is(Blocks.STONE_BRICKS),
                                    "pier has a gap at y " + y);
                        }
                        helper.assertTrue(level.getBlockState(new BlockPos(x, floor, bz + dz)).is(Blocks.SAND), "bed changed");
                        pierXs.add(dx);
                    }
                } else {
                    helper.assertTrue(
                            level.getBlockState(column.above()).isAir(), "rail on an inner column at " + dx + "," + dz);
                }
            }
            helper.assertTrue(railColumns >= 2, "need a rail on both edges at " + dx + ", got " + railColumns);
        }
        // Water below the deck away from piers is still there, all the way down to the bed.
        BlockPos middle = new BlockPos(bx + 17, floor + 1, bz);
        helper.assertTrue(level.getBlockState(middle).is(Blocks.WATER), "water removed under the bridge");

        // 40 pool columns hold piers every 8 blocks (4 or 5 clusters, two edges each).
        helper.assertTrue(pierXs.size() >= 8, "too few pier columns: " + pierXs);
        int last = Integer.MIN_VALUE;
        int clusters = 0;
        List<Integer> starts = new ArrayList<>();
        for (int dx : pierXs.stream().sorted().distinct().toList()) {
            if (last == Integer.MIN_VALUE || dx - last > 2) {
                clusters++;
                starts.add(dx);
            }
            last = dx;
        }
        helper.assertTrue(clusters >= 4 && clusters <= 6, "pier clusters " + clusters + " at " + starts);
        for (int i = 1; i < starts.size(); i++) {
            int gap = starts.get(i) - starts.get(i - 1);
            helper.assertTrue(gap >= 7 && gap <= 9, "piers " + gap + " apart: " + starts);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oddDepthEndsInASlabAndDeepWaterCapsAtFive(GameTestHelper helper) {
        helper.assertTrue(RoadPlanner.deckY(100, 6) == 103, "6 deep");
        helper.assertTrue(RoadPlanner.deckY(100, 5) == 103 && RoadPlanner.deckHasHalf(5), "5 deep is 2.5 up: a slab");
        helper.assertTrue(RoadPlanner.deckY(100, 1) == 101 && RoadPlanner.deckHasHalf(1), "1 deep is half a block up");
        helper.assertTrue(RoadPlanner.deckY(100, 10) == 105 && !RoadPlanner.deckHasHalf(10), "10 deep");
        helper.assertTrue(RoadPlanner.deckY(100, 30) == 105 && !RoadPlanner.deckHasHalf(30), "deep water caps at 5");

        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = buildPool(helper, 5);
        RoadNetwork network = road(base.getX() - 40, base.getZ(), base.getX() + 60);
        bridgeOver(helper, y0, network);
        BlockPos slab = new BlockPos(base.getX() + 10, y0 - 1 + 3, base.getZ());
        BlockState state = level.getBlockState(slab);
        helper.assertTrue(state.is(Blocks.OAK_SLAB), "no half step on a 5 deep pool: " + state);
        helper.assertTrue(
                state.getValue(SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM,
                "half step is not a bottom slab");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void structureBoxesAndDryLandAreNotBridged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = buildPool(helper, 6);
        RoadNetwork network = road(base.getX() - 40, base.getZ(), base.getX() + 60);
        int bx = base.getX();
        BoundingBox landmark = new BoundingBox(bx + 10, y0 - 12, base.getZ() - 12, bx + 20, y0 + 6, base.getZ() + 12);
        RoadFeature.placeRegion(
                level,
                network,
                new RoadFeature.LevelTerrain(level, x -> y0 + 12),
                bx - 44,
                base.getZ() - 10,
                bx + 44,
                base.getZ() + 10,
                List.of(landmark));
        int deckY = y0 - 1 + 3;
        for (int dx = 10; dx <= 20; dx++) {
            helper.assertTrue(
                    !level.getBlockState(new BlockPos(bx + dx, deckY, base.getZ())).is(Blocks.OAK_PLANKS),
                    "bridge inside a structure box at " + dx);
        }
        helper.assertTrue(
                level.getBlockState(new BlockPos(bx + 8, deckY, base.getZ())).is(Blocks.OAK_PLANKS),
                "no bridge outside the box");
        // Land beside the pool gets no deck.
        helper.assertTrue(
                !level.getBlockState(new BlockPos(bx - 20, deckY, base.getZ())).is(Blocks.OAK_PLANKS),
                "deck on dry land");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replanningAnExistingBridgeFindsNoGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = buildPool(helper, 6);
        RoadNetwork network = road(base.getX() - 40, base.getZ(), base.getX() + 60);
        bridgeOver(helper, y0, network);
        RoadFeature.LevelTerrain terrain = new RoadFeature.LevelTerrain(level, x -> y0 + 12);
        // A column carrying a deck, a rail or a pier is still a water column to the planner.
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx : new int[] {8, 16, 24}) {
                helper.assertTrue(
                        terrain.top(base.getX() + dx, base.getZ() + dz) == RoadPlanner.NO_GROUND,
                        "bridge column read as ground at " + dx + "," + dz);
            }
        }
        helper.succeed();
    }

    private RoadBridgeTest() {}
}
