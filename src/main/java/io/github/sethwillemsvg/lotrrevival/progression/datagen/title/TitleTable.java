package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Every title. This ticket ships the system and a handful of sample titles, one per source type; the full list
 * is a content ticket. Names are our own text.
 */
public final class TitleTable {
    private TitleTable() {}

    public static List<TitleRow> rows() {
        List<TitleRow> rows = new ArrayList<>();
        starter(rows, "wanderer", "Wanderer");
        starter(rows, "road_walker", "Road-walker");
        starter(rows, "hearth_seeker", "Hearth-seeker");

        rows.add(new TitleRow("special/playtester", "Playtester", null, TitleSource.Special.INSTANCE, true));

        // Alignment with the faction at its pledge threshold.
        rows.add(new TitleRow(
                "faction/hobbit_friend",
                "Friend of the Hobbits",
                null,
                new TitleSource.FactionAlignment(List.of(Factions.HOBBIT), Optional.empty()),
                false));

        rows.add(new TitleRow(
                "achievement/threshold_crosser",
                "Threshold Crosser",
                null,
                new TitleSource.AchievementSource(ResourceLocation.fromNamespaceAndPath("lotrrevival", "general/enter_middle_earth")),
                false));

        // A rank above the hobbit pledge rank (r2), so it also needs an active pledge.
        rows.add(new TitleRow(
                "rank/hobbit_hearthwarden",
                "Hearthwarden of the Shire",
                "Hearthwardeness of the Shire",
                new TitleSource.AlignmentRank(Factions.HOBBIT, "r3"),
                false));
        return rows;
    }

    private static void starter(List<TitleRow> rows, String name, String text) {
        rows.add(new TitleRow("starter/" + name, text, null, TitleSource.Starter.INSTANCE, false));
    }
}
