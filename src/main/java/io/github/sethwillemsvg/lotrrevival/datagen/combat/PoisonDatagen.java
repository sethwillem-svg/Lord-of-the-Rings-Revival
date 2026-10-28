package io.github.sethwillemsvg.lotrrevival.datagen.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.poison.PoisonItems;
import io.github.sethwillemsvg.lotrrevival.core.combat.poison.PoisonTooltip;
import io.github.sethwillemsvg.lotrrevival.core.combat.poison.PoisonWeaponRecipe;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Datagen for the bottle of poison, its stand-in recipe, the {@code poison_weapon} special recipe and lang. */
final class PoisonDatagen {
    private PoisonDatagen() {}

    static void gather(GatherDataEvent event, CompletableFuture<HolderLookup.Provider> lookup) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator().addProvider(event.includeClient(), new Models(output, event.getExistingFileHelper()));
        event.getGenerator().addProvider(event.includeServer(), new NamedProvider("Poison recipes", new Recipes(output, lookup)));
        ModLangProvider.contribute(lang -> {
            lang.addItem(PoisonItems.BOTTLE_OF_POISON, "Bottle of Poison");
            lang.add(PoisonTooltip.KEY, "Poisoned");
        });
    }

    private static final class Models extends ItemModelProvider {
        Models(PackOutput output, ExistingFileHelper helper) {
            super(output, LOTRRevival.MOD_ID, helper);
        }

        @Override
        protected void registerModels() {
            basicItem(PoisonItems.BOTTLE_OF_POISON.get());
        }

        @Override
        public String getName() {
            return "Poison item models";
        }
    }

    private static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            // Stand-in until poisonous berries and venomous creatures exist (spec "Poison").
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PoisonItems.BOTTLE_OF_POISON.get())
                    .requires(Items.GLASS_BOTTLE)
                    .requires(Items.SPIDER_EYE)
                    .requires(Items.POISONOUS_POTATO)
                    .unlockedBy("has_spider_eye", has(Items.SPIDER_EYE))
                    .save(out, LOTRRevival.id("bottle_of_poison"));
            SpecialRecipeBuilder.special(PoisonWeaponRecipe::new).save(out, LOTRRevival.id("poison_weapon").toString());
        }
    }
}
