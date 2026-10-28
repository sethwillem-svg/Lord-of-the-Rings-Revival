package io.github.sethwillemsvg.lotrrevival.world.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.road.RoadMod;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** World pod datagen providers. Called from {@code DataGenerators}. */
public final class WorldDatagen {
    private WorldDatagen() {}

    /** Adds the World pod's datapack registry entries to the shared builder (one provider per mod). */
    public static void addRegistries(RegistrySetBuilder registries) {
        registries
                .add(Registries.DIMENSION_TYPE, WorldDatagen::bootstrapDimensionTypes)
                .add(Registries.CONFIGURED_FEATURE, WorldDatagen::bootstrapConfiguredFeatures)
                .add(Registries.PLACED_FEATURE, WorldDatagen::bootstrapPlacedFeatures)
                .add(Registries.BIOME, WorldDatagen::bootstrapBiomes);
    }

    /** Adds the World pod's other providers; {@code registryLookup} includes the shared datapack entries. */
    public static void gather(GatherDataEvent event, CompletableFuture<HolderLookup.Provider> registryLookup) {
        var generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existing = event.getExistingFileHelper();

        generator.addProvider(event.includeServer(), new MiddleEarthWorldgenProvider(output));
        generator.addProvider(event.includeServer(), new ReliefImageProvider(output));
        io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointSystem.contributeLang();
        io.github.sethwillemsvg.lotrrevival.world.portal.RingPortals.contributeLang();
        generator.addProvider(event.includeServer(), new MiddleEarthBiomeTags(output, registryLookup, existing));
    }

    private static void bootstrapDimensionTypes(BootstrapContext<DimensionType> context) {
        context.register(
                ModDimensions.MIDDLE_EARTH_TYPE,
                new DimensionType(
                        OptionalLong.empty(),
                        true, // has_skylight
                        false, // has_ceiling
                        false, // ultrawarm
                        true, // natural
                        1.0, // coordinate_scale
                        true, // bed_works
                        false, // respawn_anchor_works
                        -64, // min_y
                        384, // height
                        384, // logical_height
                        BlockTags.INFINIBURN_OVERWORLD,
                        BuiltinDimensionTypes.OVERWORLD_EFFECTS,
                        0.0f, // ambient_light
                        new DimensionType.MonsterSettings(
                                false, true, UniformInt.of(0, 7), 0)));
    }

    private static void bootstrapConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        context.register(
                RoadMod.ROAD_CONFIGURED,
                new ConfiguredFeature<>(RoadMod.ROAD_FEATURE.get(), NoneFeatureConfiguration.INSTANCE));
        OreFeatures.bootstrapConfigured(context);
    }

    private static void bootstrapPlacedFeatures(BootstrapContext<PlacedFeature> context) {
        var configured = context.lookup(Registries.CONFIGURED_FEATURE);
        context.register(
                RoadMod.ROAD_PLACED,
                new PlacedFeature(configured.getOrThrow(RoadMod.ROAD_CONFIGURED), List.of(BiomeFilter.biome())));
        OreFeatures.bootstrapPlaced(context);
    }

    private static void bootstrapBiomes(BootstrapContext<Biome> context) {
        var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        var carvers = context.lookup(Registries.CONFIGURED_CARVER);

        // Every biome gets its identity, climate and the passive animals only. Vegetation, decoration and the
        // faction spawn lists arrive with the biome and NPC tickets.
        for (ResourceKey<Biome> key : MiddleEarthBiomes.allKeys()) {
            Climate climate = climate(key);
            MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
            if (climate.farmAnimals()) {
                spawns.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SHEEP, 12, 2, 4));
                spawns.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PIG, 10, 1, 3));
                spawns.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.CHICKEN, 10, 2, 4));
                spawns.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.COW, 8, 2, 4));
            }
            context.register(
                    key,
                    biome(
                            climate.temperature(),
                            climate.downfall(),
                            WATER_COLOR,
                            WATER_FOG_COLOR,
                            spawns,
                            key,
                            placedFeatures,
                            carvers));
        }
    }

    /** Climate for one biome: temperature, downfall, and whether passive farm animals live there. */
    private record Climate(float temperature, float downfall, boolean farmAnimals) {}

    private static final int WATER_COLOR = 0x3F76E4;
    private static final int WATER_FOG_COLOR = 0x050533;

    private static Climate climate(ResourceKey<Biome> key) {
        return switch (key.location().getPath()) {
            case "ocean", "lake", "river" -> new Climate(0.5f, 0.5f, false);
            case "shore" -> new Climate(0.7f, 0.5f, false);
            case "shire", "bree_land" -> new Climate(0.8f, 0.8f, true);
            case "shire_woodlands" -> new Climate(0.8f, 0.9f, true);
            case "shire_marshes", "midgewater_marshes" -> new Climate(0.7f, 1.0f, false);
            case "shire_moors", "white_downs", "lone_lands" -> new Climate(0.6f, 0.6f, true);
            case "old_forest", "barrow_downs" -> new Climate(0.5f, 0.9f, false);
            case "chetwood" -> new Climate(0.8f, 0.9f, true);
            case "tower_hills" -> new Climate(0.7f, 0.8f, true);
            case "ered_luin_foothills" -> new Climate(0.4f, 0.8f, false);
            default -> throw new IllegalStateException("No climate for biome " + key.location());
        };
    }

    private static Biome biome(
            float temperature,
            float downfall,
            int waterColor,
            int waterFogColor,
            MobSpawnSettings.Builder spawns,
            ResourceKey<Biome> biomeKey,
            HolderGetter<PlacedFeature> placed,
            HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(placed, carvers);
        for (io.github.sethwillemsvg.lotrrevival.world.MiddleEarthOres.Spec ore :
                io.github.sethwillemsvg.lotrrevival.world.MiddleEarthOres.oresFor(biomeKey)) {
            generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ore.placed());
        }
        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(temperature)
                .downfall(downfall)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .waterColor(waterColor)
                        .waterFogColor(waterFogColor)
                        .fogColor(0xC0D8FF)
                        .skyColor(0x78A7FF)
                        .build())
                .mobSpawnSettings(spawns.build())
                .generationSettings(generation
                        // Roads: after surface structures, before vegetation (specs/world/waypoints-travel.md).
                        .addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, RoadMod.ROAD_PLACED)
                        .build())
                .build();
    }

    /** Fills {@code #lotrrevival:is_middle_earth} with every Middle-earth biome. */
    private static final class MiddleEarthBiomeTags extends TagsProvider<Biome> {
        MiddleEarthBiomeTags(
                PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper existing) {
            super(output, Registries.BIOME, lookup, LOTRRevival.MOD_ID, existing);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            TagAppender<Biome> tag = tag(ModTags.Biomes.IS_MIDDLE_EARTH);
            for (ResourceKey<Biome> key : MiddleEarthBiomes.allKeys()) {
                tag.add(key);
            }
        }
    }
}
