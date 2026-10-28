package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTables;
import io.github.sethwillemsvg.lotrrevival.core.table.TableRef;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Core: faction crafting table datagen (block states, models, loot, tags, recipes and lang). */
final class FactionTableProviders {
    private FactionTableProviders() {}

    private static final Map<String, String> NAMES = Map.of(
            "hobbit_crafting_table", "Hobbit Crafting Table",
            "bree_crafting_table", "Bree Crafting Table",
            "ranger_crafting_table", "Ranger Crafting Table",
            "gondor_crafting_table", "Gondor Crafting Table",
            "mordor_crafting_table", "Mordor Crafting Table");

    static void gather(GatherDataEvent event) {
        var generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        var lookup = event.getLookupProvider();
        ModLangProvider.contribute(lang -> {
            NAMES.forEach((name, english) -> lang.add("block.lotrrevival." + name, english));
            lang.add(
                    "message.lotrrevival.faction_table.refused",
                    "The %s will not let you use their table until you have earned their trust.");
        });
        generator.addProvider(event.includeClient(), new BlockStates(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(), new ItemModels(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new Named(new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(TableLoot::new, LootContextParamSets.BLOCK)),
                lookup), "Faction table loot tables"));
        generator.addProvider(event.includeServer(), new Tags(output, lookup, event.getExistingFileHelper()));
        VanillaTagProviders.contributeBlocks(tag -> FactionTables.ALL.forEach(
                entry -> tag.apply(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE).add(entry.block().get())));
        generator.addProvider(event.includeServer(), new Named(new Recipes(output, lookup), "Faction table recipes"));
    }

    static final class BlockStates extends BlockStateProvider {
        BlockStates(PackOutput output, ExistingFileHelper helper) {
            super(output, LOTRRevival.MOD_ID, helper);
        }

        @Override
        protected void registerStatesAndModels() {
            for (FactionTables.Entry entry : FactionTables.ALL) {
                ModelFile model = models().cubeBottomTop(
                        entry.name(),
                        modLoc("block/" + entry.name() + "_side"),
                        mcLoc("block/oak_planks"),
                        modLoc("block/" + entry.name() + "_top"));
                simpleBlock(entry.block().get(), model);
            }
        }

        @Override
        public String getName() {
            return "Faction table block states";
        }
    }

    static final class ItemModels extends ItemModelProvider {
        ItemModels(PackOutput output, ExistingFileHelper helper) {
            super(output, LOTRRevival.MOD_ID, helper);
        }

        @Override
        protected void registerModels() {
            for (FactionTables.Entry entry : FactionTables.ALL) {
                getBuilder(entry.name())
                        .parent(new ModelFile.UncheckedModelFile(LOTRRevival.id("block/" + entry.name())));
            }
        }

        @Override
        public String getName() {
            return "Faction table item models";
        }
    }

    /** RecipeProvider and LootTableProvider have a final getName(); wrap them to get a unique provider name. */
    static final class Named implements DataProvider {
        private final DataProvider delegate;
        private final String name;

        Named(DataProvider delegate, String name) {
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput output) {
            return delegate.run(output);
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class TableLoot extends BlockLootSubProvider {
        TableLoot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            FactionTables.blocks().forEach(this::dropSelf);
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return FactionTables.blocks();
        }
    }

    static final class Tags extends BlockTagsProvider {
        Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
            super(output, lookup, LOTRRevival.MOD_ID, helper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            // mineable/axe is written by VanillaTagProviders (contributed in gather).
            var crafting = tag(ModTags.Blocks.CRAFTING_TABLES);
            FactionTables.ALL.forEach(entry -> crafting.add(entry.block().get()));
        }

        @Override
        public String getName() {
            return "Faction table block tags";
        }
    }

    static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            // 2 planks above 2 culture blocks, on the table's own table (the first table is crafted on a vanilla one
            // by whoever finds or buys it; later tables chain from this recipe).
            table(out, FactionTables.HOBBIT, ItemTags.WOOL);
            table(out, FactionTables.BREE, Items.BRICKS);
            table(out, FactionTables.RANGER, io.github.sethwillemsvg.lotrrevival.core.building.ArnorBlocks.ARNOR_BRICK.block().get());
            table(out, FactionTables.GONDOR, Items.CHISELED_STONE_BRICKS);
            table(out, FactionTables.MORDOR, Items.POLISHED_BLACKSTONE_BRICKS);
        }

        private static void table(RecipeOutput out, FactionTables.Entry entry, ItemLike culture) {
            builder(entry).define('C', culture).save(out, LOTRRevival.id("faction_tables/" + entry.name()));
        }

        private static void table(RecipeOutput out, FactionTables.Entry entry, net.minecraft.tags.TagKey<Item> culture) {
            builder(entry).define('C', culture).save(out, LOTRRevival.id("faction_tables/" + entry.name()));
        }

        private static FactionShapedRecipeBuilder builder(FactionTables.Entry entry) {
            return FactionShapedRecipeBuilder.shaped(TableRef.block(entry.block().get()), entry.item().get(), 1)
                    .category(RecipeCategory.DECORATIONS)
                    .define('P', ItemTags.PLANKS)
                    .pattern("PP")
                    .pattern("CC");
        }

    }
}
