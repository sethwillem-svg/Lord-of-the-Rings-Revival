package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the mordor tab. Empty until its content ticket. */
public final class MordorAchievements extends AchievementTabProvider {
    public MordorAchievements() {
        super(AchievementTab.MORDOR);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
