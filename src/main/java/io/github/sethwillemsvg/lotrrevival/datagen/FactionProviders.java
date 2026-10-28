package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionKind;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Datagen providers for the faction registry: entries and tags. */
final class FactionProviders {
    private FactionProviders() {}

    /** Adds the faction registry entries to the shared builder (one provider per mod). */
    static void addRegistries(RegistrySetBuilder registries) {
        registries.add(ModDataRegistries.FACTION, context -> {
            for (FactionData.Def def : FactionData.DEFS) {
                context.register(def.key(), FactionData.build(def));
            }
        });
    }

    /** kind/*, region/*, hidden and isolationist tags. */
    static final class Tags extends TagsProvider<Faction> {
        Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
            super(output, ModDataRegistries.FACTION, lookup, LOTRRevival.MOD_ID, helper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            // Create every tag file even when empty, so the tag set is stable.
            for (FactionKind kind : FactionKind.values()) {
                tag(ModTags.Factions.kind(kind));
            }
            for (var region : io.github.sethwillemsvg.lotrrevival.core.faction.FactionRegion.values()) {
                tag(ModTags.Factions.region(region));
            }
            for (FactionData.Def def : FactionData.DEFS) {
                for (FactionKind kind : def.kinds()) {
                    tag(ModTags.Factions.kind(kind)).add(def.key());
                }
                tag(ModTags.Factions.region(def.region())).add(def.key());
                if (def.hidden()) {
                    tag(ModTags.Factions.HIDDEN).add(def.key());
                }
                if (def.isolationist()) {
                    tag(ModTags.Factions.ISOLATIONIST).add(def.key());
                }
            }
        }
    }
}
