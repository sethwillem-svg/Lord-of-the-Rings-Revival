package io.github.sethwillemsvg.lotrrevival.world.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.world.road.MapCorridors;
import io.github.sethwillemsvg.lotrrevival.world.road.Road;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadNetwork;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadNetworks;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadSpline;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Road spline, network index and the {@code lotrrevival:map_corridors} density function
 * ({@code specs/world/waypoints-travel.md}, Roads). The GameTest server has no Middle-earth level, so terrain is
 * tested by decoding the datapack level stem and sampling its noise settings, like
 * {@link MiddleEarthDimensionTest}.
 */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadCorridorTest {
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
    private static final ResourceLocation EAST_ROAD = LOTRRevival.id("east_road");

    private static final double[] CX = {0, 400, 900, 1300, 2000};
    private static final double[] CZ = {0, 120, 60, 300, 250};

    @GameTest(template = "empty")
    public static void splineIsDeterministicAndOneBlockApart(GameTestHelper helper) {
        RoadSpline.Samples a = RoadSpline.sample("lotrrevival:test_road", CX, CZ);
        RoadSpline.Samples b = RoadSpline.sample("lotrrevival:test_road", CX, CZ);
        helper.assertTrue(Arrays.equals(a.x(), b.x()) && Arrays.equals(a.z(), b.z()), "two runs differ");
        helper.assertTrue(Arrays.equals(a.arc(), b.arc()), "arc lengths differ");

        // Samples are one block of arc length apart (the jittered chord is at most ~1.1 blocks).
        for (int i = 0; i + 1 < a.size() - 1; i++) {
            helper.assertTrue(a.arc()[i + 1] - a.arc()[i] == 1.0, "arc spacing at " + i);
            double d = Math.hypot(a.x()[i + 1] - a.x()[i], a.z()[i + 1] - a.z()[i]);
            helper.assertTrue(d > 0.5 && d < 1.6, "sample spacing " + d + " at " + i);
        }
        // Starts and ends within the 1 block jitter of the end control points; long enough to pass the points.
        helper.assertTrue(Math.hypot(a.x()[0] - CX[0], a.z()[0] - CZ[0]) <= 1.0001, "start");
        int last = a.size() - 1;
        helper.assertTrue(Math.hypot(a.x()[last] - CX[4], a.z()[last] - CZ[4]) <= 1.0001, "end");
        helper.assertTrue(a.length() > 2000, "length " + a.length());

        // The jitter is seeded by the id: another id gives another wobble but the same route (within 2 blocks).
        RoadSpline.Samples other = RoadSpline.sample("lotrrevival:other_road", CX, CZ);
        helper.assertTrue(!Arrays.equals(a.x(), other.x()) || !Arrays.equals(a.z(), other.z()), "same jitter");
        for (int i = 0; i < Math.min(a.size(), other.size()); i += 17) {
            double d = Math.hypot(a.x()[i] - other.x()[i], a.z()[i] - other.z()[i]);
            helper.assertTrue(d <= 2.0001, "jitter difference " + d + " at " + i);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roadColumnsAreWithinTheHalfWidth(GameTestHelper helper) {
        RoadSpline.Samples samples = RoadSpline.sample("lotrrevival:test_road", CX, CZ);
        ResourceLocation id = LOTRRevival.id("test_road");
        RoadNetwork network = RoadNetwork.of(List.of(new RoadNetwork.Entry(id, 4, samples)));
        for (int i = 50; i < samples.size() - 50; i += 211) {
            // Normal of the local centre line.
            double tx = samples.x()[i + 1] - samples.x()[i - 1];
            double tz = samples.z()[i + 1] - samples.z()[i - 1];
            double len = Math.hypot(tx, tz);
            double nx = -tz / len;
            double nz = tx / len;
            RoadNetwork.Hit near = network.nearest(samples.x()[i] + nx * 3, samples.z()[i] + nz * 3, 64);
            helper.assertTrue(near != null && near.road().equals(id), "no road found 3 blocks from sample " + i);
            helper.assertTrue(near.onRoad(), "3 blocks from the centre is road, distance " + near.distance());
            helper.assertTrue(Math.abs(near.distance() - 3) < 0.3, "distance " + near.distance());
            helper.assertTrue(Math.abs(near.arcLength() - samples.arc()[i]) < 3, "arc " + near.arcLength());
            helper.assertTrue(near.halfWidth() == 4, "half-width");
            RoadNetwork.Hit far = network.nearest(samples.x()[i] + nx * 6, samples.z()[i] + nz * 6, 64);
            helper.assertTrue(far != null && !far.onRoad(), "6 blocks from the centre is not road");
        }
        helper.assertTrue(network.nearest(-5000, -5000, 64) == null, "nothing near (-5000, -5000)");
        helper.assertTrue(RoadNetwork.EMPTY.nearest(0, 0, 1000) == null, "empty network has no roads");
        helper.assertTrue(RoadNetwork.EMPTY.mask(0, 0, 32) == 1.0, "empty network never masks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void corridorMaskRampsFromHalfWidthToFalloff(GameTestHelper helper) {
        // A straight road along x at z = 0 with no jitter, so distances are exact.
        double[] x = new double[1001];
        double[] z = new double[1001];
        double[] arc = new double[1001];
        for (int i = 0; i <= 1000; i++) {
            x[i] = i;
            arc[i] = i;
        }
        RoadNetwork network = RoadNetwork.of(
                List.of(new RoadNetwork.Entry(LOTRRevival.id("straight"), 4, new RoadSpline.Samples(x, z, arc))));
        helper.assertTrue(network.mask(500, 0, 32) == 0.0, "mask on the centre line");
        helper.assertTrue(network.mask(500, 4, 32) == 0.0, "mask 4 blocks from the centre is 0");
        helper.assertTrue(network.mask(500, 36, 32) == 1.0, "mask 36 blocks from the centre is 1");
        helper.assertTrue(network.mask(500, -90, 32) == 1.0, "mask far away is 1");
        double mid = network.mask(500, 20, 32);
        helper.assertTrue(mid > 0.0 && mid < 1.0, "mask at 20 blocks is between 0 and 1: " + mid);
        helper.assertTrue(Math.abs(mid - 0.5) < 1e-9, "smoothstep midpoint is 0.5: " + mid);
        double prev = 0;
        for (int d = 4; d <= 36; d++) {
            double m = network.mask(500, d, 32);
            helper.assertTrue(m >= prev, "mask must not decrease at " + d);
            prev = m;
        }
        // A wider road moves the whole ramp outward.
        RoadNetwork wide = RoadNetwork.of(
                List.of(new RoadNetwork.Entry(LOTRRevival.id("wide"), 6, new RoadSpline.Samples(x, z, arc))));
        helper.assertTrue(wide.mask(500, 6, 32) == 0.0 && wide.mask(500, 38, 32) == 1.0, "wide road ramp");
        // Around the ends the mask follows the end point, not an infinite line.
        double pastEnd = network.mask(1030, 0, 32);
        helper.assertTrue(pastEnd > 0.0 && pastEnd < 1.0, "30 blocks past the end is still in the ramp: " + pastEnd);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mapCorridorsDensityFunctionMultipliesInputByMask(GameTestHelper helper) {
        var json = JsonParser.parseString("{\"type\":\"lotrrevival:map_corridors\",\"input\":7.0,\"falloff\":32}");
        DensityFunction function = DensityFunction.DIRECT_CODEC
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new IllegalStateException(msg));
        helper.assertTrue(function instanceof MapCorridors c && c.falloff() == 32.0, "decoded " + function);
        var defaulted = DensityFunction.DIRECT_CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"lotrrevival:map_corridors\",\"input\":1.0}"))
                .getOrThrow(msg -> new IllegalStateException(msg));
        helper.assertTrue(((MapCorridors) defaulted).falloff() == 32.0, "falloff defaults to 32");

        RoadNetwork network = RoadNetworks.current();
        helper.assertTrue(!network.isEmpty(), "the road registry is empty: the pilot roads did not load");
        RoadNetwork.Entry east = network.roads().stream()
                .filter(e -> e.id().equals(EAST_ROAD))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("east_road is not in the road registry"));
        int mid = east.samples().size() / 2;
        double cx = east.samples().x()[mid];
        double cz = east.samples().z()[mid];
        DensityFunction.FunctionContext onRoad =
                new DensityFunction.SinglePointContext((int) Math.round(cx), 0, (int) Math.round(cz));
        helper.assertTrue(function.compute(onRoad) == 0.0, "detail is flattened on the East Road");
        DensityFunction.FunctionContext away = new DensityFunction.SinglePointContext(60000, 0, 60000);
        helper.assertTrue(function.compute(away) == 7.0, "detail is untouched far from any road");
        // Dropped into the ramp (about 20 blocks off the road edge) the value is input x mask.
        int probeZ = (int) Math.round(cz) + 24;
        double mask = MapCorridors.mask((int) Math.round(cx), probeZ, 32);
        double value = function.compute(new DensityFunction.SinglePointContext((int) Math.round(cx), 0, probeZ));
        helper.assertTrue(Math.abs(value - 7.0 * mask) < 1e-9, "value " + value + " mask " + mask);
        helper.assertTrue(function.minValue() <= 0 && function.maxValue() >= 7.0, "bounds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roadRegistryDecodesAndIsIndexed(GameTestHelper helper) {
        var registry = helper.getLevel().getServer().registryAccess().registryOrThrow(ModDataRegistries.<Road>road());
        Road east = registry.get(EAST_ROAD);
        helper.assertTrue(east != null, "east_road missing from the road registry");
        helper.assertTrue(east.width() == 4 && east.points().size() >= 10, "east_road data: " + east);
        helper.assertTrue(RoadNetworks.fromRegistry(helper.getLevel().getServer().registryAccess()).roads().size()
                == registry.size(), "every registry road is indexed");
        helper.succeed();
    }

    /** The surface at a road centre stays on the painted base: the corridor mask removes the detail there. */
    @GameTest(template = "empty")
    public static void surfaceAtRoadCentreFollowsThePaintedBase(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var generator = (NoiseBasedChunkGenerator) stem(helper).generator();
        var noises = server.registryAccess().lookupOrThrow(Registries.NOISE);
        RandomState random = RandomState.create(generator.generatorSettings().value(), noises, 12345L);
        DensityFunction base = server.registryAccess()
                .lookupOrThrow(Registries.DENSITY_FUNCTION)
                .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, LOTRRevival.id("middle_earth/base")))
                .value();

        RoadNetwork.Entry east = RoadNetworks.current().roads().stream()
                .filter(e -> e.id().equals(EAST_ROAD))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("east_road is not published"));
        int checked = 0;
        // Pilot road centres inside the Shire, around Hobbiton and Bywater (blocks -2000 .. 1000).
        for (int i = 0; i < east.samples().size(); i++) {
            int x = (int) Math.round(east.samples().x()[i]);
            int z = (int) Math.round(east.samples().z()[i]);
            if (x < -2000 || x > 1000 || i % 500 != 0) {
                continue;
            }
            double painted = base.compute(new DensityFunction.SinglePointContext(x, 0, z));
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random) - 1;
            helper.assertTrue(
                    Math.abs(surface - painted) <= 2.0,
                    "surface " + surface + " at road centre (" + x + ", " + z + ") but painted base " + painted);
            checked++;
        }
        helper.assertTrue(checked >= 3, "only " + checked + " road centres checked");
        helper.succeed();
    }

    /**
     * Benchmark, not a correctness test: create an empty file {@code lotr_road_bench} in the run directory to time column height queries near the East
     * Road with the road index published and without it. Skipped (and side-effect free) otherwise.
     */
    @GameTest(template = "empty", timeoutTicks = 6000)
    public static void benchmarkCorridorCost(GameTestHelper helper) {
        if (!java.nio.file.Files.exists(java.nio.file.Path.of("lotr_road_bench"))) {
            helper.succeed();
            return;
        }
        var server = helper.getLevel().getServer();
        var generator = (NoiseBasedChunkGenerator) stem(helper).generator();
        var noises = server.registryAccess().lookupOrThrow(Registries.NOISE);
        RoadNetwork withRoads = RoadNetworks.current();
        RoadNetwork.Entry east = withRoads.roads().stream()
                .filter(e -> e.id().equals(EAST_ROAD))
                .findFirst()
                .orElseThrow();
        int base = east.samples().size() / 3;
        try {
            long[] roadNanos = new long[5];
            long[] emptyNanos = new long[5];
            for (int round = 0; round < 5; round++) {
                for (boolean useRoads : new boolean[] {true, false}) {
                    RoadNetworks.publish(useRoads ? withRoads : RoadNetwork.EMPTY);
                    RandomState random = RandomState.create(generator.generatorSettings().value(), noises, 12345L);
                    long start = System.nanoTime();
                    for (int k = 0; k < 160; k++) {
                        int i = base + k * 12;
                        int x = (int) east.samples().x()[i] + (k % 7) * 9 - 27;
                        int z = (int) east.samples().z()[i] + (k % 5) * 11 - 22;
                        generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random);
                    }
                    long elapsed = System.nanoTime() - start;
                    if (useRoads) {
                        roadNanos[round] = elapsed;
                    } else {
                        emptyNanos[round] = elapsed;
                    }
                }
            }
            Arrays.sort(roadNanos);
            Arrays.sort(emptyNanos);
            org.slf4j.LoggerFactory.getLogger(RoadCorridorTest.class).info("[road-bench] median with roads " + roadNanos[2] / 1_000_000.0 + " ms, without "
                    + emptyNanos[2] / 1_000_000.0 + " ms, ratio " + (double) roadNanos[2] / emptyNanos[2]);
        } finally {
            RoadNetworks.publish(withRoads);
        }
        helper.succeed();
    }

    private static LevelStem stem(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var file = server.getResourceManager().getResource(LOTRRevival.id("dimension/middle_earth.json"));
        helper.assertTrue(file.isPresent(), "dimension/middle_earth.json missing from the data pack");
        try (var reader = file.get().openAsReader()) {
            var json = JsonParser.parseReader(reader);
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            return LevelStem.CODEC.parse(ops, json).getOrThrow(msg -> new IllegalStateException(msg));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
