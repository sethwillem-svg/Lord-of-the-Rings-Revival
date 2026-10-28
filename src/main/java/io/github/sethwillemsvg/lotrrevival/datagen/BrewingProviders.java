package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Core: brewing item models and recipes (ticket 1: mug and drinks). */
final class BrewingProviders {
    private BrewingProviders() {}

    static final class ItemModels extends ItemModelProvider {
        ItemModels(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, LOTRRevival.MOD_ID, existingFileHelper);
        }

        @Override
        protected void registerModels() {
            basicItem(BrewingItems.WOODEN_MUG.get());
            BrewingItems.DRINKS.forEach(drink -> basicItem(drink.get()));
        }
    }

    static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, BrewingItems.WOODEN_MUG.get(), 2)
                    .pattern("P P")
                    .pattern("PNP")
                    .define('P', ItemTags.PLANKS)
                    .define('N', Items.IRON_NUGGET)
                    .unlockedBy("has_iron_nugget", has(Items.IRON_NUGGET))
                    .save(out);
        }
    }
}
