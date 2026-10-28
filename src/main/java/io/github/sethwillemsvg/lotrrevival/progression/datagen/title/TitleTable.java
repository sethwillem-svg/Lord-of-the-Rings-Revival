package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import java.util.ArrayList;
import java.util.List;

/**
 * Every title. All text is our own. Content lives in {@link MiscTitles} (starter, special, achievement),
 * {@link FactionTitles} (faction, multi-faction and any-faction alignment) and {@link RankTitles}.
 */
public final class TitleTable {
    private TitleTable() {}

    public static List<TitleRow> rows() {
        List<TitleRow> rows = new ArrayList<>();
        MiscTitles.addRows(rows);
        FactionTitles.addRows(rows);
        RankTitles.addRows(rows);
        return rows;
    }

    /** Achievement id paths ({@code <tab>/<name>}) that have a title row. */
    public static java.util.Set<String> achievementPaths() {
        return MiscTitles.achievementPaths();
    }
}
