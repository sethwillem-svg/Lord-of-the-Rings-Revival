package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Feeds the shared achievement table with the rows of one tab. Content tickets add rows only. */
public abstract class AchievementTabProvider {
    private final AchievementTab tab;

    protected AchievementTabProvider(AchievementTab tab) {
        this.tab = tab;
    }

    public final AchievementTab tab() {
        return tab;
    }

    /** Starts a row {@code lotrrevival:<tab>/<name>} in this provider's tab. */
    protected final AchievementRow.Builder row(String name) {
        return AchievementRow.builder(tab, name);
    }

    /** Emits every row of this tab. */
    public abstract void addRows(Consumer<AchievementRow> out);
}
