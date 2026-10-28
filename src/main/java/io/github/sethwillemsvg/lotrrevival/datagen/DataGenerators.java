package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementDatagen;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.title.TitleDatagen;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.quest.QuestDatagen;
import io.github.sethwillemsvg.lotrrevival.world.datagen.WorldDatagen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Registers every data provider. Run with {@code ./gradlew runData}; output lands in
 * {@code src/generated/resources} and is committed. Never hand-edit generated JSON.
 */
public final class DataGenerators {
    private DataGenerators() {}

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        // One DatapackBuiltinEntriesProvider for the whole mod (Minecraft allows only one per generator).
        // Each pod adds its datapack registry entries to this builder.
        RegistrySetBuilder registries = new RegistrySetBuilder();
        FactionProviders.addRegistries(registries);
        WorldDatagen.addRegistries(registries);
        AchievementDatagen.addRegistries(registries);
        TitleDatagen.addRegistries(registries);
        QuestDatagen.addRegistries(registries);
        DatapackBuiltinEntriesProvider entries = generator.addProvider(
                event.includeServer(),
                new DatapackBuiltinEntriesProvider(output, lookup, registries, Set.of(LOTRRevival.MOD_ID)));
        CompletableFuture<HolderLookup.Provider> registryLookup = entries.getRegistryProvider();

        // Core: faction tags, built against the generated registry contents.
        generator.addProvider(
                event.includeServer(),
                new FactionProviders.Tags(output, registryLookup, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(), new ModLangProvider(output));
        // Core: brewing (mug and drinks)
        generator.addProvider(
                event.includeClient(), new BrewingProviders.ItemModels(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new BrewingProviders.Recipes(output, lookup));
        generator.addProvider(
                event.includeClient(), new BrewingProviders.BlockStates(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new BrewingProviders.Loot(output, lookup));
        generator.addProvider(
                event.includeServer(), new BrewingProviders.BlockTags(output, lookup, event.getExistingFileHelper()));
        // End Core: brewing
        // Core: gold ring and Arnor bricks
        ArnorProviders.contributeLang();
        generator.addProvider(event.includeServer(), new ArnorProviders.RecipeData(output, lookup));
        generator.addProvider(
                event.includeClient(), new ArnorProviders.BlockStates(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new ArnorProviders.Loot(output, lookup));
        generator.addProvider(
                event.includeServer(), new ArnorProviders.Tags(output, lookup, event.getExistingFileHelper()));
        // End Core: gold ring and Arnor bricks
        generator.addProvider(
                event.includeServer(), new BrewingProviders.CoreItemTags(output, lookup, event.getExistingFileHelper()));

        // World
        WorldDatagen.gather(event, registryLookup);

        // Progression: achievement backing advancements and lang
        AchievementDatagen.gather(event, registryLookup);
        // Progression: titles lang (the entries ride in the shared registry provider)
        TitleDatagen.registerLang();
        TitleDatagen.gather(event, registryLookup);

        // Progression: mini-quest lang (registry entries are added above)
        QuestDatagen.gather();

        // NPC
        io.github.sethwillemsvg.lotrrevival.npc.datagen.NpcDataGen.gather(event);
        // Pods add providers here, for example:
        // generator.addProvider(event.includeServer(), new ModRecipeProvider(generator.getPackOutput(), event.getLookupProvider()));
    }
}
