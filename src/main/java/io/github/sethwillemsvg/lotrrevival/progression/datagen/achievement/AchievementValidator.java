package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Datagen checks for the achievement table. Throws (failing runData) listing every problem found. */
public final class AchievementValidator {
    private static final Pattern PATH = Pattern.compile("[a-z0-9_]+/[a-z0-9_]+");

    private AchievementValidator() {}

    /**
     * @param ladders rank ladder of a faction, empty when the faction does not exist in the registry
     * @throws IllegalStateException with one line per problem
     */
    public static void validate(
            List<AchievementRow> rows,
            Map<ResourceLocation, AchievementEntry> entries,
            Map<ResourceLocation, Advancement> advancements,
            Function<ResourceKey<Faction>, Optional<List<FactionRank>>> ladders) {
        List<String> errors = collect(rows, entries, advancements, ladders);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Achievement datagen validation failed:\n - " + String.join("\n - ", errors));
        }
    }

    public static List<String> collect(
            List<AchievementRow> rows,
            Map<ResourceLocation, AchievementEntry> entries,
            Map<ResourceLocation, Advancement> advancements,
            Function<ResourceKey<Faction>, Optional<List<FactionRank>>> ladders) {
        List<String> errors = new ArrayList<>();
        Set<String> langKeys = new HashSet<>();

        for (AchievementRow row : rows) {
            ResourceLocation id = row.id();
            if (!PATH.matcher(id.getPath()).matches()) {
                errors.add(id + ": id must be <tab>/<name> in snake_case");
            } else if (!id.getPath().startsWith(row.tab().id() + "/")) {
                errors.add(id + ": row tab '" + row.tab().id() + "' differs from the id prefix");
            }
            if (!langKeys.add(row.titleKey())) {
                errors.add(id + ": lang key " + row.titleKey() + " is shared with another row");
            }
            langKeys.add(row.descriptionKey());
        }

        for (ResourceLocation id : entries.keySet()) {
            if (!advancements.containsKey(id)) {
                errors.add(id + ": display entry has no backing advancement");
            }
        }
        for (Map.Entry<ResourceLocation, Advancement> advancement : advancements.entrySet()) {
            ResourceLocation id = advancement.getKey();
            if (!entries.containsKey(id)) {
                errors.add(id + ": backing advancement has no display entry");
            }
            if (advancement.getValue().display().isPresent()) {
                errors.add(id + ": backing advancement must not have a display block");
            }
        }

        for (Map.Entry<ResourceLocation, AchievementEntry> mapEntry : entries.entrySet()) {
            ResourceLocation id = mapEntry.getKey();
            AchievementEntry entry = mapEntry.getValue();
            if (!entry.requires().isEmpty() && entry.rank().isPresent()) {
                errors.add(id + ": requires and rank are mutually exclusive");
            }
            for (AchievementEntry.Requirement requirement : entry.requires()) {
                for (ResourceKey<Faction> faction : requirement.factions()) {
                    if (!knownFaction(faction)) {
                        errors.add(id + ": requires unknown faction " + faction.location());
                    }
                }
            }
            entry.rank().ifPresent(rank -> {
                if (!knownFaction(rank.faction())) {
                    errors.add(id + ": rank uses unknown faction " + rank.faction().location());
                    return;
                }
                Optional<List<FactionRank>> ladder = ladders.apply(rank.faction());
                if (ladder.isEmpty() || ladder.get().stream().noneMatch(r -> r.id().equals(rank.rank()))) {
                    errors.add(id + ": rank '" + rank.rank() + "' is not in the ladder of " + rank.faction().location());
                }
            });
        }
        return errors;
    }

    private static boolean knownFaction(ResourceKey<Faction> faction) {
        return Factions.REAL.contains(faction) || Factions.PSEUDO.contains(faction);
    }
}
