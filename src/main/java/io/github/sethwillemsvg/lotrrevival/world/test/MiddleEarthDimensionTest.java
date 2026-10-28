package io.github.sethwillemsvg.lotrrevival.world.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthLanding;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Proves the Middle-earth dimension loads, that its noise settings resolve the NovoAtlas map_info through the
 * painted-height density function, and that the projection puts the painted heights where the spec says.
 */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiddleEarthDimensionTest {
    /**
     * The GameTest server bakes its own level stems (three vanilla ones), so decode our datapack stem file with
     * the server's registries. That resolves the noise settings, the map_info references and the NovoAtlas
     * biome source exactly as a normal world would.
     */
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

    private static RandomState randomState(GameTestHelper helper, NoiseBasedChunkGenerator generator) {
        var noises = helper.getLevel().getServer().registryAccess().lookupOrThrow(Registries.NOISE);
        return RandomState.create(generator.generatorSettings().value(), noises, 12345L);
    }

    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);

    @GameTest(template = "empty")
    public static void dimensionUsesNoiseGeneratorAndNovoAtlasBiomes(GameTestHelper helper) {
        LevelStem stem = stem(helper);
        ChunkGenerator generator = stem.generator();
        helper.assertTrue(generator instanceof NoiseBasedChunkGenerator, "generator is not minecraft:noise");
        var noise = (NoiseBasedChunkGenerator) generator;
        helper.assertTrue(
                noise.generatorSettings().is(LOTRRevival.id("middle_earth")), "noise settings are not ours");
        JsonElement biomeSource = BiomeSource.CODEC
                .encodeStart(JsonOps.INSTANCE, generator.getBiomeSource())
                .getOrThrow();
        helper.assertTrue(
                biomeSource.getAsJsonObject().get("type").getAsString().equals("novoatlas:biome_cell_color_map"),
                "biome source is " + biomeSource);
        helper.assertTrue(stem.type().value().minY() == -64, "min_y");
        helper.assertTrue(stem.type().value().height() == 384, "height");
        helper.succeed();
    }

    /** The painted height at the origin is 68; at an unpainted column it is the y 40 ocean floor. */
    @GameTest(template = "empty")
    public static void noiseSettingsReadPaintedHeights(GameTestHelper helper) {
        var generator = (NoiseBasedChunkGenerator) stem(helper).generator();
        var random = randomState(helper, generator);

        // First free block above the floor, so surface y = result - 1. Origin is Shire: painted 68, amplitude 7.
        int origin = generator.getBaseHeight(0, 0, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random) - 1;
        helper.assertTrue(Math.abs(origin - 68) <= 7 + 2, "surface at origin was " + origin + ", expected 68 +- 9");

        // 60,000 blocks away: placeholder ocean, floor 40, amplitude 2.
        int ocean = generator.getBaseHeight(60000, 60000, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random) - 1;
        helper.assertTrue(Math.abs(ocean - 40) <= 2 + 2, "ocean floor was " + ocean + ", expected 40 +- 4");

        // Three blocks of the projection: the pixel centres of the heightmap line up with blocks.
        int east = generator.getBaseHeight(3000, 0, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random) - 1;
        helper.assertTrue(Math.abs(east - 68) <= 7 + 2, "surface at x=3000 was " + east + ", expected 68 +- 9");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void biomesFollowThePaintedMap(GameTestHelper helper) {
        var generator = (NoiseBasedChunkGenerator) stem(helper).generator();
        var sampler = randomState(helper, generator).sampler();

        var landing = MiddleEarthLanding.load(helper.getLevel().getServer().getResourceManager());
        helper.assertTrue(
                biomeAt(generator, sampler, landing.x(), landing.z()) == MiddleEarthBiomes.SHIRE,
                "landing point is not in the shire");
        helper.assertTrue(
                biomeAt(generator, sampler, 0, 0) == MiddleEarthBiomes.SHIRE, "block (0,0) is not in the shire");
        // Old Forest patch: centre at biome px (840, 730) = 30 px = 3840 blocks east of the origin.
        helper.assertTrue(
                biomeAt(generator, sampler, 3840, 0) == MiddleEarthBiomes.OLD_FOREST,
                "3840 blocks east should be the old forest");
        helper.assertTrue(
                biomeAt(generator, sampler, 60000, -60000) == MiddleEarthBiomes.OCEAN,
                "unpainted canvas should be ocean");
        // Biome pixel 38 px west of the origin (4864 blocks) is the last shire pixel edge; 6000 is ocean.
        helper.assertTrue(
                biomeAt(generator, sampler, -6000, 0) == MiddleEarthBiomes.OCEAN, "west coast should be ocean");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void centerOffsetsReproduceTheProjection(GameTestHelper helper) {
        // Reproduce NovoAtlas's index formula: index = (block - offset * size) / scale + size / 2.
        double terrainX = (0 - MiddleEarthMap.terrainOffsetX() * MiddleEarthMap.HEIGHT_WIDTH)
                        / MiddleEarthMap.HEIGHT_BLOCKS_PER_PIXEL
                + MiddleEarthMap.HEIGHT_WIDTH / 2.0;
        double terrainZ = (0 - MiddleEarthMap.terrainOffsetZ() * MiddleEarthMap.HEIGHT_HEIGHT)
                        / MiddleEarthMap.HEIGHT_BLOCKS_PER_PIXEL
                + MiddleEarthMap.HEIGHT_HEIGHT / 2.0;
        helper.assertTrue(
                Math.abs(terrainX - 1619.5) * 64 < 0.05 && Math.abs(terrainZ - 1459.5) * 64 < 0.05,
                "terrain offset index " + terrainX + "," + terrainZ);
        helper.assertTrue(Math.abs(MiddleEarthMap.terrainOffsetX() - 15.805f) < 1e-4, "terrain offset x");
        helper.assertTrue(Math.abs(MiddleEarthMap.terrainOffsetZ() - 20.324f) < 1e-4, "terrain offset z");
        helper.assertTrue(Math.abs(MiddleEarthMap.biomesOffsetX() - 7.9f) < 1e-4, "biomes offset x");
        helper.assertTrue(Math.abs(MiddleEarthMap.biomesOffsetZ() - 10.16f) < 1e-4, "biomes offset z");
        helper.assertTrue(Math.abs(MiddleEarthMap.reliefOffsetX() - 63.28f) < 1e-3, "relief offset x");
        helper.assertTrue(Math.abs(MiddleEarthMap.reliefOffsetZ() - 81.344f) < 1e-3, "relief offset z");
        helper.succeed();
    }

    private static ResourceKey<Biome> biomeAt(
            ChunkGenerator generator, Climate.Sampler sampler, int x, int z) {
        return generator
                .getBiomeSource()
                .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z), sampler)
                .unwrapKey()
                .orElseThrow();
    }
}
