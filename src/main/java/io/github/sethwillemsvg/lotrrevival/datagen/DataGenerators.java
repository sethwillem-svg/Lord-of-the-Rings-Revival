package io.github.sethwillemsvg.lotrrevival.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
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

        // Core: faction registry entries, then tags built against the generated registry contents.
        DatapackBuiltinEntriesProvider factions =
                generator.addProvider(event.includeServer(), FactionProviders.entries(output, lookup));
        generator.addProvider(
                event.includeServer(),
                new FactionProviders.Tags(output, factions.getRegistryProvider(), event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(), new ModLangProvider(output));
        // Core: brewing (mug and drinks)
        generator.addProvider(
                event.includeClient(), new BrewingProviders.ItemModels(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new BrewingProviders.Recipes(output, lookup));
        // End Core: brewing
        // Pods add providers here, for example:
        // generator.addProvider(event.includeServer(), new ModRecipeProvider(generator.getPackOutput(), event.getLookupProvider()));
    }
}
