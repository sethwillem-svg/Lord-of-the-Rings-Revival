package io.github.sethwillemsvg.lotrrevival.world.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthMap;
import io.github.sethwillemsvg.lotrrevival.world.WorldScale;
import io.github.sethwillemsvg.lotrrevival.world.WorldScales;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Writes the Middle-earth worldgen JSON that refers to NovoAtlas types: the three map_infos, the level stem,
 * noise settings, detail noises and density functions.
 *
 * <p>The mod's data uses {@link WorldScale#DEFAULT}. For every other scale it also writes a small built-in data
 * pack under {@code datapacks/world_scale_<id>/} with the files that depend on the scale (see {@code WorldScales}).
 *
 * <p>Raw JSON on purpose: NovoAtlas types are only referenced by id, so no NovoAtlas class is needed at
 * compile time or datagen time. The shapes follow the NovoAtlas 1.8.2 wiki and
 * {@code specs/world/dimension-and-mapgen.md}.
 */
public final class MiddleEarthWorldgenProvider implements DataProvider {
    private static final String DF_PREFIX = LOTRRevival.MOD_ID + ":middle_earth/";

    private final PackOutput output;

    public MiddleEarthWorldgenProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        // The mod's own data is the default scale.
        String data = "data/" + LOTRRevival.MOD_ID + "/";
        writeScaled(cache, writes, data, WorldScale.DEFAULT);
        // dimension
        write(cache, writes, data + "dimension/middle_earth", levelStem());
        write(cache, writes, data + "worldgen/noise_settings/middle_earth", noiseSettings());
        // detail noises
        write(cache, writes, data + "worldgen/noise/relief_large", noise(-7, 1.0, 1.0, 0.5));
        write(cache, writes, data + "worldgen/noise/relief_small", noise(-5, 1.0, 1.0, 0.5));
        // density functions
        write(cache, writes, data + "worldgen/density_function/middle_earth/amplitude", amplitude());
        write(cache, writes, data + "worldgen/density_function/middle_earth/detail", detail());
        write(cache, writes, data + "worldgen/density_function/middle_earth/surface_y", surfaceY());
        write(cache, writes, data + "worldgen/density_function/middle_earth/terrain", terrain());
        write(cache, writes, data + "worldgen/density_function/middle_earth/initial", initial());
        write(cache, writes, data + "worldgen/density_function/middle_earth/final", finalDensity());
        // The other scales: optional built-in data packs that override only the scale-dependent files.
        for (WorldScale scale : WorldScales.PACK_SCALES) {
            String root = packRoot(scale);
            write(cache, writes, root + "pack", packMeta(scale));
            writeScaled(cache, writes, root + "data/" + LOTRRevival.MOD_ID + "/", scale);
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    /** Folder of a scale's data pack inside the mod jar (see {@code WorldScales}). */
    public static String packRoot(WorldScale scale) {
        return "datapacks/" + scale.packName() + "/";
    }

    /** The files that differ between scales: the three map_infos, the painted height and the scale entry. */
    private void writeScaled(CachedOutput cache, List<CompletableFuture<?>> writes, String data, WorldScale scale) {
        write(cache, writes, data + "novoatlas/map_info/middle_earth", terrainInfo(scale));
        write(cache, writes, data + "novoatlas/map_info/middle_earth_biomes", biomesInfo(scale));
        write(cache, writes, data + "novoatlas/map_info/middle_earth_relief", reliefInfo(scale));
        write(cache, writes, data + "worldgen/density_function/middle_earth/base", base(scale));
        JsonObject setting = obj();
        setting.addProperty("scale", scale.id());
        write(cache, writes, data + LOTRRevival.MOD_ID + "/world_scale/middle_earth", setting);
    }

    /** {@code pack.mcmeta}: data pack format 48 is Minecraft 1.21.1. */
    private static JsonObject packMeta(WorldScale scale) {
        JsonObject pack = obj();
        pack.addProperty(
                "description",
                "Middle-earth at " + scale.id() + " scale: one map pixel is " + (int) scale.heightBlocksPerPixel()
                        + " blocks. Choose when creating a world; never change it afterwards.");
        pack.addProperty("pack_format", 48);
        JsonObject meta = obj();
        meta.add("pack", pack);
        return meta;
    }

    @Override
    public String getName() {
        return "Middle-earth worldgen";
    }

    /** {@code path} is relative to the output root and has no extension; {@code pack} becomes pack.mcmeta. */
    private void write(CachedOutput cache, List<CompletableFuture<?>> writes, String path, JsonElement json) {
        String file = path.endsWith("/pack") ? path + ".mcmeta" : path + ".json";
        writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(file)));
    }

    // ---- map_infos ----

    private static JsonObject mapInfo(
            String heightMapKey,
            double verticalScale,
            String interpolation,
            double horizontalScale,
            float offsetX,
            float offsetZ) {
        JsonObject horizontal = obj();
        horizontal.addProperty("interpolation", interpolation);
        horizontal.addProperty("value", horizontalScale);
        JsonObject scaling = obj();
        scaling.addProperty("vertical_scale", verticalScale);
        scaling.add("horizontal_scale", horizontal);

        JsonArray biomes = new JsonArray();
        for (MiddleEarthBiomes.Entry entry : MiddleEarthBiomes.TABLE) {
            JsonObject biome = obj();
            biome.addProperty("biome", entry.key().location().toString());
            biome.addProperty("color", String.format("#%06X", entry.color()));
            biomes.add(biome);
        }
        JsonObject surface = obj();
        surface.addProperty("map", MiddleEarthMap.BIOME_MAP_IMAGE.toString());
        surface.addProperty("strict", true);
        surface.add("biomes", biomes);

        JsonArray offset = new JsonArray();
        offset.add(offsetX);
        offset.add(offsetZ);

        JsonObject info = obj();
        info.addProperty("height_map", heightMapKey);
        info.addProperty("starting_y", 0);
        info.add("scaling", scaling);
        info.add("center_offset", offset);
        info.addProperty("image_wrapping", "clamp_to_edge");
        info.add("surface_biomes", surface);
        return info;
    }

    private static JsonObject terrainInfo(WorldScale scale) {
        return mapInfo(
                MiddleEarthMap.HEIGHTMAP_IMAGE.toString(),
                MiddleEarthMap.HEIGHT_VERTICAL_SCALE,
                "novoatlas:bicubic",
                scale.heightBlocksPerPixel(),
                MiddleEarthMap.terrainOffsetX(scale),
                MiddleEarthMap.terrainOffsetZ(scale));
    }

    /** The biome source reads its map at 1/4 of the scale, in quarts: at Legacy 32 quarts per px is 128 blocks. */
    private static JsonObject biomesInfo(WorldScale scale) {
        return mapInfo(
                MiddleEarthMap.HEIGHTMAP_IMAGE.toString(),
                MiddleEarthMap.HEIGHT_VERTICAL_SCALE,
                "novoatlas:nearest_neighbor",
                MiddleEarthMap.biomeQuartsPerPixel(scale),
                MiddleEarthMap.biomesOffsetX(scale),
                MiddleEarthMap.biomesOffsetZ(scale));
    }

    private static JsonObject reliefInfo(WorldScale scale) {
        return mapInfo(
                MiddleEarthMap.RELIEF_IMAGE.toString(),
                MiddleEarthMap.RELIEF_VERTICAL_SCALE,
                "novoatlas:bilinear",
                scale.reliefBlocksPerPixel(),
                MiddleEarthMap.reliefOffsetX(scale),
                MiddleEarthMap.reliefOffsetZ(scale));
    }

    // ---- dimension ----

    private static JsonObject levelStem() {
        JsonObject biomeSource = obj();
        biomeSource.addProperty("type", "novoatlas:biome_cell_color_map");
        biomeSource.addProperty("map_info", MiddleEarthMap.BIOMES_INFO.toString());
        biomeSource.addProperty("default_biome", MiddleEarthBiomes.OCEAN.location().toString());

        JsonObject generator = obj();
        generator.addProperty("type", "minecraft:noise");
        generator.addProperty("settings", LOTRRevival.id("middle_earth").toString());
        generator.add("biome_source", biomeSource);

        JsonObject stem = obj();
        stem.addProperty("type", LOTRRevival.id("middle_earth").toString());
        stem.add("generator", generator);
        return stem;
    }

    private static JsonObject noiseSettings() {
        JsonObject noise = obj();
        noise.addProperty("min_y", -64);
        noise.addProperty("height", 384);
        noise.addProperty("size_horizontal", 1);
        noise.addProperty("size_vertical", 2);

        JsonObject router = obj();
        router.add("barrier", noiseDf("minecraft:aquifer_barrier", 0.5));
        router.add("fluid_level_floodedness", noiseDf("minecraft:aquifer_fluid_level_floodedness", 0.67));
        router.add("fluid_level_spread", noiseDf("minecraft:aquifer_fluid_level_spread", 0.7142857142857143));
        router.add("lava", noiseDf("minecraft:aquifer_lava", 1.0));
        for (String zero : List.of("temperature", "vegetation", "continents", "erosion", "depth", "ridges",
                "vein_toggle", "vein_ridged", "vein_gap")) {
            router.addProperty(zero, 0.0);
        }
        router.addProperty("initial_density_without_jaggedness", DF_PREFIX + "initial");
        router.addProperty("final_density", DF_PREFIX + "final");

        JsonObject settings = obj();
        settings.add("noise", noise);
        settings.add("default_block", state("minecraft:stone", null));
        settings.add("default_fluid", state("minecraft:water", "level", "0"));
        settings.add("noise_router", router);
        settings.add("surface_rule", surfaceRule());
        settings.add("spawn_target", new JsonArray());
        settings.addProperty("sea_level", MiddleEarthMap.SEA_LEVEL);
        settings.addProperty("disable_mob_generation", false);
        settings.addProperty("aquifers_enabled", true);
        settings.addProperty("ore_veins_enabled", false);
        settings.addProperty("legacy_random_source", false);
        return settings;
    }

    private static JsonObject noiseDf(String noise, double yScale) {
        JsonObject df = obj();
        df.addProperty("type", "minecraft:noise");
        df.addProperty("noise", noise);
        df.addProperty("xz_scale", 1.0);
        df.addProperty("y_scale", yScale);
        return df;
    }

    /** Placeholder surface rules: bedrock floor, grass over dirt above water, sand under it. */
    private static JsonObject surfaceRule() {
        JsonObject bedrock = obj();
        bedrock.addProperty("type", "minecraft:condition");
        JsonObject gradient = obj();
        gradient.addProperty("type", "minecraft:vertical_gradient");
        gradient.addProperty("random_name", "minecraft:bedrock_floor");
        gradient.add("true_at_and_below", above(-64));
        gradient.add("false_at_and_above", above(-59));
        bedrock.add("if_true", gradient);
        bedrock.add("then_run", block(state("minecraft:bedrock", null)));

        JsonObject notUnderwater = obj();
        notUnderwater.addProperty("type", "minecraft:water");
        notUnderwater.addProperty("offset", -1);
        notUnderwater.addProperty("surface_depth_multiplier", 0);
        notUnderwater.addProperty("add_stone_depth", false);

        JsonObject topCondition = obj();
        topCondition.addProperty("type", "minecraft:condition");
        topCondition.add("if_true", notUnderwater);
        topCondition.add("then_run", block(state("minecraft:grass_block", "snowy", "false")));

        JsonObject floorTop = obj();
        floorTop.addProperty("type", "minecraft:condition");
        floorTop.add("if_true", stoneDepth(false));
        floorTop.add("then_run", sequence(topCondition, block(state("minecraft:sand", null))));

        JsonObject floorDirt = obj();
        floorDirt.addProperty("type", "minecraft:condition");
        floorDirt.add("if_true", stoneDepth(true));
        floorDirt.add("then_run", block(state("minecraft:dirt", null)));

        return sequence(bedrock, floorTop, floorDirt);
    }

    private static JsonObject above(int y) {
        JsonObject anchor = obj();
        anchor.addProperty("absolute", y);
        return anchor;
    }

    private static JsonObject stoneDepth(boolean addSurfaceDepth) {
        JsonObject depth = obj();
        depth.addProperty("type", "minecraft:stone_depth");
        depth.addProperty("offset", 0);
        depth.addProperty("surface_type", "floor");
        depth.addProperty("add_surface_depth", addSurfaceDepth);
        depth.addProperty("secondary_depth_range", 0);
        return depth;
    }

    private static JsonObject block(JsonObject state) {
        JsonObject rule = obj();
        rule.addProperty("type", "minecraft:block");
        rule.add("result_state", state);
        return rule;
    }

    private static JsonObject sequence(JsonObject... rules) {
        JsonArray array = new JsonArray();
        for (JsonObject rule : rules) {
            array.add(rule);
        }
        JsonObject sequence = obj();
        sequence.addProperty("type", "minecraft:sequence");
        sequence.add("sequence", array);
        return sequence;
    }

    private static JsonObject state(String name, String property, String... value) {
        JsonObject state = obj();
        state.addProperty("Name", name);
        if (property != null) {
            JsonObject properties = obj();
            properties.addProperty(property, value[0]);
            state.add("Properties", properties);
        }
        return state;
    }

    // ---- noises ----

    private static JsonObject noise(int firstOctave, double... amplitudes) {
        JsonArray array = new JsonArray();
        for (double amplitude : amplitudes) {
            array.add(amplitude);
        }
        JsonObject noise = obj();
        noise.addProperty("firstOctave", firstOctave);
        noise.add("amplitudes", array);
        return noise;
    }

    // ---- density functions ----
    //
    // surface_y = cache_2d(base + detail)
    // base      = painted height (NovoAtlas returns height - 8, so add 8 back)
    // detail    = amplitude * (0.7 * relief_large + 0.3 * relief_small)

    private static JsonObject mapHeight(String mapInfo, int lower, int upper) {
        JsonObject df = obj();
        df.addProperty("type", "novoatlas:get_preliminary_height_from_map");
        df.addProperty("map_info", mapInfo);
        df.addProperty("lower_bound", lower);
        df.addProperty("upper_bound", upper);
        return df;
    }

    /**
     * Painted height at {@code scale}: {@code sea + k x (painted - sea)} with {@code k} the scale's vertical factor
     * (see {@link WorldScale}). At Legacy ({@code k} = 1) this is exactly {@code painted}, so Legacy worlds made
     * before world scales existed keep generating the same terrain.
     */
    private static JsonObject base(WorldScale scale) {
        JsonObject painted = mapHeight(MiddleEarthMap.TERRAIN_INFO.toString(), -64, 320);
        double k = scale.verticalFactor();
        if (k == 1.0) {
            return add(8, painted);
        }
        // k x (raw + 8) + sea x (1 - k) = (8k + sea x (1 - k)) + k x raw
        double constant = 8 * k + MiddleEarthMap.SEA_LEVEL * (1 - k);
        return add(constant, mul(k, painted));
    }

    private static JsonObject amplitude() {
        return add(8, mapHeight(MiddleEarthMap.RELIEF_INFO.toString(), -8, 64));
    }

    private static JsonObject detail() {
        JsonObject large = mul(0.7, noiseDf(LOTRRevival.id("relief_large").toString(), 0.0));
        JsonObject small = mul(0.3, noiseDf(LOTRRevival.id("relief_small").toString(), 0.0));
        JsonObject raw = mul(DF_PREFIX + "amplitude", add(large, small));
        // Roads flatten the detail within their half-width and fade it back in over 32 blocks.
        JsonObject corridors = obj();
        corridors.addProperty("type", LOTRRevival.id("map_corridors").toString());
        corridors.add("input", raw);
        corridors.addProperty("falloff", 32);
        return corridors;
    }

    private static JsonObject surfaceY() {
        JsonObject cache = obj();
        cache.addProperty("type", "minecraft:cache_2d");
        cache.add("argument", add(DF_PREFIX + "base", DF_PREFIX + "detail"));
        return cache;
    }

    /** {@code surface_y - y}, using a gradient that equals {@code -y} between y -64 and 320. */
    private static JsonObject heightAboveY() {
        JsonObject negY = obj();
        negY.addProperty("type", "minecraft:y_clamped_gradient");
        negY.addProperty("from_y", -64);
        negY.addProperty("to_y", 320);
        negY.addProperty("from_value", 64);
        negY.addProperty("to_value", -320);
        return add(DF_PREFIX + "surface_y", negY);
    }

    private static JsonObject clampUnit(JsonElement input) {
        JsonObject clamp = obj();
        clamp.addProperty("type", "minecraft:clamp");
        clamp.add("input", input);
        clamp.addProperty("min", -1.0);
        clamp.addProperty("max", 1.0);
        return clamp;
    }

    private static JsonObject terrain() {
        JsonObject interpolated = obj();
        interpolated.addProperty("type", "minecraft:interpolated");
        interpolated.add("argument", clampUnit(mul(1.0 / 128.0, heightAboveY())));
        return interpolated;
    }

    private static JsonObject initial() {
        return clampUnit(mul(1.0 / 128.0, add(-8, heightAboveY())));
    }

    private static JsonObject finalDensity() {
        JsonObject min = obj();
        min.addProperty("type", "minecraft:min");
        min.addProperty("argument1", DF_PREFIX + "terrain");
        min.addProperty("argument2", "novoatlas:no_cave_entrances");
        return min;
    }

    private static JsonObject add(Object a, Object b) {
        return binary("minecraft:add", a, b);
    }

    private static JsonObject mul(Object a, Object b) {
        return binary("minecraft:mul", a, b);
    }

    private static JsonObject binary(String type, Object a, Object b) {
        JsonObject df = obj();
        df.addProperty("type", type);
        put(df, "argument1", a);
        put(df, "argument2", b);
        return df;
    }

    private static void put(JsonObject json, String key, Object value) {
        if (value instanceof JsonElement element) {
            json.add(key, element);
        } else if (value instanceof Number number) {
            json.addProperty(key, number);
        } else {
            json.addProperty(key, value.toString());
        }
    }

    private static JsonObject obj() {
        return new JsonObject();
    }
}
