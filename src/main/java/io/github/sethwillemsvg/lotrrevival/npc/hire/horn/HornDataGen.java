package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Datagen of the command horn and sword: models, recipes and lang. */
public final class HornDataGen {
    private HornDataGen() {}

    /** Called once from {@code NpcDataGen.gather}. */
    public static void gather(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator().addProvider(event.includeClient(), new Models(output, event.getExistingFileHelper()));
        event.getGenerator()
                .addProvider(
                        event.includeServer(),
                        new Named("Command horn recipes", new Recipes(output, event.getLookupProvider())));
        ModLangProvider.contribute(lang -> {
            lang.addItem(HornItems.COMMAND_HORN, "Command Horn");
            lang.addItem(HornItems.COMMAND_SWORD, "Command Sword");
            lang.add("item.lotrrevival.command_horn.mode", "Mode: %s");
            lang.add(HornMode.BLANK.langKey(), "Blank");
            lang.add(HornMode.HALT.langKey(), "Halt");
            lang.add(HornMode.READY.langKey(), "Ready");
            lang.add(HornMode.SUMMON.langKey(), "Summon");
            lang.add("item.lotrrevival.squadron", "Squadron: %s");
            lang.add("item.lotrrevival.squadron.none", "No squadron");
            lang.add("screen.lotrrevival.horn_mode", "Choose the horn's call");
            lang.add("screen.lotrrevival.squadron_rename", "Squadron name");
        });
    }

    static final class Models extends ItemModelProvider {
        Models(PackOutput output, ExistingFileHelper files) {
            super(output, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void registerModels() {
            singleTexture("command_horn", mcLoc("item/generated"), "layer0", modLoc("item/command_horn"));
            singleTexture("command_sword", mcLoc("item/handheld"), "layer0", modLoc("item/command_sword"));
        }

        @Override
        public String getName() {
            return "Command horn item models";
        }
    }

    /** Horn: shaped, 3 iron ingots and 1 leather. Sword: shapeless, iron sword and gold ingot. */
    static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, HornItems.COMMAND_HORN.get())
                    .pattern("II")
                    .pattern("IL")
                    .define('I', Items.IRON_INGOT)
                    .define('L', Items.LEATHER)
                    .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                    .save(out, LOTRRevival.id("command_horn"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.COMBAT, HornItems.COMMAND_SWORD.get())
                    .requires(Items.IRON_SWORD)
                    .requires(Items.GOLD_INGOT)
                    .unlockedBy("has_iron_sword", has(Items.IRON_SWORD))
                    .save(out, LOTRRevival.id("command_sword"));
        }
    }

    /** Gives a vanilla provider with a final {@code getName()} a unique name. */
    private record Named(String name, DataProvider delegate) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput output) {
            return delegate.run(output);
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
