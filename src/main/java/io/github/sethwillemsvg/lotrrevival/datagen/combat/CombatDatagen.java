package io.github.sethwillemsvg.lotrrevival.datagen.combat;

import io.github.sethwillemsvg.lotrrevival.core.combat.CombatItems;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearCatalog;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearEntry;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.datagen.VanillaTagProviders;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Entry point for the generic combat providers (specs/core/combat-items.md, "Datagen for hundreds of
 * variants"). Everything is driven by {@link GearCatalog}; adding a gear set needs no edits here.
 */
public final class CombatDatagen {
    private CombatDatagen() {}

    public static void gather(GatherDataEvent event, CompletableFuture<HolderLookup.Provider> lookup) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        generator.addProvider(event.includeClient(), new CombatItemModels(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new CombatItemTags(output, lookup, event.getExistingFileHelper()));
        // Vanilla tags (swords, axes, armour slots, enchantable/*) are written by the shared VanillaTagProviders.
        VanillaTagProviders.contributeItems(tag -> {
            for (GearEntry entry : GearCatalog.entries()) {
                for (TagKey<Item> key : entry.tags()) {
                    if (VanillaTagProviders.isVanilla(key)) {
                        tag.apply(key).add(entry.item().get());
                    }
                }
            }
        });
        generator.addProvider(event.includeServer(), new NamedProvider("Combat recipes", new CombatRecipes(output, lookup)));

        ModLangProvider.contribute(lang -> {
            lang.add("itemGroup.lotrrevival." + CombatItems.COMBAT_TAB_ID, "Combat");
            lang.add("itemGroup.lotrrevival." + CombatItems.MATERIALS_TAB_ID, "Materials");
            lang.addEntityType(
                    io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrowingEntities.THROWN_SPEAR, "Thrown Spear");
            lang.addEntityType(
                    io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrowingEntities.THROWN_AXE, "Thrown Axe");
            for (GearEntry entry : GearCatalog.entries()) {
                lang.addItem(entry.item(), entry.name());
            }
        });
    }
}
