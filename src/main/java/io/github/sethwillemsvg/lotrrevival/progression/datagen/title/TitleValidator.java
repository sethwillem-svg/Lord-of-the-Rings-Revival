package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Datagen checks for the title table. Throws (failing runData) listing every problem found. */
public final class TitleValidator {
    /** Rendered title text limit. */
    public static final int MAX_TEXT_LENGTH = 32;

    private static final Pattern PATH = Pattern.compile("(starter|special|faction|achievement|rank)/[a-z0-9_]+");

    private TitleValidator() {}

    /**
     * @param achievementIds ids of the achievement display entries
     * @param factions looks a faction up in the registry, empty when it does not exist
     * @throws IllegalStateException with one line per problem
     */
    public static void validate(
            List<TitleRow> rows,
            Set<ResourceLocation> achievementIds,
            Function<ResourceKey<Faction>, Optional<Faction>> factions) {
        List<String> errors = collect(rows, achievementIds, factions);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Title datagen validation failed:\n - " + String.join("\n - ", errors));
        }
    }

    public static List<String> collect(
            List<TitleRow> rows,
            Set<ResourceLocation> achievementIds,
            Function<ResourceKey<Faction>, Optional<Faction>> factions) {
        List<String> errors = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        Set<String> langKeys = new HashSet<>();
        for (TitleRow row : rows) {
            String path = row.path();
            if (!paths.add(path)) {
                errors.add(path + ": duplicate title id");
            }
            if (!PATH.matcher(path).matches()) {
                errors.add(path + ": id must be <starter|special|faction|achievement|rank>/<name> in snake_case");
            } else if (!folderFor(row.source()).equals(path.substring(0, path.indexOf('/')))) {
                errors.add(path + ": folder does not match the source type " + row.source().type().getSerializedName());
            }
            if (!langKeys.add(row.textKey())) {
                errors.add(path + ": lang key " + row.textKey() + " is shared with another row");
            }
            checkText(errors, path, row.text());
            if (row.feminine() != null) {
                checkText(errors, path + " (feminine)", row.feminine());
            }
            checkSource(errors, row, achievementIds, factions);
        }
        return errors;
    }

    private static String folderFor(TitleSource source) {
        return switch (source) {
            case TitleSource.Starter s -> "starter";
            case TitleSource.Special s -> "special";
            case TitleSource.FactionAlignment s -> "faction";
            case TitleSource.AchievementSource s -> "achievement";
            case TitleSource.AlignmentRank s -> "rank";
        };
    }

    private static void checkText(List<String> errors, String what, String text) {
        if (text == null || text.isBlank()) {
            errors.add(what + ": text is empty");
        } else if (text.length() > MAX_TEXT_LENGTH) {
            errors.add(what + ": text is " + text.length() + " characters, the limit is " + MAX_TEXT_LENGTH);
        }
    }

    private static void checkSource(
            List<String> errors,
            TitleRow row,
            Set<ResourceLocation> achievementIds,
            Function<ResourceKey<Faction>, Optional<Faction>> factions) {
        String path = row.path();
        switch (row.source()) {
            case TitleSource.AchievementSource source -> {
                if (!achievementIds.contains(source.achievement())) {
                    errors.add(path + ": achievement " + source.achievement() + " does not exist");
                }
            }
            case TitleSource.FactionAlignment source -> {
                for (ResourceKey<Faction> key : source.factions()) {
                    Optional<Faction> faction = factions.apply(key);
                    if (faction.isEmpty()) {
                        errors.add(path + ": unknown faction " + key.location());
                    } else if (source.min().isEmpty() && faction.get().pledgeRank().isEmpty()) {
                        errors.add(path + ": faction " + key.location() + " has no pledge rank for \"pledge\"");
                    }
                }
            }
            case TitleSource.AlignmentRank source -> {
                Optional<Faction> faction = factions.apply(source.faction());
                if (faction.isEmpty()) {
                    errors.add(path + ": unknown faction " + source.faction().location());
                } else if (faction.get().ranks().stream().map(FactionRank::id).noneMatch(source.rank()::equals)) {
                    errors.add(path + ": rank '" + source.rank() + "' is not in the ladder of " + source.faction().location());
                }
            }
            case TitleSource.Starter source -> {}
            case TitleSource.Special source -> {}
        }
    }
}
