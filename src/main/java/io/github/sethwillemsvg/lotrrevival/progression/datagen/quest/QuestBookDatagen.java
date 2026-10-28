package io.github.sethwillemsvg.lotrrevival.progression.datagen.quest;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.item.CoreItems;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Quest book recipe, item model and the quest log screen strings. */
public final class QuestBookDatagen {
    private QuestBookDatagen() {}

    public static void gather(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator()
                .addProvider(event.includeServer(), new Named("Quest Book Recipes", new Recipes(output, event.getLookupProvider())));
        event.getGenerator()
                .addProvider(event.includeClient(), new ItemModels(output, event.getExistingFileHelper()));
        ModLangProvider.contribute(QuestBookDatagen::lang);
    }

    private static void lang(net.neoforged.neoforge.common.data.LanguageProvider lang) {
        lang.add("gui.lotrrevival.quests.title", "Quest Book");
        lang.add("gui.lotrrevival.quests.tab.active", "Active (%s)");
        lang.add("gui.lotrrevival.quests.tab.completed", "Completed (%s)");
        lang.add("gui.lotrrevival.quests.empty", "No active quests.");
        lang.add("gui.lotrrevival.quests.empty_log", "Nothing in the log yet.");
        lang.add("gui.lotrrevival.quests.select", "Select a quest.");
        lang.add("gui.lotrrevival.quests.track", "Track");
        lang.add("gui.lotrrevival.quests.untrack", "Untrack");
        lang.add("gui.lotrrevival.quests.abandon", "Abandon");
        lang.add("gui.lotrrevival.quests.remove", "Remove");
        lang.add("gui.lotrrevival.quests.delete", "Delete");
        lang.add("gui.lotrrevival.quests.abandon.title", "Abandon this quest?");
        lang.add("gui.lotrrevival.quests.abandon.message", "The quest from %s will end. There is no penalty.");
        lang.add("gui.lotrrevival.quests.delete.title", "Delete this record?");
        lang.add("gui.lotrrevival.quests.delete.message", "The log entry for the quest from %s will be removed for good.");

        lang.add("quest.lotrrevival.objective.collect", "Collect %s");
        lang.add("quest.lotrrevival.objective.kill_entity", "Slay %s");
        lang.add("quest.lotrrevival.objective.kill_faction", "Defeat %s");
        lang.add("quest.lotrrevival.objective.unknown", "Finish the task");
        lang.add("quest.lotrrevival.objective.items", "items");
        lang.add("quest.lotrrevival.objective.creatures", "creatures");
        lang.add("quest.lotrrevival.objective.enemies", "enemies");
        lang.add("quest.lotrrevival.return_to", "Return to %s");
        lang.add("quest.lotrrevival.opening.collect", "I am in need of supplies, and you look like someone who can find them.");
        lang.add("quest.lotrrevival.opening.kill_entity", "Dangerous creatures trouble these lands. Will you deal with them?");
        lang.add("quest.lotrrevival.opening.kill_faction", "Our enemies grow bold. Strike at them, and you will be rewarded.");
        lang.add("quest.lotrrevival.opening.unknown", "I have a task for you, traveller.");
        lang.add("quest.lotrrevival.diary.day", "Day %s");
        lang.add("quest.lotrrevival.diary.day_in", "Day %1$s, %2$s");
        lang.add("quest.lotrrevival.diary.ended_day", "Day %s");
        lang.add("quest.lotrrevival.diary.from", "Given by %s");
        lang.add("quest.lotrrevival.diary.objective", "Objective: %s");
        lang.add("quest.lotrrevival.diary.progress", "Progress: %1$s / %2$s");
        lang.add("quest.lotrrevival.diary.failed", "Failed: %s");
        lang.add("quest.lotrrevival.diary.rewards", "Rewards");
        lang.add("quest.lotrrevival.diary.reward.alignment", "%1$s alignment with %2$s");
        lang.add("quest.lotrrevival.diary.reward.coins", "%s coins");
        lang.add("quest.lotrrevival.diary.reward.item", "%1$s x %2$s");
        lang.add("quest.lotrrevival.diary.reward.hired", "%s joined you as a hired unit");
        lang.add("quest.lotrrevival.diary.reward.none", "No reward was recorded.");
        lang.add("quest.lotrrevival.outcome.completed", "Completed");
        lang.add("quest.lotrrevival.outcome.failed", "Failed");
        lang.add("quest.lotrrevival.outcome.abandoned", "Abandoned");
    }

    /** Delegating provider with its own name; the vanilla recipe provider's name is final and would collide. */
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

    static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput out) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, QuestItems.QUEST_BOOK.get())
                    .requires(Items.BOOK)
                    .requires(CoreItems.SILVER_COIN.get())
                    .unlockedBy("has_silver_coin", has(CoreItems.SILVER_COIN.get()))
                    .save(out);
        }
    }

    static final class ItemModels extends ItemModelProvider {
        ItemModels(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, LOTRRevival.MOD_ID, existingFileHelper);
        }

        @Override
        public String getName() {
            return "Quest Book Item Models";
        }

        @Override
        protected void registerModels() {
            // Placeholder art: the vanilla book texture until a dedicated one is made.
            withExistingParent("quest_book", ResourceLocation.withDefaultNamespace("item/generated"))
                    .texture("layer0", ResourceLocation.withDefaultNamespace("item/book"));
        }
    }
}
