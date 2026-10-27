package io.github.sethwillemsvg.lotrrevival.datagen;

import net.minecraft.data.DataGenerator;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Registers every data provider. Run with {@code ./gradlew runData}; output lands in
 * {@code src/generated/resources} and is committed. Never hand-edit generated JSON.
 */
public final class DataGenerators {
    private DataGenerators() {}

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        // Pods add providers here, for example:
        // generator.addProvider(event.includeServer(), new ModRecipeProvider(generator.getPackOutput(), event.getLookupProvider()));
    }
}
