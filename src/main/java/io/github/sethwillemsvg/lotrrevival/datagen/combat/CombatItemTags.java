package io.github.sethwillemsvg.lotrrevival.datagen.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearCatalog;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearEntry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Adds every {@link GearEntry} to the tags it lists: its {@code weapons/<class>} tag, the vanilla
 * {@code swords}, {@code axes}, armour-slot and {@code enchantable/*} tags, and {@code armor/<faction>}.
 */
final class CombatItemTags extends ItemTagsProvider {
    CombatItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
        super(output, lookup, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()), LOTRRevival.MOD_ID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        for (GearEntry entry : GearCatalog.entries()) {
            for (TagKey<Item> tag : entry.tags()) {
                tag(tag).add(entry.item().get());
            }
        }
    }

    @Override
    public String getName() {
        return "Combat item tags";
    }
}
