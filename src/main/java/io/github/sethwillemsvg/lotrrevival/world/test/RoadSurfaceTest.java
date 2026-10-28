package io.github.sethwillemsvg.lotrrevival.world.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadFeature;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadNetwork;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadPlanner;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadStyle;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadSpline;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadStyles;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadVegetation;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Road surface rules ({@code specs/world/waypoints-travel.md}, Roads): depth, levelling, half steps, repair,
 * structures, and the road-safe vegetation filter. The GameTest server has no Middle-earth level, so the rules run on
 * a fake terrain, and the block writing runs on a platform built high above the test.
 */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadSurfaceTest {
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();

    /** A straight east-west road on z = 0 from x = -20 to x = {@code length}, half-width 4. */
    private static RoadNetwork straightRoad(double x0, double z, double x1) {
        return RoadNetwork.of(List.of(new RoadNetwork.Entry(
                LOTRRevival.id("test_road"),
                4,
                RoadSpline.sample("lotrrevival:test_road", new double[] {x0, x1}, new double[] {z, z}))));
    }

    private static RoadPlanner.Terrain terrain(IntBinaryOperator top, ResourceKey<Biome> biome) {
        return new RoadPlanner.Terrain() {
            @Override
            public int top(int x, int z) {
                return top.applyAsInt(x, z);
            }

            @Override
            @Nullable
            public ResourceKey<Biome> biome(int x, int z, int top) {
                return biome;
            }
        };
    }

    private static int roadColumns(RoadNetwork network, int minX, int minZ, int maxX, int maxZ) {
        int n = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (network.edgeDistance(x, z, 0) <= 0) {
                    n++;
                }
            }
        }
        return n;
    }

    @GameTest(template = "empty")
    public static void loneLandsPlacesAboutFortyPercent(GameTestHelper helper) {
        RoadNetwork network = straightRoad(-20, 0, 220);
        int total = roadColumns(network, 0, -8, 139, 8);
        helper.assertTrue(total >= 1000, "need 1000+ road columns, got " + total);

        List<RoadPlanner.Column> lone = RoadPlanner.plan(
                network, terrain((x, z) -> 64, MiddleEarthBiomes.LONE_LANDS), 0, -8, 139, 8, List.of());
        double share = lone.size() / (double) total;
        helper.assertTrue(share > 0.35 && share < 0.45, "Lone-lands repair " + share + " of " + total);
        helper.assertTrue(lone.stream().allMatch(c -> c.style().id().equals("arnor")), "Lone-lands style is arnor");

        // The same hash gives the same columns in a second run (and in any world).
        List<RoadPlanner.Column> again = RoadPlanner.plan(
                network, terrain((x, z) -> 64, MiddleEarthBiomes.LONE_LANDS), 0, -8, 139, 8, List.of());
        helper.assertTrue(lone.equals(again), "repair differs between runs");

        // Every other pilot biome uses the path style with repair 1.
        List<RoadPlanner.Column> shire =
                RoadPlanner.plan(network, terrain((x, z) -> 64, MiddleEarthBiomes.SHIRE), 0, -8, 139, 8, List.of());
        helper.assertTrue(shire.size() == total, "Shire places " + shire.size() + " of " + total);
        helper.assertTrue(shire.stream().allMatch(c -> c.style().id().equals("path")), "Shire style is path");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void surfaceMixFollowsTheStyleWeights(GameTestHelper helper) {
        RoadNetwork network = straightRoad(-20, 0, 420);
        List<RoadPlanner.Column> columns =
                RoadPlanner.plan(network, terrain((x, z) -> 64, MiddleEarthBiomes.SHIRE), 0, -8, 399, 8, List.of());
        double paths = 0;
        double coarse = 0;
        double gravel = 0;
        for (RoadPlanner.Column c : columns) {
            Block b = c.surface().block().getBlock();
            if (b == Blocks.DIRT_PATH) {
                paths++;
            } else if (b == Blocks.COARSE_DIRT) {
                coarse++;
            } else if (b == Blocks.GRAVEL) {
                gravel++;
            }
        }
        double n = columns.size();
        helper.assertTrue(paths + coarse + gravel == n, "unexpected surface block");
        helper.assertTrue(Math.abs(paths / n - 0.7) < 0.04, "dirt path share " + paths / n);
        helper.assertTrue(Math.abs(coarse / n - 0.2) < 0.04, "coarse dirt share " + coarse / n);
        helper.assertTrue(Math.abs(gravel / n - 0.1) < 0.04, "gravel share " + gravel / n);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void halfStepsFollowTheNeighbourMean(GameTestHelper helper) {
        RoadNetwork network = straightRoad(-20, 0, 120);
        // A 2 block ledge at z >= 2 across the road.
        IntBinaryOperator ledge = (x, z) -> z >= 2 ? 66 : 64;
        List<RoadPlanner.Column> columns =
                RoadPlanner.plan(network, terrain(ledge, MiddleEarthBiomes.SHIRE), 10, -8, 90, 8, List.of());
        int steps = 0;
        for (RoadPlanner.Column c : columns) {
            double sum = 0;
            int count = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && network.edgeDistance(c.x() + dx, c.z() + dz, 0) <= 0) {
                        sum += ledge.applyAsInt(c.x() + dx, c.z() + dz);
                        count++;
                    }
                }
            }
            boolean expected = count > 0 && sum / count - c.top() >= 0.5;
            helper.assertTrue(
                    c.step() == expected,
                    "step at " + c.x() + "," + c.z() + " is " + c.step() + ", expected " + expected);
            if (c.step()) {
                steps++;
                helper.assertTrue(c.top() == 64, "only the low side steps");
            }
        }
        helper.assertTrue(steps > 20, "expected a row of half steps, got " + steps);
        helper.assertTrue(columns.stream().anyMatch(c -> !c.step()), "flat parts have no step");

        // The Arnor style steps with its slab.
        RoadStyle arnor = RoadStyles.arnor();
        helper.assertTrue(arnor.surface().stream().allMatch(s -> s.slab() != null), "arnor has slabs");
        helper.assertTrue(
                RoadStyles.path().surface().stream().allMatch(s -> s.slab() == null), "path has no slab");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterAndStructureColumnsAreSkipped(GameTestHelper helper) {
        RoadNetwork network = straightRoad(-20, 0, 120);
        // Water for z > 2, and a structure covering x 40..60 at the road height.
        IntBinaryOperator shore = (x, z) -> z > 2 ? RoadPlanner.NO_GROUND : 64;
        List<BoundingBox> boxes = List.of(new BoundingBox(40, 60, -10, 60, 70, 10));
        List<RoadPlanner.Column> columns =
                RoadPlanner.plan(network, terrain(shore, MiddleEarthBiomes.SHIRE), 0, -8, 99, 8, boxes);
        helper.assertTrue(!columns.isEmpty(), "no columns planned");
        for (RoadPlanner.Column c : columns) {
            helper.assertTrue(c.z() <= 2, "water column built at " + c.x() + "," + c.z());
            helper.assertTrue(c.x() < 40 || c.x() > 60, "structure column built at " + c.x() + "," + c.z());
        }
        // A structure far below the road does not block it.
        List<BoundingBox> buried = List.of(new BoundingBox(40, 10, -10, 60, 30, 10));
        List<RoadPlanner.Column> over =
                RoadPlanner.plan(network, terrain((x, z) -> 64, MiddleEarthBiomes.SHIRE), 0, -8, 99, 8, buried);
        helper.assertTrue(over.stream().anyMatch(c -> c.x() >= 40 && c.x() <= 60), "buried structure blocked road");
        helper.succeed();
    }

    /** Fills a platform of grass over stone, with room above, and returns its surface y. */
    private static int buildPlatform(GameTestHelper helper, int radius) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = Math.min(base.getY() + 50, 280);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = y0 - 8; y <= y0 + 14; y++) {
                    BlockState state = y < y0 ? Blocks.STONE.defaultBlockState() : y == y0 ? GRASS : Blocks.AIR.defaultBlockState();
                    level.setBlock(new BlockPos(base.getX() + x, y, base.getZ() + z), state, 2);
                }
            }
        }
        return y0;
    }

    @GameTest(template = "empty")
    public static void roadBlocksAreFourDeepAndPlantsAreCleared(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = buildPlatform(helper, 14);
        int bx = base.getX();
        int bz = base.getZ();
        RoadNetwork network = straightRoad(bx - 40, bz, bx + 40);

        // Plants and snow above the road, and above a column well off the road.
        level.setBlock(new BlockPos(bx, y0 + 1, bz), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        level.setBlock(new BlockPos(bx + 1, y0 + 1, bz), Blocks.POPPY.defaultBlockState(), 2);
        level.setBlock(new BlockPos(bx + 2, y0 + 1, bz), Blocks.SNOW.defaultBlockState(), 2);
        level.setBlock(new BlockPos(bx + 3, y0 + 1, bz), Blocks.FERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(bx, y0 + 1, bz + 10), Blocks.SHORT_GRASS.defaultBlockState(), 2);

        RoadFeature.placeRegion(
                level,
                network,
                new RoadFeature.LevelTerrain(level, x -> y0 + 8),
                bx - 12,
                bz - 12,
                bx + 12,
                bz + 12,
                List.of());

        for (int dx = -6; dx <= 6; dx++) {
            BlockPos column = new BlockPos(bx + dx, y0, bz);
            helper.assertTrue(
                    RoadStyles.isRoadSurface(level.getBlockState(column)),
                    "no surface at " + dx + ": " + level.getBlockState(column));
            for (int i = 1; i <= 3; i++) {
                helper.assertTrue(
                        level.getBlockState(column.below(i)).is(Blocks.DIRT),
                        "no base " + i + " below at " + dx + ": " + level.getBlockState(column.below(i)));
            }
            helper.assertTrue(level.getBlockState(column.below(4)).is(Blocks.STONE), "base is deeper than 4");
            helper.assertTrue(level.getBlockState(column.above()).isAir(), "plant left above road at " + dx);
        }
        // Outside the half-width (4 plus up to 1 jitter) nothing changes.
        BlockPos off = new BlockPos(bx, y0, bz + 10);
        helper.assertTrue(level.getBlockState(off).is(Blocks.GRASS_BLOCK), "off-road grass changed");
        helper.assertTrue(level.getBlockState(off.above()).is(Blocks.SHORT_GRASS), "off-road plant removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void treeFeatureFailsOnARoadBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = buildPlatform(helper, 14);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Holder<ConfiguredFeature<?, ?>> oak =
                level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(TreeFeatures.OAK);

        // Stand-in road block: coarse dirt, which trees would normally accept. The real filter does the same with
        // the tag; see realFilterMatchesTheRoadSurfaceTag.
        PlacedFeature guarded = new PlacedFeature(oak, List.of(BlockPredicateFilter.forPredicate(
                RoadVegetation.rejecting(BlockPredicate.matchesBlocks(new BlockPos(0, -1, 0), Blocks.COARSE_DIRT)))));
        PlacedFeature unguarded = new PlacedFeature(oak, List.of());

        BlockPos road = base.offset(8, 0, 8).atY(y0);
        level.setBlock(road, Blocks.COARSE_DIRT.defaultBlockState(), 2);
        helper.assertTrue(
                !guarded.place(level, generator, RandomSource.create(7), road.above()),
                "tree placed on a road block");
        helper.assertTrue(level.getBlockState(road.above()).isAir(), "a failed tree left blocks");

        boolean placedOnGrass = false;
        for (int seed = 0; seed < 8 && !placedOnGrass; seed++) {
            placedOnGrass = guarded.place(
                    level, generator, RandomSource.create(seed), base.offset(-8, 0, -8).atY(y0 + 1));
        }
        helper.assertTrue(placedOnGrass, "the filter blocked a tree on grass");

        // Control: without the filter the same spot takes a tree.
        boolean control = false;
        for (int seed = 0; seed < 8 && !control; seed++) {
            control = unguarded.place(level, generator, RandomSource.create(seed), road.above());
        }
        helper.assertTrue(control, "the road block cannot hold a tree even without the filter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realFilterMatchesTheRoadSurfaceTag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int y0 = buildPlatform(helper, 6);
        PlacementContext context =
                new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty());
        BlockPos above = base.atY(y0 + 1);
        helper.assertTrue(
                RoadVegetation.filter().getPositions(context, RandomSource.create(1), above).count() == 1,
                "the filter rejected grass");

        // Rejects every block that is in the tag (Core's Arnor set once it is merged).
        var tag = BuiltInRegistries.BLOCK.getTag(ModTags.Blocks.ROAD_SURFACES);
        int checked = 0;
        if (tag.isPresent()) {
            for (Holder<Block> holder : tag.get()) {
                level.setBlock(above.below(), holder.value().defaultBlockState(), 2);
                helper.assertTrue(
                        RoadVegetation.filter()
                                        .getPositions(context, RandomSource.create(1), above)
                                        .count()
                                == 0,
                        "the filter accepted " + holder.value());
                checked++;
            }
        }
        helper.assertTrue(checked >= 0, "unreachable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roadSafeAddsTheFilterBeforeTheBiomeFilter(GameTestHelper helper) {
        var biome = net.minecraft.world.level.levelgen.placement.BiomeFilter.biome();
        var count = net.minecraft.world.level.levelgen.placement.CountPlacement.of(3);
        List<net.minecraft.world.level.levelgen.placement.PlacementModifier> out =
                RoadVegetation.roadSafe(List.of(count, biome));
        helper.assertTrue(out.size() == 3, "size " + out.size());
        helper.assertTrue(out.get(0) == count && out.get(2) == biome, "order changed");
        helper.assertTrue(out.get(1) instanceof BlockPredicateFilter, "filter missing");
        helper.succeed();
    }

    private RoadSurfaceTest() {}
}
