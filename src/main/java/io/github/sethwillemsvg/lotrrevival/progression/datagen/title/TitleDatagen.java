package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementRow;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementTable;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleDefinition;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Wires the title table into datagen: registry entries (validated first) and lang. */
public final class TitleDatagen {
    private TitleDatagen() {}

    /** Adds the {@code lotrrevival:title} entries to the shared registry builder. Must come after factions. */
    public static void addRegistries(RegistrySetBuilder registries) {
        registries.add(TitleDefinition.REGISTRY, context -> {
            for (TitleRow row : TitleTable.rows()) {
                context.register(ResourceKey.create(TitleDefinition.REGISTRY, row.id()), row.toDefinition());
            }
        });
    }

    /** Adds the validation step; it fails the run if a text is too long or a reference does not resolve. */
    public static void gather(GatherDataEvent event, CompletableFuture<HolderLookup.Provider> registryLookup) {
        event.getGenerator().addProvider(event.includeServer(), new Validation(registryLookup));
    }

    /** Checks the table against the built registries (faction values are only bound once building is done). */
    static final class Validation implements DataProvider {
        private final CompletableFuture<HolderLookup.Provider> lookup;

        Validation(CompletableFuture<HolderLookup.Provider> lookup) {
            this.lookup = lookup;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return lookup.thenAccept(registries -> {
                Set<ResourceLocation> achievementIds = new HashSet<>();
                for (AchievementRow row : AchievementTable.rows()) {
                    achievementIds.add(row.id());
                }
                HolderLookup.RegistryLookup<Faction> factions = registries.lookupOrThrow(ModDataRegistries.FACTION);
                List<TitleRow> rows = TitleTable.rows();
                TitleValidator.validate(rows, achievementIds, key -> factions.get(key).map(Holder::value));
                report(rows, AchievementTable.rows());
            });
        }

        /** Prints the title count per source and lists non-rank achievements that have no title yet. */
        private static void report(List<TitleRow> rows, List<AchievementRow> achievements) {
            Map<String, Integer> counts = new TreeMap<>();
            for (TitleRow row : rows) {
                counts.merge(row.path().substring(0, row.path().indexOf('/')), 1, Integer::sum);
            }
            LOTRRevival.LOGGER.info("Title counts by folder: {} (total {})", counts, rows.size());
            List<String> missing = new ArrayList<>();
            for (AchievementRow achievement : achievements) {
                String path = achievement.id().getPath();
                if (achievement.rank().isEmpty() && !TitleTable.achievementPaths().contains(path)) {
                    missing.add(path);
                }
            }
            if (!missing.isEmpty()) {
                LOTRRevival.LOGGER.warn("Achievements without a title: {}", missing);
            }
        }

        @Override
        public String getName() {
            return "Progression Title Validation";
        }
    }

    /** Adds the lang contribution (title texts and the strings the system itself shows). */
    public static void registerLang() {
        ModLangProvider.contribute(lang -> {
            lang.add("title.lotrrevival.lost", "You are no longer eligible for the title [%s].");
            lang.add("commands.lotrrevival.title.unknown", "Unknown title: %s");
            lang.add("commands.lotrrevival.title.not_special", "%s is not a special title.");
            lang.add("commands.lotrrevival.title.grant.success", "Granted the title %2$s to %1$s.");
            lang.add("commands.lotrrevival.title.grant.already", "%s already has the title %s.");
            lang.add("commands.lotrrevival.title.revoke.success", "Revoked the title %2$s from %1$s.");
            lang.add("commands.lotrrevival.title.revoke.missing", "%s has not been granted the title %s.");
            lang.add("commands.lotrrevival.title.list.header", "Titles usable by %s: %s");
            lang.add("commands.lotrrevival.title.list.entry", "%s");
            lang.add("commands.lotrrevival.title.list.selected", "%s (selected)");
            for (TitleRow row : TitleTable.rows()) {
                lang.add(row.textKey(), row.text());
                if (row.feminine() != null) {
                    lang.add(row.feminineKey(), row.feminine());
                }
            }
        });
    }
}
