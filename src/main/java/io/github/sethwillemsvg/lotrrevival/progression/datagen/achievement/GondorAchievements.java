package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the gondor tab. Empty until its content ticket. */
public final class GondorAchievements extends AchievementTabProvider {
    public GondorAchievements() {
        super(AchievementTab.GONDOR);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
