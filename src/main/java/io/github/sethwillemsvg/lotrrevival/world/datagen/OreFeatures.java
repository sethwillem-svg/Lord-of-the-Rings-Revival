package io.github.sethwillemsvg.lotrrevival.world.datagen;

import io.github.sethwillemsvg.lotrrevival.core.material.MaterialBlocks;
import io.github.sethwillemsvg.lotrrevival.core.material.Metal;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthOres;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

/** Configured and placed features for {@link MiddleEarthOres}. */
final class OreFeatures {
    private OreFeatures() {}

    static void bootstrapConfigured(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        for (MiddleEarthOres.Spec spec : MiddleEarthOres.ALL) {
            List<OreConfiguration.TargetBlockState> targets = new ArrayList<>();
            targets.add(OreConfiguration.target(
                    new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), stoneOre(spec).defaultBlockState()));
            targets.add(OreConfiguration.target(
                    new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), deepslateOre(spec).defaultBlockState()));
            context.register(
                    spec.configured(),
                    new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(targets, spec.veinSize())));
        }
    }

    static void bootstrapPlaced(BootstrapContext<PlacedFeature> context) {
        var configured = context.lookup(Registries.CONFIGURED_FEATURE);
        for (MiddleEarthOres.Spec spec : MiddleEarthOres.ALL) {
            List<PlacementModifier> placement = new ArrayList<>();
            if (spec.perChunk() >= 1) {
                placement.add(CountPlacement.of((int) spec.perChunk()));
            } else {
                placement.add(RarityFilter.onAverageOnceEvery((int) Math.round(1.0 / spec.perChunk())));
            }
            placement.add(InSquarePlacement.spread());
            placement.add(HeightRangePlacement.uniform(
                    VerticalAnchor.absolute(spec.minY()), VerticalAnchor.absolute(spec.maxY())));
            placement.add(BiomeFilter.biome());
            context.register(spec.placed(), new PlacedFeature(configured.getOrThrow(spec.configured()), placement));
        }
    }

    private static Block stoneOre(MiddleEarthOres.Spec spec) {
        return spec == MiddleEarthOres.COPPER ? Blocks.COPPER_ORE : MaterialBlocks.ORE.get(metal(spec)).get();
    }

    private static Block deepslateOre(MiddleEarthOres.Spec spec) {
        return spec == MiddleEarthOres.COPPER
                ? Blocks.DEEPSLATE_COPPER_ORE
                : MaterialBlocks.DEEPSLATE_ORE.get(metal(spec)).get();
    }

    private static Metal metal(MiddleEarthOres.Spec spec) {
        if (spec == MiddleEarthOres.TIN) {
            return Metal.TIN;
        } else if (spec == MiddleEarthOres.SILVER) {
            return Metal.SILVER;
        } else if (spec == MiddleEarthOres.MORGUL_IRON) {
            return Metal.MORGUL_IRON;
        } else if (spec == MiddleEarthOres.MITHRIL) {
            return Metal.MITHRIL;
        }
        throw new IllegalArgumentException(spec.id());
    }
}
