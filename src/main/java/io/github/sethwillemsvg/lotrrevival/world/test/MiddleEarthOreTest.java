package io.github.sethwillemsvg.lotrrevival.world.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialBlocks;
import io.github.sethwillemsvg.lotrrevival.core.material.Metal;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthBiomes;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthOres;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Decodes the ore features and the Middle-earth biomes from the data pack. The GameTest server has no
 * Middle-earth level, so this checks the data rather than generating chunks.
 */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiddleEarthOreTest {
    private static Set<ResourceKey<PlacedFeature>> oresIn(GameTestHelper helper, ResourceKey<Biome> key) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Biome biome = biomes.getOrThrow(key);
        Set<ResourceKey<PlacedFeature>> found = new HashSet<>();
        for (var step : biome.getGenerationSettings().features()) {
            step.forEach(holder -> holder.unwrapKey().ifPresent(found::add));
        }
        return found;
    }

    @GameTest(template = "empty")
    public static void everyBiomeHasCopperTinSilverOnly(GameTestHelper helper) {
        for (ResourceKey<Biome> key : MiddleEarthBiomes.allKeys()) {
            Set<ResourceKey<PlacedFeature>> found = oresIn(helper, key);
            for (var ore : new MiddleEarthOres.Spec[] {
                MiddleEarthOres.COPPER, MiddleEarthOres.TIN, MiddleEarthOres.SILVER
            }) {
                helper.assertTrue(found.contains(ore.placed()), key.location() + " is missing " + ore.id());
            }
            helper.assertTrue(
                    !found.contains(MiddleEarthOres.MITHRIL.placed()),
                    key.location() + " must not have mithril (Misty Mountains only)");
            helper.assertTrue(
                    !found.contains(MiddleEarthOres.MORGUL_IRON.placed()),
                    key.location() + " must not have Morgul iron (Mordor, Angmar, Dol Guldur only)");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void biomePolicyPlacesRareOresInTheRightBiomes(GameTestHelper helper) {
        ResourceKey<Biome> misty = ResourceKey.create(Registries.BIOME, LOTRRevival.id("misty_mountains"));
        ResourceKey<Biome> mordor = ResourceKey.create(Registries.BIOME, LOTRRevival.id("gorgoroth"));
        ResourceKey<Biome> angmar = ResourceKey.create(Registries.BIOME, LOTRRevival.id("angmar"));
        helper.assertTrue(MiddleEarthOres.oresFor(misty).contains(MiddleEarthOres.MITHRIL), "Misty Mountains mithril");
        helper.assertTrue(!MiddleEarthOres.oresFor(misty).contains(MiddleEarthOres.MORGUL_IRON), "no Morgul iron");
        helper.assertTrue(MiddleEarthOres.oresFor(mordor).contains(MiddleEarthOres.MORGUL_IRON), "Mordor Morgul iron");
        helper.assertTrue(!MiddleEarthOres.oresFor(mordor).contains(MiddleEarthOres.MITHRIL), "Mordor no mithril");
        helper.assertTrue(MiddleEarthOres.oresFor(angmar).contains(MiddleEarthOres.MORGUL_IRON), "Angmar Morgul iron");
        for (ResourceKey<Biome> key : MiddleEarthBiomes.allKeys()) {
            helper.assertTrue(!MiddleEarthOres.oresFor(key).contains(MiddleEarthOres.MITHRIL), key.location().toString());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void featuresDecodeWithLegacyNumbers(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var configured = access.registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        for (MiddleEarthOres.Spec spec : MiddleEarthOres.ALL) {
            ConfiguredFeature<?, ?> feature = configured.getOrThrow(spec.configured());
            helper.assertTrue(feature.config() instanceof OreConfiguration, spec.id() + " is an ore feature");
            OreConfiguration ore = (OreConfiguration) feature.config();
            helper.assertTrue(ore.size == spec.veinSize(), spec.id() + " vein size " + ore.size);
            helper.assertTrue(ore.targetStates.size() == 2, spec.id() + " has stone and deepslate targets");

            PlacedFeature placedFeature = placed.getOrThrow(spec.placed());
            boolean hasHeight = placedFeature.placement().stream().anyMatch(HeightRangePlacement.class::isInstance);
            helper.assertTrue(hasHeight, spec.id() + " has a height range");
            helper.assertTrue(spec.minY() >= -64 && spec.maxY() <= 320, spec.id() + " range inside the dimension");
        }
        helper.assertTrue(
                placed.getOrThrow(MiddleEarthOres.MITHRIL.placed()).placement().stream()
                        .anyMatch(RarityFilter.class::isInstance),
                "mithril is one attempt per four chunks");
        // Legacy 0-32 and 0-16 stay at the bottom of the world.
        helper.assertTrue(MiddleEarthOres.SILVER.maxY() == -32, "silver top");
        helper.assertTrue(MiddleEarthOres.MITHRIL.maxY() == -48, "mithril top");

        var stoneTin = ((OreConfiguration) configured.getOrThrow(MiddleEarthOres.TIN.configured()).config())
                .targetStates.get(0).state.getBlock();
        helper.assertTrue(stoneTin == MaterialBlocks.ORE.get(Metal.TIN).get(), "tin places tin ore");
        var stoneCopper = ((OreConfiguration) configured.getOrThrow(MiddleEarthOres.COPPER.configured()).config())
                .targetStates.get(0).state.getBlock();
        helper.assertTrue(stoneCopper == Blocks.COPPER_ORE, "copper is vanilla copper");
        helper.succeed();
    }
}
