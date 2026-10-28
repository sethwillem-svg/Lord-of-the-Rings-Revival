package io.github.sethwillemsvg.lotrrevival.world.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** World pod datagen providers. Called from {@code DataGenerators}. */
public final class WorldDatagen {
    private WorldDatagen() {}

    public static void gather(GatherDataEvent event) {
        var generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper existing = event.getExistingFileHelper();

        RegistrySetBuilder registries = new RegistrySetBuilder()
                .add(Registries.DIMENSION_TYPE, WorldDatagen::bootstrapDimensionTypes)
                .add(Registries.BIOME, WorldDatagen::bootstrapBiomes);
        var entries = generator.addProvider(
                event.includeServer(),
                new DatapackBuiltinEntriesProvider(output, lookup, registries, Set.of(LOTRRevival.MOD_ID)));

        generator.addProvider(event.includeServer(), new MiddleEarthWorldgenProvider(output));
        generator.addProvider(event.includeServer(), new ReliefImageProvider(output));
        generator.addProvider(
                event.includeServer(),
                new MiddleEarthBiomeTags(output, entries.getRegistryProvider(), existing));
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

    private static void bootstrapBiomes(BootstrapContext<Biome> context) {
        var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        var carvers = context.lookup(Registries.CONFIGURED_CARVER);

        // Open sea (also the placeholder canvas). No spawns, no decoration yet.
        context.register(
                MiddleEarthBiomes.OCEAN,
                biome(0.5f, 0.5f, 0x1F3A93, 0x050533, new MobSpawnSettings.Builder(), placedFeatures, carvers));

        // Shire: passive farm animals only. Vegetation and decoration arrive with the biome ticket.
        MobSpawnSettings.Builder shire = new MobSpawnSettings.Builder();
        shire.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SHEEP, 12, 2, 4));
        shire.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PIG, 10, 1, 3));
        shire.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.CHICKEN, 10, 2, 4));
        shire.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.COW, 8, 2, 4));
        context.register(
                MiddleEarthBiomes.SHIRE, biome(0.7f, 0.6f, 0x3F76E4, 0x050533, shire, placedFeatures, carvers));

        context.register(
                MiddleEarthBiomes.OLD_FOREST,
                biome(0.6f, 0.8f, 0x3F76E4, 0x050533, new MobSpawnSettings.Builder(), placedFeatures, carvers));
    }

    private static Biome biome(
            float temperature,
            float downfall,
            int waterColor,
            int waterFogColor,
            MobSpawnSettings.Builder spawns,
            HolderGetter<PlacedFeature> placed,
            HolderGetter<ConfiguredWorldCarver<?>> carvers) {
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
                .generationSettings(new BiomeGenerationSettings.Builder(placed, carvers).build())
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
            for (MiddleEarthBiomes.Entry entry : MiddleEarthBiomes.TABLE) {
                tag.add(entry.key());
            }
        }
    }
}
