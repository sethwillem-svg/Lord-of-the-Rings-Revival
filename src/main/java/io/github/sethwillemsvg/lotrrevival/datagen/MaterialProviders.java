package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialBlocks;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialItems;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialTags;
import io.github.sethwillemsvg.lotrrevival.core.material.Metal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Core: datagen for metals and ores (tin, silver, bronze, Morgul iron, orc steel, mithril). One entry point,
 * {@link #gather}, called from a single marked block in {@link DataGenerators}.
 */
final class MaterialProviders {
    private MaterialProviders() {}

    static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper files = event.getExistingFileHelper();

        ModLangProvider.contribute(MaterialProviders::lang);
        generator.addProvider(event.includeClient(), new ItemModels(output, files));
        generator.addProvider(event.includeClient(), new BlockStates(output, files));
        generator.addProvider(event.includeServer(), new RecipesProvider(output, lookup));
        generator.addProvider(event.includeServer(), new Loot(output, lookup));
        generator.addProvider(event.includeServer(), new BlockTagProvider(output, lookup, files));
        generator.addProvider(event.includeServer(), new ItemTagProvider(output, lookup, files));
    }

    private static void lang(LanguageProvider lang) {
        for (Metal metal : Metal.values()) {
            String name = metal.displayName();
            if (metal.hasOre()) {
                lang.addBlock(MaterialBlocks.ORE.get(metal), name + " Ore");
                lang.addBlock(MaterialBlocks.DEEPSLATE_ORE.get(metal), "Deepslate " + name + " Ore");
                lang.addBlock(MaterialBlocks.RAW_BLOCK.get(metal), "Block of Raw " + name);
                lang.addItem(MaterialItems.RAW.get(metal), "Raw " + name);
            }
            if (metal.hasIngot()) {
                lang.addBlock(MaterialBlocks.BLOCK.get(metal), "Block of " + name);
                lang.addItem(MaterialItems.INGOT.get(metal), name + " Ingot");
                lang.addItem(MaterialItems.NUGGET.get(metal), name + " Nugget");
            }
        }
    }

    static final class ItemModels extends ItemModelProvider {
        ItemModels(PackOutput output, ExistingFileHelper files) {
            super(output, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void registerModels() {
            MaterialItems.RAW.values().forEach(item -> basicItem(item.get()));
            MaterialItems.INGOT.values().forEach(item -> basicItem(item.get()));
            MaterialItems.NUGGET.values().forEach(item -> basicItem(item.get()));
        }

        @Override
        public String getName() {
            return "Material item models";
        }
    }

    /** Cube-all blocks; the block items' models are written by {@code simpleBlockWithItem}. */
    static final class BlockStates extends BlockStateProvider {
        BlockStates(PackOutput output, ExistingFileHelper files) {
            super(output, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            for (var block : MaterialBlocks.all()) {
                String name = block.getId().getPath();
                simpleBlockWithItem(block.get(), models().cubeAll(name, modLoc("block/" + name)));
            }
        }

        @Override
        public String getName() {
            return "Material block states";
        }
    }

    /** Ores drop their raw item (silk touch gives the ore, fortune like copper, 1 base); blocks drop themselves. */
    static final class Loot implements DataProvider {
        private final LootTableProvider delegate;

        Loot(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            this.delegate = new LootTableProvider(
                    output,
                    Set.of(),
                    List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)),
                    registries);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return delegate.run(cache);
        }

        @Override
        public String getName() {
            return "Material loot tables";
        }
    }

    private static final class BlockLoot extends BlockLootSubProvider {
        BlockLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            for (Metal metal : Metal.values()) {
                if (metal.hasOre()) {
                    Item raw = MaterialItems.RAW.get(metal).get();
                    add(MaterialBlocks.ORE.get(metal).get(), block -> createOreDrop(block, raw));
                    add(MaterialBlocks.DEEPSLATE_ORE.get(metal).get(), block -> createOreDrop(block, raw));
                    dropSelf(MaterialBlocks.RAW_BLOCK.get(metal).get());
                }
                if (metal.hasIngot()) {
                    dropSelf(MaterialBlocks.BLOCK.get(metal).get());
                }
            }
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return MaterialBlocks.all().stream().<Block>map(holder -> holder.get()).toList();
        }
    }

    static final class BlockTagProvider extends BlockTagsProvider {
        BlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (Metal metal : Metal.values()) {
                if (metal.hasOre()) {
                    Block ore = MaterialBlocks.ORE.get(metal).get();
                    Block deepslate = MaterialBlocks.DEEPSLATE_ORE.get(metal).get();
                    Block raw = MaterialBlocks.RAW_BLOCK.get(metal).get();
                    tag(MaterialTags.commonOreBlocks(metal)).add(ore, deepslate);
                    tag(MaterialTags.modOreBlocks(metal)).add(ore, deepslate);
                    tag(MaterialTags.rawStorageBlocksBlock(metal)).add(raw);
                    tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ore, deepslate, raw);
                    tag(metal.tier().tag).add(ore, deepslate, raw);
                }
                if (metal.hasIngot()) {
                    Block block = MaterialBlocks.BLOCK.get(metal).get();
                    tag(MaterialTags.storageBlocksBlock(metal)).add(block);
                    tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block);
                    tag(metal.tier().tag).add(block);
                    tag(BlockTags.BEACON_BASE_BLOCKS).add(block);
                }
            }
        }

        @Override
        public String getName() {
            return "Material block tags";
        }
    }

    static final class ItemTagProvider extends net.minecraft.data.tags.ItemTagsProvider {
        ItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(
                    output,
                    lookup,
                    CompletableFuture.completedFuture(net.minecraft.data.tags.TagsProvider.TagLookup.empty()),
                    LOTRRevival.MOD_ID,
                    files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (Metal metal : Metal.values()) {
                if (metal.hasOre()) {
                    Item ore = MaterialBlocks.ORE_ITEM.get(metal).get();
                    Item deepslate = MaterialBlocks.DEEPSLATE_ORE_ITEM.get(metal).get();
                    tag(MaterialTags.commonOres(metal)).add(ore, deepslate);
                    tag(MaterialTags.modOres(metal)).add(ore, deepslate);
                    tag(MaterialTags.rawMaterials(metal)).add(MaterialItems.RAW.get(metal).get());
                    tag(MaterialTags.rawStorageBlocks(metal)).add(MaterialBlocks.RAW_BLOCK_ITEM.get(metal).get());
                }
                if (metal.hasIngot()) {
                    Item ingot = MaterialItems.INGOT.get(metal).get();
                    tag(MaterialTags.ingots(metal)).add(ingot);
                    tag(MaterialTags.nuggets(metal)).add(MaterialItems.NUGGET.get(metal).get());
                    tag(MaterialTags.storageBlocks(metal)).add(MaterialBlocks.BLOCK_ITEM.get(metal).get());
                    tag(ItemTags.BEACON_PAYMENT_ITEMS).add(ingot);
                }
            }
        }

        @Override
        public String getName() {
            return "Material item tags";
        }
    }

    static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            for (Metal metal : Metal.values()) {
                String id = metal.id();
                if (metal.hasIngot()) {
                    Item ingot = MaterialItems.INGOT.get(metal).get();
                    Item nugget = MaterialItems.NUGGET.get(metal).get();
                    ItemLike block = MaterialBlocks.BLOCK_ITEM.get(metal).get();
                    compress(out, nugget, ingot, id + "_ingot_from_nuggets");
                    decompress(out, ingot, nugget, id + "_nugget_from_ingot");
                    compress(out, ingot, block, id + "_block");
                    decompress(out, block, ingot, id + "_ingot_from_block");
                }
                if (metal.hasOre()) {
                    Item raw = MaterialItems.RAW.get(metal).get();
                    ItemLike rawBlock = MaterialBlocks.RAW_BLOCK_ITEM.get(metal).get();
                    compress(out, raw, rawBlock, "raw_" + id + "_block");
                    decompress(out, rawBlock, raw, "raw_" + id + "_from_block");
                }
            }

            smelt(out, Metal.TIN, 0.7f);
            smelt(out, Metal.SILVER, 1.0f);
            smelt(out, Metal.MITHRIL, 2.0f);
            // Raw Morgul iron deliberately has no furnace or blast furnace recipe: only the orc forge smelts it.

            ShapelessRecipeBuilder.shapeless(
                            RecipeCategory.MISC, MaterialItems.INGOT.get(Metal.BRONZE).get())
                    .requires(Items.COPPER_INGOT)
                    .requires(MaterialItems.INGOT.get(Metal.TIN).get())
                    .unlockedBy("has_tin_ingot", has(MaterialItems.INGOT.get(Metal.TIN).get()))
                    .save(out, LOTRRevival.id("bronze_ingot_from_copper_and_tin"));
        }

        private void smelt(RecipeOutput out, Metal metal, float xp) {
            Item raw = MaterialItems.RAW.get(metal).get();
            Item ingot = MaterialItems.INGOT.get(metal).get();
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(raw), RecipeCategory.MISC, ingot, xp, 200)
                    .unlockedBy("has_raw_" + metal.id(), has(raw))
                    .save(out, LOTRRevival.id(metal.id() + "_ingot_from_smelting"));
            SimpleCookingRecipeBuilder.blasting(Ingredient.of(raw), RecipeCategory.MISC, ingot, xp, 100)
                    .unlockedBy("has_raw_" + metal.id(), has(raw))
                    .save(out, LOTRRevival.id(metal.id() + "_ingot_from_blasting"));
        }

        /** Nine of {@code small} become one {@code big}. */
        private void compress(RecipeOutput out, ItemLike small, ItemLike big, String name) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, big)
                    .pattern("###")
                    .pattern("###")
                    .pattern("###")
                    .define('#', small)
                    .unlockedBy("has_" + name, has(small))
                    .save(out, LOTRRevival.id(name));
        }

        /** One {@code big} becomes nine {@code small}. */
        private void decompress(RecipeOutput out, ItemLike big, ItemLike small, String name) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, small, 9)
                    .requires(big)
                    .unlockedBy("has_" + name, has(big))
                    .save(out, LOTRRevival.id(name));
        }

    }

    /** RecipeProvider.getName is final; this wrapper gives the recipes a unique provider name. */
    static final class RecipesProvider implements DataProvider {
        private final Recipes delegate;

        RecipesProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            this.delegate = new Recipes(output, registries);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return delegate.run(cache);
        }

        @Override
        public String getName() {
            return "Material recipes";
        }
    }
}
