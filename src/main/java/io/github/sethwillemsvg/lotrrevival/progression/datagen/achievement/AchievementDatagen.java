package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Wires the achievement table into datagen: registry entries, backing advancements and lang. */
public final class AchievementDatagen {
    private AchievementDatagen() {}

    /** Adds the {@code lotrrevival:achievement} entries to the shared registry builder. */
    public static void addRegistries(RegistrySetBuilder registries) {
        registries.add(AchievementEntry.REGISTRY, context -> {
            for (AchievementRow row : AchievementTable.rows()) {
                context.register(ResourceKey.create(AchievementEntry.REGISTRY, row.id()), row.toEntry());
            }
        });
    }

    /** Adds the advancement provider and the lang contribution. */
    public static void gather(GatherDataEvent event, CompletableFuture<HolderLookup.Provider> registryLookup) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator()
                .addProvider(event.includeServer(), new Advancements(output, registryLookup));
        ModLangProvider.contribute(lang -> {
            lang.add("chat.lotrrevival.achievement.earned", "%1$s has earned the achievement %2$s");
            lang.add("toast.lotrrevival.achievement", "Achievement Earned");
            io.github.sethwillemsvg.lotrrevival.progression.datagen.MenuLang.add(lang);
            for (AchievementTab tab : AchievementTab.values()) {
                lang.add(tab.langKey(), tabName(tab));
            }
            for (AchievementRow row : AchievementTable.rows()) {
                if (!row.usesCoreTitle()) {
                    lang.add(row.titleKey(), row.title());
                }
                lang.add(row.descriptionKey(), row.description());
            }
        });
    }

    private static String tabName(AchievementTab tab) {
        return switch (tab) {
            case GENERAL -> "General";
            case SHIRE -> "The Shire";
            case ERIADOR -> "Eriador";
            case MISTY_MOUNTAINS -> "Misty Mountains";
            case RHOVANION -> "Rhovanion";
            case LOTHLORIEN -> "Lothlorien";
            case ROHAN -> "Rohan";
            case GONDOR -> "Gondor";
            case MORDOR -> "Mordor";
            case SOUTH_EAST -> "South and East";
            case COMBAT -> "Combat";
            case CRAFTING -> "Crafting";
            case TRADE -> "Trade";
            case TRAVEL -> "Travel";
        };
    }

    /** Backing advancements (criteria only, no display). Validates the table before writing anything. */
    static final class Advancements implements DataProvider {
        private final AdvancementProvider delegate;

        Advancements(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            this.delegate = new AdvancementProvider(output, lookup, List.of(new Backing()));
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return delegate.run(cache);
        }

        @Override
        public String getName() {
            return "Progression Achievement Advancements";
        }
    }

    private static final class Backing implements AdvancementSubProvider {
        private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AchievementDatagen.class);

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> writer) {
            List<AchievementRow> rows = AchievementTable.rows();
            Map<ResourceLocation, Advancement> advancements = new LinkedHashMap<>();
            for (AchievementRow row : rows) {
                advancements.put(row.id(), row.toAdvancement());
            }
            HolderLookup.RegistryLookup<Faction> factions = registries.lookupOrThrow(ModDataRegistries.FACTION);
            AchievementFactions.validateCoverage(
                    rows, faction -> factions.get(faction).map(holder -> holder.value()));
            AchievementValidator.validate(
                    rows,
                    AchievementTable.entries(rows),
                    advancements,
                    faction -> factions.get(faction).map(holder -> holder.value().ranks()));
            Map<AchievementTab, Integer> perTab = new java.util.EnumMap<>(AchievementTab.class);
            for (AchievementRow row : rows) {
                perTab.merge(row.tab(), 1, Integer::sum);
            }
            LOGGER.info("Achievement table: {} entries {}", rows.size(), perTab);
            advancements.forEach((id, advancement) -> writer.accept(new AdvancementHolder(id, advancement)));
        }
    }
}
