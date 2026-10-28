package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.building.ArnorBlocks;
import io.github.sethwillemsvg.lotrrevival.core.building.GoldRingItems;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Core: gold ring and Arnor brick set datagen (models, states, loot, tags, recipes, lang). */
final class ArnorProviders {
    private ArnorProviders() {}

    /** Registers the lang entries through the shared en_us provider. */
    static void contributeLang() {
        ModLangProvider.contribute(lang -> {
            lang.add("item.lotrrevival.gold_ring", "Gold Ring");
            for (ArnorBlocks.Variant v : ArnorBlocks.VARIANTS) {
                String title = title(v.name());
                lang.add("block.lotrrevival." + v.name(), title);
                lang.add("block.lotrrevival." + v.name() + "_slab", title + " Slab");
                lang.add("block.lotrrevival." + v.name() + "_stairs", title + " Stairs");
            }
        });
    }

    private static String title(String id) {
        StringBuilder sb = new StringBuilder();
        for (String word : id.split("_")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    static final class BlockStates extends BlockStateProvider {
        BlockStates(PackOutput output, ExistingFileHelper helper) {
            super(output, LOTRRevival.MOD_ID, helper);
        }

        @Override
        protected void registerStatesAndModels() {
            itemModels().basicItem(GoldRingItems.GOLD_RING.get());
            for (ArnorBlocks.Variant v : ArnorBlocks.VARIANTS) {
                var texture = modLoc("block/" + v.name());
                var full = models().cubeAll(v.name(), texture);
                simpleBlock(v.block().get(), full);
                simpleBlockItem(v.block().get(), full);
                slabBlock(v.slab().get(), full.getLocation(), texture);

                itemModels().withExistingParent(v.name() + "_slab", modLoc("block/" + v.name() + "_slab"));
                stairsBlock(v.stairs().get(), texture);
                itemModels().withExistingParent(v.name() + "_stairs", modLoc("block/" + v.name() + "_stairs"));
            }
        }

        @Override
        public String getName() {
            return "Arnor block states";
        }
    }

    /**
     * Arnor's vanilla block tags, written by {@link VanillaTagProviders}. Its {@code #lotrrevival:road_surfaces}
     * entries live in {@link RoadSurfaceTags}, the single writer of that tag.
     */
    static void contributeVanillaTags() {
        VanillaTagProviders.contributeBlocks(tag -> {
            for (ArnorBlocks.Variant v : ArnorBlocks.VARIANTS) {
                tag.apply(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                        .add(v.block().get(), v.slab().get(), v.stairs().get());
                tag.apply(net.minecraft.tags.BlockTags.SLABS).add(v.slab().get());
                tag.apply(net.minecraft.tags.BlockTags.STAIRS).add(v.stairs().get());
            }
        });
    }

    /** Block loot as a custom provider: vanilla's loot table provider has a fixed name already used by Core brewing. */
    static final class Loot implements DataProvider {
        private final PackOutput output;
        private final CompletableFuture<HolderLookup.Provider> registries;

        Loot(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            this.output = output;
            this.registries = registries;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return registries.thenCompose(provider -> {
                PackOutput.PathProvider paths = output.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
                java.util.List<CompletableFuture<?>> writes = new java.util.ArrayList<>();
                new ArnorLoot(provider).generate((key, builder) -> {
                    LootTable table = builder.setParamSet(LootContextParamSets.BLOCK).build();
                    writes.add(DataProvider.saveStable(
                            cache, provider, LootTable.DIRECT_CODEC, table, paths.json(key.location())));
                });
                return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
            });
        }

        @Override
        public String getName() {
            return "Arnor block loot tables";
        }
    }

    private static final class ArnorLoot extends BlockLootSubProvider {
        ArnorLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            for (ArnorBlocks.Variant v : ArnorBlocks.VARIANTS) {
                dropSelf(v.block().get());
                add(v.slab().get(), createSlabItemTable(v.slab().get()));
                dropSelf(v.stairs().get());
            }
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            java.util.List<Block> blocks = new java.util.ArrayList<>();
            for (ArnorBlocks.Variant v : ArnorBlocks.VARIANTS) {
                blocks.add(v.block().get());
                blocks.add(v.slab().get());
                blocks.add(v.stairs().get());
            }
            return blocks;
        }
    }

    /** Delegating wrapper: vanilla's recipe provider has a fixed name that Core brewing already uses. */
    static final class RecipeData implements DataProvider {
        private final Recipes inner;

        RecipeData(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            this.inner = new Recipes(output, registries);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return inner.run(cache);
        }

        @Override
        public String getName() {
            return "Arnor recipes";
        }
    }

    private static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, GoldRingItems.GOLD_RING.get())
                    .pattern(" N ")
                    .pattern("N N")
                    .pattern(" N ")
                    .define('N', Items.GOLD_NUGGET)
                    .unlockedBy("has_gold_nugget", has(Items.GOLD_NUGGET))
                    .save(out);

            ArnorBlocks.Variant plain = ArnorBlocks.ARNOR_BRICK;
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, plain.block().get(), 4)
                    .pattern("SS")
                    .pattern("SS")
                    .define('S', Items.STONE)
                    .unlockedBy("has_stone", has(Items.STONE))
                    .save(out);
            stonecut(out, Items.STONE, plain.block().get(), 1);

            ShapelessRecipeBuilder.shapeless(
                            RecipeCategory.BUILDING_BLOCKS, ArnorBlocks.MOSSY_ARNOR_BRICK.block().get())
                    .requires(plain.block().get())
                    .requires(Items.VINE)
                    .unlockedBy("has_arnor_brick", has(plain.block().get()))
                    .save(out, LOTRRevival.id("mossy_arnor_brick_from_vine"));
            ShapelessRecipeBuilder.shapeless(
                            RecipeCategory.BUILDING_BLOCKS, ArnorBlocks.MOSSY_ARNOR_BRICK.block().get())
                    .requires(plain.block().get())
                    .requires(Items.MOSS_BLOCK)
                    .unlockedBy("has_arnor_brick", has(plain.block().get()))
                    .save(out, LOTRRevival.id("mossy_arnor_brick_from_moss"));

            SimpleCookingRecipeBuilder.smelting(
                            net.minecraft.world.item.crafting.Ingredient.of(plain.block().get()),
                            RecipeCategory.BUILDING_BLOCKS,
                            ArnorBlocks.CRACKED_ARNOR_BRICK.block().get(),
                            0.1f,
                            200)
                    .unlockedBy("has_arnor_brick", has(plain.block().get()))
                    .save(out);

            for (ArnorBlocks.Variant v : ArnorBlocks.VARIANTS) {
                ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, v.slab().get(), 6)
                        .pattern("BBB")
                        .define('B', v.block().get())
                        .unlockedBy("has_" + v.name(), has(v.block().get()))
                        .save(out);
                ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, v.stairs().get(), 4)
                        .pattern("B  ")
                        .pattern("BB ")
                        .pattern("BBB")
                        .define('B', v.block().get())
                        .unlockedBy("has_" + v.name(), has(v.block().get()))
                        .save(out);
                stonecut(out, v.block().get(), v.slab().get(), 2);
                stonecut(out, v.block().get(), v.stairs().get(), 1);
            }
        }

        private static void stonecut(RecipeOutput out, ItemLike from, ItemLike to, int count) {
            String fromId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(from.asItem()).getPath();
            String toId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(to.asItem()).getPath();
            SingleItemRecipeBuilder.stonecutting(
                            net.minecraft.world.item.crafting.Ingredient.of(from), RecipeCategory.BUILDING_BLOCKS, to, count)
                    .unlockedBy("has_" + fromId, has(from))
                    .save(out, LOTRRevival.id(toId + "_from_" + fromId + "_stonecutting"));
        }
    }
}
