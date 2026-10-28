package io.github.sethwillemsvg.lotrrevival.world.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthLanding;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthMap;
import io.github.sethwillemsvg.lotrrevival.world.WorldScale;
import io.github.sethwillemsvg.lotrrevival.world.WorldScaleLock;
import io.github.sethwillemsvg.lotrrevival.world.WorldScales;
import io.github.sethwillemsvg.lotrrevival.world.road.Road;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadNetwork;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelRules;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.Waypoint;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

/**
 * World scale (specs/world/dimension-and-mapgen.md, "World scale"): the pixel to block math, the generated data of
 * every scale, and that terrain, biomes, roads, waypoints and the landing point line up at Legacy, half and quarter.
 *
 * <p>The GameTest server enables every available data pack, so it runs at whichever world-scale pack is on top.
 * Every scale is therefore tested from the files in the mod itself: the Middle-earth level stem is decoded with that
 * scale's pack files laid over the mod's own data, the same files a world with only that pack enabled would load.
 */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldScaleTest {
    private static final double[] HOBBITON = {1620, 1460};
    private static final double[] BRIDGE = {1696.6, 1459.5};
    private static final double[] BREE = {1819.5, 1466};
    private static final double[] MICHEL_DELVING = {1500, 1470};

    /** Straight-line distances from Hobbiton at Legacy scale, in blocks. */
    private static final double LEGACY_BRIDGE = 4902.5;

    private static final double LEGACY_BREE = 12773.8;
    private static final double LEGACY_MICHEL_DELVING = 7706.6;

    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);

    private static double distance(double[] a, double[] b, WorldScale scale) {
        return Math.hypot(
                MiddleEarthMap.blockX(a[0], scale) - MiddleEarthMap.blockX(b[0], scale),
                MiddleEarthMap.blockZ(a[1], scale) - MiddleEarthMap.blockZ(b[1], scale));
    }

    @GameTest(template = "empty")
    public static void pixelBlockMathAtEveryScale(GameTestHelper helper) {
        for (WorldScale scale : WorldScale.values()) {
            String s = scale.id() + ": ";
            helper.assertTrue(scale.heightBlocksPerPixel() == 64 * scale.factor(), s + "blocks per pixel");
            helper.assertTrue(
                    MiddleEarthMap.blockX(MiddleEarthMap.ORIGIN_U, scale) == 0
                            && MiddleEarthMap.blockZ(MiddleEarthMap.ORIGIN_V, scale) == 0,
                    s + "Hobbiton's pixel is block (0, 0)");
            for (double u : new double[] {0, 1234.5, 1620, 1819.5, 6400}) {
                double back = MiddleEarthMap.pixelU(MiddleEarthMap.blockX(u, scale), scale);
                helper.assertTrue(Math.abs(back - u) < 1e-9, s + "pixel " + u + " round-trips to " + back);
                double backV = MiddleEarthMap.pixelV(MiddleEarthMap.blockZ(u, scale), scale);
                helper.assertTrue(Math.abs(backV - u) < 1e-9, s + "pixel v " + u + " round-trips to " + backV);
            }
            helper.assertTrue(MiddleEarthMap.blockZ(1400, scale) < 0, s + "north is negative z");

            double bridge = distance(HOBBITON, BRIDGE, scale);
            double bree = distance(HOBBITON, BREE, scale);
            double michel = distance(HOBBITON, MICHEL_DELVING, scale);
            helper.assertTrue(Math.abs(bridge - LEGACY_BRIDGE * scale.factor()) < 1, s + "to the bridge " + bridge);
            helper.assertTrue(Math.abs(bree - LEGACY_BREE * scale.factor()) < 1, s + "to Bree " + bree);
            helper.assertTrue(
                    Math.abs(michel - LEGACY_MICHEL_DELVING * scale.factor()) < 1, s + "to Michel Delving " + michel);

            // Heights: sea level never moves; painted slopes get at most twice as steep.
            helper.assertTrue(scale.surfaceY(MiddleEarthMap.SEA_LEVEL) == MiddleEarthMap.SEA_LEVEL, s + "sea level");
            helper.assertTrue(scale.verticalFactor() / scale.factor() <= 2.0, s + "slopes at most 2x as steep");
            helper.assertTrue(scale.surfaceY(97) > 75, s + "Bree-hill still stands well above the land");
            // Legacy-scale block numbers (config defaults) shrink with the map.
            helper.assertTrue(
                    scale.blocks(5000) == Math.round(5000 * scale.factor()), s + "respawn distance " + scale.blocks(5000));
            helper.assertTrue(Math.abs(scale.legacyBlocks(scale.blocks(1000.0)) - 1000) < 1e-9, s + "legacy blocks");
        }
        helper.assertTrue(WorldScale.LEGACY.surfaceY(97) == 97, "Legacy heights are unchanged");
        helper.assertTrue(WorldScale.DEFAULT == WorldScale.HALF, "new worlds default to half");
        helper.succeed();
    }

    /** NovoAtlas's index formula: index = (block - offset x size) / blocksPerSample + size / 2. */
    private static double index(double block, float offset, int size, double blocksPerSample) {
        return (block - offset * size) / blocksPerSample + size / 2.0;
    }

    @GameTest(template = "empty")
    public static void centerOffsetsReproduceTheProjectionAtEveryScale(GameTestHelper helper) {
        for (WorldScale scale : WorldScale.values()) {
            double bpp = scale.heightBlocksPerPixel();
            for (double block : new double[] {0, -5000, 12_773.8}) {
                double u = MiddleEarthMap.pixelU(block, scale);
                double v = MiddleEarthMap.pixelV(block, scale);
                double tx = index(block, MiddleEarthMap.terrainOffsetX(scale), MiddleEarthMap.HEIGHT_WIDTH, bpp);
                double tz = index(block, MiddleEarthMap.terrainOffsetZ(scale), MiddleEarthMap.HEIGHT_HEIGHT, bpp);
                helper.assertTrue(
                        Math.abs(tx - (u - 0.5)) * bpp < 0.05 && Math.abs(tz - (v - 0.5)) * bpp < 0.05,
                        scale.id() + ": terrain index of block " + block + " is " + tx + ", " + tz);
                double quarts = MiddleEarthMap.biomeQuartsPerPixel(scale);
                double bx = index(block / 4.0, MiddleEarthMap.biomesOffsetX(scale), MiddleEarthMap.BIOME_WIDTH, quarts);
                double bz = index(block / 4.0, MiddleEarthMap.biomesOffsetZ(scale), MiddleEarthMap.BIOME_HEIGHT, quarts);
                helper.assertTrue(
                        Math.abs(bx - u / 2) * bpp < 0.1 && Math.abs(bz - v / 2) * bpp < 0.1,
                        scale.id() + ": biome index of block " + block + " is " + bx + ", " + bz);
                double rpp = scale.reliefBlocksPerPixel();
                double rx = index(block, MiddleEarthMap.reliefOffsetX(scale), MiddleEarthMap.RELIEF_WIDTH, rpp);
                double rz = index(block, MiddleEarthMap.reliefOffsetZ(scale), MiddleEarthMap.RELIEF_HEIGHT, rpp);
                helper.assertTrue(
                        Math.abs(rx - (u / 4 - 0.5)) * rpp < 0.1 && Math.abs(rz - (v / 4 - 0.5)) * rpp < 0.1,
                        scale.id() + ": relief index of block " + block + " is " + rx + ", " + rz);
            }
        }
        helper.succeed();
    }

    /** The running world's scale is the top-most enabled world-scale pack, or the default with none. */
    @GameTest(template = "empty")
    public static void runningWorldUsesItsDataPackScale(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WorldScale expected = WorldScale.DEFAULT;
        for (String id : server.getPackRepository().getSelectedIds()) {
            for (WorldScale scale : WorldScales.PACK_SCALES) {
                if (id.equals(WorldScales.packId(scale))) {
                    expected = scale; // later in the list is higher priority
                }
            }
        }
        var access = server.registryAccess();
        helper.assertTrue(
                WorldScales.fromRegistry(access) == expected,
                "registry scale " + WorldScales.fromRegistry(access) + ", packs " + server.getPackRepository().getSelectedIds());
        helper.assertTrue(WorldScales.current() == expected, "published scale " + WorldScales.current());
        for (WorldScale scale : WorldScales.PACK_SCALES) {
            helper.assertTrue(
                    server.getPackRepository().getAvailableIds().contains(WorldScales.packId(scale)),
                    "pack " + WorldScales.packId(scale) + " is not offered");
        }
        helper.assertTrue(
                WorldScales.packId(WorldScale.QUARTER).equals("mod/lotrrevival:datapacks/world_scale_quarter"),
                "quarter pack id " + WorldScales.packId(WorldScale.QUARTER));
        helper.succeed();
    }

    // ---- generated data ----

    /** Folder of {@code scale}'s built-in data pack in the mod file. */
    private static Path packDir(WorldScale scale) {
        return ModList.get()
                .getModFileById(LOTRRevival.MOD_ID)
                .getFile()
                .findResource("datapacks", scale.packName());
    }

    /**
     * A {@code data/lotrrevival/<path>} file as a world with only {@code scale}'s pack loads it: the pack's copy
     * wins over the mod's own. Read from the mod file, not the server, whose packs may differ.
     */
    private static JsonElement data(MinecraftServer server, WorldScale scale, String path) {
        Path file = null;
        if (scale != WorldScale.DEFAULT) {
            file = packDir(scale).resolve("data").resolve(LOTRRevival.MOD_ID).resolve(path);
        }
        if (file == null || !Files.exists(file)) {
            file = ModList.get()
                    .getModFileById(LOTRRevival.MOD_ID)
                    .getFile()
                    .findResource("data", LOTRRevival.MOD_ID)
                    .resolve(path);
        }
        if (!Files.exists(file)) {
            throw new IllegalStateException("missing " + file);
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void checkMapInfo(
            GameTestHelper helper, JsonObject info, String name, double scale, float offsetX, float offsetZ) {
        double value = info.getAsJsonObject("scaling")
                .getAsJsonObject("horizontal_scale")
                .get("value")
                .getAsDouble();
        JsonArray offset = info.getAsJsonArray("center_offset");
        helper.assertTrue(value == scale, name + " horizontal_scale " + value + ", expected " + scale);
        helper.assertTrue(
                offset.get(0).getAsFloat() == offsetX && offset.get(1).getAsFloat() == offsetZ,
                name + " center_offset " + offset + ", expected [" + offsetX + ", " + offsetZ + "]");
    }

    @GameTest(template = "empty")
    public static void generatedDataMatchesEveryScale(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (WorldScale scale : WorldScale.values()) {
            String s = scale.id() + ": ";
            checkMapInfo(
                    helper,
                    data(server, scale, "novoatlas/map_info/middle_earth.json").getAsJsonObject(),
                    s + "terrain",
                    scale.heightBlocksPerPixel(),
                    MiddleEarthMap.terrainOffsetX(scale),
                    MiddleEarthMap.terrainOffsetZ(scale));
            checkMapInfo(
                    helper,
                    data(server, scale, "novoatlas/map_info/middle_earth_biomes.json").getAsJsonObject(),
                    s + "biomes",
                    MiddleEarthMap.biomeQuartsPerPixel(scale),
                    MiddleEarthMap.biomesOffsetX(scale),
                    MiddleEarthMap.biomesOffsetZ(scale));
            checkMapInfo(
                    helper,
                    data(server, scale, "novoatlas/map_info/middle_earth_relief.json").getAsJsonObject(),
                    s + "relief",
                    scale.reliefBlocksPerPixel(),
                    MiddleEarthMap.reliefOffsetX(scale),
                    MiddleEarthMap.reliefOffsetZ(scale));
            String named = data(server, scale, "lotrrevival/world_scale/middle_earth.json")
                    .getAsJsonObject()
                    .get("scale")
                    .getAsString();
            helper.assertTrue(named.equals(scale.id()), s + "world_scale entry says " + named);
            if (scale == WorldScale.DEFAULT) {
                helper.assertTrue(packDir(scale) == null || !Files.exists(packDir(scale)), s + "needs no pack");
            } else {
                Path meta = packDir(scale).resolve("pack.mcmeta");
                helper.assertTrue(Files.exists(meta), s + "pack.mcmeta missing at " + meta);
                try (BufferedReader reader = Files.newBufferedReader(meta)) {
                    int format = JsonParser.parseReader(reader)
                            .getAsJsonObject()
                            .getAsJsonObject("pack")
                            .get("pack_format")
                            .getAsInt();
                    helper.assertTrue(format == 48, s + "pack_format " + format);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        helper.succeed();
    }

    // ---- waypoints, landing point and roads ----

    @GameTest(template = "empty")
    public static void waypointsAndLandingAtEveryScale(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Registry<Waypoint> waypoints = server.registryAccess().registryOrThrow(ModDataRegistries.<Waypoint>waypoint());
        Waypoint hobbiton = waypoints.get(LOTRRevival.id("hobbiton"));
        Waypoint bree = waypoints.get(LOTRRevival.id("bree"));
        helper.assertTrue(hobbiton != null && bree != null, "pilot waypoints missing");
        for (WorldScale scale : WorldScale.values()) {
            String s = scale.id() + ": ";
            helper.assertTrue(hobbiton.blockX(scale) == 0 && hobbiton.blockZ(scale) == 0, s + "Hobbiton at (0, 0)");
            double bpp = scale.heightBlocksPerPixel();
            helper.assertTrue(
                    bree.blockX(scale) == 199.5 * bpp && bree.blockZ(scale) == 6 * bpp,
                    s + "Bree at " + bree.blockX(scale) + ", " + bree.blockZ(scale));
            // Landing point: heightmap pixel (1,623, 1,460), three pixels east of Hobbiton.
            var landing = MiddleEarthLanding.load(server.getResourceManager(), scale);
            helper.assertTrue(
                    landing.x() == (int) Math.round(3 * bpp) && landing.z() == 0,
                    s + "landing point " + landing);
        }
        // The running world uses its own scale's numbers.
        WorldScale running = WorldScales.current();
        var landing = MiddleEarthLanding.load(server.getResourceManager());
        helper.assertTrue(
                landing.x() == (int) Math.round(3 * running.heightBlocksPerPixel()) && landing.z() == 0,
                running.id() + " running landing point " + landing);
        helper.assertTrue(bree.blockX() == bree.blockX(running), "running waypoint position");
        helper.succeed();
    }

    /** Each road passes through its towns (within its half-width plus 16 blocks) and has no gaps, at every scale. */
    @GameTest(template = "empty")
    public static void roadsLineUpWithWaypointsAtEveryScale(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        Registry<Road> roads = access.registryOrThrow(ModDataRegistries.<Road>road());
        Registry<Waypoint> waypoints = access.registryOrThrow(ModDataRegistries.<Waypoint>waypoint());
        Map<String, List<String>> through = Map.of(
                "east_road", List.of("michel_delving", "bywater", "brandywine_bridge", "bree"),
                "hobbiton_lane", List.of("hobbiton", "bywater"));
        for (WorldScale scale : WorldScale.values()) {
            for (var entry : through.entrySet()) {
                ResourceLocation id = LOTRRevival.id(entry.getKey());
                Road road = roads.get(id);
                helper.assertTrue(road != null, "road " + id + " missing");
                RoadNetwork network = RoadNetwork.fromRoads(Map.of(id, road), scale);
                RoadNetwork.Entry sampled = network.roads().get(0);
                double[] xs = sampled.samples().x();
                double[] zs = sampled.samples().z();
                for (int i = 0; i + 1 < xs.length; i++) {
                    double gap = Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]);
                    helper.assertTrue(gap <= 2.0, scale.id() + ": " + id + " has a " + gap + "-block gap at " + i);
                }
                for (String town : entry.getValue()) {
                    Waypoint waypoint = waypoints.get(LOTRRevival.id(town));
                    helper.assertTrue(waypoint != null, "waypoint " + town + " missing");
                    var hit = network.nearest(waypoint.blockX(scale), waypoint.blockZ(scale), 1024);
                    double limit = road.width() + 16;
                    helper.assertTrue(
                            hit != null && hit.distance() <= limit,
                            scale.id() + ": " + id + " misses " + town + " by "
                                    + (hit == null ? "over 1024" : hit.distance()) + " blocks");
                }
            }
        }
        helper.succeed();
    }

    /** {@code travel.distance_factor} is per Legacy block: the same trip needs the same wait at every scale. */
    @GameTest(template = "empty")
    public static void travelDistanceFactorIsPerLegacyBlock(GameTestHelper helper) {
        TravelRules.setCooldownOverride(true);
        try {
            // 100,000 Legacy blocks: multiplier 1.2 on the 1,800 s first-trip wait.
            int legacy = TravelRules.requiredTicks(0, 100_000, WorldScale.LEGACY);
            int half = TravelRules.requiredTicks(0, 50_000, WorldScale.HALF);
            int quarter = TravelRules.requiredTicks(0, 25_000, WorldScale.QUARTER);
            helper.assertTrue(legacy == 43_200, "legacy " + legacy);
            helper.assertTrue(half == legacy && quarter == legacy, "half " + half + ", quarter " + quarter);
            // 25,000 blocks is a short trip at Legacy scale: no distance bonus.
            int shortTrip = TravelRules.requiredTicks(0, 25_000, WorldScale.LEGACY);
            helper.assertTrue(shortTrip == 36_000, "short legacy trip " + shortTrip);
        } finally {
            TravelRules.setCooldownOverride(null);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldKeepsTheScaleItWasMadeWith(GameTestHelper helper) {
        WorldScaleLock fresh = WorldScaleLock.EMPTY.settle(WorldScale.QUARTER, false);
        helper.assertTrue(
                fresh.madeWith().equals(Optional.of(WorldScale.QUARTER)) && !fresh.assumed(), "new world " + fresh);
        WorldScaleLock old = WorldScaleLock.EMPTY.settle(WorldScale.HALF, true);
        helper.assertTrue(
                old.madeWith().equals(Optional.of(WorldScale.LEGACY)) && old.assumed(), "pre-scale world " + old);
        helper.assertTrue(fresh.settle(WorldScale.HALF, true) == fresh, "a recorded scale never changes");
        helper.assertTrue(fresh.mismatch(WorldScale.QUARTER).isEmpty(), "same scale is no mismatch");
        helper.assertTrue(
                fresh.mismatch(WorldScale.HALF).equals(Optional.of(WorldScale.QUARTER)), "changed scale is reported");
        helper.assertTrue(old.mismatch(WorldScale.HALF).isPresent(), "an old Legacy world on the half default warns");
        helper.succeed();
    }

    // ---- terrain and biomes, end to end ----

    private static final ResourceKey<Registry<Object>> MAP_INFO =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("novoatlas", "map_info"));

    private static final String DF_PREFIX = LOTRRevival.MOD_ID + ":middle_earth/";

    /** Replaces every reference to one of our {@code middle_earth/*} density functions with its (scaled) file. */
    private static JsonElement inline(MinecraftServer server, WorldScale scale, JsonElement json) {
        if (json.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (var e : json.getAsJsonObject().entrySet()) {
                JsonElement value = e.getValue();
                if (!e.getKey().equals("type")
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                        && value.getAsString().startsWith(DF_PREFIX)) {
                    String path = value.getAsString().substring(LOTRRevival.MOD_ID.length() + 1);
                    out.add(e.getKey(), inline(server, scale, data(server, scale, "worldgen/density_function/" + path + ".json")));
                } else {
                    out.add(e.getKey(), inline(server, scale, value));
                }
            }
            return out;
        }
        if (json.isJsonArray()) {
            JsonArray out = new JsonArray();
            json.getAsJsonArray().forEach(element -> out.add(inline(server, scale, element)));
            return out;
        }
        return json;
    }

    /**
     * The Middle-earth level stem as a world at {@code scale} would load it: noise settings and density functions
     * inlined from the scale's files, and the three NovoAtlas map_infos replaced by the scale's versions.
     */
    @SuppressWarnings("unchecked")
    private static LevelStem stem(MinecraftServer server, WorldScale scale) {
        var access = server.registryAccess();
        Codec<Object> mapInfoCodec = (Codec<Object>) DataPackRegistriesHooks.getDataPackRegistries().stream()
                .filter(d -> d.key().equals(MAP_INFO))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("NovoAtlas map_info registry not found"))
                .elementCodec();
        RegistryOps<JsonElement> plain = RegistryOps.create(JsonOps.INSTANCE, access);
        MappedRegistry<Object> mapInfos = new MappedRegistry<>(MAP_INFO, Lifecycle.stable());
        for (String name : List.of("middle_earth", "middle_earth_biomes", "middle_earth_relief")) {
            Object info = mapInfoCodec
                    .parse(plain, data(server, scale, "novoatlas/map_info/" + name + ".json"))
                    .getOrThrow(msg -> new IllegalStateException(name + ": " + msg));
            mapInfos.register(ResourceKey.create(MAP_INFO, LOTRRevival.id(name)), info, RegistrationInfo.BUILT_IN);
        }
        mapInfos.freeze();
        HolderLookup.Provider lookups = HolderLookup.Provider.create(Stream.concat(
                access.registries()
                        .filter(entry -> !entry.key().equals(MAP_INFO))
                        .map(entry -> (HolderLookup.RegistryLookup<?>) entry.value().asLookup()),
                Stream.of(mapInfos.asLookup())));
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookups);

        JsonObject stem = data(server, scale, "dimension/middle_earth.json").getAsJsonObject().deepCopy();
        JsonObject generator = stem.getAsJsonObject("generator");
        generator.add(
                "settings",
                inline(server, scale, data(server, scale, "worldgen/noise_settings/middle_earth.json")));
        return LevelStem.CODEC.parse(ops, stem).getOrThrow(msg -> new IllegalStateException(scale.id() + ": " + msg));
    }

    private static ResourceKey<Biome> biomeAt(NoiseBasedChunkGenerator generator, RandomState random, int x, int z) {
        return generator
                .getBiomeSource()
                .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z), random.sampler())
                .unwrapKey()
                .orElseThrow();
    }

    private static int surface(NoiseBasedChunkGenerator generator, RandomState random, int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random) - 1;
    }

    private record Point(String name, double u, double v, ResourceKey<Biome> biome) {}

    private static final List<Point> POINTS = List.of(
            new Point("Hobbiton", 1620, 1460, MiddleEarthBiomes.SHIRE),
            new Point("Bree", 1819.5, 1466, MiddleEarthBiomes.BREE_LAND),
            new Point("Old Forest heart", 1745, 1535, MiddleEarthBiomes.OLD_FOREST),
            new Point("Barrow-downs stones", 1795, 1550, MiddleEarthBiomes.BARROW_DOWNS),
            new Point("Chetwood", 1868, 1416, MiddleEarthBiomes.CHETWOOD),
            new Point("White Downs", 1490, 1460, MiddleEarthBiomes.WHITE_DOWNS),
            new Point("Tower Hills", 1400, 1480, MiddleEarthBiomes.TOWER_HILLS),
            new Point("Lake Evendim", 1575, 1331, MiddleEarthBiomes.LAKE),
            new Point("Gulf of Lune", 1260, 1440, MiddleEarthBiomes.OCEAN));

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void terrainAndBiomesLineUpAtEveryScale(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var noises = server.registryAccess().lookupOrThrow(Registries.NOISE);
        Map<WorldScale, Integer> hills = new HashMap<>();
        for (WorldScale scale : WorldScale.values()) {
            String s = scale.id() + ": ";
            var generator = (NoiseBasedChunkGenerator) stem(server, scale).generator();
            RandomState random = RandomState.create(generator.generatorSettings().value(), noises, 12345L);
            for (Point point : POINTS) {
                int x = (int) Math.round(MiddleEarthMap.blockX(point.u(), scale));
                int z = (int) Math.round(MiddleEarthMap.blockZ(point.v(), scale));
                ResourceKey<Biome> actual = biomeAt(generator, random, x, z);
                helper.assertTrue(
                        actual == point.biome(),
                        s + point.name() + " at block (" + x + ", " + z + ") is " + actual.location());
            }
            var landing = MiddleEarthLanding.load(server.getResourceManager(), scale);
            helper.assertTrue(
                    biomeAt(generator, random, landing.x(), landing.z()) == MiddleEarthBiomes.SHIRE,
                    s + "landing point is not in the Shire");

            int hillX = (int) Math.round(MiddleEarthMap.blockX(1820, scale));
            int hillZ = (int) Math.round(MiddleEarthMap.blockZ(1462, scale));
            int hill = surface(generator, random, hillX, hillZ);
            double expected = scale.surfaceY(97);
            helper.assertTrue(
                    Math.abs(hill - expected) <= 5 + 2, s + "Bree-hill is " + hill + ", expected about " + expected);
            int farmland = surface(
                    generator,
                    random,
                    (int) Math.round(MiddleEarthMap.blockX(1790, scale)),
                    (int) Math.round(MiddleEarthMap.blockZ(1420, scale)));
            helper.assertTrue(
                    farmland < hill - 15 * scale.verticalFactor(),
                    s + "farmland " + farmland + " should sit well below Bree-hill " + hill);
            int origin = surface(generator, random, 0, 0);
            helper.assertTrue(
                    Math.abs(origin - scale.surfaceY(68)) <= 7 + 2, s + "surface at Hobbiton is " + origin);
            hills.put(scale, hill);
        }
        helper.assertTrue(
                hills.get(WorldScale.LEGACY) > hills.get(WorldScale.HALF)
                        && hills.get(WorldScale.HALF) > hills.get(WorldScale.QUARTER),
                "Bree-hill shrinks with the scale: " + hills);
        helper.succeed();
    }
}
